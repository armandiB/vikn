// reLation — a scale: some pitch classes of a system, in order from a tonic, as a pitch system of their own.
//
// The scale's pitch classes are its degrees, 0 (the tonic) to n - 1, and its pitches Integers: degree and
// octave, pitch 0 the tonic at or above the parent's origin. It answers what every RLEDO answers, so chord
// spaces, contexts, relations, graphs, walks and voice leading work on the degrees as they do on the steps of
// a division: a step is a degree, whatever its size. Heights are the true ones, through the parent, so a walk
// on the degrees sounds in the parent's tuning.
//
//   ~c = RLScale.major(RLEDO(12));            // C major: the tones of the triads on IV, I and V
//   ~c.degrees;                               // [0, 2, 4, 5, 7, 9, 11]: the parent's pitch classes
//   ~c.toParent(8);                           // 14: degree 1 an octave up is D
//   ~c.specific(2);                           // [4, 3]: a third of the scale is major or minor
//   ~triads = ~c.triads;                      // a chord space: one type, [0, 2, 4]
//   ~c.numeral(~triads.chord(1));             // ii
//   ~c.progression(~triads.chord(4), ~triads.chord(0));    // ascending: the root a fourth up
//   RLScale.major(RLJust([3, 5])).degrees;    // the just major scale on the lattice
//   RLScale.major(RLEDO(19), 3).degrees;      // the same scale in 19 steps, from its pitch class 3
//
// A minor scale holds two forms (Schoenberg, Structural Functions of Harmony, chapter II): its degrees are the
// descending (natural) form, and its sixth and seventh degrees have a substitute a chromatic step higher, kept
// in alternates. holds and degreeOf know both; the pitch system is the natural form.
//
// What a scale is not: a morphism of intervals. One step of the scale is a tone or a semitone of the parent;
// specific(k) lists the sizes a number of steps takes.

RLScale : RLEDO {
	var <parent, <degrees, <alternates, <label, offsets, tonicHeight;

	// pcs: pitch classes of the parent, the tonic first, the rest in any order.
	// alternates: [[degree, pitch class], ...], other forms of some degrees.
	*new { |parent, pcs, alternates, label| ^super.new(pcs.size).prScale(parent, pcs, alternates, label) }

	// The tones of the major triads on IV, I and V of a tonic (the parent's zero when none is given).
	*major { |parent, tonic|
		^this.prRatios(parent, tonic, #[[1, 1], [9, 8], [5, 4], [4, 3], [3, 2], [5, 3], [15, 8]], nil, "major")
	}

	// The tones of the minor triads on iv, i and v, and the substitutes of the sixth and seventh degrees.
	*minor { |parent, tonic|
		^this.prRatios(parent, tonic, #[[1, 1], [9, 8], [6, 5], [4, 3], [3, 2], [8, 5], [9, 5]], #[[5, [5, 3]], [6, [15, 8]]], "minor")
	}

	// A chain of fifths: below the tonic and above it (1 and 5: the diatonic scale in Pythagorean order).
	*fifths { |parent, tonic, below = 1, above = 5, label|
		var sys = parent ?? { RLEDO(12) }, t = tonic ?? { sys.pcZero }, fifth = sys.interval([3, 2]);
		^this.new(sys, Array.series(below + above + 1, below.neg).collect { |k| sys.pcAdd(t, sys.pcScale(fifth, k)) }.rotate(below.neg), nil, label ? "fifths")
	}

	*prRatios { |parent, tonic, ratios, alternates, label|
		var sys = parent ?? { RLEDO(12) }, t = tonic ?? { sys.pcZero };
		^this.new(sys, ratios.collect { |r| sys.pcAdd(t, sys.interval(r)) },
			alternates !? { alternates.collect { |pair| [pair[0], sys.pcAdd(t, sys.interval(pair[1]))] } }, label)
	}

	prScale { |sys, pcs, alts, name|
		var tonic = pcs.first, order;
		parent = sys;
		order = pcs.asArray.collect { |pc| [sys.pcHeight(sys.pcSub(pc, tonic)), pc] }.sort { |a, b| a[0] <= b[0] };
		offsets = order.collect(_[0]);
		degrees = order.collect(_[1]);
		offsets.doAdjacentPairs { |a, b|
			if((b - a) < 1e-9) { Error("RLScale: two degrees on one pitch class in %".format(pcs)).throw };
		};
		tonicHeight = sys.pcHeight(tonic);
		alternates = (alts ? []).collect { |pair| [pair[0] % steps, pair[1]] };
		label = name;
	}

	tonic { ^degrees[0] }

	// The parent's pitch class of a degree (its natural form), and the degree of a parent's pitch class
	// (natural or alternate), nil when the scale does not hold it.
	parentPc { |degree| ^degrees[degree % steps] }
	degreeOf { |pc|
		var d = degrees.detectIndex { |x| x == pc };
		^d ?? { alternates.detect { |pair| pair[1] == pc } !? { |pair| pair[0] } }
	}
	holds { |pc| ^this.degreeOf(pc).notNil }
	isNatural { |pc| ^degrees.includesEqual(pc) }

	// Every pitch class the scale holds, the natural ones first.
	tones { ^degrees ++ alternates.collect(_[1]) }

	// The pitch of the parent for a pitch of the scale, and back (nil for a pitch outside the natural form).
	toParent { |pitch| ^parent.nearest(degrees[pitch % steps], this.height(pitch)) }
	fromParent { |pitch|
		var d = degrees.detectIndex { |x| x == parent.pc(pitch) };
		^d !? { this.nearest(d, parent.height(pitch)) }
	}

	// As a pitch system. A pitch class's height is its height above the tonic.
	pcHeight { |pc| ^offsets[pc % steps] }
	height { |pitch|
		var d = pitch % steps;
		^tonicHeight + offsets[d] + (pitch - d).div(steps)
	}
	nearest { |pc, height|
		var d = pc % steps;
		^d + (steps * (height - tonicHeight - offsets[d]).round.asInteger)
	}

	// The numbers of steps that take some degree up by this interval of the parent, and the sizes a number of
	// steps takes in the parent (two for each, in a diatonic scale).
	generic { |parentInterval|
		^Array.series(steps - 1, 1).select { |k|
			degrees.any { |pc, d| parent.pcSub(degrees[(d + k) % steps], pc) == parentInterval }
		}
	}
	specific { |k|
		var out = Array.new;
		steps.do { |d|
			var s = parent.pcSub(degrees[(d + k) % steps], degrees[d]);
			if(out.includesEqual(s).not) { out = out.add(s) };
		};
		^out
	}

	// A just ratio as a number of steps: the steps that the parent's interval for it spans somewhere in the scale.
	interval { |ratio|
		var s = parent.interval(ratio);
		^if(s == parent.pcZero) { 0 } {
			this.generic(s).first ?? { Error("RLScale: no two degrees of % are % apart".format(this, ratio)).throw }
		}
	}
	pitchInterval { |ratio|
		var r = if(ratio.isKindOf(SequenceableCollection)) { ratio[0] / ratio[1] } { ratio };
		^this.interval(ratio) + (steps * r.log2.floor.asInteger)
	}
	val { |prime| ^this.pitchInterval([prime, 1]) }

	name { |pc| ^parent.name(degrees[pc % steps]) }

	// Chords of the scale: a root and every second degree above it, three tones or four, or any pattern of steps.
	triads { ^this.chords(3) }
	sevenths { ^this.chords(4) }
	chords { |size = 3, step = 2, name|
		^RLChordSpace(this, [[name ?? { #[\dyad, \triad, \seventh, \ninth][size - 2] ? \chord }, Array.series(size, 0, step) % steps]])
	}

	// A chord of the scale as pitch classes of the parent, and as a chord of a space of the parent (nil when the
	// space has none with these tones).
	realize { |chord| ^chord.pcs.collect { |d| degrees[d % steps] } }
	in { |space, chord| ^space.find(this.realize(chord)) }

	// The Roman numeral of a chord of thirds on a degree: capitals when its third is the parent's 5/4, small
	// letters otherwise, o for a fifth smaller than 3/2 and + for a larger one.
	numeral { |chord|
		var pcs = this.realize(chord), roman = #["I", "II", "III", "IV", "V", "VI", "VII"][chord.root % steps] ? (chord.root + 1).asString;
		var third = parent.pcSub(pcs[1], pcs[0]), fifth = parent.pcSub(pcs[2] ? pcs[0], pcs[0]);
		var just = parent.interval([3, 2]), h = { |x| parent.pcHeight(x) };
		if(third != parent.interval([5, 4])) { roman = roman.toLower };
		if(pcs.size > 2) {
			if(h.(fifth) < (h.(just) - 1e-9)) { roman = roman ++ "o" };
			if(h.(fifth) > (h.(just) + 1e-9)) { roman = roman ++ "+" };
		};
		if(chord.pcs.size > 3) { roman = roman ++ "7" };
		^roman
	}

	// Schoenberg's three kinds of root progression (Structural Functions of Harmony, chapter II), from the steps
	// between two roots: \ascending (strong: a fourth up, a third down), \descending (a fourth down, a third up),
	// \superstrong (a step up or down), \same, and \other in a scale where the steps fall under none.
	progression { |from, to| ^this.progressionOf((to.root - from.root) % steps) }
	progressionOf { |k|
		var fifth = this.interval([3, 2]), third = 2, m = k % steps;
		^case
		{ m == 0 } { \same }
		{ (m == (steps - fifth)) or: { m == (steps - third) } } { \ascending }
		{ (m == fifth) or: { m == third } } { \descending }
		{ (m == 1) or: { m == (steps - 1) } } { \superstrong }
		{ \other }
	}

	// The same tones from another of its degrees: mode(1) of a major scale is its Dorian.
	mode { |degree, label|
		var d = degree % steps;
		^RLScale(parent, degrees.rotate(d.neg), alternates.collect { |pair| [(pair[0] - d) % steps, pair[1]] }, label)
	}

	// The tones shared with another scale of the same parent (natural forms).
	commonTones { |that| ^degrees.select { |pc| that.degrees.includesEqual(pc) } }

	stepCents { ^1200 / steps }

	== { |other|
		^(other.class === this.class) and: { parent == other.parent } and: { degrees == other.degrees } and: { alternates == other.alternates }
	}
	hash { ^[\RLScale, parent.hash, degrees, alternates].hash }

	printOn { |stream|
		stream << "RLScale(" << parent.name(degrees[0]);
		label !? { stream << " " << label };
		stream << " in " << parent << ")";
	}
}
