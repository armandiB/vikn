// reLation — a morphism between two contexts: a map of chords that the transformations go through.
//
// map sends each chord of the source to a chord of the target so that, for every name both contexts
// know, moving then mapping is mapping then moving: map(P(x)) = P(map(x)). Such a map carries walks
// forward (push) and, the other way, lifts them: a walk of the target and a chord above its start give
// the walk above it, by the same word.
//
// A temperament is the first example: the val of an equal division sends the just lattice's triads onto
// the division's, and every closed walk of the division lifts to a just walk that ends a comma away.
//
//   ~just = RLChordContext(RLChordSpace.triads(RLJust([3, 5])));
//   ~edo = RLChordContext(RLChordSpace.triads(RLEDO(12)));
//   ~temper = RLMorphism.temper(~just, ~edo);
//   ~temper.(~just.space.minor([-1, 1]));      // A minor
//   ~temper.isValid;                            // true, on a patch of the lattice
//
//   ~hexatonic = RLWalk.cycle(~edo, ~edo.space.major(0), [\P, \L]);
//   ~above = ~temper.lift(~hexatonic);          // from 1/1 major
//   ~above.drift;                               // [0, -3]
//   ~just.system.commaName(~above.drift);       // diesis 128/125

RLMorphism {
	var <source, <target, <map, <>label;

	*new { |source, target, map, label| ^super.newCopyArgs(source, target, map, label) }

	// A temperament, types kept: from a just context to an equal division's (roots through the val), from a
	// just context to a temperament's (RLTempered: roots reduced by its commas), or from a temperament's to an
	// equal division it supports.
	*temper { |source, target|
		var from = source.system, to = target.system, map;
		if(from.isKindOf(RLTempered) and: { to.isKindOf(RLEDO) } and: { from.supports(to).not }) {
			Error("RLMorphism.temper: % does not temper out the commas of %".format(to, from)).throw
		};
		map = case
		{ (from.isKindOf(RLJust) or: { from.isKindOf(RLTempered) }) and: { to.isKindOf(RLEDO) } } {
			{ |chord| target.space.chord(from.temperPc(to, chord.root), chord.type) }
		}
		{ to.isKindOf(RLTempered) and: { (from == to.just) or: { from.isKindOf(RLTempered) and: { from.just == to.just }
			and: { from.commas.every { |c| to.tempers(c) } } } } } {
			{ |chord| target.space.chord(to.reducePc(chord.root), chord.type) }
		};
		if(map.isNil) {
			Error("RLMorphism.temper: from the lattice or a temperament to a temperament of it or an equal division, not % to %".format(from, to)).throw
		};
		^this.new(source, target, map, "temper to %".format(if(to.isKindOf(RLEDO)) { to.steps } { to }))
	}

	value { |chord| ^map.value(chord) }

	// Whether the map commutes with every transformation the two contexts both name, on a sample of the
	// source's chords (all of them when it is finite and none is given; a patch round the origin otherwise).
	isValid { |sample|
		var names = source.names.select { |n| target.names.includes(n) };
		sample = sample ?? { this.prSample };
		^sample.every { |chord|
			names.every { |n| this.value(source.transform(n).value(chord)) == target.transform(n).value(this.value(chord)) }
		}
	}

	prSample {
		var system = source.system, range;
		^if(system.isFinite) { source.space.all } {
			range = (-3..3);
			range.dup(system.pcZero.size).allTuples.collect { |x|
				// in a temperament the point of the lattice stands for its class
				var root = if(system.respondsTo(\reducePc)) { system.reducePc(x) } { x };
				source.space.typeNames.collect { |t| source.space.chord(root, t) }
			}.flatten(1)
		}
	}

	// A walk of the source, carried to the target.
	push { |walk| ^RLWalk(target, this.value(walk.start), walk.word) }

	// A walk of the target, lifted to the source from a chord above its start (the source's base chord of
	// the start's type when none is given). The chord must map to the walk's start.
	lift { |walk, from|
		var above = from ?? { source.space.chord(source.system.pcZero, walk.start.type) };
		if(this.value(above) != walk.start) {
			Error("RLMorphism: % is not above %".format(above, walk.start)).throw
		};
		^RLWalk(source, above, walk.word)
	}

	printOn { |stream| stream << "RLMorphism(" << (label ? "") << ": " << source.system << " -> " << target.system << ")" }
}
