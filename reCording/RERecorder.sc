// reCording — a named set of tracks recorded together, one Recorder per track,
// under <root>/<subfolder>/<stamp>_<name>[ <version>].<format>
// (the ~rc_make_recorders / ~start_recording / ~stop_recording of the pieces).
//
//   ~song.addRecorder(\ambix, RERecorder(s, ~piece_dir +/+ "RecorderSC", "LeafHues/AmbiX", "w1", numChannels: 16)
//       .addTrack('AmbiX Out', ~song.ambi.decoder)).prepare;
//   ~song.recorder(\ambix).start([4, 0]);     // on the song's clock
//   ~song.recorder(\ambix).stop;
//
// A track's bus is a Bus, a NodeProxy (its bus, read at record time) or an
// integer index. prepare (server running) makes the folder and the Recorders
// with stamped paths; start needs a prepared recorder for a quantized start
// (an unprepared one is prepared on the spot, with a warning). stop closes
// the files: the next start needs a new prepare (new stamp). stopAfter runs
// on SystemClock (seconds, tempo-independent).

RERecorder {
	var <server, <root, <subfolder, <version, <format, <numChannels, <>clock, <>node, <>sampleFormat;
	var <tracks, <recorders, <paths, <stamp, <isPrepared = false, stopRoutine;

	*new { |server, root, subfolder = "", version = "", format = "aiff", numChannels = 2, clock, node, sampleFormat|
		^super.new.initRERecorder(server, root, subfolder, version, format, numChannels, clock, node, sampleFormat)
	}

	initRERecorder { |serverarg, rootarg, subfolderarg, versionarg, formatarg, numChannelsarg, clockarg, nodearg, sampleFormatarg|
		server = serverarg ? Server.default;
		root = rootarg.asString;
		subfolder = subfolderarg.asString;
		version = versionarg.asString;
		format = formatarg.asString;
		numChannels = numChannelsarg;
		clock = clockarg;
		node = nodearg;
		sampleFormat = sampleFormatarg;
		tracks = [];
	}

	//////// tracks

	// numChannels: the bus's when it has one (Bus, NodeProxy), else the recorder's. Chainable.
	addTrack { |name, bus, numChannels|
		name = name.asSymbol;
		tracks = tracks.reject { |t| t[0] == name } ++ [[name, bus, numChannels]];
		^this
	}

	removeTrack { |name|
		name = name.asSymbol;
		tracks = tracks.reject { |t| t[0] == name };
		^this
	}

	trackNames { ^tracks.collect(_.first) }
	track { |name| ^tracks.detect { |t| t[0] == name.asSymbol } }

	trackChannels { |t| ^t[2] ?? { this.prBusChannels(t[1]) } ? numChannels }

	prBusChannels { |bus|
		if(bus.isKindOf(NodeProxy) or: { bus.isKindOf(Bus) }) { ^bus.numChannels };
		^nil
	}

	// Recorder.record wants an integer index (a NodeProxy would become a map string).
	prBusIndex { |bus|
		case
		{ bus.isKindOf(NodeProxy) } { ^bus.bus !? (_.index) }
		{ bus.isKindOf(Bus) } { ^bus.index }
		{ bus.isNumber } { ^bus.asInteger };
		^nil
	}

	pathFor { |name, stamparg| ^RETake.path(root, subfolder, name, version, format, stamparg ? stamp) }

	//////// lifecycle

	prepare { |stamparg|
		if(server.serverRunning.not) { RCLog.error(\recorder, "prepare: server not running"); ^this };
		if(tracks.isEmpty) { RCLog.error(\recorder, "prepare: no tracks (addTrack first)"); ^this };
		this.stop;
		stamp = stamparg ?? { Date.localtime.stamp };
		recorders = IdentityDictionary.new;
		paths = IdentityDictionary.new;
		tracks.do { |t|
			var name = t[0];
			var path = this.pathFor(name);
			var r = Recorder(server);
			r.recHeaderFormat = format;
			sampleFormat !? { |f| r.recSampleFormat = f };
			RCGuard.call(\recorder, nil) { r.prepareForRecord(path, this.trackChannels(t)) };
			recorders[name] = r;
			paths[name] = path;
		};
		isPrepared = true;
		RCLog.post(\recorder, "prepared % track(s) under %".format(tracks.size, paths.values.first.dirname));
	}

	// Quantized start on clock (the recorder's, else TempoClock.default), recording
	// after node (nil: the tail of the root node, after every group). alignToLatency
	// delays the record synths by server.latency, in line with scheduled bundles.
	start { |quant, clockarg, nodearg, alignToLatency = false|
		var c = clockarg ? clock ? TempoClock.default;
		var target = nodearg ? node;
		if(server.serverRunning.not) { RCLog.error(\recorder, "start: server not running"); ^this };
		if(isPrepared.not) {
			RCLog.warn(\recorder, "start: not prepared, preparing now (the start is not quantized)");
			this.prepare;
			if(isPrepared.not) { ^this };
		};
		Routine {
			var go = {
				recorders.keysValuesDo { |name, r|
					var idx = this.prBusIndex(this.track(name)[1]);
					if(idx.isNil) {
						RCLog.warn(\recorder, "track %: no bus index, not recorded".format(name));
					} {
						RCGuard.call(\recorder, nil) { r.record(nil, idx, nil, target) };
					};
				};
				RCLog.post(\recorder, "started recording %".format(this.trackNames));
			};
			if(alignToLatency) { SystemClock.sched(server.latency ? 0, { go.value; nil }) } { go.value };
		}.play(c, quant);
	}

	stop {
		stopRoutine !? (_.stop);
		stopRoutine = nil;
		recorders !? { |dict|
			dict.do { |r|
				if(r.isRecording or: { r.path.notNil }) { RCGuard.call(\recorder, nil) { r.stopRecording } };
			};
			RCLog.post(\recorder, "stopped recording %".format(this.trackNames));
		};
		recorders = nil;
		isPrepared = false;
	}

	// Stop after `seconds` (SystemClock); a previous stopAfter is cancelled.
	stopAfter { |seconds|
		stopRoutine !? (_.stop);
		stopRoutine = Routine { seconds.wait; stopRoutine = nil; this.stop }.play(SystemClock);
	}

	isRecording { ^recorders.notNil and: { recorders.values.any(_.isRecording) } }

	free { this.stop }

	printOn { |stream| stream << "RERecorder(" << this.trackNames << (if(isPrepared) { ", prepared" } { "" }) << ")" }
}
