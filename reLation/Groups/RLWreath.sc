// reLation — uniform transformations (Hook): each chord type has its own transposition, and the types are
// permuted.
//
// An element is [shifts, types]: a chord of type i goes to the root shifts[i] higher, with type types[i]. For
// two types and 12 steps these are Hook's 288 uniform triadic transformations <p major, p minor, sign>; for k
// types and n steps the wreath product of Z_n by the permutations of k things, n^k k! elements.
//
//   ~w = RLWreath(12, 2);
//   ~space = RLChordSpace.triads(RLEDO(12));  ~ctx = RLChordContext(~space);
//   ~w.of(~ctx.l, ~space);                // [[4, 8], [1, 0]]: L is <4, 8, ->
//   ~w.of(~ctx.i(0), ~space);             // nil: an inversion is not uniform
//   ~u = [[9, 8], [1, 0]];                // Beethoven's Ninth: R and L in turn are one transformation repeated
//   ~w.act(~u, ~space.major(0));          // A minor
//   ~w.mul(~u, ~u);                       // [[5, 5], [0, 1]]: twice is a fourth up
//   ~w.order(~u);                         // 24
//
// mul(u, v) is u after v.

RLWreath {
	var <n, <k;

	*new { |n = 12, k = 2| ^super.newCopyArgs(n, k) }

	size { ^(n ** k).asInteger * k.factorial.asInteger }
	identity { ^[0 ! k, Array.series(k)] }

	// u after v: a chord of type i is first moved by v (to the type v's types[i]), then by u.
	mul { |u, v|
		^[Array.fill(k) { |i| (u[0][v[1][i]] + v[0][i]) % n }, Array.fill(k) { |i| u[1][v[1][i]] }]
	}
	inv { |u|
		var shifts = Array.newClear(k), types = Array.newClear(k);
		k.do { |i| types[u[1][i]] = i; shifts[u[1][i]] = u[0][i].neg % n };
		^[shifts, types]
	}
	order { |u|
		var out = u, count = 1, id = this.identity;
		while { out != id } { out = this.mul(u, out); count = count + 1 };
		^count
	}

	// Every element (meant for small n and k: 288 for 12 and 2).
	elements {
		var perms = Array.fill(k.factorial.asInteger) { |i| Array.series(k).permute(i) };
		^(Array.series(n) ! k).allTuples.collect { |shifts| perms.collect { |types| [shifts, types] } }.flatten(1)
	}

	// What an element makes of a chord of a k-type space.
	act { |u, chord|
		var i = chord.typeIndex;
		^chord.space.chord(chord.root + u[0][i], chord.space.typeAt(u[1][i]))
	}

	// The element that does what a function of chords does on a space of an RLEDO, or nil when the function is
	// not uniform (its shift depends on the root, as an inversion's does).
	of { |func, space|
		var shifts = Array.newClear(k), types = Array.newClear(k), ok = true;
		space.types.do { |t, i|
			space.system.pcs.do { |r|
				var image = func.value(space.chord(r, t[0])), shift = (image.root - r) % n;
				if(shifts[i].isNil) { shifts[i] = shift; types[i] = image.typeIndex } {
					if((shifts[i] != shift) or: { types[i] != image.typeIndex }) { ok = false };
				};
			};
		};
		^if(ok) { [shifts, types] }
	}

	printOn { |stream| stream << "RLWreath(" << n << ", " << k << ")" }
}
