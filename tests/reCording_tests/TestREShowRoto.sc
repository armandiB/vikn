// REShowRoto: the Roto-Control's knobs and buttons through the public methods the MIDI handlers
// call (no device, no MIDIClient), the mixes they drive, the sends they make; the mappings' MIDIdefs
// driven as a device would (RCMidi's fine pairing), without MIDI initialized.
TestREShowRoto : UnitTest {
	var sent, roto, savedRateLimit;

	setUp {
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
		sent = List.new;
		roto = REShowRoto((device: "No Such Device", glide: 1.0, knobs: [
			(cc: 0, ctl: "wave", op: \add, range: [-0.5, 0.5]),
			(cc: 1, set: ["colour", "mix"], range: [0, 1]),
			(cc: 2, ctl: "kick", op: \scale, range: [0, 2])
		]));
		roto.sendFunc = { |p, a| sent.add([p.asString] ++ a) };
	}

	tearDown {
		roto.stop;
		RCLog.rateLimit = savedRateLimit;
	}

	logHas { |text| ^RCLog.history.any { |e| e[2].contains(text) } }

	test_spec {
		this.assertEquals(roto.size, 3);
		this.assertEquals(roto.deviceName, "No Such Device");
		this.assertEquals(roto.glide, 1.0);
		this.assertEquals(roto.ctlNames, ["kick", "wave"]);
		this.assertEquals(roto.knob(1)[\set], ["colour", "mix"]);
		this.assert(roto.mix(0).isKindOf(REKnobMix) and: { roto.mix(1).isNil }, "a mix per ctl knob, none for a set knob");
		this.assertEquals(roto.mix(2).op, \scale);
		this.assertEquals(roto.mix(0).glide, 1.0, "the spec's glide on every mix");
		this.assertEquals(roto.mixes.size, 2);
	}

	test_spec_variants {
		var r = REShowRoto((knobs: (\3: (ctl: "a"), \4: (set: ["x", "y"]))));
		this.assertEquals(r.deviceName, "Roto-Control", "the default device");
		this.assertEquals(r.knob(3)[\name], "a", "knobs as an object keyed by cc");
		this.assertEquals(r.knob(3)[\op], \add);
		this.assertEquals([r.knob(3)[\lo], r.knob(3)[\hi]], [0.0, 1.0], "the default range");
		this.assertEquals(r.knob(4)[\set], ["x", "y"]);
		r = REShowRoto((knobs: [(cc: 1, ctl: "a", op: "scale"), (ctl: "b"), (cc: 2)]));
		this.assertEquals(r.size, 1, "a knob without a cc and one with neither ctl nor set are skipped");
		this.assertEquals(r.knob(1)[\op], \scale, "op as a string (JSON)");
		this.assert(this.logHas("without a cc") and: { this.logHas("neither ctl nor set") });
		r = REShowRoto(nil);
		this.assertEquals(r.size, 0, "no spec: no knobs");
	}

	test_states_by_thirds {
		this.assertEquals([0, 42, 43, 85, 86, 127].collect { |v| REShowRoto.stateOf(v) }, [1, 1, 2, 2, 3, 3]);
	}

	test_fine_position {
		this.assertFloatEquals(REShowRoto.positionOf(127 + (127 / 128)), 1.0, "the 14-bit top is 1");
		this.assertFloatEquals(REShowRoto.positionOf(0), 0.0);
		this.assertFloatEquals(REShowRoto.positionOf(64), 64 * 128 / 16383, "the MSB alone: 8192 / 16383");
		this.assertFloatEquals(REShowRoto.positionOf(127), 127 * 128 / 16383, "an MSB-only device tops at 0.992");
	}

	test_ctl_knob_mixes_and_sends {
		var out = roto.knobReceived(0, 0.75, 0);
		this.assertFloatEquals(out, 0.25, "no tape yet: the knob's offset alone, -0.5 + 0.75");
		this.assertEquals(sent.size, 1);
		this.assertEquals(sent[0][0..1], ["/fx/ctl", "wave"], "sent as /fx/ctl <name> <out> at once");
		this.assertFloatEquals(sent[0][2], 0.25);
		this.assertFloatEquals(roto.tapeValue("wave", 0.3, 1), 0.55, "a tape value: the mix's output for the chase to send");
		this.assertEquals(sent.size, 1, "tapeValue itself sends nothing (the chase does)");
		this.assertEquals(roto.tapeValue("other", 0.3, 1), nil, "a name no knob takes: nil, the entry goes as it is");
		this.assertEquals(roto.tapeValue("wave", "x", 1), nil, "a value that is no number: left alone");
		this.assertEquals(roto.knobReceived(9, 0.5, 1), nil, "an unmapped cc: nothing");
		this.assertEquals(sent.size, 1);
	}

	test_set_knob {
		var out = roto.knobReceived(1, 0.5, 0);
		this.assertFloatEquals(out, 0.5);
		this.assertEquals(sent.asArray, [["/fx/set", "colour", "mix", 0.5]], "a set knob: /fx/set <effect> <param> <lo + k·(hi - lo)>");
		this.assertEquals(roto.buttonReceived(1, 100, 0), nil, "a set knob has no states");
		this.assertEquals(sent.size, 1);
	}

	test_scale_knob {
		this.assertEquals(roto.knobReceived(2, 0.5, 0), nil, "scale without a tape: nothing");
		this.assertEquals(sent.size, 0, "...and nothing sent");
		this.assertFloatEquals(roto.tapeValue("kick", 0.8, 0), 0.8, "0.8 × (0 + 0.5 × 2)");
	}

	test_button_states_hold_and_tick {
		roto.knobReceived(0, 0.75, 0);        // +0.25
		roto.tapeValue("wave", 0.3, 1);       // T 0.3
		this.assertEquals(roto.buttonReceived(0, 60, 2), 2, "the middle third: state 2");
		this.assertEquals(roto.mix(0).state, 2);
		this.assertEquals(sent.size, 2, "a button press sends the output when there is one");
		this.assertFloatEquals(sent.last[2], 0.55);
		this.assertFloatEquals(roto.tapeValue("wave", 0.9, 3), 0.55, "the tape held at 0.3");
		this.assertEquals(roto.tick(3), 0, "nothing moves under a hold");
		this.assertEquals(roto.buttonReceived(0, 10, 4), 1, "the bottom third: state 1");
		this.assertEquals(roto.tick(4), 1, "the tape converges: sent while moving");
		this.assertFloatEquals(sent.last[2], 0.55, "at the start of the glide");
		roto.tick(4.5);
		this.assertFloatEquals(sent.last[2], 0.85, "half way from 0.3 to 0.9, plus 0.25");
		roto.tick(5);
		this.assertFloatEquals(sent.last[2], 1.15, "the live value at the end");
		this.assertEquals(roto.tick(5.1), 0, "then nothing");
		this.assertEquals(roto.buttonReceived(0, 127, 6), 3, "the top third: state 3, the knob held");
		roto.knobReceived(0, 0.0, 6.5);
		this.assertFloatEquals(sent.last[2], 1.15, "the knob held at +0.25: its move changes nothing yet");
		this.assertEquals(roto.buttonReceived(0, 0, 7), 1);
		roto.tick(7.5);
		this.assertFloatEquals(sent.last[2], 0.9 + 0.25 - 0.375, "half way from +0.25 to -0.5");
		roto.tick(8);
		this.assertFloatEquals(sent.last[2], 0.4);
	}

	test_status_lines {
		roto.knobReceived(0, 0.5, 0);
		this.assertEquals(roto.status.size, 3);
		this.assert(roto.status[0].contains("wave") and: { roto.status[0].contains("state 1") });
		this.assert(roto.status[1].contains("colour mix"));
	}

	// the MIDIdefs RCMidi makes for the mappings, driven as the device would send: an MSB then its LSB
	// on the knob's channel, a 7-bit value on the button's; no MIDI initialized
	test_mappings_through_rcmidi {
		var midi = roto.installMappings, defs = MIDIdef.all;
		var msb = { |cc, v| MIDIdef(("rc_roto_knob_" ++ cc).asSymbol).func.value(v, cc, 0, nil) };
		var lsb = { |cc, v| MIDIdef(("rc_roto_knob_" ++ cc ++ "_lsb").asSymbol).func.value(v, cc + 32, 0, nil) };
		var button = { |cc, v| MIDIdef(("rc_roto_button_" ++ cc).asSymbol).func.value(v, cc, 1, nil) };
		this.assert(midi.isKindOf(RCMidi));
		this.assertEquals(midi.names.asArray, [\button_0, \button_2, \knob_0, \knob_1, \knob_2], "a fine pair per knob, a button per ctl knob");
		this.assert(defs[\rc_roto_knob_0].notNil and: { defs[\rc_roto_knob_0_lsb].notNil } and: { defs[\rc_roto_button_0].notNil }, "the defs, named after the stand-in song roto");
		this.assert(defs[\rc_roto_button_1].isNil, "no button def for a set knob");
		this.assert(this.logHas("not found"), "the missing device is said (the mappings listen to all sources)");
		msb.(0, 127);
		this.assertEquals(sent.size, 0, "the MSB waits for its LSB");
		lsb.(0, 127);
		this.assertEquals(sent.size, 1, "the LSB completes the pair: one send");
		this.assertEquals(sent.last[0..1], ["/fx/ctl", "wave"]);
		this.assertFloatEquals(sent.last[2], 0.5, "the top of the range: -0.5 + 1.0");
		msb.(0, 0);
		lsb.(0, 0);
		this.assertFloatEquals(sent.last[2], -0.5, "the bottom");
		msb.(1, 64);
		lsb.(1, 0);
		this.assertEquals(sent.last[0..2], ["/fx/set", "colour", "mix"]);
		this.assertFloatEquals(sent.last[3], 64 * 128 / 16383);
		button.(0, 100);
		this.assertEquals(roto.mix(0).state, 3, "a button value in the top third: state 3");
		roto.stop;
		this.assert(MIDIdef.all[\rc_roto_knob_0].isNil, "stop frees the defs");
	}
}
