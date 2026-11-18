// reLation — just intonation as a lattice: a pitch class is an Array of exponents, one for each of the
// system's odd primes; a pitch has the exponent of 2 in front.
//
//   ~just = RLJust([3, 5]);
//   ~just.interval([3, 2]);          // [1, 0]: one step along 3
//   ~just.interval([6, 5]);          // [1, -1]: a fifth up, a major third down
//   ~just.name([-1, 1]);             // 5/3
//   ~just.cents([0, -3]);            // 41.06: three major thirds down, the diesis 128/125
//   ~just.commaName([4, -1]);        // syntonic comma 81/80
//
// The lattice is where every equal division comes from: temperPc(edo, pc) sends a pitch class to the
// division's step through its val, and kernel(edo) lists the commas the division tempers out, the
// smallest first.

RLJust : RLPitchSystem {
	classvar <commas;
	var <primes, logs;

	*initClass {
		// known commas and small steps, by their ratio above 1
		commas = [
			[[81, 80], "syntonic comma"], [[128, 125], "diesis"], [[648, 625], "major diesis"],
			[[531441, 524288], "Pythagorean comma"], [[2048, 2025], "diaschisma"], [[32805, 32768], "schisma"],
			[[25, 24], "chromatic semitone"], [[16, 15], "diatonic semitone"], [[10, 9], "minor tone"],
			[[9, 8], "major tone"], [[250, 243], "porcupine comma"], [[3125, 3072], "magic comma"],
			[[15625, 15552], "kleisma"], [[64, 63], "septimal comma"], [[36, 35], "septimal quarter tone"],
			[[225, 224], "marvel comma"], [[126, 125], "starling comma"], [[2401, 2400], "breedsma"]
		];
	}

	*new { |primes = #[3, 5]| ^super.new.prInit(primes) }

	prInit { |ps|
		primes = ps.asArray.collect(_.asInteger);
		logs = primes.collect(_.log2);
	}

	rank { ^primes.size }

	pcZero { ^0 ! primes.size }
	pcAdd { |a, b| ^a + b }
	pcNeg { |a| ^a.neg }
	pcScale { |a, k| ^a * k }
	pc { |pitch| ^pitch.copyToEnd(1) }
	pcHeight { |pc| var h = (pc * logs).sum; ^h - h.floor }
	height { |pitch| ^pitch[0] + (pitch.copyToEnd(1) * logs).sum }
	nearest { |pc, height| ^[(height - (pc * logs).sum).round.asInteger] ++ pc }

	// The exponents of a just ratio, with the exponent of 2 (pitchInterval) or without (interval).
	pitchInterval { |ratio|
		var m = RLPitchSystem.monzo(ratio), out;
		m.keysDo { |p|
			if((p != 2) and: { primes.includes(p).not }) {
				Error("RLJust%: the ratio % needs the prime %".format(primes, ratio, p)).throw;
			};
		};
		out = primes.collect { |p| m[p] ? 0 };
		^[m[2] ? 0] ++ out
	}
	interval { |ratio| ^this.pitchInterval(ratio).copyToEnd(1) }

	pitchAdd { |a, b| ^a + b }
	pitchNeg { |a| ^a.neg }
	octave { ^[1] ++ (0 ! primes.size) }

	// A pitch class as a ratio between 1 and 2, [numerator, denominator] (Floats: exact up to 2 ** 53).
	ratioOf { |pc|
		var num = 1.0, den = 1.0;
		pc.do { |e, i|
			if(e > 0) { num = num * (primes[i] ** e) };
			if(e < 0) { den = den * (primes[i] ** e.neg) };
		};
		while { (num / den) >= 2 } { den = den * 2 };
		while { (num / den) < 1 } { num = num * 2 };
		^[num, den]
	}

	name { |pc|
		var r = this.ratioOf(pc);
		^if(r[0] < 1e9 and: { r[1] < 1e9 }) {
			"%/%".format(r[0].asInteger, r[1].asInteger)
		} {
			primes.collect { |p, i| "%^%".format(p, pc[i]) }.join(" ")
		}
	}

	// The name of a comma or of a small step, in either direction, or nil.
	commaName { |pc|
		var up = this.ratioOf(pc), down = this.ratioOf(pc.neg), found;
		found = commas.detect { |c| (c[0] == up.collect(_.asInteger)) or: { c[0] == down.collect(_.asInteger) } };
		^found !? { "% %/%".format(found[1], found[0][0], found[0][1]) }
	}

	// A pitch class or a pitch in an equal division, through its val.
	temperPc { |edo, pc|
		var sum = 0;
		pc.do { |e, i| sum = sum + (e * edo.val(primes[i])) };
		^sum % edo.steps
	}
	temperPitch { |edo, pitch|
		var sum = pitch[0] * edo.steps;
		pitch.copyToEnd(1).do { |e, i| sum = sum + (e * edo.val(primes[i])) };
		^sum
	}

	// The commas an equal division tempers out: the pitch classes sent to 0, with exponents up to bound,
	// one of each pair (x, -x), the smallest in cents first.
	kernel { |edo, bound = 6|
		var out = List.new, range = (bound.neg..bound);
		range.dup(primes.size).allTuples.do { |x|
			var first = x.detect { |e| e != 0 };
			if(first.notNil and: { first > 0 } and: { this.temperPc(edo, x) == 0 }) { out.add(x) };
		};
		^out.asArray.sort { |a, b| this.cents(a).abs <= this.cents(b).abs }
	}

	isFinite { ^false }
	size { ^inf }

	== { |other| ^other.isKindOf(RLJust) and: { primes == other.primes } }
	!= { |other| ^(this == other).not }
	hash { ^[\RLJust, primes].hash }

	printOn { |stream| stream << "RLJust(" << primes << ")" }
}
