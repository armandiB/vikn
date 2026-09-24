TestRCRhythmData : UnitTest {
	var savedRateLimit;

	setUp {
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown { RCLog.rateLimit = savedRateLimit }

	library {
		^RCSubseqLibrary.newFrom((
			percs: (
				kick: ('fourfour': [\fourfour, 0, [1, 1, 1, 1]]),
				hh: ('fourfour': [\fourfour, 0.5, [1, 1, 1, 1]], 'short': [\short, 0, [0.5, 0.5]]),
				snare: ('twos': [\twos, 1, [2, 2], (accent: 1), [\accent]])
			),
			pattern: (any: ('x': [\x, 0, Pseq([1, 1], 1)]))
		))
	}

	test_subseq_value_object {
		var a = RCSubseq(1, 0.5, [1, 2], (k: [1, 2]), [true, false], "n", [\k], 1);
		var b = RCSubseq(1, 0.5, [1, 2], (k: [1, 2]), [true, false], "n", [\k], 1);
		var c = a.deepCopy;
		this.assert(a == b, "structural equality");
		this.assertEquals(a.hash, b.hash, "equal hash");
		this.assert(Bag[a, b].contents.size == 1, "bags merge equal subseqs");
		this.assertEquals(a[1], 0.5, "index access (shift)");
		this.assertEquals(a[5], "n", "index access (name)");
		a[4] = [false, false];
		this.assertEquals(a.mask, [false, false], "index put");
		this.assert(a != b, "changed → different");
		c.durs[0] = 9;
		this.assertEquals(b.durs[0], 1, "deepCopy independent");
		this.assertEquals(RCSubseq.fromArray([2, 1, [1]]).priority, 2, "fromArray short");
		this.assertEquals(RCSubseq.fromArray([2, 1, [1]]).params, (), "params default");
	}

	test_library {
		var lib = this.library;
		this.assertEquals(lib.at("percs.kick.fourfour")[2], [1, 1, 1, 1], "dotted lookup");
		this.assertEquals(lib.at("percs.kick.fourfour").size, 4, "3-element entries padded with params");
		this.assertEquals(lib.at("percs.snare.twos")[4], [\accent], "keysIgnoreOrder kept");
		this.assertEquals(lib.at("percs.nope.x"), nil, "unknown → nil + error");
		this.assertEquals(lib.sizeOf("percs.hh.short"), 2, "sizeOf");
		this.assertEquals(lib.sizeOf("pattern.any.x"), nil, "sizeOf a pattern seq is nil");
		this.assertEquals(lib.size, 5, "size is the number of subseqs (Object:size stays valid)");
		this.assert(lib.includes("percs.hh.short") and: { lib.includes("zz").not }, "includes");
		this.assertEquals(lib.names.size, 5, "names enumerated");
		lib.put(\extra, (a: ('1': [\a, 0, [1]])));
		this.assert(lib.includes("extra.a.1"), "category added later");
	}

	test_rhythmDict_resolution {
		var lib = this.library;
		var rd = RCRhythmDict(lib);
		var subseqs, s;
		rd.at(\drums)[\basic] = [
			[1, 0, "percs.kick.fourfour"],
			[2, 0.25, "percs.hh.fourfour", [true, false, true, false], (vel: 0.5), nil, nil, 2],
			[1, 0, "percs.snare.twos", true, (accent: 3, extra: Pn(7)), [\extra]],
			[1, 0, "percs.does.not.exist"]
		];
		subseqs = rd.subseqs(\drums, \basic);
		this.assertEquals(subseqs.size, 3, "unknown library name skipped");
		s = subseqs[0];
		this.assertEquals(s.name, "percs.kick.fourfour", "name is the dotted path");
		this.assertEquals(s.mask, [true, true, true, true], "mask true expanded per hit");
		this.assertEquals(s.params, (), "no params");
		s = subseqs[1];
		this.assertEquals(s.shift, 1.5, "(library shift + entry shift) * timeMult");
		this.assertEquals(s.durs, [2, 2, 2, 2], "durs scaled by timeMult");
		this.assertEquals(s.params[\vel], [0.5, 0.5, 0.5, 0.5], "scalar param expanded per hit");
		this.assertEquals(s.mask, [true, false, true, false], "explicit mask kept");
		this.assertEquals(s.timeMult, 2, "timeMult recorded");
		s = subseqs[2];
		this.assertEquals(s.params[\accent], [3, 3], "entry param overrides the library default");
		this.assert(s.params[\extra].isKindOf(Pattern), "pattern params kept");
		this.assertEquals(s.keysIgnoreOrder, [\extra], "library keysIgnoreOrder dropped when overridden, entry ones kept");
		this.assertEquals(lib.at("percs.snare.twos")[3], (accent: 1), "library params never mutated");
		this.assertEquals(rd.subseqs(\drums, \nope), [], "missing entry → empty + error");
		this.assertEquals(rd.clone.subseqs(\drums, \basic).size, 3, "clone keeps entries");
	}

	test_seqParamsForLoop_merges_subseqs {
		var lib = this.library;
		var rd = RCRhythmDict(lib);
		var st, stream, out, deltas, whos;
		rd.at(\d)[\k] = [[1, 0, "percs.kick.fourfour", true, (who: \k)], [1, 0, "percs.hh.fourfour", true, (who: \h)]];
		st = (dur_params: [4, 1], seq_list: rd.subseqs(\d, \k), other_params_key_list: [\who]);
		stream = RCOrgnsmPatterns.seqParamsForLoop(false, st, 0, false).asStream;
		out = List.new;
		RCGuard.boundedLoop(100, \test, { var v = stream.next(()); v !? { out.add(v) }; v.notNil }, {});
		deltas = out.collect { |v| v[0].value };
		whos = out.collect { |v| v[1] };
		this.assertEquals(out.size, 8, "eight hits in the loop, no zero-length artifacts");
		this.assertEquals(deltas, 0.5 ! 8, "interleaved kick/hh at half beats");
		this.assertEquals(whos, [\k, \h, \k, \h, \k, \h, \k, \h], "params follow their subseq");
		this.assertEquals(deltas.sum, 4, "exactly one loop");
		this.assert(out[0][0].isRest.not, "the hit at loop start keeps its length (proto gave it dur 0)");
	}

	test_seqParamsForLoop_partial_loop_is_padded {
		var lib = this.library;
		var rd = RCRhythmDict(lib);
		var st, stream, out = List.new;
		rd.at(\d)[\p] = [[1, 1, "percs.hh.short"]];   // shift 1, hits at 1 and 1.5
		st = (dur_params: [4, 1], seq_list: rd.subseqs(\d, \p), other_params_key_list: []);
		stream = RCOrgnsmPatterns.seqParamsForLoop(false, st, 0, false).asStream;
		RCGuard.boundedLoop(100, \test, { var v = stream.next(()); v !? { out.add(v) }; v.notNil }, {});
		this.assert(out[0][0].isRest and: { out[0][0].value == 1 }, "leading rest until the first hit");
		this.assertEquals(out.collect { |v| v[0].value }.sum, 4, "padded to the loop length");
		this.assert(out.last[0].isRest, "trailing rest fills the loop");
	}

	test_seqParamsForLoop_padding_rests_params {
		// the silent events prMerge adds (leading gap) carry Rest() for every key, never nil
		var st = (dur_params: [4, 1], seq_list: [[1, 1, [1], (who: [\a]), true]], other_params_key_list: [\who]);
		var stream = RCOrgnsmPatterns.seqParamsForLoop(false, st, 0, false).asStream;
		var out = List.new, v;
		RCGuard.boundedLoop(100, \test, { var x = stream.next(()); x !? { out.add(x) }; x.notNil }, {});
		this.assertEquals(out.collect { |x| x[0].value }, [1, 1, 2], "gap, hit, tail");
		this.assert(out[0][0].isRest and: { out[0][1].isRest }, "the leading gap rests its params");
		this.assertEquals(out[1][1], \a, "the hit keeps its param");
		this.assert(out[2][0].isRest and: { out[2][1].isRest }, "the tail rests its params");
		v = RCOrgnsmPatterns.seqParamsForLoop(false, st, 0, true).asStream.next(()).dereference;
		this.assert(v[\dur].isRest and: { v[\who].isRest }, "dict form: the leading gap rests its params");
	}

	test_seqParamsForLoop_raw_array_subseq {
		// a seq_list written by hand, as in WeMadeATrack: [priority, shift, durs, params, mask]
		var st = (dur_params: [4, 1], seq_list: [[1, 0, [1, 1, 1, 1], (who: [\a, \b, \c, \d]), true], [1, 2.5, [1, 1], (who: [\e, \f])]], other_params_key_list: [\who]);
		var stream = RCOrgnsmPatterns.seqParamsForLoop(false, st, 0, false).asStream;
		var out = List.new;
		RCGuard.boundedLoop(100, \test, { var v = stream.next(()); v !? { out.add(v) }; v.notNil }, {});
		this.assertEquals(out.collect { |v| v[0].value }, [1, 1, 0.5, 0.5, 0.5, 0.5], "raw arrays fill the loop, a Boolean or missing mask means every hit");
		this.assertEquals(out.collect { |v| v[1] }, [\a, \b, \c, \e, \d, \f], "params follow their subseq");
	}

	pullAll { |stream|
		var out = List.new;
		RCGuard.boundedLoop(10000, \test, { var v = stream.next(()); v !? { out.add(v) }; v.notNil }, {});
		^out
	}

	test_seqParamsForLoop_two_params_one_ignoring_order {
		var st = (dur_params: [4, 1], seq_list: [[1, 0, [1, 1], (a: [\x, \y], b: Pseq([7], inf)), true, \n, [\b]]], other_params_key_list: [\a, \b]);
		var out = this.pullAll(RCOrgnsmPatterns.seqParamsForLoop(false, st, 0, false).asStream);
		var hits = out.reject { |v| v[0].isRest };
		this.assertEquals(hits.collect { |v| v[1] }, [\x, \y], "the array param follows the hits");
		this.assertEquals(hits.collect { |v| v[2] }, [7, 7], "the pattern param listed in keysIgnoreOrder streams alongside");
	}

	test_seqParamsForLoop_rejects_zero_time_mult {
		var st = (dur_params: [4, 0], seq_list: [[1, 0, [1, 1], (), true]], other_params_key_list: []);
		var v = RCOrgnsmPatterns.seqParamsForLoop(false, st, 0, false).asStream.next(());
		this.assert(v[0].isRest and: { v[0].value == 1 }, "a zero time mult rests one beat");
		this.assert(RCLog.history.any { |e| e[2].contains("dur_params") }, "reported");
	}

	test_prMerge_caps_zero_duration_patterns {
		var saved = RCOrgnsmPatterns.maxEventsPerLoop;
		var st = (dur_params: [4, 1], seq_list: [[1, 0, Pn(0), (), true]], other_params_key_list: []);
		var out;
		RCOrgnsmPatterns.maxEventsPerLoop = 50;
		out = this.pullAll(RCOrgnsmPatterns.seqParamsForLoop(false, st, 0, false).asStream);
		this.assert(out.size <= 52, "a zero-duration pattern is cut at the cap (%)".format(out.size));
		this.assert(RCLog.history.any { |e| e[2].contains("events in one loop") }, "reported");
		RCOrgnsmPatterns.maxEventsPerLoop = saved;
	}

	test_rhythmDict_reports_param_length_mismatch {
		var rd = RCRhythmDict(this.library);
		rd.at(\d)[\bad] = [[1, 0, "percs.kick.fourfour", true, (acc: [1, 2])]];
		rd.subseqs(\d, \bad);
		this.assert(RCLog.history.any { |e| e[2].contains("2 values for 4 hits") }, "array params must match the hit count");
	}

	test_seqParamsForLoop_dict_form_and_empty {
		var st = (dur_params: [2, 1], seq_list: [], other_params_key_list: [\a]);
		var v = RCOrgnsmPatterns.seqParamsForLoop(false, st, 0, true).asStream.next(());
		this.assert(v.isKindOf(Ref), "dict form is Ref'd");
		this.assert(v.dereference[\dur].isRest and: { v.dereference[\dur].value == 2 }, "empty seq list → rest of the loop");
		this.assert(v.dereference[\a].isRest, "params rest too");
		v = RCOrgnsmPatterns.seqParamsForLoop(false, (dur_params: nil), 0, true).asStream.next(());
		this.assert(v.dereference[\dur].isRest and: { v.dereference[\dur].value == 1 }, "invalid dur_params → 1-beat rest, no crash");
	}

	test_seqParams_initial_shift {
		var lib = this.library;
		var rd = RCRhythmDict(lib);
		var o = RCSubseqLibrary; // placeholder to keep var list simple
		var fake, stream, first, second;
		rd.at(\d)[\s] = [[1, 0, "percs.snare.twos"]];
		fake = (staticAttrs: (dur_params: [4, 1], seq_list: rd.subseqs(\d, \s), other_params_key_list: [\accent]));
		stream = RCOrgnsmPatterns.seqParams(false).asStream;
		first = stream.next((self: fake));
		second = stream.next((self: fake));
		this.assert(first[0].isRest and: { first[0].value == 1 }, "initial rest equals the smallest shift");
		this.assertEquals(second[0].value, 2, "then the first hit of the loop");
		this.assertEquals(second[1], 1, "accent from the library default");
	}

	test_euclidDurAmp {
		var fake = (staticAttrs: (dur_params: [4, 8], amp_params: [[3, 0, 1], [2, 1, 0.5]]));
		var stream = RCOrgnsmPatterns.euclidDurAmp(false).asStream;
		var out = 6.collect { stream.next((self: fake)) };
		// E(3,8) ∪ E(2,8) shifted by 1 = [1,1,0,1,0,1,1,0]: five hits per 4-beat loop
		this.assertEquals(out.collect { |p| p[0].value }[..4], [0.5, 1, 1, 0.5, 1], "five hits cover the 4-beat loop");
		this.assertEquals(out[5][0].value, 0.5, "then the loop repeats");
		this.assert(out.every { |p| p[1] > 0 }, "amps combined");
	}

	test_loop_helper_does_not_spin {
		var stream = RCOrgnsmPatterns.loop({ Pseq([], 1) }).asStream;
		var v = stream.next(());
		this.assert(v.isRest and: { v.value == 1 }, "empty body → 1-beat rest");
		stream = RCOrgnsmPatterns.loop({ nil.explode }).asStream;
		this.assert(stream.next(()).isRest, "error in body → rest, no crash");
	}

	test_loop_helper_key_gates_like_Pn {
		// the gated key must come after the loop key: Pn-style keys are visible to later keys of the same event
		var pat = Pbind(\x, RCOrgnsmPatterns.loop({ Pseq([1, 2], 1) }, \t, \advance), \seed, Pgate(Pseries(0, 1), inf, \advance));
		var s = pat.asStream;
		var seeds = 6.collect { s.next(()).seed };
		this.assertEquals(seeds, [0, 0, 1, 1, 2, 2], "the gate advances once per loop of the body");
	}
}
