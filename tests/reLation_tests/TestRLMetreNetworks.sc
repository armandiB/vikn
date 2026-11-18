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

	// periods whose fractions have large terms, very different sizes, a sign: found by a review of Nov 18 2026
	test_periods_far_from_small_fractions {
		var time = RLTimePoints.new;
		this.assertFloatEquals(RLPulse.gcd(21.4749, 0.0001), 0.0001, "the gcd of 21.4749 and a ten-thousandth (their cross product passes 32 bits)", 1e-12);
		this.assertEquals(RLPulse(0.1271) & RLPulse(0.1313), RLPulse(166.8823), "the lcm of 0.1271 and 0.1313");
		this.assertFloatEquals(RLPulse.gcd(0.00004, 0.00001), 0.00001, "periods under a ten-thousandth", 1e-12);
		this.assert(RLNetwork(time, [[\a, 0], [\b, 0.0001], [\c, 21.475]], [[\a, \b, 0.0001], [\b, \c, 21.4749], [\a, \c, 0.0001]]).isValid,
			"a network with such periods is valid");
		this.assert((RLPulse(0.0001) <= RLPulse(2000)).not and: { RLPulse(2000) <= RLPulse(0.0001) }, "a short pulse is not included in a long one, the long one is in the short");
		this.assert((RLPulse(1001.0001) <= RLPulse(1001)).not, "1001.0001 is no whole multiple of 1001");
		this.assert(RLPulse(2000).relates(0, 0.0001).not and: { RLPulse(2000).relates(1, 4001) }, "two points a ten-thousandth apart are not 2000 apart");
		this.assertEquals([RLPulse(-1/2), RLPulse.lcm(-1/2, 1/3), RLPulse.gcd(1/2, -1/3)], [RLPulse(1/2), 1, 1/6], "the sign of a period is dropped");
		this.assertException({ RLPulse.skiHill(0, 1) }, Error, "no ski-hill from 0");
		this.assertException({ RLPulse.paths(-6, 1/2) }, Error, "nor from a negative period");
		this.assertException({ RLPulse.skiHill(0.0001, 2000) }, Error, "nor from a top below its bottom");
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
		this.assert([[0.5e-10 - 1e-13, 0.5e-10 + 1e-13], [0.25, 0.25 + 1e-13]].every { |pair|
			var f = RLTimeMap(1, pair[0]), g = RLTimeMap(1, pair[1]);
			(f == g) == (f.hash == g.hash)
		}, "two maps are equal exactly when their hashes are");
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
		['M.', '0/0', 'M1.2.3', '1/0', 'M', '1/2/3'].do { |bad|
			this.assertException({ time.arrow(bad) }, Error, "% is refused".format(bad));
		};
		this.assertEquals([time.arrow('M0'), time.arrow("3/2"), time.arrow('M0.25')], [RLPulse(0), RLPulse(1.5), RLPulse(1/4)], "M0, 3/2 and M0.25 are pulses");
	}

	// a metre network moved by a map of time that brings it back: networks of metre networks
	test_a_network_of_metre_networks {
		var flip = time.affine(-1, 11/2), hyper = RLRelationContext.networks(top, [[\flip, flip]]);
		var pair = RLNetwork(hyper, [[\a, top], [\b, top.transformedBy(flip)]], [[\a, \b, \flip], [\b, \a, \flip]]);
		this.assertEquals(hyper.objects.collect(_.objects), [[5/2, 3], [3, 5/2]], "the half bar and the same read backwards about its middle");
		this.assertEquals(hyper.category.size, 2, "the flip and the identity");
		this.assert(pair.isValid, "the two as one network: the flip there and back");
		this.assertException({ RLRelationContext.networks(top, [[\later, time.shift(1/12)]], 50) }, Error, "a shift never brings a network back: more networks than max");
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

	// one point alone (period 0), periods with large terms, a sign: found by a review of Nov 18 2026
	test_layers_at_the_edges {
		this.assertEquals(time.common([0, 1/2], [1/2, 0]), [0, 1/2], "one point and a layer it is on: that point");
		this.assertEquals(time.common([1/2, 0], [0, 1/2]), [0, 1/2], "the other way round");
		this.assertEquals(time.common([0, 1], [0, 1]), [0, 1], "one point twice");
		this.assertEquals(time.common([0, 1/3], [1/2, 0]), nil, "one point off the layer: nothing shared");
		this.assertEquals(time.dissonance([0, 1/2], 1/2), \consonance, "a point on a layer");
		this.assertEquals(time.dissonance([0, 1/3], 1/2), \displacement, "a point off it");
		this.assertArrayFloatEquals(time.common(0.1271, 0.1313), [166.8823, 0], "two layers that meet again after 1313 steps of the first", 1e-9);
		// 1271 k is 1 modulo 1313 for k = 719: the first layer's step 719 is the second's step 696
		this.assertArrayFloatEquals(time.common([0.1271, 0], [0.1313, 0.0001]), [166.8823, 0.1271 * 719], "and, a ten-thousandth apart, first at step 719", 1e-9);
		this.assert(RLPulse(0.1313).relates(0.0001, 0.1271 * 719), "that point is on the second layer");
		this.assertEquals([time.dissonance(-1, 1/2), time.common(-1/2, 1/3)], [\consonance, [1, 0]], "the sign of a period is dropped");
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

// The article itself (Popoff and Yust, "Meter networks: a categorical framework for metrical analysis",
// J. Math. Music 2020; the accepted manuscript, read Nov 18 2026): its propositions, its figures 2 and 5, the
// morphisms of its section 3.3, with its own time points (the second half of bar 7 of "Auf dem See" at 0:
// A''7 = 0, B''7 = C''7 = 1/12, A'''7 = C'''7 = 1/3, A8 = 1/2, B8 = C8 = 7/12).
TestRLMeterNetworksArticle : UnitTest {
	var time, periods;

	setUp {
		time = RLTimePoints.new;
		periods = [1, 1/2, 1/3, 1/4, 1/6, 1/12, 3/2, 5, 4];
	}

	// 2.5 inclusion, 3.2 composition, 3.3 idempotence, 3.6 intersection, 3.13 stretches: over every pair
	test_the_propositions {
		var twelfths = { |d| (d * 12).round.asInteger }, ok = [true, true, true, true];
		periods.do { |d1|
			periods.do { |d2|
				var a = RLPulse(d1), b = RLPulse(d2), whole = ((d1 / d2) - (d1 / d2).round).abs < 1e-9;
				if((a <= b) != whole) { ok[0] = false };
				if((b <> a) != RLPulse(twelfths.(d1).gcd(twelfths.(d2)) / 12)) { ok[1] = false };
				if((b & a) != RLPulse(twelfths.(d1).lcm(twelfths.(d2)) / 12)) { ok[2] = false };
				[1/2, 3, 5/4].do { |lambda| if((b.scaled(lambda) <> a.scaled(lambda)) != (b <> a).scaled(lambda)) { ok[3] = false } };
			};
		};
		this.assert(ok[0], "2.5: M(d1) is included in M(d2) exactly when d1 is a whole multiple of d2");
		this.assert(ok[1], "3.2: M(d2) after M(d1) is M(gcd)");
		this.assert(ok[2], "3.6: M(d2) and M(d1) meet in M(lcm)");
		this.assert(ok[3], "3.13: a stretch goes through the gcd");
		this.assert(periods.every { |d| (RLPulse(d) <> RLPulse(d)) == RLPulse(d) }, "3.3: every pulse is its own square");
		this.assert(periods.every { |d| ((RLPulse(d) <> RLPulse(0)) == RLPulse(d)) and: { RLPulse(0) <= RLPulse(d) } },
			"M0 is the identity; as a relation it is included in every pulse (2.5 as stated leaves it out)");
	}

	// section 2.2: the inclusion diagram of Brahms's op. 78, bar 235, the bar as the unit; section 3.1
	test_figure_2_the_ski_hill_of_op_78 {
		this.assertEquals(RLPulse.skiHill(1, 1/12), [1, 1/2, 1/3, 1/4, 1/6, 1/12], "the six relations of the diagram");
		this.assertEquals(RLPulse.paths(1, 1/12), [[1, 1/2, 1/4, 1/12], [1, 1/2, 1/6, 1/12], [1, 1/3, 1/6, 1/12]],
			"three paths from M1 to M1/12: by the right hand's small arpeggios (M1/4), by the left hand's quarters (M1/6), by the violin's slurs (M1/3)");
		this.assertEquals(RLPulse(1/3) <> RLPulse(1/2), RLPulse(1/6), "M1/3 after M1/2 is M1/6: where the violin and the left hand come together");
	}

	// figure 5 (a): one network with the beat and its division between the same two points
	test_figure_5_as_a_free_network {
		var fig = RLNetwork(time, [[\A2, 0], [\A3, 1/3], [\A8, 1/2], [\C2, 1/12], [\C3, 1/3], [\C8, 7/12]], [
			[\A2, \A3, 1/6], [\A3, \A8, 1/6], [\A2, \A8, 1/6], [\A2, \A8, 1/2],
			[\C2, \C3, 1/4], [\C3, \C8, 1/4], [\C2, \C8, 1/4], [\C2, \C8, 1/2],
			[\A2, \C2, 1/12], [\A3, \C3, 0], [\A8, \C8, 1/12]]);
		this.assert(fig.isValid.not, "read strictly it is not valid: M1/2 and M1/6 both go from A''7 to A8");
		this.assert(fig.free.isValid, "read as a free diagram it is: every relation fits its two time points");
		this.assertEquals(fig.free.ways(\A2, \A8), [RLPulse(1/6), RLPulse(1/2)], "two ways from A''7 to A8: the eighths and the beat");
		this.assert(fig.free.isFree and: { fig.isFree.not } and: { fig.free.strict == fig } and: { fig.free != fig }, "free and strict are two readings of one diagram");
		this.assert(fig.free.transformedBy(time.shift(1)).isFree, "a free network moved is free");
		this.assert(RLNetwork(time, [[\a, 0], [\b, 1/3]], [[\a, \b, 1/2]]).free.isValid.not, "a relation that misses its point is still seen");
		this.assertEquals([RLPulse(1/6) <> RLPulse(1/4), RLPulse(1/6) & RLPulse(1/4)], [RLPulse(1/12), RLPulse(1/2)],
			"the two metres share the sixteenth below them and the half bar above");
	}

	// section 3.3.1: the piano's half bar is the voice's a sixteenth later
	test_the_displacement {
		var voice = RLNetwork(time, [[\A2, 0], [\A8, 1/2]], [[\A2, \A8, 1/2]]);
		var piano = RLNetwork(time, [[\B2, 1/12], [\B8, 7/12]], [[\B2, \B8, 1/2]]), map = [[\A2, \B2], [\A8, \B8]];
		this.assert(voice.isMorphismTo(piano, map, time.shift(1/12)), "by the map of time t + 1/12");
		this.assert(voice.isMorphismTo(piano, map).not, "not without it");
		// 3.16 and the last diagram: the same recast with no map of time, M1/12 at each node
		this.assert(voice.isMorphismTo(piano, map, nil, 1/12), "or with no map of time, when M1/12 joins each point to its image");
		this.assert(voice.isMorphismTo(piano, map, nil, [[\A2, 1/12], [\A8, 'M1/12']]), "the links node by node");
		this.assert(voice.isMorphismTo(piano, map, nil, 1/5).not, "M1/5 does not join 0 to 1/12");
		this.assert([0, 1/3, 5/2].every { |t| RLPulse(1/12).relates(t, time.shift(1/12).value(t)) }, "3.16: a shift by u is included in M(u)");
	}

	// section 3.3.2: the half bar divided by A'''7, arrows both ways, M0 at each node
	test_the_inclusion {
		var beat = RLNetwork(time, [[\X, 0], [\Y, 1/2]], [[\X, \Y, 1/2], [\Y, \X, 1/2]]);
		var eighths = RLNetwork(time, [[\X, 0], [\Y, 1/3], [\Z, 1/2]], [[\X, \Y, 1/6], [\Y, \X, 1/6], [\Y, \Z, 1/6], [\Z, \Y, 1/6]]);
		var dotted = RLNetwork(time, [[\X, 1/12], [\Y, 1/3], [\Z, 7/12]], [[\X, \Y, 1/4], [\Y, \X, 1/4], [\Y, \Z, 1/4], [\Z, \Y, 1/4]]);
		this.assert(beat.isValid and: { eighths.isValid } and: { dotted.isValid }, "three valid networks");
		this.assert(beat.isMorphismTo(eighths, [[\Y, \Z]], nil, 0), "X to X', Y to Z', M0 at each node: M1/2 is included in M1/6");
		this.assert(beat.isMorphismTo(eighths, [[\Y, \Z]]), "the same with no link named");
		this.assert(eighths.isMorphismTo(beat, [[\Z, \Y]]).not, "and not the other way");
		this.assert(beat.isMorphismTo(dotted, [[\Y, \Z]], time.shift(1/12)), "the half bar a sixteenth later, divided in two dotted eighths");
		this.assert(RLNetwork(time, [[\P, 1/3]], []).isMorphismTo(eighths, [[\P, \Y]]) and: { RLNetwork(time, [[\P, 1/3]], []).isMorphismTo(dotted, [[\P, \Y]]) },
			"the weak beat A'''7 = C'''7 goes into both: the point the two metres share");
	}

	// section 2.4: Ligeti's sixth study, the sixteenth as the unit
	test_ligeti_s_layers {
		this.assertEquals([RLPulse(4) <> RLPulse(5), RLPulse(4) & RLPulse(5)], [RLPulse(1), RLPulse(20)], "M4 and M5 compose to M1 and meet in M20");
		this.assert((RLPulse(4) <= RLPulse(5)).not and: { (RLPulse(5) <= RLPulse(4)).not }, "neither is included in the other");
		this.assertEquals(time.dissonance([4, 0], [5, 0]), \grouping, "the ostinato against the upper line");
		this.assertEquals(time.dissonance([4, 0], [4, -1]), \displacement, "the ostinato against itself a sixteenth earlier");
		this.assertEquals(time.common([4, -1], [5, 0]), [20, 15], "that second ostinato and the upper line share a point every 20 sixteenths");
	}

	// links in another context: the local isographies of Webern's two networks (TestRLNetwork) are morphisms
	test_links_are_local_isographies {
		var pcs = RLRelationContext.pitchClasses, labels = [[\x, \y, \I8], [\y, \z, \I9], [\x, \z, \T1]];
		var w1 = RLNetwork(pcs, [[\x, 9], [\y, 11], [\z, 10]], labels), w2 = RLNetwork(pcs, [[\x, 1], [\y, 7], [\z, 2]], labels);
		this.assert(w1.isMorphismTo(w2, nil, nil, [[\x, \T4], [\y, \T8], [\z, \T4]]), "T4, T8, T4, node by node");
		this.assert(w1.isMorphismTo(w2, nil, nil, [[\x, \I10], [\y, \I6], [\z, \I0]]), "I10, I6, I0");
		this.assert(w1.isMorphismTo(w2, nil, nil, \T4).not, "T4 at every node is not one");
		this.assert(w1.isMorphismTo(w2).not, "nor the identity");
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
