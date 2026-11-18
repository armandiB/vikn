// reLation, chords of four tones: the tetrads 4:5:6:7 and their inversion in an RLChordContext, the seventh
// chords of 12 steps as a graph. No server. HomewareSC's Math/Category_Theory/THEORY.md § 3.5, H1_CONNECTION.md.
TestRLTetrads : UnitTest {
	var just, jctx, words;

	setUp {
		just = RLJust([3, 5, 7]);
		jctx = RLChordContext(RLChordSpace.tetrads(just));
		// two contextual inversions in a row transpose an otonal chord: fifths, thirds and sevenths as steps
		words = IdentityDictionary[
			\marvel -> [\K12, \K01, \K12, \K01, \K12, \K02, \K12, \K02, \K01, \K13],      // two fifths and two thirds up, a seventh down
			\septimal -> [\K12, \K01, \K12, \K01, \K13, \K01],                            // two fifths up, a seventh up
			\jubilee -> [\K01, \K03, \K01, \K03],                                         // two tritones 7/5
			\quarter -> [\K12, \K01, \K12, \K01, \K02, \K12, \K01, \K13]                  // two fifths up, a third and a seventh down
		];
	}

	test_the_tetrads_of_four_systems {
		this.assertEquals(RLChordSpace.tetrads(RLEDO(12)).types, [[\otonal, [0, 4, 7, 10]], [\utonal, [0, 3, 6, 10]]], "12 steps: a dominant and a half-diminished seventh");
		this.assertEquals(RLChordSpace.tetrads(RLEDO(22)).pattern(\otonal), [0, 7, 13, 18], "22 steps");
		this.assertEquals(RLChordSpace.tetrads(RLEDO(31)).pattern(\utonal), [0, 7, 15, 25], "31 steps");
		this.assertEquals(jctx.space.otonal.pcs.collect { |x| just.name(x) }, ["1/1", "5/4", "3/2", "7/4"], "just: 4:5:6:7");
		this.assertEquals(jctx.space.utonal.pcs.collect { |x| just.name(x) }, ["1/1", "7/6", "7/5", "7/4"], "and the same upside down");
		this.assertException({ RLChordSpace.tetrads(RLJust([3, 5])) }, Error, "a lattice without the prime 7 is refused");
	}

	test_six_contextual_inversions {
		[RLEDO(12), RLEDO(22), RLEDO(31), just].do { |sys|
			var ctx = RLChordContext(RLChordSpace.tetrads(sys)), bad = 0;
			var chords = [ctx.space.otonal, ctx.space.utonal, ctx.space.otonal(sys.interval([3, 2])), ctx.space.utonal(sys.interval([7, 4]))];
			this.assertEquals(ctx.generators, [\K01, \K02, \K03, \K12, \K13, \K23], "%: one for each pair of tones".format(sys));
			chords.do { |x|
				4.do { |i| 4.do { |j|
					var y, a, b;
					if(j > i) {
						y = ctx.k(i, j).(x);
						// tones i and j of an otonal chord; on a utonal chord, the tones that answer them upside down
						#a, b = if(x.type == \otonal) { [i, j] } { [3 - i, 3 - j] };
						if((y.type == x.type) or: { y.pcs.includesEqual(x.pcs[a]).not } or: { y.pcs.includesEqual(x.pcs[b]).not }) { bad = bad + 1 };
						if(ctx.k(i, j).(y) != x) { bad = bad + 1 };
					};
				} };
			};
			this.assertEquals(bad, 0, "%: each turns the chord over, keeps its two tones and undoes itself".format(sys));
		};
		this.assertEquals(RLChordContext(RLChordSpace.tetrads(RLEDO(12))).generators.collect { |k|
			var ctx = RLChordContext(RLChordSpace.tetrads(RLEDO(12)));
			ctx.transform(k).(ctx.space.otonal(0)).commonPcs(ctx.space.otonal(0)).size
		}, [2, 2, 2, 2, 3, 2], "12 steps: K13 keeps three tones (C7 to E half-diminished), the others two");
	}

	test_triads_keep_their_names {
		var ctx = RLChordContext(RLChordSpace.triads(RLEDO(12)));
		this.assertEquals(ctx.generators, [\P, \L, \R], "P, L, R");
		this.assertEquals(ctx.names, [\L, \P, \R], "and no other name");
		this.assertEquals([ctx.transform(\K02), ctx.transform(\K12), ctx.transform(\K01)], [ctx.p, ctx.l, ctx.r], "K02, K12, K01 are other spellings of P, L, R");
		this.assertEquals(ctx.k(2, 0), ctx.p, "by the two tones exchanged");
		this.assertEquals(RLChordContext(RLChordSpace.pair(RLEDO(12), \up, [0, 5], \down)).generators, [\K01], "two tones: one contextual inversion");
	}

	test_left_and_right_commute {
		var ctx = RLChordContext(RLChordSpace.tetrads(RLEDO(12))), bad = 0;
		ctx.space.all.do { |x|
			[ctx.t(5), ctx.i(3)].do { |g| ctx.generators.do { |k|
				if((g <> ctx.transform(k)).(x) != (ctx.transform(k) <> g).(x)) { bad = bad + 1 };
			} };
		};
		this.assertEquals(bad, 0, "T and I commute with the six contextual inversions on the 24 tetrads");
		this.assertEquals(ctx.i(0).(ctx.space.otonal(0)).pcs.asSet, Set[0, 8, 5, 2], "I0 turns C7 into D half-diminished");
		this.assertEquals(ctx.rightInterval(ctx.space.otonal(0), ctx.space.utonal(4)).label, "K13", "an interval named by a contextual inversion");
	}

	// THEORY.md § 3: a closed walk of a division lifts to a comma it tempers out, now in the 7-limit
	test_seventh_limit_commas {
		var expected = [
			[\marvel, [2, 2, -1], 7.7115, "marvel comma 225/224", [true, true, true]],
			[\septimal, [2, 0, 1], -27.2641, "septimal comma 64/63", [true, true, false]],
			[\jubilee, [0, 2, -2], 34.9756, "jubilisma 50/49", [true, true, false]],
			[\quarter, [2, -1, -1], 48.7704, "septimal quarter tone 36/35", [true, false, false]]
		];
		expected.do { |row|
			var walk = RLWalk(jctx, jctx.space.otonal, words[row[0]]);
			this.assertEquals(walk.drift, row[1], "%: where the walk ends on the lattice".format(row[0]));
			this.assertFloatEquals(walk.driftCents, row[2], "in cents", 0.001);
			this.assertEquals(just.commaName(walk.drift), row[3], "a known comma");
			this.assertEquals([12, 22, 31].collect { |n|
				var ctx = RLChordContext(RLChordSpace.tetrads(RLEDO(n)));
				RLWalk(ctx, ctx.space.otonal(0), words[row[0]]).isClosed
			}, row[4], "%: closed or not in 12, 22 and 31 steps".format(row[0]));
		};
	}

	test_temperaments_and_lifts {
		var bad = 0, closed = 0;
		thisThread.randSeed = 7;
		[12, 22, 31].do { |n|
			var edo = RLEDO(n), ctx = RLChordContext(RLChordSpace.tetrads(edo)), temper = RLMorphism.temper(jctx, ctx);
			this.assert(temper.isValid, "% steps: the val commutes with the six contextual inversions".format(n));
			500.do {
				var word = { ctx.generators.choose } ! (2 * (1 + 5.rand)), walk = RLWalk(ctx, ctx.space.otonal(0), word), up;
				if(walk.isClosed) {
					closed = closed + 1;
					up = temper.lift(walk);
					if(up.drift.isNil or: { just.temperPc(edo, up.drift) != 0 }) { bad = bad + 1 };
				};
			};
		};
		this.assert(closed > 40, "enough closed walks were drawn (%)".format(closed));
		this.assertEquals(bad, 0, "each of the % closed walks lifts to a walk ending in the kernel".format(closed));
	}

	test_four_voices {
		var ctx = RLChordContext(RLChordSpace.tetrads(RLEDO(12))), walk = RLWalk(ctx, ctx.space.otonal(0), words[\jubilee]);
		this.assertEquals(walk.voices.first, [0, 4, 7, 10], "close position");
		this.assertEquals(walk.voices.collect(_.size), 4 ! 5, "four voices at each chord");
		this.assertEquals(walk.chords.collect(_.name), ["C otonal", "F# utonal", "F# otonal", "C utonal", "C otonal"], "C7, F# half-diminished, F#7, C half-diminished, C7");
		this.assertEquals(walk.voiceDrift, [0, 0, 0, 0], "the voices come back in place");
		this.assertEquals(walk.voiceCents.round.asInteger, [200, 200, 200, 200], "each step moves two voices by a semitone");
	}
}

TestRLPowerTowers : UnitTest {

	// H1_CONNECTION.md § 3: Power Towers, 39 seventh chords, graph distance = least voice leading on 741 pairs
	test_power_towers {
		var pt = RLRelationContext.powerTowers, g = pt.graph, same = 0, diff = 0;
		this.assertEquals([pt.objects.size, g.edgeCount], [39, 72], "39 seventh chords, 72 edges");
		this.assertEquals(g.degrees.asSet, Set[3, 4, 8], "a diminished seventh has eight neighbours");
		this.assertEquals(pt.space.distinct.count { |x| x.type == \dim7 }, 3, "three diminished sevenths");
		pt.objects.do { |x, i|
			var dist = g.distances(x);
			pt.objects.do { |y, j| if(j > i) { if(dist[j] == RLVoiceLeading.stepsBetween(x, y)) { same = same + 1 } { diff = diff + 1 } } };
		};
		this.assertEquals([same, diff], [741, 0], "the steps between two chords are their least voice leading, for all 741 pairs");
		this.assertEquals(pt.act(\S, pt.space.chord(0, \dim7)).size, 8, "from C diminished seventh: four dominant and four half-diminished sevenths");
	}

	test_single_step_graphs {
		var cube = RLRelationContext.singleStep(RLRelationContext.cubeDance.space);
		this.assertEquals(cube.transform(\S), RLRelationContext.cubeDance.union.without(cube.identityTransform), "on major, minor and augmented triads it is Cube Dance");
		this.assertEquals(RLRelationContext.singleStep(RLChordSpace.triads(RLEDO(12)), [2]).transform(\S).pairCount, 24, "one voice by a whole tone on the 24 triads: R");
	}
}
