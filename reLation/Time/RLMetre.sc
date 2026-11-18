// reLation — metre and canons on a cycle of n pulses.
//
// Metre as a relation (Popoff and Yust): M(d) relates two pulses a multiple of d apart. Composing two metres
// gives their greatest common divisor, intersecting them their least common multiple, and one is included in
// another when its number is a multiple of the other's. Shifting the pulses, or multiplying them by a number
// prime to n, changes no metre: they are automorphisms.
//
//   ~m = RLMetre(12);
//   (~m.relation(4) <> ~m.relation(6)) == ~m.relation(2);      // true: the gcd
//   (~m.relation(4) & ~m.relation(6)) == ~m.relation(12);      // true: the lcm, here each pulse with itself
//   ~m.relation(4) <= ~m.relation(2);                          // true: 4 is a multiple of 2
//
// A rhythmic canon: a rhythm A and a set of entries B tile the cycle when every pulse is sounded once.
//
//   ~m.tiles([0, 1, 2], [0, 3, 6, 9]);       // true
//   ~m.complements([0, 1, 5]);               // [[0, 3, 6, 9]]: the entries with which this rhythm tiles 12 pulses
//   ~m.complements([0, 1, 3]);               // []: this one tiles them with no set of entries

RLMetre {
	var <n, <points;

	*new { |n = 12| ^super.newCopyArgs(n, Array.series(n)) }

	// Two pulses a multiple of d apart (round the cycle that is a multiple of the gcd of d and n).
	relation { |d|
		var g = d.gcd(n);
		^RLRelation.on(points, { |x, y| ((y - x) % g) == 0 }, nil, "M" ++ d)
	}

	// The pulses with several metres as named relations M2, M3 ...: an RLRelationContext.
	context { |divisors|
		^RLRelationContext(points, divisors.collect { |d| [("M" ++ d).asSymbol, this.relation(d)] })
	}

	// Whether a rhythm (a list of pulses) entering at each of the entries sounds every pulse of the cycle once.
	tiles { |rhythm, entries|
		var hits = rhythm.collect { |x| entries.collect { |y| (x + y) % n } }.flat;
		^(hits.size == n) and: { hits.asSet.size == n }
	}

	// Every set of entries, the pulse 0 among them, with which a rhythm tiles the cycle.
	complements { |rhythm|
		var out = Array.new, count = n.div(rhythm.size), try;
		if((rhythm.size * count) != n) { ^out };
		try = { |entries, taken, from|
			if(entries.size == count) { out = out.add(entries) } {
				Array.series(n - from, from).do { |e|
					var sounds = rhythm.collect { |x| (x + e) % n };
					if(sounds.every { |s| taken.includes(s).not }) { try.value(entries ++ [e], taken ++ sounds, e + 1) };
				};
			};
		};
		try.value([0], rhythm.collect { |x| x % n }, 1);
		^out
	}

	printOn { |stream| stream << "RLMetre(" << n << ")" }
}
