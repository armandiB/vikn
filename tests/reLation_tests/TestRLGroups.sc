// reLation, chords as group elements: RLExtension, RLWreath. No server.
// HomewareSC's Math/Category_Theory/THEORY.md § 1.2 and CHUCK_CODE.md.
TestRLExtension : UnitTest {
	var space, base, ctx;

	setUp {
		space = RLChordSpace.triads(RLEDO(12));
		base = space.major(0);
		ctx = RLChordContext(space);
	}

	// the eight groups of order 24 the blog lists, by the orders of their elements
	test_the_eight_groups_in_twelve {
		var expected = [
			[[1, 1], "the cyclic group of 24", [[1, 1], [2, 1], [3, 2], [4, 2], [6, 2], [8, 4], [12, 4], [24, 8]]],
			[[0, 1], "Z12 x Z2", [[1, 1], [2, 3], [3, 2], [4, 4], [6, 6], [12, 8]]],
			[[0, 11], "the dihedral group", [[1, 1], [2, 13], [3, 2], [4, 2], [6, 2], [12, 4]]],
			[[0, 5], "Z4 x S3", [[1, 1], [2, 7], [3, 2], [4, 8], [6, 2], [12, 4]]],
			[[0, 7], "Z3 x D8", [[1, 1], [2, 5], [3, 2], [4, 2], [6, 10], [12, 4]]],
			[[2, 7], "Z3 x Q8", [[1, 1], [2, 1], [3, 2], [4, 6], [6, 2], [12, 12]]],
			[[3, 5], "Z3 : Z8", [[1, 1], [2, 1], [3, 2], [4, 2], [6, 2], [8, 12], [12, 4]]],
			[[6, 11], "Z3 : Q8", [[1, 1], [2, 1], [3, 2], [4, 14], [6, 2], [12, 4]]]
		];
		expected.do { |row|
			var ext = RLExtension(12, row[0][0], row[0][1]);
			this.assertEquals(ext.orders, row[2], "(p, q) = %: the element orders of %".format(row[0], row[1]));
			this.assert(ext.isGroup, "% is a group".format(row[0]));
		};
		this.assertEquals(RLExtension.classes(12).size, 8, "eight different groups in 12");
		this.assert(expected.every { |row| RLExtension.classes(12).includesEqual(row[0]) }, "with these (p, q)");
		this.assert(RLExtension(12, 2, 7).isAssociative, "the product of a valid pair is associative");
	}

	test_other_divisions {
		this.assertEquals(RLExtension.classes(19), [[0, 1], [0, 18]], "a prime division has two: the product and the dihedral group");
		this.assertEquals(RLExtension.classes(31).size, 2, "31 too");
		this.assertEquals(RLExtension(19, 0, 18).orders, [[1, 1], [2, 19], [19, 18]], "the dihedral group of 38");
	}

	test_the_dihedral_one_is_the_chord_context {
		var ext = RLExtension(12, 0, 11), bad = 0;
		space.all.do { |x|
			if(ext.right([4, 1], x, base) != ctx.l.(x)) { bad = bad + 1 };
			if(ext.right([0, 1], x, base) != ctx.p.(x)) { bad = bad + 1 };
			if(ext.right([9, 1], x, base) != ctx.r.(x)) { bad = bad + 1 };
			if(ext.left([5, 0], x, base) != ctx.t(5).(x)) { bad = bad + 1 };
			if(ext.left([7, 1], x, base) != ctx.i(2).(x)) { bad = bad + 1 };
		};
		this.assertEquals(bad, 0, "(0, 11): right is P, L, R and left is T, I, on every triad");
		this.assertEquals(ext.element(space.minor(7), base), [7, 1], "a chord as an element");
		this.assertEquals(ext.chordOf([7, 1], space.minor(2)), space.major(9), "and back, from another base");
		this.assertEquals(ext.inv([4, 1]), [4, 1], "an inversion undoes itself");
		this.assertEquals(ext.inv([5, 0]), [7, 0], "a transposition is undone by its opposite");
	}

	test_the_two_actions_commute_in_a_group {
		var bad = 0;
		[[0, 7], [2, 7], [6, 11], [3, 5]].do { |pq|
			var ext = RLExtension(12, pq[0], pq[1]);
			[[6, 0], [3, 1], [1, 1]].do { |g|
				[[5, 1], [2, 0]].do { |h|
					space.all.do { |x|
						if(ext.left(g, ext.right(h, x, base), base) != ext.right(h, ext.left(g, x, base), base)) { bad = bad + 1 };
					};
				};
			};
		};
		this.assertEquals(bad, 0, "left then right is right then left, in four of the groups");
	}

	test_moving_the_base {
		var ext = RLExtension(12, 2, 7), x = space.minor(7), g = [6, 0];
		this.assertEquals(ext.right(g, x, space.minor(0)), space.minor(1), "from one base");
		this.assertEquals(ext.right(g, x, space.major(3)), space.minor(1), "a transposition on the right of a chord of the other type goes through q");
		this.assertEquals(ext.right([3, 1], x, space.minor(0)), space.major(10), "an element that changes the type, from a minor base: Bb major");
		this.assertEquals(ext.right([3, 1], x, space.major(3)), space.major(6), "and from a major base: F# major");
	}

	// CHUCK_CODE.md: the calls of 2019 gave phi the first number and zeta the second
	test_structures_that_are_not_groups {
		var swapped = RLExtension(12, 7, 0, false), x = space.minor(7), bad = 0;
		this.assertException({ RLExtension(12, 7, 0) }, Error, "(p, q) = (7, 0) is refused");
		this.assert(swapped.isGroup.not, "taken on request, it is not a group");
		this.assert(swapped.isAssociative.not, "its product is not associative");
		this.assert(swapped.failures > 100, "for many triples of the 13824");
		this.assertEquals(swapped.mul([3, 1], [5, 0]), [3, 1], "a left factor of the second type forgets the root it meets");
		space.all.do { |c|
			if(swapped.left([3, 1], swapped.right([5, 0], c, base), base) != swapped.right([5, 0], swapped.left([3, 1], c, base), base)) { bad = bad + 1 };
		};
		this.assertEquals(bad, 12, "and left and right no longer commute: on the 12 minor triads, for one pair of elements");
		this.assertEquals(RLExtension(12, 0, 7), RLExtension(12, 0, 7), "an extension is a value");
	}
}

// More than Z_n by Z_2: any number of types in a cycle, the just lattice as roots, any finite group of types.
TestRLExtensionGeneral : UnitTest {

	// the two conditions are exactly what associativity asks, checked over every triple of small structures
	test_valid_means_associative {
		var bad = 0, count = 0, groups = 0;
		[[6, 2], [8, 2], [6, 3], [7, 3], [5, 4], [4, 4]].do { |nm|
			var n = nm[0], m = nm[1];
			n.do { |p|
				n.do { |q|
					var ext = RLExtension.cyclic(RLEDO(n), m, p, q, false);
					count = count + 1;
					if(ext.isGroup) { groups = groups + 1 };
					if(ext.isGroup != RLExtension.isValid(n, p, q, m)) { bad = bad + 1 };
					if(ext.isGroup != ext.isAssociative) { bad = bad + 1 };
					if(ext.isGroup) {
						ext.elements.do { |g|
							if((ext.mul(g, ext.inv(g)) != [0, 0]) or: { ext.mul(ext.inv(g), g) != [0, 0] }) { bad = bad + 1 };
						};
					};
				};
			};
		};
		this.assertEquals(bad, 0, "% structures on 2, 3 and 4 types: a group by the two conditions exactly when associative, each element then undone by its inverse".format(count));
		this.assert(groups > 40, "% of them are groups".format(groups));
	}

	test_three_and_four_types {
		var frob = RLExtension.cyclic(RLEDO(7), 3, 0, 2), z44 = RLExtension.cyclic(RLEDO(4), 4, 0, 3);
		var space = RLChordSpace(RLEDO(7), [[\a, [0, 2, 4]], [\b, [0, 2, 5]], [\c, [0, 3, 5]]]), base = space.chord(0, \a);
		this.assertEquals(frob.orders, [[1, 1], [3, 14], [7, 6]], "Z7 by three types, q = 2: the group of order 21 with 14 elements of order 3");
		this.assertEquals(z44.orders, [[1, 1], [2, 3], [4, 12]], "Z4 by four types, q = 3: three involutions, twelve elements of order 4");
		this.assertEquals(frob.types, 3, "three types");
		this.assertEquals(frob.elements.collect { |g| frob.right(g, base, base) }.asSet.size, 21, "its right action takes the base chord to each of the 21 chords");
		this.assertEquals(frob.elements.collect { |g| frob.left(g, base, base) }.asSet.size, 21, "so does its left action");
		this.assertEquals(frob.element(frob.chordOf([3, 2], space.chord(1, \b)), space.chord(1, \b)), [3, 2], "a chord as an element and back, from any base");
		this.assertEquals(RLExtension.valid(7, 3).size, 9, "nine valid pairs for 7 steps and three types: q = 1 with any p, q = 2 and 4 with p = 0");
		this.assertException({ RLExtension.cyclic(RLEDO(7), 3, 0, 3) }, Error, "q = 3 is refused: three times is not the identity");
	}

	test_on_the_just_lattice {
		var just = RLJust([3, 5]), jctx = RLChordContext(RLChordSpace.triads(just)), base = jctx.space.major;
		var dihedral = RLExtension.cyclic(just), swap = RLExtension.cyclic(just, 2, [1, 1], [[0, 1], [1, 0]]), bad = 0;
		var patch = [-2, -1, 0, 1, 2].collect { |x| [-1, 0, 1].collect { |y| [jctx.space.major([x, y]), jctx.space.minor([x, y])] } }.flat;
		this.assert(dihedral.isGroup, "the lattice by two types with inversion: a group");
		patch.do { |c|
			if(dihedral.right([[0, 1], 1], c, base) != jctx.l.(c)) { bad = bad + 1 };
			if(dihedral.right([[0, 0], 1], c, base) != jctx.p.(c)) { bad = bad + 1 };
			if(dihedral.right([[-1, 1], 1], c, base) != jctx.r.(c)) { bad = bad + 1 };
			if(dihedral.left([[1, 0], 0], c, base) != jctx.t([1, 0]).(c)) { bad = bad + 1 };
		};
		this.assertEquals(bad, 0, "it is the chord context of the lattice: right P, L, R, left T, on a patch of 30 triads");
		this.assertEquals([dihedral.order([[0, 1], 1]), dihedral.order([[1, 0], 0])], [2, nil], "an inversion has order 2, a transposition never comes back");
		this.assert(swap.isGroup, "thirds and fifths exchanged, with p = 15/8 that the exchange keeps: another group on the just triads");
		thisThread.randSeed = 11;
		bad = 0;
		200.do {
			var a = [[5.rand2, 5.rand2], 2.rand], b = [[5.rand2, 5.rand2], 2.rand], c = [[5.rand2, 5.rand2], 2.rand];
			if(swap.mul(swap.mul(a, b), c) != swap.mul(a, swap.mul(b, c))) { bad = bad + 1 };
			if(swap.mul(a, swap.inv(a)) != swap.identity) { bad = bad + 1 };
			if(swap.left(a, swap.right(b, base, base), base) != swap.right(b, swap.left(a, base, base), base)) { bad = bad + 1 };
		};
		this.assertEquals(bad, 0, "associative, with inverses, its two actions commuting, on 200 random triples");
		this.assertEquals(swap.right([[0, 0], 1], base, base), jctx.space.minor([0, 0]), "its simplest contextual move from 1/1 major");
		this.assertEquals(swap.right([[0, 0], 1], jctx.space.minor([0, 0]), base), jctx.space.major([1, 1]), "and back: not to 1/1 but to 15/8 major");
		this.assertException({ RLExtension.cyclic(just, 2, [0, 0], [[1, 1], [0, 1]]) }, Error, "a matrix that is not its own inverse is refused");
		this.assertException({ RLExtension.cyclic(just, 2, [1, 0], [[0, 1], [1, 0]]) }, Error, "and a p the exchange does not keep");
		this.assertException({ swap.elements }, Error, "a lattice has no list of all its elements");
	}

	// the 48 affine maps of twelve pitch classes as an extension: Z12 by the four-group of its multipliers
	test_any_group_of_types {
		var klein = [[0, 1, 2, 3], [1, 0, 3, 2], [2, 3, 0, 1], [3, 2, 1, 0]], units = [1, 11, 5, 7];
		var hol = RLExtension.general(RLEDO(12), klein, units, { 0 });
		var affine = Dictionary.new, order, space, base;
		// the same group made by hand: x -> kx + c composed until the identity
		order = { |k, c|
			var kk = k, cc = c, count = 1;
			while { (kk != 1) or: { cc != 0 } } { cc = ((k * cc) + c) % 12; kk = (k * kk) % 12; count = count + 1 };
			count
		};
		units.do { |k| 12.do { |c| var o = order.(k, c); affine[o] = (affine[o] ? 0) + 1 } };
		this.assert(hol.isGroup and: { hol.elements.size == 48 }, "a group of 48");
		this.assertEquals(hol.orders, affine.asSortedArray, "with the element orders of the 48 affine maps");
		this.assert(hol.isAssociative, "associative over every triple");
		space = RLChordSpace(RLEDO(12), [[\dom7, [0, 4, 7, 10]], [\min7, [0, 3, 7, 10]], [\hdim7, [0, 3, 6, 10]], [\maj7, [0, 4, 7, 11]]]);
		base = space.chord(0, \dom7);
		this.assertEquals(hol.elements.collect { |g| hol.right(g, base, base) }.asSet.size, 48, "it moves four types of seventh chords simply transitively");
		this.assertException({ RLExtension.general(RLEDO(12), klein, [1, 11, 5, 5], { 0 }) }, Error, "multipliers that do not follow the table are refused");
		this.assertException({ RLExtension.general(RLEDO(12), klein, units, { |h, k| if((h == 1) and: { k == 1 }) { 1 } { 0 } }) }, Error, "and a zeta that is no cocycle");
		this.assert(RLExtension.general(RLEDO(12), klein, units, { |h, k| if((h == 1) and: { k == 1 }) { 1 } { 0 } }, false).isAssociative.not, "taken on request, it is not associative");
	}
}

TestRLWreath : UnitTest {
	var space, ctx, w;

	setUp {
		space = RLChordSpace.triads(RLEDO(12));
		ctx = RLChordContext(space);
		w = RLWreath(12, 2);
	}

	test_hook_s_288 {
		var els = w.elements;
		this.assertEquals([w.size, els.size, els.asSet.size], [288, 288, 288], "288 uniform triadic transformations");
		this.assertEquals(RLWreath(12, 5).size, 12 ** 5 * 120, "five types of seventh chords: 12^5 times 5!");
		this.assert(els.includesEqual(w.mul(els[100], els[201])), "closed under composition");
		this.assertEquals(w.mul(els[77], w.inv(els[77])), w.identity, "each undone by its inverse");
	}

	test_p_l_r_are_uniform_and_inversions_are_not {
		this.assertEquals(w.of(ctx.p, space), [[0, 0], [1, 0]], "P = <0, 0, ->");
		this.assertEquals(w.of(ctx.l, space), [[4, 8], [1, 0]], "L = <4, 8, ->");
		this.assertEquals(w.of(ctx.r, space), [[9, 3], [1, 0]], "R = <9, 3, ->");
		this.assertEquals(w.of(ctx.t(5), space), [[5, 5], [0, 1]], "T5 = <5, 5, +>");
		this.assertEquals(w.of(ctx.i(0), space), nil, "an inversion is not uniform");
		this.assertEquals(w.of(ctx.l <> ctx.r, space), w.mul(w.of(ctx.l, space), w.of(ctx.r, space)), "composition is kept");
		this.assertEquals(w.act([[4, 8], [1, 0]], space.major(0)), space.minor(4), "acting on a chord");
	}

	// THEORY.md § 1.2: Beethoven's Ninth alternates R and L, one transformation repeated
	test_one_transformation_repeated {
		var u = [[9, 8], [1, 0]], chord = space.major(0), names = [chord.name];
		5.do { chord = w.act(u, chord); names = names.add(chord.name) };
		this.assertEquals(names, ["C major", "A minor", "F major", "D minor", "Bb major", "G minor"], "<9, 8, -> again and again");
		this.assertEquals(w.mul(u, u), [[5, 5], [0, 1]], "twice is a fourth up");
		this.assertEquals(w.order(u), 24, "of order 24: it meets every triad");
	}

	// THEORY.md § 1.2: the right action of each extension is a simply transitive set of uniform transformations
	test_right_actions_are_uniform {
		var base = space.major(0);
		RLExtension.classes(12).do { |pq|
			var ext = RLExtension(12, pq[0], pq[1]);
			var rights = ext.elements.collect { |g| w.of({ |c| ext.right(g, c, base) }, space) };
			this.assert(rights.every(_.notNil) and: { rights.asSet.size == 24 }, "%: 24 different uniform transformations".format(pq));
			this.assertEquals(rights.collect { |u| w.act(u, base) }.asSet.size, 24, "%: taking the base chord to every triad".format(pq));
		};
		this.assertEquals(RLExtension(12, 0, 11).elements.count { |g| w.of({ |c| RLExtension(12, 0, 11).left(g, c, base) }, space).notNil }, 12,
			"on the left, the dihedral group is uniform for its 12 transpositions only");
	}

	test_other_trichords_and_divisions {
		var e19 = RLChordContext(RLChordSpace.triads(RLEDO(19))), w19 = RLWreath(19, 2);
		this.assertEquals(w19.of(e19.l, e19.space), [[6, 13], [1, 0]], "19 steps: L = <a, -a, ->");
		this.assertEquals(w19.of(e19.r, e19.space), [[14, 5], [1, 0]], "R = <a - b, b - a, ->");
		this.assertEquals(w19.size, 722, "19 times 19 times 2");
	}
}
