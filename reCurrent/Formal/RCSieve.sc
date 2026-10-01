// reCurrent — a sieve (Xenakis, Formalized Music ch. XI-XII): a set of integers built from
// residue classes, for pitch scales, time grids, or any well-ordered set with a unit.
//
// A pair (M, I) is the residue class I modulo M: the integers n with n ≡ I (mod M). A formula is
// an Array of unions, each union an Array of pairs intersected:
//   [[[3, 2], [4, 7]], [[6, 9], [15, 18]]]        = (3,2)∩(4,7) ∪ (6,9)∩(15,18)
// A sieve is a formula with a unit and a zero: the integer n sounds at zero + n * unit (pitch in
// octaves: unit 1/12 a semitone, 1/24 a quartertone; time in beats: unit 1/4 a sixteenth).
//
//   ~diatonic = RCSieve.residues(12, [0, 2, 4, 5, 7, 9, 11], 1/12);     // the white keys (p. 269)
//   ~diatonic.points(0, 0.99);                                           // the seven degrees of the first octave, in octaves
//   RCSieve.intersect([3, 2], [4, 7]);                                   // [12, 11] (p. 272-274)
//   RCSieve([[[3, 2], [4, 7]], [[6, 9], [15, 18]]]).pairs;              // [[12, 11], [30, 3]] (p. 278)
//   ~diatonic.metabola(7);                                               // the same structure transposed by a fifth (p. 275-276)
//   RCSieve.fromPoints([0, 3, 6, 8, 11, 14, 16, 19, 22]).pairs;         // [[8, 0], [8, 3], [8, 6]]: the 3, 3, 2 rhythm read back
//
// Membership is tested arithmetically, so the sizes are never an issue; the intersection of two
// pairs is reduced to one pair as in the book (lcm, the first coincidence), an empty one is dropped.
// A sieve is a value: equality and hash are structural.

RCSieve {
	var <formula, <unit, <zero, <>name;

	*new { |formula, unit = 1, zero = 0, name = \sieve|
		^super.newCopyArgs(this.prFormula(formula), unit, zero, name.asSymbol)
	}

	// The union of the residues of one modulus: residues(8, [0, 3, 6], 1/2) is the 3, 3, 2 rhythm in eighths.
	*residues { |modulus = 12, residues = #[0], unit = 1, zero = 0, name = \sieve|
		^this.new(this.residuesFormula(modulus, residues), unit, zero, name)
	}

	*residuesFormula { |modulus = 12, residues = #[0]|
		^residues.asArray.collect { |r| [[modulus, r]] }
	}

	// The inverse (p. 274-275, simplified): a sieve from a set of integers.
	*fromPoints { |points, maxModulus = 24, unit = 1, zero = 0, name = \sieve|
		^this.new(this.formulaFromPoints(points, maxModulus), unit, zero, name)
	}

	// formula as given, every pair [M, I] with M a positive integer and I reduced
	*prFormula { |formula|
		^(formula ? []).collect { |inter|
			inter.collect { |p| var m = p[0].asInteger.max(1); [m, p[1].asInteger % m] }
		}
	}

	//////// arithmetic on formulas (class side)

	// (M1, I1) ∩ (M2, I2) = (M3, I3) with M3 the lcm (p. 271-273), or nil when the classes never
	// meet ((I1 - I2) not divisible by the gcd). I3 is the first coincidence from I1 on.
	*intersect { |a, b|
		var m1 = a[0], i1 = a[1] % a[0], m2 = b[0], i2 = b[1] % b[0];
		var d = m1.gcd(m2), m3 = m1.lcm(m2), n, found;
		if((i1 - i2) % d != 0) { ^nil };
		n = i1;
		while { found.isNil and: { n < (i1 + m3) } } {
			if((n - i2) % m2 == 0) { found = n };
			n = n + m1;
		};
		^found !? { [m3, found % m3] }
	}

	// Every union reduced to one pair; an empty intersection drops out.
	*simplify { |formula|
		^this.prFormula(formula).collect { |inter|
			var res = inter[0];
			inter[1..].do { |p| res = res !? { this.intersect(res, p) } };
			res
		}.reject(_.isNil).collect { |p| [p] }
	}

	// The period of a formula: the lcm of its unions' moduli (p. 275: (24,23) ∪ (30,3) ∪ (104,70) → 1560).
	*periodOf { |formula|
		^this.simplify(formula).collect { |inter| inter[0][0] }.reduce { |a, b| a.lcm(b) } ? 1
	}

	*formulaIncludes { |formula, n|
		^formula.any { |inter| inter.every { |p| p[0] > 0 and: { (n - p[1]) % p[0] == 0 } } }
	}

	*formulaIntegers { |formula, lo = 0, hi = 60|
		^(lo.asInteger..hi.asInteger).select { |n| this.formulaIncludes(formula, n) }
	}

	// From a set of integers: the union of the (Q, I) pairs whose every point within the set's span
	// belongs to the set, the smallest modulus first, each point covered once; a point no modulus
	// up to maxModulus explains gets the span as its modulus.
	*formulaFromPoints { |points, maxModulus = 24|
		var sorted = points.asArray.collect(_.asInteger).asSet.asArray.sort;
		var lo = sorted.first ? 0, hi = sorted.last ? 0, span = (hi - lo + 1).max(1);
		var set = IdentitySet.newFrom(sorted), covered = IdentitySet.new, pairs = List.new;
		sorted.do { |p|
			if(covered.includes(p).not) {
				var q = 2, pair = nil;
				while { pair.isNil and: { q <= maxModulus } } {
					var members = (lo..hi).select { |x| (x - p) % q == 0 };
					if(members.every { |x| set.includes(x) }) { pair = [q, p % q]; members.do { |x| covered.add(x) } };
					q = q + 1;
				};
				pair = pair ?? { covered.add(p); [span, p % span] };
				pairs.add([pair]);
			};
		};
		^pairs.asArray
	}

	//////// the sieve

	includes { |n| ^this.class.formulaIncludes(formula, n) }

	// The integers of the sieve in [lo, hi].
	integers { |lo = 0, hi = 60| ^this.class.formulaIntegers(formula, lo, hi) }

	// The values of the sieve within [lo, hi] (its unit and zero applied).
	points { |lo = 0, hi = 1|
		var nlo = ((lo - zero) / unit).ceil.asInteger, nhi = ((hi - zero) / unit).floor.asInteger;
		^this.integers(nlo, nhi).collect { |n| zero + (n * unit) }
	}

	// The simplified pairs [[M, I], ...], one per union.
	pairs { ^this.class.simplify(formula).collect { |inter| inter[0] } }

	simplified { ^this.class.new(this.class.simplify(formula), unit, zero, name) }

	period { ^this.class.periodOf(formula) }

	// Metabolae (p. 275-276): shift a number adds it to every residue (a transposition of the
	// sieve); an Array gives one shift per pair in order (the intervallic structure changes, the
	// period stays); unit replaces the unit (the third metabola: the diatonic structure in
	// quartertones). Returns a new sieve.
	metabola { |shift = 0, unit|
		var i = -1;
		var shifted = formula.collect { |inter| inter.collect { |p|
			var s = if(shift.isKindOf(SequenceableCollection)) { i = i + 1; shift.wrapAt(i) } { shift };
			[p[0], (p[1] + s) % p[0]]
		} };
		^this.class.new(shifted, unit ? this.unit, zero, name)
	}

	== { |other| ^other.isKindOf(RCSieve) and: { formula == other.formula } and: { unit == other.unit } and: { zero == other.zero } }
	!= { |other| ^(this == other).not }
	hash { ^[formula, unit, zero].hash }

	printOn { |stream|
		stream << "RCSieve(" << name << ", " << this.pairs.collect { |p| p[0].asString ++ "_" ++ p[1].asString }.join(" ∪ ") << ", unit " << unit << ")"
	}
}
