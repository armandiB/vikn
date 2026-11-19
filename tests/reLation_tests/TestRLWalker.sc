// reLation, a walk without an end: RLWalker, alone and inside a beat of reCurrent. No server.
TestRLWalker : UnitTest {
	var ctx, c, pump;

	setUp {
		RCTestSupport.bootSession;
		ctx = RLChordContext(RLChordSpace.triads(RLEDO(12)));
		c = ctx.space.major(0);
		pump = [\R, \L, \R, \P, \R, \L, \R, \L];
	}

	tearDown { RCTestSupport.reset }

	test_steps_follow_the_walk {
		var walker = RLWalker(ctx, c, [60, 64, 67], 8.1757989156), voicings = [walker.voicing];
		pump.do { |name| walker.step(name); voicings = voicings.add(walker.voicing) };
		this.assertEquals(voicings, RLWalk(ctx, c, pump).voices([60, 64, 67]), "step by step, the voicings of the whole walk");
		this.assertEquals([walker.chord, walker.count], [c, 8], "back on C major after eight steps");
		this.assertFloatEquals(walker.freqs[0], 64.midicps, "its lowest voice now on E", 0.001);
		this.assertException({ RLWalker(ctx, c, [60, 63, 67]) }, Error, "a voicing that is not the chord is refused");
		this.assertEquals(RLWalker(ctx, c, [48, 60, 64, 67]).step(\R).voicing.size, 4, "four voices on triads");
	}

	test_a_word_as_a_pattern {
		var walker = RLWalker(ctx, c, [60, 64, 67], 8.1757989156), stream = walker.asPattern([\R, \L]).asStream, again;
		var expected = RLWalk(ctx, c, [\R, \L, \P]).freqs([60, 64, 67], 8.1757989156);
		this.assertArrayFloatEquals(stream.next, expected[0], "first the chord it stands on", 0.001);
		this.assertArrayFloatEquals(stream.next, expected[1], "then a step", 0.001);
		this.assertArrayFloatEquals(stream.next, expected[2], "and another", 0.001);
		this.assertEquals(stream.next, nil, "the pattern ends with the word");
		again = walker.asPattern(Pseq([\P], inf)).asStream;
		this.assertArrayFloatEquals(again.next, expected[2], "started again, it goes on from where the walker stands", 0.001);
		this.assertArrayFloatEquals(again.next, expected[3], "with the new word", 0.001);
	}

	test_where_a_step_offers_several_chords {
		var cd = RLRelationContext.cubeDance, sp = cd.space, aug = sp.chord(0, \aug);
		var near = RLWalker(cd, aug, [60, 64, 68]), last = RLWalker(cd, aug, [60, 64, 68]);
		last.chooser = { |options| options.last };
		this.assertEquals(near.step(\U).chord, sp.major(0), "the nearest in voice leading (the first of those as near)");
		this.assertEquals(last.step(\U).chord, sp.minor(9), "or what the chooser says");
		this.assertEquals(near.step(\P).chord, sp.minor(0), "a step with one image");
		this.assertFloatEquals(RLVoiceLeading.cents(RLEDO(12), [60, 64, 68], RLWalker(cd, aug, [60, 64, 68]).step(\U).voicing), 100.0, "one semitone from the augmented triad", 0.001);
	}

	test_voices_kept_within_reach {
		var free = RLWalker(ctx, c, [60, 64, 67]), held = RLWalker(ctx, c, [60, 64, 67], 261.6, 0.75), worst = 0, shifts = 0;
		48.do { |i|
			free.step([\R, \L][i % 2]);
			held.step([\R, \L][i % 2]);
			worst = max(worst, (held.middle - held.centre).abs);
			shifts = shifts + held.shifted;
		};
		this.assertEquals(held.octaves, -2, "brought down twice, an octave each time");
		this.assertEquals(shifts, held.octaves, "and each step said so (shifted)");
		this.assertFloatEquals(held.rise, free.rise + held.octaves, "the voices' rise is the free walker's, those octaves less");
		this.assertEquals([free.shifted, free.octaves], [0, 0], "a walker without a reach is never brought back");
		this.assert((free.middle - free.centre) > 1.9, "the R, L chain climbs two octaves in 48 steps");
		this.assert(worst <= 0.76, "with a reach, the voices are brought back by octaves (never further than % octaves)".format(worst.round(0.01)));
		this.assertEquals(held.chord, free.chord, "on the same chords");
	}

	// what the lattice page of HomewareVisuals is sent (its protocol: public/lib/lattice-viz.js there)
	test_the_lattice_page {
		var got = Array.new, saved = RCVisuals.sink, walker, up, mean;
		RCVisuals.sink = { |path, args| got = got.add([path] ++ args) };
		up = RLLattice.show(RLWalk(ctx, c, pump));
		this.assertEquals(got[0], ['/lattice/config', 12, 7, 4, 10], "the division: its steps, its fifth, its major third, its harmonic seventh");
		this.assertEquals(got[1], ['/lattice/clear'], "then a clear");
		this.assertEquals(got.drop(2).size, 9, "then the nine chords of the lift");
		this.assertEquals(got.last, ['/lattice/chord', -4, 1, 0, 8], "the last one four fifths down and a third up: a syntonic comma from the start");
		this.assertEquals(got[3], ['/lattice/chord', -1, 1, 1, 1], "the second 5/3 minor");
		this.assertEquals(up.drift, [-4, 1], "the lift is returned");
		walker = RLWalker(ctx, c, [60, 64, 67]).follow;
		pump.do { |name| walker.step(name) };
		this.assertEquals([walker.chord, walker.latticeChord.root], [c, [-4, 1]], "a walker's shadow: back on C major, a comma away on the lattice");
		this.assertEquals(RLLattice.args(walker.latticeChord, walker.count), [-4, 1, 0, 8], "what a beat sends for it");
		this.assertException({ RLLattice.args(c) }, Error, "a chord that is not on the lattice is refused");
		got = Array.new;
		mean = RLChordContext(RLChordSpace.triads(RLTempered.meantone));
		up = RLLattice.show(RLWalk(mean, mean.space.major, pump));
		this.assertEquals(got[0], ['/lattice/comma', 4, -1], "a temperament of one comma: the comma, for the cylinder");
		this.assertEquals(got.last, ['/lattice/chord', -4, 1, 0, 8], "then the lift, as for a division");
		this.assertException({ RLLattice.config(RLTempered.named(\pajara)) }, Error, "a temperament the page cannot draw is refused");
		RCVisuals.sink = saved;
	}

	// tetrads on the lattice of 3, 5 and 7: the page's third axis (PROGRESS.md, "Seventh chords and 7-limit
	// tetrads": the marvel walk ends 225/224 from its start)
	test_tetrads_on_the_lattice_page {
		var got = Array.new, saved = RCVisuals.sink, four = RLChordContext(RLChordSpace.tetrads(RLEDO(31))), up, walker;
		var marvel = [\K12, \K01, \K12, \K01, \K12, \K02, \K12, \K02, \K01, \K13];
		RCVisuals.sink = { |path, args| got = got.add([path] ++ args) };
		up = RLLattice.show(RLWalk(four, four.space.otonal, marvel));
		this.assertEquals(got[0], ['/lattice/config', 31, 18, 10, 25], "31 steps: its fifth, its major third, its harmonic seventh");
		this.assertEquals(got.drop(2).size, 11, "the eleven chords of the lift");
		this.assertEquals(got[2], ['/lattice/tetrad', 0, 0, 0, 0, 0], "the otonal tetrad on 1/1");
		this.assertEquals(got[3], ['/lattice/tetrad', 1, 1, -1, 1, 1], "K12: the utonal tetrad whose root is 15/14");
		this.assertEquals(got[4], ['/lattice/tetrad', 1, 0, 0, 0, 2], "then K01: a fifth up");
		this.assertEquals(got.last, ['/lattice/tetrad', 2, 2, -1, 0, 10], "the last one two fifths and two thirds up, a seventh down: 225/224");
		this.assertEquals(up.drift, [2, 2, -1], "the lift is returned");
		walker = RLWalker(four, four.space.otonal).follow;
		marvel.do { |name| walker.step(name) };
		this.assertEquals([walker.chord, walker.latticeChord.root], [four.space.otonal, [2, 2, -1]], "a walker's shadow: back on its chord in 31 steps, a comma away on the lattice");
		this.assertEquals(RLLattice.args(walker.latticeChord, walker.count), [2, 2, -1, 0, 10], "what a beat sends for it");
		this.assertEquals(RLLattice.path(walker.latticeChord), '/lattice/tetrad', "to the tetrads' message");
		this.assertException({ RLLattice.args(four.space.otonal) }, Error, "a tetrad that is not on the lattice is refused");
		RCVisuals.sink = saved;
	}

	test_in_a_beat_of_recurrent {
		var walker = RLWalker(ctx, c, [60, 64, 67], 8.1757989156), expected = RLWalk(ctx, c, [\R, \L, \R]).freqs([60, 64, 67], 8.1757989156);
		var beat, stream, evs;
		beat = RCBeat(RCSong(\rlwalker, 1994).layer(\core), \harmony, [type: \rest, dur_flex: [2, 1, 1], freq: walker.asPattern(Pseq([\R, \L], inf))]);
		stream = beat.asStream;
		evs = 4.collect { stream.next(Event.default) };
		4.do { |i| this.assertArrayFloatEquals(evs[i][\freq], expected[i], "event %: the chord of the walk as the beat's freq".format(i), 0.001) };
		this.assertEquals(walker.chord, ctx.space.minor(2), "the walker stands where the beat left it: D minor");
	}
}
