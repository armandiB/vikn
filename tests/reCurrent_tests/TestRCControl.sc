TestRCControl : UnitTest {
	var clock, song, savedRateLimit;

	setUp {
		clock = RCTestSupport.clock;
		RCTestSupport.bootSession;
		song = RCSong(\ctl, 1);
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown {
		RCTestSupport.reset;
		RCLog.rateLimit = savedRateLimit;
	}

	logHas { |text| ^RCLog.history.any { |entry| entry[2].contains(text) } }

	//////// OSC

	test_osc_paths {
		var osc = song.osc;
		this.assertEquals(osc.path(\scene, \init), '/ctl/scene/init', "symbol name");
		this.assertEquals(osc.key(\scene, \init), 'ctl_scene_init', "key");
		this.assertEquals(osc.path(\meta, \start_normal), '/ctl/meta/start/normal', "underscores split into segments");
		this.assertEquals(osc.path(\a, [\b, \c]), '/ctl/a/b/c', "array of segments");
		this.assertEquals(osc.path(\kill, nil), '/ctl/kill', "nil adds nothing");
	}

	test_osc_def_is_guarded_and_callable {
		var hits = 0;
		var f = song.osc.def(\scene, \init, { hits = hits + 1 }, print: false);
		var g = song.osc.def(\scene, \boom, { nil.explode }, print: false);
		f.value;
		this.assertEquals(hits, 1, "returned handler runs the function");
		this.assert(OSCdef('ctl_scene_init').notNil, "OSCdef registered under the proto-library key");
		this.assert(OSCdef('ctl_scene_init').permanent, "permanent: Cmd-Period keeps it");
		g.value;
		this.assert(this.logHas("explode"), "error in a handler is reported, not thrown");
		song.osc.free(\scene, \init);
		this.assertEquals(song.osc.defs.size, 1, "freed by instr/names");
		song.osc.freeAll;
		this.assertEquals(song.osc.defs.size, 0, "freeAll");
	}

	test_osc_startDefs_and_editDef {
		var layer = song.layer(\core);
		var spec = RCBeatSpec(layer, \inst, [type: \rest, dur_flex: 1, amp: 0.1]);
		song.osc.startDefs(spec, \inst, \start_normal, print: false);
		this.assert(OSCdef('ctl_inst_kill').notNil and: { OSCdef('ctl_inst_start_normal').notNil }, "kill/pause/resume/start defs");
		OSCdef('ctl_inst_start_normal').func.value;
		this.assert(layer.beat(\inst).notNil and: { layer.beat(\inst).isPlaying }, "start def starts the beat");
		song.osc.editDef(\core, \inst, \louder, \amp, 0.9, print: false, printModif: false);
		OSCdef('ctl_inst_louder').func.value;
		this.assertEquals(layer.beat(\inst).keyProxy(\amp).source, 0.9, "edit def sets the attribute");
		OSCdef('ctl_inst_pause').func.value;
		this.assert(layer.beat(\inst).player.isPlaying.not, "pause def");
		OSCdef('ctl_inst_kill').func.value;
		this.assertEquals(layer.beat(\inst), nil, "kill def frees the beat");
		song.osc.killAllDef(print: false);
		this.assert(OSCdef('ctl_kill_all').notNil, "kill all def");
	}

	//////// MIDI

	test_midi_findSrcId_unknown_device {
		this.assertEquals(RCMidi.findSrcId("No Such Device"), nil, "unknown device → nil");
		this.assert(this.logHas("not found"), "warned");
	}

	test_midi_control_and_fine {
		var got = nil;
		var keys = song.midi.control(\knob, 5, 0, "No Such Device", { |x| x * 2 }, { |v, raw| got = [v, raw] }, fine: true);
		this.assertEquals(keys, [\rc_ctl_knob, \rc_ctl_knob_lsb], "msb and lsb defs, namespaced by song");
		this.assert(MIDIdef.all[\rc_ctl_knob].permanent, "permanent: Cmd-Period keeps it");
		MIDIdef(\rc_ctl_knob).func.value(10, 5, 0, nil);
		this.assertEquals(got, nil, "the MSB waits for its LSB");
		MIDIdef(\rc_ctl_knob_lsb).func.value(127, 37, 0, nil);
		this.assertFloatEquals(got[1], 10 + (127 / 128), "the LSB completes it: lsb/128 added (never reaches the next msb step)");
		this.assertFloatEquals(got[0], 2 * (10 + (127 / 128)), "valFunc applied");
		song.midi.free(\knob);
		this.assertEquals(MIDIdef.all[\rc_ctl_knob], nil, "defs freed by name");
		this.assertEquals(MIDIdef.all[\rc_ctl_knob_lsb], nil, "lsb def freed too");
		this.assertEquals(song.midi.defs.size, 0, "mapping forgotten");
		this.assertEquals(RCMidi.fineValue(nil, 0, 5, \rc_ctl_knob), nil, "fine value forgotten");
	}

	// The MIDI spec's order: the MSB, then its LSB. Turned up across a step the value never goes back
	// (fired on the MSB with the last LSB, it read 63.98, 64.98, 64.02).
	test_midi_control_fine_pairs {
		var got = List.new, msb = { |v| MIDIdef(\rc_ctl_pairs).func.value(v, 6, 0, nil) }, lsb = { |v| MIDIdef(\rc_ctl_pairs_lsb).func.value(v, 38, 0, nil) };
		var pause = { |secs, what| var t0 = Main.elapsedTime; this.wait({ (Main.elapsedTime - t0) > secs }, what, 2) };
		song.midi.control(\pairs, 6, 0, "No Such Device", { |x| x }, { |v| got.add(v) }, fine: true);
		lsb.(50);
		this.assertEquals(got.size, 0, "an LSB before any MSB: nothing to complete");
		[[63, 120], [63, 127], [64, 0], [64, 2], [64, 9]].do { |p| msb.(p[0]); lsb.(p[1]) };
		this.assertEquals(got.asArray, [63 + (120 / 128), 63 + (127 / 128), 64, 64 + (2 / 128), 64 + (9 / 128)], "one value a pair, rising across the step");
		lsb.(20);
		this.assertFloatEquals(got.last, 64 + (20 / 128), "an LSB alone: a fine move on the last MSB");
		this.assertEquals(RCMidi.fineValue(nil, 0, 6, \rc_ctl_pairs), 20, "the LSB kept");
		msb.(66);
		this.assertEquals(RCMidi.fineValue(nil, 0, 6, \rc_ctl_pairs), 0, "an MSB sets the LSB to 0");
		this.wait({ got.size >= 7 }, "an MSB with no LSB fires alone", 1);
		this.assertEquals(got.last, 66, "alone: its LSB 0, not the last one (20)");
		pause.(0.1, "the wait passes again");
		this.assertEquals(got.size, 7, "once");
		msb.(70);
		song.midi.free(\pairs);
		pause.(0.1, "the wait would pass");
		this.assertEquals(got.size, 7, "freed while an MSB waited: nothing fires");
	}

	test_midi_control_throttle {
		var got = List.new, t0;
		song.midi.control(\th, 9, 0, "No Such Device", { |x| x }, { |v| got.add(v) }, throttle: 0.05);
		5.do { |i| MIDIdef(\rc_ctl_th).func.value(10 + i, 9, 0, nil) };
		this.assertEquals(got.asArray, [10], "the first message fires at once, the burst is swallowed");
		this.wait({ got.size >= 2 }, "the window closes", 2);
		this.assertEquals(got.asArray, [10, 14], "the last pending value fires when the window closes");
		t0 = Main.elapsedTime;
		this.wait({ (Main.elapsedTime - t0) > 0.12 }, "a quiet window passes", 2);
		MIDIdef(\rc_ctl_th).func.value(20, 9, 0, nil);
		this.assertEquals(got.asArray, [10, 14, 20], "after a quiet window the next message fires at once");
		MIDIdef(\rc_ctl_th).func.value(21, 9, 0, nil);
		song.midi.free(\th);
		t0 = Main.elapsedTime;
		this.wait({ (Main.elapsedTime - t0) > 0.12 }, "the window would close", 2);
		this.assertEquals(got.asArray, [10, 14, 20], "a pending value is dropped with its mapping");
	}

	test_midi_controlAttribute_and_guard {
		var target = (x: 0);
		var swing = song.layer(\core).swing;
		song.midi.controlAttribute(target, \x, { |v| v / 127 }, 1, 0, "No Such Device", name: \tx);
		song.midi.controlAttribute(swing, \amount, { |v| v / 127 }, 2, 0, "No Such Device", name: \tsw);
		song.midi.control(\bad, 3, 0, "No Such Device", { |v| nil.explode }, { }, false);
		MIDIdef(\rc_ctl_tx).func.value(127, 1, 0, nil);
		MIDIdef(\rc_ctl_tsw).func.value(63.5, 2, 0, nil);
		MIDIdef(\rc_ctl_bad).func.value(1, 3, 0, nil);
		this.assertEquals(target.x, 1.0, "dictionary target");
		this.assertFloatEquals(swing.amount, 0.5, "setter target");
		this.assert(this.logHas("explode"), "error in a mapping is reported");
		song.midi.freeAll;
		this.assertEquals(MIDIdef.all[\rc_ctl_bad], nil, "freeAll");
	}

	test_midi_function_target_prefix_and_cc_helpers {
		var node = RCTestFakeSettable.new;
		var other = RCTestFakeSettable.new;
		var current = node;
		song.midi.controlSynth({ current }, \set_width, { |v| v }, 7, 0, "No Such Device", name: \bass_w);
		MIDIdef(\rc_ctl_bass_w).func.value(3, 7, 0, nil);
		current = other;
		MIDIdef(\rc_ctl_bass_w).func.value(4, 7, 0, nil);
		this.assertEquals([node.width, other.width], [3, 4], "a Function target is resolved per message");
		song.midi.control(\bass_x, 8, 0, "No Such Device", { |v| v }, { });
		song.midi.control(\lead, 8, 0, "No Such Device", { |v| v }, { });
		this.assert(this.logHas("already listens to cc 8 chan 0"), "a second mapping on one (cc, chan) is warned about");
		this.assertEquals(song.midi.freeMatching("bass_").sort, [\bass_w, \bass_x], "freeMatching frees by prefix");
		this.assertEquals(song.midi.defs.keys.asArray, [\lead], "the others stay");
		this.assertEquals(song.midi.freeCC(8, 0), [\lead], "freeCC frees the mappings on a controller");
		this.assertEquals(song.midi.defs.size, 0, "all gone");
	}

	//////// keyboard

	test_keyboard_state {
		var kb = RCKeyboardState(song, nil);
		kb.noteOn(100, 60, 1);
		kb.touch(50, 1);
		kb.bend(8192 + 4096, 1);
		kb.cc(90, 74, 1);
		kb.noteOn(80, 64, 2);
		this.assertEquals(kb.notes, [60, 64], "notes sorted");
		kb.noteOn(90, 60, 5);
		kb.noteOn(110, 60, 3);
		this.assertEquals(kb.notes, [60, 60, 60, 64], "the same note on several channels");
		this.assertEquals(kb.heldChans.collect { |e| e[\velocity] }, [100, 110, 90, 80], "equal notes ordered by channel (1, 3, 5), then note 64");
		kb.noteOff(0, 60, 5);
		kb.noteOff(0, 60, 3);
		this.assertEquals(kb.held(1)[\touch], 50, "aftertouch stored");
		this.assertFloatEquals(kb.held(1)[\bend], 0.5, "bend scaled to -1..1");
		this.assertEquals(kb.held(1)[\control][74], 90, "cc stored");
		kb.noteOn(70, 67, 1);
		this.assertEquals(kb.held(1)[\note], 67, "new noteOn replaces a stuck note");
		this.assert(this.logHas("replaced"), "replacement warned");
		kb.noteOff(0, 60, 1);
		this.assertEquals(kb.held(1), nil, "mismatched noteOff still clears the channel");
		kb.noteOff(0, 64, 2);
		this.assertEquals(kb.size, 0, "all released");
		kb.noteOff(0, 1, 9);
		this.assert(this.logHas("no state"), "noteOff without state warned");
		kb.bend(0, 3);
		this.assertEquals(kb.held(3), nil, "bend without a note does not make the channel held");
		this.assertEquals(kb.size, 0, "nor counted");
		kb.free;
	}

	test_keyboard_stale_timeout {
		var kb = RCKeyboardState(song, nil, staleTimeout: 0.05);
		var t0 = Main.elapsedTime;
		kb.noteOn(100, 60, 1);
		this.assertEquals(kb.size, 1, "held");
		this.wait({ (Main.elapsedTime - t0) > 0.08 }, "time passes", 2);
		this.assertEquals(kb.size, 0, "stale channel dropped on read");
		kb.free;
	}

	test_song_makeKeyboard_and_free {
		var kb = song.makeKeyboard(nil);
		this.assert(song.keyboard === kb, "song holds the keyboard");
		song.free;
		this.assertEquals(kb.defs.size, 0, "freed with the song");
	}
}
