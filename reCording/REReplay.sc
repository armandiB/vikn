// reCording — a soundfile cued for replay into a NodeProxy (the chain's
// signal, for AmbiX conversions) or a Bus (the ~rc_prepare_soundfile /
// ~start_playback_file of the pieces).
//
//   ~song.addReplay(\take, REReplay(s, ~original_root +/+ "LeafHues/take.aiff", 16, ampDb: 0)
//       .into(~song.ambi.signal)).prepare;
//   ~song.replay(\take).start([4, 0]);
//   ~song.replay(\take).set(\amp, 0.5);
//   ~song.replay(\take).stop;
//
// prepare (server running) cues the file, then, for a proxy target, primes the
// DiskIn source into it (fadeTime 0, awake false: nothing plays until start);
// free restores the proxy's fadeTime and awake. process is a { |sig| } applied
// to the DiskIn output (a re-encoding, a gain).

REReplay {
	var <server, <path, <numChannels, <>clock, <ampDb, <bufferSize, <process;
	var <target, <targetGroup, <targetKind, <buffer, <synth, <isPrepared = false;
	var savedFadeTime, savedAwake;
	var <>addAction = \addToHead;   // of the DiskIn synth in its group (a bus target)
	var <cuedFrame = 0, def;        // the frame the file was cued from; the bus target's SynthDef, sent at prepare

	*new { |server, path, numChannels = 16, clock, ampDb = 0, bufferSize = 262144, process|
		^super.new.initREReplay(server, path, numChannels, clock, ampDb, bufferSize, process)
	}

	initREReplay { |serverarg, patharg, numChannelsarg, clockarg, ampDbarg, bufferSizearg, processarg|
		server = serverarg ? Server.default;
		path = patharg.asString.standardizePath;
		numChannels = numChannelsarg;
		clock = clockarg;
		ampDb = ampDbarg;
		bufferSize = bufferSizearg;
		process = processarg;
	}

	process_ { |func| process = func }
	ampDb_ { |db| ampDb = db; if(isPrepared) { this.set(\amp0, db.dbamp) } }

	// NodeProxy: the DiskIn source is primed into it. Bus or index: a DiskIn synth plays in group. Chainable.
	into { |proxyOrBus, group|
		target = proxyOrBus;
		targetGroup = group;
		targetKind = case
			{ proxyOrBus.isKindOf(NodeProxy) } { \proxy }
			{ proxyOrBus.isKindOf(Bus) or: { proxyOrBus.isNumber } } { \bus };
		if(targetKind.isNil) { RCLog.error(\replay, "into: % is neither a NodeProxy nor a Bus".format(proxyOrBus)) };
		^this
	}

	prBusIndex { ^if(target.isKindOf(Bus)) { target.index } { target } }

	prSourceFunc {
		var buf = buffer, n = numChannels, gain = ampDb.dbamp;
		var proc = process ? { |sig| sig };
		^{ |amp = 1, amp0 = 1| amp0 * gain * amp * proc.value(DiskIn.ar(n, buf, loop: 0)) }
	}

	// Cues the file (from startFrame) and, for a proxy target, primes the source; for a bus target
	// the synth's definition is sent now, so that a start sends one message. action.(this) once cued.
	prepare { |startFrame = 0, action|
		if(server.serverRunning.not) { RCLog.error(\replay, "prepare: server not running"); ^this };
		if(targetKind.isNil) { RCLog.error(\replay, "prepare: no target, call into(proxyOrBus) first"); ^this };
		if(File.exists(path).not) { RCLog.warn(\replay, "soundfile not found: %".format(path)) };
		this.prFreeBuffer;
		cuedFrame = (startFrame ? 0).asInteger.max(0);
		buffer = Buffer.cueSoundFile(server, path, cuedFrame, numChannels, bufferSize);
		Routine {
			server.sync;
			if(targetKind == \proxy) {
				savedFadeTime = savedFadeTime ? target.fadeTime;
				savedAwake = savedAwake ? target.awake;
				target.fadeTime = 0;
				target.awake = false;
				target.prime(this.prSourceFunc);
			} {
				def = this.prSourceFunc.asSynthDef(name: ("re_replay_" ++ this.identityHash.abs).asSymbol);
				def.send(server);
				server.sync;
			};
			isPrepared = true;
			RCLog.post(\replay, "prepared %%".format(path.basename, if(cuedFrame > 0) { " from frame " ++ cuedFrame } { "" }));
			action.value(this);
		}.play(SystemClock);
	}

	// Quantized start on clock (the replay's, else TempoClock.default).
	start { |quant, clockarg, amp|
		var c = clockarg ? clock ? TempoClock.default;
		if(isPrepared.not) { RCLog.error(\replay, "start: not prepared (call prepare and wait for the cue)"); ^this };
		Routine { this.startNow(amp) }.play(c, quant);
	}

	// Now, in the calling thread: inside server.bind the synth's message joins the bind's bundle
	// (the definition went at prepare, nothing waits).
	startNow { |amp|
		if(isPrepared.not) { RCLog.error(\replay, "startNow: not prepared (call prepare and wait for the cue)"); ^this };
		if(targetKind == \proxy) {
			target.send(nil, 0);
		} {
			synth !? { |s| RCGuard.call(\replay, nil) { s.free } };
			synth = Synth.basicNew(def.name, server);
			server.sendMsg(*synth.newMsg(targetGroup ?? { server.defaultGroup }, [\i_out, this.prBusIndex], addAction));
		};
		amp !? { |a| this.set(\amp, a) };
		RCLog.post(\replay, "started playback %".format(path.basename));
	}

	stop {
		if(targetKind == \proxy) {
			target !? { |t| if(isPrepared) { RCGuard.call(\replay, nil) { t.removeAt(0) } } };
		} {
			synth !? { |s| RCGuard.call(\replay, nil) { s.free } };
			synth = nil;
		};
	}

	// proxy.set / synth.set: the \amp control of the source ({ |amp = 1| }).
	set { |key, value|
		if(targetKind == \proxy) { target !? { |t| if(isPrepared) { t.set(key, value) } } } { synth !? (_.set(key, value)) };
	}

	prFreeBuffer {
		buffer !? { |b| RCGuard.call(\replay, nil) { b.close({ |buf| buf.freeMsg }) } };
		buffer = nil;
	}

	// Stops, drops the cue buffer and gives the proxy its fadeTime and awake back.
	free {
		this.stop;
		if(targetKind == \proxy and: { target.notNil }) {
			savedFadeTime !? { |ft| target.fadeTime = ft };
			savedAwake !? { |aw| target.awake = aw };
		};
		savedFadeTime = nil;
		savedAwake = nil;
		this.prFreeBuffer;
		def !? { |d| if(server.serverRunning) { server.sendMsg(\d_free, d.name) } };
		def = nil;
		isPrepared = false;
	}

	printOn { |stream| stream << "REReplay(" << path.basename << " → " << (targetKind ? "no target") << (if(isPrepared) { ", prepared" } { "" }) << ")" }
}
