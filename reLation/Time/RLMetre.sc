// reLation — metre and canons on a cycle of n pulses.
//
// Metre as a relation (Popoff and Yust): M(d) relates two pulses a multiple of d apart. Composing two metres
// gives their greatest common divisor, intersecting them their least common multiple, and one is included in
// another when its number is a multiple of the other's. Shifting the pulses, or multiplying them by a number
// prime to n, changes no metre: they are automorphisms. (On the line of time, with periods that are fractions,
// the same relations are RLPulses, and metre networks live on RLTimePoints.)
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
//
// A canon by augmentation: the voices play the rhythm at different stretches, on the line of the n pulses.
//
//   ~m.augmented([0, 1, 2], [1, 1, 2, 2]);   // [[0, 3, 6, 7], ...]: the entries, one per stretch
//   RLMetre.perfect([0, 1, 2], 5);           // every stretch different: 15 pulses, stretches 1, 2, 4, 5, 7, two ways

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

	// A canon by augmentation on the line of the n pulses (not round the cycle): one voice per stretch plays
	// the rhythm d times as slow from its entry, and every pulse is sounded once. Every list of entries, in the
	// order of the stretches (voices of one stretch enter in that order).
	augmented { |rhythm, stretches|
		var out = Array.new, k = stretches.size, steps = rhythm.asArray.sort, try;
		steps = steps - steps[0];
		if((steps.size * k) != n) { ^out };
		try = { |covered, entries|
			var x = covered.indexOf(false);
			if(x.isNil) { out = out.add(entries) } {
				k.do { |i|
					var d = stretches[i], pulses, next;
					if(entries[i].isNil and: { Array.series(i).any { |j| entries[j].isNil and: { stretches[j] == d } }.not }) {
						pulses = steps.collect { |r| x + (d * r) };
						if((pulses.last < n) and: { pulses.every { |p| covered[p].not } }) {
							next = covered.copy;
							pulses.do { |p| next[p] = true };
							try.value(next, entries.copy.put(i, x));
						};
					};
				};
			};
		};
		try.value(Array.fill(n, false), Array.newClear(k));
		^out
	}

	// Perfect tilings: a rhythm played by `voices` voices, each at a stretch of its own, tiling the line of
	// rhythm.size * voices pulses. [stretches, entries] pairs. Three pulses in a row: none before five voices
	// (15 pulses, stretches 1, 2, 4, 5, 7).
	*perfect { |rhythm, voices|
		var steps = rhythm.asArray.sort, n = rhythm.size * voices, most, metre = this.new(n), out = Array.new, choose;
		most = (n - 1).div(steps.last - steps.first);
		choose = { |from, chosen|
			if(chosen.size == voices) {
				metre.augmented(rhythm, chosen).do { |entries| out = out.add([chosen, entries]) };
			} {
				Array.series((most - from + 1).max(0), from).do { |d| choose.value(d + 1, chosen ++ [d]) };
			};
		};
		choose.value(1, []);
		^out
	}

	printOn { |stream| stream << "RLMetre(" << n << ")" }
}
