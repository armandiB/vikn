// RCSurface's curves (second partials, geodesics, parameter lines, the steepest ascent of a time
// function) and RCProjection's time function.
TestRCSurface : UnitTest {

	near { |a, b, tol = 1e-6| ^(a - b).abs < tol }

	dist { |a, b| ^(a - b).squared.sum.sqrt }

	test_second_partials_of_the_presets {
		var points = [[0.3, 0.7], [1.9, -0.4], [4.0, 0.2]];
		[RCSurface.plane, RCSurface.hyperbolicParaboloid(0.7), RCSurface.conoid, RCSurface.cone, RCSurface.cylinder,
			RCSurface.hyperboloid, RCSurface.sphere, RCSurface.torus(2, 0.6), RCSurface.clifford,
			RCSurface.rails({ |u| [u, 0, u.squared] }, { |u| [0, u, 1] }, via: { |u| [0.5, 0.5, 2 * u] })].do { |s|
			points.do { |uv|
				var u = uv[0], v = uv[1], e = 1e-4;
				var duu = (s.du(u + e, v) - s.du(u - e, v)) / (2 * e);
				var duv = (s.du(u, v + e) - s.du(u, v - e)) / (2 * e);
				var dvv = (s.dv(u, v + e) - s.dv(u, v - e)) / (2 * e);
				this.assert([[s.duu(u, v), duu], [s.duv(u, v), duv], [s.dvv(u, v), dvv]].every { |pair| this.dist(pair[0], pair[1]) < 1e-5 },
					"% at %: the second partials match differences of the first".format(s.name, uv));
			};
		};
	}

	test_projection_time_function {
		var p = RCProjection(3, [\pitch], origin: [1, 0, 0], scales: [4, 2]);
		this.assertEquals(p.timeOf([3, 5, 7]), 8, "the time of a point: (x0 - 1) * 4");
		this.assertEquals(p.timeOf([3, 5, 7]), p.project([3, 5, 7])[0], "project's time");
		this.assertEquals(p.timeGradient, [4.0, 0.0, 0.0], "its gradient: the time axis times its scale");
	}

	//////// curves

	test_geodesics_on_the_plane_are_straight {
		var s = RCSurface.plane([-2, 2], [-2, 2]);
		var g = s.geodesic(0, 0, 0.3, 1.5, 12);
		this.assertEquals(g.size, 13, "12 steps, the start included");
		this.assert(g.every { |uv, j| this.dist(uv, [0.3.cos, 0.3.sin] * (1.5 * j / 12)) < 1e-9 }, "a straight line at the angle, at unit speed");
		g = s.geodesic(0, 0, 0.3, -1.5, 12);
		this.assert(this.dist(g.last, [0.3.cos, 0.3.sin] * -1.5) < 1e-9, "a negative length goes the other way");
		g = RCSurface.plane.geodesic(0.5, 0, 0, 2, 20);   // the patch ends at u = 1
		this.assert(g.last[0] <= (1 + 1e-9) and: { g.last[0] > 0.85 } and: { g.size < 21 }, "it stops at the edge of a patch that is not periodic (last u %)".format(g.last[0]));
		this.assertEquals(RCSurface.sphere.geodesic(0, 0.5pi, 0, 1), nil, "none where its direction vanishes (along u at a pole)");
	}

	test_geodesics_on_the_sphere_are_great_circles {
		var s = RCSurface.sphere;
		var g = s.geodesic(0, 0, 0.25pi, pi, 48);   // from (1, 0, 0) heading north-east, half a turn
		var pts = g.collect { |uv| s.at(uv[0], uv[1]) };
		var t = [0, 0.5.sqrt, 0.5.sqrt], n = [0, 0.5.sqrt.neg, 0.5.sqrt];   // its unit tangent at the start, the normal of its plane
		this.assertEquals(g.size, 49, "the whole half turn (it never reaches a pole)");
		this.assert(pts.every { |x| this.near((x * n).sum, 0, 1e-6) }, "every point in a plane through the centre: a great circle");
		this.assert(pts.every { |x, j| this.dist(x, ([1, 0, 0] * (pi * j / 48).cos) + (t * (pi * j / 48).sin)) < 1e-5 }, "at unit speed along it");
		this.assert(this.dist(pts.last, [-1, 0, 0]) < 1e-5, "half a turn on: the antipode");
		this.assert(g.collect(_[1]).maxItem.inRange(0.25pi - 1e-4, 0.25pi + 1e-4), "climbing to 45 degrees, no further");
	}

	test_geodesics_along_rulings_are_the_rulings {
		var s = RCSurface.hyperbolicParaboloid(1, [-2, 2], [-2, 2]);
		var g = s.geodesic(0.3, -0.5, 0.5pi, 1.2, 16);   // along v: the ruling u = 0.3
		var pts = g.collect { |uv| s.at(uv[0], uv[1]) };
		var dir = s.dv(0.3, -0.5) / s.dv(0.3, -0.5).squared.sum.sqrt;
		var diag = s.geodesic(0.3, -0.5, 0.25pi, 1.2, 16).collect { |uv| s.at(uv[0], uv[1]) };
		this.assert(g.every { |uv| this.near(uv[0], 0.3, 1e-12) }, "it keeps its u: the ruling");
		this.assert(pts.every { |x, j| this.dist(x, s.at(0.3, -0.5) + (dir * (1.2 * j / 16))) < 1e-9 }, "a straight line of R^3, at unit speed");
		this.assert(this.dist(diag[8], (diag.first + diag.last) / 2) > 1e-3, "across the rulings a geodesic bends in R^3");
	}

	test_parameter_lines {
		var s = RCSurface.torus(2, 1);
		var l = s.paramLine(0, 0, \u, 1.5pi, 24);   // the outer equator (radius 3): a quarter of it is 1.5 pi long
		this.assert(this.near(l.last[0], 0.5pi, 1e-6) and: { l.every { |uv| uv[1] == 0 } }, "along u by arc length: a quarter of the outer equator");
		l = s.paramLine(0, 0, \v, -0.5pi, 8);   // the tube's circle (radius 1), backwards
		this.assert(this.near(l.last[1], -0.5pi, 1e-6) and: { l.every { |uv| uv[0] == 0 } }, "along v, backwards");
	}

	test_flow_climbs_one_unit_of_time_per_unit_to_the_top {
		var s = RCSurface.torus(2, 1);
		var frame = RCProjection(3, [\pitch]);   // time = x: the top of the torus (3, 0, 0), its bottom (-3, 0, 0)
		var strand = s.flow(0.5pi, 0.3, frame, 10, 40);   // from x = 0, as long as it climbs
		var times = strand.collect(_[0]);
		this.assert(this.near(times[0], 0, 1e-9), "it starts at its point's time");
		this.assert(times.differentiate.drop(1).drop(-2).every { |d| this.near(d, 0.25, 1e-3) }, "each step a quarter of a unit later (unit time)");
		this.assert(times.last > 2.4 and: { times.last <= 3.0 } and: { strand.size < 41 }, "it stops below the top, 3 (last time %)".format(times.last));
		this.assert(this.dist(s.at(strand.last[1], strand.last[2]), [3, 0, 0]) < 1.3, "near the top, where the strands converge");
		this.assertEquals(strand, s.flow(0.5pi, 0.3, frame, 10, 40), "the same strand again");
		this.assertEquals(s.flow(0, 0, frame, 1, 4).size, 1, "none from the top itself");
	}

	test_flow_at_unit_speed_and_descending {
		var s = RCSurface.sphere;
		var frame = RCProjection(3, [\pitch], axes: [[0, 0, 1], [1, 0, 0]], scales: [4, 1]);   // time = 4 z
		var up = s.flow(1, 0, frame, 1, 20, \length);   // up a meridian from the equator
		var down = s.flow(1, 0, frame, -1, 20, \length);
		this.assertEquals(up.size, 21, "the whole length");
		this.assert(up.every { |q| this.near(q[1], 1, 1e-9) }, "up a meridian (its azimuth kept)");
		this.assert(up.every { |q, j| this.near(q[2], j / 20, 1e-6) }, "at unit speed: its elevation is its arc length");
		this.assert(up.every { |q| this.near(q[0], 4 * q[2].sin, 1e-9) }, "each sample with its time, 4 z");
		this.assert(down.every { |q, j| this.near(q[2], j.neg / 20, 1e-6) }, "a negative length descends");
		this.assert(this.near(s.flow(1, 0, { |x| 4 * x[2] }, 1, 20, \length).last[2], 1, 1e-5), "a time Function: the same strand");
	}
}
