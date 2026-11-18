// reLation — a walk shown on HomewareVisuals' lattice page (/lattice/): the plane of fifths and major thirds,
// and the torus an equal division folds it into, or the cylinder of a temperament of one comma (meantone).
//
// The page draws triads of the just lattice of 3 and 5, and tetrads of the lattice of 3, 5 and 7 (4:5:6:7 and
// its inversion: a third axis, and the division's steps round a circle in place of the torus). A walk of an
// equal division is shown by its lift: the same word from 1/1 (RLLattice.show does it), or, for a walker, a
// shadow that takes the same steps on the lattice (RLWalker:follow). Messages go through RCVisuals, as every
// page's do.
//
//   ~ctx = RLChordContext(RLChordSpace.triads(RLEDO(12)));
//   ~pump = RLWalk(~ctx, ~ctx.space.major(0), [\R, \L, \R, \P, \R, \L, \R, \L]);
//   RLLattice.show(~pump);                     // the division, then the nine chords of the lift
//
//   ~four = RLChordContext(RLChordSpace.tetrads(RLEDO(31)));
//   RLLattice.show(RLWalk(~four, ~four.space.otonal, [\K12, \K01, \K12, \K01, \K12, \K02, \K12, \K02, \K01, \K13]));
//                                              // two fifths and two thirds up, a seventh down: 225/224 away
//
//   ~walker = RLWalker(~ctx, ~ctx.space.major(0), [60, 64, 67], 8.1757989156, 0.75).follow;
//   RLLattice.config(~ctx.system);  RLLattice.clear;
//   // in a beat: each chord reaches the page when its sound starts (put the key after freq)
//   ~beat = ~song.layer(\core).addBeat(\harmony, [instrument: \default, dur_flex: 2,
//       freq: ~walker.asPattern(Pseq([\R, \L], inf)), lattice: RLLattice.pfunc(~walker)]);

RLLattice {

	// Tell the page what folds the lattice: an equal division (its steps, those of its fifth, of its major
	// third and of its harmonic seventh: the torus, or the circle for tetrads), a temperament of one comma
	// (RLTempered on the lattice of 3 and 5: the comma, for the cylinder), or nothing (nil).
	*config { |system|
		^case
		{ system.isNil } { RCVisuals.send('/lattice/config', 0, 0, 0, 0) }
		{ system.isKindOf(RLTempered) } {
			if((system.primes != [3, 5]) or: { system.basis.size != 1 }) {
				Error("RLLattice: the page draws a temperament of one comma of the lattice of 3 and 5, not %".format(system)).throw
			};
			RCVisuals.send('/lattice/comma', system.basis[0][1], system.basis[0][2])
		}
		{ RCVisuals.send('/lattice/config', system.steps, system.interval([3, 2]), system.interval([5, 4]), system.interval([7, 4])) }
	}

	*clear { ^RCVisuals.send('/lattice/clear') }

	// Whether a chord is a tetrad (four tones): the page then has a message of its own.
	*isTetrad { |chord| ^chord.pcs.size == 4 }
	*path { |chord| ^if(this.isTetrad(chord)) { '/lattice/tetrad' } { '/lattice/chord' } }

	// The context of the lattice that lifts a chord's walks: triads on RLJust([3, 5]), tetrads on RLJust([3, 5, 7]).
	*just { |chord|
		^RLChordContext(if(this.isTetrad(chord)) { RLChordSpace.tetrads(RLJust([3, 5, 7])) } { RLChordSpace.triads(RLJust([3, 5])) })
	}

	// The arguments of the page's message for a chord of the lattice: [fifths, thirds, type, step] for a triad
	// of RLJust([3, 5]) (/lattice/chord), [fifths, thirds, sevenths, type, step] for a tetrad of
	// RLJust([3, 5, 7]) (/lattice/tetrad).
	*args { |chord, k = 0|
		var want = if(this.isTetrad(chord)) { 3 } { 2 };
		if(chord.system.isKindOf(RLJust).not or: { chord.root.size != want } or: { chord.pcs.size != (want + 1) }) {
			Error("RLLattice: a triad of RLJust([3, 5]) or a tetrad of RLJust([3, 5, 7]) is needed, not %".format(chord)).throw
		};
		^chord.root ++ [chord.typeIndex, k]
	}

	*chord { |chord, k = 0| ^RCVisuals.sendArgs(this.path(chord), this.args(chord, k)) }

	// A whole walk at once: the division or the temperament, a clear, then every chord. A walk of an equal
	// division or of a temperament is lifted to the lattice from 1/1 (edo is then its own system); a walk of
	// the lattice is shown as it is.
	*show { |walk, edo|
		var system = walk.context.system, up = walk;
		if(system.isKindOf(RLJust).not) {
			edo = edo ? system;
			up = walk.inContext(this.just(walk.start));
		};
		this.config(edo);
		this.clear;
		up.chords.do { |c, k| this.chord(c, k) };
		^up
	}

	// For a beat or a Pbind: a key whose value sends the walker's place on the lattice when each event's sound
	// starts (the walker must stand on the lattice, or follow there: RLWalker:follow).
	*pfunc { |walker|
		^RCVisuals.notePfunc(this.path(walker.latticeChord), { |ev| this.args(walker.latticeChord, walker.count) })
	}
}
