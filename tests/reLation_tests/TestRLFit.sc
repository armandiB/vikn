// reLation, reading a piece: RLScore and RLFit, on progressions whose reading is known. No server. The costs are
// the hand-set model's; a counted model is checked in HomewareSC (Math/Category_Theory/fit) on a corpus.
TestRLFit : UnitTest {

	prTriads { ^RLScale.major(RLEDO(12)).triads }
	prIn { |scale, degrees| ^RLFit.events(degrees.collect { |d| scale.triads.chord(d) }, scale) }

	test_periods_of_a_word {
		this.assertEquals(RLFit.periods([3, 3, 3, 3]).collect { |p| [p[\start], p[\period], p[\length]] }, [[0, 1, 4]], "one step repeated");
		this.assertEquals(RLFit.periods([5, 3, 5, 3, 5, 3, 1]).collect { |p| [p[\start], p[\unit], p[\repeats]] }, [[0, [5, 3], 3]], "a unit of two, three times; not its double");
		this.assertEquals(RLFit.periods([1, 3, 3]), [], "two equal steps are not yet a pattern");
		this.assertEquals(RLFit.periods([0, 0, 0, 0]), [], "a root that stays is none");
		this.assertEquals(RLFit.periods([3, 3, nil, 3, 3]), [], "an unknown step breaks a run");
		this.assertEquals(RLFit.periods([\P, \L, \P, \L]).collect { |p| p[\unit] }, [[\P, \L]], "words of any kind");
	}

	test_a_ring_of_fifths_in_one_key {
		var c = RLScale.major(RLEDO(12)), fit = RLFit(this.prIn(c, [0, 3, 6, 2, 5, 1, 4, 0]), RLRegions(RLEDO(12)));
		var seg = fit.segments.first;
		this.assertEquals(fit.segments.size, 1, "one passage");
		this.assertEquals([seg[\kind], seg[\region], seg[\name]], [\region, [0, \major], \T], "in C major, the tonic region");
		this.assertEquals(seg[\numerals], ["I", "IV", "viio", "iii", "vi", "ii", "V", "I"], "its chords as Roman numerals");
		this.assertEquals(seg[\word], 3 ! 7, "the roots a fourth up, seven times");
		this.assertEquals(seg[\progressions], \ascending ! 7, "ascending progressions");
		this.assertEquals(seg[\patterns].collect { |p| [p[\unit], p[\repeats]] }, [[[3], 7]], "found as one pattern");
		this.assertEquals(fit.surprises, [], "nothing stands out");
		this.assertFloatEquals(fit.segments.sum { |s| s[\cost] }, fit.cost, "the passages' costs add up to the fit's");
	}

	test_a_broken_pattern_is_a_surprise {
		var c = RLScale.major(RLEDO(12)), fit = RLFit(this.prIn(c, [0, 3, 6, 2, 5, 1, 3, 4, 0]), RLRegions(RLEDO(12)));
		var s = fit.surprises.first;
		this.assertEquals(fit.surprises.size, 1, "one surprise");
		this.assertEquals([s[\kind], s[\at], s[\expected], s[\actual], s[\by]], [\break, 6, "V", "IV", -1], "after ii the ring asks for V: IV came, one step under");
	}

	test_two_keys {
		var edo = RLEDO(12), c = RLScale.major(edo), g = RLScale.major(edo, 7);
		var fit = RLFit(this.prIn(c, [0, 3, 4, 0, 5, 1, 4, 0]) ++ this.prIn(g, [4, 0, 3, 4, 0, 1, 4, 0]), RLRegions(edo));
		this.assertEquals(fit.segments.collect { |s| [s[\first], s[\last], s[\name]] }, [[0, 7, \T], [8, 15, \D]], "C major, then its dominant region, cut where it was made");
		this.assertEquals(fit.segments[1][\numerals], ["V", "I", "IV", "V", "I", "ii", "V", "I"], "the second passage read in G major");
		this.assertFloatEquals(fit.segments.sum { |s| s[\cost] }, fit.cost, "the passages' costs add up, the change of region counted");
	}

	test_the_triads_take_a_hexatonic_cycle {
		var edo = RLEDO(12), ctx = RLChordContext(RLChordSpace.triads(edo));
		var fit = RLFit(RLFit.events(RLWalk.cycle(ctx, ctx.space.major(0), [\P, \L]).chords), RLRegions(edo)), seg = fit.segments.first;
		this.assertEquals([fit.segments.size, seg[\kind]], [1, \triads], "no key holds it: one passage among the triads");
		this.assertEquals(seg[\word], [\P, \L, \P, \L, \P, \L], "its word");
		this.assertEquals(seg[\patterns].collect { |p| [p[\unit], p[\repeats]] }, [[[\P, \L], 3]], "P then L, three times");
		this.assertEquals(fit.title(seg), "triads", "its title");
	}

	test_artificial_dominants_and_foreign_chords {
		var edo = RLEDO(12), reg = RLRegions(edo), fit, seg;
		var one = (pcs: [0, 4, 7], root: 0), four = (pcs: [5, 9, 0], root: 5), five = (pcs: [7, 11, 2], root: 7);
		// C major: I IV V I, V7 of V, V, I, a D flat major triad, I IV V I
		fit = RLFit([one, four, five, one, (pcs: [2, 6, 9, 0], root: 2), five, one, (pcs: [1, 5, 8], root: 1), one, four, five, one], reg);
		seg = fit.segments.first;
		this.assertEquals(fit.segments.size, 1, "one passage");
		this.assertEquals(seg[\numerals], ["I", "IV", "V", "I", "V7/V", "V", "I", "*C#", "I", "IV", "V", "I"],
			"an artificial dominant is named by where it leads, a foreign root by its note");
		this.assertEquals(fit.surprises.collect { |s| [s[\kind], s[\at]] }, [[\foreign, 7]], "the chord that leads nowhere is the surprise");
	}

	test_minor_with_its_substitutes {
		var edo = RLEDO(12), fit;
		// A minor: i iv V i, the dominant with the substitute seventh degree
		fit = RLFit([(pcs: [9, 0, 4], root: 9), (pcs: [2, 5, 9], root: 2), (pcs: [4, 8, 11], root: 4), (pcs: [9, 0, 4], root: 9)], RLRegions(edo, 9, \minor));
		this.assertEquals(fit.segments.collect { |s| [fit.title(s), s[\numerals]] }, [["A minor (t)", ["i", "iv", "V", "i"]]], "read in A minor, its V inside the key");
	}

	test_agreement_with_written_keys {
		var edo = RLEDO(12), c = RLScale.major(edo), chords = this.prIn(c, [0, 3, 4, 0]);
		chords.do { |ch| ch[\key] = [0, \major] };
		chords[3][\key] = [1, \major];
		this.assertEquals(RLFit(chords, RLRegions(edo)).agreement, [3, 4], "three quarter notes of four in the key an analyst wrote");
	}

	test_models {
		var m = RLFit.handModel, dearer = RLFit.handModel(IdentityDictionary[\region -> 10]);
		this.assertEquals([m[\major][\first].size, m[\major][\step].size, m[\change][\minor].size], [9, 9, 6], "nine kinds of chord, six classes of change");
		this.assertFloatEquals(m[\major][\step][4][0], 1.0, "V to I: an ascending progression onto the tonic");
		this.assertFloatEquals(m[\major][\step][0][4], 1.5, "I to V: a descending one");
		this.assertFloatEquals(dearer[\change][\major][1], 10, "an entry changed");
		this.assertException({ RLFit.readModel("/nonexistent/model.json") }, Error, "a missing model file is an error");
	}

	test_a_score_and_its_coverage {
		var edo = RLEDO(12), notes, score, fit;
		// C E G for two quarter notes with a passing D, then F A C
		notes = [[0, 2, 0], [0, 2, 4], [0, 1, 7], [1, 1, 2], [2, 2, 5], [2, 2, 9], [2, 2, 0]].collect { |n| (onset: n[0], dur: n[1], pitch: n[2], pc: n[2] % 12, mn: 1) };
		score = RLScore(edo, notes);
		this.assertEquals(score.duration, 4, "its length");
		this.assertEquals(score.weights(0, 2)[2], 1, "the D sounds one quarter note");
		this.assertEquals(score.coverage(0, 2, [0, 4, 7]), [5, 6], "the C major triad holds five quarter notes of six");
		this.assertEquals(score.sounding(1.5, 2.5).size, 6, "notes sounding across a span");
		fit = RLFit([(pcs: [0, 4, 7], root: 0, onset: 0, dur: 2), (pcs: [5, 9, 0], root: 5, onset: 2, dur: 2),
			(pcs: [7, 11, 2], root: 7, onset: 4, dur: 2), (pcs: [0, 4, 7], root: 0, onset: 6, dur: 2)], RLRegions(edo));
		this.assertEquals(fit.coverage(score).first[\chords], [11, 12], "the passage's chords hold eleven of twelve");
		this.assertEquals(fit.coverage(score).first[\natural], [12, 12], "its scale holds all");
		this.assert(fit.report(score).contains("C major (T)"), "a report names the passage");
	}

	test_spelled_notes_in_other_divisions {
		this.assertEquals(RLScore.pitchOf(RLEDO(12), 68, -4), 8, "A flat above middle C in 12 steps");
		this.assertEquals(RLScore.pitchOf(RLEDO(19), 68, -4), 13, "in 19 steps");
		this.assertEquals(RLScore.pitchOf(RLEDO(19), 68, 8), 12, "G sharp is a step lower there");
		this.assertEquals(RLScore.pitchOf(RLEDO(12), 53, -1), -7, "the F below middle C");
	}
}
