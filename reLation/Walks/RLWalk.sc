// reLation — a walk: a start chord and a word, the list of its steps in the order they are taken.
//
// A step is the Symbol of one of the context's transformations (\P, \L, \R, or a name it defines), or an
// RLTransform. The same word can be read in any context that knows its names: inContext carries a walk
// from 12 equal steps to 19, or to the just lattice.
//
//   ~ctx = RLChordContext(RLChordSpace.triads(RLEDO(12)));
//   ~pump = RLWalk(~ctx, ~ctx.space.major(0), [\R, \L, \R, \P, \R, \L, \R, \L]);
//   ~pump.chords;                       // C major, A minor, F major, D minor, D major, B minor, G major, E minor, C major
//   ~pump.isClosed;                     // true
//
//   ~just = RLChordContext(RLChordSpace.triads(RLJust([3, 5])));
//   ~lifted = ~pump.inContext(~just);   // the same word from 1/1 major
//   ~lifted.isClosed;                   // false
//   ~lifted.drift;                      // [-4, 1]: where the walk ends, from where it started
//   ~lifted.driftCents;                 // -21.5: a syntonic comma down
//
// The voices: voices(start) plays the walk from a voicing, each chord by the least motion from the one
// before; voiceDrift says how far each voice is from where it began once the walk is over.
//
//   ~pump.voices([60, 64, 67]).last;    // [64, 67, 72]: back on C major, one inversion higher
//   ~pump.voiceDrift([60, 64, 67]);     // [4, 3, 5]
//
// A walk is a value. A word of Symbols is the same walk in every context; one holding RLTransforms belongs
// to their context.

RLWalk {
	var <context, <start, <word, chordsCache;

	*new { |context, start, word| ^super.newCopyArgs(context, start, word.asArray) }

	// A word repeated until the walk is back on its start: (context, start, [\P, \L]) is the hexatonic cycle.
	// nil when it has not closed after maxRepeats.
	*cycle { |context, start, word, maxRepeats = 512|
		var chord = start, steps = word.asArray.collect { |w| this.prResolve(context, w) }, count = 0;
		while { count < maxRepeats } {
			steps.do { |tr| chord = tr.value(chord) };
			count = count + 1;
			if(chord == start) { ^this.new(context, start, word.asArray.wrapExtend(word.size * count)) };
		};
		^nil
	}

	*prResolve { |context, w| ^if(w.isKindOf(RLTransform)) { w } { context.transform(w) } }

	// The steps as transformations.
	transforms { ^word.collect { |w| RLWalk.prResolve(context, w) } }

	// The chords met, the start first: one more than the word has steps.
	chords {
		^chordsCache ?? {
			var chord = start, out = [start];
			this.transforms.do { |tr| chord = tr.value(chord); out = out.add(chord) };
			chordsCache = out;
			out
		}
	}

	end { ^this.chords.last }
	size { ^word.size }
	isClosed { ^this.end == start }

	// The whole walk as one transformation.
	composed {
		var tr = context.identityTransform;
		this.transforms.do { |step| tr = step <> tr };
		^tr
	}

	// Where the walk ends from where it started: the interval between the two roots when the types agree,
	// nil when they differ. Zero for a closed walk; a comma for the lift of one (see RLMorphism).
	drift {
		^if(this.end.type == start.type) { context.system.pcSub(this.end.root, start.root) }
	}
	driftCents { ^this.drift !? { |d| context.system.cents(d) } }

	// The same word in another context, from a chord of its own (its base chord of the same type when none
	// is given).
	inContext { |other, from|
		^RLWalk(other, from ?? { other.space.chord(other.system.pcZero, start.type) }, word)
	}

	// The word n times; one walk after another.
	repeated { |n| ^RLWalk(context, start, word.wrapExtend(word.size * n)) }
	++ { |that| ^RLWalk(context, start, word ++ that.word) }

	// The voicings of its chords, from a voicing of the start (close position at the origin when none is
	// given): one Array of pitches per chord. More voices than the chords have tones double some.
	voices { |from| ^RLVoiceLeading.through(this.chords, from) }

	// How far each voice ends from where it began, as pitch intervals (steps in RLEDO).
	voiceDrift { |from|
		var all = this.voices(from), system = context.system;
		^all.last.collect { |p, i| system.pitchSub(p, all.first[i]) }
	}

	// The total motion of the voices in cents, step by step.
	voiceCents { |from, measure = \l1|
		var all = this.voices(from), system = context.system, out = Array.new;
		all.doAdjacentPairs { |a, b| out = out.add(RLVoiceLeading.cents(system, a, b, measure)) };
		^out
	}

	// The voicings as frequencies, for an origin at base Hz.
	freqs { |from, base = 261.6255653006|
		var system = context.system;
		^this.voices(from).collect { |v| v.collect { |p| system.freq(p, base) } }
	}

	// The frequencies as a pattern of chords: Pbind(\freq, walk.asPattern, \dur, 1).
	asPattern { |from, base = 261.6255653006, repeats = 1| ^Pseq(this.freqs(from, base), repeats) }

	== { |other|
		^other.isKindOf(RLWalk) and: { start == other.start } and: { word == other.word } and: { context == other.context }
	}
	!= { |other| ^(this == other).not }
	hash { ^[\RLWalk, start.hash, word].hash }

	printOn { |stream|
		stream << "RLWalk(" << start << ", " << word.collect { |w| if(w.isKindOf(Symbol)) { w.asString } { w.asString } }.join(" ") << ")"
	}
}
