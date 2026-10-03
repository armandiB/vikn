// reCurrent — sonic straight lines and their generators.
//
// A uniform glissando is a straight line in the (time, pitch) plane: Xenakis drew
// the glissandi of Metastasis as lines and read the surfaces they rule (Formalized
// Music, ch. I). Here a line is a segment in a score space of time plus any number
// of named parameters, each linear in its own unit (pitch in octaves, amp in dB,
// azimuth in radians...): one note whose parameters glide from one end to the
// other. A generator returns an Array of lines for one cycle; RCLineControl hands
// them to the voices of a batch.
//
// A line is an Event:
//   (onset: beats, dur: beats, from: (pitch: 0, amp: -20), to: (pitch: 1, amp: -10), id: 3)
// plus, optionally, voice: an Integer pinning it to one voice of the batch (allocate), and path:
// a curved line's course, an Event key → Env over the note (RCCurve; a key without one is straight).
// A point of the score space is [time, params] with params an Event.
//
// Generators (every one pure, seeded where it draws):
//   rails      lines joining two rails A(u), B(u): the string-art ruled surfaces of
//              Metastasis (affine rails, evenly spaced u: a hyperbolic paraboloid)
//   tangents   lines tangent to a curve at given touch times (the Xenakeur parabola)
//   contact    paths touching a curve, the tangents bent by `bend` times its second derivative
//              (0: the tangents, 1: the osculating arcs, 2: curling past the curve)
//   projected  the tangent lines (or rulings, or curves on it: geodesics, parameter lines,
//              steepest ascents) of an RCSurface in R^m, sampled and projected on the score
//              space by an RCProjection; move the projection between cycles and the texture evolves
//   sections   the slice of an RCSurface by a moving hyperplane (the projection's time swept at a
//              speed): strands riding the surface so as to stay on it, born at its bottoms,
//              converging at its tops
//   cloud      Pithoprakta: short lines of Gaussian speed (temperature a)
// Then allocate (lines → voices) and subseq (a voice's lines → an RCSubseq).

RCLines {
	classvar <>defaultMinDur = 0.01;   // beats: a line steeper than time itself becomes a chirp this long

	//////// lines and points

	*line { |onset = 0, dur = 1, from, to, id|
		^(onset: onset, dur: dur, from: from ?? { () }, to: to ?? { from ?? { () } }, id: id)
	}

	// The union of the parameter keys of the lines, sorted.
	*keysOf { |lines|
		var res = IdentitySet.new;
		(lines ? []).do { |l| [l[\from], l[\to]].do { |p| p !? { |e| e.keysDo { |k| res.add(k) } } } };
		^res.asArray.sort { |a, b| a.asString <= b.asString }
	}

	// The slope of a line for one key: units per beat.
	*slope { |line, key|
		var a = line[\from][key], b = line[\to][key];
		if(a.isNil or: { b.isNil } or: { line[\dur] <= 0 }) { ^0 };
		^(b - a) / line[\dur]
	}

	// The line from point a to point b ([time, params]), in time order: when b comes
	// first the ends are swapped. A line shorter than minDur (vertical in the score,
	// a chord) is stretched to minDur, its ends kept: a chirp.
	*between { |a, b, minDur, id|
		var p0 = a, p1 = b, dur;
		if(p1[0] < p0[0]) { p0 = b; p1 = a };
		dur = p1[0] - p0[0];
		minDur = minDur ? defaultMinDur;
		if(dur < minDur) { dur = minDur };
		^this.line(p0[0], dur, this.prParams(p0[1]), this.prParams(p1[1]), id)
	}

	*prParams { |p|
		var res = ();
		p !? { p.keysValuesDo { |k, v| res[k] = v } };
		^res
	}

	// params + params, params * number: per key (a key missing on one side keeps the other's value)
	*paramsAdd { |a, b|
		var res = this.prParams(a);
		b !? { b.keysValuesDo { |k, v| res[k] = (res[k] ? 0) + v } };
		^res
	}

	*paramsScale { |a, x|
		var res = ();
		a !? { a.keysValuesDo { |k, v| res[k] = v * x } };
		^res
	}

	// The point between two points at u (0 → a, 1 → b).
	*lerpPoint { |a, b, u|
		var params = ();
		var keys = IdentitySet.new;
		[a[1], b[1]].do { |p| p !? { p.keysDo { |k| keys.add(k) } } };
		keys.do { |k|
			var va = a[1][k], vb = b[1][k];
			params[k] = case
				{ va.isNil } { vb }
				{ vb.isNil } { va }
				{ va + (u * (vb - va)) };
		};
		^[a[0] + (u * (b[0] - a[0])), params]
	}

	//////// rails: the string-art surfaces (Metastasis bars 309-314, the Philips Pavilion)

	// The affine rail from point a to point b: a Function u → point.
	*rail { |a, b|
		^{ |u| this.lerpPoint(a, b, u) }
	}

	// n lines, line i joining a.(u_i) to b.(u_i); a and b are rails (Functions u → point,
	// see rail). u_i evenly spaced in [0, 1] (n lines), or the given `us`. Two affine rails
	// give the hyperbolic paraboloid of the string section; a rail collapsed to one point
	// (rail(p, p)) gives the fan of the opening of Metastasis. Lines are returned in time
	// order.
	// Curved rulings (a generalized ruled surface, the architect's sense): with `via` (a point, or a
	// rail u → point) each ruling is the quadratic Bézier arc from a.(u) to b.(u) pulled towards
	// via.(u): pull 0 the straight line, 1 the arc through it, more overshooting (a number, or an Event
	// key → pull, the time coordinate reading \time; 0 for a key it lacks). The arc's control point keeps
	// its time between the ends' (its time stays monotone). samples: points along each arc.
	*rails { |a, b, n = 8, us, minDur, via, pull = 1, samples = 16|
		us = us ?? { if(n <= 1) { [0] } { n.collect { |i| i / (n - 1) } } };
		^us.collect { |u, i|
			if(via.isNil or: { this.prFlat(pull) }) {
				this.between(a.value(u), b.value(u), minDur, i)
			} {
				this.prArc(a.value(u), if(via.isKindOf(Function)) { via.value(u) } { via }, b.value(u), pull, samples, minDur, i)
			}
		}.reject(_.isNil).sort { |x, y| x[\onset] <= y[\onset] }
	}

	// The arc of one curved ruling: Q = mid + 2 pull (via - mid) per coordinate (the arc passes via at
	// its middle for pull 1), Q's time clipped between the ends', sampled, as a line.
	*prArc { |pa, pm, pb, pull, samples, minDur, id|
		var pullOf = { |k| if(pull.isNumber) { pull } { pull[k] ? 0 } };
		var keys = IdentitySet.new, qt, qp = (), t0, t1, pts;
		[pa[1], pb[1]].do { |e| e.keysDo { |k| keys.add(k) } };
		t0 = pa[0].min(pb[0]);
		t1 = pa[0].max(pb[0]);
		qt = (((pa[0] + pb[0]) / 2) + (2 * pullOf.(\time) * (pm[0] - ((pa[0] + pb[0]) / 2)))).clip(t0, t1);
		keys.do { |k|
			var va = pa[1][k] ? pb[1][k], vb = pb[1][k] ? pa[1][k], mid = (va + vb) / 2;
			qp[k] = mid + (2 * pullOf.(k) * ((pm[1][k] ? mid) - mid));
		};
		pts = (samples + 1).collect { |j|
			var s = j / samples, w0 = (1 - s).squared, w1 = 2 * s * (1 - s), w2 = s.squared, params = ();
			keys.do { |k| params[k] = (w0 * (pa[1][k] ? pb[1][k])) + (w1 * qp[k]) + (w2 * (pb[1][k] ? pa[1][k])) };
			[(w0 * pa[0]) + (w1 * qt) + (w2 * pb[0]), params]
		};
		if(pb[0] < pa[0]) { pts = pts.reverse };
		^RCCurve.lineFrom(pts, id, minDur)
	}

	//////// tangents: the Xenakeur construction

	// Lines tangent to `curve` (a Function tau → point) at the touch parameters `touches`.
	// derivative: a Function tau → [dtime, dparams] (nil: central differences with eps).
	// extent: how far the line runs along the tangent, in units of tau: a number (each
	// side), [before, after], or a Function (point, tangent, tau) → [before, after]. The line
	// goes from C - before * C' to C + after * C'. A tangent running backwards in time still
	// gives a line in time order. Returns the lines in time order.
	*tangents { |curve, touches, extent = 1, derivative, eps = 1e-4, minDur|
		var lines = List.new;
		touches.do { |tau, i|
			var point = curve.value(tau);
			var tangent = derivative !? (_.value(tau)) ?? { this.prDerivative(curve, tau, eps) };
			var ext = extent;
			var before, after;
			if(ext.isKindOf(Function)) { ext = ext.value(point, tangent, tau) };
			if(ext.isNumber) { ext = [ext, ext] };
			before = ext[0];
			after = ext[1];
			if((before + after) > 0) {
				lines.add(this.between(
					[point[0] - (before * tangent[0]), this.paramsAdd(point[1], this.paramsScale(tangent[1], before.neg))],
					[point[0] + (after * tangent[0]), this.paramsAdd(point[1], this.paramsScale(tangent[1], after))],
					minDur, i));
			};
		};
		^lines.asArray.sort { |x, y| x[\onset] <= y[\onset] }
	}

	*prDerivative { |curve, tau, eps|
		var a = curve.value(tau - eps), b = curve.value(tau + eps);
		var params = ();
		b[1].keysValuesDo { |k, v| params[k] = (v - (a[1][k] ? v)) / (2 * eps) };
		^[(b[0] - a[0]) / (2 * eps), params]
	}

	*prSecondDerivative { |curve, tau, eps|
		var a = curve.value(tau - eps), m = curve.value(tau), b = curve.value(tau + eps);
		var params = ();
		m[1].keysValuesDo { |k, v| params[k] = ((b[1][k] ? v) - (2 * v) + (a[1][k] ? v)) / eps.squared };
		^[(b[0] - (2 * m[0]) + a[0]) / eps.squared, params]
	}

	//////// contact: the tangential construction with bent generatrices

	// Paths touching `curve` (a Function tau → point) at the touch parameters: each the curve's tangent
	// bent by `bend` times half its second derivative, C(tau) + h C'(tau) + bend h²/2 C''(tau), h from
	// -before to after (extent as for tangents). The curve is their envelope whatever the bend: 0 gives
	// the tangent lines (exactly `tangents`), 1 the osculating arcs (the curve to second order), 2 arcs
	// curling past it (the mass on the other side of the curve). bend: a number, an Event key → number
	// (the time coordinate reads \time), or a Function (tau) → either. A bent path whose time turns back
	// is cut at the turn, the part through the touch kept; its keys that stay straight carry no path
	// (RCCurve.lineFrom). samples: points along each bent path. Returns the lines in time order.
	*contact { |curve, touches, extent = 1, bend = 0, derivative, eps = 1e-4, minDur, samples = 16|
		var lines = List.new;
		touches.do { |tau, i|
			var point = curve.value(tau);
			var d1 = derivative !? (_.value(tau)) ?? { this.prDerivative(curve, tau, eps) };
			var b = if(bend.isKindOf(Function)) { bend.value(tau) } { bend };
			var ext = extent, before, after, line;
			if(ext.isKindOf(Function)) { ext = ext.value(point, d1, tau) };
			if(ext.isNumber) { ext = [ext, ext] };
			before = ext[0];
			after = ext[1];
			if((before + after) > 0) {
				line = if(this.prFlat(b)) {
					this.between(
						[point[0] - (before * d1[0]), this.paramsAdd(point[1], this.paramsScale(d1[1], before.neg))],
						[point[0] + (after * d1[0]), this.paramsAdd(point[1], this.paramsScale(d1[1], after))],
						minDur, i)
				} {
					this.prBentLine(point, d1, this.prSecondDerivative(curve, tau, eps * 10), b, before, after, samples, minDur, i)
				};
				line !? { lines.add(line) };
			};
		};
		^lines.asArray.sort { |x, y| x[\onset] <= y[\onset] }
	}

	*prFlat { |b| ^if(b.isNumber) { b == 0 } { b.isNil or: { b.values.every { |v| v == 0 } } } }

	// The samples of one bent path, kept in time order up to where its time turns back, as a line.
	*prBentLine { |point, d1, d2, b, before, after, samples, minDur, id|
		var bendOf = { |k| if(b.isNumber) { b } { b[k] ? 0 } };
		var pts = (samples + 1).collect { |j|
			var h = before.neg + ((before + after) * j / samples), params = ();
			point[1].keysValuesDo { |k, v| params[k] = v + (h * (d1[1][k] ? 0)) + (bendOf.(k) * h.squared / 2 * (d2[1][k] ? 0)) };
			[point[0] + (h * d1[0]) + (bendOf.(\time) * h.squared / 2 * d2[0]), params]
		};
		var run = this.prTimeRun(pts, (before / (before + after) * samples).round.asInteger.clip(0, samples));
		^run !? { RCCurve.lineFrom(run, id, minDur) }
	}

	// The longest run of samples [time, ...] through index j0 along which time keeps the direction it
	// has at j0, in time order (reversed when time runs backwards there); nil for fewer than two samples.
	*prTimeRun { |pts, j0|
		var last = pts.size - 1, lo = j0, hi = j0, up;
		if(last < 1) { ^nil };
		up = if(j0 < last) { pts[j0 + 1][0] >= pts[j0][0] } { pts[j0][0] >= pts[j0 - 1][0] };
		if(up) {
			while { (lo > 0) and: { pts[lo - 1][0] < pts[lo][0] } } { lo = lo - 1 };
			while { (hi < last) and: { pts[hi + 1][0] > pts[hi][0] } } { hi = hi + 1 };
		} {
			while { (lo > 0) and: { pts[lo - 1][0] > pts[lo][0] } } { lo = lo - 1 };
			while { (hi < last) and: { pts[hi + 1][0] < pts[hi][0] } } { hi = hi + 1 };
		};
		if(hi <= lo) { ^nil };
		^if(up) { pts.copyRange(lo, hi) } { pts.copyRange(lo, hi).reverse }
	}

	// The parabola of the Xenakeur parabolas: tau → [tau, apex + curvature * (tau - apexTime)^2]
	// per key. apex: an Event (key → value at the apex); curvature: a number for every key
	// or an Event per key (units per beat^2; the sign turns it). Its tangent at tau has the
	// slope 2 * curvature * (tau - apexTime).
	*parabola { |apexTime = 0, apex, curvature = 1|
		apex = apex ?? { (pitch: 0) };
		^{ |tau|
			var params = ();
			var d = tau - apexTime;
			apex.keysValuesDo { |k, v|
				var c = if(curvature.isNumber) { curvature } { curvature[k] ? 0 };
				params[k] = v + (c * d * d);
			};
			[tau, params]
		}
	}

	// A sine in any keys, tau → [tau, center + amp sin(2pi (tau - t0) / period + phase)] per key: center
	// and amp Events (key → value), period (beats) and phase numbers or Events per key. Two keys with
	// phases a quarter turn apart draw a helix (a spiral in pitch × az over time), other periods a
	// Lissajous figure. A key of center without amp stays at its center.
	*sine { |center, amp, period = 8, phase = 0, t0 = 0|
		center = center ?? { (pitch: 0) };
		amp = amp ?? { () };
		^{ |tau|
			var params = ();
			center.keysValuesDo { |k, c|
				var a = amp[k] ? 0, per = if(period.isNumber) { period } { period[k] ? 8 }, ph = if(phase.isNumber) { phase } { phase[k] ? 0 };
				params[k] = c + (a * ((2pi * (tau - t0) / per.max(1e-9)) + ph).sin);
			};
			[tau, params]
		}
	}

	// A circle (an ellipse) in the plane of time and the keys of `center`: tau an angle, the point
	// [centerTime + radiusTime cos tau, center + radius sin tau] (radius an Event key → radius, or a
	// number for every key). Its time turns back at tau = 0 and pi: a path through there is cut.
	*circle { |centerTime = 0, center, radiusTime = 4, radius = 1|
		center = center ?? { (pitch: 0) };
		^{ |tau|
			var params = ();
			center.keysValuesDo { |k, c| params[k] = c + ((if(radius.isNumber) { radius } { radius[k] ? 0 }) * tau.sin) };
			[centerTime + (radiusTime * tau.cos), params]
		}
	}

	// A smooth curve through the composer's points [[time, params], ...] (times increasing): cubic
	// Hermite between them with the slopes of the neighbours (Catmull-Rom on uneven times, one-sided
	// at the ends), tau the time; on along the end slopes outside. A key missing from a point is left out.
	*spline { |points|
		var pts = points.asArray.sort { |a, b| a[0] <= b[0] };
		var n = pts.size, keys, slopes;
		if(n < 2) { ^{ |tau| [tau, (pts.first ? [0, ()])[1].copy] } };
		keys = pts.first[1].keys.select { |k| pts.every { |p| p[1][k].isNumber } };
		slopes = pts.collect { |p, i|
			var a = pts[(i - 1).max(0)], b = pts[(i + 1).min(n - 1)], dt = (b[0] - a[0]).max(1e-9), s = ();
			keys.do { |k| s[k] = (b[1][k] - a[1][k]) / dt };
			s
		};
		^{ |tau|
			var j = 0, params = (), p0, p1, h, u, h00, h10, h01, h11;
			while { (j < (n - 2)) and: { pts[j + 1][0] < tau } } { j = j + 1 };
			p0 = pts[j];
			p1 = pts[j + 1];
			h = (p1[0] - p0[0]).max(1e-9);
			case
			{ tau < pts.first[0] } { keys.do { |k| params[k] = pts.first[1][k] + (slopes.first[k] * (tau - pts.first[0])) } }
			{ tau > pts.last[0] } { keys.do { |k| params[k] = pts.last[1][k] + (slopes.last[k] * (tau - pts.last[0])) } }
			{
				u = (tau - p0[0]) / h;
				h00 = (2 * u.cubed) - (3 * u.squared) + 1;
				h10 = u.cubed - (2 * u.squared) + u;
				h01 = (-2 * u.cubed) + (3 * u.squared);
				h11 = u.cubed - u.squared;
				keys.do { |k| params[k] = (h00 * p0[1][k]) + (h10 * h * slopes[j][k]) + (h01 * p1[1][k]) + (h11 * h * slopes[j + 1][k]) };
			};
			[tau, params]
		}
	}

	// The tangent of `curve` at tau as [point, tangent] (a helper for extent functions and tests).
	*tangentAt { |curve, tau, eps = 1e-4|
		^[curve.value(tau), this.prDerivative(curve, tau, eps)]
	}

	//////// projected: the tangent lines and the curves of a surface in R^m, seen through a projection

	// For each sample [u, v] of `surface` (an RCSurface), a segment or a curve of R^m through S(u, v),
	// projected on the score space by `frame` (an RCProjection):
	//   \ruling            the whole v-line of the point, from vRange[0] to vRange[1] (on a ruled
	//                      surface, the ruling itself)
	//   \u, \v, a number (an angle in the tangent plane, 0 = \u, pi/2 = \v), a Function (u, v, i) →
	//                      angle: the tangent segment, bent by `bend` times the surface's curvature
	//                      along it (0 the tangent, 1 the geodesic to second order, 2 curling past it;
	//                      a number or a Function (u, v, i) → number)
	//   \geodesic          the geodesic through the point at `angle` (a number or a Function (u, v, i)
	//                      → angle): the straightest curve on the surface (a ruling, a great circle)
	//   \uLine, \vLine     the parameter line through the point
	//   \gradient          the steepest ascent of the frame's time through the point: the line that
	//                      climbs the score fastest (none at a critical point)
	// `length` is the segment's or the curve's length in R^m, centred on the point (ignored by \ruling);
	// a curve is drawn in `steps` steps. A curve whose time turns back is cut at the turn, the part
	// through the point kept, and fitted as a curved line (RCCurve.lineFrom), its R^m polyline kept in
	// `curve` beside its `ends`, both in the line's own order (a point travelling them from the first
	// to the last is the sound travelling the line).
	// timeMode: \window keeps the lines starting within [0, cycle), \wrap wraps their onsets
	// into the cycle, \sequence ignores the projected time and starts line i at
	// i * cycle / n, \none keeps every line as projected. Returns the lines in time order.
	*projected { |surface, frame, samples, direction = \ruling, length = 1, timeMode = \window, cycle, minDur, bend = 0, angle = 0, steps = 16|
		var lines = List.new;
		var n = samples.size;
		samples.do { |uv, i|
			var u = uv[0], v = uv[1];
			var x0, x1, curve, line, keep = true;
			case
			{ direction == \ruling } {
				x0 = surface.at(u, surface.vRange[0]);
				x1 = surface.at(u, surface.vRange[1]);
			}
			{ [\geodesic, \uLine, \vLine, \gradient].includes(direction) } {
				curve = this.prSurfaceCurve(surface, frame, u, v, direction, if(angle.isKindOf(Function)) { angle.value(u, v, i) } { angle }, length, steps);
				if(curve.isNil) { keep = false };
			}
			{
				var a = case
					{ direction == \u } { 0 }
					{ direction == \v } { 0.5pi }
					{ direction.isKindOf(Function) } { direction.value(u, v, i) }
					{ direction };
				var b = if(bend.isKindOf(Function)) { bend.value(u, v, i) } { bend };
				var tangent = surface.tangent(u, v, a);
				var norm = tangent.squared.sum.sqrt;
				var point = surface.at(u, v);
				case
				{ norm < 1e-9 } { keep = false }
				{ b == 0 } {
					tangent = tangent * (length / 2 / norm);
					x0 = point - tangent;
					x1 = point + tangent;
				}
				{
					curve = this.prBentTangent(surface, u, v, a, norm, b, length, steps);
					if(curve.isNil) { keep = false };
				};
			};
			if(keep) {
				if(curve.isNil) {
					var p0 = frame.project(x0), p1 = frame.project(x1);
					line = this.between(p0, p1, minDur, i);
					// the segment in R^m, for a picture of the surface, in the line's own order: between
					// starts the line at the earlier end, so the ends swap with it (a point travelling the
					// segment from ends[0] to ends[1] is the sound travelling the line)
					line[\ends] = if(p1[0] < p0[0]) { [x1, x0] } { [x0, x1] };
				} {
					// [time, params, x] along the curve, cut where its time turns back
					var run = this.prTimeRun(curve[0].collect { |x| frame.project(x) ++ [x] }, curve[1]);
					line = run !? { RCCurve.lineFrom(run, i, minDur) };
					if(line.isNil) { keep = false } {
						line[\curve] = run.collect(_[2]);
						line[\ends] = [run.first[2], run.last[2]];
					};
				};
			};
			if(keep) {
				line[\sample] = uv;
				switch(timeMode,
					\window, { if(cycle.notNil) { keep = (line[\onset] >= 0) and: { line[\onset] < cycle } } },
					\wrap, { if(cycle.notNil) { line[\onset] = line[\onset] % cycle } },
					\sequence, { if(cycle.notNil) { line[\onset] = i * cycle / n.max(1) } }
				);
				if(keep) { lines.add(line) };
			};
		};
		^lines.asArray.sort { |x, y| x[\onset] <= y[\onset] }
	}

	// A curve on the surface through (u, v), `length` long in R^m and centred there: [the points of R^m
	// along it, the index of the point], nil where it has no direction.
	*prSurfaceCurve { |surface, frame, u, v, direction, angle, length, steps|
		var half = (steps / 2).ceil.asInteger.max(1);
		var walk = { |len|
			switch(direction,
				\geodesic, { surface.geodesic(u, v, angle, len, half) },
				\uLine, { surface.paramLine(u, v, \u, len, half) },
				\vLine, { surface.paramLine(u, v, \v, len, half) },
				\gradient, { surface.flow(u, v, frame, len, half, \length).collect { |s| [s[1], s[2]] } }
			)
		};
		var back = walk.(length.neg / 2), fwd = walk.(length / 2), uvs;
		if(back.isNil or: { fwd.isNil }) { ^nil };
		uvs = back.reverse ++ fwd.drop(1);
		if(uvs.size < 2) { ^nil };
		^[uvs.collect { |q| surface.at(q[0], q[1]) }, back.size - 1]
	}

	// The tangent at angle a (its length norm) bent by b times the surface's curvature along it: x(h) =
	// S + h t + b h²/2 k for h over ±length / 2, t the unit tangent, k the acceleration of the geodesic
	// leaving along t (the part of S_uu u'² + 2 S_uv u'v' + S_vv v'² across the surface). [points,
	// index of the point], nil at a singular point.
	*prBentTangent { |surface, u, v, a, norm, b, length, steps|
		var up = a.cos / norm, vp = a.sin / norm;
		var acc = surface.geodesicAccel(u, v, up, vp);
		var point = surface.at(u, v), t = surface.tangent(u, v, a) / norm, k;
		var m = steps.max(2);
		if(acc.isNil) { ^nil };
		k = (surface.duu(u, v) * up.squared) + (surface.duv(u, v) * (2 * up * vp)) + (surface.dvv(u, v) * vp.squared)
			+ (surface.du(u, v) * acc[0]) + (surface.dv(u, v) * acc[1]);
		^[(m + 1).collect { |j| var h = (length.neg / 2) + (length * j / m); point + (t * h) + (k * (b * h.squared / 2)) }, m div: 2]
	}

	//////// sections: a surface cut by a moving hyperplane

	// A section of `surface` by the moving hyperplane of `frame`'s time (or the hypersurface of timeFunc,
	// a Function of R^m → number): at beat t of the cycle it is {T = offset + speed t}, and what sounds is
	// the slice. Strands stay on it, riding the surface's steepest ascent of T at unit time
	// (RCSurface.flow), each a curved line whose time is exactly the score's and whose other coordinates
	// are the frame's projection of its point (pitch, az...). seeding: \slice (n strands spread by arc
	// length along the slice at the cycle's start), \births (n strands leaving every way from each bottom
	// of T the hyperplane reaches in the cycle), \sweep (both), \random (n strands from random points the
	// hyperplane crosses in the cycle; seed). A strand ends at a top of T (where strands converge), at the
	// edge of a patch, after `length` beats or at the cycle's end: the strands live within a cycle (each
	// sample's time is its point's, so an end may pass by the integration's error). A negative speed
	// sweeps down (births at the tops). steps: a whole cycle's strand (a shorter one fewer, at least 8);
	// res: the grid of the slice (the bottoms on one of half as many nodes). Returns the lines in time
	// order, each with its R^m `curve` and `ends`, its seed `sample` and how it was `born` (\slice, \min,
	// \max, \random).
	*sections { |surface, frame, offset = 0, speed = 1, n = 12, length, cycle = 8, seeding = \sweep, seed, timeFunc, steps = 16, res = 32, minDur|
		var tf = timeFunc ? frame;
		var tau = { |u, v| surface.timeAt(u, v, tf) };
		var window = [offset, offset + (speed * cycle)].sort;   // the times the hyperplane sweeps in the cycle
		var maxLen = (length ? cycle).min(cycle);
		var seeds = List.new, lines = List.new;
		if(speed == 0 or: { n < 1 }) { ^[] };
		if([\slice, \sweep].includes(seeding)) { this.prSliceSeeds(surface, tf, offset, n, res).do { |uv| seeds.add(uv ++ [\slice]) } };
		if([\births, \sweep].includes(seeding)) { this.prBirthSeeds(surface, tau, window, speed.sign, n, (res / 2).asInteger.max(8)).do { |s| seeds.add(s) } };
		if(seeding == \random) {
			RCUtil.seeded(seed, {
				var tries = 0;
				while { (seeds.size < n) and: { tries < (30 * n) } } {
					var uv = [rrand(surface.uRange[0], surface.uRange[1]), rrand(surface.vRange[0], surface.vRange[1])], t = tau.(uv[0], uv[1]);
					tries = tries + 1;
					if((t >= window[0]) and: { t < window[1] }) { seeds.add(uv ++ [\random]) };
				};
			});
		};
		seeds.do { |s, i|
			var t0 = ((tau.(s[0], s[1]) - offset) / speed).max(0), span = maxLen.min(cycle - t0), pts, line;
			if(span > 1e-6) {
				pts = surface.flow(s[0], s[1], tf, speed * span, (steps * span / cycle).ceil.asInteger.max(8), \time).collect { |q|
					var x = surface.at(q[1], q[2]);
					[(q[0] - offset) / speed, frame.project(x)[1], x]
				};
				line = if(pts.size >= 2) { RCCurve.lineFrom(pts, i, minDur) };
				line !? {
					line[\curve] = pts.collect(_[2]);
					line[\ends] = [pts.first[2], pts.last[2]];
					line[\sample] = [s[0], s[1]];
					line[\born] = s[2];
					lines.add(line);
				};
			};
		};
		^lines.asArray.sort { |x, y| x[\onset] <= y[\onset] }
	}

	// n points spread by arc length (in R^m) along the level set {time = value} of the time function tf,
	// [u, v] each, brought onto it by two Newton steps along the surface's gradient (the marching
	// squares' points lie a little off).
	*prSliceSeeds { |surface, tf, value, n, res|
		var tau = { |u, v| surface.timeAt(u, v, tf) };
		var pieces = List.new, total = 0;   // [uv a, uv b, length, length before]
		surface.levelSet(tau, value, res, res).do { |pl|
			var xs = pl.collect { |uv| surface.at(uv[0], uv[1]) };
			(pl.size - 1).do { |j|
				var d = (xs[j + 1] - xs[j]).squared.sum.sqrt;
				pieces.add([pl[j], pl[j + 1], d, total]);
				total = total + d;
			};
		};
		if(total <= 0) { ^[] };
		^n.collect { |i|
			var target = total * (i + 0.5) / n;
			var p = pieces.detect { |q| target <= (q[3] + q[2]) } ? pieces.last;
			var w = if(p[2] > 0) { ((target - p[3]) / p[2]).clip(0, 1) } { 0 };
			var uv = p[0] + ((p[1] - p[0]) * w);
			2.do {
				var sg = surface.surfaceGradient(uv[0], uv[1], tf), d;
				if(sg.notNil and: { sg[1] > 1e-12 }) {
					d = (value - tau.(uv[0], uv[1])) / sg[1];
					uv = uv + (sg[0] * d);
				};
			};
			uv
		}
	}

	// n points round each bottom of tau (a top when sweeping down) within the window, a fiftieth of the
	// surface's size away, every way: [u, v, \min or \max].
	*prBirthSeeds { |surface, tau, window, sign, n, res|
		var kind = if(sign > 0) { \min } { \max };
		var crit = surface.criticalPoints(tau, res, res).select { |c| (c[\kind] == kind) and: { c[\value] >= window[0] } and: { c[\value] < window[1] } };
		var corners, radius;
		if(crit.isEmpty) { ^[] };
		corners = surface.grid(6, 6).collect { |uv| surface.at(uv[0], uv[1]) };
		radius = 0.02 * (corners.flop.collect { |c| c.maxItem - c.minItem }.squared.sum.sqrt).max(1e-6);
		^crit.collect { |c|
			n.collect { |i|
				var a = 2pi * i / n, t = surface.tangent(c[\u], c[\v], a), len = t.squared.sum.sqrt;
				if(len < 1e-9) { [c[\u], c[\v], kind] } { [c[\u] + (a.cos * radius / len), c[\v] + (a.sin * radius / len), kind] }
			}
		}.flatten(1)
	}

	//////// cloud: Pithoprakta

	// n short lines over one cycle: onsets uniform in [0, cycle), start values uniform in
	// `ranges` (key → [lo, hi]), speeds from Xenakis' law for glissando speeds (Formalized
	// Music p. 14): |v| half-normal with temperature a, either sign, i.e. v ~ N(0, a / sqrt 2),
	// units per beat, per key (`temperatures`: key → a; a key missing there is static). dur: a
	// number or [min, max] (uniform), beats. With clip, an end leaving the range is clipped to
	// it. Seeded: the same seed gives the same cloud.
	*cloud { |n = 16, cycle = 8, ranges, temperatures, dur = 1, seed, clip = true, minDur|
		ranges = ranges ?? { (pitch: [0, 2]) };
		temperatures = temperatures ?? { () };
		^RCUtil.seeded(seed, {
			n.collect { |i|
				var d = if(dur.isKindOf(SequenceableCollection)) { rrand(dur[0], dur[1]) } { dur };
				var from = (), to = ();
				d = d.max(minDur ? defaultMinDur);
				ranges.keysValuesDo { |k, range|
					var lo = range[0], hi = range[1];
					var a = temperatures[k] ? 0;
					var start = rrand(lo, hi);
					var speed = if(a > 0) { 0.0.gauss(a / 2.sqrt) } { 0 };
					var end = start + (speed * d);
					if(clip) { end = end.clip(lo.min(hi), hi.max(lo)) };
					from[k] = start;
					to[k] = end;
				};
				this.line(rrand(0.0, cycle), d, from, to, i)   // not cycle.rand: an Integer cycle would put every onset on a beat
			}.sort { |x, y| x[\onset] <= y[\onset] }
		})
	}

	//////// allocation and subseqs

	// Distribute lines over n voices (in onset order): \round_robin (line i to voice i mod n,
	// overlaps allowed), \free (the voice free the longest; when every voice is busy, the one
	// free soonest: an overlap on that voice), \strict (as \free, but a line finding no free
	// voice is dropped: n strings play at most n lines at once). A line carrying `voice` (an
	// Integer) is pinned to voice `voice mod n` in every mode, placed first and never dropped:
	// the thread of a continuous texture stays on one voice (the free lines then avoid it while
	// it is busy). Returns (voices: Array of n Arrays of lines, dropped: Integer).
	*allocate { |lines, n = 1, mode = \free|
		var sorted = (lines ? []).sort { |x, y| x[\onset] <= y[\onset] };
		var voices = Array.fill(n.max(1), { List.new });
		var busyUntil = 0 ! n.max(1);
		var dropped = 0;
		var pinned = sorted.select { |l| l[\voice].isKindOf(Integer) };
		pinned.do { |line|
			var v = line[\voice] % voices.size;
			voices[v].add(line);
			busyUntil[v] = max(busyUntil[v], line[\onset] + line[\dur]);
		};
		sorted = sorted.reject { |l| l[\voice].isKindOf(Integer) };
		sorted.do { |line, i|
			var v;
			if(mode == \round_robin) {
				v = i % voices.size;
			} {
				var free = busyUntil.collect { |t, j| if(t <= (line[\onset] + 1e-9)) { j } };
				free = free.reject(_.isNil);
				if(free.size > 0) {
					v = free.minItem { |j| busyUntil[j] };
				} {
					v = if(mode == \strict) { nil } { busyUntil.minIndex };
				};
			};
			if(v.isNil) {
				dropped = dropped + 1;
			} {
				voices[v].add(line);
				busyUntil[v] = max(busyUntil[v], line[\onset] + line[\dur]);
			};
		};
		^(voices: voices.collect(_.asArray), dropped: dropped)
	}

	// One voice's lines (in onset order) as an RCSubseq for the seq machinery: shift = the
	// first onset, each hit lasting until the next line of the voice (the last one its own
	// length), params key0 / key1 per line for every key of `keys` (the lines' union when
	// nil; a key a line lacks is nil there), plus sustain (the line's length), line_id and
	// path (the line's `path`, whatever it holds: a curved line's envelopes per key; nil for a
	// straight one). A line starting less than minGap after the previous one of the voice is
	// dropped (warned): a zero-length hit would stall the loop. Returns nil for no lines.
	*subseq { |lines, keys, priority = 1, minGap = 0.001, tag = \lines|
		var sorted = (lines ? []).sort { |x, y| x[\onset] <= y[\onset] };
		var kept = List.new, dropped = 0, last;
		var durs, params;
		sorted.do { |line|
			if(last.notNil and: { line[\onset] < (last[\onset] + minGap) }) { dropped = dropped + 1 } { kept.add(line); last = line };
		};
		if(dropped > 0) { RCLog.warn(tag, "% line(s) starting within % beat of the previous one on the same voice dropped".format(dropped, minGap)) };
		if(kept.size == 0) { ^nil };
		keys = keys ?? { this.keysOf(kept) };
		durs = kept.collect { |line, i| if(i < (kept.size - 1)) { kept[i + 1][\onset] - line[\onset] } { line[\dur] } };
		params = ();
		keys.do { |k|
			params[this.fromKey(k)] = kept.collect { |line| line[\from][k] };
			params[this.toKey(k)] = kept.collect { |line| line[\to][k] };
		};
		params[\sustain] = kept.collect { |line| line[\dur] };
		params[\line_id] = kept.collect { |line, i| line[\id] ? i };
		if(kept.any { |line| line[\path].notNil }) { params[\path] = kept.collect { |line| line[\path] } };   // only when a line is curved: a straight voice carries nothing more
		^RCSubseq(priority, kept[0][\onset], durs, params, true ! kept.size, nil, [], 1)
	}

	// The per-hit param names of a key: pitch → pitch0 (from) and pitch1 (to).
	*fromKey { |key| ^(key.asString ++ "0").asSymbol }
	*toKey { |key| ^(key.asString ++ "1").asSymbol }

	// The param keys the subseqs of `keys` carry, in a stable order.
	*paramKeys { |keys|
		^(keys.collect { |k| [this.fromKey(k), this.toKey(k)] }.flatten(1) ++ [\sustain, \line_id])
	}
}
