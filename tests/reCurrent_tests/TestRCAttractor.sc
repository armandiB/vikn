// RCAttractor: the sets, the warp's properties, the time mode, the event controls, the synth side.
TestRCAttractor : UnitTest {
	var savedRateLimit;

	setUp {
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown { RCLog.rateLimit = savedRateLimit }

	near { |a, b, tol = 1e-6| ^(a - b).abs < tol }

	//////// sets

	test_sets_from_scales_edos_sieves_values {
		var major = RCAttractor.scale(Scale.major);
		var edo = RCAttractor.edo(19);
		var diatonic = RCAttractor.sieve(RCSieve.residues(12, [0, 2, 4, 5, 7, 9, 11], 1/12));
		var book = RCAttractor.sieve(RCSieve([[[12, 11]], [[30, 3]]], 1/24));
		var vals = RCAttractor.values([0.7, 0.2, 0.7, -0.1]);
		this.assert(major.degrees.collect { |d| (d * 12).round(1e-9) } == [0, 2, 4, 5, 7, 9, 11] and: { major.period == 1 }, "a scale: its degrees in octaves, an octave's period");
		this.assert(edo.degrees.size == 19 and: { this.near(edo.degrees[1], 1/19) }, "19 equal divisions");
		this.assertEquals(diatonic.degrees.collect { |d| (d * 12).round(1e-9) }, [0, 2, 4, 5, 7, 9, 11], "a sieve: its points over one period (the white keys)");
		this.assert(this.near(book.period, 60 / 24) and: { book.degrees.every { |d| d >= 0 and: { d < book.period } } }, "a sieve's period is its formula's lcm times its unit (2.5 octaves, % points)".format(book.degrees.size));
		this.assert(vals.isPeriodic.not and: { vals.degrees == [-0.1, 0.2, 0.7] }, "values: sorted, each once, not repeating");
		this.assert(RCAttractor.from(\minor).notNil and: { RCAttractor.from(31).degrees.size == 31 } and: { RCAttractor.from([0, 1]).isPeriodic.not } and: { RCAttractor.from(major) === major }, "a spec's set from a Symbol, an Integer, an Array, an attractor");
		this.assert(RCAttractor.periodic([1.25, 0.5], 1).degrees == [0.25, 0.5], "degrees reduced into the period");
	}

	test_tuned_sieve {
		var just = RCAttractor.sieve(RCSieve.residues(12, [0, 4, 7], 1/12, tuning: Tuning.just));
		this.assertEquals(just.degrees.size, 3, "a tuned sieve: the steps it picks over the period");
		this.assert(this.near(just.degrees[2], (3/2).log2, 1e-9), "in its tuning (a just fifth)");
	}

	//////// the warp

	test_warp_properties {
		var a = RCAttractor.scale(Scale.major);
		var xs = (0..400).collect { |i| -0.5 + (i / 100) };
		var ws = xs.collect { |x| a.at(x, 0.6) };
		this.assert(xs.every { |x| a.at(x, 0) == x }, "amount 0: the identity");
		this.assert(ws.differentiate.drop(1).every { |d| d >= -1e-12 }, "monotone");
		this.assert(a.valuesIn(-0.5, 3.5).every { |v| this.near(a.at(v, 0.8), v, 1e-9) }, "every attractor fixed");
		this.assert(xs.every { |x| this.near(a.at(x + 1, 0.6), a.at(x, 0.6) + 1, 1e-9) }, "a period on, the same warp a period up");
		this.assert(xs.every { |x| var nb = a.neighbours(x), u = (x - nb[0]) / (nb[1] - nb[0]); (u - 0.5).abs < 0.1 or: { ((a.at(x, 1) - a.nearest(x)).abs / (nb[1] - nb[0])) < 0.02 } },
			"amount 1: steps, every value within 2 percent of its interval of the nearest attractor away from the switch");
	}

	test_warp_lingers {
		var a = RCAttractor.edo(12);
		// an evenly moving value: the fraction of its time within 10 cents of a semitone
		var near10 = { |amount| var n = 2001; (0..(n - 1)).count { |i| var w = a.at(i / (n - 1), amount); (w - a.nearest(w)).abs < (10 / 1200) } / n };
		var fr = [0, 0.3, 0.6, 0.9].collect(near10);
		this.assert(fr.differentiate.drop(1).every(_ > 0) and: { this.near(fr[0], 0.2, 0.01) } and: { fr[3] > 0.8 },
			"the time spent near the set grows with the amount (within 10 cents: %)".format(fr.collect(_.round(0.001))));
	}

	test_weights_move_the_switch {
		var a = RCAttractor.values([0, 1], [3, 1]);
		var even = RCAttractor.values([0, 1]);
		var dead = RCAttractor.values([0, 1], [0, 1]);
		this.assert(this.near(even.at(0.5, 0.7), 0.5, 1e-9), "equal weights: the switch halfway");
		this.assert(a.at(0.5, 0.7) < 0.2 and: { a.at(0.7, 0.7) < 0.5 }, "a heavier attractor holds longer (% at 0.5)".format(a.at(0.5, 0.7).round(0.001)));
		this.assert(dead.at(0.1, 0.9) > 0.5, "a weight of 0 never holds: the value leaves it at once");
		this.assert(RCAttractor.values([0, 1], [0, 0]).at(0.3, 0.9) == 0.3 and: { RCAttractor.mid(0, 0) == -1 } and: { RCAttractor.shape(0.3, 20, -1) == 0.3 }, "between two weights of 0: even");
	}

	test_outside_and_small_sets {
		var v = RCAttractor.values([0.2, 0.8]);
		var one = RCAttractor.periodic([0.25], 1);
		this.assert(v.at(0.1, 0.9) == 0.1 and: { v.at(0.95, 0.9) == 0.95 }, "outside a set that does not repeat: as it is");
		this.assert(this.near(one.at(1.24, 0.9), 1.25, 0.01) and: { one.neighbours(0.7) == [0.25, 1.25, 1, 1] }, "one value per period: its own neighbours a period apart");
		this.assert(RCAttractor.values([]).at(0.3, 1) == 0.3, "an empty set leaves values alone");
	}

	//////// the time mode

	test_progress_straight {
		var a = RCAttractor.edo(12);
		var us = (0..200) / 200;
		var ps = us.collect { |u| a.progressAt(u, 0, 1, 0.7) };
		var slopes = ps.differentiate.drop(1);
		this.assert(this.near(a.progressAt(0, 0, 1, 0.7), 0) and: { this.near(a.progressAt(1, 0, 1, 0.7), 1) }, "the ends kept");
		this.assert(slopes.every { |d| d >= -1e-12 }, "monotone");
		this.assert(slopes.minItem < (0.2 / 200) and: { slopes.maxItem > (2 / 200) }, "slow near the crossings, fast between");
		this.assert(a.progressAt(1.3, 0, 1, 0.7) == 1.3 and: { a.progressAt(0.4, 0.5, 0.5, 0.7) == 0.4 } and: { RCAttractor.values([5]).progressAt(0.3, 0, 1, 0.9) == 0.3 }, "past the line, on a line that does not move, or on one meeting no attractor: as it is");
	}

	test_progress_ends_off_the_set {
		// a line from 36 cents below C to 48 above C two octaves up, through C major: its ends kept, its
		// course lingering on the scale's degrees themselves (not on degrees shifted by the ends' offsets)
		var a = RCAttractor.scale(Scale.major), x0 = -0.03, x1 = 2.04;
		var course = { |u, amount| x0 + ((x1 - x0) * a.progressAt(u, x0, x1, amount)) };
		var near = { |amount, cents| var n = 4001; (0..(n - 1)).count { |i| var p = course.(i / (n - 1), amount); (p - a.nearest(p)).abs < (cents / 1200) } / n };
		var fr = [0, 0.5, 0.9].collect { |amt| near.(amt, 5) };
		var ends = a.straightEnds(x0, x1), down = a.straightEnds(x1, x0);
		this.assert(ends[0] == 0 and: { this.near(ends[1], 2) } and: { ends[2] == 0 } and: { ends[3] == 1 }, "its first and last degrees within (C and C two octaves up), the ends weighted 0");
		this.assert(this.near(down[0], 2) and: { down[1] == 0 }, "falling: from the top");
		this.assert(this.near(course.(1e-6, 0.9), x0, 0.001) and: { this.near(course.(1 - 1e-6, 0.9), x1, 0.001) }, "the ends kept (continuous there)");
		this.assert(fr.differentiate.drop(1).every(_ > 0.1) and: { fr[2] > 0.75 }, "within 5 cents of a degree for longer as the amount grows (%)".format(fr.collect(_.round(0.001))));
		this.assert(this.near(course.(0.02, 0.9), 0, 0.002), "leaving the start at once for C (a weight of 0 never holds)");
	}

	test_crossings_of_a_curved_course {
		var a = RCAttractor.edo(12, weights: (1 ! 6) ++ [2] ++ (1 ! 5));   // the tritone (0.5) heavier
		var env = Env([0.05, 0.6, 0.3], [0.5, 0.5], [2, -2]);
		var cr = a.crossings(env);
		var inner = cr.drop(1).drop(-1);
		this.assert(cr.first == [0, 0] and: { cr.last == [1, 0] }, "from the start to the end, weighted 0");
		this.assert(inner.every { |c| this.near(a.nearest(env.at(c[0])), env.at(c[0]), 1e-6) }, "each crossing on an attractor (% of them)".format(inner.size));
		this.assert(inner.size == (7 + 4), "up through 7 semitones, back down through 4 (% found)".format(inner.size));
		this.assert(inner.count { |c| c[1] == 2 } == 2 and: { inner.every { |c| (c[1] == 1) or: { c[1] == 2 } } }, "each with its attractor's weight (the tritone, crossed twice)");
		RCAttractor.maxCrossings = 6;
		cr = a.crossings(env);
		this.assert(cr.size == 6 and: { cr.last == [1, 0] }, "capped, the end kept");
		RCAttractor.maxCrossings = 64;
	}

	//////// template and synth sides

	test_event_controls {
		var major = RCAttractor.scale(Scale.major);
		var ev = (pitch0: 0, pitch1: 1, az0: 0, az1: 1);
		var v = RCAttractor.eventControls(ev, (pitch: (set: major, amount: 0.5)), [\pitch, \az]);
		var ev2 = (pitch0: 0, pitch1: 1, az0: 0, az1: 1), v2 = RCAttractor.eventControls(ev2, (az: (set: [0, 0.5], amount: 0.5), pitch: (set: major, amount: 0.4, mode: \time)), [\pitch, \az]);
		var ev3 = (pitch0: 0, pitch1: 0.3, path: (pitch: Env([0, 0.6, 0.3], [0.5, 0.5]))), v3 = RCAttractor.eventControls(ev3, (pitch: (set: 12, amount: 0.5, mode: \time)), [\pitch]);
		var ev4 = (pitch0: 0.2), v4 = RCAttractor.eventControls(ev4, nil, [\pitch]);
		var dense = RCAttractor.edo(48), ev5 = (pitch0: 0.1, pitch1: 0.3), v5 = RCAttractor.eventControls(ev5, (pitch: (set: dense, amount: 0.5)), [\pitch]);
		this.assert(v == \A and: { ev[\pitch_astr] > 0 } and: { ev[\pitch_aset][0].size == (RCAttractor.maxDegrees + 2) } and: { ev[\pitch_an] == 9 } and: { ev[\pitch_aperiod] == 1 }, "pitch warped: its set with a period each side (9 values), variant A");
		this.assert(ev[\az_astr] == 0 and: { ev[\progress_astr] == 0 }, "the other key and the time mode off");
		this.assert(v2 == \AA and: { ev2[\pitch_astr] == 0 } and: { ev2[\progress_astr] > 0 } and: { ev2[\progress_x1] == 1 } and: { ev2[\progress_alo] == 0 } and: { ev2[\progress_ahi] == 1 } and: { ev2[\progress_mlo] == 0 } and: { ev2[\progress_mhi] == 1 }, "az warped (AA), pitch driving the time mode straight: its ends, its first and last degrees, its end segments' switches");
		this.assert(v3 == \A and: { ev3[\progress_x0] == 0 } and: { ev3[\progress_x1] == 1 } and: { ev3[\progress_alo] == 0 } and: { ev3[\progress_ahi] == 1 } and: { ev3[\progress_aperiod] == 0 } and: { ev3[\progress_an] > 2 }, "a curved driving course: its crossings over [0, 1], its ends among them");
		this.assert(RCAttractor.eventControls((pitch0: 0.1, pitch1: 0.15), (pitch: (set: [0, 1], amount: 0.5, mode: \time)), [\pitch]).isNil, "a course meeting no attractor in the time mode: nothing to warp, no variant");
		this.assert(v4.isNil and: { ev4[\pitch_astr] == 0 } and: { ev4[\progress_astr] == 0 }, "no spec: every strength 0, no variant");
		this.assert(v5 == \A and: { ev5[\pitch_aperiod] == 0 } and: { ev5[\pitch_an] <= (RCAttractor.maxDegrees + 2) }, "a set too dense for the buffer: cut to the line's reach, not repeating");
	}

	test_resolve {
		var major = RCAttractor.scale(Scale.major);
		var r = RCAttractor.resolve((pitch: (set: major, amount: 0.4, mode: \both), az: (set: [0, 1], amount: 0.6, mode: \time), amp: (set: \minor, amount: 0)), [\pitch, \amp, \az]);
		var r2 = RCAttractor.resolve((pitch: (set: major, amount: 0.4, mode: \time), az: (set: [0, 1], amount: 0.6, mode: \time), time_key: \az), [\pitch, \az]);
		var r3 = RCAttractor.resolve((amp: (set: [0, -12], amount: 0.5, mode: \time), az: (set: [0, 1], amount: 0.6, mode: \time)), [\amp, \az]);
		var r4 = RCAttractor.resolve((pitch: (set: [0, 1], amount: 0.5, weights: [2, 1])), [\pitch]);
		this.assert(r[0].size == 1 and: { r[0][0][0] == \pitch } and: { r[0][0][1] === major } and: { r[0][0][2] == 0.4 }, "the keys warped: pitch (both), not az (time) nor amp (amount 0)");
		this.assert(r[1][0] == \pitch, "the time mode to pitch (time_key's default), az asking too");
		this.assert(r2[1][0] == \az and: { r2[0].isEmpty }, "time_key names the driving key");
		this.assert(r3[1][0] == \amp, "without time_key among them: the first key asking");
		this.assert(r4[0][0][1].weights == [2, 1], "weights applied to a copy of the set");
		this.assert(RCAttractor.resolve(nil, [\pitch]) == [[], nil] and: { RCAttractor.resolve((pitch: (set: \noSuchScale, amount: 0.5)), [\pitch]) == [[], nil] }, "no spec, or no set: nothing");
	}

	test_synth_side_declares_its_controls {
		var def = SynthDef(\test_rc_attractor, {
			var x = Line.kr(0, 2, 1);
			Out.kr(0, [RCAttractor.kr(\pitch, x), RCAttractor.progress(Line.kr(0, 1, 1))]);
		});
		var names = def.allControlNames.collect(_.name);
		this.assert(names.includesAll([\pitch_astr, \pitch_aset, \pitch_aweights, \pitch_an, \pitch_aperiod, \pitch_aroot, \progress_astr, \progress_aset, \progress_x0, \progress_x1, \progress_alo, \progress_ahi, \progress_mlo, \progress_mhi]), "the warp and the time warp declare their controls");
		this.assert(def.children.count { |u| u.isKindOf(LocalBuf) } == 4 and: { def.children.count { |u| u.isKindOf(SetBuf) } == 4 } and: { def.children.any { |u| u.isKindOf(IndexInBetween) } }, "each a LocalBuf of values and one of weights, filled at the start, searched by IndexInBetween");
	}
}
