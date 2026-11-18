// reLation, networks of networks: isographies named and composed (Lewin's <T1>, <I5>), networks as the objects
// of a context (RLRelationContext.networks), a network on that context, and the same again one level up.
// No server. The examples: Webern's network and Gesualdo's square (Popoff's posts on networks, as
// HomewareSC's Math/Category_Theory/THEORY.md § 1.5 recalls them), and Lewin's remark that the isographies
// <T(p)>, <I(p)> compose as T(p), I(p) do, so that a network of networks can have the labels of its own nodes.
TestRLNetworksOfNetworks : UnitTest {
	var pcs, cat;

	setUp {
		pcs = RLRelationContext.pitchClasses;
		cat = pcs.category;
	}

	prT { |p| ^pcs.isography([\T1, ("I" ++ p).asSymbol]) }
	prI { |p| ^pcs.isography([\T11, ("I" ++ p).asSymbol]) }
	prAfter { |a, b| ^b.collect { |i| a[i] } }

	test_isographies_by_name {
		var auts = cat.automorphisms, t1 = this.prT(1), i5 = this.prI(5), name = { |am, l| pcs.nameOf(cat.imageOf(am, pcs.transform(l))) };
		this.assert(auts.includesEqual(t1) and: { auts.includesEqual(i5) }, "<T1> and <I5> are two of the 48 automorphisms of T and I");
		this.assertEquals([name.(t1, \T4), name.(t1, \I8)], ["T4", "I9"], "<T1> keeps every T and sends I(n) to I(n + 1)");
		this.assertEquals([name.(i5, \T4), name.(i5, \I8)], ["T8", "I9"], "<I5> sends T(n) to T(-n) and I(n) to I(5 - n)");
		this.assertEquals(pcs.isography([\I0, \T1]), nil, "no automorphism sends T1 to an inversion");
		this.assertEquals(pcs.isography([\T5, \I0]), cat.automorphism([pcs.transform(\T5), pcs.transform(\I0)]), "names, words or relations");
		this.assertEquals(Array.fill(12) { |p| [this.prT(p), this.prI(p)] }.flatten(1).asSet.size, 24, "24 of them are a <T(p)> or an <I(p)>");
	}

	// Lewin: <T(p)> and <I(p)> compose as T(p) and I(p)
	test_they_compose_as_t_and_i {
		var ok = true;
		12.do { |p|
			12.do { |q|
				if(this.prAfter(this.prT(p), this.prT(q)) != this.prT((p + q) % 12)) { ok = false };
				if(this.prAfter(this.prI(p), this.prI(q)) != this.prT((p - q) % 12)) { ok = false };
				if(this.prAfter(this.prT(p), this.prI(q)) != this.prI((p + q) % 12)) { ok = false };
				if(this.prAfter(this.prI(p), this.prT(q)) != this.prI((p - q) % 12)) { ok = false };
			};
		};
		this.assert(ok, "<T(p)><T(q)> = <T(p + q)>, <I(p)><I(q)> = <T(p - q)>, <T(p)><I(q)> = <I(p + q)>, <I(p)><T(q)> = <I(p - q)>");
		this.assertEquals(pcs.nameOf(pcs.transform(\I9) <> pcs.transform(\I8)), "T1", "as I9 after I8 is T1");
	}

	// Webern's network (A, B, Bb; I8, I9, T1) and what an isography offers
	test_what_an_isography_offers {
		var w1 = RLNetwork(pcs, [[\x, 9], [\y, 11], [\z, 10]], [[\x, \y, \I8], [\y, \z, \I9], [\x, \z, \T1]]);
		var w2 = RLNetwork(pcs, [[\x, 1], [\y, 7], [\z, 2]], [[\x, \y, \I8], [\y, \z, \I9], [\x, \z, \T1]]);
		var n1 = RLNetwork(pcs, [[\a, 1], [\b, 5], [\c, 10]], [[\a, \b, \I6], [\b, \c, \T5], [\a, \c, \I11]]);
		var plain = RLNetwork(pcs, [[\x, 0], [\y, 4], [\z, 7]], [[\x, \y, \T4], [\y, \z, \T3], [\x, \z, \T7]]);
		var offered = w1.isographs(this.prT(1));
		this.assertEquals(offered.size, 12, "<T1> offers twelve networks");
		this.assert(offered.every { |n| n.isValid and: { n.labelNames == ["I9", "I10", "T1"] } }, "each valid, with the labels I9, I10, T1");
		this.assertEquals(w1.isographiesTo(w2), [this.prT(0)], "two networks with the same labels: one isography, the identity");
		this.assertEquals(n1.isographiesTo(n1.transformedBy(RLAutomorphism.affine(pcs, 11, 8))), [this.prI(4)],
			"C#, F, Bb to its inversion about 8: the isography <I4>");
		this.assertEquals(w1.isographiesTo(plain), [], "Webern's network and C, E, G under T4, T3, T7: none");
		this.assertException({ w1.isographiesTo(n1) }, Error, "two networks of different shapes are refused");
		this.assertEquals(w1.fillings(\x, 9), [w1], "the fillings from one object at one node");
	}

	// Gesualdo's square (TestRLNetwork has it corner by corner) as one network of four networks
	test_gesualdo_s_square_as_a_network {
		var cw = RLRelationContext.triads(RLEDO(12)), ws = cw.space;
		var word = { |k| [\R] ++ ([\L, \R] ! k).flat };
		var g1 = RLNetwork.chain(cw, [ws.major(9), ws.minor(0), ws.major(7)], [word.(6), word.(4)]);
		var t7 = RLAutomorphism.transposition(cw, 7), nu = RLAutomorphism(cw, { |c| if(c.type == \major) { ws.major(c.root + 7) } { c } });
		var hyper = RLRelationContext.networks(g1, [[\T7, t7], [\nu, nu]]);
		var corners = [g1, g1.transformedBy(t7), g1.transformedBy(nu), g1.transformedBy(nu).transformedBy(t7)];
		var arrows = [[\a, \b, \T7], [\a, \c, \nu], [\b, \d, \nu], [\c, \d, \T7]];
		var square = RLNetwork(hyper, [\a, \b, \c, \d].collect { |n, i| [n, corners[i]] }, arrows);
		var bent = RLNetwork(hyper, [[\a, g1], [\b, corners[1]], [\c, corners[2]], [\d, corners[1]]], arrows);
		this.assertEquals(hyper.objects.size, 144, "the networks the first becomes: 144, twelve places for the majors by twelve for the minors");
		this.assert(hyper.objects.every(_.isValid), "each a valid network of three triads");
		this.assertEquals([hyper.category.size, hyper.category.isGroup], [144, true], "the two moves generate a group of 144");
		this.assert(square.isValid, "the square is a valid network: T7 then nu is nu then T7");
		this.assertEquals(square.at(\d).objects.collect(_.name), ["B major", "G minor", "A major"], "its fourth corner: B, Gm, A");
		this.assert(bent.isValid.not, "a wrong corner is seen");
		this.assertEquals(square.fillings(\a, g1), [square], "the first corner decides the three others");
		this.assertEquals(square.fillings(\a).size, 144, "one square from each network");
	}

	// A cell and a plan with the cell's own labels: C#, Eb, G under I4, I10, T6; the plan moves the notes by
	// I2, I5, T3, which move the labels as <I4>, <I10>, <T6> do. Then the same one level up: 27 notes.
	test_a_tower_with_its_own_labels {
		var cell = RLNetwork(pcs, [[\x, 1], [\y, 3], [\z, 7]], [[\x, \y, \I4], [\y, \z, \I10], [\x, \z, \T6]]);
		var moves = [RLAutomorphism.affine(pcs, 11, 2), RLAutomorphism.affine(pcs, 11, 5), RLAutomorphism.affine(pcs, 1, 3)];
		var names = [\i2, \i5, \t3], plan = { |ctx, seed| RLNetwork(ctx, [\x, \y, \z].collect { |n| [n, seed] }, [[\x, \y, \i2], [\y, \z, \i5], [\x, \z, \t3]]) };
		var hyper = RLRelationContext.networks(cell, names.collect { |n, i| [n, moves[i]] }), top, up, hyper2, top3;
		this.assert(cell.isValid, "the cell is a valid network");
		this.assertEquals(moves.collect(_.arrowMap), [this.prI(4), this.prI(10), this.prT(6)], "I2, I5, T3 move the labels as <I4>, <I10>, <T6>: the cell's own labels");
		this.assertEquals([hyper.objects.size, hyper.category.size, hyper.category.isGroup], [8, 8, true], "eight networks, moved by a group of eight");
		this.assert(plan.(hyper, cell).isValid.not, "the cell at every node is not a network of networks");
		top = plan.(hyper, cell).fillings(\x, cell);
		this.assertEquals(top.size, 1, "from the cell, one network of networks has this plan");
		top = top[0];
		this.assert(top.isValid, "valid: I5 after I2 is T3, as I10 after I4 is T6");
		this.assertEquals(top.objects.collect(_.objects), [[1, 3, 7], [1, 11, 7], [4, 6, 10]], "its nine notes");
		this.assertEquals(top.objects.collect(_.labelNames), [["I4", "I10", "T6"], ["I0", "I6", "T6"], ["I10", "I4", "T6"]], "the labels of its three networks");
		up = moves.collect { |m| RLAutomorphism.onNetworks(hyper, m) };
		this.assert(up.every(_.isValid), "the three moves carried to the networks are automorphisms there");
		hyper2 = RLRelationContext.networks(top, names.collect { |n, i| [n, up[i]] });
		top3 = plan.(hyper2, top).fillings(\x, top);
		this.assertEquals([hyper2.objects.size, top3.size], [8, 1], "one level up: eight networks of networks, one with the plan");
		this.assert(top3[0].isValid, "a valid network of networks of networks");
		this.assertEquals(top3[0].objects.collect { |n| n.objects.collect(_.objects) }.flat.size, 27, "27 notes");
		this.assertEquals(top3[0].at(\z).objects.collect(_.objects), [[4, 6, 10], [4, 2, 10], [7, 9, 1]], "its last nine: the first nine a minor third up");
	}

	// Isographies as moves: the contents are free, so a move is a relation with twelve images
	test_isographies_as_moves {
		var w1 = RLNetwork(pcs, [[\x, 9], [\y, 11], [\z, 10]], [[\x, \y, \I8], [\y, \z, \I9], [\x, \z, \T1]]);
		var hyper = RLRelationContext.networks(w1, [['<T1>', this.prT(1)], ['<I0>', this.prI(0)], ['<I8>', this.prI(8)], ['<I9>', this.prI(9)]],
			600, ['<T1>', '<I0>']), m = hyper.category, top;
		this.assertEquals(hyper.objects.size, 288, "24 patterns of labels, twelve networks each");
		this.assertEquals(hyper.transform('<T1>').degrees.asSet, Set[12], "a move offers twelve networks");
		this.assertEquals([m.size, m.isGroup], [25, false], "the monoid: the 24 isographies and the identity, which is none of them");
		this.assertEquals(m.arrow(['<I0>'] ++ ('<T1>' ! 8)), hyper.transform('<I8>'), "<I0> then <T1> eight times is <I8>");
		this.assertEquals(hyper.transform('<I9>') <> hyper.transform('<I8>'), hyper.transform('<T1>'), "<I9> after <I8> is <T1>");
		this.assert(m.arrow('<T1>' ! 12) != hyper.identityTransform, "<T1> twelve times: the networks with the same labels, not each network alone");
		top = RLNetwork(hyper, [[\x, w1], [\y, w1.isographs(this.prI(8))[3]], [\z, w1.isographs(this.prT(1))[7]]],
			[[\x, \y, '<I8>'], [\y, \z, '<I9>'], [\x, \z, '<T1>']]);
		this.assert(top.isValid, "a network of networks with the labels of its first node: <I8>, <I9>, <T1> over I8, I9, T1");
		this.assertEquals(top.labelNames.collect { |n| n.reject { |c| "<>".includes(c) } }, w1.labelNames, "name for name");
		this.assertException({ RLRelationContext.networks(w1, [['<T1>', this.prT(1)]], 100) }, Error, "more networks than max is an Error");
		this.assertException({ RLRelationContext.networks(w1, [[\none, pcs.isography([\I0, \T1])]]) }, Error, "a move that is no isography (nil) is refused by name");
	}

	test_an_empty_network {
		var empty = RLNetwork(pcs, [], []);
		this.assert(empty.isValid, "a network of nothing is valid");
		this.assertEquals(empty.fillings, [empty], "and is its own one filling");
		this.assertEquals(RLRelationContext.networks(empty, [['<T1>', this.prT(1)]]).objects, [empty], "moved, it stays what it is");
	}
}
