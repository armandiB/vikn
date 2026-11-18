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

	test_a_canon_is_one_word_under_left_steps {
		var spans = time.spans([1, 0.5, 0.5, 1, 1]), voices = time.canon(spans, [[0, 1], [0, 2], [2, 0.5]]);
		var steps = { |v| (v.size - 1).collect { |i| time.interval(v[i], v[i + 1]) } };
		this.assertEquals(voices[0], spans, "the identity gives the leader");
		this.assertEquals(voices[1], [[0, 2], [2, 1], [3, 1], [4, 2], [6, 2]], "twice as slow");
		this.assertEquals(voices[2], [[2, 0.5], [2.5, 0.25], [2.75, 0.25], [3, 0.5], [3.5, 0.5]], "twice as fast, two beats later");
		this.assert(voices.every { |v| steps.(v) == steps.(spans) }, "every voice has the leader's word: the same right steps");
		this.assertEquals(time.endTogether(spans, 0.5), [2, 0.5], "the voice twice as fast that ends with the leader enters at 2");
		this.assertEquals(time.endTogether(spans, 2), [-4, 2], "the one twice as slow would have started 4 before");
		this.assertEquals(time.canon(spans, [time.endTogether(spans, 0.25)])[0].last, [3.75, 0.25], "its last note ends where the leader's does");
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

	// Canons by augmentation on a line of pulses: each voice the rhythm at its own stretch, every pulse once.
	// The numbers are those of a search over every case in Python (HomewareSC, Math/Category_Theory, Nov 18 2026).
	test_canons_by_augmentation {
		var perfect = RLMetre.perfect([0, 1, 2], 5), sounded;
		this.assertEquals(RLMetre(12).augmented([0, 1, 2], [1, 1, 2, 2]).size, 3, "two voices at 1 and two at 2 tile 12 pulses three ways");
		this.assert(RLMetre(12).augmented([0, 1, 2], [1, 1, 2, 2]).includesEqual([0, 3, 6, 7]), "one of them: entries 0, 3 and 6, 7");
		[2, 3, 4, 6].do { |k|
			this.assertEquals(RLMetre.perfect([0, 1, 2], k).size, 0, "three pulses in a row, % voices each at its own stretch: no tiling".format(k));
		};
		this.assertEquals(perfect.size, 2, "five voices: two tilings of 15 pulses");
		this.assert(perfect.every { |pair| pair[0] == [1, 2, 4, 5, 7] }, "both with the stretches 1, 2, 4, 5, 7");
		this.assert(perfect.collect(_[1]).includesEqual([10, 2, 1, 3, 0]) and: { perfect.collect(_[1]).includesEqual([2, 8, 5, 1, 0]) },
			"entering at 10, 2, 1, 3, 0 or at 2, 8, 5, 1, 0");
		this.assertEquals([10, 2, 1, 3, 0].collect { |u, i| 14 - (u + (2 * [1, 2, 4, 5, 7][i])) }, [2, 8, 5, 1, 0], "each the other read backwards");
		sounded = [1, 2, 4, 5, 7].collect { |d, i| [0, 1, 2].collect { |r| [10, 2, 1, 3, 0][i] + (d * r) } }.flat.sort;
		this.assertEquals(sounded, Array.series(15), "every pulse sounded once");
	}
}

// what the canon page of HomewareVisuals is sent (its protocol: public/lib/canon-viz.js there)
TestRLCanonPage : UnitTest {
	var got, saved;

	setUp {
		got = Array.new;
		saved = RCVisuals.sink;
		RCVisuals.sink = { |path, args| got = got.add([path] ++ args) };
	}

	tearDown { RCVisuals.sink = saved }

	test_a_tiling_canon {
		var pair = RLMetre.perfect([0, 1, 2], 5).detect { |p| p[1] == [10, 2, 1, 3, 0] }, spans;
		RLCanonPage.canon([[0, 1], [1, 1], [2, 1]], pair[1].collect { |u, i| [u, pair[0][i]] }, nil, 15, "a perfect tiling");
		spans = got.select { |m| m[0] == '/canon/span' };
		this.assertEquals(got.keep(4), [['/canon/clear'], ['/canon/title', "a perfect tiling"], ['/canon/pulses', 15, 1], ['/canon/voice', 0, "[10, 1]"]],
			"a clear, the title, the pulses, then each voice named by its left step");
		this.assertEquals([got.size, spans.size], [23, 15], "five voices of three notes");
		this.assertEquals(spans.last, ['/canon/span', 4, 14, 7], "the last note of the slowest voice: from 14, 7 pulses long");
		this.assertEquals(spans.collect(_[2]).sort, Array.series(15), "the onsets are the 15 pulses, each once");
	}

	test_heights_and_the_head {
		RLCanonPage.show([[[0, 1], [1, 1]], [[0, 2]]], ["high", "low"], [[72, 60], [48]]);
		this.assertEquals(got.select { |m| m[0] == '/canon/span' }.collect(_.last), [1, 0.5, 0], "heights scaled together between 0 and 1");
		this.assertEquals(got.select { |m| m[0] == '/canon/voice' }.collect(_.last), ["high", "low"], "the names given");
		got = Array.new;
		RLCanonPage.head(2.5);
		RLCanonPage.head;
		this.assertEquals(got, [['/canon/head', 2.5], ['/canon/head', -1]], "the head, and none");
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
