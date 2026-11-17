// reLation, networks and what moves them: RLNetwork, RLAutomorphism, the automorphisms of an RLCategory.
// No server. The examples are those of Popoff's posts on networks and of "Composing with automorphisms", as
// HomewareSC's Math/Category_Theory/THEORY.md (§ 1.3, 1.5, 1.6) recalls them.
TestRLAutomorphism : UnitTest {
	var pcs, cat, cd, sp;

	setUp {
		pcs = RLRelationContext.pitchClasses;
		cat = pcs.category;
		cd = RLRelationContext.cubeDance;
		sp = cd.space;
	}

	test_t_and_i_on_pitch_classes {
		this.assert((cat.size == 24) and: { cat.isGroup }, "T and I on twelve pitch classes: a group of 24");
		this.assertEquals(pcs.transform(\I8).value(9), 11, "I8 sends A to B");
		this.assertEquals(pcs.nameOf(pcs.arrow([\T4, \I11])), "I7", "I11 after T4 is I7");
		this.assertEquals(pcs.nameOf(pcs.transform(\I9) <> pcs.transform(\I8)), "T1", "two inversions make a transposition");
	}

	// THEORY.md § 1.3 and § 1.6: 48 automorphisms of T/I, the affine maps realise those with p even
	test_the_48_automorphisms_and_the_complete_ones {
		var auts = cat.automorphisms, realised = Set.new, t1 = cat.indexOf(pcs.transform(\T1)), i0 = cat.indexOf(pcs.transform(\I0));
		this.assertEquals(auts.size, 48, "T/I has 48 automorphisms");
		[1, 5, 7, 11].do { |k|
			12.do { |c|
				var a = RLAutomorphism.affine(pcs, k, c);
				if(a.isValid.not) { this.failed(thisMethod, "x -> %x + % should be valid".format(k, c)) };
				realised.add(a.arrowMap);
			};
		};
		this.assertEquals(realised.size, 24, "the 48 maps x -> kx + c realise 24 of them, each twice");
		this.assert(realised.every { |am| auts.includesEqual(am) }, "all among the 48");
		this.assert(realised.every { |am| pcs.nameOf(cat.arrows[am[i0]]).drop(1).asInteger.even }, "those that send I0 to an even inversion");
		this.assertEquals(pcs.nameOf(RLAutomorphism.affine(pcs, 5, 2).onArrow(\I3)), "I7", "x -> 5x + 2 sends I(n) to I(5n + 4)");
		this.assertEquals(pcs.nameOf(RLAutomorphism.affine(pcs, 5, 2).onArrow(\T1)), "T5", "and T(n) to T(5n)");
	}

	test_in_19_steps_every_automorphism_is_complete {
		var e19 = RLRelationContext.pitchClasses(RLEDO(19)), realised = Set.new;
		this.assertEquals(e19.category.automorphisms.size, 342, "19 steps: 19 times 18 automorphisms");
		(1..18).do { |k| 19.do { |c| realised.add(RLAutomorphism.affine(e19, k, c).arrowMap) } };
		this.assertEquals(realised.size, 342, "and the 342 affine maps realise them all");
	}

	// "Composing with automorphisms": 12 automorphisms of the monoid, U goes to U or PUP
	test_the_12_automorphisms_of_u_p_l {
		var m = cd.category, auts = m.automorphisms;
		this.assertEquals(auts.size, 12, "the monoid of U, P, L has 12 automorphisms");
		this.assertEquals(auts.collect { |am| m.imageOf(am, m.generator(\U)).label }.asSet, Set["U", "PUP"], "U goes to U or to PUP");
		this.assertEquals(cd.arrow([\L, \U, \L]), cd.arrow([\P, \U, \P]), "LUL is PUP");
		this.assertEquals(auts.collect { |am| [m.imageOf(am, m.generator(\P)).label, m.imageOf(am, m.generator(\L)).label] }.asSet.size, 6,
			"P and L go where the six automorphisms of their group send them");
	}

	test_transposing_and_inverting_cube_dance {
		var t5 = RLAutomorphism.transposition(cd, 5), i0 = RLAutomorphism.inversion(cd, 0);
		this.assert(t5.isValid and: { i0.isValid }, "a transposition and an inversion are automorphisms");
		this.assert([\U, \P, \L].every { |n| (t5.onArrow(n) == cd.transform(n)) and: { i0.onArrow(n) == cd.transform(n) } }, "that change no relation");
		this.assertEquals(t5.value(sp.chord(2, \aug)), sp.chord(3, \aug), "D augmented a fourth up is G augmented, written Eb");
		this.assertEquals([t5.order, i0.order], [12, 2], "of orders 12 and 2");
		this.assertEquals((t5 <> t5.inverse), RLAutomorphism(cd, []), "undone");
		this.assertEquals(t5.repeated(3), RLAutomorphism.transposition(cd, 3), "three fourths are a minor third");
		this.assertException({ RLAutomorphism(cd, [[sp.major(0), sp.major(1)]]) }, Error, "a map that is not one to one is refused");
	}

	test_a_map_that_is_no_automorphism {
		var swap = RLAutomorphism(cd, [[sp.major(0), sp.major(1)], [sp.major(1), sp.major(0)]]);
		var third = RLAutomorphism(cd, cd.objects.select { |x| (x.type != \aug) and: { [0, 4, 8].includes(x.root) } }.collect { |x| [x, sp.chord(x.root + 4, x.type)] });
		this.assert(swap.isValid.not, "swapping C major and C# major alone breaks the relations");
		this.assertEquals(swap.arrowMap, nil, "and moves no arrow to an arrow");
		this.assert(third.isValid and: { third.onArrow(\U) == cd.transform(\U) }, "one hexatonic system alone a major third up: an automorphism");
		this.assertEquals(third.order, 3, "of order 3");
	}

	// the automorphism of the blog's code post: P and L kept, U sent to LUL, the augmented triads turned
	test_the_blog_s_sequence {
		var m = cd.category, lul, nu, seq;
		lul = m.automorphisms.detect { |am|
			(m.imageOf(am, m.generator(\P)) == m.generator(\P)) and: { m.imageOf(am, m.generator(\L)) == m.generator(\L) }
			and: { m.imageOf(am, m.generator(\U)) != m.generator(\U) }
		};
		nu = RLAutomorphism.extend(cd, [[sp.major(0), sp.minor(11)], [sp.major(3), sp.minor(6)], [sp.major(6), sp.minor(9)], [sp.major(9), sp.minor(0)]], lul);
		nu = RLAutomorphism(cd, cd.objects.collect { |x| if(x.type == \aug) { [x, sp.canonical(sp.chord(x.root + 3, \aug))] } { [x, nu.value(x)] } });
		this.assert(nu.isValid and: { nu.arrowMap == lul }, "valid, and it moves the relations as that automorphism of the monoid");
		this.assertEquals(nu.order, 12, "of order 12");
		seq = RLNetwork.chain(cd, [sp.major(2), sp.chord(2, \aug), sp.minor(7)], [\U, \U]).iterate(nu, 9);
		this.assertEquals(seq.collect { |n| n.objects.collect(_.name) }, [
			["D major", "D aug", "G minor"], ["C# minor", "C# aug", "D major"], ["Ab major", "C aug", "C# minor"],
			["Eb minor", "Eb aug", "Ab major"], ["F# major", "D aug", "Eb minor"], ["A minor", "C# aug", "F# major"],
			["C major", "C aug", "A minor"], ["B minor", "Eb aug", "C major"], ["Bb major", "D aug", "B minor"],
			["F minor", "C# aug", "Bb major"]
		], "the ten networks the post prints (its F, G, C aug are our C#, Eb, C aug)");
		this.assertEquals(seq.collect { |n| n.labelNames.first }, ["U", "PUP"].wrapExtend(10), "labelled U and LUL in turn");
		this.assert(seq.every(_.isValid), "each a valid network");
	}
}

TestRLNetwork : UnitTest {
	var pcs, cat, labels;

	setUp {
		pcs = RLRelationContext.pitchClasses;
		cat = pcs.category;
		labels = [[\x, \y, \I8], [\y, \z, \I9], [\x, \z, \T1]];
	}

	prWebern { |x, y, z| ^RLNetwork(pcs, [[\x, x], [\y, y], [\z, z]], labels) }

	// Webern, op. 11 no. 2: (A, B, Bb), (C#, G, D), (F, Eb, F#) share I8, I9, T1
	test_webern_s_three_networks {
		var w1 = this.prWebern(9, 11, 10), w2 = this.prWebern(1, 7, 2), w3 = this.prWebern(5, 3, 6);
		this.assert(w1.isValid and: { w2.isValid } and: { w3.isValid }, "three valid networks");
		this.assert(w1.hasSameLabels(w2) and: { w2.hasSameLabels(w3) }, "with the same labels: strongly isographic");
		this.assertEquals(w1.labelNames, ["I8", "I9", "T1"], "I8, I9 and their composite T1");
		this.assertEquals(pcs.nameOf(w1.composite([\x, \y, \z])), "T1", "the composite of a route");
		this.assertEquals(w1.fillings(\x).collect(_.objects), 12.collect { |x| [x, (8 - x) % 12, (x + 1) % 12] }, "twelve networks have these labels: (x, 8 - x, x + 1)");
		this.assertEquals([w1.at(\y), w1.objects], [11, [9, 11, 10]], "contents by node");
		this.assert(this.prWebern(9, 11, 9).isValid.not, "a wrong note is seen");
	}

	test_local_and_complete_isographies {
		var w1 = this.prWebern(9, 11, 10), w2 = this.prWebern(1, 7, 2), local, complete;
		local = w1.localIsographies(w2).collect { |l| l.collect { |p| pcs.nameOf(p[1]) } };
		this.assertEquals(local.size, 2, "two local isographies take the first to the second");
		this.assert(local.includesEqual(["T4", "T8", "T4"]) and: { local.includesEqual(["I10", "I6", "I0"]) }, "T4, T8, T4 and I10, I6, I0, node by node");
		complete = [1, 5, 7, 11].collect { |k| (0..11).select { |c| w1.transformedBy(RLAutomorphism.affine(pcs, k, c)) == w2 } }.flat;
		this.assertEquals(complete, [], "and no complete one does");
		this.assertEquals(w1.transformedBy(RLAutomorphism.affine(pcs, 1, 4)).labelNames, ["I4", "I5", "T1"], "transposing by 4 turns I8, I9 into I4, I5");
	}

	// Networks (2): on C, E, G the labels T4, I11, T7 take each note to the next and do not compose
	test_a_network_must_compose {
		var net = { |a, b, c| RLNetwork(pcs, [[\c, 0], [\e, 4], [\g, 7]], [[\c, \e, a], [\e, \g, b], [\c, \g, c]]) };
		this.assert(net.(\T4, \T3, \T7).isValid, "T4, T3, T7");
		this.assert(net.(\T4, \I11, \I7).isValid, "T4, I11, I7");
		this.assertEquals(net.(\T4, \I11, \T7).problems, ["two routes from c to g compose to I7 and to T7"], "T4, I11, T7 does not compose");
		this.assertEquals(net.(\T4, \T4, \T7).problems.size, 2, "a label that misses its note is named too");
	}

	// Networks (3): I8 as an isography, and the two squares that only a local isography joins
	test_isographies_of_the_posts {
		var n1 = RLNetwork(pcs, [[\a, 1], [\b, 5], [\c, 10]], [[\a, \b, \I6], [\b, \c, \T5], [\a, \c, \I11]]);
		var moved = n1.transformedBy(RLAutomorphism.affine(pcs, 11, 8));
		var sq1 = RLNetwork(pcs, [[\a, 8], [\b, 10], [\c, 5], [\d, 9]], [[\a, \b, \T2], [\a, \c, \I1], [\b, \d, \I7], [\c, \d, \T4]]);
		var sq2 = RLNetwork(pcs, [[\a, 11], [\b, 1], [\c, 3], [\d, 7]], [[\a, \b, \T2], [\a, \c, \I2], [\b, \d, \I8], [\c, \d, \T4]]);
		var t1 = cat.automorphisms.detect { |am|
			(cat.imageOf(am, pcs.transform(\T1)) == pcs.transform(\T1)) and: { cat.imageOf(am, pcs.transform(\I0)) == pcs.transform(\I1) }
		};
		this.assertEquals([moved.objects, moved.labelNames], [[7, 3, 10], ["I10", "T7", "I5"]], "C#, F, Bb under I8: G, Eb, Bb with I10, T7, I5");
		this.assert(sq1.isValid and: { sq2.isValid }, "two valid squares");
		this.assertEquals(sq1.localIsographies(sq2), [], "their labels differ");
		this.assert(sq1.localIsographies(sq2, t1).collect { |l| l.collect { |p| pcs.nameOf(p[1]) } }.includesEqual(["T3", "T3", "T10", "T10"]),
			"under <T1> they are joined node by node: T3, T3, T10, T10");
	}

	// Networks (3): Gesualdo, four networks of three triads in a square of two isographies
	test_gesualdo_s_square {
		var cw = RLRelationContext.triads(RLEDO(12)), ws = cw.space;
		var word = { |k| [\R] ++ ([\L, \R] ! k).flat };       // (RL)^k R: R first
		var g1 = RLNetwork.chain(cw, [ws.major(9), ws.minor(0), ws.major(7)], [word.(6), word.(4)]);
		var g2 = RLNetwork.chain(cw, [ws.major(4), ws.minor(0), ws.major(2)], [word.(7), word.(5)]);
		var g3 = RLNetwork.chain(cw, [ws.major(4), ws.minor(7), ws.major(2)], [word.(6), word.(4)]);
		var t7 = RLAutomorphism.transposition(cw, 7), nu = RLAutomorphism(cw, { |c| if(c.type == \major) { ws.major(c.root + 7) } { c } });
		this.assert(g1.isValid and: { g2.isValid } and: { g3.isValid }, "A, Cm, G; E, Cm, D; E, Gm, D with the post's labels");
		this.assertEquals(g1.transformedBy(t7), g3, "the first to the third: every chord a fifth up, the labels kept");
		this.assert(nu.isValid, "majors a fifth up, minors in place: an automorphism");
		this.assertEquals([cw.nameOf(nu.onArrow(\L)), cw.nameOf(nu.onArrow(\R))], ["R", "RLR"], "that sends L to R and R to RLR");
		this.assertEquals(g1.transformedBy(nu), g2, "the first to the second");
		this.assertEquals(g1.transformedBy(t7).transformedBy(nu), g1.transformedBy(nu).transformedBy(t7), "the square commutes");
		this.assertEquals(g2.transformedBy(t7).objects.collect(_.name), ["B major", "G minor", "A major"], "its fourth corner: B, Gm, A");
	}

	// Networks (4): Muse, "Take a Bow"
	test_take_a_bow {
		var cd = RLRelationContext.cubeDance, sp = cd.space, t5 = RLAutomorphism.transposition(cd, 5);
		var cell = RLNetwork(cd, [[\a, sp.major(2)], [\b, sp.chord(2, \aug)], [\c, sp.minor(7)]], [[\a, \b, \U], [\b, \c, \U], [\a, \c, [\U, \U]]]);
		var seq = cell.iterate(t5, 2);
		this.assert(cell.isValid, "D major, D aug, G minor: U, U and U twice");
		this.assertEquals(seq.collect { |n| n.objects.collect(_.name) },
			[["D major", "D aug", "G minor"], ["G major", "Eb aug", "C minor"], ["C major", "C aug", "F minor"]], "one automorphism, again and again");
		this.assert(seq.every { |n| n.isValid and: { n.hasSameLabels(cell) } }, "each valid, with the same labels");
		this.assertEquals(cell.fillings(\a).size, 168, "168 networks have this shape and these labels: six from each chord");
		this.assert(RLNetwork(cd, [[\a, sp.major(2)], [\b, sp.minor(7)]], [[\a, \b, \U]]).isValid.not, "D major is not one U from G minor");
	}

	test_in_a_chord_context_and_as_a_value {
		var ctx = RLChordContext(RLChordSpace.triads(RLEDO(12))), c = ctx.space.major(0);
		var net = RLNetwork(ctx, [[\a, c], [\b, ctx.space.minor(4)], [\c, ctx.space.major(7)]], [[\a, \b, \L], [\b, \c, \R], [\a, \c, ctx.r <> ctx.l]]);
		var bad = RLNetwork(ctx, [[\a, c], [\b, ctx.space.minor(4)], [\c, ctx.space.major(7)]], [[\a, \b, \L], [\b, \c, \R], [\a, \c, ctx.t(7)]]);
		this.assert(net.isValid, "transformations as labels: C major, E minor, G major with L, R and R after L");
		this.assert(bad.isValid.not, "T7 takes C major to G major and is not R after L");
		this.assertEquals(net, RLNetwork(ctx, [[\a, c], [\b, ctx.space.minor(4)], [\c, ctx.space.major(7)]], [[\a, \b, \L], [\b, \c, \R], [\a, \c, ctx.r <> ctx.l]]), "equal by nodes, contents and labels");
		this.assertException({ RLNetwork(ctx, [[\a, c]], [[\a, \b, \L]]) }, Error, "an arrow to a node that is not there is refused");
	}
}
