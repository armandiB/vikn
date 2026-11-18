// reLation — a pulse: the relation between time points a whole number of periods apart.
//
// Popoff and Yust's metrical relation M(d): two time points are related when the time between them is a whole
// multiple of d. It holds between the points of every pulse layer of period d, wherever the layer starts.
// M(0) relates a point to itself alone: the identity.
//
//   RLPulse(1/2) <> RLPulse(1/3) == RLPulse(1/6);   // composing two pulses: the pulse both fit in (their gcd)
//   RLPulse(1/2) & RLPulse(1/3) == RLPulse(1);      // what both hold: the pulse that fits in both (their lcm)
//   RLPulse(1) <= RLPulse(1/2);                     // true: included, 1 is a whole multiple of 1/2
//   RLPulse(1/2).relates(1/2, 3);                   // true: five halves apart
//   RLPulse.paths(6, 1/2).size;                     // 3: the ways of dividing six quarters down to eighths by
//                                                   // 2s and 3s (Cohn's ski-hill graph)
//
// Periods are plain numbers read as fractions of whole numbers (1/6, 0.25, 3): the nearest one with a
// denominator up to 10000 (a period under a ten-thousandth keeps a larger one). A period that is no such
// fraction is replaced by the nearest. A pulse is a value. RLTimePoints is the context where pulses label
// networks.

RLPulse {
	var <period, <>label;

	// the period is kept as the number its fraction gives, so that 1/6 and 0.5 / 3 are one pulse
	*new { |period = 0|
		var f = this.fraction(period);
		^super.newCopyArgs(f[0] / f[1])
	}

	// A period as [numerator, denominator] in lowest terms; its sign is dropped.
	*fraction { |x|
		var f = x.abs.asFraction(10000, false), g = f[0].gcd(f[1]).max(1);
		^[f[0].div(g), f[1].div(g)]
	}

	// The longest period that fits a whole number of times in two periods (gcd(0, x) is x), and the shortest
	// that both fit in (lcm(0, x) is 0). Of two fractions in lowest terms: the gcd of the numerators over the
	// lcm of the denominators, and the other way round (no product of a numerator by a denominator, which
	// passes 32 bits for a period like 21.4749).
	*gcd { |a, b|
		var fa = this.fraction(a), fb = this.fraction(b);
		^fa[0].gcd(fb[0]) / this.prLcm(fa[1], fb[1])
	}
	*lcm { |a, b|
		var fa = this.fraction(a), fb = this.fraction(b);
		^this.prLcm(fa[0], fb[0]) / fa[1].gcd(fb[1])
	}
	// the least common multiple of two whole numbers, as a Float: it can pass 32 bits
	*prLcm { |x, y| ^if((x == 0) or: { y == 0 }) { 0.0 } { (x / x.gcd(y)) * y } }

	// Whether two time points are a whole number of periods apart: what is left of the time between them,
	// once the nearest whole number of periods is taken away, is nothing (a billionth of that time, or of 1).
	relates { |x, y|
		var d = y - x, tolerance = 1e-9 * d.abs.max(1);
		^if(period == 0) { d.abs < tolerance } { (d - ((d / period).round * period)).abs < tolerance }
	}

	// This after that: two points are related when a point between them is related to both. The gcd.
	<> { |that| ^RLPulse(RLPulse.gcd(period, that.period)) }
	// What two pulses both hold: the lcm.
	& { |that| ^RLPulse(RLPulse.lcm(period, that.period)) }
	// Included in that: every two points this relates, that relates. This period is a whole multiple of
	// that one's; the identity is included in every pulse.
	<= { |that|
		^if(period == 0) { true } { if(that.period == 0) { false } { that.relates(0, period) } }
	}

	// what RLCategory and RLNetwork ask of an arrow
	follows { |that| ^true }
	sourceIdentity { ^RLPulse(0) }
	targetIdentity { ^RLPulse(0) }
	isIdentity { ^period == 0 }

	// The pulse of a time stretched by a.
	scaled { |a| ^RLPulse(period * a.abs) }

	// The time points of the layer of this period through `phase`, from `from` up to `to` (itself left out).
	points { |from = 0, to = 1, phase = 0|
		var first;
		if(period == 0) { ^if((phase >= from) and: { phase < to }) { [phase] } { [] } };
		first = phase + ((((from - phase) / period) - 1e-9).ceil * period);
		^Array.series((((to - first) / period) - 1e-9).ceil.max(0).asInteger, first, period).round(1e-10)
	}

	// Cohn's ski-hill graph between two periods, the top a whole multiple of the bottom: the periods that
	// divide the top and that the bottom divides, the longest first; and its paths, each a list of periods
	// from top to bottom, every step a division by a prime number (2 and 3 in the usual metres). A path is
	// a metre: 6, 3, 1 is six quarters in two beats of three, 6, 2, 1 in three beats of two.
	*skiHill { |top, bottom|
		var n = this.prRatio(top, bottom), f = this.fraction(bottom);
		^Array.series(n, n, -1).select { |k| (n % k) == 0 }.collect { |k| (f[0] * k) / f[1] }
	}
	*paths { |top, bottom|
		var n = this.prRatio(top, bottom), f = this.fraction(bottom), out = Array.new, walk;
		walk = { |k, path|
			if(k == 1) { out = out.add(path) } {
				k.factors.asSet.asArray.sort.do { |p| walk.value(k.div(p), path ++ [(f[0] * k.div(p)) / f[1]]) };
			};
		};
		walk.value(n, [(f[0] * n) / f[1]]);
		^out
	}
	*prRatio { |top, bottom|
		if((bottom <= 0) or: { top <= 0 } or: { (this.new(top) <= this.new(bottom)).not }) {
			Error("RLPulse: % is not a whole multiple of % (two periods above 0 are needed)".format(top, bottom)).throw
		};
		^(top / bottom).round.asInteger
	}

	// M and the period as a fraction: M1/6, M3, M0.
	name {
		var f = RLPulse.fraction(period);
		^"M" ++ if(f[1] == 1) { f[0].asString } { "%/%".format(f[0], f[1]) }
	}

	== { |that| ^that.isKindOf(RLPulse) and: { period == that.period } }
	!= { |that| ^(this == that).not }
	hash { ^[\RLPulse, period].hash }

	printOn { |stream| stream << "RLPulse(" << this.name.drop(1) << ")" }
}
