// reLation — a context: the chords of a two-type space and the group that moves them.
//
// Once a base chord is chosen (the first type on the zero root), every chord is an element of one group:
// the chord of root x and type index e is [x, e]. Elements multiply as
//
//   [v, 0] * [w, e] = [v + w, e]            [v, 1] * [w, e] = [v - w, 1 + e]
//
// and the group moves the chords in two ways that commute (Lewin's duality):
//
//   on the left    T and I: the transpositions and inversions of the pitch classes, the same for every chord
//   on the right   P, L, R and their products: each read from the chord it is applied to (contextual)
//
// A transformation is an RLTransform, a left element and a right element: chord -> left * chord * right.
//
//   ~ctx = RLChordContext(RLChordSpace.triads(RLEDO(12)));
//   ~c = ~ctx.space.major(0);
//   ~ctx.l.(~c);                      // E minor
//   ~ctx.t(7).(~c);                   // G major
//   ~ctx.i(0).(~c);                   // F minor: the pitch classes turned around 0
//   (~ctx.l <> ~ctx.p).(~c);          // Ab major: P first, then L
//   ~ctx.act(\R, ~c);                 // [A minor]: a list, as every context answers
//   ~ctx.rightInterval(~c, ~ctx.space.minor(9)).label;   // R
//
// The same class serves an equal division and the just lattice: nothing here counts the pitch classes.
// The names P, L, R are the three moves that keep two tones of a triad: P keeps root and fifth, L third and
// fifth, R root and third.

RLChordContext {
	var <space, <system, third, fifth, named;

	*new { |space| ^super.new.prInit(space) }

	prInit { |sp|
		var major, minor;
		space = sp;
		system = sp.system;
		if(sp.types.size != 2) { Error("RLChordContext: a space of two chord types is needed, not %".format(sp.typeNames)).throw };
		major = sp.types[0][1];
		minor = sp.types[1][1];
		if(major.size != 3) { Error("RLChordContext: chords of three tones are needed").throw };
		third = major[1];
		fifth = major[2];
		if(minor != [system.pcZero, system.pcSub(fifth, third), fifth]) {
			Error("RLChordContext: the second type % is not the inversion of the first %".format(minor, major)).throw
		};
		named = IdentityDictionary.new;
		named[\P] = RLTransform(this, nil, [system.pcZero, 1], "P");
		named[\L] = RLTransform(this, nil, [third, 1], "L");
		named[\R] = RLTransform(this, nil, [system.pcSub(third, fifth), 1], "R");
	}

	// the group, on elements [v, e]
	identity { ^[system.pcZero, 0] }
	mul { |g, h| ^[system.pcAdd(g[0], if(g[1] == 0) { h[0] } { system.pcNeg(h[0]) }), (g[1] + h[1]) % 2] }
	inv { |g| ^if(g[1] == 0) { [system.pcNeg(g[0]), 0] } { g.copy } }

	// The elements that commute with all others: a transposition by half the octave, when there is one.
	// Such an element does the same on the left and on the right (T6 is also (RP)^2).
	isCentral { |g| ^(g[1] == 0) and: { system.pcAdd(g[0], g[0]) == system.pcZero } }
	center {
		^if(system.isFinite) {
			system.pcs.select { |v| (v != system.pcZero) and: { this.isCentral([v, 0]) } }.collect { |v| [v, 0] }
		} { [] }
	}

	// An order on the elements of a finite system, for a transformation's canonical form.
	prBefore { |g, h| ^(g[1] < h[1]) or: { (g[1] == h[1]) and: { g[0] < h[0] } } }

	// chords as elements, and back
	element { |chord| ^[chord.root, chord.typeIndex] }
	chordOf { |g| ^space.chord(g[0], space.typeAt(g[1])) }

	// the transformations by name
	t { |v| ^RLTransform(this, [v, 0], nil, "T" ++ this.prLabel(v)) }
	i { |k| ^RLTransform(this, [system.pcSub(k, fifth), 1], nil, "I" ++ this.prLabel(k)) }
	p { ^named[\P] }
	l { ^named[\L] }
	r { ^named[\R] }

	prLabel { |v| ^if(v.isKindOf(SequenceableCollection)) { "(" ++ v.join(" ") ++ ")" } { v.asString } }

	// A transformation by its Symbol: \P, \L, \R, or a name given with define.
	transform { |name|
		^named[name.asSymbol] ?? { Error("RLChordContext: no transformation named %".format(name)).throw }
	}

	// Name the transformation that a word does: define(\N, [\R, \L, \P]) for R, then L, then P.
	define { |name, word|
		var tr = RLTransform(this);
		word.do { |w| tr = this.prResolve(w) <> tr };
		tr.label = name.asString;
		named[name.asSymbol] = tr;
		^tr
	}

	prResolve { |w| ^if(w.isKindOf(RLTransform)) { w } { this.transform(w) } }

	generators { ^#[\P, \L, \R] }
	names { ^named.keys.asArray.sort }

	// What a named transformation makes of a chord: a list (here always of one chord).
	act { |name, chord| ^[this.prResolve(name).value(chord)] }

	// The chords one generator away, as [name, chord] pairs.
	neighbours { |chord| ^this.generators.collect { |g| [g, named[g].value(chord)] } }

	// The one transformation of each side taking x to y.
	leftInterval { |x, y|
		var g = this.mul(this.element(y), this.inv(this.element(x)));
		^RLTransform(this, g, nil, this.prLeftLabel(g))
	}
	rightInterval { |x, y|
		var tr = RLTransform(this, nil, this.mul(this.inv(this.element(x)), this.element(y))), known;
		known = named.keys.asArray.sort.detect { |k| named[k] == tr };
		tr.label = known !? { known.asString };
		^tr
	}

	prLeftLabel { |g|
		^if(g[1] == 0) { "T" ++ this.prLabel(g[0]) } { "I" ++ this.prLabel(system.pcAdd(g[0], fifth)) }
	}

	== { |other| ^other.isKindOf(RLChordContext) and: { space == other.space } }
	!= { |other| ^(this == other).not }
	hash { ^[\RLChordContext, space.hash].hash }

	printOn { |stream| stream << "RLChordContext(" << space << ")" }
}
