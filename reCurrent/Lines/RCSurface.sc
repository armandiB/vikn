// reCurrent — a parametric surface in R^m, sampled for its tangent lines.
//
//   ~s = RCSurface.hyperbolicParaboloid;              // [u, v, u * v]: doubly ruled
//   ~s.at(0.5, 0.2);                                  // a point of R^3
//   ~s.tangent(0.5, 0.2, 0.5pi);                      // the v tangent there
//   ~s.grid(4, 6);                                    // 24 samples [u, v]
//   RCLines.projected(~s, RCProjection(3, [\pitch]), ~s.random(12, seed: 1), \ruling, cycle: 8);
//
// A surface is a Function (u, v) → Array of dim numbers over uRange × vRange; the
// partial derivatives come from du / dv Functions when given, else from central
// differences (eps). The presets are the surfaces Xenakis drew for Metastasis and the
// Philips Pavilion (ruled: the fan of a cone, the conoid, the hyperbolic paraboloid,
// the hyperboloid of one sheet), the surface ruled by two rails, the surface of the
// tangents of a curve, and closed surfaces for projected textures (sphere, torus, the
// Clifford torus in R^4). Rulings run along v: the \ruling direction of
// RCLines.projected reads the v-line of a sample.

RCSurface {
	var <func, <dim, <uRange, <vRange, <>duFunc, <>dvFunc, <>eps = 1e-4, <>name;
	var <>uPeriodic = false, <>vPeriodic = false;

	*new { |func, dim = 3, uRange = #[0, 1], vRange = #[0, 1], du, dv, name|
		^super.newCopyArgs(func, dim, uRange, vRange, du, dv).initRCSurface(name)
	}

	initRCSurface { |namearg| name = namearg }

	//////// evaluation

	at { |u, v| ^func.value(u, v) }

	du { |u, v|
		^duFunc !? (_.value(u, v)) ?? { (this.at(u + eps, v) - this.at(u - eps, v)) / (2 * eps) }
	}

	dv { |u, v|
		^dvFunc !? (_.value(u, v)) ?? { (this.at(u, v + eps) - this.at(u, v - eps)) / (2 * eps) }
	}

	// A tangent vector at (u, v): cos(angle) * du + sin(angle) * dv (0: along u, pi/2: along v).
	tangent { |u, v, angle = 0|
		^(this.du(u, v) * angle.cos) + (this.dv(u, v) * angle.sin)
	}

	// The unit normal (dim 3 only: the cross product), nil elsewhere.
	normal { |u, v|
		var a, b, n, len;
		if(dim != 3) { ^nil };
		a = this.du(u, v);
		b = this.dv(u, v);
		n = [(a[1] * b[2]) - (a[2] * b[1]), (a[2] * b[0]) - (a[0] * b[2]), (a[0] * b[1]) - (a[1] * b[0])];
		len = n.squared.sum.sqrt;
		^if(len < 1e-12) { nil } { n / len }
	}

	//////// samples: Arrays of [u, v]

	// nu × nv samples on a grid over the ranges (the ends included, unless periodic), u varying slowest.
	grid { |nu = 4, nv = 4|
		var us = this.prSpan(uRange, nu, uPeriodic), vs = this.prSpan(vRange, nv, vPeriodic);
		^us.collect { |u| vs.collect { |v| [u, v] } }.flatten(1)
	}

	prSpan { |range, n, periodic|
		var lo = range[0], hi = range[1];
		if(n <= 1) { ^[lo] };
		^if(periodic) { n.collect { |i| lo + ((hi - lo) * i / n) } } { n.collect { |i| lo + ((hi - lo) * i / (n - 1)) } }
	}

	// n samples uniform over the ranges, seeded.
	random { |n = 16, seed|
		^RCUtil.seeded(seed, { n.collect { [rrand(uRange[0], uRange[1]), rrand(vRange[0], vRange[1])] } })
	}

	// A random walk of n samples from `start` ([u, v], the ranges' middle by default), steps
	// uniform in [-stepU, stepU] × [-stepV, stepV], wrapped over a periodic range, clipped
	// otherwise; seeded.
	walk { |n = 16, stepU = 0.1, stepV = 0.1, start, seed|
		var u = start !? (_[0]) ?? { (uRange[0] + uRange[1]) / 2 };
		var v = start !? (_[1]) ?? { (vRange[0] + vRange[1]) / 2 };
		^RCUtil.seeded(seed, {
			n.collect {
				var sample = [u, v];
				u = this.prFold(u + stepU.rand2, uRange, uPeriodic);
				v = this.prFold(v + stepV.rand2, vRange, vPeriodic);
				sample
			}
		})
	}

	prFold { |x, range, periodic|
		^if(periodic) { x.wrap(range[0], range[1]) } { x.clip(range[0], range[1]) }
	}

	//////// presets (rulings along v where the surface is ruled)

	// The plane z = 0: [u, v, 0].
	*plane { |uRange = #[-1, 1], vRange = #[-1, 1]|
		^this.new({ |u, v| [u, v, 0] }, 3, uRange, vRange, { |u, v| [1, 0, 0] }, { |u, v| [0, 1, 0] }, \plane)
	}

	// The hyperbolic paraboloid z = u * v / c, doubly ruled (u-lines and v-lines are straight):
	// the string section of Metastasis, the Philips Pavilion's walls.
	*hyperbolicParaboloid { |c = 1, uRange = #[-1, 1], vRange = #[-1, 1]|
		^this.new({ |u, v| [u, v, u * v / c] }, 3, uRange, vRange, { |u, v| [1, 0, v / c] }, { |u, v| [0, 1, u / c] }, \hyperbolicParaboloid)
	}

	// The right conoid (a helicoid) [v cos u, v sin u, c u]: rulings through the axis, rising with u.
	*conoid { |c = 0.2, uRange, vRange = #[-1, 1]|
		uRange = uRange ?? { [0, 2pi] };
		^this.new({ |u, v| [v * u.cos, v * u.sin, c * u] }, 3, uRange, vRange,
			{ |u, v| [v * u.sin.neg, v * u.cos, c] }, { |u, v| [u.cos, u.sin, 0] }, \conoid).uPeriodic_(true)
	}

	// The cone [v cos u, v sin u, v]: every ruling through the apex, the fan of the opening of Metastasis.
	*cone { |uRange, vRange = #[0, 1]|
		uRange = uRange ?? { [0, 2pi] };
		^this.new({ |u, v| [v * u.cos, v * u.sin, v] }, 3, uRange, vRange,
			{ |u, v| [v * u.sin.neg, v * u.cos, 0] }, { |u, v| [u.cos, u.sin, 1] }, \cone).uPeriodic_(true)
	}

	// The cylinder [cos u, sin u, v].
	*cylinder { |uRange, vRange = #[-1, 1]|
		uRange = uRange ?? { [0, 2pi] };
		^this.new({ |u, v| [u.cos, u.sin, v] }, 3, uRange, vRange,
			{ |u, v| [u.sin.neg, u.cos, 0] }, { |u, v| [0, 0, 1] }, \cylinder).uPeriodic_(true)
	}

	// The hyperboloid of one sheet [cos u - v sin u, sin u + v cos u, v]: doubly ruled, the
	// v-lines are straight (the rulings of the cooling tower).
	*hyperboloid { |uRange, vRange = #[-1, 1]|
		uRange = uRange ?? { [0, 2pi] };
		^this.new({ |u, v| [u.cos - (v * u.sin), u.sin + (v * u.cos), v] }, 3, uRange, vRange,
			{ |u, v| [u.sin.neg - (v * u.cos), u.cos - (v * u.sin), 0] }, { |u, v| [u.sin.neg, u.cos, 1] }, \hyperboloid).uPeriodic_(true)
	}

	// The unit sphere, u the azimuth, v the elevation.
	*sphere { |uRange, vRange|
		uRange = uRange ?? { [0, 2pi] };
		vRange = vRange ?? { [-0.5pi, 0.5pi] };
		^this.new({ |u, v| [u.cos * v.cos, u.sin * v.cos, v.sin] }, 3, uRange, vRange,
			{ |u, v| [u.sin.neg * v.cos, u.cos * v.cos, 0] }, { |u, v| [u.cos * v.sin.neg, u.sin * v.sin.neg, v.cos] }, \sphere).uPeriodic_(true)
	}

	// The torus of radii bigR (the ring) and r (the tube).
	*torus { |bigR = 2, r = 1, uRange, vRange|
		uRange = uRange ?? { [0, 2pi] };
		vRange = vRange ?? { [0, 2pi] };
		^this.new({ |u, v| var w = bigR + (r * v.cos); [w * u.cos, w * u.sin, r * v.sin] }, 3, uRange, vRange,
			{ |u, v| var w = bigR + (r * v.cos); [w * u.sin.neg, w * u.cos, 0] },
			{ |u, v| [r * v.sin.neg * u.cos, r * v.sin.neg * u.sin, r * v.cos] }, \torus).uPeriodic_(true).vPeriodic_(true)
	}

	// The Clifford torus in R^4, [cos u, sin u, cos v, sin v] / sqrt 2: flat, and every
	// rotation of R^4 shows it differently to a projection.
	*clifford { |uRange, vRange|
		var k = 2.sqrt.reciprocal;
		uRange = uRange ?? { [0, 2pi] };
		vRange = vRange ?? { [0, 2pi] };
		^this.new({ |u, v| [u.cos, u.sin, v.cos, v.sin] * k }, 4, uRange, vRange,
			{ |u, v| [u.sin.neg, u.cos, 0, 0] * k }, { |u, v| [0, 0, v.sin.neg, v.cos] * k }, \clifford).uPeriodic_(true).vPeriodic_(true)
	}

	// The surface ruled by two rails: S(u, v) = A(u) + v (B(u) - A(u)), a and b Functions
	// u → Array of dim numbers; the v-lines are the strings between the rails.
	*rails { |a, b, dim = 3, uRange = #[0, 1], vRange = #[0, 1]|
		^this.new({ |u, v| var pa = a.value(u), pb = b.value(u); pa + ((pb - pa) * v) }, dim, uRange, vRange,
			nil, { |u, v| b.value(u) - a.value(u) }, \rails)
	}

	// The tangent developable of a curve C (a Function u → Array of dim numbers):
	// S(u, v) = C(u) + v C'(u), ruled by the tangents of the curve (Xenakeur's construction
	// as a surface: its rulings are the tangent lines).
	*tangentDevelopable { |curve, dim = 3, uRange = #[0, 1], vRange = #[-1, 1], eps = 1e-4|
		var deriv = { |u| (curve.value(u + eps) - curve.value(u - eps)) / (2 * eps) };
		^this.new({ |u, v| curve.value(u) + (deriv.value(u) * v) }, dim, uRange, vRange, nil, { |u, v| deriv.value(u) }, \tangentDevelopable)
	}

	printOn { |stream| stream << "RCSurface(" << (name ? "custom") << ", R^" << dim << ")" }
}
