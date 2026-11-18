// reLation, relations and what they generate: RLQuantale, RLRelation, RLCategory, RLRelationContext, RLGraph.
// No server. The numbers are those of HomewareSC's Math/Category_Theory/THEORY.md (§ 1.4) and H1_CONNECTION.md.
TestRLQuantale : UnitTest {

	test_the_four_quantales {
		var b = RLQuantale.boolean, l = RLQuantale.lawvere, m = RLQuantale.multiplicative, i = RLQuantale.interval;
		this.assertEquals([b.mul(1, 0), b.join(1, 0)], [0, 1], "boolean: and, or");
		this.assertEquals([l.mul(2, 3), l.join(2, 3)], [5, 2], "lawvere: costs add along a walk, the cheaper walk wins");
		this.assertEquals([m.mul(0.5, 0.5), m.join(0.5, 0.25)], [0.25, 0.5], "multiplicative: product, max");
		this.assertEquals([i.mul(0.5, 1), i.join(0.5, 1)], [0.5, 1], "interval: min, max");
		this.assert(RLQuantale.named(\lawvere) === l, "by name");
	}

	test_zero_and_unit {
		[RLQuantale.boolean, RLQuantale.lawvere, RLQuantale.multiplicative, RLQuantale.interval].do { |q|
			[0.0, 0.5, 1.0].do { |v|
				this.assertEquals(q.mul(q.unit, v), v, "%: unit is neutral along a walk".format(q.name));
				this.assertEquals(q.mul(q.zero, v), q.zero, "%: zero absorbs".format(q.name));
				this.assertEquals(q.join(q.zero, v), v, "%: zero is neutral between walks".format(q.name));
			};
		};
		this.assert(RLQuantale.lawvere.leq(3, 2), "lawvere: a larger cost is below a smaller one");
		this.assert(RLQuantale.interval.leq(0.5, 1), "interval: the order of the numbers");
		this.assertEquals([RLQuantale.lawvere.coerce(true), RLQuantale.lawvere.coerce(nil), RLQuantale.lawvere.coerce(2)],
			[0.0, inf, 2.0], "a rule's answer as a value: true, nil, a number");
	}
}

TestRLRelation : UnitTest {
	var edo, space, all, c, ctx;

	setUp {
		edo = RLEDO(12);
		space = RLChordSpace.triads(edo);
		all = space.all;
		c = space.major(0);
		ctx = RLChordContext(space);
	}

	test_images_and_composition {
		var near = RLRelation.voiceMoves(all, [1]);
		this.assertEquals(near.images(c).collect(_.name), ["C minor", "E minor"], "one voice by one step from C major");
		this.assertEquals((near <> near).images(c).collect(_.name), ["C major", "E major", "Ab major"], "two such steps");
		this.assert(near.repeated(3).relates(c, space.minor(8)), "three steps reach the hexatonic pole");
		this.assert(near.relates(c, space.minor(9)).not, "A minor is a whole tone away");
		this.assertEquals(near.at(c, space.minor(0)), 1.0, "the value of a related pair");
		this.assertEquals(near.preimages(space.minor(0)).collect(_.name), ["C major", "Ab major"], "what leads to C minor");
		this.assertEquals(near.pairCount, 48, "two images for each of the 24 triads");
		this.assert(near.isSymmetric and: { near.isTotal } and: { near.isFunction.not }, "symmetric, total, not a function");
	}

	test_functions_as_relations {
		var p = RLRelation.ofFunction(all, { |x| ctx.p.(x) }), l = RLRelation.ofFunction(all, { |x| ctx.l.(x) });
		this.assert(p.isFunction and: { p.isBijection }, "P is a function, and one to one");
		this.assertEquals(p.value(c), space.minor(0), "its one image");
		this.assertEquals((l <> p).value(c), ctx.l.(ctx.p.(c)), "composition reads right to left: P first");
		this.assertEquals((l <> p).converse, p <> l, "read backwards, the steps are undone in the other order");
		this.assert((p <> p).isIdentity, "P twice is the identity");
		this.assertException({ RLRelation.voiceMoves(all, [1]).value(c) }, Error, "two images are not a value");
	}

	test_voice_moves_name_the_usual_relations {
		var p = RLRelation.ofFunction(all, { |x| ctx.p.(x) }), l = RLRelation.ofFunction(all, { |x| ctx.l.(x) });
		var r = RLRelation.ofFunction(all, { |x| ctx.r.(x) });
		this.assertEquals(RLRelation.voiceMoves(all, [1]), p | l, "12: one voice by a semitone is P or L");
		this.assertEquals(RLRelation.voiceMoves(all, [2]), r, "one voice by a whole tone is R");
		this.assertEquals(RLRelation.pmn(all, 1, 0), p | l, "Douthett and Steinbach's P(1,0)");
		this.assertEquals(RLRelation.commonTones(all, 2), p | l | r, "two common tones: P, L or R");
		this.assert((p <= (p | l)) and: { ((p | l) <= p).not }, "a relation is included in its join with another");
		this.assertEquals((p | l).without(l), p, "and taken out again");
	}

	test_single_moves_in_19_and_31_steps {
		[[19, [1, 2, 3]], [31, [2, 3, 5]]].do { |row|
			var cx = RLChordContext(RLChordSpace.triads(RLEDO(row[0]))), chords = cx.space.all;
			var found = [\P, \L, \R].collect { |name|
				var rel = RLRelation.ofFunction(chords, { |x| cx.transform(name).(x) });
				(1..6).detect { |k| RLRelation.voiceMoves(chords, [k]) == rel }
			};
			this.assertEquals(found, row[1], "% steps: P, L and R each move one voice, by % steps".format(row[0], row[1]));
		};
	}

	test_between_two_lists {
		var inc = RLRelation.between(edo.pcs, all, { |pc, chord| chord.pcs.includes(pc) });
		var shared = inc <> inc.converse;
		this.assertEquals(inc.images(0).size, 6, "C belongs to six triads");
		this.assertEquals(inc.preimages(c), [0, 4, 7], "the tones of C major");
		this.assert(shared.relates(c, space.major(7)), "C major and G major share a tone");
		this.assert(shared.relates(c, space.major(2)).not, "C major and D major share none");
		this.assertException({ inc <> inc }, Error, "relations compose only when one starts where the other ends");
		this.assertException({ inc.images(c) }, Error, "an object outside the source is refused");
	}

	test_costs_and_closure {
		var cd = RLRelationContext.cubeDance, chords = cd.objects, sp = cd.space, start = sp.major(0);
		var step = cd.union.without(RLRelation.identity(chords)).valued(RLQuantale.lawvere, { 1 });
		var dist = step.closure, graph = cd.graph;
		this.assertEquals(step.at(start, sp.minor(0)), 1.0, "one step costs 1");
		this.assertEquals(step.at(start, sp.major(2)), inf, "no step: the cost is infinite");
		this.assertEquals((step <> step).at(start, sp.major(4)), 2.0, "composing adds the costs and keeps the least");
		this.assertEquals(chords.collect { |y| dist.at(start, y) }, graph.distances(start).collect(_.asFloat),
			"the closure of a relation of costs is the distance in its graph");
		this.assertEquals(dist.at(start, sp.major(6)), 6.0, "C major to F# major: six steps on Cube Dance");
	}

	// THEORY.md § 1.4: two common tones, 1 for a semitone move and 1/2 for a whole tone, in the interval quantale
	test_a_relation_with_strengths {
		var cd = RLRelationContext.cubeDance, sp = cd.space;
		var strength = { |x, y| if(RLVoiceLeading.stepsBetween(x, y) == 1) { 1 } { 0.5 } };
		var d = RLRelation.commonTones(cd.objects, 2).valued(RLQuantale.interval, strength);
		var d24 = RLRelation.commonTones(all, 2).valued(RLQuantale.interval, strength);
		this.assertEquals(RLCategory([[\D, d]]).size, 7, "on the 28 triads it generates 7 relations");
		this.assertEquals(d.repeated(5).at(sp.major(0), sp.minor(2)), 1.0, "five steps join C major and D minor by semitones only");
		this.assertEquals(RLCategory([[\D, d24]]).size, 6, "on the 24 triads, 6");
		this.assertEquals(d24.repeated(5).at(c, space.minor(2)), 0.5, "and there a whole tone is needed");
	}

	test_a_relation_is_a_value {
		var objs = [\a, \b], set = Set.new;
		var id = RLRelation.identity(objs), same = RLRelation(objs, objs, [[1, 0], [0, 1]], nil, "another label");
		this.assert(id == same, "equal by their lists and values, whatever the label");
		this.assert(id != RLRelation(objs, objs, [[1, 1], [0, 1]]), "another pair, another relation");
		this.assert(id != RLRelation.identity(objs, RLQuantale.lawvere), "another quantale, another relation");
		set.add(id); set.add(same); set.add(RLRelation(objs, objs, [[1, 1], [0, 1]]));
		this.assertEquals(set.size, 2, "usable as keys");
		this.assertEquals(RLRelation.fromPairs(objs, objs, [[\a, \b], [\b, \a]]).images(\a), [\b], "from a list of pairs");
		this.assertEquals(RLRelation.fromPairs(objs, objs, [[\a, \b, 3]], RLQuantale.lawvere).at(\a, \b), 3.0, "with values");
		this.assertException({ RLRelation(objs, objs, [[1, 0]]) }, Error, "rows that do not fit are refused");
	}
}

TestRLCategory : UnitTest {
	var cd, chords, sp;

	setUp {
		cd = RLRelationContext.cubeDance;
		chords = cd.objects;
		sp = cd.space;
	}

	// THEORY.md § 1.4, the table of monoids on the 24 triads and 4 augmented triads
	test_monoids_of_relations {
		var s = RLRelation.pmn(chords, 1, 0), t = RLRelation.pmn(chords, 2, 0), d = RLRelation.commonTones(chords, 2);
		var ms = RLCategory([[\S, s]]);
		this.assertEquals(ms.size, 7, "one voice by a semitone: 7 relations");
		this.assert((s.repeated(7) == s.repeated(5)) and: { s.repeated(6) != s.repeated(4) }, "its powers come back: S7 = S5");
		this.assertEquals(ms.words.last, \S ! 6, "each with its shortest word");
		this.assertEquals(RLCategory([[\T, t]]).size, 4, "two voices by a semitone: 4");
		this.assertEquals(RLCategory([[\S, s], [\T, t]]).size, 8, "both: 8");
		this.assertEquals(RLCategory([[\D, d]]).size, 6, "two common tones: 6");
		this.assert(d.repeated(6) == d.repeated(5), "D6 = D5");
	}

	test_the_monoid_of_u_p_l {
		var m = cd.category, u = m.generator(\U), p = m.generator(\P), l = m.generator(\L);
		this.assertEquals(m.size, 40, "U, P, L generate 40 relations");
		this.assert(m.isMonoid and: { m.isGroup.not }, "a monoid, not a group");
		this.assertEquals(m.invertibles.collect { |a| m.wordOf(a) }, [[], [\P], [\L], [\P, \L], [\L, \P], [\P, \L, \P]],
			"six can be undone: P, L and their products");
		this.assertEquals(m.wordOf(l <> p), [\P, \L], "words are in the order of time: P first");
		this.assertEquals(m.arrow([\P, \L]), l <> p, "and back");
		this.assertEquals(m.arrow([\U, \U, \U]), u, "U three times is U");
		this.assertEquals(m.arrow([]), RLRelation.identity(chords), "the empty word is the identity");
		this.assertEquals(m.relating(sp.major(2), sp.minor(7)).collect(_.label), ["UU", "UPU", "PUUPUU", "UPUUPU", "PUPUPUU", "PUPUPUPU"],
			"six of them relate D major to G minor (labels read right to left)");
		this.assertEquals((u | p.without(p.sourceIdentity) | l.without(l.sourceIdentity)), RLRelation.pmn(chords, 1, 0),
			"together U, P and L are one voice by a semitone");
	}

	test_the_table_follows_the_arrows {
		var m = cd.category, bad = 0;
		thisThread.randSeed = 3;
		60.do {
			var i = m.size.rand, j = m.size.rand;
			if(m.arrows[m.composeIndex(i, j)] != (m.arrows[i] <> m.arrows[j])) { bad = bad + 1 };
		};
		this.assertEquals(bad, 0, "composing by the table gives the composed relations");
		this.assertEquals(m.table.size, 40, "the whole table");
	}

	test_groups_of_transformations {
		var ctx = RLChordContext(RLChordSpace.triads(RLEDO(12)));
		var plr = RLCategory([[\P, ctx.p], [\L, ctx.l], [\R, ctx.r]]);
		var ti = RLCategory([[\T1, ctx.t(1)], [\I0, ctx.i(0)]]);
		var e19 = RLChordContext(RLChordSpace.triads(RLEDO(19)));
		var plr19 = RLCategory([[\P, e19.p], [\L, e19.l], [\R, e19.r]]), ti19 = RLCategory([[\T1, e19.t(1)], [\I0, e19.i(0)]]);
		this.assert((plr.size == 24) and: { plr.isGroup }, "P, L, R generate a group of 24");
		this.assert((ti.size == 24) and: { ti.isGroup }, "T and I too");
		this.assertEquals(plr.arrows.count { |a| ti.includes(a) }, 2, "in 12 they share the identity and the tritone (T6 is RPRP)");
		this.assertEquals([plr19.size, ti19.size], [38, 38], "in 19 steps, 38 each");
		this.assertEquals(plr19.arrows.count { |a| ti19.includes(a) }, 1, "sharing the identity only: 19 has no half octave");
		this.assertEquals(plr.wordOf(ctx.l <> ctx.r), [\R, \L], "a product by its word: R then L");
		this.assert(plr.includes(ctx.t(7)).not, "a transposition by a fifth is not a product of P, L, R");
		this.assertEquals(plr.wordOf(ctx.t(6)).size, 4, "the tritone is: four steps");
	}

	test_several_lists_and_the_guard {
		var edo = RLEDO(12), all = RLChordSpace.triads(edo).all;
		var inc = RLRelation.between(edo.pcs, all, { |pc, chord| chord.pcs.includes(pc) });
		var cat = RLCategory([[\in, inc], [\out, inc.converse]]);
		this.assertEquals(cat.identities.size, 2, "two lists, two identities");
		this.assert(cat.isMonoid.not, "not a monoid");
		this.assertEquals(cat.arrow([\in, \out]), inc.converse <> inc, "a word across the two lists");
		this.assertException({ cat.arrow([\in, \in]) }, Error, "steps that do not follow one another are refused");
		this.assert(cat.arrows.select { |a| cat.wordOf(a).notEmpty }.every { |a| cat.arrow(cat.wordOf(a)) == a },
			"each arrow is the arrow of its word");
		this.assertException({ RLCategory([[\S, RLRelation.pmn(chords, 1, 0)]], 3) }, Error, "the closure is guarded");
	}
}

TestRLRelationContext : UnitTest {

	test_cube_dance {
		var cd = RLRelationContext.cubeDance, sp = cd.space, c = sp.major(0), aug = sp.chord(0, \aug);
		this.assertEquals(cd.objects.size, 28, "24 triads and 4 augmented triads");
		this.assertEquals(cd.act(\U, aug).collect(_.name), ["C major", "E major", "Ab major", "C# minor", "F minor", "A minor"],
			"an augmented triad is one semitone from six triads");
		this.assertEquals(cd.act(\P, c), [sp.minor(0)], "P on a triad");
		this.assertEquals(cd.act(\P, aug), [aug], "P keeps an augmented triad");
		this.assertEquals(cd.neighbours(c).collect { |x| [x[0], x[1].name] }, [[\U, "C aug"], [\P, "C minor"], [\L, "E minor"]], "the three neighbours of C major");
		this.assertEquals(cd.labels(c, aug), [\U], "which relation joins two chords");
		this.assertEquals(sp.canonical(sp.chord(11, \aug)), sp.chord(3, \aug), "B augmented is Eb augmented");
		this.assertEquals(cd.union.without(cd.identityTransform), RLRelation.pmn(cd.objects, 1, 0), "its edges are P(1,0)");
	}

	// Muse, "Take a Bow" (THEORY.md § 1.6): the path D major, D aug, G minor, G major, B aug, C minor, C major
	test_a_path_and_its_word {
		var cd = RLRelationContext.cubeDance, sp = cd.space;
		var path = [sp.major(2), sp.chord(2, \aug), sp.minor(7), sp.major(7), sp.chord(3, \aug), sp.minor(0), sp.major(0)];
		this.assertEquals(cd.wordOf(path), [\U, \U, \P, \U, \U, \P], "the word of a path");
		this.assert(cd.isWalk(path, [\U, \U, \P, \U, \U, \P]), "which fits it");
		this.assert(cd.isWalk(path, [\U, \U, \L, \U, \U, \P]).not, "and another word does not");
		this.assert(cd.define(\UU, [\U, \U]).relates(sp.major(2), sp.minor(7)), "a named word: U twice takes D major to G minor");
	}

	test_cube_dance_in_other_divisions {
		[[15, 35], [18, 42], [24, 56]].do { |row|
			var a = RLRelationContext.cubeDance(RLEDO(row[0]));
			this.assertEquals(a.objects.size, row[1], "% steps: % chords".format(row[0], row[1]));
			this.assertEquals(a.union.without(a.identityTransform), RLRelation.voiceMoves(a.objects, [1]),
				"% steps: U, P and L are the moves of one voice by one step".format(row[0]));
		};
		this.assertException({ RLRelationContext.cubeDance(RLEDO(19)) }, Error, "a division by three is needed");
	}

	test_triads_as_relations_and_walks {
		var cw = RLRelationContext.triads(RLEDO(12)), ctx = RLChordContext(RLChordSpace.triads(RLEDO(12)));
		var c = cw.space.major(0), pump = [\R, \L, \R, \P, \R, \L, \R, \L];
		var cd = RLRelationContext.cubeDance;
		this.assert((cw.category.size == 24) and: { cw.category.isGroup }, "as relations too, P, L, R generate a group of 24");
		this.assertEquals(RLWalk(cw, c, pump).chords, RLWalk(ctx, c, pump).chords, "a walk reads the same in both kinds of context");
		this.assert(RLWalk(cw, c, pump).composed.isIdentity, "and composes to the identity");
		this.assertEquals(RLWalk(cd, cd.space.major(0), [\P, \L, \P]).end, cd.space.minor(8), "on Cube Dance, where a step has one image");
		this.assertException({ RLWalk(cd, cd.space.chord(0, \aug), [\U]).chords }, Error, "a step with six images is not a walk's step");
	}
}

TestRLGraph : UnitTest {
	var cd, sp, graph, cw, c;

	setUp {
		cd = RLRelationContext.cubeDance;
		sp = cd.space;
		graph = cd.graph;
		cw = RLRelationContext.triads(RLEDO(12));
		c = sp.major(0);
	}

	// the least voice leading against the distance in a graph, over every pair: [equal, different]
	prCompare { |g|
		var objs = g.objects, same = 0, diff = 0;
		objs.do { |x, i|
			var dist = g.distances(x);
			objs.do { |y, j|
				if(j > i) { if((dist[j] - RLVoiceLeading.stepsBetween(x, y)).abs < 1e-9) { same = same + 1 } { diff = diff + 1 } };
			};
		};
		^[same, diff]
	}

	test_cube_dance_as_a_graph {
		this.assertEquals([graph.size, graph.edgeCount], [28, 48], "28 chords, 48 edges");
		this.assertEquals(graph.degrees.asSet, Set[3, 6], "triads have three neighbours, augmented triads six");
		this.assertEquals(graph.neighbours(c).collect(_.name), ["C minor", "E minor", "C aug"], "the neighbours of C major");
		this.assert(graph.isSymmetric and: { graph.components.size == 1 }, "symmetric, in one piece");
		this.assertEquals(graph.diameter, 6, "no two chords are more than six steps apart");
		this.assertEquals(graph.distance(c, sp.major(6)), 6, "C major to F# major");
		this.assertEquals(graph.cost(c, sp.minor(0)), 1, "each edge costs 1");
		this.assertEquals(graph.cost(c, sp.major(2)), nil, "no edge, no cost");
	}

	test_shortest_walks {
		var paths = graph.shortestPaths(c, sp.major(1)), one = graph.shortestPath(c, sp.major(1));
		this.assertEquals(graph.distance(c, sp.major(1)), 3, "C major to C# major: three steps");
		this.assert(paths.includesEqual(one), "the first shortest walk is one of them");
		this.assert(paths.every { |p| (p.size == 4) and: { p.first == c } and: { p.last == sp.major(1) } }, "each has four chords, from C major to C# major");
		this.assert(paths.every { |p| cd.wordOf(p).every(_.notNil) }, "each step is U, P or L");
		this.assertEquals(paths.size, 2, "two of them");
		this.assert([[\U, \U, \P], [\U, \U, \L]].every { |w| paths.collect { |p| cd.wordOf(p) }.includesEqual(w) },
			"through the augmented triad, then P or L");
		this.assertEquals(graph.shortestPath(c, c), [c], "a chord to itself");
	}

	// H1_CONNECTION.md: 62 on the 24 triads, 1296 on Cube Dance
	test_hamiltonian_cycles {
		var cwg = cw.graph, cycle = graph.hamiltonianCycle, in15 = RLRelationContext.cubeDance(RLEDO(15)).graph;
		this.assertEquals(cwg.countHamiltonianCycles, 62, "the 24 triads under P, L, R: 62 cycles through all of them");
		this.assertEquals(graph.countHamiltonianCycles, 1296, "Cube Dance: 1296, six ways through each of its four hexatonic cycles");
		this.assertEquals(in15.countHamiltonianCycles, 7776, "its analogue in 15 steps: six to the fifth");
		this.assertEquals(cycle.asSet.size, 28, "a cycle meets every chord once");
		this.assert(cd.wordOf(cycle ++ [cycle.first]).every(_.notNil), "by steps of the graph, back to its start");
		this.assertEquals(graph.hamiltonianCycles(c, 5).size, 5, "the first five");
		this.assertEquals(graph.hamiltonianCycle(sp.major(4)).first, sp.major(4), "from a chosen start");
	}

	// H1_CONNECTION.md § 3: where the distance in the graph is the least voice leading
	test_the_two_distances {
		this.assertEquals(this.prCompare(graph), [378, 0], "Cube Dance: equal for all 378 pairs");
		this.assertEquals(this.prCompare(cw.graph), [72, 204], "the 24 triads under P, L, R: equal for 72 pairs of 276");
		this.assertEquals(this.prCompare(RLGraph.voiceLeading(cw.union)), [216, 60], "216 when an edge costs the semitones it moves");
		this.assertEquals(this.prCompare(RLRelationContext.cubeDance(RLEDO(15)).graph), [595, 0], "Cube Dance in 15 steps: all 595 pairs");
	}

	test_costs {
		var weighted = RLGraph.voiceLeading(cw.union), a = cw.space.minor(9);
		var step = cw.union.valued(RLQuantale.lawvere, { |x, y| RLVoiceLeading.stepsBetween(x, y) });
		this.assertEquals([cw.graph.distance(cw.space.major(0), a), weighted.distance(cw.space.major(0), a)], [1, 2], "R is one edge and two semitones");
		this.assertEquals(RLGraph(step, \value).distances(a), weighted.distances(a), "costs taken from a relation's values");
		this.assertEquals(weighted.distance(cw.space.major(0), cw.space.major(1)), 5, "C major to C# major along the edges: 5 semitones");
		this.assertEquals(RLVoiceLeading.stepsBetween(cw.space.major(0), cw.space.major(1)), 3, "directly: 3");
	}

	test_a_directed_graph {
		var objs = [\a, \b, \c], ring = RLGraph(RLRelation.fromPairs(objs, objs, [[\a, \b], [\b, \c], [\c, \a]]));
		var line = RLGraph(RLRelation.fromPairs(objs, objs, [[\a, \b], [\b, \c]]));
		this.assert(ring.isSymmetric.not, "one way only");
		this.assertEquals(ring.countHamiltonianCycles, 1, "one cycle");
		this.assertEquals(ring.distance(\b, \a), 2, "the long way round");
		this.assertEquals(line.countHamiltonianCycles, 0, "a line has none");
		this.assertEquals(line.distance(\c, \a), inf, "and no way back");
		this.assertEquals(line.shortestPath(\c, \a), nil, "no walk");
		this.assertException({ ring.neighbours(\z) }, Error, "an object outside the graph is refused");
	}
}
