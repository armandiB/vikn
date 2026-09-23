// Octahedron: 6 points, 8 triangular faces. Stands in for a TDesign.
RCTestFakeDesign {
	var <triplets;
	*new { ^super.new.init }
	init {
		triplets = [[0, 2, 4], [0, 4, 3], [0, 3, 5], [0, 5, 2], [1, 2, 4], [1, 4, 3], [1, 3, 5], [1, 5, 2]];
	}
	size { ^6 }
	calcTriplets { ^this }
	directions { ^6.collect { |i| [i * 1.0, 0] } }
}

TestRCPathControl : UnitTest {
	var clock, song, savedRateLimit;

	setUp {
		RCSession.reset;
		clock = TempoClock.new(20);
		RCSession.boot(Server.default, clock, oscPort: nil, initMidi: false);
		song = RCSong(\pc, 1994);
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown {
		RCSession.reset;
		clock.stop;
		RCLog.rateLimit = savedRateLimit;
	}

	test_generatePath {
		var design = RCTestFakeDesign.new;
		var path = RCSpherePath.generatePath(design, 0, seed: 3);
		var again = RCSpherePath.generatePath(design, 0, seed: 3);
		this.assertEquals(path.size, 6, "full path over the octahedron");
		this.assertEquals(path[0], 0, "starts at the start point");
		this.assertEquals(path.asSet.size, 6, "no point twice");
		this.assert(path.doAdjacentPairs { |a, b| }.isNil or: { (0..4).every { |i| design.triplets.any { |t| t.includes(path[i]) and: { t.includes(path[i + 1]) } } } }, "consecutive points share a face");
		this.assertEquals(path, again, "seeded → reproducible");
		this.assertEquals(RCSpherePath.generatePath(design, 0, seed: 1, pathSize: 3).size, 3, "requested size");
	}

	test_disjointTriplets {
		var design = RCTestFakeDesign.new;
		var res = RCSpherePath.disjointTriplets(design, seed: 5);
		this.assertEquals(res.size, 2, "two disjoint faces cover the octahedron");
		this.assertEquals(res.flatten.asSet.size, 6, "no shared point");
	}

	test_thueMorse {
		this.assertEquals((0..7).collect { |i| RCSpherePath.thueMorse(i, 2) }, [0, 1, 1, 0, 1, 0, 0, 1], "Thue–Morse base 2");
		this.assertEquals(RCSpherePath.thueMorse(4, 3), 2, "base 3 (4 = 11 in base 3)");
	}

	test_distribution_over_batch {
		var lib = RCSubseqLibrary.newFrom((percs: (kick: ('fourfour': [\fourfour, 0, [1, 1, 1, 1]]))));
		var rd = RCRhythmDict(lib);
		var design = RCTestFakeDesign.new;
		var tpl = RCOrgnsm(\voice, 0, 0, song);
		var batch, pc, control, stream, ev, seqs;
		rd.at(\voices)[\basic] = [[1, 0, "percs.kick.fourfour", true, (who: \k)]];
		tpl.addStaticAttrs((seed: 1, dur_params: [1, 1], other_params_key_list: [], seq_list: [], quant: [1, 0]));
		tpl.addFirstArrayBase = [compute_seq_params: RCOrgnsmPatterns.seqParams(true)];
		tpl.attrDictBase = [type: \rest, dur_flex: Pfunc { |ev| ev.compute_seq_params.dereference[\dur] }];
		batch = RCBatch(\voices, tpl, layerKey: \core);
		3.do { |i| batch.addCreate(i) };
		batch.startPrepared;

		pc = RCPathControl(song, design, rd, \voices, \basic, batch);
		pc.rPut("dur_params", [4, 1]);
		pc.rPut("orgnsm_series_pattern", { |self| Pseq([0, 1, 2, 1], inf) });   // hit i → orgnsm key
		control = pc.create(layerKey: \core);
		this.assert(control.isKindOf(RCPathControl), "clone keeps the class");
		this.assertEquals(control.name, 'orgnsm_path_control_t0_0', "path control identity");

		stream = RCBeat(song.layer(\core), control.name, control.attrDict, seeds: 1, addFirst: control.addFirstArray, addFirstSeeds: 1).asStream;
		ev = stream.next(Event.default);
		this.assert(ev.type == \rest and: { ev.dur.value == 4 }, "the control beat rests for one loop");
		this.assertEquals(ev.other_params_key_list, [\who], "param keys collected");
		seqs = ev.seq_list_by_orgnsm_dict;
		this.assertEquals(seqs.keys.asArray.sort, [0, 1, 2], "every visited orgnsm gets a seq");
		if(seqs.keys.asArray.sort != [0, 1, 2]) { ^this };   // avoid nil errors below
		this.assertEquals(seqs[0][0].shift, 0, "orgnsm 0 starts at the loop start");
		this.assertEquals(seqs[0][0].durs.collect(_.value), [1], "one hit for orgnsm 0");
		this.assertEquals(seqs[1][0].shift, 1, "orgnsm 1 shifted by one beat");
		this.assertEquals(seqs[1][0].durs.collect(_.value), [1, 1, 1], "hit, rest, hit for orgnsm 1");
		this.assert(seqs[1][0].durs[1].isRest, "the gap is a rest");
		this.assertEquals(seqs[1][0].params[\who].collect { |x| x.isKindOf(Rest) }, [false, true, false], "params rest with the gap");
		this.assertEquals(seqs[2][0].shift, 2, "orgnsm 2 shifted by two beats");
		this.assertEquals(batch.orgnsms(1)[0].staticAttrs.seq_list, seqs[1], "seq_list pushed into the batch");
		this.assertEquals(batch.orgnsms(1)[0].staticAttrs.dur_params, [4, 1], "dur_params pushed into the batch");
		this.assertEquals(batch.orgnsms(1)[0].staticAttrs.other_params_key_list, [\who], "key list pushed into the batch");
		this.assert(batch.orgnsms(1)[0].beat.keyProxy(\who).notNil, "new param key added to the running beats");
		batch.free;
	}

	test_missing_batch_and_rhythm_are_reported {
		var design = RCTestFakeDesign.new;
		var pc = RCPathControl(song, design, RCRhythmDict(RCSubseqLibrary.new), \nope, \nope, nil);
		var control = pc.create(layerKey: \core);
		var stream = RCBeat(song.layer(\core), control.name, control.attrDict, addFirst: control.addFirstArray).asStream;
		var ev = stream.next(Event.default);
		this.assert(ev.notNil and: { ev.type == \rest }, "control beat survives");
		this.assertEquals(ev.seqs_info, [], "missing rhythm → no subseqs");
		this.assert(RCLog.history.any { |e| e[2].contains("no controlled_batch") }, "missing batch reported");
	}
}
