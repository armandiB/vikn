// reLation, every automorphism of a context, by search: the counts of Popoff's posts and of the study notes,
// recomputed by the classes; and the monoids of U, P, L in other divisions. No server. Takes some seconds.
TestRLAutomorphismSearch : UnitTest {

	// THEORY.md § 1.6: 48 automorphisms of T and I on twelve notes, the maps x -> kx + c; 342 in 19 steps
	test_pitch_classes {
		var twelve = RLRelationContext.pitchClasses(RLEDO(12)), all = RLAutomorphism.all(twelve);
		this.assertEquals(all.size, 48, "T and I on 12 notes: 48 one-to-one maps of the notes turn the group into itself");
		this.assert(all.every(_.isValid), "each one valid");
		this.assert(all.includesEqual(RLAutomorphism.affine(twelve, 5, 3)), "5x + 3 among them");
		this.assertEquals(all.collect(_.arrowMap).asSet.size, 24, "they realise 24 of the 48 automorphisms of the group, each twice");
		this.assertEquals(RLAutomorphism.count(RLRelationContext.pitchClasses(RLEDO(19))), 342, "on 19 notes: 342, the maps kx + c");
		this.assertEquals(RLAutomorphism.all(twelve, 10), nil, "nil past the number allowed");
	}

	// Popoff's count for Cube Dance (the blog's code post; study notes 02): 648 maps of the 28 chords for each
	// of the 12 automorphisms of the monoid of U, P, L, 7776 in all
	test_cube_dance {
		var cd = RLRelationContext.cubeDance, maps = cd.category.automorphisms;
		this.assertEquals(maps.size, 12, "the monoid of U, P, L has 12 automorphisms");
		this.assertEquals(RLAutomorphism.count(cd, 100000, [maps.first]), 648, "648 maps of the chords for one of them");
		this.assertEquals(RLAutomorphism.count(cd), 7776, "7776 automorphisms of Cube Dance in all");
	}

	test_the_triads {
		this.assertEquals(RLAutomorphism.count(RLRelationContext.triads), 1152,
			"the 24 triads under P, L, R: 1152, 24 for each of the 48 automorphisms of the group");
	}

	// PROGRESS.md, phase 2: the monoid of U, P, L depends on the division
	test_monoids_of_other_divisions {
		this.assertEquals([15, 18, 24].collect { |n| RLRelationContext.cubeDance(RLEDO(n)).category.size }, [172, 82, 140],
			"172 relations in 15 steps, 82 in 18, 140 in 24 (40 in 12)");
	}
}
