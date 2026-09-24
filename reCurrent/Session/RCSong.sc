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
	var <groupArray, <outArray, <outNames, <inChanArray, <fobjectGroupArray, <fobjectOutArray;
	var <loopBuffers, <>sampleLibrary;
	var <osc, <midi, <keyboard, <registry;
	var <outputs, <recorders, <replays;
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
		outputs = IdentityDictionary.new;
		recorders = IdentityDictionary.new;
		replays = IdentityDictionary.new;
		groupArray = [];
		outArray = [0];
		inChanArray = [0];
		fobjectGroupArray = [];
		fobjectOutArray = [0];
		osc = RCOsc(this);
		midi = RCMidi(this);
		registry = RCOrgnsmRegistry(this);
		layerKeysarg.do { |k| this.addLayer(k) };
		session.registerSong(this);
	}

	//////// control surfaces

	// MPE keyboard state for a device (see RCKeyboardState).
	makeKeyboard { |deviceName, staleTimeout|
		keyboard !? (_.free);
		keyboard = RCKeyboardState(this, deviceName, staleTimeout);
		^keyboard
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
	outArray_ { |array| outArray = array.asArray; outNames = nil ! outArray.size }

	// Named out slots: registerOut(\arps, bus) appends a slot (or updates the
	// slot of that name) and returns its index; batches read it with
	// outIndex(\arps) instead of a literal position that depends on which
	// block ran last. bus: a Bus or an index.
	registerOut { |key, bus|
		var index = if(bus.isKindOf(Bus)) { bus.index } { bus };
		var i;
		key = key.asSymbol;
		if(index.isNil) { RCLog.error(\song, "% registerOut %: no bus".format(name, key)); ^nil };
		outNames = outNames ? (nil ! outArray.size);
		i = outNames.indexOf(key);
		if(i.isNil) {
			outArray = outArray ++ [index];
			outNames = outNames ++ [key];
			i = outArray.size - 1;
		} {
			outArray = outArray.copy.put(i, index);
		};
		^i
	}

	outIndex { |key|
		^(outNames ? []).indexOf(key.asSymbol) ?? {
			RCLog.error(\song, "% no out registered as % (registerOut first)".format(name, key));
			nil
		}
	}
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

	// The group arrays are emptied too: an event resolving orgnsm_group_idx
	// afterwards reports the missing group instead of targeting a dead node.
	freeGroups {
		[\in, \sounds, \background, \outputDecode].do { |k|
			groups[k] !? { |g| RCGuard.call(\song, nil) { g.free } };
		};
		groups.clear;
		groupArray = [];
		fobjectGroupArray = [];
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

	//////// outputs, recorders, replays (duck-typed: reAmbi / reCording objects, or anything
	// answering build(parentGroup) / clear, and free / clock)

	// An output chain (RAOutputChain) under a name; \main is the song's ambi. An
	// existing object under that name is cleared first. Registered unbuilt: its
	// configuration (numChannels, order, outBus) is valid before buildOutputs.
	addOutput { |key, output|
		key = key.asSymbol;
		outputs[key] !? { |old| if(old !== output) { RCGuard.call(\song, nil) { old.clear } } };
		outputs[key] = output;
		^output
	}

	output { |key| ^outputs[key.asSymbol] }
	ambi { ^outputs[\main] }

	// Build every output inside parent (the outputDecode group by default), after makeGroups.
	buildOutputs { |parent|
		parent = parent ?? { groups[\outputDecode] };
		outputs.do { |o| RCGuard.call(\song, nil) { o.build(parent) } };
		^outputs
	}

	// Recorders and replays (RERecorder, REReplay): the song's clock is adopted
	// when the object has none; a previous object under the name is freed.
	addRecorder { |key, recorder|
		key = key.asSymbol;
		recorders[key] !? { |old| if(old !== recorder) { RCGuard.call(\song, nil) { old.free } } };
		this.prAdoptClock(recorder);
		recorders[key] = recorder;
		^recorder
	}

	recorder { |key| ^recorders[key.asSymbol] }

	addReplay { |key, replay|
		key = key.asSymbol;
		replays[key] !? { |old| if(old !== replay) { RCGuard.call(\song, nil) { old.free } } };
		this.prAdoptClock(replay);
		replays[key] = replay;
		^replay
	}

	replay { |key| ^replays[key.asSymbol] }

	prAdoptClock { |obj|
		if(obj.respondsTo(\clock) and: { obj.respondsTo(\clock_) } and: { obj.clock.isNil }) { obj.clock = this.clock };
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

	// One guard per buffer: a bad entry does not keep the others allocated.
	freeSampleLibrary {
		sampleLibrary !? { |lib|
			RCGuard.call(\song, nil) {
				lib.leafDo { |keys, buffer| RCGuard.call(\song, nil) { buffer.free } };
			};
		};
		sampleLibrary = nil;
	}

	// Everything a scene/init re-creates: beats, buffers, recorders, replays,
	// output chains (cleared but kept registered: their configuration is
	// rebuilt by buildOutputs), groups. OSC and MIDI definitions survive (a
	// scene/init handler must stay reachable).
	clearAll { |freeGroups = true|
		this.killAllBeats;
		registry.fobjects.copy.do { |f| RCGuard.call(\song, nil) { f.free } };
		registry.all.do { |o| RCGuard.call(\song, nil) { o.free } };
		registry.clear;
		loopBuffers.copy.do(_.free);
		loopBuffers.clear;
		this.freeSampleLibrary;
		recorders.copy.do { |r| RCGuard.call(\song, nil) { r.free } };
		recorders.clear;
		replays.copy.do { |r| RCGuard.call(\song, nil) { r.free } };
		replays.clear;
		outputs.do { |o| RCGuard.call(\song, nil) { o.clear } };   // before the groups they live in
		if(freeGroups) { this.freeGroups };
		RCLog.post(\song, "% cleared".format(name));
	}

	free {
		this.clearAll(true);
		outputs.clear;
		layers.do(_.free);
		osc.freeAll;
		midi.freeAll;
		keyboard !? (_.free);
		session.unregisterSong(this);
	}

	printOn { |stream|
		stream << "RCSong(" << name << ", seed " << seed << ")"
	}
}
