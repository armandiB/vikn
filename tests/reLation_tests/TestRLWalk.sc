// reLation, walks and morphisms: RLWalk, RLMorphism. No server.
// The tables of HomewareSC's Math/Category_Theory/THEORY.md, § 2 (voices, commas) and § 3.3 (cycles).
TestRLWalk : UnitTest {
	var edo, just, ctx, jctx, c, pump;

	setUp {
		edo = RLEDO(12);
		just = RLJust([3, 5]);
		ctx = RLChordContext(RLChordSpace.triads(edo));
		jctx = RLChordContext(RLChordSpace.triads(just));
		c = ctx.space.major(0);
		pump = [\R, \L, \R, \P, \R, \L, \R, \L];
	}

	test_chords_of_a_walk {
		var walk = RLWalk(ctx, c, pump);
		var beethoven = RLWalk(ctx, c, [\R, \L].wrapExtend(11));
		this.assertEquals(walk.chords.collect(_.name),
			["C major", "A minor", "F major", "D minor", "D major", "B minor", "G major", "E minor", "C major"], "the pump");
		this.assert(walk.isClosed, "closes in 12");
		this.assertEquals(walk.size, 8, "eight steps");
		this.assert(walk.composed.isIdentity, "its steps compose to nothing");
		this.assertEquals(beethoven.chords.collect(_.name),
			["C major", "A minor", "F major", "D minor", "Bb major", "G minor", "Eb major", "C minor", "Ab major", "F minor", "C# major", "Bb minor"],
			"the R, L chain of the Ninth Symphony (opycleid's README)");
		this.assertEquals(RLWalk(ctx, c, [ctx.r, ctx.l]).chords, RLWalk(ctx, c, [\R, \L]).chords, "steps given as transformations");
	}

	test_cycles_in_seven_divisions {
		// steps, then how many times each word is taken before the walk closes, then whether the pump closes
		var expected = [
			[12, 3, 4, 12, true], [19, 19, 19, 19, true], [22, 22, 11, 22, false], [31, 31, 31, 31, true],
			[34, 34, 34, 17, false], [41, 41, 41, 41, false], [53, 53, 53, 53, false]
		];
		expected.do { |row|
			var cx = RLChordContext(RLChordSpace.triads(RLEDO(row[0]))), start = cx.space.major(0);
			var got = [[\P, \L], [\P, \R], [\R, \L]].collect { |w| RLWalk.cycle(cx, start, w).size.div(2) };
			this.assertEquals(got, row.copyRange(1, 3), "% steps: the LP, RP and LR cycles".format(row[0]));
			this.assertEquals(RLWalk(cx, start, pump).isClosed, row[4], "% steps: the pump".format(row[0]));
		};
	}

	test_closed_walks_end_a_comma_away_on_the_lattice {
		var start = jctx.space.major;
		var cases = [
			[[\P, \L].wrapExtend(6), [0, -3], 41.0589, "diesis 128/125"],
			[[\P, \R].wrapExtend(8), [4, -4], 62.5651, "major diesis 648/625"],
			[[\R, \L].wrapExtend(24), [-12, 0], -23.46, "Pythagorean comma 531441/524288"],
			[pump, [-4, 1], -21.5063, "syntonic comma 81/80"]
		];
		cases.do { |row|
			var walk = RLWalk(jctx, start, row[0]);
			this.assert(RLWalk(ctx, c, row[0]).isClosed, "% closes in 12".format(row[3]));
			this.assert(walk.isClosed.not, "not on the lattice");
			this.assertEquals(walk.drift, row[1], "it ends % away".format(row[1]));
			this.assertFloatEquals(walk.driftCents, row[2], "in cents", 0.001);
			this.assertEquals(just.commaName(walk.drift), row[3], "a known comma");
		};
		this.assert(RLWalk(jctx, start, pump).composed.isIdentity.not, "the pump is not the identity there");
		this.assertEquals(RLWalk.cycle(jctx, start, [\P, \L], 40), nil, "and the hexatonic cycle never closes");
	}

	test_voices_of_closed_walks {
		var v = [60, 64, 67];
		this.assertEquals(RLWalk(ctx, c, [\P, \L].wrapExtend(6)).voiceDrift(v), [0, 0, 0], "hexatonic: every voice back in place");
		this.assertEquals(RLWalk(ctx, c, [\P, \R].wrapExtend(8)).voiceDrift(v), [-5, -4, -3], "octatonic: one inversion lower");
		this.assertEquals(RLWalk(ctx, c, [\R, \L].wrapExtend(24)).voiceDrift(v), [12, 12, 12], "the LR chain: every voice an octave up");
		this.assertEquals(RLWalk(ctx, c, pump).voiceDrift(v), [4, 3, 5], "the pump: one inversion higher");
		this.assertEquals(RLWalk(ctx, c, pump).voices(v).last, [64, 67, 72], "its last voicing");
		this.assertEquals(RLWalk(ctx, c, pump).voiceCents(v).round.asInteger, [200, 100, 200, 100, 200, 100, 200, 100], "R moves a tone, L and P a semitone");
		this.assertEquals(RLWalk(ctx, c, pump).voices.first, [0, 4, 7], "close position at the origin when no voicing is given");
		this.assertException({ RLWalk(ctx, c, pump).voices([60, 63, 67]) }, Error, "a voicing that is not the start chord is refused");
	}

	test_voices_on_the_lattice_and_in_other_divisions {
		var walk = RLWalk(jctx, jctx.space.major, pump);
		var e19 = RLChordContext(RLChordSpace.triads(RLEDO(19)));
		this.assertArrayFloatEquals(walk.voiceCents, [182.4037, 111.7313, 182.4037, 70.6724, 182.4037, 111.7313, 182.4037, 111.7313],
			"just: R moves 10/9, L 16/15, P 25/24", 0.001);
		this.assertEquals(RLWalk(e19, e19.space.major(0), pump).voiceCents.collect { |x| (x / (1200 / 19)).round.asInteger },
			[3, 2, 3, 1, 3, 2, 3, 2], "19 steps: R 3 steps, L 2, P 1");
		this.assertEquals(RLWalk(e19, e19.space.major(0), pump).voiceDrift, [6, 5, 8], "the pump closes in 19, one inversion higher");
		this.assertFloatEquals(walk.freqs(nil, 200).first[2], 300.0, "frequencies: the fifth above 200 Hz", 0.0001);
		this.assertEquals(walk.asPattern.class, Pseq, "and a pattern of them");
	}

	test_a_walk_is_a_value {
		var a = RLWalk(ctx, c, pump), b = RLWalk(ctx, c, pump.copy);
		this.assert(a == b, "equal by start and word");
		this.assert(a != RLWalk(ctx, ctx.space.major(2), pump), "another start");
		this.assertEquals(a.repeated(2).size, 16, "repeated");
		this.assertEquals((RLWalk(ctx, c, [\R]) ++ RLWalk(ctx, c, [\L])).chords.last, ctx.space.major(5), "joined");
		this.assertEquals(a.inContext(jctx).start, jctx.space.major, "read in another context from its base chord");
	}
}

TestRLMorphism : UnitTest {

	test_temperaments_are_morphisms {
		var jctx = RLChordContext(RLChordSpace.triads(RLJust([3, 5])));
		[12, 19, 22, 31, 34, 41, 53].do { |n|
			var cx = RLChordContext(RLChordSpace.triads(RLEDO(n)));
			this.assert(RLMorphism.temper(jctx, cx).isValid, "% steps: the val commutes with P, L and R".format(n));
		};
		this.assertEquals(RLMorphism.temper(jctx, RLChordContext(RLChordSpace.triads(RLEDO(12)))).(jctx.space.minor([-1, 1])).name,
			"A minor", "5/3 minor is A minor in 12");
		this.assertException({ RLMorphism.temper(RLChordContext(RLChordSpace.triads(RLEDO(12))), jctx) }, Error, "only from the lattice to a division");
	}

	test_a_map_that_is_no_morphism {
		var ctx = RLChordContext(RLChordSpace.triads(RLEDO(12)));
		var shift = RLMorphism(ctx, ctx, { |chord| ctx.t(1).(chord) }, "T1");
		var flip = RLMorphism(ctx, ctx, { |chord| ctx.i(0).(chord) }, "I0");
		var bad = RLMorphism(ctx, ctx, { |chord| ctx.space.chord(chord.root * 2, chord.type) }, "doubling");
		this.assert(shift.isValid, "a transposition commutes with P, L, R");
		this.assert(flip.isValid, "so does an inversion (duality)");
		this.assert(bad.isValid.not, "doubling the roots does not");
	}

	test_push_and_lift {
		var jctx = RLChordContext(RLChordSpace.triads(RLJust([3, 5])));
		var ctx = RLChordContext(RLChordSpace.triads(RLEDO(12)));
		var temper = RLMorphism.temper(jctx, ctx);
		var pump = RLWalk(ctx, ctx.space.major(0), [\R, \L, \R, \P, \R, \L, \R, \L]);
		var above = temper.lift(pump);
		this.assertEquals(above.drift, [-4, 1], "the pump lifted from 1/1 major ends a syntonic comma down");
		this.assertEquals(temper.push(above), pump, "pushed back, it is the pump");
		this.assertEquals(temper.lift(pump, jctx.space.major([4, -1])).start, jctx.space.major([4, -1]), "lifted from another chord above C major");
		this.assertException({ temper.lift(pump, jctx.space.major([1, 0])) }, Error, "a chord that is not above the start is refused");
		this.assertEquals(above.chords.collect { |x| temper.(x) }, pump.chords, "chord by chord, the lift lies above the walk");
	}

	// THEORY.md § 3.2: every closed walk of an equal division lifts to a just walk ending on a comma of its kernel.
	test_closed_walks_lift_to_the_kernel {
		var just = RLJust([3, 5]), jctx = RLChordContext(RLChordSpace.triads(just));
		var bad = 0, closed = 0;
		thisThread.randSeed = 7;
		[12, 19, 31].do { |n|
			var edo = RLEDO(n), ctx = RLChordContext(RLChordSpace.triads(edo)), temper = RLMorphism.temper(jctx, ctx);
			600.do {
				var word = { [\P, \L, \R].choose } ! (2 + 12.rand), walk = RLWalk(ctx, ctx.space.major(0), word), up;
				if(walk.isClosed) {
					closed = closed + 1;
					up = temper.lift(walk);
					if(up.drift.isNil or: { just.temperPc(edo, up.drift) != 0 }) { bad = bad + 1 };
				};
			};
		};
		this.assert(closed > 50, "enough closed walks were drawn (%)".format(closed));
		this.assertEquals(bad, 0, "each of the % closed walks lifts to a walk ending in the kernel".format(closed));
	}
}
