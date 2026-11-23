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

	test_regions_of_any_mode {
		var edo = RLEDO(12), r = RLRegions(edo, 0, \major), d = RLRegions(edo, 2, \dorian);
		this.assertEquals(r.scale([1, \mixolydian]).degrees, [7, 9, 11, 0, 2, 4, 5], "the Mixolydian region on G");
		this.assertEquals(r.scale([2, \dorian]).tonic, 2, "the Dorian on D");
		this.assertEquals(r.regions(0, [\major, \minor, \dorian]), [[0, \major], [0, \minor], [0, \dorian]], "the regions of the modes named");
		this.assertEquals([\mixolydian, \lydian, \major, \dorian, \phrygian, \minor].collect { |m| RLRegions.third(m) },
			[\major, \major, \major, \minor, \minor, \minor], "a mode is seen as major or minor by the third on its tonic");
		this.assertEquals(RLRegions.interval(\major, 1, \mixolydian), [1, 0], "G Mixolydian from C major: the centre a fifth up, the same tones");
		this.assertEquals(RLRegions.interval(\major, 0, \mixolydian), [0, -1], "C Mixolydian: the same centre, one flat more");
		this.assertEquals(RLRegions.interval(\major, 3, \minor), [3, 0], "the relative minor has the same tones");
		this.assertEquals(RLRegions.interval(\dorian, 1, \dorian), [1, 1], "the same mode a fifth up: one sharp more");
		this.assertEquals([RLRegions.steps(\major, 2, \dorian), RLRegions.steps(\major, 0, \mixolydian), RLRegions.steps(\major, 0, \dorian),
			RLRegions.steps(\major, 1, \major), RLRegions.steps(\major, 0, \minor), RLRegions.steps(\major, 0, \major)], [1, 1, 2, 1, 3, 0],
			"the steps between two regions");
		this.assertEquals(RLRegions.classBetween(\major, 1, \mixolydian), 1, "where the chart has no word, the steps");
		this.assertEquals(RLRegions.classBetween(\major, 0, \minor), 2, "between major and minor the chart: the parallel minor is indirect but close");
		this.assertEquals(RLRegions.classBetween(\major, 0, \minor, true), 3, "by the modes it is three tones away");
		this.assertEquals(RLRegions.classBetween(\major, 6, \dorian), 5, "five at the most");
		this.assertEquals(RLRegions.classBetween(\dorian, 0, \dorian), 0, "the same region");
		this.assertEquals(r.relation([0, \major], [2, \dorian]), 1, "D Dorian seen from C major");
		this.assertEquals(r.nameOf([2, \dorian]), nil, "the chart has no name for it");
		this.assertEquals(d.central, [0, \dorian], "regions round a Dorian centre");
		this.assertEquals(d.scale(d.central).degrees, [2, 4, 5, 7, 9, 11, 0], "its scale");
		this.assertEquals(edo.name(d.tonicOf([-2, \major])), "C", "C major from D Dorian, two fifths down");
		this.assertEquals(d.relation(d.central, [-2, \major]), 1, "one step away");
	}

	test_the_context_of_modes {
		var edo = RLEDO(12), r = RLRegions(edo, 0, \major), ctx = r.context(3), g = ctx.graph, near = r.regions(1, RLScale.modes), differ = 0;
		this.assertEquals(r.context(2).objects.size, 35, "five centres, seven modes");
		this.assertEquals(ctx.generators, [\R, \S, \T], "three relations");
		this.assert(ctx.act(\R, [0, \major]).includesEqual([1, \mixolydian]) and: { ctx.act(\R, [0, \major]).includesEqual([2, \dorian]) },
			"R: the same tones about another centre");
		this.assertEquals(ctx.act(\S, [0, \major]).size, 2, "S: one tone changed about the same centre, either way");
		this.assert(ctx.act(\S, [0, \major]).includesEqual([0, \mixolydian]) and: { ctx.act(\S, [0, \major]).includesEqual([0, \lydian]) }, "C Mixolydian and C Lydian");
		this.assert(ctx.act(\T, [0, \dorian]).includesEqual([1, \dorian]), "T: the same mode a fifth away");
		this.assert(ctx.isWalk([[0, \major], [1, \mixolydian], [1, \major]], [\R, \S]), "C major, G Mixolydian, G major is a walk: R then S");
		this.assertEquals(g.distance([0, \major], [0, \dorian]), 2, "C Dorian is two steps from C major");
		// away from the span's edge the graph's distances are the formula's
		near.do { |a| near.do { |b| if(g.distance(a, b) != RLRegions.steps(a[1], b[0] - a[0], b[1])) { differ = differ + 1 } } };
		this.assertEquals(differ, 0, "the graph's distances are *steps for the regions within a fifth");
	}
}
