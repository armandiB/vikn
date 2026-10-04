// reCurrent — a parametric surface in R^m, sampled for its tangent lines and the curves on it.
//
//   ~s = RCSurface.hyperbolicParaboloid;              // [u, v, u * v]: doubly ruled
//   ~s.at(0.5, 0.2);                                  // a point of R^3
//   ~s.tangent(0.5, 0.2, 0.5pi);                      // the v tangent there
//   ~s.grid(4, 6);                                    // 24 samples [u, v]
//   RCLines.projected(~s, RCProjection(3, [\pitch]), ~s.random(12, seed: 1), \ruling, cycle: 8);
//   RCSurface.torus.geodesic(0, 0.5, 0.3pi, 4);                       // a geodesic 4 long in R^3: samples [u, v]
//   RCSurface.torus.flow(0.5pi, 0, RCProjection(3, [\pitch]), 2);     // climbing the frame's time: [time, u, v]
//   RCSurface.torus.levelSet({ |u, v| u.cos * v.cos }, 0.3);          // polylines of [u, v]
//
// A surface is a Function (u, v) → Array of dim numbers over uRange × vRange; the
// partial derivatives come from du / dv Functions when given, else from central
// differences (eps), the second partials from duu / duv / dvv Functions (secondPartials:
// the presets have them, the curves on a surface read them at every step) or differences
// of the first. The presets are the surfaces Xenakis drew for Metastasis and the
// Philips Pavilion (ruled: the fan of a cone, the conoid, the hyperbolic paraboloid,
// the hyperboloid of one sheet), the surface ruled by two rails, the surface of the
// tangents of a curve, and closed surfaces for projected textures (sphere, torus, the
// Clifford torus in R^4). Rulings run along v: the \ruling direction of
// RCLines.projected reads the v-line of a sample.
// Curves on the surface (Runge-Kutta 4 in the parameters; their equations written in R^m with
// the metric of the first partials, so any dimension): geodesics (the straightest curves: a
// ruling, a great circle), parameter lines, and the steepest ascent of a time function (flow:
// the strands of a section, their time growing one unit per unit). A function on the patch
// has level sets (marching squares) and critical points (where strands are born, part and
// converge).

RCSurface {
	var <func, <dim, <uRange, <vRange, <>duFunc, <>dvFunc, <>eps = 1e-4, <>name;
	var <>uPeriodic = false, <>vPeriodic = false;
	var <>duuFunc, <>duvFunc, <>dvvFunc;

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

	// The second partials: from the duu / duv / dvv Functions, else central differences of the first.
	duu { |u, v| ^duuFunc !? (_.value(u, v)) ?? { (this.du(u + eps, v) - this.du(u - eps, v)) / (2 * eps) } }
	duv { |u, v| ^duvFunc !? (_.value(u, v)) ?? { (this.du(u, v + eps) - this.du(u, v - eps)) / (2 * eps) } }
	dvv { |u, v| ^dvvFunc !? (_.value(u, v)) ?? { (this.dv(u, v + eps) - this.dv(u, v - eps)) / (2 * eps) } }

	// Give the second partials as Functions (u, v) → Array of dim numbers (nil: differences); returns
	// the surface.
	secondPartials { |duu, duv, dvv| duuFunc = duu; duvFunc = duv; dvvFunc = dvv }

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

	// The first fundamental form at (u, v): [E, F, G], the dot products of the partials.
	metric { |u, v|
		var su = this.du(u, v), sv = this.dv(u, v);
		^[(su * su).sum, (su * sv).sum, (sv * sv).sum]
	}

	// Whether (u, v) lies on the patch (a periodic coordinate always does).
	contains { |u, v, tol = 1e-9|
		^(uPeriodic or: { (u >= (uRange.minItem - tol)) and: { u <= (uRange.maxItem + tol) } })
			and: { vPeriodic or: { (v >= (vRange.minItem - tol)) and: { v <= (vRange.maxItem + tol) } } }
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

	// The spacing of prSpan's nodes.
	prStep { |range, n, periodic|
		var cells = if(periodic) { n } { (n - 1).max(1) };
		^(range[1] - range[0]) / cells
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

	//////// curves on the surface: samples [u, v] (unwrapped across a periodic seam), Runge-Kutta 4

	// The parameter acceleration [u'', v''] of a geodesic through (u, v) at the parameter velocity
	// (up, vp): its acceleration in R^m, S_uu u'² + 2 S_uv u'v' + S_vv v'² + S_u u'' + S_v v'', has no
	// part along the surface. nil where the partials are dependent (a pole, an apex).
	geodesicAccel { |u, v, up, vp|
		var su = this.du(u, v), sv = this.dv(u, v);
		var a = (this.duu(u, v) * up.squared) + (this.duv(u, v) * (2 * up * vp)) + (this.dvv(u, v) * vp.squared);
		^this.prSolve(su, sv, (su * a).sum.neg, (sv * a).sum.neg)
	}

	// The geodesic leaving (u, v) at `angle` (along cos angle S_u + sin angle S_v, as tangent), `length`
	// long in R^m (negative: the other way), in `steps` steps. It stops at the edge of a patch that is
	// not periodic and at a singular point. Returns its samples, nil where the direction vanishes.
	geodesic { |u, v, angle = 0, length = 1, steps = 16|
		var t = this.tangent(u, v, angle), norm = t.squared.sum.sqrt, sign = if(length < 0) { -1 } { 1 };
		var f = { |s| this.geodesicAccel(s[0], s[1], s[2], s[3]) !? { |acc| [s[2], s[3], acc[0], acc[1]] } };
		if(norm < 1e-9 or: { steps < 1 }) { ^nil };
		^this.prIntegrate([u, v, sign * angle.cos / norm, sign * angle.sin / norm], f, length.abs / steps, steps,
			{ |next| this.contains(next[0], next[1]) }).collect { |s| [s[0], s[1]] }
	}

	// The parameter line through (u, v), along u (which \u) or v (\v), `length` long in R^m (its arc
	// length; negative: backwards), in `steps` steps. Returns its samples (only the first where the
	// partial vanishes).
	paramLine { |u, v, which = \u, length = 1, steps = 16|
		var sign = if(length < 0) { -1 } { 1 };
		var f = { |s|
			var d = if(which == \v) { this.dv(s[0], s[1]) } { this.du(s[0], s[1]) };
			var n = d.squared.sum.sqrt;
			if(n < 1e-9) { nil } { if(which == \v) { [0, sign / n] } { [sign / n, 0] } }
		};
		^this.prIntegrate([u, v], f, length.abs / steps.max(1), steps.max(1), { |next| this.contains(next[0], next[1]) })
	}

	//////// a time function on the surface: an RCProjection (its time) or a Function (a point of R^m → number)

	timeAt { |u, v, timeFunc|
		var x = this.at(u, v);
		^if(timeFunc.isKindOf(RCProjection)) { timeFunc.timeOf(x) } { timeFunc.value(x) }
	}

	// The time function's gradient along the surface at (u, v), in parameters g⁻¹ ∇τ (τ the time of
	// S(u, v), g the metric), with its squared length |∇_S T|²: [[u', v'], squared length]; nil at a
	// singular point.
	surfaceGradient { |u, v, timeFunc|
		var su = this.du(u, v), sv = this.dv(u, v), tu, tv, gradT, w;
		if(timeFunc.isKindOf(RCProjection)) {
			gradT = timeFunc.timeGradient;
			tu = (gradT * su).sum;
			tv = (gradT * sv).sum;
		} {
			tu = (this.timeAt(u + eps, v, timeFunc) - this.timeAt(u - eps, v, timeFunc)) / (2 * eps);
			tv = (this.timeAt(u, v + eps, timeFunc) - this.timeAt(u, v - eps, timeFunc)) / (2 * eps);
		};
		w = this.prSolve(su, sv, tu, tv);
		^w !? { [w, (w[0] * tu) + (w[1] * tv)] }
	}

	// The steepest ascent of a time function through (u, v) (a negative length descends), in `steps`
	// steps. unit \time: its time grows by one per unit of length (length in time units: the strands
	// of a section ride it), \length: unit speed in R^m. A step whose time does not grow as it should
	// (at unit time by half to five quarters of the step: Runge-Kutta overshoots where the flow runs ever
	// faster, nearing a critical point) is tried again at half the length, four times; then it stops: at
	// a top, where strands converge (a bottom, going down), at a saddle reached along its separatrix, at
	// the edge of a patch that is not periodic, at a singular point. Returns [time, u, v] samples, each
	// with the time of its point (closer together where a step was halved).
	flow { |u, v, timeFunc, length = 1, steps = 16, unit = \time|
		var sign = if(length < 0) { -1 } { 1 }, total = length.abs, h0 = total / steps.max(1);
		var state = [u, v], t = this.timeAt(u, v, timeFunc), res = [[t, u, v]], travelled = 0, going = true;
		var f = { |s|
			var sg = this.surfaceGradient(s[0], s[1], timeFunc), speed;
			if(sg.isNil or: { sg[1] < 1e-12 }) { nil } {
				speed = if(unit == \length) { sg[1].sqrt } { sg[1] };   // the gradient's length or its square
				[sg[0][0] * sign / speed, sg[0][1] * sign / speed]
			}
		};
		while { going and: { travelled < (total - 1e-9) } } {
			var h = h0.min(total - travelled), next, tn, tries = 0, ok = false;
			while { ok.not and: { tries < 5 } } {
				next = this.prIntegrate(state, f, h, 1).at(1);   // one step, nil when it could not be taken
				ok = next.notNil and: { this.contains(next[0], next[1]) } and: {
					tn = this.timeAt(next[0], next[1], timeFunc);
					if(unit == \length) { ((tn - t) * sign) > 0 } { (((tn - t) * sign) > (0.5 * h)) and: { ((tn - t) * sign) < (1.25 * h) } }
				};
				if(ok.not) { h = h / 2; tries = tries + 1 };
			};
			if(ok) {
				state = next;
				t = tn;
				travelled = travelled + h;
				res = res.add([t, next[0], next[1]]);
			} { going = false };
		};
		^res
	}

	//////// a function on the patch (u, v) → number: its level sets and critical points, on a grid of
	// nu × nv nodes (prSpan's, wrapped where periodic)

	// The level set {func(u, v) = value} by marching squares (a saddle cell decided by its centre): an
	// Array of polylines, each an Array of [u, v] in order along the curve, unwrapped across a periodic
	// seam (a closed one ends on its first point, or a period on from it when it goes round).
	levelSet { |func, value = 0, nu = 32, nv = 32|
		var us = this.prSpan(uRange, nu, uPeriodic), vs = this.prSpan(vRange, nv, vPeriodic);
		var stepU = this.prStep(uRange, nu, uPeriodic), stepV = this.prStep(vRange, nv, vPeriodic);
		var periods = [uRange[1] - uRange[0], vRange[1] - vRange[0]];
		var vals = us.collect { |u| vs.collect { |v| func.value(u, v) - value } };
		var val = { |i, j| vals[i % nu][j % nv] };
		var key = { |i, j, dir| ((((i % nu) * nv) + (j % nv)) * 2) + dir };
		var cross = { |e|   // the crossing on edge e: dir 0 from node (i, j) to (i + 1, j), 1 to (i, j + 1)
			var dir = e % 2, i = (e div: 2) div: nv, j = (e div: 2) % nv;
			var f0 = val.(i, j), f1 = if(dir == 0) { val.(i + 1, j) } { val.(i, j + 1) };
			var t = if((f0 - f1).abs < 1e-300) { 0.5 } { f0 / (f0 - f1) };
			if(dir == 0) { [us[i] + (t * stepU), vs[j]] } { [us[i], vs[j] + (t * stepV)] }
		};
		var segs = List.new, byEdge = Dictionary.new, used, walk, polylines = List.new;
		(if(uPeriodic) { nu } { nu - 1 }).do { |i|
			(if(vPeriodic) { nv } { nv - 1 }).do { |j|
				var a = val.(i, j) >= 0, b = val.(i + 1, j) >= 0, c = val.(i + 1, j + 1) >= 0, d = val.(i, j + 1) >= 0;
				var bottom = key.(i, j, 0), right = key.(i + 1, j, 1), top = key.(i, j + 1, 0), left = key.(i, j, 1);
				var edges = [[bottom, a != b], [right, b != c], [top, c != d], [left, d != a]].select(_[1]).collect(_[0]);
				var centre;
				case
				{ edges.size == 2 } { segs.add(edges) }
				{ edges.size == 4 } {   // a saddle: the corners on the centre's side join through it
					centre = (val.(i, j) + val.(i + 1, j) + val.(i + 1, j + 1) + val.(i, j + 1)) >= 0;
					if(centre == a) { segs.add([bottom, right]); segs.add([top, left]) } { segs.add([left, bottom]); segs.add([right, top]) };
				};
			};
		};
		segs.do { |s, k| s.do { |e| byEdge[e] = (byEdge[e] ? []).add(k) } };
		used = false ! segs.size;
		walk = { |k, from|   // the edges of the polyline through segment k, entered at edge `from`
			var keys = [from], e = from, s = k;
			while { s.notNil } {
				used[s] = true;
				e = if(segs[s][0] == e) { segs[s][1] } { segs[s][0] };
				keys = keys.add(e);
				s = byEdge[e].detect { |q| used[q].not };
			};
			keys
		};
		// the open ones from the patch's border (an edge of one segment), then the loops
		segs.do { |s, k| if(used[k].not) { s.detect { |e| byEdge[e].size == 1 } !? { |e| polylines.add(walk.(k, e)) } } };
		segs.do { |s, k| if(used[k].not) { polylines.add(walk.(k, s[0])) } };
		^polylines.collect { |keys|
			var pts = keys.collect { |e| cross.(e) };
			(1..(pts.size - 1)).do { |k|
				[uPeriodic, vPeriodic].do { |periodic, c|
					if(periodic) { pts[k][c] = pts[k][c] - (periods[c] * ((pts[k][c] - pts[k - 1][c]) / periods[c]).round) };
				};
			};
			pts
		}.asArray
	}

	// The critical points of func: a node below (above) all its neighbours is a \min (\max), one
	// around which the neighbours' signs, relative to it, change four times or more a \saddle; an
	// interior one is moved to the stationary point of the quadratic through its neighbours (within a
	// cell). On the border of a patch that is not periodic only extremes, of what is there (where a
	// section's strands begin and end on the patch). Nodes on one point of R^m (a pole) count once.
	// Returns Events (u:, v:, value:, kind:).
	criticalPoints { |func, nu = 32, nv = 32|
		var us = this.prSpan(uRange, nu, uPeriodic), vs = this.prSpan(vRange, nv, vPeriodic);
		var stepU = this.prStep(uRange, nu, uPeriodic), stepV = this.prStep(vRange, nv, vPeriodic);
		var ring =[[1, 0], [1, 1], [0, 1], [-1, 1], [-1, 0], [-1, -1], [0, -1], [1, -1]];
		var vals = us.collect { |u| vs.collect { |v| func.value(u, v) } };
		var res = List.new, seen = List.new;
		nu.do { |i|
			nv.do { |j|
				var f0 = vals[i][j];
				var n = ring.collect { |d| this.prNode(i + d[0], j + d[1], nu, nv) !? { |ij| vals[ij[0]][ij[1]] } };
				var present = n.reject(_.isNil), interior = n.every(_.notNil);
				var kind, u = us[i], v = vs[j], fx, fy, fxx, fyy, fxy, det, x;
				kind = case
					{ present.every { |y| y >= f0 } and: { present.any { |y| y > f0 } } } { \min }
					{ present.every { |y| y <= f0 } and: { present.any { |y| y < f0 } } } { \max }
					{ interior and: { this.prSignChanges(n.collect { |y| y - f0 }) >= 4 } } { \saddle };
				if(kind.notNil) {
					if(interior) {
						fx = (n[0] - n[4]) / 2;
						fy = (n[2] - n[6]) / 2;
						fxx = n[0] - (2 * f0) + n[4];
						fyy = n[2] - (2 * f0) + n[6];
						fxy = (n[1] - n[7] - n[3] + n[5]) / 4;
						det = (fxx * fyy) - fxy.squared;
						if(det.abs > 1e-12) {
							u = u + ((((fxy * fy) - (fyy * fx)) / det).clip(-1, 1) * stepU);
							v = v + ((((fxy * fx) - (fxx * fy)) / det).clip(-1, 1) * stepV);
						};
					};
					x = this.at(u, v);
					if(seen.any { |q| (q[0] == kind) and: { (q[1] - x).squared.sum.sqrt < (1e-6 * x.abs.maxItem.max(1)) } }.not) {
						seen.add([kind, x]);
						res.add((u: u, v: v, value: if(interior) { func.value(u, v) } { f0 }, kind: kind));
					};
				};
			};
		};
		^res.asArray
	}

	// A node's indices, wrapped where periodic, nil off the patch.
	prNode { |i, j, nu, nv|
		var ii = i, jj = j;
		if(uPeriodic) { ii = i % nu } { if(i < 0 or: { i >= nu }) { ^nil } };
		if(vPeriodic) { jj = j % nv } { if(j < 0 or: { j >= nv }) { ^nil } };
		^[ii, jj]
	}

	// How many times the signs of a ring of numbers change going round it (0 counts as positive).
	prSignChanges { |ring|
		var signs = ring.collect { |y| y >= 0 };
		^signs.size.collect { |k| if(signs[k] != signs.wrapAt(k + 1)) { 1 } { 0 } }.sum
	}

	// Runge-Kutta 4 from state (an Array) in steps of h: f, state → its derivative (nil: stop); keep,
	// (next, state) → whether to go on to next (false: stop before it). Returns the states, the first
	// included.
	prIntegrate { |state, f, h, steps, keep|
		var res = [state];
		block { |break|
			steps.do {
				var k1, k2, k3, k4, next;
				k1 = f.(state);
				if(k1.isNil) { break.value };
				k2 = f.(state + (k1 * (h / 2)));
				if(k2.isNil) { break.value };
				k3 = f.(state + (k2 * (h / 2)));
				if(k3.isNil) { break.value };
				k4 = f.(state + (k3 * h));
				if(k4.isNil) { break.value };
				next = state + ((k1 + (k2 * 2) + (k3 * 2) + k4) * (h / 6));
				if(keep.notNil and: { keep.(next, state).not }) { break.value };
				state = next;
				res = res.add(state);
			};
		};
		^res
	}

	// [x, y] with (x S_u + y S_v) . S_u = bu and (x S_u + y S_v) . S_v = bv (the metric's system), nil
	// where S_u and S_v are (nearly) dependent.
	prSolve { |su, sv, bu, bv|
		var e = (su * su).sum, f = (su * sv).sum, g = (sv * sv).sum, det = (e * g) - f.squared;
		if(det <= (1e-12 * (e * g).max(1e-24))) { ^nil };
		^[((g * bu) - (f * bv)) / det, ((e * bv) - (f * bu)) / det]
	}

	//////// presets (rulings along v where the surface is ruled)

	// The plane z = 0: [u, v, 0].
	*plane { |uRange = #[-1, 1], vRange = #[-1, 1]|
		var zero = { |u, v| [0, 0, 0] };
		^this.new({ |u, v| [u, v, 0] }, 3, uRange, vRange, { |u, v| [1, 0, 0] }, { |u, v| [0, 1, 0] }, \plane)
			.secondPartials(zero, zero, zero)
	}

	// The hyperbolic paraboloid z = u * v / c, doubly ruled (u-lines and v-lines are straight):
	// the string section of Metastasis, the Philips Pavilion's walls.
	*hyperbolicParaboloid { |c = 1, uRange = #[-1, 1], vRange = #[-1, 1]|
		var zero = { |u, v| [0, 0, 0] };
		^this.new({ |u, v| [u, v, u * v / c] }, 3, uRange, vRange, { |u, v| [1, 0, v / c] }, { |u, v| [0, 1, u / c] }, \hyperbolicParaboloid)
			.secondPartials(zero, { |u, v| [0, 0, 1 / c] }, zero)
	}

	// The right conoid (a helicoid) [v cos u, v sin u, c u]: rulings through the axis, rising with u.
	*conoid { |c = 0.2, uRange, vRange = #[-1, 1]|
		uRange = uRange ?? { [0, 2pi] };
		^this.new({ |u, v| [v * u.cos, v * u.sin, c * u] }, 3, uRange, vRange,
			{ |u, v| [v * u.sin.neg, v * u.cos, c] }, { |u, v| [u.cos, u.sin, 0] }, \conoid).uPeriodic_(true)
			.secondPartials({ |u, v| [v.neg * u.cos, v.neg * u.sin, 0] }, { |u, v| [u.sin.neg, u.cos, 0] }, { |u, v| [0, 0, 0] })
	}

	// The cone [v cos u, v sin u, v]: every ruling through the apex, the fan of the opening of Metastasis.
	*cone { |uRange, vRange = #[0, 1]|
		uRange = uRange ?? { [0, 2pi] };
		^this.new({ |u, v| [v * u.cos, v * u.sin, v] }, 3, uRange, vRange,
			{ |u, v| [v * u.sin.neg, v * u.cos, 0] }, { |u, v| [u.cos, u.sin, 1] }, \cone).uPeriodic_(true)
			.secondPartials({ |u, v| [v.neg * u.cos, v.neg * u.sin, 0] }, { |u, v| [u.sin.neg, u.cos, 0] }, { |u, v| [0, 0, 0] })
	}

	// The cylinder [cos u, sin u, v].
	*cylinder { |uRange, vRange = #[-1, 1]|
		var zero = { |u, v| [0, 0, 0] };
		uRange = uRange ?? { [0, 2pi] };
		^this.new({ |u, v| [u.cos, u.sin, v] }, 3, uRange, vRange,
			{ |u, v| [u.sin.neg, u.cos, 0] }, { |u, v| [0, 0, 1] }, \cylinder).uPeriodic_(true)
			.secondPartials({ |u, v| [u.cos.neg, u.sin.neg, 0] }, zero, zero)
	}

	// The hyperboloid of one sheet [cos u - v sin u, sin u + v cos u, v]: doubly ruled, the
	// v-lines are straight (the rulings of the cooling tower).
	*hyperboloid { |uRange, vRange = #[-1, 1]|
		uRange = uRange ?? { [0, 2pi] };
		^this.new({ |u, v| [u.cos - (v * u.sin), u.sin + (v * u.cos), v] }, 3, uRange, vRange,
			{ |u, v| [u.sin.neg - (v * u.cos), u.cos - (v * u.sin), 0] }, { |u, v| [u.sin.neg, u.cos, 1] }, \hyperboloid).uPeriodic_(true)
			.secondPartials({ |u, v| [u.cos.neg + (v * u.sin), u.sin.neg - (v * u.cos), 0] }, { |u, v| [u.cos.neg, u.sin.neg, 0] }, { |u, v| [0, 0, 0] })
	}

	// The unit sphere, u the azimuth, v the elevation.
	*sphere { |uRange, vRange|
		uRange = uRange ?? { [0, 2pi] };
		vRange = vRange ?? { [-0.5pi, 0.5pi] };
		^this.new({ |u, v| [u.cos * v.cos, u.sin * v.cos, v.sin] }, 3, uRange, vRange,
			{ |u, v| [u.sin.neg * v.cos, u.cos * v.cos, 0] }, { |u, v| [u.cos * v.sin.neg, u.sin * v.sin.neg, v.cos] }, \sphere).uPeriodic_(true)
			.secondPartials({ |u, v| [u.cos.neg * v.cos, u.sin.neg * v.cos, 0] }, { |u, v| [u.sin * v.sin, u.cos.neg * v.sin, 0] },
				{ |u, v| [u.cos.neg * v.cos, u.sin.neg * v.cos, v.sin.neg] })
	}

	// The torus of radii bigR (the ring) and r (the tube).
	*torus { |bigR = 2, r = 1, uRange, vRange|
		uRange = uRange ?? { [0, 2pi] };
		vRange = vRange ?? { [0, 2pi] };
		^this.new({ |u, v| var w = bigR + (r * v.cos); [w * u.cos, w * u.sin, r * v.sin] }, 3, uRange, vRange,
			{ |u, v| var w = bigR + (r * v.cos); [w * u.sin.neg, w * u.cos, 0] },
			{ |u, v| [r * v.sin.neg * u.cos, r * v.sin.neg * u.sin, r * v.cos] }, \torus).uPeriodic_(true).vPeriodic_(true)
			.secondPartials({ |u, v| var w = bigR + (r * v.cos); [w.neg * u.cos, w.neg * u.sin, 0] },
				{ |u, v| [r * v.sin * u.sin, r.neg * v.sin * u.cos, 0] },
				{ |u, v| [r.neg * v.cos * u.cos, r.neg * v.cos * u.sin, r.neg * v.sin] })
	}

	// The Clifford torus in R^4, [cos u, sin u, cos v, sin v] / sqrt 2: flat, and every
	// rotation of R^4 shows it differently to a projection.
	*clifford { |uRange, vRange|
		var k = 2.sqrt.reciprocal;
		uRange = uRange ?? { [0, 2pi] };
		vRange = vRange ?? { [0, 2pi] };
		^this.new({ |u, v| [u.cos, u.sin, v.cos, v.sin] * k }, 4, uRange, vRange,
			{ |u, v| [u.sin.neg, u.cos, 0, 0] * k }, { |u, v| [0, 0, v.sin.neg, v.cos] * k }, \clifford).uPeriodic_(true).vPeriodic_(true)
			.secondPartials({ |u, v| [u.cos.neg, u.sin.neg, 0, 0] * k }, { |u, v| [0, 0, 0, 0] }, { |u, v| [0, 0, v.cos.neg, v.sin.neg] * k })
	}

	// The surface ruled by two rails: S(u, v) = A(u) + v (B(u) - A(u)), a and b Functions
	// u → Array of dim numbers; the v-lines are the strings between the rails. With `via` (a third
	// rail, u → Array) the v-lines are quadratic Bézier arcs pulled towards it, Q = mid + 2 pull
	// (M - mid): pull 0 the ruled surface, 1 the arcs through M at their middle (a generalized ruled
	// surface, its generatrices curved).
	*rails { |a, b, dim = 3, uRange = #[0, 1], vRange = #[0, 1], via, pull = 1|
		var q = { |u| var pa = a.value(u), pb = b.value(u), mid = (pa + pb) / 2; mid + (2 * pull * (via.value(u) - mid)) };
		if(via.isNil or: { pull == 0 }) {
			^this.new({ |u, v| var pa = a.value(u), pb = b.value(u); pa + ((pb - pa) * v) }, dim, uRange, vRange,
				nil, { |u, v| b.value(u) - a.value(u) }, \rails).secondPartials(nil, nil, { |u, v| 0 ! dim })
		};
		^this.new({ |u, v| ((1 - v).squared * a.value(u)) + (2 * v * (1 - v) * q.value(u)) + (v.squared * b.value(u)) }, dim, uRange, vRange,
			nil, { |u, v| (2 * (1 - v) * (q.value(u) - a.value(u))) + (2 * v * (b.value(u) - q.value(u))) }, \curvedRails)
			.secondPartials(nil, nil, { |u, v| 2 * (a.value(u) - (2 * q.value(u)) + b.value(u)) })
	}

	// The tangent developable of a curve C (a Function u → Array of dim numbers):
	// S(u, v) = C(u) + v C'(u), ruled by the tangents of the curve (Xenakeur's construction
	// as a surface: its rulings are the tangent lines).
	*tangentDevelopable { |curve, dim = 3, uRange = #[0, 1], vRange = #[-1, 1], eps = 1e-4|
		var deriv = { |u| (curve.value(u + eps) - curve.value(u - eps)) / (2 * eps) };
		^this.new({ |u, v| curve.value(u) + (deriv.value(u) * v) }, dim, uRange, vRange, nil, { |u, v| deriv.value(u) }, \tangentDevelopable)
			.secondPartials(nil, nil, { |u, v| 0 ! dim })
	}

	printOn { |stream| stream << "RCSurface(" << (name ? "custom") << ", R^" << dim << ")" }
}
