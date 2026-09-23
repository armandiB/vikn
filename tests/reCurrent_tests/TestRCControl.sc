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
		var names = song.midi.control(\knob, 5, 0, "No Such Device", { |x| x * 2 }, { |v, raw| got = [v, raw] }, fine: true);
		this.assertEquals(names, [\knob, \rc_knob_lsb], "msb and lsb defs");
		MIDIdef(\rc_knob_lsb).func.value(127, 37, 0, nil);
		MIDIdef(\knob).func.value(10, 5, 0, nil);
		this.assertEquals(got, [22, 11], "fine value adds lsb/127");
		song.midi.free(\knob);
		this.assertEquals(MIDIdef.all[\knob], nil, "defs freed by name");
		this.assertEquals(MIDIdef.all[\rc_knob_lsb], nil, "lsb def freed too");
		this.assertEquals(song.midi.defs.size, 0, "mapping forgotten");
	}

	test_midi_controlAttribute_and_guard {
		var target = (x: 0);
		var swing = song.layer(\core).swing;
		song.midi.controlAttribute(target, \x, { |v| v / 127 }, 1, 0, "No Such Device", name: \tx);
		song.midi.controlAttribute(swing, \amount, { |v| v / 127 }, 2, 0, "No Such Device", name: \tsw);
		song.midi.control(\bad, 3, 0, "No Such Device", { |v| nil.explode }, { }, false);
		MIDIdef(\tx).func.value(127, 1, 0, nil);
		MIDIdef(\tsw).func.value(63.5, 2, 0, nil);
		MIDIdef(\bad).func.value(1, 3, 0, nil);
		this.assertEquals(target.x, 1.0, "dictionary target");
		this.assertFloatEquals(swing.amount, 0.5, "setter target");
		this.assert(this.logHas("explode"), "error in a mapping is reported");
		song.midi.freeAll;
		this.assertEquals(MIDIdef.all[\bad], nil, "freeAll");
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
