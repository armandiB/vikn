// RCLineControl: lines generated per cycle, allocated to a batch's voices and written as
// their seq_lists; the voices' streams read them back as notes with pitch0 / pitch1 / sustain.
TestRCLineControl : UnitTest {
	var clock, song, savedRateLimit;

	setUp {
		clock = RCTestSupport.clock;
		RCTestSupport.bootSession;
		song = RCSong(\lc, 1994);
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown {
		RCTestSupport.reset;
		RCLog.rateLimit = savedRateLimit;
	}

	// a batch of n voices reading their seq lists (pitch0 / pitch1 / sustain declared by the
	// template, az0 / az1 not), and a control on it with `generator`
	makeRig { |n = 3, generator, startAll = true|
		var tpl = RCOrgnsm(\voice, 0, 0, song);
		var batch, lc;
		tpl.addStaticAttrs((seed: 1, dur_params: [8, 1], other_params_key_list: [\pitch0, \pitch1, \sustain], seq_list: [], quant: [8, 0], initial_beg_shift: 0));
		tpl.addFirstArrayBase = [compute_seq_params: RCOrgnsmPatterns.seqParams(true)];
		tpl.attrDictBase = [
			type: \note,
			dur_flex: Pfunc { |ev| ev.compute_seq_params.dereference[\dur] },
			pitch0: Pfunc { |ev| var v = ev.compute_seq_params.dereference[\pitch0]; if(v.isNumber) { v } { -1 } },
			pitch1: Pfunc { |ev| var v = ev.compute_seq_params.dereference[\pitch1]; if(v.isNumber) { v } { -1 } },
			sustain: Pfunc { |ev| var v = ev.compute_seq_params.dereference[\sustain]; if(v.isNumber) { v } { 0.1 } }
		];
		batch = RCBatch(\voices, tpl, layerKey: \core);
		n.do { |i| batch.addCreate(i) };
		if(startAll) {
			batch.startPrepared;
			batch.allOrgnsms.values.flatten.do { |o| o.beat.stop };   // the voices' streams are pulled by hand
		};
		lc = RCLineControl(song, generator, batch);
		^(batch: batch, lc: lc)
	}

	controlStream { |control|
		^RCBeat(song.layer(\core), control.name, control.attrDict, seeds: 1, addFirst: control.addFirstArray, addFirstSeeds: 1).asStream
	}

	// the note events of a voice's stream over `beats`: [time, event]
	pullVoice { |o, beats = 8|
		var stream = o.beat.pattern.asStream, time = 0, ev, res = List.new;
		while { time < beats and: { (ev = stream.next(Event.default)).notNil } } {
			if(ev.isRest.not) { res.add([time, ev]) };
			time = time + ev.delta.value;
		};
		^res.asArray
	}

	test_distribution_over_batch {
		var lines = [
			RCLines.line(0, 4, (pitch: 0), (pitch: 1), \a),
			RCLines.line(2, 4, (pitch: 1, az: 0.5), (pitch: 0, az: 1), \b),
			RCLines.line(5, 2, (pitch: 2), (pitch: 2), \c)
		];
		var calls = List.new;
		var rig = this.makeRig(2, { |control, index, beats| calls.add([index, beats]); lines });
		var control = rig[\lc].create(layerKey: \core);
		var stream = this.controlStream(control);
		var ev, seqs, notes;
		control.rPut("line_defaults", (az: 0));
		this.assert(control.isKindOf(RCLineControl), "clone keeps the class");
		this.assertEquals(control.name, 'orgnsm_line_control_t0_0', "line control identity");
		ev = stream.next(Event.default);
		this.assert(ev.type == \rest and: { ev.dur.value == 8 }, "the control beat rests for one cycle");
		this.assertEquals(calls.asArray, [[0, 8]], "the generator was asked once, for cycle 0 of 8 beats");
		this.assertEquals(ev[\other_params_key_list], [\az0, \az1, \pitch0, \pitch1, \sustain, \line_id], "the param keys of the lines' coordinates (az from the defaults)");
		seqs = ev[\seq_list_by_orgnsm_dict];
		this.assertEquals(seqs.keys.asArray.sort, [0, 1], "both voices get a seq");
		if(seqs.keys.asArray.sort != [0, 1]) { ^this };
		this.assertEquals(seqs[0][0].shift, 0, "voice 0 starts at the cycle start");
		this.assertEquals(seqs[0][0].durs, [5, 2], "voice 0: line a until c starts, then c (free allocation: c goes to the voice free the longest)");
		this.assertEquals(seqs[0][0].params[\pitch1], [1, 2], "pitch ends");
		this.assertEquals(seqs[0][0].params[\sustain], [4, 2], "sustains: the lines' lengths");
		this.assertEquals(seqs[0][0].params[\az0], [0, 0], "a coordinate the lines lack takes the default on both ends");
		this.assertEquals(seqs[1][0].shift, 2, "voice 1 starts when b starts");
		this.assertEquals(seqs[1][0].params[\az1], [1], "b's own az");
		this.assertEquals(seqs[1][0].params[\line_id], [\b], "ids kept");
		this.assertEquals(control.staticAttrs[\cycle_index], 1, "one cycle generated");
		this.assertEquals(control.staticAttrs[\last_lines].size, 3, "the cycle's lines kept");
		this.assertEquals(control.staticAttrs[\dropped], 0, "nothing dropped");
		this.assertEquals(rig[\batch].orgnsms(0)[0].staticAttrs.seq_list, seqs[0], "seq_list pushed into the batch");
		this.assertEquals(rig[\batch].orgnsms(0)[0].staticAttrs.dur_params, [8, 1], "dur_params pushed into the batch");
		this.assertEquals(rig[\batch].orgnsms(0)[0].staticAttrs.other_params_key_list, ev[\other_params_key_list], "key list pushed into the batch");
		this.assert(rig[\batch].orgnsms(0)[0].beat.keyProxy(\az0).notNil, "a key the template does not declare is added to the running beats");
		// the voices play the lines back: notes at the onsets with the ends and the sustain
		notes = this.pullVoice(rig[\batch].orgnsms(0)[0]);
		this.assertEquals(notes.collect(_[0]), [0, 5], "voice 0 plays at the onsets");
		this.assertEquals(notes.collect { |n| [n[1][\pitch0], n[1][\pitch1], n[1][\sustain]] }, [[0, 1, 4], [2, 2, 2]], "with the lines' ends and lengths");
		this.assertEquals(notes.collect { |n| n[1][\az1] }, [0, 0], "and the default az");
		notes = this.pullVoice(rig[\batch].orgnsms(1)[0]);
		this.assertEquals(notes.collect(_[0]), [2], "voice 1 plays b");
		this.assertEquals(notes[0][1][\az1], 1, "with b's az");
		ev = stream.next(Event.default);
		this.assertEquals(calls.size, 2, "the next cycle asks the generator again");
		this.assertEquals(calls[1], [1, 8], "for cycle 1");
		rig[\batch].free;
	}

	test_validation_and_silence {
		var bad = [RCLines.line(-1, 2, (pitch: 0), (pitch: 0)), RCLines.line(9, 2, (pitch: 0), (pitch: 0)), RCLines.line(1, 0, (pitch: 0), (pitch: 0)), RCLines.line(1, 1, (pitch: 0), (pitch: 0))];
		var rig = this.makeRig(2, { |control, index| if(index == 0) { bad } { nil } });
		var control = rig[\lc].create(layerKey: \core);
		var stream = this.controlStream(control);
		var ev = stream.next(Event.default);
		this.assertEquals(ev[\lines].size, 1, "lines outside the cycle or without a positive dur are dropped");
		this.assert(RCLog.history.any { |e| e[1] == \warn and: { e[2].contains("3 line(s) dropped") } }, "and reported");
		ev = stream.next(Event.default);
		this.assertEquals(ev[\lines], [], "a nil result is a silent cycle");
		this.assertEquals(rig[\batch].orgnsms(0)[0].staticAttrs.seq_list, [], "the voices get empty seq lists");
		this.assertEquals(this.pullVoice(rig[\batch].orgnsms(0)[0]).size, 0, "and play nothing");
		rig[\batch].free;
	}

	test_strict_allocation_and_voice_keys {
		var lines = 4.collect { |i| RCLines.line(i * 0.5, 4, (pitch: i), (pitch: i)) };
		var rig = this.makeRig(3, { lines });
		var control, stream, ev, seqs;
		rig[\lc].rPut("allocation", \strict);
		rig[\lc].rPut("voice_keys", [2, 0]);   // only two voices, in this order
		control = rig[\lc].create(layerKey: \core);
		stream = this.controlStream(control);
		ev = stream.next(Event.default);
		seqs = ev[\seq_list_by_orgnsm_dict];
		this.assertEquals(seqs.keys.asArray.sort, [0, 2], "only the named voices");
		this.assertEquals(seqs[2][0].params[\pitch0], [0], "voice 2 (first named) takes the first line");
		this.assertEquals(seqs[0][0].params[\pitch0], [1], "voice 0 the second");
		this.assertEquals(control.staticAttrs[\dropped], 2, "strict: the two lines finding no free voice are dropped");
		this.assertEquals(rig[\batch].orgnsms(1)[0].staticAttrs.seq_list, [], "the voice left out rests");
		rig[\batch].free;
	}

	test_generator_error_is_guarded {
		var rig = this.makeRig(1, { |control, index| if(index == 0) { Error("boom").throw } { [RCLines.line(0, 1, (pitch: 0), (pitch: 0))] } });
		var control = rig[\lc].create(layerKey: \core);
		var stream = this.controlStream(control);
		var ev = stream.next(Event.default);
		this.assertEquals(ev[\lines], [], "a failing generator gives a silent cycle");
		this.assert(RCLog.history.any { |e| e[1] == \error }, "and is reported");
		ev = stream.next(Event.default);
		this.assertEquals(ev[\lines].size, 1, "the control goes on");
		rig[\batch].free;
	}

	test_reserveKeysIn {
		var rig = this.makeRig(2, { [] });
		var control = rig[\lc].create(layerKey: \core);
		var keys = control.reserveKeysIn([\az]);
		this.assertEquals(keys, [\az0, \az1, \sustain, \line_id], "the param keys of the coordinates");
		this.assert(rig[\batch].orgnsms(0)[0].beat.keyProxy(\az0).notNil, "declared on the beats");
		rig[\batch].free;
	}
}
