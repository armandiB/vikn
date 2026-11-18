// reLation, metre on the line of time: RLPulse (Popoff and Yust's metrical relations), RLTimePoints (their
// context), RLTimeMap (its automorphisms), and networks carried into one another (RLNetwork:isMorphismTo).
// No server. The numbers are those of Popoff's post on metre networks (Transformational music theory 19) as
// HomewareSC's study notes have them: Brahms, "Auf dem See" op. 106 no. 2, bars 5 to 8, the bar as the unit
// (the time points are the notes' reading of the score), and Cohn's ski-hill graph of Brahms's op. 78.
TestRLPulse : UnitTest {

	test_the_monoid_of_pulses {
		var m = { |d| RLPulse(d) };
		this.assertEquals(m.(1/2) <> m.(1/3), m.(1/6), "composing two pulses gives the pulse both fit in: their gcd");
		this.assertEquals(m.(1/2) & m.(1/3), m.(1), "what both hold: their lcm");
		this.assertEquals(m.(1/2) <> m.(1/2), m.(1/2), "a pulse composed with itself is itself");
		this.assertEquals([m.(0) <> m.(1/4), m.(1/4) <> m.(0)], [m.(1/4), m.(1/4)], "M0 is the identity");
		this.assert((m.(1) <= m.(1/2)) and: { (m.(1/2) <= m.(1)).not }, "M1 is included in M1/2, and not the other way");
		this.assert((m.(1/2) <= m.(1/6)) and: { m.(1/2) <= m.(1/4) } and: { (m.(1/6) <= m.(1/4)).not }, "M1/2 in M1/6 and in M1/4; M1/6 not in M1/4");
		this.assert((m.(0) <= m.(1/2)) and: { (m.(1/2) <= m.(0)).not }, "the identity is included in every pulse");
		this.assertEquals([m.(1/6).name, m.(3).name, m.(0).name, m.(3/2).name], ["M1/6", "M3", "M0", "M3/2"], "names as fractions");
		this.assertEquals([RLPulse.gcd(3/2, 1), RLPulse.lcm(3/2, 1), RLPulse.gcd(0, 1/4), RLPulse.lcm(0, 1/4)], [0.5, 3.0, 0.25, 0.0], "gcd and lcm of periods");
		this.assertEquals(RLCategory([[\half, m.(1/2)], [\third, m.(1/3)]]).arrows.collect(_.name), ["M0", "M1/2", "M1/3", "M1/6"],
			"two pulses generate a monoid of four: every arrow its own square");
	}

	// the relations the post states between the time points of the song
	test_the_time_points_of_auf_dem_see {
		var m = { |d| RLPulse(d) }, a = 0, b = 1/2, c = 5/6, d = 1, g = 3, b1 = 7/12, d1 = 13/12, e1 = 31/12, e2 = 17/6;
		this.assert(m.(1/2).relates(b, d) and: { m.(1/2).relates(a, d) } and: { m.(1/2).relates(b, g) } and: { m.(1/2).relates(b1, d1) },
			"M1/2 relates B to D, A to D, B to G, and B' to D'");
		this.assert(m.(1/6).relates(b, c) and: { m.(1/4).relates(e1, e2) }, "M1/6 relates B to C, M1/4 E' to E''");
		this.assert(m.(1/6).relates(b, d) and: { m.(1/12).relates(b, d) }, "B and D are also related by M1/6 and M1/12");
		this.assert(m.(1/2).relates(b, c).not and: { m.(1/2).relates(b, b1).not }, "M1/2 relates neither B to C nor B to B'");
		this.assert(m.(0).relates(b, b) and: { m.(0).relates(b, d).not }, "M0 relates a point to itself alone");
		this.assertEquals(m.(1/4).points(31/12, 37/12 + 0.01, 31/12), [31/12, 17/6, 37/12].round(1e-10), "the layer of dotted eighths from E' to G'");
		this.assertEquals(m.(1/2).points(0, 2, 1/12), [1/12, 7/12, 13/12, 19/12].round(1e-10), "the layer of the piano's right hand in two bars");
	}

	// Cohn's ski-hill graph of Brahms's violin sonata op. 78: a bar of six quarters down to eighths
	test_the_ski_hill {
		this.assertEquals(RLPulse.skiHill(6, 1/2), [6, 3, 2, 1.5, 1, 0.5], "the six durations between the bar and the eighth");
		this.assertEquals(RLPulse.paths(6, 1/2), [[6, 3, 1.5, 0.5], [6, 3, 1, 0.5], [6, 2, 1, 0.5]],
			"three paths: the piano's right hand, its left hand, the violin");
		this.assertEquals(RLPulse.paths(6, 1), [[6, 3, 1], [6, 2, 1]], "six quarters as two beats of three or three beats of two: the hemiola");
		this.assertEquals(RLPulse.paths(4, 1/2).size, 1, "a bar that only halves has one path");
		this.assertEquals(RLPulse.paths(12, 1).size, 3, "twelve units by 2, 2 and 3: three orders");
		this.assert(RLPulse.paths(6, 1/2).every { |p| p.every { |d, i| (i == 0) or: { RLPulse(p[i - 1]) <= RLPulse(d) } } }, "down a path each pulse is included in the next");
		this.assertException({ RLPulse.paths(5, 2) }, Error, "a top that is no whole multiple of the bottom is refused");
	}

	test_maps_of_time_are_automorphisms {
		var maps = [RLTimeMap(1, 1/12), RLTimeMap(2, 0), RLTimeMap(-1, 3), RLTimeMap(3/2, 1/4)], ok = true;
		maps.do { |f|
			[1/2, 1/6, 1/4, 1].do { |d|
				[[0, 1/2], [1/2, 5/6], [5/2, 3], [0, 1/4]].do { |pair|
					if(RLPulse(d).relates(pair[0], pair[1]) != f.onArrow(RLPulse(d)).relates(f.value(pair[0]), f.value(pair[1]))) { ok = false };
				};
			};
		};
		this.assert(ok, "two points are related by M(d) exactly when their images are by M(|a| d)");
		this.assertEquals(RLTimeMap(1, 1/12).onArrow(RLPulse(1/2)), RLPulse(1/2), "a shift changes no pulse");
		this.assertEquals(RLTimeMap(2, 0).onArrow(RLPulse(1/2)), RLPulse(1), "a stretch by 2 doubles every period");
		this.assertEquals(RLTimeMap(2, 1) <> RLTimeMap(3, 1/2), RLTimeMap(6, 2), "one map after another");
		this.assert((RLTimeMap(2, 1) <> RLTimeMap(2, 1).inverse).isIdentity, "undone");
		this.assertException({ RLTimeMap(0, 1) }, Error, "a stretch by 0 is refused");
	}
}

TestRLMetreNetworks : UnitTest {
	var time, top, voice, piano, fine, one;

	setUp {
		time = RLTimePoints.new;
		top = RLNetwork(time, [[\E, 5/2], [\G, 3]], [[\E, \G, 1/2]]);
		voice = RLNetwork(time, [[\E, 5/2], [\F, 17/6], [\G, 3]], [[\E, \F, 1/6], [\F, \G, 1/6]]);
		piano = RLNetwork(time, [[\Ep, 31/12], [\Gp, 37/12]], [[\Ep, \Gp, 1/2]]);
		fine = RLNetwork(time, [[\Ep, 31/12], [\Epp, 17/6], [\Gp, 37/12]], [[\Ep, \Epp, 'M1/4'], [\Epp, \Gp, 'M1/4']]);
		one = RLNetwork(time, [[\F, 17/6]], []);
	}

	test_metre_networks {
		var triangle = { |across| RLNetwork(time, [[\a, 0], [\b, 1/2], [\c, 5/6]], [[\a, \b, 1/2], [\b, \c, 1/3], [\a, \c, across]]) };
		this.assert([top, voice, piano, fine, one].every(_.isValid), "the five networks of the post's last figure are valid");
		this.assertEquals(fine.labelNames, ["M1/4", "M1/4"], "labels by name, by period or as pulses");
		this.assertEquals(voice.labelBetween(\E, \G), RLPulse(1/6), "the way from E to G through F: M1/6");
		this.assertEquals(time.nameOf(voice.composite([\E, \F, \G])), "M1/6", "the composite of a route");
		this.assert(triangle.(1/6).isValid, "M1/2 then M1/3 with M1/6 across: a valid network");
		this.assert(triangle.(1/2).isValid.not, "with M1/2 across it is not");
		this.assert(RLNetwork(time, [[\a, 0], [\b, 1/3]], [[\a, \b, 1/2]]).isValid.not, "a pulse that misses its point is seen");
		this.assertException({ time.arrow(\Mx) }, Error, "a name that is no pulse is refused");
	}

	// the post's last figure: two inclusions (groupings), one shift (displacement), and the point both share
	test_the_morphisms_of_the_post {
		var later = time.shift(1/12), primes = [[\E, \Ep], [\G, \Gp]];
		this.assert(top.isMorphismTo(voice), "the half bar into three eighths: M1/2 is included in M1/6");
		this.assert(top.isMorphismTo(piano, primes, later), "the voice's half bar into the piano's: a sixteenth later, the pulse kept");
		this.assertEquals(top.morphismProblems(piano, primes).size, 2, "without the shift the two points do not fit");
		this.assert(piano.isMorphismTo(fine), "the piano's half bar into two dotted eighths: M1/2 is included in M1/4");
		this.assert(top.isMorphismTo(fine, primes, later), "and the two in a row");
		this.assert(one.isMorphismTo(voice) and: { one.isMorphismTo(fine, [[\F, \Epp]]) }, "one point in both: F is E''");
		this.assertEquals(voice.morphismProblems(fine, [[\E, \Ep], [\F, \Epp], [\G, \Gp]], later).size, 3,
			"the voice's eighths do not go into the piano's dotted eighths: one point and two pulses are in the way");
		this.assertEquals(voice.morphismProblems(top), ["F goes to F, which is not a node there"], "a node with nowhere to go is named");
		this.assertEquals(top.transformedBy(time.stretch(2)).labelNames, ["M1"], "a network stretched: its pulse twice as slow");
		this.assert(top.isMorphismTo(top.transformedBy(time.affine(2, 1)), nil, time.affine(2, 1)), "a network into its image under any map of time");
	}

	test_layers_and_their_dissonance {
		this.assertEquals(time.dissonance([1/2, 0], [1/2, 1/12]), \displacement, "voice and piano: the same pulse a sixteenth apart");
		this.assertEquals(time.dissonance([1/6, 5/2], [1/4, 31/12]), \grouping, "eighths against dotted eighths");
		this.assertEquals(time.dissonance(1/2, 1/6), \consonance, "the half bar and its eighths");
		this.assertEquals(time.dissonance([1, 0], [1/2, 1/4]), \displacement, "a bar against half bars that never fall on it");
		this.assertEquals(time.common([1/6, 5/2], [1/4, 31/12]), [1/2, (17/6).round(1e-10)], "the two layers meet every half bar, at F first");
		this.assertEquals(time.common([1/2, 0], [1/2, 1/12]), nil, "displaced layers share no point");
		this.assertEquals(time.common(1/2, 1/3), [1, 0], "three against two: once a bar");
		this.assertEquals(time.pulseOf([0, 1/2, 5/6, 1]), RLPulse(1/6), "the finest pulse that holds the voice's four points");
		this.assertEquals(time.layer([1/6, 5/2], 5/2, 3.01), [5/2, 8/3, 17/6, 3].round(1e-10), "a layer's points in a window");
	}

	// layers as rows of the canon page (its protocol: public/lib/canon-viz.js in HomewareVisuals)
	test_layers_on_the_canon_page {
		var got = Array.new, saved = RCVisuals.sink, spans, pulses;
		RCVisuals.sink = { |path, args| got = got.add([path] ++ args) };
		RLCanonPage.layers([[1/6, 5/2], [1/4, 31/12]], 5/2, 3.2, title: "eighths against dotted eighths");
		RCVisuals.sink = saved;
		spans = got.select { |m| m[0] == '/canon/span' };
		pulses = got.detect { |m| m[0] == '/canon/pulses' };
		this.assertEquals(got.select { |m| m[0] == '/canon/voice' }, [['/canon/voice', 0, "M1/6"], ['/canon/voice', 1, "M1/4"]], "a row for each layer, named by its pulse");
		this.assertEquals(spans.select { |m| m[1] == 0 }.size, 5, "five eighths from 5/2 to 3.2");
		this.assertArrayFloatEquals(spans.select { |m| m[1] == 1 }.collect { |m| m[2] }, [1/12, 1/3, 7/12], "the dotted eighths, counted from the start of the window", 1e-9);
		this.assertEquals(pulses[1], 8, "the line of pulses: eight");
		this.assertFloatEquals(pulses[2], 1/12, "of a twelfth of a bar: the finest pulse every point falls on", 1e-9);
	}
}

// Networks carried into one another in the other contexts: a progression filled in, and moved on the way.
TestRLNetworkMorphisms : UnitTest {

	test_relations_and_transformations {
		var cd = RLRelationContext.cubeDance, sp = cd.space, t5 = RLAutomorphism.transposition(cd, 5);
		var far = RLNetwork(cd, [[\x, sp.major(0)], [\y, sp.major(4)]], [[\x, \y, [\U, \U]]]);
		var filled = RLNetwork(cd, [[\x, sp.major(0)], [\m, sp.chord(0, \aug)], [\y, sp.major(4)]], [[\x, \m, \U], [\m, \y, \U]]);
		var wrong = RLNetwork(cd, [[\x, sp.major(0)], [\m, sp.chord(0, \aug)]], [[\x, \m, \P]]);
		var ctx = RLChordContext(RLChordSpace.triads(RLEDO(12))), c = ctx.space.major(0);
		var leap = RLNetwork(ctx, [[\a, c], [\c, ctx.space.major(7)]], [[\a, \c, ctx.r <> ctx.l]]);
		var steps = RLNetwork(ctx, [[\a, c], [\b, ctx.space.minor(4)], [\c, ctx.space.major(7)]], [[\a, \b, \L], [\b, \c, \R]]);
		this.assert(far.isValid and: { filled.isValid }, "C to E by two U, and the same through C augmented");
		this.assert(far.isMorphismTo(filled), "the leap goes into the progression that fills it");
		this.assert(far.isMorphismTo(filled.transformedBy(t5), nil, t5), "and into that progression a fourth up, moved on the way");
		this.assert(far.isMorphismTo(filled.transformedBy(t5)).not, "not without being moved");
		this.assertEquals(wrong.morphismProblems(filled).size, 1, "a label that is not included is named: P is not in U");
		this.assert(leap.isMorphismTo(steps), "transformations: R after L is the way from C to G through E minor");
		this.assert(RLNetwork(ctx, [[\a, c], [\c, ctx.space.major(7)]], [[\a, \c, ctx.t(7)]]).isMorphismTo(steps).not, "T7 is not: it takes C to G and is another label");
		this.assertEquals(filled.labelBetween(\x, \x), cd.identityTransform, "from a node to itself: the identity");
		this.assertEquals(filled.labelBetween(\y, \x), nil, "nothing joins y to x");
	}
}
