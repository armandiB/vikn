// RCSieve, RCLaws, RCMarkov: the formal tools of the texture rigs, against the examples of
// Formalized Music (page numbers of the Pendragon 2nd edition). No session, no server.
TestRCSieve : UnitTest {

	test_diatonic_residues {
		var s = RCSieve.residues(12, [0, 2, 4, 5, 7, 9, 11], 1/12, 0, \diatonic);
		this.assertEquals(s.integers(0, 11), [0, 2, 4, 5, 7, 9, 11], "the white keys of one octave (p. 269)");
		this.assert(s.includes(14) and: { s.includes(13).not }, "and of every octave");
		this.assertEquals(s.points(0, 0.99).collect { |x| (x * 12).round.asInteger }, [0, 2, 4, 5, 7, 9, 11], "the points in octaves");
		this.assertEquals(s.period, 12, "the octave is the period");
		this.assertEquals(s.name, \diatonic, "named");
	}

	test_intersections_of_the_book {
		this.assertEquals(RCSieve.intersect([3, 2], [4, 7]), [12, 11], "(3,2)∩(4,7) = (12,11) (p. 274)");
		this.assertEquals(RCSieve.intersect([6, 9], [15, 18]), [30, 3], "(6,9)∩(15,18) = (30,3)");
		this.assertEquals(RCSieve.intersect([6, 3], [8, 4]), nil, "classes that never meet");
		this.assertEquals(RCSieve.intersect([6, 3], [8, 3]), [24, 3], "(6,3)∩(8,3) = (24,3) (p. 272)");
		this.assertEquals(RCSieve.simplify([[[3, 2], [4, 7]], [[6, 9], [15, 18]]]), [[[12, 11]], [[30, 3]]], "the book's sieve reduced (p. 278)");
		this.assertEquals(RCSieve.periodOf([[[24, 23]], [[30, 3]], [[104, 70]]]), 1560, "the period is the lcm (p. 275)");
	}

	test_book_sieve_points_and_pairs {
		var s = RCSieve([[[3, 2], [4, 7]], [[6, 9], [15, 18]]], 1/24, 0, \book);
		this.assertEquals(s.pairs, [[12, 11], [30, 3]], "its pairs");
		this.assertEquals(s.period, 60, "its period");
		this.assertEquals(s.integers(0, 100), [3, 11, 23, 33, 35, 47, 59, 63, 71, 83, 93, 95], "its points");
		this.assertEquals(s.simplified, RCSieve([[[12, 11]], [[30, 3]]], 1/24), "the simplified sieve is the same value");
		this.assert(s == RCSieve([[[3, 2], [4, 7]], [[6, 9], [15, 18]]], 1/24), "structural equality");
		this.assert(s != RCSieve([[[3, 2], [4, 7]], [[6, 9], [15, 18]]], 1/12), "a different unit is another sieve");
	}

	test_metabolae_of_page_275 {
		var p = RCSieve([[[5, 4]], [[3, 2]], [[7, 3]]]);   // the book prints (5,1), but its series and metabolae are those of (5,4)
		var up = p.metabola(7), changed = p.metabola([3, 1, -6]);
		this.assertEquals(p.integers(0, 31), [2, 3, 4, 5, 8, 9, 10, 11, 14, 17, 19, 20, 23, 24, 26, 29, 31], "(5,4) ∪ (3,2) ∪ (7,3): the series H");
		this.assertEquals(up.formula, [[[5, 1]], [[3, 0]], [[7, 3]]], "+7 on every index, reduced (p. 276)");
		this.assertEquals(up.integers(0, 30), [0, 1, 3, 6, 9, 10, 11, 12, 15, 16, 17, 18, 21, 24, 26, 27, 30], "the series H'");
		this.assertEquals(up.integers(7, 112), p.integers(0, 105) + 7, "a transposition: the whole series moved by 7");
		this.assertEquals(changed.formula, [[[5, 2]], [[3, 0]], [[7, 4]]], "+3, +1, -6: the intervallic structure changes");
		this.assertEquals(changed.integers(0, 32), [0, 2, 3, 4, 6, 7, 9, 11, 12, 15, 17, 18, 21, 22, 24, 25, 27, 30, 32], "its series");
		this.assertEquals(changed.period, 105, "the period stays");
		this.assertEquals(p.metabola(0, 1/24).unit, 1/24, "the unit metabola");
	}

	test_from_points_reproduces_the_set {
		var white = [0, 2, 4, 5, 7, 9, 11, 12, 14, 16, 17, 19, 21, 23];
		var s = RCSieve.fromPoints(white, 24);
		var rhythm = RCSieve.fromPoints([0, 3, 6, 8, 11, 14, 16, 19, 22], 24);
		this.assertEquals(s.integers(0, 23), white, "two octaves of white keys come back");
		this.assert(s.formula.every { |inter| inter[0][0] <= 12 }, "with moduli up to 12");
		this.assertEquals(rhythm.pairs, [[8, 0], [8, 3], [8, 6]], "the 3, 3, 2 rhythm is three residues of 8");
		this.assertEquals(RCSieve.fromPoints([]).formula, [], "nothing from nothing");
	}

	test_time_sieves {
		var clave = RCSieve.residues(8, [0, 3, 6], 1/2);
		var grid = RCSieve(RCSieve.residuesFormula(12, [0]) ++ RCSieve.residuesFormula(15, [0]) ++ RCSieve.residuesFormula(20, [0]), 4/60);
		this.assertEquals(clave.points(0, 4 - 1e-6), [0, 1.5, 3.0], "3, 3, 2 in eighths: onsets at 0, 1.5, 3 beats");
		this.assertEquals(grid.points(0, 4 - 1e-6).size, 10, "Analogique's 5 + 4 + 3 grid: ten distinct positions in the cycle");
	}
}

TestRCLaws : UnitTest {

	near { |a, b, tol = 1e-6| ^(a - b).abs < tol }

	test_second_law_mean {
		var mean = RCUtil.seeded(1, { 6000.collect { RCLaws.value(\linear, nil, 0, 3) }.mean });
		this.assert(this.near(mean, 1, 0.06), "x = a (1 - sqrt(1 - u)) has mean a / 3 (% for a = 3)".format(mean.round(0.001)));
		this.assert(RCUtil.seeded(2, { 2000.collect { RCLaws.value(\linear, nil, 2, 5) } }).every { |v| v >= 2 and: { v <= 5 } }, "inside its range");
	}

	test_exponential_mean_and_clip {
		var mean = RCUtil.seeded(2, { 6000.collect { RCLaws.exponential(0.5, 0, inf) }.mean });
		this.assert(this.near(mean, 0.5, 0.03), "mean 1 / c (%)".format(mean.round(0.001)));
		this.assert(RCUtil.seeded(3, { 500.collect { RCLaws.exponential(0.5, 0.05, 0.4) } }).every { |v| v >= 0.05 and: { v <= 0.4 } }, "clipped");
	}

	test_arcsine_favours_the_edges {
		var values = RCUtil.seeded(3, { 2000.collect { RCLaws.value(\arcsine, nil, -1, 1) } });
		this.assert(values.every { |v| v >= -1 and: { v <= 1 } }, "in range");
		this.assert(values.count { |v| v.abs > 0.7 } > values.count { |v| v.abs < 0.3 }, "the edges likelier than the middle");
	}

	test_every_law_stays_in_range {
		RCLaws.valueLaws.do { |law|
			var values = RCUtil.seeded(4, { 500.collect { RCLaws.value(law, nil, -2, 3) } });
			this.assert(values.every { |v| v >= -2 and: { v <= 3 } }, "% stays in [-2, 3]".format(law));
		};
	}

	test_reflect {
		this.assert(this.near(RCLaws.reflect(1.2, 0, 1), 0.8), "1.2 → 0.8");
		this.assert(this.near(RCLaws.reflect(-0.3, 0, 1), 0.3), "-0.3 → 0.3");
		this.assert(this.near(RCLaws.reflect(2.5, 0, 1), 0.5), "2.5 → 0.5 (twice reflected)");
		this.assertEquals(RCLaws.reflect(0.4, 0, 1), 0.4, "inside: untouched");
		this.assertEquals(RCLaws.reflect(3, 1, 1), 1, "a point range");
	}

	test_poisson {
		var mean = RCUtil.seeded(4, { 4000.collect { RCLaws.poisson(3.2) }.mean });
		this.assert(this.near(mean, 3.2, 0.15), "the mean (% for λ 3.2)".format(mean.round(0.01)));
		this.assert(RCUtil.seeded(5, { 200.collect { RCLaws.poisson(0) } }).every(_ == 0), "none for λ 0");
		this.assertEquals(RCLaws.poissonTable(0.6, 196), [107, 65, 19, 4, 1, 0, 0], "Achorripsis' table (p. 29)");
		this.assertEquals(RCLaws.poissonTable(0.6, 196).sum, 196, "every cell counted");
	}

	test_steps {
		var cauchy = RCUtil.seeded(6, { 3000.collect { RCLaws.step(\cauchy, nil, 1) } });
		var gauss = RCUtil.seeded(7, { 3000.collect { RCLaws.step(\gauss, nil, 1) } });
		this.assert(cauchy.count { |v| v.abs < 0.5 } > (cauchy.size * 0.6), "Cauchy: mostly small");
		this.assert(cauchy.any { |v| v.abs > 2 } and: { cauchy.every { |v| v.abs <= 4 } }, "sometimes a leap, clipped at four steps");
		this.assert(this.near(gauss.mean, 0, 0.05), "Gauss steps are centred");
		RCLaws.stepLaws.do { |law| this.assert(RCUtil.seeded(8, { RCLaws.step(law, nil, 1) }).isNumber, "% is a number".format(law)) };
	}

	test_entropy_bits {
		this.assertEquals(RCLaws.entropyBits([0.5, 0.5, 0.5, 0.5], 1/16), 0, "one value: 0 bits");
		this.assert(this.near(RCLaws.entropyBits([0, 1, 2, 3], 1), 2), "four equally frequent values: 2 bits");
		this.assertEquals(RCLaws.entropyBits([], 1), 0, "nothing: 0");
	}

	test_seeded {
		var a = RCUtil.seeded(1994, { 10.collect { RCLaws.value(\gauss) } });
		var b = RCUtil.seeded(1994, { 10.collect { RCLaws.value(\gauss) } });
		var before = thisThread.randData, inner;
		this.assertEquals(a, b, "the same seed gives the same draws");
		inner = RCUtil.seeded(7, { 1.0.rand });
		this.assertEquals(thisThread.randData, before, "a seeded draw leaves the caller's state alone");
		this.assertEquals(RCUtil.seeded(nil, { 3 }), 3, "no seed: the function as it is");
	}
}

TestRCMarkov : UnitTest {
	var savedRateLimit;

	setUp {
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown { RCLog.rateLimit = savedRateLimit }

	near { |a, b, tol = 1e-6| ^(a - b).abs < tol }

	test_stationary_and_entropy_of_the_book {
		var beta = [[0.85, 0.15], [0.4, 0.6]], alpha = [[0.2, 0.8], [0.8, 0.2]];
		var st = RCMarkov.stationary(beta);
		this.assert(this.near(st[0], 0.727, 0.002) and: { this.near(st[1], 0.273, 0.002) }, "(β)'s stationary distribution is 0.73 / 0.27 (p. 85)");
		this.assert(this.near(RCMarkov.entropy(beta), 0.708, 0.003), "(β): 0.707 bits (p. 86)");
		this.assert(this.near(RCMarkov.entropy(alpha), 0.722, 0.001), "(α): 0.722 bits (p. 87)");
		this.assertEquals(RCMarkov.stationary(alpha).collect(_.round(0.001)), [0.5, 0.5], "a symmetric matrix: half and half");
		this.assert(RCMarkov.isStochastic(beta) and: { RCMarkov.isStochastic([[0.5, 0.6]]).not }, "rows sum to one");
		this.assertEquals(RCMarkov.normalize([[2, 2], [0, 0]]), [[0.5, 0.5], [0.5, 0.5]], "normalised, an empty row uniform");
		this.assertEquals(RCMarkov.uniform(3)[1], [1/3, 1/3, 1/3], "the uniform matrix");
	}

	test_from_protocol {
		var est = RCMarkov.fromProtocol("ABABBBABAABABABABBBBABAABABBAABABBABAAABABBAABBABBA", [\A, \B]);
		this.assertEquals(est.counts, [[6, 17], [17, 10]], "A → A 6, A → B 17, B → A 17, B → B 10 (p. 73-74)");
		this.assert(this.near(est.mtp[0][1], 17 / 23) and: { this.near(est.mtp[1][0], 17 / 27) }, "the probabilities");
		this.assertEquals(RCMarkov.fromProtocol([\b, \a, \b]).states, [\a, \b], "symbols, sorted states");
		this.assertEquals(RCMarkov.fromProtocol("A").mtp, [[1]], "a state never left: a uniform row");
	}

	test_next_visits_by_the_stationary_distribution {
		var beta = [[0.85, 0.15], [0.4, 0.6]];
		var counts = 0 ! 2;
		RCUtil.seeded(7, { var s = 0; 3000.do { s = RCMarkov.next(beta, s); counts[s] = counts[s] + 1 } });
		this.assert(this.near(counts[0] / 3000, 0.727, 0.03), "state 0 about 73 percent of the time (%)".format((counts[0] / 3000).round(0.001)));
		this.assertEquals(RCMarkov.next([[0, 1], [1, 0]], 0), 1, "a sure transition");
	}

	test_coupled_step_analogique {
		var alpha = [[0.2, 0.8], [0.8, 0.2]], beta = [[0.85, 0.15], [0.4, 0.6]];
		var vars = [
			(key: \f, state: 0, mtps: (alpha: alpha, beta: beta), couplings: (d: [\alpha, \beta], g: [\beta, \alpha])),
			(key: \g, state: 0, mtps: (alpha: alpha, beta: beta), couplings: (f: [\alpha, \beta], d: [\alpha, \beta])),
			(key: \d, state: 0, mtps: (alpha: alpha, beta: beta), couplings: (f: [\alpha, \beta], g: [\alpha, \beta]))
		];
		var states = List.new, used = List.new;
		RCUtil.seeded(9, { 60.do { used.add(RCMarkov.coupledStep(vars)); states.add(vars.collect(_[\state])) } });
		this.assert(states.every { |st| st.every { |x| x == 0 or: { x == 1 } } }, "two states each");
		this.assert(states.asArray.flop.every { |v| v.asSet.size == 2 }, "every variable visits both");
		this.assert(used.every { |u| u.every { |m| [\alpha, \beta].includes(m) } }, "every step draws from a proposed matrix");
		this.assert(used.flatten.asSet.size == 2, "both matrices get proposed");
		this.assertEquals(vars.collect(_[\mtp_used]), used.last, "mtp_used holds the last matrix of each");
		this.assertEquals(RCLog.history.select { |e| e[1] == \error }, [], "no error");
	}

	test_coupled_step_without_couplings {
		var vars = [(key: \a, state: 0, mtps: (only: [[0, 1], [1, 0]])), (key: \b, state: 1, mtps: (x: [[1, 0], [1, 0]], y: [[0, 1], [0, 1]]), mtp: \y)];
		RCMarkov.coupledStep(vars);
		this.assertEquals(vars[0][\state], 1, "a single matrix is used as it is");
		this.assertEquals(vars[1][\state], 1, "mtp names the matrix: y keeps state 1");
		RCMarkov.coupledStep([(key: \z, state: 0, mtps: ())]);
		this.assert(RCLog.history.any { |e| e[1] == \error and: { e[2].asString.contains("no matrix") } }, "a missing matrix is reported, the state kept");
	}
}
