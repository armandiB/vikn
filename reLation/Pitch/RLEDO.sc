// reLation — an equal division of the octave: pitch classes are the Integers 0..steps-1, pitches the Integers.
//
// Its val gives each prime a number of steps: the nearest one, (steps * log2(prime)).round, unless the
// mapping says otherwise (RLEDO(17, (5: 40)) gives 5/1 40 steps, hence 17's other major third, 6 steps
// and not 5). interval reads a just ratio through the val, so RLEDO(19).interval([5, 4]) is 6 and
// RLEDO(12).interval([81, 80]) is 0: the syntonic comma is tempered out.
//
//   RLEDO(31).val(3);              // 49 steps for 3/1, so 18 for a fifth
//   RLEDO(12).name(10);            // Bb
//   RLEDO(19).nearest(11, 1.5);    // 30: the pitch of class 11 closest to an octave and a half up

RLEDO : RLPitchSystem {
	classvar <noteNames;
	var <steps, mapping;

	*initClass { noteNames = #["C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"] }

	*new { |steps = 12, mapping| ^super.new.prInit(steps, mapping) }

	prInit { |n, map|
		steps = n.asInteger.max(1);
		mapping = Dictionary.new;
		map !? { map.keysValuesDo { |p, s| mapping[p.asInteger] = s.asInteger } };
	}

	// The steps of a prime: the mapping's, else the nearest.
	val { |prime|
		var p = prime.asInteger;
		^mapping[p] ?? { (steps * p.log2).round.asInteger }
	}

	// The primes the mapping names, with their steps (the nearest steps are not listed).
	mapping { ^mapping.copy }

	pcZero { ^0 }
	pcAdd { |a, b| ^(a + b) % steps }
	pcNeg { |a| ^a.neg % steps }
	pcScale { |a, k| ^(a * k) % steps }
	pc { |pitch| ^pitch % steps }
	pcHeight { |pc| ^(pc % steps) / steps }
	height { |pitch| ^pitch / steps }
	nearest { |pc, height| ^pc + (steps * (((height * steps) - pc) / steps).round.asInteger) }

	// The steps of a just ratio, as a pitch (pitchInterval) or reduced to a pitch class (interval).
	pitchInterval { |ratio|
		var sum = 0;
		RLPitchSystem.monzo(ratio).keysValuesDo { |p, e| sum = sum + (e * this.val(p)) };
		^sum
	}
	interval { |ratio| ^this.pitchInterval(ratio) % steps }

	pitchAdd { |a, b| ^a + b }
	pitchNeg { |a| ^a.neg }
	octave { ^steps }

	name { |pc| ^if(steps == 12) { noteNames[pc % 12] } { (pc % steps).asString } }
	isFinite { ^true }
	size { ^steps }
	pcs { ^Array.series(steps) }
	stepCents { ^1200 / steps }

	prMapping { ^mapping.asSortedArray }

	== { |other| ^other.isKindOf(RLEDO) and: { steps == other.steps } and: { this.prMapping == other.prMapping } }
	!= { |other| ^(this == other).not }
	hash { ^[\RLEDO, steps, this.prMapping].hash }

	printOn { |stream|
		stream << "RLEDO(" << steps;
		if(mapping.notEmpty) { stream << ", " << mapping.asSortedArray };
		stream << ")";
	}
}
