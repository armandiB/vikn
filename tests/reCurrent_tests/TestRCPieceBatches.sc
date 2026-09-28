// The batch, layer, path-control and crawler idioms of the Biomusic clones (HadronLake3 Rhythms,
// WeMadeATrack Rhythms), as contracts on RCBatch / RCSong / RCPathControl / RCCrawler. The rig is
// built the way the pieces build it, in miniature over the octahedron of TestRCPathControl; nothing
// is read from HomewareSC.
TestRCPieceBatches : UnitTest {
	var clock, song, savedRateLimit;

	setUp {
		clock = RCTestSupport.clock;
		RCTestSupport.bootSession;
		song = RCSong(\pieces, 1994);
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown {
		RCTestSupport.reset;
		RCLog.rateLimit = savedRateLimit;
	}

	logHas { |text| ^RCLog.history.any { |e| e[2].contains(text) } }

	// HadronLake3's percussion voice in miniature: a silent orgnsm reading the seq_list its path
	// control writes, its param keys declared up front, its place on the design as static attrs
	percTemplate {
		var tpl = RCOrgnsm(\percs_path, 0, 0, song);
		tpl.addStaticAttrs((
			seed: 1994, dur_params: [4, 1],
			loop_time: { |self| var dp = self.dur_params; dp[0] * dp[1] },
			quant: { |self| [self.loop_time, 0] },
			initial_beg_shift: 0,
			other_params_key_list: [\perc, \amp_unadj], seq_list: [],
			spos_center_idx: 0, zpos_center: [1.5]
		));
		tpl.addFirstArrayBase = [compute_seq_params: RCOrgnsmPatterns.seqParams(true)];
		tpl.attrDictBase = [
			type: \rest,
			dur_flex: Pfunc { |ev| ev.compute_seq_params.dereference[\dur] },
			orgnsm_out_idx: 0,
			perc: Pfunc { |ev| var v = ev.compute_seq_params.dereference[\perc]; if(v.isKindOf(Symbol)) { v } { \kick } }
		];
		^tpl
	}

	// two rhythms under the path name \rhythms, as in the HadronLake3 dicts
	rhythmDict {
		var lib = RCSubseqLibrary.newFrom((percs: (kick: ('fourfour': [\fourfour, 0, [1, 1, 1, 1]]), hh: ('fourfour': [\fourfour, 0.5, [1, 1, 1, 1]]))));
		var rd = RCRhythmDict(lib);
		rd.at(\rhythms)[\percs_t1] = [[1, 0, "percs.kick.fourfour", true, (perc: \kick, amp_unadj: 1)], [1, 0, "percs.hh.fourfour", true, (perc: \hh, amp_unadj: 0.25)]];
		rd.at(\rhythms)[\percs_t2] = [[1, 0, "percs.kick.fourfour", true, (perc: \kick, amp_unadj: 0.5)]];
		^rd
	}

	// The rig of HadronLake3 Rhythms: one batch per rhythm in a layer of its own, one path control
	// per batch cloned from the Assemblage's template into a batch on the meta layer, keyed by the
	// controlled batch's name. Started, then every player stopped: the streams are pulled by hand.
	makeRig {
		var rd = this.rhythmDict;
		var design = RCTestFakeDesign.new;
		var tpl = this.percTemplate;
		var pcTemplate = RCPathControl(song, design, nil, \fill_path_name, \fill_path_key, nil);
		var batches, controlBatch, controlKeys = ();   // not `keys`: Event:keys is a method (SC_NOTES)
		pcTemplate.addStaticAttrs((seed: 1994, dur_params: [4, 1], start_orgnsm_series: 0, seed_orgnsm_series: 2));
		batches = [1, 2].collect { |tribe|
			var key = ("rhythm_t" ++ tribe).asSymbol;
			var b;
			song.layers[key] ?? { song.addLayer(key) };
			b = RCBatch(("batch_percs_t" ++ tribe).asSymbol, tpl, layerKey: key, replaceAttrs: ("tribe": tribe));
			design.size.do { |i| b.addCreate(i, ("spos_center_idx": i, "seed": 1994 + i + (100 * tribe))) };
			b
		};
		controlBatch = RCBatch(\batch_path_control_rhythms, pcTemplate, layerKey: \meta);
		batches.do { |batch, i|
			controlKeys[i + 1] = controlBatch.addCreate(batch.name, (
				"controlled_batch": batch, "rhythm_dict": rd, "path_name": \rhythms, "path_key": ("percs_t" ++ (i + 1)).asSymbol,
				"dur_params": [4, 1], "quant": [4, -0.25], "tdesign": design,
				"start_orgnsm_series": 3 * i, "seed_orgnsm_series": 2 + i,
				"other_params_default_values": (perc: \kick, amp_unadj: 1)
			));
		};
		batches.do(_.startPrepared);
		controlBatch.startPrepared;
		song.allBeats.do(_.stop);
		^(rd: rd, design: design, template: pcTemplate, batches: batches, controlBatch: controlBatch, controlKeys: controlKeys)
	}

	// the HadronLake3 crawler template: stays on its path control, morphs the held rhythm
	crawlerTemplate {
		var t = RCCrawler(["static_attrs.seqs_info"]);
		t.nextOrgnsm = { |c| c.orgnsm };
		t.setLeaveVal = false;
		t.patternAttrs = (
			tribe: 0,
			static_attrs: (seed: 1994, quant: [4, 1], rhythm_dict_target: nil, tolerance_change_subseq: 2, keep_other_params_not_in_new_seq: true,
				matching_distance_func: {{ |target, current| target.hammingDistance(current) }}, priority_matching_func: {{ |target, current| target == current }},
				error_probability_shift_converge: 0, error_probability_mask_converge: 0, error_probability_mask_decrease: 0),
			attr_dict_base: [dur_flex: [4], next_val: RCCrawlerMoves.nextValRhythmChange]
		);
		^t
	}

	test_layer_per_rhythm_idiom {
		var l1 = song.layers[\rhythm_t1] ?? { song.addLayer(\rhythm_t1) };
		var again = song.layers[\rhythm_t1] ?? { song.addLayer(\rhythm_t1) };
		var l2 = song.layers[\rhythm_t2] ?? { song.addLayer(\rhythm_t2) };
		var beat, replaced;
		this.assert(again === l1, "the ?? idiom finds the existing layer");
		l1.swing.amount = 1/22;
		l2.swing.amount = 1/6;
		this.assertEquals(song.layer(\rhythm_t1).swing.amount, 1/22, "each rhythm layer swings on its own");
		this.assert(song.layer(\rhythm_t1).swing !== song.layer(\core).swing, "and not with the core layer");
		beat = l1.addBeat(\x, [type: \rest, dur_flex: 1], post: false);
		replaced = song.addLayer(\rhythm_t1);
		this.assert(beat.isFreed, "addLayer on an existing key frees the previous layer's beats (hence the idiom)");
		this.assert(song.layer(\rhythm_t1) === replaced and: { replaced !== l1 }, "and installs a fresh layer");
		this.assertEquals(song.allBeats.size, 0, "no beat survives the replacement");
	}

	test_batch_replace_attrs_route_to_the_registered_out {
		var bus = Bus.audio(Server.default, 16);
		var tpl = this.percTemplate;
		var batch, o, stream, ev;
		song.outArray = [2, 4, 6];
		song.registerOut(\ambi, 64);
		song.registerOut(\percs, bus);
		tpl.rPut("attr_dict_base.instrument_flex", \kick_MG_w0_HOA);
		batch = RCBatch(\batch_percs_t1, tpl, layerKey: \core, replaceAttrs: ("tribe": 1, "attr_dict_base.orgnsm_out_idx": song.outIndex(\percs)));
		3.do { |i| batch.addCreate(i, ("spos_center_idx": i, "seed": 1994 + i + 100, "zpos_center": [2])) };
		batch.startPrepared;
		song.allBeats.do(_.stop);
		o = batch.orgnsms(1)[0];
		this.assertEquals(o.tribeName, "orgnsm_percs_path_t1", "the batch's tribe names the orgnsms");
		this.assertEquals([o.staticAttrs.spos_center_idx, o.staticAttrs.seed, o.staticAttrs.zpos_center], [1, 2095, [2]], "per-orgnsm replace attrs land in the static attrs");
		this.assertEquals(RCUtil.kvAt(o.attrDictBase, \orgnsm_out_idx), 4, "the dotted key lands in the attr dict base");
		this.assertEquals(tpl.staticAttrs.spos_center_idx, 0, "the template keeps its own values");
		stream = RCBeat(song.layer(\core), o.name, o.attrDict, seeds: o.seed, addFirst: o.addFirstArray, addFirstSeeds: o.seed).asStream;
		ev = stream.next(Event.default);
		this.assertEquals(ev.outs, [bus.index], "orgnsm_out_idx from the registered slot: the note goes to the percussion bus");
		this.assertEquals(ev.instrument, 'kick_MG_w0_HOA__1_out', "with the single-output variant of the sound");
		this.assertEquals(ev.perc, \kick, "the per-hit param reader falls back while no rhythm is distributed");
		batch.free;
		bus.free;
	}

	test_delete_and_recreate_a_batch {
		var tpl = this.percTemplate;
		var first = RCBatch(\batch_percs_t1, tpl, layerKey: \core);
		var old, second;
		2.do { |i| first.addCreate(i) };
		first.startPrepared;
		old = first.allOrgnsms.values.flatten;
		this.assertEquals(song.registry.size, 2, "two orgnsms registered");
		first !? { first.deleteBeats(nil, true) };
		this.assert(old.every(_.isFreed), "deleteBeats(nil, true) frees every orgnsm");
		this.assert(first.size == 0 and: { first.keys.isEmpty }, "and forgets them");
		this.assertEquals(song.registry.size, 0, "the registry is empty");
		this.assertEquals(song.layer(\core).beats.size, 0, "no beat left in the layer");
		second = RCBatch(\batch_percs_t1, tpl, layerKey: \core);
		2.do { |i| second.addCreate(i) };
		second.startPrepared;
		this.assertEquals(song.registry.size, 2, "the new batch's orgnsms only");
		this.assert(second.allOrgnsms.values.flatten.every { |o| o.isPlaying }, "all playing");
		this.assertEquals(song.layer(\core).beats.size, 2, "one beat per orgnsm");
		this.assertEquals(second.allOrgnsms.values.flatten.collect(_.number).sort, [2, 3], "numbers are never reused");
		second.free;
	}

	test_path_controls_in_a_batch_keyed_by_the_controlled_batch {
		var rig = this.makeRig;
		var controls = rig.controlBatch.allOrgnsms;
		var control = controls[\batch_percs_t1][0];
		var voices, hits;
		this.assertEquals(rig.controlKeys[1], \batch_percs_t1, "addCreate returns the key: the controlled batch's name");
		this.assertEquals(controls.keys.asArray.sort, [\batch_percs_t1, \batch_percs_t2], "one control per batch");
		this.assert(control.isKindOf(RCPathControl), "a clone of the path control template is a path control");
		this.assert(control.staticAttrs.controlled_batch === rig.batches[0], "controlling its batch");
		this.assertEquals(control.rhythmDictKeys, [\rhythms, \percs_t1], "looked up by path name and key");
		this.assertEquals(control.beat.playQuant, [4, -0.25], "reads a quarter beat before the voices' loops");
		this.assertEquals(control.staticAttrs[\seqs_info].size, 2, "a started control holds its rhythm (two subseqs)");
		this.assertEquals(controls[\batch_percs_t2][0].staticAttrs[\seqs_info].size, 1, "each control its own rhythm");
		this.assertEquals(controls[\batch_percs_t2][0].staticAttrs.start_orgnsm_series, 3, "per-control series start");
		this.assert(rig.template.staticAttrs.controlled_batch.isNil and: { rig.template.staticAttrs.rhythm_dict.isNil }, "the template is left untouched");
		control.beat.asStream.next(Event.default);   // one control loop, by hand
		voices = rig.batches[0].allOrgnsms.values.flatten;
		this.assertEquals(voices.size, rig.design.size, "one voice per point of the design");
		this.assert(voices.every { |o| o.staticAttrs.seq_list.notEmpty }, "every voice received a seq_list");
		hits = voices.sum { |o| o.staticAttrs.seq_list.sum { |s| s.durs.count { |d| d.isRest.not } } };
		this.assertEquals(hits, 8, "the eight hits of the rhythm are distributed, none lost");
		this.assert(voices.every { |o| o.staticAttrs.other_params_key_list.asSet == Set[\perc, \amp_unadj] }, "the control pushes the rhythm's keys: the ones declared up front, no other");
		this.assert(this.logHas("added key").not, "no key installed on a running beat");
		this.assert(rig.batches[1].allOrgnsms.values.flatten.every { |o| o.staticAttrs.seq_list.isEmpty }, "the other batch waits for its own control");
		rig.controlBatch.free;
		rig.batches.do(_.free);
	}

	test_crawler_cloned_from_a_template_and_retargeted_in_place {
		var rig = this.makeRig;
		var control = rig.controlBatch.allOrgnsms[\batch_percs_t1][0];
		var template = this.crawlerTemplate;
		var target = RCRhythmDict(rig.rd.library), other = RCRhythmDict(rig.rd.library);
		var crawler, pattern, res, lost;
		target.at(\rhythms)[\percs_t1] = [[1, 0.5, "percs.kick.fourfour", true, (perc: \kick, amp_unadj: 1)], [1, 0, "percs.hh.fourfour", true, (perc: \hh, amp_unadj: 0.25)]];
		other.at(\rhythms)[\percs_t1] = [[1, 0, "percs.kick.fourfour", false, (perc: \kick, amp_unadj: 1)]];
		lost = template.clone.initFromBatch(rig.controlBatch, \nope, 0);
		this.assert(lost.orgnsm.isNil and: { this.logHas("no live orgnsm") }, "no control for that name: no orgnsm, reported (the pieces then refuse the fade)");
		crawler = template.clone.initFromBatch(rig.controlBatch, \batch_percs_t1, 0);
		this.assert(crawler.orgnsm === control, "initFromBatch by the controlled batch's name lands on its path control");
		crawler.patternAttrs.tribe = 1;
		crawler.patternAttrs.static_attrs.rhythm_dict_target = target;
		this.assertEquals(template.patternAttrs.tribe, 0, "the template's pattern attrs are untouched");
		this.assert(template.patternAttrs.static_attrs.rhythm_dict_target.isNil, "down to the nested static attrs");
		pattern = crawler.createPattern(layerKey: \meta);
		this.assert(pattern.notNil and: { pattern.isPlaying } and: { song.layer(\meta).beat(pattern.name) === pattern.beat }, "the crawler's pattern orgnsm plays in the meta layer");
		pattern.beat.stop;
		this.assert(pattern.staticAttrs[\rhythm_dict_target] === target, "the pattern orgnsm reads the crawler's static attrs");
		res = RCCrawlerMoves.computeNext(crawler, pattern.staticAttrs);
		this.assertFloatEquals(res.detect { |s| s.name == "percs.kick.fourfour" }.shift, 0.5, "one deterministic move: the kick's shift reaches the target's");
		crawler.setNextVal([res]);
		this.assert(control.staticAttrs[\seqs_info] === res, "the move becomes the rhythm the control distributes");
		crawler.patternAttrs.static_attrs.rhythm_dict_target = other;
		this.assert(pattern.staticAttrs[\rhythm_dict_target] === other, "retargeting the crawler's attrs retargets the running pattern (a second fade call)");
		crawler.free;
		this.assert(pattern.isFreed and: { song.layer(\meta).beat(pattern.name).isNil }, "free stops and unregisters the pattern orgnsm");
		this.assert(control.staticAttrs[\seqs_info] === res, "setLeaveVal false: the held rhythm stays as crawled");
		rig.controlBatch.free;
		rig.batches.do(_.free);
	}

	test_array_batch_keys {
		var tpl = this.percTemplate;
		var b = RCBatch(\batch_handshake_t0, tpl, layerKey: \core);
		var c;
		3.do { |j| b.addCreate([0, j]) };
		b.startPrepared([[0, 1]]);
		this.assertEquals(b.size, 1, "startPrepared by an Array key");
		this.assertEquals(b.prepared.keys.asSet, Set[[0, 0], [0, 2]], "the others stay prepared");
		b.startPrepared;
		this.assertEquals(b.size, 3, "all started");
		this.assertEquals(b.orgnsms([0, 2]).size, 1, "orgnsms by an equal Array key");
		c = RCCrawler(["static_attrs.seq_list"]).initFromBatch(b, [0, 0], 0);
		this.assert(c.orgnsm === b.orgnsms([0, 0])[0], "a crawler starts from an Array key too");
		this.assertEquals(c.orgnsm.rhythmDictKeys, [\batch_handshake_t0, [0, 0]], "the orgnsm's rhythm dict keys carry the Array key");
		b.free;
	}
}
