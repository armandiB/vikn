// reLation, seventh chords of 12 steps beyond the tetrads' six contextual inversions: Childs's operations on
// dominant and half-diminished sevenths, Pipeline, and the 17 operations of Cannas, Antonini and Pernazza on the
// five classical types. No server. The tables are those of the study notes (HomewareSC-data,
// references/transformational_theory/notes/08_articles_algebra.md § 3.3, § 4 and § 7; 07, Douthett and Steinbach).
TestRLSeventhChords : UnitTest {
	var edo, sp, ctx, moved;

	setUp {
		edo = RLEDO(12);
		sp = RLChordSpace(edo, [[\dom7, [0, 4, 7, 10]], [\hdim7, [0, 3, 6, 10]]]);
		ctx = RLChordContext(sp);
		// how the voices move from one chord to another by the least motion: the steps that are not zero, signed
		moved = { |x, y|
			var from = x.pcs.collect { |c| edo.nearest(c, 0.0) }, to = RLVoiceLeading.minimal(edo, from, y.pcs);
			(to - from).reject { |d| d == 0 }
		};
	}

	// Childs's S operations hold two tones and move two by a semitone the same way. By Hook's numbers they are
	// the wechsels W0, W1, W7, W6, W9 and W10: five contextual inversions, and the sixth after a tritone.
	test_childs_s_operations_are_wechsels {
		var s = [[ctx.k(0, 3), 0], [ctx.k(1, 2), 1], [ctx.k(2, 3), 7], [ctx.k(0, 1), 6], [ctx.k(0, 2), 9], [ctx.t(6) <> ctx.k(1, 3), 10]];
		var bad = 0, flip = moved.(sp.chord(0, \dom7), ctx.k(1, 3).(sp.chord(0, \dom7)));
		s.do { |pair| this.assert(pair[0] == ctx.wechsel(pair[1]), "the wechsel W%".format(pair[1])) };
		this.assertEquals(ctx.k(0, 3).(sp.chord(5, \dom7)), sp.chord(5, \hdim7), "S2(3): F7 to F half-diminished");
		this.assertEquals([ctx.k(1, 2).(sp.chord(0, \dom7)), ctx.k(1, 2).(sp.chord(0, \hdim7))], [sp.chord(1, \hdim7), sp.chord(11, \dom7)],
			"S3(2): C7 to C# half-diminished, C half-diminished to B7");
		s.do { |pair|
			sp.all.do { |x|
				var m = moved.(x, pair[0].(x));
				if((m.size != 2) or: { m.abs != [1, 1] } or: { m[0] != m[1] }) { bad = bad + 1 };
			};
		};
		this.assertEquals(bad, 0, "each keeps two tones and moves two by a semitone the same way, on all 24 chords");
		this.assert(ctx.k(1, 3) == ctx.wechsel(4), "the sixth contextual inversion is W4");
		this.assertEquals(flip.abs, [2], "it keeps three tones and moves one by a whole tone (Gollin's flip, L13 below)");
	}

	// His C operations move the two voices opposite ways: the schritts by 3, 6 and 9.
	test_childs_c_operations_are_schritts {
		var bad = 0;
		[3, 6, 9].do { |v|
			sp.all.do { |x|
				var m = moved.(x, ctx.schritt(v).(x));
				if((m.size != 2) or: { m.abs != [1, 1] } or: { m[0] != m[1].neg }) { bad = bad + 1 };
			};
		};
		this.assertEquals(bad, 0, "each keeps two tones and moves two by a semitone opposite ways, on all 24 chords");
		this.assertEquals([ctx.schritt(3).(sp.chord(0, \dom7)), ctx.schritt(3).(sp.chord(0, \hdim7))], [sp.chord(3, \dom7), sp.chord(9, \hdim7)],
			"a schritt: C7 up to Eb7, C half-diminished down to A half-diminished");
		this.assert((ctx.schritt(3) <> ctx.i(5)) == (ctx.i(5) <> ctx.schritt(3)), "schritts commute with inversions");
		this.assert((ctx.wechsel(1) <> ctx.t(4)) == (ctx.t(4) <> ctx.wechsel(1)), "and wechsels with transpositions");
		this.assert([ctx.schritt(1), ctx.schritt(11)].includesEqual(ctx.wechsel(1) <> ctx.wechsel(0)), "two wechsels in a row are a schritt");
	}

	// Pipeline (Douthett and Steinbach): 24 chords, two voices by a semitone each; its nine edges at a chord are
	// Childs's nine operations.
	test_pipeline {
		var pipe = RLRelationContext.pipeline, g = pipe.graph, bad = 0;
		var nine = [ctx.k(0, 3), ctx.k(1, 2), ctx.k(2, 3), ctx.k(0, 1), ctx.k(0, 2), ctx.t(6) <> ctx.k(1, 3), ctx.schritt(3), ctx.schritt(6), ctx.schritt(9)];
		this.assertEquals([pipe.objects.size, g.edgeCount, g.degrees.asSet, g.components.size], [24, 108, Set[9], 1],
			"24 chords, 108 edges, nine at each chord, in one piece");
		this.assertEquals(pipe.objects.collect { |x| x.pcs.sum % 12 }.asSet.size, 6, "six voice-leading zones");
		pipe.objects.do { |x|
			var images = nine.collect { |tr| tr.(x) }, next = g.neighbours(x);
			if((images.asSet.size != 9) or: { images.any { |y| next.includesEqual(y).not } }) { bad = bad + 1 };
		};
		this.assertEquals(bad, 0, "the nine operations give each chord its nine neighbours");
	}

	// Cannas, Antonini and Pernazza: one voice by one or two semitones, between the five types.
	test_the_seventeen_operations {
		var w = RLWreath(12, 5), all = RLChordSpace.sevenths, census = w.census(all), sets = Array.new;
		var expected = [[0, 1, 0], [0, 3, 0], [1, 2, 0], [2, 4, 0], [0, 1, 9], [1, 2, 9], [1, 3, 3], [2, 4, 9], [2, 4, 3], [0, 2, 4],
			[0, 4, 4], [1, 3, 8], [2, 3, 11], [0, 4, 1], [2, 4, 6], [0, 4, 10], [0, 4, 7]];
		this.assertEquals(census.size, 17, "17 of them come out of the search");
		this.assert(expected.every { |e| census.includesEqual(e) }, "P12, P14, P23, P35, R12, R23, R42, R35, R53, L13, L15, L42, Q43, Q15, RR35, QQ51, N51");
		this.assert(census.every { |e| (w.swap(e[0], e[1], e[2])[0].sum % 12) == 0 }, "the shifts of each sum to zero");
		this.assert(census.every { |e| var u = w.swap(e[0], e[1], e[2]); w.mul(u, u) == w.identity }, "each undoes itself");
		this.assertEquals(w.act(w.swap(0, 2, 4), all.chord(0, \dom7)), all.chord(4, \hdim7), "L13: C7 to E half-diminished");
		this.assertEquals(w.act(w.swap(0, 2, 4), all.chord(0, \maj7)), all.chord(0, \maj7), "and the chords of the other types stay");
		census.do { |e|
			var key = [e[0], e[1], all.chord(e[2], all.typeAt(e[1])).pcs.sort];
			if(sets.includesEqual(key).not) { sets = sets.add(key) };
		};
		this.assertEquals(sets.size, 11, "as moves of notes they are 11: a diminished seventh has four roots");
		this.assertEquals(w.census(all, [1]).size, 14, "14 of the 17 move the voice by one semitone");
	}

	// Their theorem, by its proof: the operations generate every permutation of the types and every list of
	// shifts that sums to zero, 120 x 12^4 = 2 488 320 elements.
	test_the_group_they_generate {
		var w = RLWreath(12, 5), r42 = w.swap(1, 3, 3), l42 = w.swap(1, 3, 8);
		this.assertEquals(w.generated([w.swap(0, 1, 0), w.swap(0, 3, 0), w.swap(1, 2, 0), w.swap(2, 4, 0)]).size, 120,
			"the four P generate the 120 permutations of the five types");
		this.assertEquals(w.mul(r42, l42), [[0, 5, 0, 7, 0], Array.series(5)],
			"R42 after L42 changes no type: minor sevenths down by 7, major sevenths up by 7; 7 has no common factor with 12");
		this.assertEquals(w.generated([r42, l42]).size, 24, "R42 and L42 alone, on major and minor sevenths: a group of 24 (Kerkez's)");
		this.assertEquals(RLWreath(12, 2).generated([[[9, 3], [1, 0]], [[4, 8], [1, 0]]]).size, 24, "as R and L on triads");
		this.assertEquals(w.generated([r42, l42], 10), nil, "past the size allowed, nil");
	}
}
