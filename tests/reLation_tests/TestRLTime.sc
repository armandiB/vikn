// reLation, time and graded belonging: RLTimeSpans, RLMetre, truth values. No server.
// HomewareSC's Math/Category_Theory/THEORY.md § 1.8, § 1.9 and § 4.
TestRLTimeSpans : UnitTest {
	var time;

	setUp {
		time = RLTimeSpans.new;
		time.define(\same, [1, 1]);
		time.define(\half, [1, 0.5]);
		time.define(\twice, [1, 2]);
	}

	test_the_two_actions {
		this.assertEquals(time.left(2, 3).([1, 0.5]), [5, 1.5], "on the left: stretched by 3, shifted by 2");
		this.assertEquals(time.right(2, 3).([1, 0.5]), [2, 1.5], "on the right: two of its own durations later, three times as long");
		this.assert((time.left(3, 2) <> time.right(1, 0.5)) == (time.right(1, 0.5) <> time.left(3, 2)), "the two commute");
		this.assertEquals(time.interval([0, 1], [3, 2]), [3, 2], "the right step between two spans (Lewin's interval)");
		this.assertEquals(time.right(3, 2).([0, 1]), [3, 2], "which takes the first to the second");
		this.assertEquals(time.leftInterval([1, 0.5], [5, 1.5]), [2, 3], "and the left one");
		this.assert(time.right(1, 2).inverse.(time.right(1, 2).([4, 0.5])) == [4, 0.5], "a step is undone by its inverse");
		this.assert((time.shift(2) <> time.shift(-2)).isIdentity, "a shift and its opposite");
	}

	test_a_rhythm_is_a_walk {
		var walk = RLWalk(time, [0, 1], [\half, \same, \twice, \same]);
		this.assertEquals(walk.chords, [[0, 1], [1, 0.5], [1.5, 0.5], [2, 1], [3, 1]], "the spans of a word of right steps");
		this.assertEquals(walk.composed.right, [3, 1], "the whole word as one step: three durations later, as long");
		this.assertEquals(RLWalk(time, time.left(2, 1.5).([0, 1]), walk.word).chords, walk.chords.collect { |s| time.left(2, 1.5).(s) },
			"the same word from another span is the same rhythm, stretched and shifted");
		this.assertEquals(RLWalk(time, [0, 1], time.right(1, 0.5) ! 4).chords.collect(_[1]), [1, 0.5, 0.25, 0.125, 0.0625],
			"one step repeated: each note half as long as the one before");
		this.assertEquals(time.names, [\half, \same, \twice], "the named steps");
		this.assertEquals(time.define(\bar, [\half, \same, \twice]).right, [2, 1], "a word named as one step");
	}

	test_durations_and_spans {
		var durs = [1, 0.5, 0.5, 1, 1];
		this.assertEquals(time.spans(durs), [[0, 1], [1, 0.5], [1.5, 0.5], [2, 1], [3, 1]], "durations as spans, each starting where the last ends");
		this.assertEquals(time.word(durs).collect(_.right), [[1, 0.5], [1, 1], [1, 2], [1, 1]], "the steps between them: the next note, by the ratio of durations");
		this.assertEquals(RLWalk(time, [0, 1], time.word(durs)).chords, time.spans(durs), "and back");
		this.assertEquals(time.spansOf([0, 3, 4, 8]), [[0, 3], [3, 1], [4, 4]], "the spans between time points");
		this.assertEquals(time.events([[0, 1], [1, 0.5], [3, 1]]), [[1, 1], [2, 0.5], [1, 1]], "spans as time to the next onset and duration");
		this.assertEquals(time.wait([2, 0.5], 3), 2, "the wait until a cue, in the span's own durations");
	}

	test_with_a_sieve {
		var sieve = RCSieve.residues(8, [0, 3, 6], 1/2), spans = time.spansOf(sieve.points(0, 8));
		this.assertEquals(spans.collect(_[1]), [1.5, 1.5, 1, 1.5, 1.5, 1], "the 3, 3, 2 rhythm of a sieve as durations");
		this.assertEquals(time.word(spans.collect(_[1])).collect { |tr| tr.right[1] }.round(0.001), [1, 0.667, 1.5, 1, 0.667], "and as ratios");
	}
}

TestRLMetre : UnitTest {

	// THEORY.md § 1.9: composition is the gcd, intersection the lcm, inclusion divisibility
	test_metres_compose_by_the_gcd {
		var m = RLMetre(12), bad = 0;
		[1, 2, 3, 4, 6, 12].do { |d|
			[1, 2, 3, 4, 6, 12].do { |e|
				if((m.relation(d) <> m.relation(e)) != m.relation(d.gcd(e))) { bad = bad + 1 };
				if((m.relation(d) & m.relation(e)) != m.relation(d.lcm(e))) { bad = bad + 1 };
				if((m.relation(d) <= m.relation(e)) != ((d % e) == 0)) { bad = bad + 1 };
			};
		};
		this.assertEquals(bad, 0, "over the 36 pairs of divisors of 12");
		this.assert(m.relation(12).isIdentity and: { m.relation(1).pairCount == 144 }, "M12 relates a pulse to itself only, M1 every pulse to every pulse");
		this.assertEquals(m.context([4, 6]).category.size, 4, "M4 and M6 generate four relations: the identity, the two, and M2");
	}

	test_shifts_and_dilations_are_automorphisms {
		var ctx = RLMetre(12).context([4, 6]);
		this.assert(RLAutomorphism(ctx, { |x| (x + 1) % 12 }).isValid, "a shift of the pulses changes no metre");
		this.assert(RLAutomorphism(ctx, { |x| (x * 5) % 12 }).isValid, "nor does multiplying them by 5");
		this.assert(RLAutomorphism(ctx, { |x| (x + 1) % 12 }).onArrow(\M4) == ctx.transform(\M4), "M4 stays M4");
		this.assertException({ RLAutomorphism(ctx, { |x| (x * 2) % 12 }) }, Error, "multiplying by 2 is not one to one");
	}

	test_canons {
		var m = RLMetre(12);
		this.assert(m.tiles([0, 1, 2], [0, 3, 6, 9]), "three pulses in a row, entering every three: every pulse once");
		this.assert(m.tiles([0, 1, 2], [0, 2, 6, 9]).not, "other entries leave a hole");
		this.assertEquals(m.complements([0, 1, 5]), [[0, 3, 6, 9]], "the entries with which a rhythm tiles the cycle");
		this.assertEquals(m.complements([0, 1, 3]), [], "a rhythm with none");
		this.assertEquals(m.complements([0, 1, 2, 3, 4]), [], "a rhythm whose size does not divide the cycle");
		this.assertEquals(RLMetre(16).complements([0, 1, 4, 5]), [[0, 2, 8, 10], [0, 6, 8, 14]], "16 pulses: two sets of entries");
	}
}

TestRLTruth : UnitTest {

	// THEORY.md § 1.8: Noll's monoid for C major, six truth values, a hierarchy of the twelve pitch classes
	test_noll_s_monoid {
		var edo = RLEDO(12), noll = RLRelationContext.preserving(edo, [0, 4, 7]), cat = noll.category, classes = Dictionary.new;
		var gen = RLCategory([['3x+7', noll.transform('3x+7')], ['8x+4', noll.transform('8x+4')]]), found;
		this.assertEquals(noll.names.size, 8, "eight maps x -> ux + v send C, E, G into C, E, G");
		this.assert((cat.size == 8) and: { cat.isMonoid } and: { cat.isGroup.not }, "a monoid of eight, not a group");
		this.assertEquals(gen.size, 8, "generated by 3x + 7 and 8x + 4");
		this.assertEquals(cat.leftIdeals.size, 6, "six truth values");
		noll.truthValues([0, 4, 7]).do { |tv, pc| classes[tv] = (classes[tv] ? []) ++ [edo.name(pc)] };
		found = classes.keys.asArray.collect { |k| [k.size, classes[k]] };
		this.assertEquals(found.size, 5, "the twelve pitch classes take five of them");
		this.assert([[8, ["C", "E", "G"]], [7, ["Eb"]], [5, ["Ab", "B"]], [5, ["C#", "F#", "A", "Bb"]], [3, ["D", "F"]]].every { |row| found.includesEqual(row) },
			"C, E, G; then Eb; then Ab, B and C#, F#, A, Bb; then D, F");
		this.assert(noll.truthValues([0, 4, 7]).every { |tv| cat.leftIdeals.includesEqual(tv) }, "each one a left ideal");
		this.assertEquals(noll.belonging([0, 4, 7]), [1, 0.625, 0.375, 0.875, 1, 0.375, 0.625, 1, 0.625, 0.625, 0.625, 0.625],
			"as control data: the share of the maps that bring each pitch class into the chord");
	}

	test_a_group_has_two_truth_values {
		var pcs = RLRelationContext.pitchClasses(RLEDO(12));
		this.assertEquals(pcs.category.truthValue(0, RLEDO(12).pcs).size, 24, "every arrow keeps the whole");
		this.assertEquals(RLRelationContext.preserving(RLEDO(19), [0, 6, 11]).names.size, 4, "in 19 steps four maps keep the triad");
	}
}
