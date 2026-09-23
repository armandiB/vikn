// reCurrent — one song: identity, seed, layers, server topology, resources.
//
// Replaces the per-song globals of the proto-library (~song_name, ~song_seed,
// ~beat_envs, ~beat_envs[\global] arrays, ~orgnsms_dict, group variables).
//
//   ~song = RCSong(\hadronlake2, 20260228);       // after RCSession.boot
//   ~song.makeGroups;                              // standard node tree
//   ~song.outArray = [2, 4, 6, ~hoaBus.index];     // what orgnsm_out_idx indexes
//   ~song.layer(\core).swing.amount = 1/22;

RCSong {
	var <name, <seed, <session, <layers, <groups;
	var <groupArray, <outArray, <inChanArray, <fobjectGroupArray, <fobjectOutArray;
	var <loopBuffers, <>sampleLibrary, <history;
	var server, clock;

	*new { |name, seed, session, layerKeys = #[\core, \details, \meta]|
		^super.new.initRCSong(name, seed, session, layerKeys)
	}

	initRCSong { |namearg, seedarg, sessionarg, layerKeysarg|
		name = namearg.asSymbol;
		seed = seedarg;
		session = sessionarg ?? { RCSession.default };
		if(session.isNil) {
			Error("RCSong(%): no RCSession. Call RCSession.boot(server, ...) first.".format(name)).throw
		};
		layers = IdentityDictionary.new;
		groups = IdentityDictionary.new;
		loopBuffers = IdentityDictionary.new;
		history = List.new;
		groupArray = [];
		outArray = [0];
		inChanArray = [0];
		fobjectGroupArray = [];
		fobjectOutArray = [0];
		layerKeysarg.do { |k| this.addLayer(k) };
		session.registerSong(this);
	}

	server { ^server ?? { session.server } }
	server_ { |s| server = s }
	clock { ^clock ?? { session.clock } }
	clock_ { |c| clock = c }
	seed_ { |s| seed = s }

	//////// layers

	layer { |key|
		^layers[key.asSymbol] ?? {
			RCLog.warn(\song, "no layer % in song %".format(key, name));
			nil
		}
	}

	addLayer { |key, clock, swing, midiOut, addMidiOuts|
		var l = RCLayer(this, key, clock, swing, midiOut, addMidiOuts);
		layers[key.asSymbol] !? (_.free);
		layers[key.asSymbol] = l;
		^l
	}

	allBeats { ^layers.values.collect { |l| l.beats.values }.flatten(1) }

	killAllBeats {
		layers.do(_.killAll);
		RCLog.post(\song, "% killed all beats".format(name));
	}

	pauseAllBeats { layers.do(_.pauseAll) }
	resumeAllBeats { layers.do(_.resumeAll) }

	//////// server topology

	groupArray_ { |array| groupArray = array.asArray }
	outArray_ { |array| outArray = array.asArray }
	inChanArray_ { |array| inChanArray = array.asArray }
	fobjectGroupArray_ { |array| fobjectGroupArray = array.asArray }
	fobjectOutArray_ { |array| fobjectOutArray = array.asArray }

	group { |role| ^groups[role.asSymbol] }

	// in → sounds { orgnsms (ParGroup), fobjects (ParGroup) } → background → outputDecode
	makeGroups { |serverarg|
		var s = serverarg ? this.server;
		if(s.serverRunning.not) {
			RCLog.error(\song, "% makeGroups: server not running".format(name));
			^nil
		};
		this.freeGroups;
		groups[\in] = ParGroup(s);
		groups[\sounds] = Group.after(groups[\in]);
		groups[\orgnsms] = ParGroup(groups[\sounds]);
		groups[\fobjects] = ParGroup.after(groups[\orgnsms]);
		groups[\background] = Group.after(groups[\sounds]);
		groups[\outputDecode] = Group.after(groups[\background]);
		groupArray = [groups[\orgnsms]];
		fobjectGroupArray = [groups[\fobjects]];
		^groups
	}

	freeGroups {
		[\in, \sounds, \background, \outputDecode].do { |k|
			groups[k] !? { |g| RCGuard.call(\song, nil) { g.free } };
		};
		groups.clear;
	}

	// Clean panic: release every gated synth in a group (default: all sounds).
	releaseAll { |groupKey = \sounds|
		var g = groups[groupKey.asSymbol];
		if(g.isNil) { RCLog.warn(\song, "% releaseAll: no group %".format(name, groupKey)); ^this };
		g.set(\gate, 0);
		RCLog.post(\song, "% released all synths in %".format(name, groupKey));
	}

	freeAllNodes { |groupKey = \sounds|
		var g = groups[groupKey.asSymbol];
		if(g.isNil) { RCLog.warn(\song, "% freeAllNodes: no group %".format(name, groupKey)); ^this };
		g.freeAll;
		RCLog.post(\song, "% freed all nodes in %".format(name, groupKey));
	}

	//////// resources

	loopBuffer { |key| ^loopBuffers[key.asSymbol] }

	registerLoopBuffer { |key, loopBuffer|
		loopBuffers[key.asSymbol] !? { |old| if(old !== loopBuffer) { old.free } };
		loopBuffers[key.asSymbol] = loopBuffer;
	}

	unregisterLoopBuffer { |key, loopBuffer|
		if(loopBuffers[key.asSymbol] === loopBuffer) { loopBuffers.removeAt(key.asSymbol) };
	}

	freeSampleLibrary {
		sampleLibrary !? { |lib|
			RCGuard.call(\song, nil) { lib.leafDo { |keys, buffer| buffer.free } };
		};
		sampleLibrary = nil;
	}

	// Everything a scene/init re-creates: beats, buffers, groups. OSC and MIDI
	// definitions survive (a scene/init handler must stay reachable).
	clearAll { |freeGroups = true|
		this.killAllBeats;
		loopBuffers.copy.do(_.free);
		loopBuffers.clear;
		this.freeSampleLibrary;
		if(freeGroups) { this.freeGroups };
		RCLog.post(\song, "% cleared".format(name));
	}

	free {
		this.clearAll(true);
		layers.do(_.free);
		session.unregisterSong(this);
	}

	printOn { |stream|
		stream << "RCSong(" << name << ", seed " << seed << ")"
	}
}
