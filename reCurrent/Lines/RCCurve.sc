// reCurrent — a voice's curved course: one Env per coordinate over the note.
//
// A line (RCLines) glides each coordinate straight from its `from` value to its `to` value. A
// curved line also carries `path`: an Event key → Env whose times are fractions of the note
// (summing to 1) and whose curves are sclang's envelope curves (a number per segment, 0 linear); a
// key without an Env stays straight. A synth reads an Env at an index, the fraction of the line, from
// a control array of maxPoints breakpoints (<key>_env, IEnvGen's data), switched on by <key>_curved;
// through a gliding release the coordinate goes on along its end tangent (<key>_tail, units per line
// length). A warped index re-times the course (RCAttractor's time mode).
//
//   ~env = RCCurve.fit({ |s| s.squared });       // a curve of s in 0..1 → at most 8 breakpoints
//   RCCurve.at(~env, 0.5);                       // ≈ 0.25
//   RCCurve.serverArray(~env);                   // the 32 values of <key>_env
//   RCCurve.lineFrom([[0, (pitch: 0)], [1, (pitch: 0.2)], [2, (pitch: 1)]], 3);   // a line with its path
//
// In a SynthDef: RCCurve.course(\pitch, pitch0, pitch1, index) (an index 0 → 1 over the line, on past
// 1 through a gliding release) or RCCurve.glide(\pitch, pitch0, pitch1, dur, trig) (restarted by a
// trigger); in a template: RCCurve.eventControls(ev, path, keys, lineDur).
// The fit, slice and server array are the Texture rig's (HadronLake3), moved here to be shared.

RCCurve {
	classvar <>maxPoints = 8;      // breakpoints (7 segments): the synths' control arrays
	classvar <>tolerance = 0.02;   // the fit's error, as a fraction of the curve's excursion
	classvar <>numSamples = 32;    // samples of a Function fitted

	//////// paths

	// An Env from levels, times (any unit: scaled to fractions of 1) and curves (numbers, 0 linear).
	*env { |levels, times, curves|
		var sum = times.sum.max(1e-9);
		^Env(levels, times.collect(_ / sum), (curves ?? { 0 ! times.size }).collect { |c| if(c.isNumber) { c } { 0 } })
	}

	// The value of a path at s in 0..1 (nil path: the straight line from a to b).
	*at { |env, s, a, b| ^if(env.isNil) { a + ((b - a) * s) } { env.at(s.clip(0, 1)) } }

	// One envelope segment's value at pos in 0..1 by sclang's curve rule (a number: 0 linear).
	*curveAt { |a, b, c, pos|
		^if(c.abs < 1e-3) { a + ((b - a) * pos) } { a + ((b - a) * ((1 - (c * pos).exp) / (1 - c.exp))) }
	}

	// The curvature that takes a segment from a to b through the sampled midpoint m: f = (m - a) / (b - a)
	// in (0, 1) gives c = 2 ln(1 / f - 1) (f 0.5: linear); nil when no monotone curve passes there.
	*curveThrough { |a, b, m|
		var f;
		if((b - a).abs < 1e-9) { ^if((m - a).abs < 1e-9) { 0 } { nil } };
		f = (m - a) / (b - a);
		if(f <= 1e-4 or: { f >= (1 - 1e-4) }) { ^nil };
		^if((f - 0.5).abs < 1e-4) { 0 } { 2 * ((1 / f) - 1).log }
	}

	// Fit a curve (a Function of s in 0..1, or an Array of samples equally spaced in s) into at most n
	// breakpoints: start with the two ends, give every segment the curvature through its midpoint, and
	// split the worst segment while its error exceeds tol × the excursion and breakpoints remain.
	// Returns an Env (times as fractions), or nil for a flat curve (a constant will do).
	*fit { |curve, n, tol, samples|
		var vals, m, span, epsilon, cuts, pass;
		n = n ? maxPoints;
		tol = tol ? tolerance;
		m = samples ? numSamples;
		vals = if(curve.isKindOf(SequenceableCollection)) { m = curve.size - 1; curve } { (m + 1).collect { |i| curve.value(i / m) } };
		span = vals.maxItem - vals.minItem;
		if(span < 1e-9) { ^nil };
		epsilon = tol * span;
		cuts = [0, m];   // sample indices of the breakpoints
		pass = {   // every segment's [curvature, error, from, to] (doAdjacentPairs returns the receiver: collect by hand)
			var segs = List.new;
			cuts.doAdjacentPairs { |i, j|
				var a = vals[i], b = vals[j], c = this.curveThrough(a, b, vals[(i + j) div: 2]), err = 0;
				if(c.isNil) { err = inf } { (i..j).do { |k| err = err.max((this.curveAt(a, b, c, (k - i) / (j - i)) - vals[k]).abs) } };
				segs.add([c ? 0, err, i, j]);
			};
			segs.asArray
		};
		block { |break|
			while { cuts.size < n } {
				var segs = pass.value, worst = segs.maxItem { |s| s[1] };
				if(worst[1] <= epsilon or: { (worst[3] - worst[2]) < 2 }) { break.value };
				cuts = (cuts ++ [(worst[2] + worst[3]) div: 2]).sort;
			};
		};
		^Env(cuts.collect { |i| vals[i] }, cuts.differentiate.drop(1).collect(_ / m), pass.value.collect(_[0]))
	}

	// A path's part between s0 and s1 (times renormalised); cut: the part before s. A segment cut keeps
	// its shape: the part of a segment of curve c between its positions p0 and p1 (fractions of the
	// segment) is the segment of curve c (p1 - p0) between the values there.
	*slice { |env, s0 = 0, s1 = 1|
		var times = env.times, curves = env.curves.asArray.wrapExtend(times.size);
		var cum = [0] ++ times.integrate;
		var bounds = [s0] ++ cum.select { |t| (t > (s0 + 1e-9)) and: { t < (s1 - 1e-9) } } ++ [s1];
		var outT = List.new, outC = List.new;
		bounds.doAdjacentPairs { |a, b|
			var mid = (a + b) / 2, k = 0, c;
			while { (k < (times.size - 1)) and: { cum[k + 1] <= mid } } { k = k + 1 };
			c = curves[k];
			if(c.isNumber.not) { c = 0 };
			outT.add(b - a);
			outC.add(if(times[k] > 1e-9) { c * (b - a) / times[k] } { c });
		};
		^this.env(bounds.collect { |s| env.at(s) }, outT.asArray.collect(_.max(1e-6)), outC.asArray)
	}

	*cut { |env, s| ^this.slice(env, 0, s) }

	// A path's breakpoints past a wall mirrored back inside (reflect) or held at it (clip).
	*reflect { |env, lo, hi| ^Env(env.levels.collect { |l| RCLaws.reflect(l, lo, hi) }, env.times, env.curves) }
	*clip { |env, lo, hi| ^Env(env.levels.collect(_.clip(lo, hi)), env.times, env.curves) }

	// The path with every level through x → offset + scale * x: an affine map keeps each segment's
	// shape exactly (a coupling to another coordinate, a gain in dB).
	*mapLevels { |env, scale = 1, offset = 0| ^Env(env.levels.collect { |l| offset + (scale * l) }, env.times, env.curves) }

	// The highest level (each segment is monotone: its extremes are its ends).
	*peak { |env| ^env.levels.maxItem }

	// The slope at the end, in units per unit of s (per line length): the tangent a gliding release
	// goes on along. A padded segment of no length at the end is skipped.
	*endSlope { |env|
		var levels = env.levels, times = env.times, curves = env.curves.asArray.wrapExtend(times.size);
		var i = times.size - 1, a, b, c, d;
		while { (i > 0) and: { times[i] <= 1e-9 } } { i = i - 1 };
		a = levels[i];
		b = levels[i + 1];
		d = times[i].max(1e-9);
		c = curves[i];
		if(c.isNumber.not) { c = 0 };
		^if(c.abs < 1e-3) { (b - a) / d } { (b - a) * (c.neg * c.exp) / (1 - c.exp) / d }
	}

	// Whether a path bends: more than two breakpoints, or one curved segment.
	*isCurved { |env| ^env.notNil and: { (env.levels.size > 2) or: { (env.curves.asArray.first ? 0).abs >= 1e-3 } } }

	// The control array a synth plays, IEnvGen's raw data read at an index (the fraction of the line):
	// [offset, first level, segments, total, (time, shape 5, curve, level) per segment], the path's
	// times as fractions (a hand-made Env's normalised), padded to n - 1 segments by flat ones of length
	// 1 past the end (an index beyond 1 holds the last level). A path with more breakpoints is refitted.
	// lineDur no longer matters (the index carries the line's time); it stays for the callers.
	*serverArray { |env, lineDur = 1, n|
		var max = n ? maxPoints, segs, pad, times, curves, last;
		if(env.levels.size > max) { env = this.fit({ |s| env.at(s) }, max) ? Env([env.levels.first, env.levels.first], [1]) };
		segs = env.times.size;
		pad = (max - 1 - segs).max(0);
		times = env.times / env.times.sum.max(1e-9);
		curves = env.curves.asArray.wrapExtend(segs).collect { |c| if(c.isNumber) { c } { 0 } };
		last = env.levels.last;
		^[0, env.levels.first, segs + pad, 1 + pad]
			++ segs.collect { |i| [times[i], 5, curves[i], env.levels[i + 1]] }.flatten
			++ pad.collect { [1, 5, 0, last] }.flatten
	}

	// The array of a flat path at 0: the controls' default.
	*prBlankArray { ^this.serverArray(Env([0, 0], [1])) }

	//////// lines

	// A line from time-ordered samples [[time, params], ...] (a voice's course sampled along it): from
	// the first sample to the last, every key whose values bend fitted into its path (resampled evenly
	// in time first), a straight key carrying none. A course shorter than minDur gives the straight
	// line between its ends, stretched to minDur (RCLines.between's chirp). nil for fewer than two samples.
	*lineFrom { |samples, id, minDur, n, tol|
		var t0, dur, keys, from, to, path, line;
		samples = (samples ? []).select { |p| p.notNil and: { p[0].isNumber } };
		if(samples.size < 2) { ^nil };
		t0 = samples.first[0];
		dur = samples.last[0] - t0;
		keys = IdentitySet.new;
		samples.do { |p| p[1] !? { |e| e.keysDo { |k| keys.add(k) } } };
		from = ();
		to = ();
		keys.do { |k| from[k] = samples.first[1][k]; to[k] = samples.last[1][k] };
		minDur = minDur ? RCLines.defaultMinDur;
		if(dur < minDur) { ^RCLines.line(t0, minDur, from, to, id) };
		path = ();
		keys.do { |k|
			var vals = this.prResample(samples, k, t0, dur, numSamples);
			var env = vals !? { this.fit(vals, n, tol) };
			if(this.isCurved(env)) { path[k] = env };
		};
		line = RCLines.line(t0, dur, from, to, id);
		if(path.size > 0) { line[\path] = path };
		^line
	}

	// The values of one key at m + 1 times evenly spaced over the samples (linear between them), or nil
	// when a sample lacks the key.
	*prResample { |samples, key, t0, dur, m|
		var j = 0, last = samples.size - 1;
		if(samples.any { |p| p[1].isNil or: { p[1][key].isNumber.not } }) { ^nil };
		^(m + 1).collect { |i|
			var t = t0 + (dur * i / m), a, b, u;
			while { (j < (last - 1)) and: { samples[j + 1][0] < t } } { j = j + 1 };
			a = samples[j];
			b = samples[(j + 1).min(last)];
			u = if((b[0] - a[0]).abs < 1e-12) { 0 } { ((t - a[0]) / (b[0] - a[0])).clip(0, 1) };
			a[1][key] + ((b[1][key] - a[1][key]) * u)
		}
	}

	//////// synth side (call inside a SynthDef function)

	// The controls of a curved coordinate: <key>_env (maxPoints breakpoints, IEnvGen's data, read at
	// the fraction of the line), <key>_curved (1 to follow it), <key>_tail (the end slope, units per
	// line length).
	*controls { |key|
		^(
			env: NamedControl.kr((key ++ "_env").asSymbol, this.prBlankArray),
			curved: NamedControl.kr((key ++ "_curved").asSymbol, 0),
			tail: NamedControl.kr((key ++ "_tail").asSymbol, 0)
		)
	}

	// A coordinate along its line at `index` (0 at the note, 1 at the line's end, beyond it through a
	// gliding release; a warped index re-times the whole course): straight from a to b, or the path
	// read at the index, then its end tangent.
	*course { |key, a, b, index|
		var c = this.controls(key);
		^Select.kr(c[\curved], [a + ((b - a) * index), IEnvGen.kr(c[\env], index.clip(0, 1)) + (c[\tail] * (index - 1).max(0))])
	}

	// A coordinate gliding from a to b over dur seconds, started again by trig (a continuous grain's
	// next target on a running synth), or its path read at the glide's index from the start; the
	// straight branch is the EnvGen line it always was.
	*glide { |key, a, b, dur, trig = 1|
		var env = NamedControl.kr((key ++ "_env").asSymbol, this.prBlankArray);
		var curved = NamedControl.kr((key ++ "_curved").asSymbol, 0);
		^Select.kr(curved, [EnvGen.kr(Env([a, b], [dur]), trig), IEnvGen.kr(env, this.glideIndex(dur, trig))])
	}

	// How far a glide is (0 → 1 over dur seconds, held at 1), started again by trig.
	*glideIndex { |dur, trig = 1| ^Sweep.kr(trig, dur.max(1e-4).reciprocal).min(1) }

	//////// template side

	// Set an event's path controls for `keys` from a hit's path (an Event key → Env, or anything else for
	// none): a curved key gets <key>_env (its server array, wrapped: one control array, not one synth
	// per value) and <key>_curved 1, plus with withTails <key>_tail (the end slope) and <key>_peak (the
	// highest level); every other key <key>_curved 0 (a straight line after a curved one on the same
	// synth must turn its envelope off). Returns the event.
	*eventControls { |ev, path, keys, lineDur = 1, withTails = true|
		(keys ? []).do { |k|
			var env = if(path.isKindOf(Dictionary)) { path[k] };
			if(env.isKindOf(Env)) {
				ev[(k ++ "_env").asSymbol] = [this.serverArray(env, lineDur)];
				ev[(k ++ "_curved").asSymbol] = 1;
				if(withTails) {
					ev[(k ++ "_tail").asSymbol] = this.endSlope(env);
					ev[(k ++ "_peak").asSymbol] = this.peak(env);
				};
			} {
				ev[(k ++ "_curved").asSymbol] = 0;
			};
		};
		^ev
	}
}
