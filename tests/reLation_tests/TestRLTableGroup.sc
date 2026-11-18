// reLation, the lists of extensions and the roots that do not commute: RLTableGroup, RLExtension.all, RLExtension
// over a group given by its table. No server. The groups are recognised by the orders of their elements.
TestRLTableGroup : UnitTest {

	test_groups_by_their_tables {
		var d4 = RLTableGroup.dihedral(4), q8 = RLTableGroup.quaternion;
		this.assertEquals(d4.orders, [[1, 1], [2, 5], [4, 2]], "the dihedral group of 8 elements: five of order 2");
		this.assertEquals(q8.orders, [[1, 1], [2, 1], [4, 6]], "the quaternion group: one of order 2");
		this.assert(d4.isAbelian.not and: { RLTableGroup.cyclic(12).isAbelian }, "one commutes, the other not");
		this.assertEquals([d4.center, q8.center], [[0, 2], [0, 1]], "their centres: the half turn, minus one");
		this.assertEquals(d4.pcAdd(4, 1), d4.pcAdd(3, 4), "s r = r^-1 s");
		this.assertEquals(d4.pcs.collect { |a| d4.pcAdd(a, d4.pcNeg(a)) }, 0 ! 8, "each element undone by its inverse");
		this.assertException({ RLTableGroup([[0, 1], [1, 1]]) }, Error, "a table that is no group's is refused");
		this.assertEquals(d4.span(d4.generators).size, 8, "a few elements that generate it");
	}

	test_automorphisms {
		var d4 = RLTableGroup.dihedral(4);
		this.assertEquals(RLTableGroup.cyclic(12).automorphisms.size, 4, "of twelve steps: the multiplications by 1, 5, 7, 11");
		this.assertEquals(RLTableGroup.dihedral(3).automorphisms.size, 6, "of the six permutations of three things: six");
		this.assertEquals(d4.automorphisms.size, 8, "of the dihedral group of 8: eight");
		this.assertEquals(RLTableGroup.quaternion.automorphisms.size, 24, "of the quaternion group: 24");
		this.assert(d4.automorphisms.every { |a| d4.isAutomorphism(a) }, "each one keeps the products");
		this.assert(d4.automorphisms.includesEqual(d4.inner(4)), "conjugation by an element is one of them");
		this.assert(d4.isAutomorphism([0, 2, 1, 3, 4, 5, 6, 7]).not, "a list that does not keep the products is none");
	}

	// T and I on twelve notes as a group by its table: THEORY.md's 48 automorphisms, found again another way
	test_the_group_of_a_category {
		var ti = RLTableGroup.of(RLRelationContext.pitchClasses(RLEDO(12)).category);
		this.assertEquals([ti.size, ti.isAbelian, ti.center.size], [24, false, 2], "24 elements, not commutative, the tritone central");
		this.assertEquals(ti.orders, RLTableGroup.dihedral(12).orders, "the orders of the dihedral group of 24");
		this.assertEquals(ti.automorphisms.size, 48, "48 automorphisms");
		this.assertEquals(ti.names[0], "e", "the identity first");
	}
}

TestRLExtensionLists : UnitTest {
	var two, three;

	setUp {
		two = [[0, 1], [1, 0]];
		three = [[0, 1, 2], [1, 2, 0], [2, 0, 1]];
	}

	test_the_eight_groups_come_out {
		var all = RLExtension.all(RLEDO(12), two), known = RLExtension.classes(12).collect { |pq| RLExtension(12, pq[0], pq[1]).orders };
		this.assertEquals(RLExtension.actions(RLEDO(12), two), [[1, 1], [1, 5], [1, 7], [1, 11]], "four actions of two types on twelve roots");
		this.assertEquals(RLExtension.cocycles(RLEDO(12), two, [1, 7]).collect { |z| z[1][1] }, [0, 2, 4, 6, 8, 10], "the cocycles for the action by 7");
		this.assertEquals(all.size, 8, "eight extensions of twelve roots by two types: two classes of cocycles for each action");
		this.assert(all.every(_.isGroup), "each one a group");
		this.assert(known.every { |o| all.collect(_.orders).includesEqual(o) } and: { all.collect(_.orders).asSet.size == 8 },
			"the eight groups of phase 4, each once, without being given");
	}

	test_other_lists {
		var four = RLExtension.all(RLEDO(4), two).collect(_.orders), seven = RLExtension.all(RLEDO(7), three);
		this.assertEquals(four.size, 4, "four roots by two types: four groups of eight elements");
		this.assert([[[1, 1], [2, 3], [4, 4]], [[1, 1], [2, 1], [4, 2], [8, 4]], [[1, 1], [2, 5], [4, 2]], [[1, 1], [2, 1], [4, 6]]].every { |o| four.includesEqual(o) },
			"Z4 x Z2, the cyclic group, the dihedral group and the quaternion group");
		[19, 31].do { |n| this.assertEquals(RLExtension.all(RLEDO(n), two).size, 2, "% roots by two types: the cyclic and the dihedral group".format(n)) };
		this.assertEquals(seven.size, 3, "seven roots by three types: three extensions");
		this.assertEquals(seven.collect(_.orders).asSet.size, 2, "two groups: the cyclic one, and the group of 21 twice (by 2 and by 4)");
		this.assert(seven.collect(_.orders).includesEqual([[1, 1], [3, 14], [7, 6]]), "the group of 21 elements that does not commute");
		this.assertEquals([RLExtension.cocycles(RLEDO(7), three, [1, 1, 1]).size, RLExtension.cocycles(RLEDO(7), three, [1, 2, 4]).size], [49, 7],
			"49 cocycles for the action that moves nothing, 7 for the action by 2: one class each, every cocycle a coboundary");
	}

	test_four_types {
		var klein = [[0, 1, 2, 3], [1, 0, 3, 2], [2, 3, 0, 1], [3, 2, 1, 0]], all = RLExtension.all(RLEDO(4), klein);
		this.assertEquals(RLExtension.actions(RLEDO(4), klein).size, 4, "the four-group acts on four roots four ways");
		this.assert(all.every { |e| e.isGroup and: { e.elements.size == 16 } }, "each extension a group of 16");
		this.assert(all.collect(_.orders).includesEqual([[1, 1], [2, 15]]).not, "none is all of order 2: the roots have an element of order 4");
		this.assert(all.collect(_.orders).includesEqual([[1, 1], [2, 7], [4, 8]]), "Z4 x Z2 x Z2 among them");
	}
}

TestRLExtensionTabled : UnitTest {
	var two;

	setUp { two = [[0, 1], [1, 0]] }

	// over every choice of an automorphism and an element: a group by Schreier's conditions exactly when the
	// product is associative
	prCensus { |roots|
		var good = Array.new, bad = 0, autos = roots.automorphisms;
		autos.do { |a|
			roots.pcs.do { |z|
				var ext = RLExtension.general(roots, two, [roots.pcs, a], [[0, 0], [0, z]], false);
				if(ext.isGroup != ext.isAssociative) { bad = bad + 1 };
				if(ext.isGroup) { good = good.add(ext) };
			};
		};
		^[good, bad, autos.size * roots.size]
	}

	test_six_permutations_by_two_types {
		var res = this.prCensus(RLTableGroup.dihedral(3)), good = res[0];
		this.assertEquals([res[2], res[1]], [36, 0], "36 choices: the conditions hold exactly when the product is associative");
		this.assertEquals(good.size, 6, "six of them give a group");
		this.assert(good.every { |e| e.orders == [[1, 1], [2, 7], [3, 2], [6, 2]] }, "always the same one: the dihedral group of 12");
		this.assert(good.every { |e| e.elements.every { |g| e.mul(g, e.inv(g)) == e.identity and: { e.mul(e.inv(g), g) == e.identity } } },
			"each element undone by its inverse, on both sides");
		this.assertException({ RLExtension.general(RLTableGroup.dihedral(3), two, [Array.series(6), Array.series(6)], [[0, 0], [0, 3]]) }, Error,
			"data that give no group are refused");
	}

	test_eight_elements_by_two_types {
		var res = this.prCensus(RLTableGroup.dihedral(4)), kinds = res[0].collect(_.orders);
		this.assertEquals([res[2], res[1]], [64, 0], "64 choices over the dihedral group of 8: conditions and associativity agree");
		this.assert(kinds.includesEqual([[1, 1], [2, 9], [4, 2], [8, 4]]), "the dihedral group of 16 among the groups");
		this.assert(kinds.includesEqual([[1, 1], [2, 5], [4, 6], [8, 4]]), "the semidihedral group");
		this.assert(kinds.includesEqual([[1, 1], [2, 11], [4, 4]]), "the product with Z2");
	}

	test_over_the_quaternions {
		var q8 = RLTableGroup.quaternion, kinds = Array.new;
		q8.automorphisms.do { |a|
			q8.pcs.do { |z|
				var ext = RLExtension.general(q8, two, [q8.pcs, a], [[0, 0], [0, z]], false);
				if(ext.isGroup and: { kinds.includesEqual(ext.orders).not }) { kinds = kinds.add(ext.orders) };
			};
		};
		this.assert(kinds.includesEqual([[1, 1], [2, 1], [4, 10], [8, 4]]), "the quaternion group of 16 among the groups");
		this.assert(kinds.includesEqual([[1, 1], [2, 3], [4, 12]]), "the product with Z2");
		this.assert(kinds.includesEqual([[1, 1], [2, 5], [4, 6], [8, 4]]), "the semidihedral group again");
	}

	// the 48 transformations of 24 quarter-tones: T and I of 12 notes by two types, "on the grid" and "a
	// quarter-tone off"
	test_quarter_tones {
		var ti = RLTableGroup.dihedral(12), quarter = Array.series(12) ++ Array.fill(12) { |i| 12 + ((i - 1) % 12) };
		var ext = RLExtension.general(ti, two, [ti.pcs, quarter], [[0, 0], [0, 1]]);
		this.assert(ti.isAutomorphism(quarter) and: { Array.series(24).every { |a| ti.inner(a) != quarter } },
			"moving every inversion by one is an automorphism of T and I that no conjugation does");
		this.assertEquals(ext.elements.size, 48, "48 elements");
		this.assertEquals(ext.orders, RLTableGroup.dihedral(24).orders, "with the orders of T and I on 24 notes");
		this.assertEquals(ext.mul([0, 1], [0, 1]), [1, 0], "two quarter-tones are a semitone");
		this.assertEquals(ext.order([0, 1]), 24, "a quarter-tone taken 24 times is back");
		this.assert(ext.isAssociative, "associative over every triple");
		this.assertException({ RLExtension.general(ti, two, [ti.pcs, quarter], [[0, 0], [0, 2]]) }, Error,
			"with a whole tone for two quarter-tones it is no group");
	}
}
