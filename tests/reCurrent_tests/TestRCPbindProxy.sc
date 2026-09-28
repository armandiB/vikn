TestRCPbindProxy : UnitTest {
	var clock, savedRateLimit;

	setUp {
		clock = RCTestSupport.clock;
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown {
		RCLog.rateLimit = savedRateLimit;
		clock.clear;
	}

	proxies { |...kv|
		^kv.clump(2).collect { |pair| [pair[0], RCKeyProxy(pair[0], pair[1], nil, clock)] }.flatten(1)
	}

	test_pairs_and_lookup {
		var p = RCPbindProxy(this.proxies(\a, 1, \rc_finish, 9, \b, 2), \rc_finish, clock, 1);
		this.assertEquals(RCUtil.kvKeys(p.pairs), [\a, \rc_finish, \b], "pairs as given");
		this.assert(p.at(\a).isKindOf(RCKeyProxy) and: { p.at(\a).source == 1 }, "at gives the key's proxy");
		this.assertEquals(p.find(\b), 4, "find gives the index of the key");
		this.assertEquals(p.at(\nope), nil, "unknown key");
		this.assert(p.includesKey(\b) and: { p.includesKey(\nope).not }, "includesKey");
		this.assertEquals(p.keys, [\a, \rc_finish, \b], "keys");
		this.assertEquals(p.quant, 1, "quant on the event pattern proxy");
		this.assertEquals(p.asStream.next(Event.default).b, 2, "the stream plays the pairs");
	}

	test_add_remove_rebuild_once_with_finish_last {
		var p = RCPbindProxy(this.proxies(\a, 1, \rc_finish, 9), \rc_finish, clock, 1);
		var rebuilds = 0, s;
		p.source.addDependant({ |obj, what| if(what == \source) { rebuilds = rebuilds + 1 } });
		p.add(\b, RCKeyProxy(\b, 2, nil, clock), nil);
		this.assertEquals(RCUtil.kvKeys(p.pairs), [\a, \b, \rc_finish], "rc_finish stays last");
		this.assertEquals(rebuilds, 1, "one rebuild per add");
		p.addAll(this.proxies(\c, 3, \d, 4), nil);
		this.assertEquals(RCUtil.kvKeys(p.pairs), [\a, \b, \c, \d, \rc_finish], "addAll keeps rc_finish last");
		this.assertEquals(rebuilds, 2, "one rebuild per addAll");
		p.add(\a, RCKeyProxy(\a, 5, nil, clock), nil);
		this.assertEquals(RCUtil.kvKeys(p.pairs), [\b, \c, \d, \a, \rc_finish], "adding an existing key replaces its proxy at the end");
		this.assertEquals(p.at(\a).source, 5, "with the new proxy");
		p.remove(\a, nil);
		this.assertEquals(RCUtil.kvKeys(p.pairs), [\b, \c, \d, \rc_finish], "removed");
		this.assertEquals(rebuilds, 4, "one rebuild per remove");
		p.remove(\nope, nil);
		this.assertEquals(rebuilds, 4, "removing an unknown key rebuilds nothing");
		s = p.asStream;
		this.assertEquals(s.next(Event.default).c, 3, "the stream plays the current pairs");
		p.add(\a, RCKeyProxy(\a, 7, nil, clock), nil);
		this.assertEquals(s.next(Event.default).a, 7, "a key added with quant nil reaches a running stream at its next event");
		p.quant = 4;
		this.assertEquals(p.quant, 4, "the quant of the whole-pattern swaps lives on the event pattern proxy");
	}

	test_lastValues_and_threads {
		var p = RCPbindProxy(this.proxies(\a, 1, \b, Pwhite(0, 9, inf), \c, Pfunc { 3 }), \rc_finish, clock, 1);
		var s = p.asStream;
		s.next(Event.default);
		this.assertEquals(p.lastValues.keys.asArray.sort, [\b, \c], "non-static keys are recorded");
		this.assertEquals(p.lastValues[\c], 3, "with their values");
		this.assertEquals(p.threads.keys.asArray.sort, [\b, \c], "every non-static key has its Routine, a static one none");
	}
}
