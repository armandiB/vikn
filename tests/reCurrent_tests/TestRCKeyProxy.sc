TestRCKeyProxy : UnitTest {
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

	// The construction RCBeat gave every non-static key before RCKeyProxy: a
	// Function in a Pfunc, PbindProxy's PatternProxy around Pseed around
	// Pcollect (the mirror).
	oldValues { |source, seed, n|
		var wrapped, stream;
		if(source.isKindOf(Function)) { source = Pfunc(source) };
		wrapped = Pcollect({ |v| v }, source);
		stream = PbindProxy(\k, if(seed.notNil) { Pseed(Pn(seed, 1), wrapped) } { wrapped }).asStream;
		^n.collect { stream.next(Event.default).k }
	}

	newValues { |source, seed, n|
		var stream = Pbind(\k, RCKeyProxy(\k, source, seed, clock)).asStream;
		^n.collect { stream.next(Event.default).k }
	}

	test_classify {
		this.assertEquals(RCKeyProxy.classify({ 1 }), \pattern, "a Function runs as a Pfunc");
		this.assertEquals(RCKeyProxy.classify(Pfunc { 1 }), \pattern, "a Pfunc");
		this.assertEquals(RCKeyProxy.classify(Pseq([1])), \pattern, "a Pattern");
		this.assertEquals(RCKeyProxy.classify(Routine { 1.yield }), \stream, "a Stream");
		this.assertEquals(RCKeyProxy.classify(5), \static, "a number");
		this.assertEquals(RCKeyProxy.classify(`[1, 2]), \static, "a Ref");
		this.assertEquals(RCKeyProxy.classify(\sym), \static, "a Symbol");
	}

	test_seeded_sources_match_the_old_stack {
		var factories = [
			{ Pwhite(0, 1000, inf) },
			{ Pbrown(0, 1000, 10, inf) },
			{ Pgbrown(0.5, 2, 0.1, inf) * Pfunc { rrand(1, 9) } },
			{ Pfunc { rrand(0, 1000) } },
			{ { 1000.rand } },
			{ Plazy { Pfunc { |ev| 1000.rand } } },
			{ Pseq([1, 2, 3], inf) },
			{ Pseq([Pwhite(0, 10, 3), Pfunc { 100.rand }], inf) },
			{ Prout { |inval| loop { inval = 1000.rand.yield } } },
			{ var r = Routine { loop { 1000.rand.yield } }; r.randSeed = 3; r }
		];
		factories.do { |factory, i|
			this.assertEquals(this.newValues(factory.value, 7, 64), this.oldValues(factory.value, 7, 64), "seeded source % gives the old sequence".format(i));
		};
	}

	test_unseeded_sources_start_from_the_creating_thread {
		[{ Pwhite(0, 1000, inf) }, { Pfunc { rrand(0, 1000) } }, { { 1000.rand } }, { Pfunc({ 1000.rand }, { }) }].do { |factory, i|
			var old, new;
			thisThread.randSeed = 42;
			old = this.oldValues(factory.value, nil, 32);
			thisThread.randSeed = 42;
			new = this.newValues(factory.value, nil, 32);
			this.assertEquals(new, old, "unseeded source % copies the creating thread's state".format(i));
		};
	}

	test_static_sources_read_as_under_pattern_proxy {
		var ref = `[1, 2, 3];
		[5, \sym, ref, (a: 1), [1, 2, 3], "str"].do { |v|
			var pp = PatternProxy.new, old, new;
			pp.setSource(v);
			old = pp.asStream.next(Event.default);
			new = RCKeyProxy(\k, v, nil, clock).asStream.next(Event.default);
			this.assertEquals(new, old, "a static % reads as under PatternProxy".format(v.class));
		};
		this.assertEquals(RCKeyProxy(\k, ref, nil, clock).asStream.next(Event.default), [1, 2, 3], "a Ref dereferences (Ref.next)");
	}

	test_lastValue_thread_and_randData {
		var stat = RCKeyProxy(\s, 5, nil, clock);
		var fn = RCKeyProxy(\f, Pfunc { 1000.rand }, 7, clock);
		var pat = RCKeyProxy(\p, Pwhite(0, 1000, inf), 7, clock);
		var fs = fn.asStream, ps = pat.asStream, data, b, c;
		stat.asStream.next(());
		this.assertEquals(stat.lastValue, nil, "a static value is not recorded");
		fs.next(());
		ps.next(());
		this.assert(fn.lastValue.notNil and: { pat.lastValue.notNil }, "non-static values are recorded");
		this.assert(fn.thread.isKindOf(Routine) and: { pat.thread.isKindOf(Routine) }, "a Function and a Pattern key each have their Routine");
		this.assert(fn.thread !== pat.thread, "one Routine per key");
		data = fn.randData;
		b = 5.collect { fs.next(()) };
		fn.randData = data;
		c = 5.collect { fs.next(()) };
		this.assertEquals(c, b, "randData round trip replays a Function key");
		data = pat.randData;
		b = 5.collect { ps.next(()) };
		pat.randData = data;
		c = 5.collect { ps.next(()) };
		this.assertEquals(c, b, "randData round trip replays a Pattern key");
	}

	// Both implementations edited and pulled in lockstep by a routine on the
	// clock, from the grid beat g: `script` receives (pull, set) and waits in
	// beats between them; the log holds [beat - g, PatternProxy value, RCKeyProxy value].
	lockstep { |oldFactory, script|
		var pp = PatternProxy.new, kp = RCKeyProxy(\k, oldFactory.value, nil, clock);
		var log = List.new, done = false, ppStream, kpStream;
		pp.clock = clock;
		pp.setSource(oldFactory.value);
		ppStream = Pbind(\k, pp).asStream;
		kpStream = Pbind(\k, kp).asStream;
		Routine {
			var g = ((thisThread.beats / 4).ceil * 4) + 4;
			var pull = { log.add([thisThread.beats - g, ppStream.next(Event.default).k, kpStream.next(Event.default).k]) };
			var set = { |factory, quant| pp.quant = quant; pp.setSource(factory.value); kp.setSource(factory.value, quant, nil) };
			(g - thisThread.beats).wait;
			script.value(pull, set);
			done = true;
		}.play(clock);
		this.wait({ done }, "the lockstep script finished", 10);
		^log
	}

	assertLockstep { |log, expected, what|
		this.assertEquals(log.collect(_[2]), log.collect(_[1]), what ++ ": RCKeyProxy agrees with PatternProxy (beat, old, new: %)".format(log));
		this.assertEquals(log.collect(_[2]), expected, what ++ ": expected values");
	}

	test_grid_edit_lands_like_pattern_proxy {
		var count = 0;
		var log = this.lockstep({ Pfunc { count = count + 1; 1 } }, { |pull, set|
			1.wait; pull.value; 0.5.wait; set.({ 2 }, 4); 0.5.wait; pull.value; 1.wait; pull.value; 1.wait; pull.value; 1.wait; pull.value;
		});
		this.assertLockstep(log, [1, 1, 1, 2, 2], "an edit on a grid of 4");
		this.assertEquals(count, 8, "each implementation pulled the old source four times: three values, one discarded at the switch");
	}

	test_edit_exactly_on_the_grid_switches_at_once {
		var log = this.lockstep({ 1 }, { |pull, set|
			1.wait; pull.value; 1.wait; pull.value; 1.wait; pull.value; 0.5.wait; set.({ 2 }, 4); 0.5.wait; pull.value;
		});
		this.assertLockstep(log, [1, 1, 1, 2], "an edit landing on the grid beat itself");
	}

	test_edit_with_quant_nil_lands_on_the_next_pull {
		var count = 0;
		var log = this.lockstep({ Pfunc { count = count + 1; 1 } }, { |pull, set|
			1.wait; pull.value; 0.5.wait; set.({ 2 }, nil); 0.5.wait; pull.value; 1.wait; pull.value;
		});
		this.assertLockstep(log, [1, 2, 2], "quant nil");
		this.assertEquals(count, 2, "no extra pull of the old source");
	}

	test_old_source_ending_before_the_grid_hands_over_at_once {
		var log = this.lockstep({ Pseq([1, 2], 1) }, { |pull, set|
			1.wait; pull.value; 0.5.wait; set.({ 9 }, 4); 0.5.wait; pull.value; 1.wait; pull.value; 1.wait; pull.value;
		});
		this.assertLockstep(log, [1, 2, 9, 9], "an old source ending early");
	}

	test_edit_before_the_first_pull_is_the_starting_source {
		var log = this.lockstep({ 1 }, { |pull, set|
			0.5.wait; set.({ 9 }, 4); 0.5.wait; pull.value; 1.wait; pull.value;
		});
		this.assertLockstep(log, [9, 9], "a stream started after an edit begins with the new source, whatever the quant");
	}

	test_two_edits_on_the_same_grid_keep_the_later {
		var log = this.lockstep({ 1 }, { |pull, set|
			1.wait; pull.value; 0.5.wait; set.({ 2 }, 4); 0.5.wait; pull.value; 0.5.wait; set.({ 3 }, 4); 0.5.wait; pull.value; 1.wait; pull.value; 1.wait; pull.value;
		});
		this.assertLockstep(log, [1, 1, 1, 3, 3], "two edits before one grid");
	}

	test_a_later_edit_with_a_nearer_grid_wins {
		var log = this.lockstep({ 1 }, { |pull, set|
			1.wait; pull.value; 0.5.wait; set.({ 2 }, 4); 0.5.wait; pull.value; 0.5.wait; set.({ 3 }, 1); 0.5.wait; pull.value; 1.wait; pull.value;
		});
		this.assertLockstep(log, [1, 1, 3, 3], "quant 4 then quant 1");
	}

	test_a_later_edit_with_a_farther_grid_follows {
		var log = this.lockstep({ 1 }, { |pull, set|
			1.wait; pull.value; 0.5.wait; set.({ 2 }, 1); 0.5.wait; pull.value; 0.5.wait; set.({ 3 }, 4); 0.5.wait; pull.value; 1.wait; pull.value;
		});
		this.assertLockstep(log, [1, 2, 2, 3], "quant 1 then quant 4");
	}

	test_two_pending_edits_switch_in_order {
		var log = this.lockstep({ 1 }, { |pull, set|
			1.wait; pull.value; 0.5.wait; set.({ 2 }, 1); 0.25.wait; pull.value; 0.05.wait; set.({ 3 }, 4); 0.2.wait; pull.value; 1.wait; pull.value; 1.wait; pull.value;
		});
		this.assertLockstep(log, [1, 1, 2, 2, 3], "a pending quant 1 edit, then a quant 4 edit before its grid");
	}

	// An unseeded Routine shares its parents' random state up to the thread that
	// last seeded itself: a seeded key must never touch the caller's state.
	test_pulling_seeded_keys_leaves_the_callers_random_state_alone {
		var s = Pbind(\k, RCKeyProxy(\k, Pfunc { 1000.rand }, 7, clock), \f, RCKeyProxy(\f, { 100.rand }, 7, clock), \p, RCKeyProxy(\p, Pwhite(0, 9, inf), 7, clock)).asStream;
		var a, b;
		thisThread.randSeed = 42;
		a = thisThread.randData;
		8.do { s.next(Event.default) };
		b = thisThread.randData;
		this.assertEquals(b, a, "eight events of three seeded random keys leave the pulling thread's state as it was");
	}

	test_stream_source_keeps_its_own_random_state {
		var r1 = Routine { loop { 1000.rand.yield } }, r2 = Routine { loop { 1000.rand.yield } };
		var stream;
		r1.randSeed = 3;
		r2.randSeed = 3;
		stream = RCKeyProxy(\k, r1, 7, clock).asStream;
		this.assertEquals(5.collect { stream.next(()) }, 5.collect { r2.next }, "a Stream is not re-seeded by the key's seed");
	}
}
