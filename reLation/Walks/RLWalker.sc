// reLation — a walker: a chord and its voices, taking one step at a time. For walks that have no end, whose
// word is a pattern and can change while they sound.
//
// An RLWalk is a whole walk, known in advance. A walker holds only where it is: the chord, the voicing, and a
// count of steps. step(name) moves it by one of its context's transformations or relations, each voice by the
// least motion; where a relation offers several chords, it takes the nearest (or what its chooser says).
// asPattern(word) gives the frequencies of each chord as the word goes by: the value of \freq in a Pbind or
// in a beat of reCurrent. Started again, the pattern goes on from where the walker stands.
//
//   ~ctx = RLChordContext(RLChordSpace.triads(RLEDO(12)));
//   ~walker = RLWalker(~ctx, ~ctx.space.major(0), [60, 64, 67], 8.1757989156);     // the origin at MIDI note 0
//   ~walker.step(\R).chord;                    // A minor
//   ~walker.voicing;                           // [60, 64, 69]
//   Pbind(\freq, ~walker.asPattern(Prand([\P, \L, \R], inf)), \dur, 1).play;
//
//   // in a piece (reCurrent): the word changes on the next beat, the harmony goes on from where it is
//   ~beat = ~song.layer(\core).addBeat(\harmony, [instrument: \default, dur_flex: 2, freq: ~walker.asPattern(Pseq([\R, \L], inf))]);
//   ~beat.set(\freq, ~walker.asPattern(Pseq([\P, \L], inf)));
//
// Voices that climb or sink for ever (the R, L chain; "Take a Bow") are brought back by octaves when their
// middle leaves the reach around the centre.

RLWalker {
	var <context, <chord, <voicing, <>base, <>centre, <>reach, <>chooser, <count = 0, <shadow;

	// voicing: pitches of the start chord (close position at the origin when nil); base: the frequency of the
	// origin; reach: how far, in octaves, the middle of the voices may go from where it started (nil: no limit).
	*new { |context, start, voicing, base = 261.6255653006, reach|
		^super.newCopyArgs(context, start, nil, base, nil, reach).prInit(voicing)
	}

	prInit { |v|
		// through checks that the voicing is the chord (more voices than tones are allowed)
		voicing = RLVoiceLeading.through([chord], v).first;
		centre = this.middle;
	}

	system { ^chord.system }

	// The middle of the voices, in octaves above the origin; the frequencies of the voices.
	middle { ^voicing.collect { |p| this.system.height(p) }.mean }
	freqs { ^voicing.collect { |p| this.system.freq(p, base) } }

	// Put the walker on a chord: with a voicing of it, or by the least motion from where the voices are.
	moveTo { |aChord, aVoicing|
		chord = aChord;
		voicing = aVoicing ?? { RLVoiceLeading.minimalDoubled(this.system, voicing, aChord.pcs) };
		this.prRecentre;
	}

	// One step by a name of the context (or a transformation, or a relation). Where several chords are
	// offered, the chooser picks: a Function (options, walker) -> chord; when nil, the chord the voices reach
	// by the least motion.
	step { |name|
		var options = context.act(name, chord), next;
		if(options.isEmpty) { Error("RLWalker: % offers no chord from %".format(name, chord)).throw };
		next = if(options.size == 1) { options[0] } {
			if(chooser.notNil) { chooser.value(options, this) } { this.prNearest(options) }
		};
		count = count + 1;
		this.moveTo(next);
		shadow !? { shadow.step(name) };
	}

	// A shadow on the just lattice that takes every step the walker takes, from 1/1 (or from a chord given):
	// where the walk really goes, for the lattice page (RLLattice). For walkers of a chord context of triads,
	// stepped by names.
	follow { |start|
		var just = RLChordContext(RLChordSpace.triads(RLJust([3, 5])));
		shadow = RLWalker(just, start ?? { just.space.chord(just.system.pcZero, chord.type) });
	}
	// The walker's chord on the lattice: its shadow's, or its own when it stands there itself.
	latticeChord { ^if(shadow.notNil) { shadow.chord } { chord } }

	prNearest { |options|
		var system = this.system, best, bestCost = inf;
		options.do { |c|
			var cost = RLVoiceLeading.cents(system, voicing, RLVoiceLeading.minimalDoubled(system, voicing, c.pcs));
			if(cost < (bestCost - 1e-9)) { bestCost = cost; best = c };
		};
		^best
	}

	prRecentre {
		var system, shift;
		if(reach.notNil and: { centre.notNil }) {
			system = this.system;
			shift = (centre - this.middle).round.asInteger;
			if((centre - this.middle).abs > reach and: { shift != 0 }) {
				voicing = voicing.collect { |p| system.pitchAdd(p, if(shift > 0) { system.octave } { system.pitchNeg(system.octave) }) };
				this.prRecentre;
			};
		};
	}

	// A step, then the frequencies.
	next { |name| this.step(name); ^this.freqs }

	// The frequencies of each chord as a word goes by: first the chord the walker stands on, then one step for
	// each name the word (a pattern of names, or an Array taken once) gives. Ends when the word does.
	asPattern { |word|
		^Prout {
			var stream = (if(word.isKindOf(SequenceableCollection)) { Pseq(word) } { word }).asStream, name;
			this.freqs.yield;
			while { (name = stream.next).notNil } { this.next(name).yield };
		}
	}

	printOn { |stream| stream << "RLWalker(" << chord << ", " << voicing << ")" }
}
