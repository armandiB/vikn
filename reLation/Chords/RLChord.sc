// reLation — a chord: a root and a type in a chord space.
//
// The root is a pitch class of the space's system (an Integer in RLEDO, an Array of exponents in RLJust),
// the type one of the space's names. The chord's tones follow from the type's pattern, root first.
//
//   ~c = RLChordSpace.triads(RLEDO(12)).major(0);
//   ~c.pcs;         // [0, 4, 7]
//   ~c.name;        // C major
//   ~c == RLChordSpace.triads(RLEDO(12)).chord(0, \major);   // true: a chord is a value

RLChord {
	var <space, <root, <type;

	*new { |space, root, type|
		^super.newCopyArgs(space, space.system.pcAdd(root, space.system.pcZero), type.asSymbol).prCheck
	}

	prCheck { space.pattern(type); ^this }

	system { ^space.system }
	typeIndex { ^space.typeIndex(type) }

	// The pitch classes of its tones, in the order of the type's pattern (root, third, fifth).
	pcs { ^space.pattern(type).collect { |x| space.system.pcAdd(root, x) } }

	// Whether these pitch classes, in any order, are its tones.
	hasPcs { |pcs|
		var mine = this.pcs;
		^(mine.size == pcs.size) and: { mine.every { |x| pcs.includesEqual(x) } } and: { pcs.every { |x| mine.includesEqual(x) } }
	}

	// The tones shared with another chord.
	commonPcs { |other| var theirs = other.pcs; ^this.pcs.select { |x| theirs.includesEqual(x) } }

	name { ^"% %".format(space.system.name(root), type) }

	== { |other| ^other.isKindOf(RLChord) and: { root == other.root } and: { type == other.type } and: { space == other.space } }
	!= { |other| ^(this == other).not }
	hash { ^[\RLChord, root, type].hash }

	printOn { |stream| stream << this.name }
}
