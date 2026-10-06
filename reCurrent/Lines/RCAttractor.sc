// reCurrent — a discrete set a coordinate is drawn to (an attractor): a course lingers near the set's
// values and passes quickly between them, from even glides (amount 0) to nearly steps (amount 1).
//
//   ~a = RCAttractor.scale(Scale.major);                     // degrees in octaves, repeating every octave
//   ~a.at(0.37, 0.6);                                        // the warped value of 0.37
//   RCAttractor.edo(19); RCAttractor.sieve(~sieve); RCAttractor.values([0, 0.25, 0.7]);
//   RCAttractor.scale(Scale.major, weights: [3, 1, 1, 1, 2, 1, 1]);   // the tonic, then the fifth, hold longest
//
// The warp: for consecutive attractors a < b with weights wa and wb, a value x between them becomes
//   a + (b - a) S(u),  u = (x - a) / (b - a),  S(u) = (tanh(s (u - m)) + tanh(s m)) / (tanh(s (1 - m)) + tanh(s m))
// with m = wa / (wa + wb) and the strength s = 40^amount - 1. It is monotone and fixes every
// attractor; as s grows it flattens near the attractors and steepens between them, so an evenly
// moving x lingers near each value; the switch between two neighbours moves towards the lighter one
// (a weight of 0 never holds).
// Value mode: a coordinate is warped (its course, straight or curved, through a gliding release too).
// Time mode: a line's index is warped (RCCurve.course reads every coordinate at it): the voice slows
// near the set and keeps its ends. A straight driving key warps through the set itself (progressAt),
// a curved one through the progress values where it crosses the attractors (crossings).
// In a SynthDef: RCAttractor.kr(\pitch, x) (a coordinate's value warp), RCAttractor.progress(index)
// (the time warp of a line's index); in a template: RCAttractor.eventControls(ev, spec, keys).

RCAttractor {
	classvar <>maxDegrees = 32;     // values per period a synth holds (its LocalBuf has a period on each side too)
	classvar <>maxCrossings = 64;   // progress values a curved line's time warp holds (its ends included)
	var <degrees, <period, <root, <weights, <>name;

	// degrees: the values (of one period from root when period > 0, reduced into it; absolute values
	// otherwise); weights: one per degree, in the degrees' order (1 by default).
	*new { |degrees, period = 1, root = 0, weights, name|
		^super.new.init(degrees, period, root, weights, name)
	}

	init { |inDegrees, inPeriod, inRoot, inWeights, inName|
		var pairs;
		period = (inPeriod ? 0).asFloat.max(0);
		root = (inRoot ? 0).asFloat;
		name = inName;
		pairs = (inDegrees ? []).asArray.collect { |d, i| [d.asFloat, (inWeights !? { inWeights.wrapAt(i) } ? 1).asFloat.max(0)] };
		if(period > 0) { pairs = pairs.collect { |p| var r = p[0].mod(period); [if((period - r) < 1e-9) { 0.0 } { r }, p[1]] } };
		pairs = pairs.sort { |a, b| a[0] <= b[0] };
		pairs = pairs.inject([]) { |acc, p| if(acc.notEmpty and: { (acc.last[0] - p[0]).abs < 1e-9 }) { acc } { acc.add(p) } };   // a value once
		degrees = pairs.collect(_[0]);
		weights = pairs.collect(_[1]);
	}

	//////// sets

	*periodic { |degrees, period = 1, root = 0, weights, name| ^this.new(degrees, period, root, weights, name) }

	// Absolute values, not repeating: outside their span a value is left as it is.
	*values { |values, weights, name| ^this.new(values, 0, 0, weights, name) }

	// n equal divisions of the period (an octave by default) from root.
	*edo { |n = 12, root = 0, period = 1, weights|
		var k = n.asInteger.max(1);
		^this.new((0..(k - 1)) * (period / k), period, root, weights, ("edo" ++ k).asSymbol)
	}

	// A Scale, a Tuning, or a Symbol naming one (RCSieve.tuningSteps: its degrees in octaves, its period
	// the octave ratio's log2); an Array of values gives a set that does not repeat.
	*scale { |scale, root = 0, weights|
		var t = RCSieve.tuningSteps(scale);
		if(t.isNil) { RCLog.warn(\attractor, "% is no scale (no attractor)".format(scale)); ^nil };
		^this.new(t[0], t[1] ? 0, if(t[1].isNil) { 0 } { root }, weights, if(scale.respondsTo(\name)) { scale.name } { scale })
	}

	// An RCSieve: its points over one period from its zero (the formula's period times its unit, or, for
	// a tuned sieve, the period after which formula and tuning repeat together); a tuned sieve on a
	// fixed field gives its points, not repeating.
	*sieve { |sieve, weights, name|
		var steps, valuePeriod, lo;
		if(sieve.isTuned) {
			steps = sieve.tuningSteps.size;
			if(sieve.tuningPeriod.isNil) {
				^this.values(sieve.points(sieve.zero + sieve.tuningSteps.first - 1e-9, sieve.zero + sieve.tuningSteps.last + 1e-9), weights, name ? sieve.name)
			};
			valuePeriod = (sieve.period lcm: steps) / steps * sieve.tuningPeriod;
		} {
			valuePeriod = sieve.period * sieve.unit;
		};
		lo = sieve.zero;
		^this.new(sieve.points(lo, lo + valuePeriod - 1e-9) - lo, valuePeriod, lo, weights, name ? sieve.name)
	}

	// An attractor from what a spec names: an RCAttractor, an RCSieve, a Scale, a Tuning or a Symbol
	// naming one, an Integer (n equal divisions of the octave), an Array (values); nil otherwise.
	*from { |thing|
		^case
		{ thing.isKindOf(RCAttractor) } { thing }
		{ thing.isKindOf(RCSieve) } { this.sieve(thing) }
		{ thing.isKindOf(Scale) or: { thing.isKindOf(Tuning) } or: { thing.isKindOf(Symbol) } } { this.scale(thing) }
		{ thing.isKindOf(Integer) } { this.edo(thing) }
		{ thing.isKindOf(SequenceableCollection) } { this.values(thing) }
		{ nil }
	}

	isPeriodic { ^period > 0 }

	weights_ { |w| weights = degrees.collect { |d, i| (w !? { w.wrapAt(i) } ? 1).asFloat.max(0) } }

	// The same set moved by `by` (its root, or every value when it does not repeat).
	transpose { |by = 0|
		^if(period > 0) { this.class.new(degrees, period, root + by, weights, name) } { this.class.new(degrees + by, 0, 0, weights, name) }
	}

	//////// the warp (language side)

	*strength { |amount| ^(40 ** amount.clip(0, 1)) - 1 }

	*shape { |u, s, m|
		var t;
		if(s < 1e-3) { ^u };
		t = (s * m).tanh;
		^((s * (u - m)).tanh + t) / ((s * (1 - m)).tanh + t)
	}

	*mid { |wa, wb| ^if((wa + wb) <= 0) { 0.5 } { wa / (wa + wb) } }

	// The attractors around x, [a, b, wa, wb] in absolute values (x on an attractor: a = x); nil for an
	// empty set, or outside a set that does not repeat (or holds fewer than two values).
	neighbours { |x|
		var n = degrees.size, f, k, r, i, base;
		if(n == 0) { ^nil };
		if(period > 0) {
			f = x - root;
			k = (f / period).floor;
			r = f - (k * period);
			i = degrees.indexOfGreaterThan(r) ? n;
			base = root + (k * period);
			^[
				base + if(i == 0) { degrees.last - period } { degrees[i - 1] },
				base + if(i == n) { degrees.first + period } { degrees[i] },
				weights[(i - 1).mod(n)],
				weights[i.mod(n)]
			]
		};
		if(n < 2 or: { x < degrees.first } or: { x > degrees.last }) { ^nil };
		i = (degrees.indexOfGreaterThan(x) ? (n - 1)).clip(1, n - 1);
		^[degrees[i - 1], degrees[i], weights[i - 1], weights[i]]
	}

	// x warped by the set at amount (0 leaves it as it is).
	at { |x, amount = 0.5|
		var nb = this.neighbours(x), s = this.class.strength(amount), u;
		if(nb.isNil or: { s < 1e-3 }) { ^x };
		u = ((x - nb[0]) / (nb[1] - nb[0]).max(1e-12)).clip(0, 1);
		^nb[0] + ((nb[1] - nb[0]) * this.class.shape(u, s, this.class.mid(nb[2], nb[3])))
	}

	// The attractor nearest x (weights aside).
	nearest { |x|
		var nb = this.neighbours(x);
		if(nb.isNil) { ^if(degrees.isEmpty) { x } { degrees.minItem { |d| (d - x).abs } } };
		^if((x - nb[0]) <= (nb[1] - x)) { nb[0] } { nb[1] }
	}

	// The attractors within [lo, hi], ascending: values, or [value, weight] pairs with withWeights.
	valuesIn { |lo, hi, withWeights = false|
		var out = List.new;
		if(period > 0) {
			(((lo - root) / period).floor.asInteger - 1 .. ((hi - root) / period).ceil.asInteger + 1).do { |k|
				degrees.do { |d, i| var v = root + (k * period) + d; if(v >= (lo - 1e-12) and: { v <= (hi + 1e-12) }) { out.add([v, weights[i]]) } };
			};
		} {
			degrees.do { |d, i| if(d >= (lo - 1e-12) and: { d <= (hi + 1e-12) }) { out.add([d, weights[i]]) } };
		};
		out = out.asArray.sort { |a, b| a[0] <= b[0] };
		^if(withWeights) { out } { out.collect(_[0]) }
	}

	// The part of the set a course between lo and hi reaches, with the attractor beyond each side, as
	// values that do not repeat: a periodic set too large for a synth's buffer, cut to a line's reach.
	withinRange { |lo, hi|
		var a = lo.min(hi), b = lo.max(hi);
		var vw = this.valuesIn(a, b, true), below = this.neighbours(a - 1e-9), above = this.neighbours(b + 1e-9);
		below !? { vw = [[below[0], below[2]]] ++ vw };
		above !? { vw = vw ++ [[above[1], above[3]]] };
		^this.class.values(vw.collect(_[0]), vw.collect(_[1]), name)
	}

	//////// the time mode (language side)

	// The warped index of a line whose driving coordinate runs straight from x0 to x1: how far W has
	// gone from W(x0) to W(x1) at x0 + (x1 - x0) index. An index outside 0..1 (a release) is left as
	// it is, and so is every index when W(x0) = W(x1) (a line within one plateau).
	progressAt { |index, x0, x1, amount = 0.5|
		var w0 = this.at(x0, amount), w1 = this.at(x1, amount);
		if(index >= 1 or: { index <= 0 } or: { (w1 - w0).abs < 1e-9 }) { ^index };
		^(this.at(x0 + ((x1 - x0) * index), amount) - w0) / (w1 - w0)
	}

	// The progress values where a curved course (an Env over the line, in the coordinate's units)
	// crosses the attractors, with their weights, between the ends weighted 0: [[u, w], ...] from
	// [0, 0] to [1, 0], an attractor over the line for the time warp of a curved course. Found on
	// `samples` intervals, then by bisection; at most maxCrossings (warned past that).
	crossings { |env, samples = 64|
		var us = (0..samples) / samples, vals = us.collect { |u| env.at(u) }, out = List[[0.0, 0.0]], res;
		samples.do { |j|
			var v0 = vals[j], v1 = vals[j + 1];
			this.valuesIn(v0.min(v1), v0.max(v1), true).do { |aw|
				var a = aw[0], u0 = us[j], u1 = us[j + 1], f0 = v0 - a;
				if(f0.abs < 1e-12) { out.add([u0, aw[1]]) } {
					30.do {
						var um = (u0 + u1) / 2, fm = env.at(um) - a;
						if((fm >= 0) == (f0 >= 0)) { u0 = um; f0 = fm } { u1 = um };
					};
					out.add([(u0 + u1) / 2, aw[1]]);
				};
			};
		};
		out.add([1.0, 0.0]);
		res = out.asArray.sort { |a, b| a[0] <= b[0] };
		// one point per place, the heavier kept (a course starting on an attractor holds there)
		res = res.inject([]) { |acc, p| if(acc.notEmpty and: { (p[0] - acc.last[0]) < 1e-6 }) { acc.last[1] = acc.last[1].max(p[1]); acc } { acc.add(p.copy) } };
		if(res.size > maxCrossings) {
			RCLog.warn(\attractor, "a course crosses % attractors: the % nearest its start are kept".format(res.size - 2, maxCrossings - 2));
			res = res.keep(maxCrossings - 1) ++ [[1.0, 0.0]];
		};
		^res
	}

	//////// synth side (call inside a SynthDef function)

	// The controls of a key's attractor: <key>_astr (the strength, 0: none), <key>_aset (what a LocalBuf
	// holds: a periodic set's degrees with a period on each side, else the values; padded high),
	// <key>_aweights (aligned), <key>_an (how many), <key>_aperiod (0: not periodic), <key>_aroot.
	*controls { |key, size|
		var k = key.asString, n = size ? (maxDegrees + 2);
		^(
			str: NamedControl.kr((k ++ "_astr").asSymbol, 0),
			set: NamedControl.kr((k ++ "_aset").asSymbol, 1e9 ! n),
			weights: NamedControl.kr((k ++ "_aweights").asSymbol, 1 ! n),
			n: NamedControl.kr((k ++ "_an").asSymbol, 0),
			period: NamedControl.kr((k ++ "_aperiod").asSymbol, 0),
			root: NamedControl.kr((k ++ "_aroot").asSymbol, 0)
		)
	}

	// x warped by the attractor in the controls c (a buffer of `size` values, filled when the synth
	// starts): x as it is when the strength is 0, the set holds fewer than two values, or x lies outside
	// a set that does not repeat.
	*prWarp { |c, x, size|
		var n = size ? (maxDegrees + 2);
		var buf = LocalBuf(n), wbuf = LocalBuf(n);
		var fill = [SetBuf(buf, c[\set]), SetBuf(wbuf, c[\weights])];   // before anything reads them
		var periodic = c[\period] > 0;
		var f = x - c[\root];
		var k = Select.kr(periodic, [0, (f / c[\period].max(1e-9)).floor]);
		var r = f - (k * c[\period]);
		var last = (c[\n] - 1).max(1);
		var i0 = IndexInBetween.kr(buf, r).floor.clip(0, last - 1);
		var a = Index.kr(buf, i0), b = Index.kr(buf, i0 + 1);
		var wa = Index.kr(wbuf, i0), wb = Index.kr(wbuf, i0 + 1);
		var u = ((r - a) / (b - a).max(1e-12)).clip(0, 1);
		var m = Select.kr((wa + wb) > 0, [0.5, wa / (wa + wb).max(1e-12)]);
		var s = c[\str].max(1e-3), t = (s * m).tanh;
		var shape = ((s * (u - m)).tanh + t) / ((s * (1 - m)).tanh + t);
		var inside = (periodic + ((r >= Index.kr(buf, 0)) * (r <= Index.kr(buf, last)))).min(1);
		var on = (c[\str] > 1e-3) * (c[\n] >= 2) * inside;
		^Select.kr(on, [x, c[\root] + (k * c[\period]) + a + ((b - a) * shape)])
	}

	// A coordinate's value warped by its attractor (value mode).
	*kr { |key, x| ^this.prWarp(this.controls(key), x) }

	// The time warp of a line's index (0 → 1 over the line, left as it is past 1): the controls
	// progress_* hold the driving course's set, its ends progress_x0 and _x1 and W at them, _aw0 and
	// _aw1 (a curved course: its crossings, x0 0, x1 1, W 0 and 1). The index as it is when the strength is 0.
	*progress { |index|
		var size = maxCrossings + 2;
		var c = this.controls(\progress, size);
		var x0 = NamedControl.kr(\progress_x0, 0), x1 = NamedControl.kr(\progress_x1, 1);
		var w0 = NamedControl.kr(\progress_aw0, 0), w1 = NamedControl.kr(\progress_aw1, 1);
		var span = w1 - w0;
		var w = this.prWarp(c, x0 + ((x1 - x0) * index.clip(0, 1)), size);
		var warped = ((w - w0) / Select.kr(span.abs > 1e-9, [1, span])).clip(0, 1);
		^Select.kr((c[\str] > 1e-3) * (span.abs > 1e-9) * (index < 1), [index, warped])
	}

	//////// template side

	// The buffer contents of a set for a synth: [values, weights, count, the set used], a periodic set
	// with a period on each side, padded to size (values high, weights 1). A periodic set too large is
	// cut to [lo, hi] (withinRange: values that do not repeat), a non-repeating one past size to its
	// first values (warned).
	*prBuffer { |set, size, lo, hi|
		var vals, wts, d, w;
		if(set.isPeriodic and: { set.degrees.size > (size - 2) }) { set = set.withinRange(lo ? 0, hi ? 0) };
		d = set.degrees;
		w = set.weights;
		if(set.isPeriodic) {
			vals = [d.last - set.period] ++ d ++ [d.first + set.period];
			wts = [w.last] ++ w ++ [w.first];
		} { vals = d; wts = w };
		if(vals.size > size) {
			RCLog.warn(\attractor, "% values for a buffer of %: the first kept".format(vals.size, size));
			vals = vals.keep(size);
			wts = wts.keep(size);
		};
		^[vals ++ (1e9 ! (size - vals.size)), wts ++ (1 ! (size - wts.size)), vals.size, set]
	}

	*prSetControls { |ev, key, set, strength, size, lo, hi|
		var b = this.prBuffer(set, size, lo, hi), k = key.asString, used = b[3];
		ev[(k ++ "_astr").asSymbol] = strength;
		ev[(k ++ "_aset").asSymbol] = [b[0]];
		ev[(k ++ "_aweights").asSymbol] = [b[1]];
		ev[(k ++ "_an").asSymbol] = b[2];
		ev[(k ++ "_aperiod").asSymbol] = used.period;
		ev[(k ++ "_aroot").asSymbol] = if(used.isPeriodic) { used.root } { 0 };
	}

	// Set an event's attraction controls from a hit's spec (an Event key → (set:, amount:, mode: \value
	// | \time | \both, weights:), with time_key: the key whose course drives the time mode, \pitch by
	// default; set: anything *from takes) for `keys`: a key in the value mode gets its set and strength,
	// every other key a strength 0; the time key's course (the hit's <key>0, <key>1, and its path's Env
	// when curved) gives the progress controls, straight or through its crossings. Returns the variant
	// the hit needs: nil (no attraction), \A (at most the pitch warped), \AA (another key warped).
	*eventControls { |ev, spec, keys|
		var valueKeys = List.new, timeKey, timeSpec, timeSet;
		(keys ? []).do { |k| ev[(k ++ "_astr").asSymbol] = 0 };
		ev[\progress_astr] = 0;
		if(spec.isKindOf(Dictionary).not) { ^nil };
		(keys ? []).do { |k|
			var s = spec[k], set, amount, mode, ends;
			if(s.isKindOf(Dictionary)) {
				set = this.from(s[\set]);
				amount = (s[\amount] ? 0.5).clip(0, 1);
				mode = s[\mode] ? \value;
				s[\weights] !? { |w| set = set !? { set.copy.weights_(w) } };
				if(set.notNil and: { amount > 0 } and: { set.degrees.size > 0 }) {
					ends = this.prRange(ev, k);
					if([\value, \both].includes(mode)) { this.prSetControls(ev, k, set, this.strength(amount), maxDegrees + 2, ends[0], ends[1]); valueKeys.add(k) };
					if([\time, \both].includes(mode) and: { timeKey.isNil or: { k == (spec[\time_key] ? \pitch) } }) { timeKey = k; timeSpec = amount; timeSet = set };
				};
			};
		};
		timeKey !? { this.prTimeControls(ev, timeKey, timeSet, timeSpec) };
		if(valueKeys.isEmpty and: { timeKey.isNil }) { ^nil };
		^if(valueKeys.every { |k| k == \pitch }) { \A } { \AA }
	}

	// The span a hit's course of key covers: its ends, and its path's levels when curved.
	*prRange { |ev, key|
		var a = ev[(key ++ "0").asSymbol] ? 0, b = ev[(key ++ "1").asSymbol] ? a, env = this.prPathEnv(ev, key);
		var vals = [a, b] ++ (env !? { env.levels } ? []);
		^[vals.minItem, vals.maxItem]
	}

	*prPathEnv { |ev, key| ^if(ev[\path].isKindOf(Dictionary)) { ev[\path][key] !? { |e| if(e.isKindOf(Env)) { e } } } }

	*prTimeControls { |ev, key, set, amount|
		var env = this.prPathEnv(ev, key), x0, x1, s = this.strength(amount), cr;
		if(env.notNil) {
			cr = set.crossings(env);
			this.prSetControls(ev, \progress, this.values(cr.collect(_[0]), cr.collect(_[1])), s, maxCrossings + 2);
			ev[\progress_x0] = 0;
			ev[\progress_x1] = 1;
			ev[\progress_aw0] = 0;
			ev[\progress_aw1] = 1;
		} {
			x0 = ev[(key ++ "0").asSymbol] ? 0;
			x1 = ev[(key ++ "1").asSymbol] ? x0;
			this.prSetControls(ev, \progress, set, s, maxCrossings + 2, x0, x1);
			ev[\progress_x0] = x0;
			ev[\progress_x1] = x1;
			ev[\progress_aw0] = set.at(x0, amount);
			ev[\progress_aw1] = set.at(x1, amount);
		};
	}

	copy { ^this.class.new(degrees, period, root, weights, name) }

	== { |other| ^other.isKindOf(RCAttractor) and: { degrees == other.degrees } and: { period == other.period } and: { root == other.root } and: { weights == other.weights } }
	!= { |other| ^(this == other).not }
	hash { ^[degrees, period, root, weights].hash }

	printOn { |stream|
		stream << "RCAttractor(" << (name ? "set") << ", " << degrees.size << " values";
		if(period > 0) { stream << " every " << period << " from " << root };
		stream << ")"
	}
}
