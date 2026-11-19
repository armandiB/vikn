// reLation, regions: Schoenberg's chart of the regions and his classes of relationship (Structural Functions of
// Harmony, pp. 20, 30, 68-69, 75). No server.
TestRLRegions : UnitTest {

	test_the_chart_round_c_major {
		var edo = RLEDO(12), r = RLRegions(edo, 0, \major);
		var tonics = { |names| names.collect { |n| edo.name(r.tonicOf(n)) } };
		this.assertEquals(tonics.([\T, \D, \SD, \sm, \m]), ["C", "G", "F", "A", "E"], "the direct and close regions of C major");
		this.assertEquals(tonics.([\t, \sd, \v, \SM, \M, \bM, \bSM]), ["C", "F", "G", "A", "E", "Eb", "Ab"], "the indirect but close ones");
		this.assertEquals(tonics.([\Np, \dor, 'S/T', \bMD, \bmv]), ["C#", "D", "D", "Bb", "Bb"], "the indirect and remote ones (C# is D flat)");
		this.assertEquals(r.region(\sm), [3, \minor], "the submediant minor: three fifths up, minor");
		this.assertEquals(r.region([3, \minor]), [3, \minor], "a region is returned as it is");
		this.assertEquals([\T, \D, \sm, \t, \bSM, \bm, \MM, \Np, \dor].collect { |n| r.classOf(n) }, [0, 1, 1, 2, 2, 3, 3, 4, 4], "the classes");
		this.assertEquals(r.classOf([6, \major]), 5, "F sharp major is distant");
		this.assertEquals(r.nameOf([-4, \major]), \bSM, "a region's name");
		this.assertEquals(r.nameOf([6, \major]), nil, "none for a region the chart leaves in class 5");
	}

	test_names_in_a_row {
		var edo = RLEDO(12), r = RLRegions(edo, 0, \major);
		// the three readings the book spells out, p. 20
		this.assertEquals([r.region(\Mm), edo.name(r.tonicOf(\Mm))], [[8, \minor], "Ab"], "Mm: a minor region on G sharp");
		this.assertEquals([r.region(\SMsm), edo.name(r.tonicOf(\SMsm))], [[6, \minor], "F#"], "SMsm: a minor region on F sharp");
		this.assertEquals([r.region(\bsmSM), edo.name(r.tonicOf(\bsmSM))], [[-8, \major], "E"], "bsmSM: a major region on F flat");
		this.assertEquals(r.region('S/TSM'), [5, \major], "the supertonic's submediant major: B major");
		this.assertEquals(r.region(\bMD), r.region(\SDSD), "bMD is the subdominant's subdominant (p. 69)");
		this.assertEquals(r.region(\dor), r.region(\SDsm), "dor is the subdominant's submediant minor");
		this.assertEquals(r.region(\Np), r.region(\sdSM), "Np is the subdominant minor's submediant major");
		this.assertEquals(r.region(\bmv), r.region(\sdsd), "bmv is the subdominant minor's subdominant minor");
		this.assertException({ r.region(\xyz) }, Error, "an unknown name is refused");
	}

	test_enharmonic_regions_stay_apart {
		var edo = RLEDO(12), r = RLRegions(edo, 0, \major);
		this.assertEquals(r.tonicOf([8, \major]), r.tonicOf([-4, \major]), "G sharp and A flat are one pitch class in 12 steps");
		this.assertEquals([r.classOf([8, \major]), r.classOf([-4, \major])], [3, 2], "and two regions: MM is indirect, bSM indirect but close");
		this.assert(RLRegions(RLEDO(19), 0, \major).tonicOf([8, \major]) != RLRegions(RLEDO(19), 0, \major).tonicOf([-4, \major]),
			"in 19 steps they are two pitch classes");
	}

	test_round_a_minor_tonic {
		var edo = RLEDO(12), r = RLRegions(edo, 9, \minor);
		this.assertEquals([\M, \T, \v, \sd].collect { |n| edo.name(r.tonicOf(n)) }, ["C", "A", "E", "D"], "close to A minor: C major, A major, E minor, D minor");
		this.assertEquals([\D, \SM].collect { |n| edo.name(r.tonicOf(n)) }, ["E", "F"], "indirect but close: E major, F major");
		this.assertEquals([\M, \D, \SD, '#sm', \Np].collect { |n| r.classOf(n) }, [1, 2, 3, 4, 4], "the classes round a minor tonic");
		this.assertEquals(r.classOf([2, \major]), 5, "B major is distant from A minor");
	}

	test_between_two_regions_and_their_scales {
		var edo = RLEDO(12), r = RLRegions(edo, 0, \major);
		this.assertEquals(r.relation(\D, \sm), 4, "A minor seen from G major is its dor");
		this.assertEquals(r.relation(\sm, \T), 1, "C major seen from A minor is its mediant major");
		this.assertEquals(r.nameOf(\T, \sm), \M, "by name");
		this.assertEquals(r.scale(\sm), RLScale.minor(edo, 9), "a region's scale");
		this.assertEquals(r.scale([1, \major]).degrees, [7, 9, 11, 0, 2, 4, 6], "G major");
		this.assertEquals(RLRegions(RLJust([3, 5])).scale(\D).tonic, [1, 0], "on the lattice the dominant region is a fifth up");
		this.assertEquals(r.regions(1), [[-1, \major], [-1, \minor], [0, \major], [0, \minor], [1, \major], [1, \minor]], "the regions within a fifth");
	}
}
