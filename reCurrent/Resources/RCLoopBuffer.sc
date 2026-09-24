// reCurrent — a live input loop capture (BlockOrgnsm's ~create_loop_buffer).
//
// Allocates a buffer of loopBeats × maxTempoDrop beats at the current tempo,
// starts a write_buffer_<n>chan synth (see RCSynthDefs.addLoopBufferWriters)
// recording from song.inChanArray[inChanIdx], and a clock-locked routine
// that retriggers the record head every loopBeats. Orgnsms replay it through
// RCOrgnsmPatterns.numFramesPerLoop / cuttingFadeStart.
//
//   ~lb = RCLoopBuffer(~song, \loop1, loopBeats: 8, inChanIdx: 0, numChannels: 2);
//   ~orgnsm.setBuffer(~lb.buffer);
//   ~lb.free
// If the tempo later drops by more than maxTempoDrop×, the loop no longer
// fits the buffer: a warning is posted once and the recording is truncated.
// The writer starts once the allocation is confirmed (server.sync), on the
// next loop grid; its retriggers are bundled with server.latency like the
// pattern events, so the loop boundary lines up with the beats.

RCLoopBuffer {
	var <song, <key, <loopBeats, <inChanIdx, <numChannels, <layerKey, <maxTempoDrop;
	var <buffer, <synth, <routine, <server, <clock, <chanIn, <allocBeatDur, <sampleRate;
	var <isFreed = false, warnedTruncation = false;

	*new { |song, key, loopBeats = 4, inChanIdx = 0, numChannels = 1, layerKey = \core, maxTempoDrop = 4|
		^super.new.initRCLoopBuffer(song, key, loopBeats, inChanIdx, numChannels, layerKey, maxTempoDrop)
	}

	// frames needed for loopBeats at beatDur, with the tempo-drop margin
	*framesFor { |sampleRate, beatDur, loopBeats, maxTempoDrop = 4|
		^(sampleRate * beatDur * loopBeats * maxTempoDrop).asInteger.max(1)
	}

	initRCLoopBuffer { |songarg, keyarg, loopBeatsarg, inChanIdxarg, numChannelsarg, layerKeyarg, maxTempoDroparg|
		var layer, trigLen;
		song = songarg;
		key = keyarg.asSymbol;
		loopBeats = loopBeatsarg;
		inChanIdx = inChanIdxarg;
		numChannels = numChannelsarg;
		layerKey = layerKeyarg;
		maxTempoDrop = maxTempoDroparg;
		server = song.server;
		layer = song.layer(layerKey);
		clock = layer !? (_.clock) ?? { song.clock };
		chanIn = song.inChanArray[inChanIdx];
		if(chanIn.isNil) {
			RCLog.error(\loopBuffer, "% : no input channel at index % (song.inChanArray has %)".format(key, inChanIdx, song.inChanArray.size));
			isFreed = true;
			^this
		};
		if(loopBeats.isNumber.not or: { loopBeats <= 0 }) {
			RCLog.error(\loopBuffer, "% : loopBeats must be positive (got %)".format(key, loopBeats));
			isFreed = true;
			^this
		};
		if(server.serverRunning.not or: { server.sampleRate.isNil }) {
			RCLog.error(\loopBuffer, "% : server not running, cannot allocate".format(key));
			isFreed = true;
			^this
		};
		allocBeatDur = clock.beatDur;
		sampleRate = server.sampleRate;
		buffer = Buffer.alloc(server, this.class.framesFor(sampleRate, allocBeatDur, loopBeats, maxTempoDrop), numChannels);
		trigLen = min(0.1, loopBeats * 0.5);
		routine = Routine {
			synth = Synth(("write_buffer_" ++ numChannels ++ "chan").asSymbol, [\bufnum, buffer, \chan_in, chanIn]);
			loop {
				server.makeBundle(server.latency, { synth.set(\trigger, 1.0) });
				trigLen.yield;
				server.makeBundle(server.latency, { synth.set(\trigger, -1.0) });
				this.prCheckTempo;
				(loopBeats - trigLen).yield;
			};
		};
		song.registerLoopBuffer(key, this);
		// the writer must not reach the server before the buffer exists
		Routine {
			server.sync;
			if(isFreed.not) { routine.play(clock, [loopBeats, 0]) };
		}.play(SystemClock);
		RCLog.post(\loopBuffer, "% : % beats × % margin, % frames on input %".format(key, loopBeats, maxTempoDrop, buffer.numFrames, chanIn));
	}

	// frames one loop occupies at the current tempo (at the allocation sample rate)
	numFramesPerLoop { ^(clock.beatDur * loopBeats * sampleRate).asInteger }

	fits { ^buffer.notNil and: { this.numFramesPerLoop <= buffer.numFrames } }

	prCheckTempo {
		if(buffer.isNil) { ^this };
		if(this.fits.not) {
			if(warnedTruncation.not) {
				RCLog.warn(\loopBuffer, "% : loop of % frames exceeds the buffer (% frames): tempo dropped by more than %×, recording truncated".format(key, this.numFramesPerLoop, buffer.numFrames, maxTempoDrop), force: true);
				warnedTruncation = true;
			};
		} {
			warnedTruncation = false;
		};
	}

	free {
		if(isFreed) { ^this };
		isFreed = true;
		RCGuard.call(\loopBuffer, nil) { routine !? (_.stop) };
		RCGuard.call(\loopBuffer, nil) { synth !? (_.free) };
		RCGuard.call(\loopBuffer, nil) { buffer !? (_.free) };
		routine = nil;
		synth = nil;
		buffer = nil;
		song.unregisterLoopBuffer(key, this);
	}

	printOn { |stream| stream << "RCLoopBuffer(" << key << ", " << loopBeats << " beats)" }
}
