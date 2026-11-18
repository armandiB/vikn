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
	var <space, <system, fifth, named, generatorNames, aliases;

	*new { |space| ^super.new.prInit(space) }

	// The second type must be the first turned upside down between its root and its top tone. Each pair of
	// tones i, j of a chord of the first type gives one contextual inversion, the one that exchanges them:
	// K01, K02 ... For triads K02 is P, K12 is L, K01 is R.
	prInit { |sp|
		var first, second, size, pairs = Array.new;
		space = sp;
		system = sp.system;
		if(sp.types.size != 2) { Error("RLChordContext: a space of two chord types is needed, not %".format(sp.typeNames)).throw };
		first = sp.types[0][1];
		second = sp.types[1][1];
		size = first.size;
		if(size < 2) { Error("RLChordContext: chords of two tones or more are needed").throw };
		fifth = first.last;
		if(second != first.reverse.collect { |x| system.pcSub(fifth, x) }) {
			Error("RLChordContext: the second type % is not the inversion of the first %".format(second, first)).throw
		};
		named = IdentityDictionary.new;
		aliases = IdentityDictionary.new;
		size.do { |i|
			Array.series(size - i - 1, i + 1).do { |j|
				var name = "K%%".format(i, j).asSymbol;
				named[name] = RLTransform(this, nil, [system.pcSub(system.pcAdd(first[i], first[j]), fifth), 1], name.asString);
				pairs = pairs.add(name);
			};
		};
		generatorNames = pairs;
		if(size == 3) {
			// the usual names only: K02, K12, K01 stay readable as other spellings of P, L, R
			named = IdentityDictionary[
				\P -> RLTransform(this, nil, named[\K02].right, "P"),
				\L -> RLTransform(this, nil, named[\K12].right, "L"),
				\R -> RLTransform(this, nil, named[\K01].right, "R")
			];
			aliases = IdentityDictionary[\K02 -> \P, \K12 -> \L, \K01 -> \R];
			generatorNames = #[\P, \L, \R];
		};
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
		} {
			// a temperament that is not finite can still have a tritone (RLTempered:halves)
			if(system.respondsTo(\halves)) { system.halves.collect { |v| [v, 0] } } { [] }
		}
	}

	// An order on the elements, for a transformation's canonical form: by type, then by root (a root that is
	// an Array, coordinate by coordinate).
	prBefore { |g, h|
		var a = g[0].asArray, b = h[0].asArray, i;
		if(g[1] != h[1]) { ^g[1] < h[1] };
		i = a.detectIndex { |x, k| x != b[k] };
		^i.notNil and: { a[i] < b[i] }
	}

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

	// The transformation that moves nothing.
	identityTransform { ^RLTransform(this) }

	// A transformation by its Symbol: \P, \L, \R, or a name given with define.
	transform { |name|
		var key = name.asSymbol;
		^named[aliases[key] ? key] ?? { Error("RLChordContext: no transformation named %".format(name)).throw }
	}

	// The contextual inversion that exchanges tones i and j of a chord of the first type (counted from 0 in its
	// pattern). On a chord of the second type it exchanges the tones that answer them upside down, counted
	// from the top: L keeps the third and fifth of a major triad, the root and third of a minor one.
	k { |i, j| ^this.transform("K%%".format(min(i, j), max(i, j))) }

	// Name the transformation that a word does: define(\N, [\R, \L, \P]) for R, then L, then P.
	define { |name, word|
		var tr = RLTransform(this);
		word.do { |w| tr = this.prResolve(w) <> tr };
		tr.label = name.asString;
		named[name.asSymbol] = tr;
		^tr
	}

	prResolve { |w| ^if(w.isKindOf(RLTransform)) { w } { this.transform(w) } }

	generators { ^generatorNames }
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
