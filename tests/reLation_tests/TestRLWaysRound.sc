// reLation, the lists of ways round: the closed words of a chord context and the cycles of a graph. No server.
// The counts for triads and for Cube Dance are those of a search written from the definitions in Python,
// without the classes (HomewareSC, Math/Category_Theory, Nov 18 2026).
TestRLWaysRound : UnitTest {
	var just;

	setUp { just = RLChordContext(RLChordSpace.triads(RLJust([3, 5]))) }

	test_closed_words_of_twelve_steps {
		var ctx = RLChordContext(RLChordSpace.triads(RLEDO(12))), words = ctx.closedWords(8);
		var hidden = words.collect { |w|
			var drift = RLWalk(just, just.space.major, w).drift;
			if(drift == [0, 0]) { "nothing" } { just.system.commaName(drift) }
		};
		this.assertEquals(words.collect(_.join), ["LPLPLP", "LPRLPR", "LPLPRPLR", "LRLRLRPR", "PRPRPRPR"],
			"five ways round in at most eight steps: the hexatonic cycle, L P R twice, one more, the pump, the octatonic cycle");
		this.assert(words.every { |w| RLWalk(ctx, ctx.space.minor(5), w).isClosed }, "each brings F minor back too");
		this.assertEquals(hidden, ["diesis 128/125", "nothing", "diesis 128/125", "syntonic comma 81/80", "major diesis 648/625"],
			"the comma each hides: where the same word ends on the lattice");
		this.assertEquals(ctx.closedWords(10).size, 13, "13 in at most ten steps");
		this.assertEquals(ctx.closedWords(5), [], "none shorter than six");
	}

	test_closed_words_of_other_systems {
		var words = { |system, max| RLChordContext(RLChordSpace.triads(system)).closedWords(max).collect(_.join) };
		var magic = RLWalk(just, just.space.major, [\L, \P, \L, \P, \L, \P, \L, \P, \R, \P]).drift;
		this.assertEquals(words.(RLEDO(19), 8), ["LPRLPR", "LRLRLRPR"], "19 steps: L P R twice and the pump");
		this.assertEquals(words.(RLEDO(31), 8), ["LPRLPR", "LRLRLRPR"], "31 steps: the same two");
		this.assertEquals([words.(RLEDO(19), 10).size, words.(RLEDO(31), 10).size], [8, 7], "8 and 7 in at most ten steps");
		this.assert(words.(RLEDO(19), 10).includesEqual("LPLPLPLPRP") and: { just.system.commaName(magic) == "magic comma 3125/3072" },
			"the one 19 steps have alone hides the magic comma");
		this.assertEquals(words.(RLJust([3, 5]), 8), ["LPRLPR"], "on the lattice: L P R twice only, which hides nothing");
		this.assertEquals(words.(RLJust([3, 5]), 10).size, 4, "4 in at most ten steps");
		this.assertEquals(words.(RLTempered.meantone, 10), words.(RLEDO(31), 10), "meantone has exactly the ways round of 31 steps, up to ten");
	}

	test_closed_words_of_tetrads {
		var count = { |system| RLChordContext(RLChordSpace.tetrads(system)).closedWords(6).size };
		this.assertEquals([count.(RLEDO(12)), count.(RLEDO(31)), count.(RLJust([3, 5, 7]))], [152, 57, 35],
			"six contextual inversions, at most six steps: 152 ways round in 12 steps, 57 in 31, 35 on the lattice");
		this.assertEquals(RLChordContext(RLChordSpace.tetrads(RLJust([3, 5, 7]))).closedWords(4).collect(_.join),
			["K01K02K23K13", "K01K03K23K12", "K02K03K13K12"], "three of four steps close in every system");
	}

	test_cycles_of_a_graph {
		var g = RLRelationContext.cubeDance.graph, cycles = g.cycles(8), by = Dictionary.new;
		var oneWay = RLGraph(RLRelation.fromPairs([\a, \b, \c], [\a, \b, \c], [[\a, \b], [\b, \c], [\c, \a], [\b, \a]]));
		cycles.do { |c| by[c.size] = (by[c.size] ? 0) + 1 };
		this.assertEquals(by.asSortedArray, [[4, 24], [6, 64], [8, 24]], "Cube Dance: 24 squares, 64 hexagons, 24 octagons");
		this.assert(cycles.every { |c| (c.asSet.size == c.size) and: { c.every { |x, i| g.neighbours(x).includesEqual(c.wrapAt(i + 1)) } } },
			"each passes no chord twice and goes along edges, back to its start");
		this.assertEquals(RLRelationContext.triads.graph.cycles(8).collect(_.size).asSet, Set[6, 8], "the triads under P, L, R: no cycle shorter than six");
		this.assertEquals(RLRelationContext.powerTowers.graph.cycles(4).size, 36, "Power Towers: 36 squares");
		this.assertEquals(oneWay.cycles(3), [[\a, \b], [\a, \b, \c]], "where edges go one way, a cycle is listed in its direction, and two chords can be one");
	}
}
