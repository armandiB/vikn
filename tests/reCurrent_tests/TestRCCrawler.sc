TestRCCrawler : UnitTest {
	var clock, song, savedRateLimit, lib, rd;

	setUp {
		clock = RCTestSupport.clock;
		RCTestSupport.bootSession;
		song = RCSong(\cr, 1994);
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
		thisThread.randSeed = 7;
		lib = RCSubseqLibrary.newFrom((percs: (
			kick: ('fourfour': [\fourfour, 0, [1, 1, 1, 1]]),
			hh: ('short': [\short, 0, [0.5, 0.5]], 'fourfour': [\fourfour, 0, [1, 1, 1, 1]]),
			snare: ('twos': [\twos, 1, [2, 2]])
		)));
		rd = RCRhythmDict(lib);
	}

	tearDown {
		RCTestSupport.reset;
		RCLog.rateLimit = savedRateLimit;
	}

	// a crawler sitting on an orgnsm of a batch, with a target rhythm dict
	makeContext { |currentEntries, targetEntries, staticAttrs|
		var tpl = RCOrgnsm(\v, 0, 0, song);
		var batch, o, crawler, target, st;
		rd.at(\b)[\k] = currentEntries;
		target = RCRhythmDict(lib);
		target.at(\b)[\k] = targetEntries;
		tpl.addStaticAttrs((seed: 1, dur_params: [4, 1], seq_list: rd.subseqs(\b, \k), quant: [1, 0]));
		tpl.attrDictBase = [type: \rest, dur_flex: 1];
		batch = RCBatch(\b, tpl, layerKey: \core);
		batch.addCreate(\k, start: true);
		crawler = RCCrawler(["static_attrs.seq_list"]);
		crawler.initFromBatch(batch, \k);
		st = (rhythm_dict_target: target, tolerance_change_subseq: 10, matching_distance_func: { |a, b| (a.size - b.size).abs },
			priority_matching_func: { |t, c| true }, error_probability_shift_converge: 0, error_probability_mask_converge: 0,
			error_probability_mask_decrease: 0, keep_other_params_not_in_new_seq: false);
		staticAttrs !? { st.putAll(staticAttrs) };
		^(crawler: crawler, st: st, batch: batch)
	}

	test_shift_move {
		var c = this.makeContext([[1, 0, "percs.kick.fourfour"]], [[1, 2, "percs.kick.fourfour"]]);
		var res = RCCrawlerMoves.computeNext(c.crawler, c.st);
		this.assertEquals(res.size, 1, "one subseq");
		this.assertEquals(res[0].shift, 1, "shift converges by one step towards the target");
		this.assertEquals(c.crawler.prevVal[0][0].shift, 0, "current seq untouched (deep copy)");
		c.batch.free;
	}

	test_change_subseq_move {
		var c = this.makeContext([[1, 0, "percs.kick.fourfour"]], [[1, 0, "percs.hh.short"]]);
		var res = RCCrawlerMoves.computeNext(c.crawler, c.st);
		this.assertEquals(res[0].name, "percs.hh.short", "unmatched subseq swapped for the target within tolerance");
		this.assertEquals(res[0].mask.size, 2, "mask fitted to the new subseq");
		c.batch.free;
	}

	test_change_subseq_respects_tolerance {
		var c = this.makeContext([[1, 0, "percs.kick.fourfour"]], [[1, 0, "percs.hh.short"]], (tolerance_change_subseq: 0));
		var res = RCCrawlerMoves.computeNext(c.crawler, c.st);
		this.assertEquals(res[0].name, "percs.kick.fourfour", "no swap beyond the tolerance");
		this.assertEquals(res[0].mask.count { |b| b }, 3, "falls through to decreaseMask: one hit masked out");
		c.batch.free;
	}

	test_converge_mask_move {
		var c = this.makeContext([[1, 0, "percs.kick.fourfour", [true, true, true, true]]], [[1, 0, "percs.kick.fourfour", [true, false, false, true]]]);
		var res = RCCrawlerMoves.computeNext(c.crawler, c.st);
		this.assertEquals(res[0].mask.count { |b| b }, 3, "one bit flipped towards the target mask");
		this.assert(res[0].mask[0] and: { res[0].mask[3] }, "only differing bits change");
		c.batch.free;
	}

	test_switch_other_params_move {
		var c = this.makeContext([[1, 0, "percs.kick.fourfour", true, (vel: 1)]], [[1, 0, "percs.kick.fourfour", true, (vel: 2)]]);
		var res = RCCrawlerMoves.computeNext(c.crawler, c.st);
		this.assertEquals(res[0].params[\vel], [2, 2, 2, 2], "params replaced by the target's");
		c.batch.free;
	}

	test_no_move_returns_current {
		var c = this.makeContext([[1, 0, "percs.kick.fourfour"]], [[1, 0, "percs.kick.fourfour"]]);
		var res = RCCrawlerMoves.computeNext(c.crawler, c.st);
		this.assertEquals(res, c.crawler.prevVal[0], "already matching → unchanged");
		c.batch.free;
	}

	test_error_reported_not_thrown {
		var c = this.makeContext([[1, 0, "percs.kick.fourfour"]], [[1, 0, "percs.kick.fourfour"]], (priority_matching_func: { nil.explode }));
		var pat = RCCrawlerMoves.nextValRhythmChange;
		var fake = (self: (parentObject: c.crawler, staticAttrs: c.st));
		var res = pat.asStream.next(fake);
		this.assertEquals(res, [c.crawler.prevVal[0]], "a failing move function → current seqs");
		this.assert(RCLog.history.any { |e| e[2].contains("explode") }, "reported");
		c.batch.free;
	}

	test_crawler_jump_and_pattern {
		var tpl = RCOrgnsm(\w, 0, 0, song);
		var batch, crawler, seen;
		tpl.addStaticAttrs((seed: 1, quant: [1, 0], width: 0));
		tpl.attrDictBase = [type: \rest, dur_flex: 1];
		batch = RCBatch(\w, tpl, layerKey: \core);
		batch.addCreate(0, start: true);
		batch.addCreate(1, start: true);
		crawler = RCCrawler(["width"]);
		crawler.initFromBatch(batch, 0, val: [5]);
		this.assertEquals(batch.orgnsms(0)[0].staticAttrs.width, 5, "init applies the value");
		crawler.leaveVal = [0];
		crawler.nextOrgnsm = batch.orgnsms(1)[0];
		crawler.jumpNext;
		this.assertEquals(batch.orgnsms(0)[0].staticAttrs.width, 0, "leave value restored on the previous orgnsm");
		this.assert(crawler.orgnsm === batch.orgnsms(1)[0], "moved to the next orgnsm");
		crawler.setNextVal([9]);
		this.assertEquals(batch.orgnsms(1)[0].staticAttrs.width, 9, "new value applied to the new orgnsm");
		crawler.patternAttrs = (tribe: 0, static_attrs: (dummy: 1), attr_dict_base: [dur_flex: 0.25, next_val: Pseq([[1], [2]], inf)], add_first_array_base: []);
		crawler.createPattern(layerKey: \core);
		this.assert(crawler.patternInstance.isPlaying, "pattern orgnsm playing");
		this.assert(crawler.patternInstance.isCrawlerPattern, "flagged as a crawler pattern");
		this.assertEquals(crawler.patternInstance.staticAttrs.dummy, 1, "pattern static attrs fall back to patternAttrs");
		this.wait({ crawler.val == [2] or: { crawler.val == [1] } }, "pattern drives setNextVal", 3);
		this.assert([[1], [2]].includesEqual(crawler.val), "value set by the pattern");
		crawler.free;
		this.assert(crawler.patternInstance.isNil, "pattern freed");
		batch.free;
	}

	test_function_next_and_leave {
		var tpl = RCOrgnsm(\fnl, 0, 0, song);
		var batch, crawler;
		tpl.addStaticAttrs((seed: 1, quant: [1, 0], width: 0));
		tpl.attrDictBase = [type: \rest, dur_flex: 1];
		batch = RCBatch(\fnl, tpl, layerKey: \core);
		batch.addCreate(0, start: true);
		batch.addCreate(1, start: true);
		crawler = RCCrawler(["width"]);
		crawler.initFromBatch(batch, 0, val: [5]);
		crawler.leaveVal = { |c| c.prevVal };
		crawler.nextOrgnsm = { |c| batch.orgnsms(1)[0] };
		crawler.jumpNext;
		this.assertEquals(batch.orgnsms(0)[0].staticAttrs.width, 0, "function leaveVal restores the previous value");
		this.assert(crawler.orgnsm === batch.orgnsms(1)[0], "function nextOrgnsm picks the next orgnsm");
		crawler.nextOrgnsm = { nil.explode };
		crawler.jumpNext;
		this.assert(crawler.orgnsm === batch.orgnsms(1)[0], "a failing nextOrgnsm function keeps the crawler in place");
		this.assert(this.logHas("explode"), "failure reported");
		crawler.free;
		batch.free;
	}

	logHas { |text|
		^RCLog.history.any { |entry| entry[2].contains(text) }
	}

	test_method_keys {
		var c = RCCrawler(["set_width"], true);
		var fake = RCTestFakeSettable.new;
		c.init(fake, [3]);
		this.assertEquals(fake.width, 3, "method key calls the camelCase method");
		this.assertEquals(RCUtil.camelCase("set_rotation_matrix"), \setRotationMatrix, "camelCase helper");
	}
}

RCTestFakeSettable {
	var <width;
	setWidth { |w| width = w }
	rGet { |key| ^nil }
	rPut { |key, val| }
	isOrgnsm { ^false }
	name { ^\fake }
}
