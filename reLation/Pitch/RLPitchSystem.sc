// reLation — a pitch system: its pitch classes, its pitches, and how they sound.
//
// A pitch class is a note once octaves are forgotten; a pitch is a note. Every system has its own
// coordinates for both and answers the same questions about them:
//
//   system              pitch class                      pitch
//   RLEDO(19)           an Integer, 0..18                an Integer, steps from the origin
//   RLJust([3, 5])      [e3, e5], exponents of 3 and 5   [e2, e3, e5], the exponent of 2 first
//
// Pitch classes form a group (pcZero, pcAdd, pcNeg, pcSub, pcScale): intervals between pitch classes are
// pitch classes too. Pitches lie on a line: height is a pitch's place on it in octaves, nearest(pc, height)
// the pitch of a class closest to a height. interval([5, 4]) is the system's pitch class for a just ratio,
// exact in RLJust, the nearest step in RLEDO (through its val, a number of steps for each prime).
//
//   ~edo = RLEDO(12);  ~just = RLJust([3, 5]);
//   ~edo.interval([3, 2]);                 // 7
//   ~just.interval([5, 3]);                // [-1, 1]: a fifth down, a major third up
//   ~just.cents(~just.interval([81, 80])); // 21.5
//   ~just.temperPc(~edo, [4, -1]);         // 0: 12 steps temper the syntonic comma out
//
// A system is a value: two systems with the same numbers are equal.

RLPitchSystem {

	// The prime factors of a positive Integer, as a Dictionary prime -> exponent.
	*factor { |n|
		var d = Dictionary.new, p = 2, m = n.asInteger.abs;
		while { m > 1 } {
			if((m % p) == 0) { d[p] = (d[p] ? 0) + 1; m = m.div(p) } { p = p + 1 };
		};
		^d
	}

	// A ratio as prime exponents, a Dictionary prime -> signed exponent: [5, 4] gives (5: 1, 2: -2).
	// A ratio is an Array [numerator, denominator] or a number (read as the nearest simple fraction).
	*monzo { |ratio|
		var num, den, d = Dictionary.new;
		#num, den = if(ratio.isKindOf(SequenceableCollection)) { ratio } { ratio.asFraction(1000, false) };
		this.factor(num).keysValuesDo { |p, e| d[p] = e };
		this.factor(den).keysValuesDo { |p, e| d[p] = (d[p] ? 0) - e };
		^d
	}

	// subclasses answer these
	pcZero { ^this.subclassResponsibility(thisMethod) }
	pcAdd { |a, b| ^this.subclassResponsibility(thisMethod) }
	pcNeg { |a| ^this.subclassResponsibility(thisMethod) }
	pcScale { |a, k| ^this.subclassResponsibility(thisMethod) }
	pc { |pitch| ^this.subclassResponsibility(thisMethod) }
	pcHeight { |pc| ^this.subclassResponsibility(thisMethod) }
	height { |pitch| ^this.subclassResponsibility(thisMethod) }
	nearest { |pc, height| ^this.subclassResponsibility(thisMethod) }
	interval { |ratio| ^this.subclassResponsibility(thisMethod) }
	pitchAdd { |a, b| ^this.subclassResponsibility(thisMethod) }
	pitchNeg { |a| ^this.subclassResponsibility(thisMethod) }
	octave { ^this.subclassResponsibility(thisMethod) }
	name { |pc| ^this.subclassResponsibility(thisMethod) }
	isFinite { ^this.subclassResponsibility(thisMethod) }

	pcSub { |a, b| ^this.pcAdd(a, this.pcNeg(b)) }
	pitchSub { |a, b| ^this.pitchAdd(a, this.pitchNeg(b)) }

	// The size of a pitch-class interval in cents, the shorter way round: in (-600, 600].
	cents { |pc|
		var c = this.pcHeight(pc) * 1200;
		^if(c > 600) { c - 1200 } { c }
	}

	// The size of an interval between two pitches, in cents.
	pitchCents { |interval| ^this.height(interval) * 1200 }

	// The frequency ratio of a pitch to the origin, and its frequency for an origin at base Hz.
	ratio { |pitch| ^2 ** this.height(pitch) }
	freq { |pitch, base = 261.6255653006| ^base * (2 ** this.height(pitch)) }

	// The pitch of a class at or above a height, within an octave.
	above { |pc, height| ^this.nearest(pc, height + 0.5) }
}
