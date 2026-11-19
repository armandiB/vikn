// reLation, scales: RLScale as a pitch system of its degrees. No server.
// Root progressions: Schoenberg, Structural Functions of Harmony, chapter II.
TestRLScale : UnitTest {

	test_major_and_minor_in_several_tunings {
		var edo = RLEDO(12);
		this.assertEquals(RLScale.major(edo).degrees, [0, 2, 4, 5, 7, 9, 11], "C major in 12 steps");
		this.assertEquals(RLScale.major(edo, 2).degrees, [2, 4, 6, 7, 9, 11, 1], "D major, from its tonic");
		this.assertEquals(RLScale.minor(edo, 9).degrees, [9, 11, 0, 2, 4, 5, 7], "A minor, the descending form");
		this.assertEquals(RLScale.minor(edo, 9).alternates, [[5, 6], [6, 8]], "its sixth and seventh degrees have a substitute");
		this.assertEquals(RLScale.major(RLEDO(19), 3).degrees, [3, 6, 9, 11, 14, 17, 1], "the major scale of 19 steps");
		this.assertEquals(RLScale.major(RLEDO(22)).degrees, [0, 4, 7, 9, 13, 16, 20], "of 22 steps: the triads on IV, I and V, two sizes of tone");
		this.assertEquals(RLScale.major(RLJust([3, 5])).degrees, [[0, 0], [2, 0], [0, 1], [-1, 0], [1, 0], [-1, 1], [1, 1]], "the just major scale on the lattice");
		this.assertEquals(RLScale.fifths(edo).degrees, [0, 2, 4, 5, 7, 9, 11], "a chain of fifths, one below and five above");
		this.assertException({ RLScale(edo, [0, 2, 2]) }, Error, "two degrees on one pitch class are refused");
	}

	test_the_pitch_system_of_the_degrees {
		var c = RLScale.major(RLEDO(12)), d = RLScale.major(RLEDO(12), 2);
		this.assertEquals(c.steps, 7, "seven steps");
		this.assertEquals((-2 .. 8).collect { |p| c.toParent(p) }, [-3, -1, 0, 2, 4, 5, 7, 9, 11, 12, 14], "pitches of the scale as pitches of the parent");
		this.assertEquals([-1, 0, 1, 2, 12, 19].collect { |p| c.fromParent(p) }, [-1, 0, nil, 1, 7, 11], "and back, nil outside the scale");
		this.assertFloatEquals(c.height(8), 1 + (2 / 12), "a true height: D an octave up");
		this.assertFloatEquals(d.height(-1), 1 / 12, "the C sharp under D major's tonic");
		this.assertFloatEquals(d.freq(0), 293.66476791741, "D major's pitch 0 sounds D", 0.001);
		this.assertEquals(c.nearest(4, 1.0), 4, "the G nearest an octave up is the one below");
		this.assertEquals(c.pcAdd(5, 4), 2, "degrees add round the scale");
		this.assertEquals(c.name(1), "D", "a degree's name is its note's");
	}

	test_steps_and_their_sizes {
		var c = RLScale.major(RLEDO(12));
		this.assertEquals(Array.series(6, 1).collect { |k| c.specific(k) }, [[2, 1], [4, 3], [5, 6], [7, 6], [9, 8], [11, 10]],
			"every number of steps takes two sizes (Myhill's property)");
		this.assertEquals(c.generic(6), [3, 4], "a tritone is three steps or four");
		this.assertEquals([c.interval([3, 2]), c.interval([5, 4]), c.interval([6, 5]), c.interval([4, 3])], [4, 2, 2, 3], "just ratios as steps");
		this.assertEquals([c.pitchInterval([3, 1]), c.pitchInterval([2, 3])], [11, -4], "with their octaves");
		this.assertEquals(RLScale.major(RLJust([3, 5])).specific(1).size, 3, "the just scale has three sizes of step");
	}

	test_two_forms_of_the_minor {
		var a = RLScale.minor(RLEDO(12), 9);
		this.assert(a.holds(8), "A minor holds G sharp");
		this.assertEquals(a.degreeOf(8), 6, "as its seventh degree");
		this.assert(a.isNatural(8).not, "not in its natural form");
		this.assert(a.holds(10).not, "it does not hold B flat");
		this.assertEquals(a.tones.size, 9, "nine tones in all");
		this.assertEquals(RLScale.major(RLEDO(12)).mode(5).degrees, a.degrees, "the major scale from its sixth degree has the same tones");
	}

	test_chords_of_the_scale {
		var edo = RLEDO(12), c = RLScale.major(edo), a = RLScale.minor(edo, 9), tri = c.triads;
		this.assertEquals(tri.all.collect { |ch| c.numeral(ch) }, ["I", "ii", "iii", "IV", "V", "vi", "viio"], "the seven triads of a major scale");
		this.assertEquals(a.triads.all.collect { |ch| a.numeral(ch) }, ["i", "iio", "III", "iv", "v", "VI", "VII"], "of a minor scale");
		this.assertEquals(c.realize(tri.chord(1)), [2, 5, 9], "ii as pitch classes of the parent");
		this.assertEquals(c.in(RLChordSpace.triads(edo), tri.chord(1)), RLChordSpace.triads(edo).minor(2), "as a chord of the parent");
		this.assertEquals(c.in(RLChordSpace.triads(edo), tri.chord(6)), nil, "the diminished triad is not one of its triads");
		this.assertEquals(c.sevenths.chord(4).pcs, [4, 6, 1, 3], "the seventh chord on V, in degrees");
	}

	test_root_progressions {
		var c = RLScale.major(RLEDO(12)), tri = c.triads;
		this.assertEquals(Array.series(7).collect { |k| c.progressionOf(k) },
			[\same, \superstrong, \descending, \ascending, \descending, \ascending, \superstrong], "the kinds, by steps up from a root");
		this.assertEquals(c.progression(tri.chord(4), tri.chord(0)), \ascending, "V to I: a fourth up");
		this.assertEquals(c.progression(tri.chord(0), tri.chord(5)), \ascending, "I to vi: a third down");
		this.assertEquals(c.progression(tri.chord(0), tri.chord(4)), \descending, "I to V");
		this.assertEquals(c.progression(tri.chord(4), tri.chord(5)), \superstrong, "V to vi, the deceptive progression");
	}

	test_the_machinery_works_on_degrees {
		var edo = RLEDO(12), c = RLScale.major(edo), tri = c.triads, ctx = RLRelationContext.singleStep(tri), graph = ctx.graph;
		this.assertEquals([ctx.objects.size, graph.edgeCount], [7, 7], "one voice by one step: the seven triads in a ring");
		this.assertEquals(graph.neighbours(tri.chord(0)).collect { |ch| c.numeral(ch) }, ["iii", "vi"], "I between iii and vi: the ring of thirds");
		this.assertEquals(RLVoiceLeading.stepsBetween(tri.chord(0), tri.chord(4)), 2, "I to V: two voices, a step each");
		this.assertFloatEquals(RLVoiceLeading.between(tri.chord(0), tri.chord(1)), 500.0, "I to ii in cents: two tones and a semitone");
		this.assertEquals(RLVoiceLeading.through([0, 3, 4, 0].collect { |r| tri.chord(r) }).collect { |v| v.collect { |p| c.toParent(p) } },
			[[0, 4, 7], [0, 5, 9], [2, 7, 11], [4, 7, 12]], "I IV V I by the least motion, as pitches of the parent");
		this.assertEquals(RLRelationContext.pitchClasses(c).act(\I4, 3), [1], "transpositions and inversions of the degrees");
		this.assertEquals(RLVoiceLeading.stepsBetween(RLChordSpace.triads(edo).major(0), RLChordSpace.triads(edo).major(6)), 6, "steps in a plain division are counted as before");
	}

	test_a_scale_is_a_value {
		var edo = RLEDO(12), c = RLScale.major(edo);
		this.assert(c == RLScale.major(edo), "two scales with the same tones are equal");
		this.assert(c != RLScale.major(edo, 2), "not with another tonic");
		this.assert(c != RLScale.minor(edo), "not with another form");
		this.assert((RLEDO(7) == c).not and: { (c == RLEDO(7)).not }, "a scale of seven tones is not seven equal steps, either way round");
		this.assertEquals(c.commonTones(RLScale.major(edo, 7)).size, 6, "C major and G major share six tones");
		this.assertEquals(RLScale.major(RLJust([3, 5])).commonTones(RLScale.minor(RLJust([3, 5]), [-1, 1])).size, 6,
			"on the lattice the relative minor shares six: its fourth degree is a comma away");
	}
}
