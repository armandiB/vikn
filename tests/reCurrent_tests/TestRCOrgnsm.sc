TestRCOrgnsm : UnitTest {
	var clock, song, savedRateLimit;

	setUp {
		clock = RCTestSupport.clock;
		RCTestSupport.bootSession;
		song = RCSong(\org, 1994);
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown {
		RCTestSupport.reset;
		RCLog.rateLimit = savedRateLimit;
	}

	template {
		var t = RCOrgnsm(\firefly, 1, 0, song);
		t.addStaticAttrs((seed: 7, loop_time: 2, quant: { |self| [self.loop_time, 0] }, amp_unadj: 0.1, legato: 1));
		t.attrDictBase = [type: \rest, dur_flex: 0.5, amp: Pfunc { |ev| ev.self.staticAttrs.amp_unadj }, legato: Pfunc { |ev| ev.self.static_attrs.legato }];
		t.addFirstArrayBase = [marker: 1];
		^t
	}

	logHas { |text| ^RCLog.history.any { |entry| entry[2].contains(text) } }

	test_names_and_keys {
		var t = this.template;
		this.assertEquals(t.name, 'orgnsm_firefly_t1_0', "name from species/tribe/number");
		this.assertEquals(t.tribeName, "orgnsm_firefly_t1", "tribe name");
		this.assertEquals(t.staticAttrs.orgnsm_name, "orgnsm_firefly_t1_0", "staticAttrs computed name");
		this.assertEquals(RCOrgnsm(\a, 0, 3, song, addSongInName: true).name, 'org_orgnsm_a_t0_3', "song prefix");
		this.assertEquals(t.convertKey("amp_unadj"), [\staticAttrs, \amp_unadj], "bare key → staticAttrs");
		this.assertEquals(t.convertKey("attr_dict_base.amp"), [\attrDictBase, \amp], "snake_case slot");
		this.assertEquals(t.convertKey("attrDictBase.amp"), [\attrDictBase, \amp], "camelCase slot");
		this.assertEquals(t.convertKey("server_ressources.buffer"), [\serverResources, \buffer], "proto spelling of resources");
		this.assertEquals(t.convertKey([\static_attrs, \x, \y]), [\staticAttrs, \x, \y], "array path");
	}

	test_rPut_rGet {
		var t = this.template;
		t.rPut("amp_unadj", 0.5);
		this.assertEquals(t.staticAttrs.amp_unadj, 0.5, "static attr set");
		this.assertEquals(t.rGet("amp_unadj"), 0.5, "static attr read");
		t.rPut("attr_dict_base.amp", 9);
		this.assertEquals(RCUtil.kvAt(t.attrDictBase, \amp), 9, "attrDictBase entry replaced in place");
		t.rPut("attrDictBase.fresh", 1);
		this.assertEquals(t.attrDictBase.last, 1, "unknown attrDictBase key appended");
		this.assertEquals(t.rGet("attr_dict_base.fresh"), 1, "read back");
		t.rPut("add_first_array_base.marker", 2);
		this.assertEquals(t.rGet("add_first_array_base.marker"), 2, "addFirstArrayBase");
		t.rPut("static_attrs.nested", (a: 1));
		t.rPut("static_attrs.nested.a", 5);
		this.assertEquals(t.rGet("nested.a"), 5, "nested static attr");
		t.rPut("attr_dict_base.a.b", 1);
		this.assert(this.logHas("nested paths are not supported"), "nested attrDictBase path refused");
		t.rPut("release", 1);
		this.assert(this.logHas("shadows a method"), "reserved static attr name warned");
	}

	test_clone_is_independent {
		var t = this.template;
		var c = t.clone;
		c.rPut("amp_unadj", 0.9);
		c.rPut("attr_dict_base.dur_flex", 0.25);
		c.addFirstArrayBase[1] = 42;
		this.assertEquals(t.staticAttrs.amp_unadj, 0.1, "template static attrs untouched");
		this.assertEquals(RCUtil.kvAt(t.attrDictBase, \dur_flex), 0.5, "template attrDictBase untouched");
		this.assertEquals(RCUtil.kvAt(t.addFirstArrayBase, \marker), 1, "template addFirstArrayBase untouched (deep copy)");
	}

	test_clone_nested_attrs_and_shared_resources {
		var t = this.template;
		var buf = RCTestFakeBeat(\buffer);
		var c1, c2;
		t.rPut("static_attrs.nested", (a: 1));
		t.setBuffer(buf, alwaysFreeAtDelete: true);
		c1 = t.clone;
		c2 = t.clone;
		c1.rPut("nested.a", 5);
		this.assertEquals(t.staticAttrs.nested.a, 1, "nested static attrs are copied, not shared");
		this.assertEquals(c2.staticAttrs.nested.a, 1, "sibling clone untouched");
		this.assert(c1.serverResources[\buffer] === buf, "server resources shared");
		c1.free;
		c2.free;
		this.assert(buf.freed.not, "clones do not free the template's buffer");
		t.freeAlwaysServerResources;
		this.assertEquals(buf.freeCount, 1, "the template frees it");
		t.freeAlwaysServerResources;
		t.freeAllServerResources;
		this.assertEquals(buf.freeCount, 1, "never twice");
	}

	test_registry_reregister_and_batch_guards {
		var t = this.template;
		var o = t.create(layerKey: \core);
		var b = RCBatch(\g, t, layerKey: \core);
		o.register;
		this.assertEquals(song.registry.size, 1, "re-registering keeps one entry");
		this.assertEquals(o.number, 1, "with a fresh number");
		b.addCreate(0, start: true);
		b.addCreate(1);
		b.store;
		b.deleteBeats({ nil.explode }, true);
		this.assert(this.logHas("explode"), "a throwing condition on prepared orgnsms is reported");
		this.assertEquals(b.size + b.prepared.size, 2, "and deletes nothing");
		b.deleteBeats(nil, true);
		this.assertEquals(b.recall.values.flatten.size, 0, "recall drops orgnsms freed since store");
		o.free;
	}

	test_registry_numbering {
		var t = this.template;
		var a = t.create(layerKey: \core);
		var b = t.create(layerKey: \core);
		var c = RCOrgnsm(\firefly, 1, 0, song);
		this.assertEquals(a.number, 0, "first number");
		this.assertEquals(b.number, 1, "next number");
		this.assertEquals(b.name, 'orgnsm_firefly_t1_1', "name follows the number");
		c.register(pickNewNumber: false);
		this.assertEquals(c.number, 2, "fixed number that exists → next free, not overwritten");
		this.assert(song.registry.tribe(\firefly, 1)[0] === a, "tribe lookup");
		this.assertEquals(song.registry.size, 3, "three registered");
		a.free;
		this.assertEquals(song.registry.tribe(\firefly, 1)[0], nil, "freed → removed");
		this.assertEquals(t.create(layerKey: \core).number, 3, "numbers never reused");
	}

	test_create_start_events {
		var t = this.template;
		var o = t.create(layerKey: \core, replaceAttrs: ("amp_unadj": 0.3, "attr_dict_base.extra": 11));
		var evs, s;
		this.assert(o.spec.isKindOf(RCBeatSpec), "prepared");
		this.assertEquals(o.spec.name, 'orgnsm_firefly_t1_0', "spec named after the orgnsm");
		s = RCBeat(song.layer(\core), o.name, o.attrDict, seeds: o.seed, addFirst: o.addFirstArray, addFirstSeeds: o.seed).asStream;
		evs = 2.collect { s.next(Event.default) };
		this.assert(evs[0].self === o, "events carry the orgnsm as \\self");
		this.assertEquals(evs[0].marker, 1, "addFirstArrayBase key present");
		this.assertEquals(evs[0].amp, 0.3, "static attr read at event time through ev.self");
		this.assertEquals(evs[0].extra, 11, "replaceAttrs into attrDictBase");
		o.rPut("amp_unadj", 0.6);
		this.assertEquals(evs[1].amp, 0.3, "value fixed at pull time");
		this.assertEquals(s.next(Event.default).amp, 0.6, "edit visible at the next event");
		o.start;
		this.assert(o.isPlaying, "started on the clock");
		this.assert(song.layer(\core).beat(o.name) === o.beat, "beat registered under the orgnsm name");
		this.assertEquals(o.beat.playQuant, [2, 0], "quant from staticAttrs");
		o.free;
		this.assert(o.beat.isFreed and: { song.layer(\core).beat(o.name).isNil }, "free stops and unregisters the beat");
	}

	test_fobject_routing_without_fobjects {
		var t = this.template;
		var o;
		t.rPut("attr_dict_base.instrument_flex", \Kalimba);
		t.rPut("attr_dict_base.orgnsm_out_idx", 0);
		song.outArray = [8];
		o = t.create(layerKey: \core);
		{
			var s = RCBeat(song.layer(\core), o.name, o.attrDict, addFirst: o.addFirstArray).asStream;
			var ev = s.next(Event.default);
			this.assertEquals(ev.instrument, 'Kalimba__1_out', "single-output variant when no fobject exists");
			this.assertEquals(ev.outs, [8], "outs = own out");
			this.assertEquals(ev.outamps, [1], "full amplitude");
		}.value;
	}

	test_batch {
		var t = this.template;
		var b = RCBatch(\fireflies, t, layerKey: \core, replaceAttrs: ("tribe": 2, "amp_unadj": 0.2));
		var seen;
		3.do { |i| b.addCreate(i, ("amp_unadj": 0.1 * (i + 1))) };
		this.assertEquals(b.prepared.size, 3, "prepared, not started");
		this.assertEquals(b.size, 0, "no live orgnsm yet");
		b.startPrepared([0, 1]);
		this.assertEquals(b.size, 2, "two started");
		this.assertEquals(b.prepared.size, 1, "one still prepared");
		b.startPrepared;
		this.assertEquals(b.size, 3, "all started");
		this.assert(b.orgnsms(2)[0].isPlaying, "orgnsm playing");
		this.assertEquals(b.orgnsms(1)[0].tribe, 2, "batch replaceAttrs applied");
		this.assertEquals(b.orgnsms(1)[0].staticAttrs.amp_unadj, 0.2, "per-key replaceAttrs win over batch ones");
		b.editAttr("amp_unadj", 0.9, { |o, i, list, key| key == 0 });
		this.assertEquals(b.orgnsms(0)[0].staticAttrs.amp_unadj, 0.9, "editAttr with condition");
		this.assertEquals(b.orgnsms(1)[0].staticAttrs.amp_unadj, 0.2, "others untouched");
		b.editAttr("amp_unadj", { |o, i, list, key| key * 10 }, nil, true);
		this.assertEquals(b.orgnsms(2)[0].staticAttrs.amp_unadj, 20, "editAttr with a value function");
		seen = b.apply({ |o, i, list, key| key }).values.flatten.sort;
		this.assertEquals(seen, [0, 1, 2], "apply visits every key");
		b.apply({ |o| nil.explode });
		this.assert(this.logHas("explode"), "an error in apply is reported, not thrown");
		this.assertEquals(b.allOrgnsms.keys.size, 3, "allOrgnsms by key");
		b.deleteBeats({ |o, i, list, key| key == 1 }, true);
		this.assertEquals(b.size, 2, "conditional delete");
		this.assertEquals(b.keys.sort, [0, 2], "cleanup drops the key");
		b.free;
		this.assertEquals(b.size, 0, "free");
		this.assertEquals(song.registry.size, 0, "registry empty after free");
	}

	test_synthdefs {
		var names = RCSynthDefs.addForOrgnsms(\rc_test_sd, { |freq = 440| SinOsc.ar(freq) }, oneOutputOnly: false);
		var desc = SynthDescLib.global[RCSynthDefs.outputSuffix(\rc_test_sd, 2)];
		this.assertEquals(names.size, RCSynthDefs.maxNumOuts, "one variant per output count");
		this.assertEquals(RCSynthDefs.outputSuffix(\x, 3), 'x__3_out', "suffix");
		this.assert(desc.notNil, "desc registered");
		this.assert(desc.controlNames.includes(\outs) and: { desc.controlNames.includes(\outamps) } and: { desc.controlNames.includes(\freq) }, "controls");
		this.assertEquals(RCSynthDefs.addForOrgnsms(\rc_bad_sd, { nil.explode }).size, 0, "failing sound function reported, nothing added");
		this.assert(this.logHas("explode"), "error reported");
		this.assertEquals(RCSynthDefs.addLoopBufferWriters(2), [\write_buffer_1chan, \write_buffer_2chan], "loop buffer writers");
	}
}
