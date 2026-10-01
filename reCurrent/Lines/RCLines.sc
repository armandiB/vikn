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
// plus, optionally, voice: an Integer pinning it to one voice of the batch (allocate).
// A point of the score space is [time, params] with params an Event.
//
// Generators (every one pure, seeded where it draws):
//   rails      lines joining two rails A(u), B(u): the string-art ruled surfaces of
//              Metastasis (affine rails, evenly spaced u: a hyperbolic paraboloid)
//   tangents   lines tangent to a curve at given touch times (the Xenakeur parabola)
//   projected  the tangent lines (or rulings) of an RCSurface in R^m, sampled and
//              projected on the score space by an RCProjection; move the projection
//              between cycles and the texture evolves
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
	*rails { |a, b, n = 8, us, minDur|
		us = us ?? { if(n <= 1) { [0] } { n.collect { |i| i / (n - 1) } } };
		^us.collect { |u, i| this.between(a.value(u), b.value(u), minDur, i) }.sort { |x, y| x[\onset] <= y[\onset] }
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

	// The tangent of `curve` at tau as [point, tangent] (a helper for extent functions and tests).
	*tangentAt { |curve, tau, eps = 1e-4|
		^[curve.value(tau), this.prDerivative(curve, tau, eps)]
	}

	//////// projected: the tangent lines of a surface in R^m, seen through a projection

	// For each sample [u, v] of `surface` (an RCSurface), a segment of R^m through S(u, v):
	// direction \u or \v (the parameter lines), a number (an angle in the tangent plane,
	// 0 = \u, pi/2 = \v), a Function (u, v, i) → angle, or \ruling (the whole v-line of the
	// point, from vRange[0] to vRange[1]: on a ruled surface, the ruling itself). `length`
	// is the segment's length in R^m (ignored by \ruling). Each end is projected by `frame`
	// (an RCProjection) to a point of the score space, and the line joins them.
	// timeMode: \window keeps the lines starting within [0, cycle), \wrap wraps their onsets
	// into the cycle, \sequence ignores the projected time and starts line i at
	// i * cycle / n, \none keeps every line as projected. Returns the lines in time order.
	*projected { |surface, frame, samples, direction = \ruling, length = 1, timeMode = \window, cycle, minDur|
		var lines = List.new;
		var n = samples.size;
		samples.do { |uv, i|
			var u = uv[0], v = uv[1];
			var x0, x1, t, line, keep = true;
			if(direction == \ruling) {
				x0 = surface.at(u, surface.vRange[0]);
				x1 = surface.at(u, surface.vRange[1]);
			} {
				var angle = case
					{ direction == \u } { 0 }
					{ direction == \v } { 0.5pi }
					{ direction.isKindOf(Function) } { direction.value(u, v, i) }
					{ direction };
				var tangent = surface.tangent(u, v, angle);
				var norm = tangent.squared.sum.sqrt;
				var point = surface.at(u, v);
				if(norm < 1e-9) {
					keep = false;
				} {
					tangent = tangent * (length / 2 / norm);
					x0 = point - tangent;
					x1 = point + tangent;
				};
			};
			if(keep) {
				line = this.between(frame.project(x0), frame.project(x1), minDur, i);
				line[\sample] = uv;
				line[\ends] = [x0, x1];   // the segment in R^m, for a picture of the surface
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
				this.line(cycle.rand, d, from, to, i)
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
	// nil; a key a line lacks is nil there), plus sustain (the line's length) and line_id.
	// A line starting less than minGap after the previous one of the voice is dropped
	// (warned): a zero-length hit would stall the loop. Returns nil for no lines.
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
