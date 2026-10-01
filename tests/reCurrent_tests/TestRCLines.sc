// RCLines, RCSurface, RCProjection: the geometry of the sonic lines, without a session.
TestRCLines : UnitTest {
	var savedRateLimit;

	setUp {
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown { RCLog.rateLimit = savedRateLimit }

	near { |a, b, tol = 1e-6| ^(a - b).abs < tol }

	warnings { ^RCLog.history.select { |e| e[1] == \warn }.collect { |e| e[2] } }

	//////// lines

	test_between_time_order_and_chirp {
		var l = RCLines.between([4, (pitch: 1)], [2, (pitch: 0)]);
		var c = RCLines.between([1, (pitch: 0)], [1, (pitch: 2)], 0.05);
		this.assertEquals(l[\onset], 2, "the earlier point starts the line");
		this.assertEquals(l[\dur], 2, "its length is the time between the points");
		this.assertEquals(l[\from][\pitch], 0, "from the earlier point's params");
		this.assertEquals(l[\to][\pitch], 1, "to the later one's");
		this.assertEquals(c[\dur], 0.05, "a vertical line is stretched to minDur");
		this.assertEquals([c[\from][\pitch], c[\to][\pitch]], [0, 2], "its ends kept: a chirp");
		this.assert(RCLines.slope(l, \pitch) == 0.5, "slope: units per beat");
	}

	test_keysOf_and_paramKeys {
		var lines = [RCLines.line(0, 1, (pitch: 0), (pitch: 1)), RCLines.line(1, 1, (amp: 0, az: 1), (amp: -6, az: 2))];
		this.assertEquals(RCLines.keysOf(lines), [\amp, \az, \pitch], "the union of the keys, sorted");
		this.assertEquals(RCLines.paramKeys([\pitch]), [\pitch0, \pitch1, \sustain, \line_id], "per-hit param names");
	}

	//////// rails

	test_rails_hyperbolic_paraboloid {
		var a = RCLines.rail([0, (pitch: 0)], [0, (pitch: 2)]);       // at time 0, from pitch 0 to 2
		var b = RCLines.rail([8, (pitch: 2)], [8, (pitch: 0)]);       // at time 8, the other way
		var lines = RCLines.rails(a, b, 5);
		this.assertEquals(lines.size, 5, "five strings");
		this.assertEquals(lines.collect { |l| l[\from][\pitch] }, [0, 0.5, 1, 1.5, 2], "evenly spaced on the first rail");
		this.assertEquals(lines.collect { |l| l[\to][\pitch] }, [2, 1.5, 1, 0.5, 0], "and on the second");
		this.assert(lines.every { |l| l[\onset] == 0 and: { l[\dur] == 8 } }, "each line spans the rails' times");
		this.assertEquals(lines.collect(_[\id]), [0, 1, 2, 3, 4], "ids in rail order");
		// the crossing lines of a hyperbolic paraboloid: the middle string is flat, the outer ones cross it
		this.assertEquals(RCLines.slope(lines[2], \pitch), 0, "the middle string holds its pitch");
		this.assert(RCLines.slope(lines[0], \pitch) > 0 and: { RCLines.slope(lines[4], \pitch) < 0 }, "the outer strings glide in opposite directions");
	}

	test_rails_fan {
		var a = RCLines.rail([0, (pitch: 1)], [0, (pitch: 1)]);   // one point: the unison
		var b = RCLines.rail([4, (pitch: 0)], [4, (pitch: 2)]);
		var lines = RCLines.rails(a, b, 3);
		this.assert(lines.every { |l| l[\from][\pitch] == 1 }, "all from the unison");
		this.assertEquals(lines.collect { |l| l[\to][\pitch] }, [0, 1, 2], "fanning out");
		this.assertEquals(RCLines.rails(a, b, 3, us: [0, 0.25]).collect { |l| l[\to][\pitch] }, [0, 0.5], "given u values");
	}

	test_rails_backwards_in_time_and_sorted {
		var a = RCLines.rail([8, (pitch: 0)], [0, (pitch: 0)]);   // a rail crossing time
		var b = RCLines.rail([4, (pitch: 1)], [4, (pitch: 1)]);
		var lines = RCLines.rails(a, b, 3);
		this.assertEquals(lines.collect(_[\onset]), [0, 4, 4], "lines in time order, each from its earlier end");
		this.assertEquals(lines.collect(_[\dur]), [4, 4, 0.01], "the line at the rails' crossing is a chirp of minDur (a stable sort keeps it last)");
	}

	//////// tangents

	test_tangents_of_a_parabola_touch_it {
		var curve = RCLines.parabola(4, (pitch: 1), 0.125);   // pitch = 1 + (t - 4)^2 / 8
		var touches = [2, 4, 6];
		var lines = RCLines.tangents(curve, touches, [1, 1]);
		this.assertEquals(lines.size, 3, "one line per touch");
		lines.do { |l, i|
			var tau = touches[i];
			var point = curve.value(tau);
			var slope = RCLines.slope(l, \pitch);
			var pitchAtTouch = l[\from][\pitch] + (slope * (tau - l[\onset]));
			this.assert(this.near(slope, 2 * 0.125 * (tau - 4), 1e-6), "slope % is the derivative at the touch %".format(slope, tau));
			this.assert(this.near(pitchAtTouch, point[1][\pitch], 1e-6), "the line passes through the curve at the touch");
			this.assert(this.near(l[\onset], tau - 1) and: { this.near(l[\dur], 2) }, "one unit each side of the touch");
		};
		this.assertEquals(lines.collect(_[\id]), [0, 1, 2], "ids follow the touches");
	}

	test_tangents_extent_function_and_derivative {
		var curve = RCLines.parabola(0, (pitch: 0), 1);
		var lines = RCLines.tangents(curve, [1, 2], { |point, tangent, tau| [0, tau] }, { |tau| [1, (pitch: 2 * tau)] });
		this.assertEquals(lines.collect(_[\onset]), [1, 2], "from the touch on (before 0)");
		this.assertEquals(lines.collect(_[\dur]), [1, 2], "after = tau");
		this.assertEquals(lines.collect { |l| l[\to][\pitch] }, [1 + 2, 4 + 8], "along the given derivative");
		this.assertEquals(RCLines.tangents(curve, [1], 0).size, 0, "a zero extent gives no line");
	}

	test_parabola_multi_key {
		var curve = RCLines.parabola(2, (pitch: 1, az: 0), (pitch: 0.5, az: -1));
		var p = curve.value(3);
		this.assertEquals(p[0], 3, "time is the parameter");
		this.assertEquals(p[1][\pitch], 1.5, "pitch: apex + curvature");
		this.assertEquals(p[1][\az], -1, "az: its own curvature");
		this.assert(this.near(RCLines.tangentAt(curve, 3)[1][1][\pitch], 1, 1e-5), "the numerical derivative at 3 is 2 * 0.5 * 1");
	}

	//////// projected

	test_projection_identity_and_scales {
		var p = RCProjection(4, [\pitch, \az]);
		var r = p.project([2, 0.5, 1, 7]);
		this.assertEquals(r[0], 2, "time is x0");
		this.assertEquals(r[1][\pitch], 0.5, "pitch is x1");
		this.assertEquals(r[1][\az], 1, "az is x2, x3 unseen");
		p.scales = [4, 2, 1];
		p.translate([1, 0, 0, 0]);
		r = p.project([2, 0.5, 1, 7]);
		this.assertEquals(r[0], 4, "(x0 - 1) * 4");
		this.assertEquals(r[1][\pitch], 1, "scaled pitch");
		p.setScale(\az, 3);
		this.assertEquals(p.project([2, 0.5, 1, 7])[1][\az], 3, "a scale set by key");
	}

	test_projection_rotation_stays_orthonormal {
		var p = RCProjection(3, [\pitch]);
		var r;
		p.rotate(0, 2, 0.5pi);
		r = p.project([0, 0, 1]);
		this.assert(this.near(r[0], 1), "after a quarter turn of (x0, x2) the time axis reads x2");
		this.assert(this.near(p.project([1, 0, 0])[0], 0), "and no longer x0");
		p.spin = [[0, 1, 0.1], [1, 2, 0.07]];
		100.do { p.advance(1) };
		this.assertEquals(p.time, 100, "time counts the beats advanced");
		this.assert(p.axes.every { |row| this.near(row.squared.sum, 1, 1e-9) }, "unit axes after 200 rotations");
		this.assert(this.near((p.axes[0] * p.axes[1]).sum, 0, 1e-9), "still orthogonal");
		p.reset;
		this.assertEquals(p.axes, [[1.0, 0.0, 0.0], [0.0, 1.0, 0.0]], "reset returns to the frame as made");
		this.assertEquals(p.time, 0, "and its time to 0");
	}

	test_projection_orthonormalizes_given_axes {
		var p = RCProjection(3, [\pitch], axes: [[2, 0, 0], [1, 1, 0]]);
		this.assertEquals(p.axes[0], [1.0, 0.0, 0.0], "the time axis normalised");
		this.assert(p.axes[1].collect { |x| x.round(1e-9) } == [0.0, 1.0, 0.0], "the pitch axis made orthogonal to it");
		this.assertEquals(p.copy.axes, p.axes, "a copy carries the axes");
	}

	test_projected_rulings_of_a_hyperbolic_paraboloid {
		var s = RCSurface.hyperbolicParaboloid(1, [-1, 1], [-1, 1]);
		var p = RCProjection(3, [\pitch], scales: [4, 1]);   // time = 4 x, pitch = y; z unseen
		var lines = RCLines.projected(s, p, [[-0.5, 0], [0.5, 0]], \ruling, timeMode: \none);
		this.assertEquals(lines.size, 2, "one line per sample");
		this.assertEquals(lines[0][\onset], -2, "the ruling at u = -0.5 starts at time 4 * -0.5");
		this.assertEquals(lines[0][\dur], 0.01, "a ruling along y projects to no time: a chirp");
		this.assertEquals([lines[0][\from][\pitch], lines[0][\to][\pitch]], [-1, 1], "over the whole v range");
		// turn pitch towards z: the rulings of a hyperbolic paraboloid stay straight, their pitch slope is u
		p.rotate(1, 2, 0.5pi);
		lines = RCLines.projected(s, p, [[-0.5, 0], [0.5, 0]], \ruling, timeMode: \none);
		this.assert(this.near(lines[0][\from][\pitch], 0.5, 1e-9) and: { this.near(lines[0][\to][\pitch], -0.5, 1e-9) }, "pitch reads z = u v: from 0.5 to -0.5 at u = -0.5");
		this.assert(this.near(lines[1][\from][\pitch], -0.5, 1e-9) and: { this.near(lines[1][\to][\pitch], 0.5, 1e-9) }, "and the other way at u = 0.5");
	}

	test_projected_tangents_time_modes_and_window {
		var s = RCSurface.sphere(vRange: [-1, 1]);   // off the poles, where the u tangent vanishes
		var p = RCProjection(3, [\pitch, \az], scales: [8, 2, 1]);
		var samples = s.grid(4, 3);
		var window = RCLines.projected(s, p, samples, \u, 0.5, \window, 8);
		var wrapped = RCLines.projected(s, p, samples, \u, 0.5, \wrap, 8);
		var sequence = RCLines.projected(s, p, samples, 0.5pi, 0.5, \sequence, 8);
		var none = RCLines.projected(s, p, samples, \u, 0.5, \none);
		this.assertEquals(samples.size, 12, "4 azimuths (periodic: the end excluded) × 3 elevations");
		this.assertEquals(none.size, 12, "every sample a line");
		this.assert(window.every { |l| l[\onset] >= 0 and: { l[\onset] < 8 } } and: { window.size < none.size }, "the window keeps the lines starting inside the cycle (% of %)".format(window.size, none.size));
		this.assertEquals(wrapped.size, 12, "wrap keeps every line");
		this.assert(wrapped.every { |l| l[\onset] >= 0 and: { l[\onset] < 8 } }, "onsets wrapped into the cycle");
		this.assertEquals(sequence.collect(_[\onset]), 12.collect { |i| i * 8 / 12 }, "a constant step of onsets");
		this.assert(none.every { |l| l[\sample].notNil }, "each line remembers its sample");
		// a tangent along u at the poles has no length: no line there
		this.assertEquals(RCLines.projected(s, p, [[0, 0.5pi]], \u, 0.5, \none).size, 0, "a vanishing tangent gives no line");
		// the u tangent of the unit sphere has length 1 * cos(el): a segment of length 0.5 spans 0.5 of arc at the equator
		this.assert(this.near(RCLines.projected(s, p, [[0, 0]], \u, 0.5, \none)[0][\to][\pitch] - RCLines.projected(s, p, [[0, 0]], \u, 0.5, \none)[0][\from][\pitch], 2 * 0.5, 1e-6), "the segment has the asked length, scaled");
	}

	//////// surfaces

	test_surface_presets_and_derivatives {
		var s = RCSurface.torus(2, 1);
		var c = RCSurface.clifford;
		var num = RCSurface({ |u, v| [u, v, u * v] });
		this.assertEquals(s.dim, 3, "a torus lives in R^3");
		this.assertEquals(c.dim, 4, "the Clifford torus in R^4");
		this.assert(this.near(c.at(1, 2).squared.sum, 1, 1e-9), "on the unit 3-sphere");
		this.assert(s.du(0.3, 0.7).collect { |x, i| this.near(x, ((s.at(0.3 + 1e-4, 0.7) - s.at(0.3 - 1e-4, 0.7)) / 2e-4)[i], 1e-4) }.every { |b| b }, "the analytic du matches central differences");
		this.assert(num.dv(0.5, 0.5).collect { |x, i| this.near(x, [0, 1, 0.5][i], 1e-6) }.every { |b| b }, "numerical dv of a custom surface");
		this.assert(this.near(s.normal(0, 0).squared.sum, 1, 1e-9), "a unit normal in R^3");
		this.assertEquals(c.normal(0, 0), nil, "no normal in R^4");
		this.assert(s.uPeriodic and: { s.vPeriodic }, "the torus is periodic both ways");
	}

	test_surface_rulings_are_straight {
		var rails = RCSurface.rails({ |u| [u, 0, 0] }, { |u| [0, u, 1] });
		var dev = RCSurface.tangentDevelopable({ |u| [u, u * u, 0] }, 3, [0, 1], [-1, 1]);
		var mid = (rails.at(0.5, 0) + rails.at(0.5, 1)) / 2;
		this.assert(rails.at(0.5, 0.5).collect { |x, i| this.near(x, mid[i], 1e-9) }.every { |b| b }, "a rail surface's v-line is the segment between the rails");
		this.assert(dev.at(0.5, 1).collect { |x, i| this.near(x, ([0.5, 0.25, 0] + [1, 1, 0])[i], 1e-6) }.every { |b| b }, "the developable's v-line is the tangent of the curve");
		[RCSurface.conoid, RCSurface.cone, RCSurface.hyperboloid, RCSurface.hyperbolicParaboloid].do { |surf|
			var a = surf.at(1, -0.5), b = surf.at(1, 0.5), m = surf.at(1, 0);
			this.assert(m.collect { |x, i| this.near(x, ((a[i] + b[i]) / 2), 1e-9) }.every { |b| b }, "% is ruled along v".format(surf.name));
		};
	}

	test_surface_samples {
		var s = RCSurface.sphere;
		var g = s.grid(3, 2);
		var r1 = s.random(5, seed: 7), r2 = s.random(5, seed: 7), r3 = s.random(5, seed: 8);
		var w = s.walk(20, 0.5, 0.5, seed: 3);
		this.assertEquals(g.size, 6, "a 3 × 2 grid");
		this.assertEquals(g.collect(_[0]).asSet.size, 3, "three azimuths, the periodic end excluded");
		this.assertEquals(g.collect(_[1]).asSet.asArray.sort, [-0.5pi, 0.5pi], "elevations include both ends");
		this.assertEquals(r1, r2, "seeded samples repeat");
		this.assert(r1 != r3, "another seed differs");
		this.assert(r1.every { |uv| uv[0] >= 0 and: { uv[0] <= 2pi } and: { uv[1].abs <= 0.5pi } }, "within the ranges");
		this.assertEquals(w.size, 20, "a walk of 20 samples");
		this.assert(w.every { |uv| uv[0] >= 0 and: { uv[0] <= 2pi } and: { uv[1].abs <= 0.5pi } }, "wrapped in azimuth, clipped in elevation");
		this.assertEquals(w, s.walk(20, 0.5, 0.5, seed: 3), "seeded walk repeats");
	}

	//////// cloud

	test_cloud_seeded_and_gaussian {
		var lines = RCLines.cloud(2000, 8, (pitch: [-10, 10]), (pitch: 2), 1, seed: 11, clip: false);
		var again = RCLines.cloud(2000, 8, (pitch: [-10, 10]), (pitch: 2), 1, seed: 11, clip: false);
		var speeds = lines.collect { |l| RCLines.slope(l, \pitch) };
		var mean = speeds.sum / speeds.size;
		var sd = (speeds.collect { |v| (v - mean).squared }.sum / speeds.size).sqrt;
		var meanAbs = speeds.collect(_.abs).sum / speeds.size;
		var clipped = RCLines.cloud(50, 8, (pitch: [0, 1]), (pitch: 100), 1, seed: 2);
		this.assertEquals(lines.size, 2000, "n lines");
		this.assertEquals(lines.collect(_[\onset]), again.collect(_[\onset]), "seeded: the same cloud");
		this.assert(lines.every { |l| l[\onset] >= 0 and: { l[\onset] < 8 } }, "onsets in the cycle");
		this.assert(lines.every { |l| l[\dur] == 1 }, "the given length");
		this.assert(lines.collect(_[\onset]) == lines.collect(_[\onset]).sort, "in time order");
		this.assert(mean.abs < 0.15, "isotropy: mean speed near 0 (%)".format(mean));
		this.assert(this.near(sd, 2 / 2.sqrt, 0.1), "sd a / sqrt 2 (%)".format(sd));
		this.assert(this.near(meanAbs, 2 / pi.sqrt, 0.1), "mean |v| = a / sqrt pi (%)".format(meanAbs));
		this.assert(clipped.every { |l| l[\to][\pitch] >= 0 and: { l[\to][\pitch] <= 1 } }, "clipped ends stay in the range");
		this.assert(RCLines.cloud(10, 4, (pitch: [0, 1], amp: [-20, 0]), (pitch: 1), [0.5, 2], seed: 1).every { |l| l[\from][\amp] == l[\to][\amp] and: { l[\dur] >= 0.5 } and: { l[\dur] <= 2 } }, "a key without temperature is static; durs in [min, max]");
	}

	//////// allocation and subseqs

	test_allocate_modes {
		var lines = [[0, 2], [0.5, 2], [1, 2], [3, 1], [3.2, 1]].collect { |p, i| RCLines.line(p[0], p[1], (pitch: i), (pitch: i)) };
		var rr = RCLines.allocate(lines, 2, \round_robin);
		var free = RCLines.allocate(lines, 2, \free);
		var strict = RCLines.allocate(lines, 2, \strict);
		var big = RCLines.allocate(lines, 5, \strict);
		this.assertEquals(rr[\voices].collect(_.size), [3, 2], "round robin alternates");
		this.assertEquals(rr[\dropped], 0, "nothing dropped");
		this.assertEquals(free[\voices].collect { |v| v.collect { |l| l[\from][\pitch] } }, [[0, 2, 4], [1, 3]], "free: the third line overlaps on the voice free soonest, 3 goes to the voice free the longest, 4 to the one free");
		this.assertEquals(strict[\voices].collect { |v| v.collect { |l| l[\from][\pitch] } }, [[0, 3], [1, 4]], "strict: the third line is dropped, 3 goes to the voice free the longest");
		this.assertEquals(strict[\dropped], 1, "one dropped");
		this.assertEquals(big[\dropped], 0, "enough voices: nothing dropped");
		this.assert(big[\voices].every { |v| v.size <= 1 }, "each line on its own voice");
	}

	test_allocate_pinned {
		var free = [[0, 2], [0.5, 2], [1, 2]].collect { |p, i| RCLines.line(p[0], p[1], (pitch: i), (pitch: i)) };
		var thread = [[0, 1], [1, 1], [2, 1.5]].collect { |p, i| RCLines.line(p[0], p[1], (pitch: 10 + i), (pitch: 11 + i)).put(\voice, 4) };
		var alloc = RCLines.allocate(free ++ thread, 3, \strict);
		var pitches = alloc[\voices].collect { |v| v.collect { |l| l[\from][\pitch] } };
		this.assertEquals(pitches[1], [10, 11, 12], "the thread's lines sit on voice 4 mod 3 = 1, in order, whatever their onsets");
		this.assertEquals(pitches[0] ++ pitches[2], [0, 1], "the free lines avoid the busy pinned voice");
		this.assertEquals(alloc[\dropped], 1, "strict: the third free line finds the two other voices busy and is dropped, never a pinned one");
		alloc = RCLines.allocate(thread, 1, \round_robin);
		this.assertEquals(alloc[\voices][0].size, 3, "one voice: the thread fits whatever the mode");
	}

	test_subseq_of_a_voice {
		var lines = [RCLines.line(1, 2, (pitch: 0, amp: -6), (pitch: 1, amp: 0), \a), RCLines.line(2, 3, (pitch: 2), (pitch: 1), \b), RCLines.line(2.0001, 1, (pitch: 5), (pitch: 5))];
		var s = RCLines.subseq(lines, [\amp, \pitch]);
		this.assert(s.isKindOf(RCSubseq), "an RCSubseq");
		this.assertEquals(s.shift, 1, "shifted to the first onset");
		this.assertEquals(s.durs, [1, 3], "each hit lasts until the next line, the last its own length");
		this.assertEquals(s.params[\pitch0], [0, 2], "pitch from");
		this.assertEquals(s.params[\pitch1], [1, 1], "pitch to");
		this.assertEquals(s.params[\amp0], [-6, nil], "a key the second line lacks is nil there");
		this.assertEquals(s.params[\sustain], [2, 3], "sustain: the lines' lengths");
		this.assertEquals(s.params[\line_id], [\a, \b], "ids");
		this.assertEquals(s.mask, [true, true], "every hit plays");
		this.assert(this.warnings.any { |w| w.contains("1 line(s) starting within") }, "the line 0.0001 after the previous one was dropped and reported");
		this.assertEquals(RCLines.subseq([]), nil, "no lines, no subseq");
	}
}
