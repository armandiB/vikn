// reLation — a score: the notes of a piece on one time line, and what analysts wrote over them.
//
// Read from the JSON file HomewareSC's Math/Category_Theory/fit/dcml_read.py writes from a corpus of annotated
// scores. Time is in quarter notes from the start. Notes are spelled (their place on the line of fifths, tpc: C 0,
// G 1, F -1), so the same score is read into any equal division that has a fifth: 12 steps, 19, 31.
//
//   ~score = RLScore.read(path);               // in RLEDO(12)
//   ~score.notes.first;                        // (onset: 0.0, dur: 1.0, pitch: 0, pc: 0, tpc: 0, staff: 1, mn: 0)
//   ~score.chords.first;                       // (onset:, dur:, mn:, label: "i", key: [0, \minor], root:, pcs: [5, 8, 0], ...)
//   ~score.regions;                            // RLRegions round the piece's global tonic
//   ~score.weights(0, 8);                      // pitch class -> quarter notes sounded in that span
//   ~score.coverage(0, 8, [5, 8, 0]);          // [covered, total]: how much of the span's notes these tones hold
//   RLScore.read(path, RLEDO(19)).notes.first; // the same note in 19 steps
//
// A pitch is the system's (steps from its origin, middle C); chords written by analysts keep their label, their
// key as a region [fifths, mode] from the global tonic, their root and tones as pitch classes.

RLScore {
	var <system, <notes, <chords, <marks, <measures, <info;

	*new { |system, notes, chords, marks, measures, info|
		^super.newCopyArgs(system ?? { RLEDO(12) }, notes ? [], chords ? [], marks ? [], measures ? [], info ? IdentityDictionary.new)
	}

	*read { |path, system|
		var sys = system ?? { RLEDO(12) }, d = REJSON.read(path), global, base, notes, chords, fifth, mode;
		if(d.isNil) { Error("RLScore: nothing read at %".format(path)).throw };
		if(sys.isKindOf(RLEDO).not) { Error("RLScore: an RLEDO is needed to place spelled notes, not %".format(sys)).throw };
		global = d[\global];
		mode = global[\mode].asSymbol;
		fifth = sys.pitchInterval([3, 2]);
		base = global[\tpc];
		notes = d[\notes].collect { |n|
			var pitch = this.pitchOf(sys, n[2], n[3]);
			(onset: n[0].asFloat, dur: n[1].asFloat, pitch: pitch, pc: sys.pc(pitch), tpc: n[3], staff: n[4], mn: n[6])
		};
		chords = d[\harmonies].collect { |h|
			var pcOf = { |f| sys.pc((base + f) * fifth) };
			(
				onset: h[\onset].asFloat, dur: h[\dur].asFloat, mn: h[\mn], label: h[\label].asString,
				key: [h[\key][0], h[\key][1].asSymbol], root: pcOf.(h[\root]), bass: h[\bass] !? { |b| pcOf.(b) },
				pcs: h[\tones].collect { |f| pcOf.(f) }, type: h[\type].asString, numeral: h[\numeral].asString
			)
		};
		^this.new(sys, notes, chords, d[\marks] ? [], d[\measures] ? [],
			IdentityDictionary[\piece -> d[\piece].asString, \source -> d[\source].asString, \tpc -> base, \mode -> mode,
				\name -> global[\name].asString, \duration -> d[\duration].asFloat])
	}

	// A spelled note as a pitch of an equal division: its fifths, and the octaves that put it at its MIDI height.
	*pitchOf { |system, midi, tpc|
		var octaves = (midi - 60 - (7 * tpc)).div(12);
		^(tpc * system.pitchInterval([3, 2])) + (octaves * system.steps)
	}

	duration { ^info[\duration] ?? { notes.collect { |n| n[\onset] + n[\dur] }.maxItem ? 0 } }

	// The global tonic as a pitch class, and the regions round it.
	tonic { ^system.pc((info[\tpc] ? 0) * system.pitchInterval([3, 2])) }
	regions { ^RLRegions(system, this.tonic, info[\mode] ? \major) }

	// The notes sounding in a span, each with the part of it inside: [[note, quarter notes], ...].
	sounding { |from, to|
		var out = Array.new;
		notes.do { |n|
			var a = max(n[\onset], from), b = min(n[\onset] + n[\dur], to);
			if(b > (a + 1e-9)) { out = out.add([n, b - a]) };
		};
		^out
	}

	// pitch class -> quarter notes sounded in a span.
	weights { |from, to|
		var w = Dictionary.new;
		this.sounding(from, to).do { |pair| w[pair[0][\pc]] = (w[pair[0][\pc]] ? 0) + pair[1] };
		^w
	}

	// How much of what sounds in a span is one of these pitch classes: [covered, total], in quarter notes.
	coverage { |from, to, pcs|
		var covered = 0, total = 0;
		this.weights(from, to).keysValuesDo { |pc, w|
			total = total + w;
			if(pcs.includesEqual(pc)) { covered = covered + w };
		};
		^[covered, total]
	}

	// The measure a time falls in, as the score numbers it, and the chord an analyst wrote there.
	measureAt { |time|
		var found = measures.first;
		measures.do { |m| if(m[1] <= (time + 1e-9)) { found = m } };
		^found !? { found[0] }
	}
	chordAt { |time| ^chords.detect { |c| (c[\onset] <= (time + 1e-9)) and: { time < (c[\onset] + c[\dur] - 1e-9) } } }

	// The part of the score between two measures (inclusive), times kept.
	between { |firstMeasure, lastMeasure|
		var keep = { |mn| (mn >= firstMeasure) and: { mn <= lastMeasure } };
		^RLScore(system, notes.select { |n| keep.(n[\mn]) }, chords.select { |c| keep.(c[\mn]) },
			marks.select { |m| keep.(m[1]) }, measures.select { |m| keep.(m[0]) }, info)
	}

	printOn { |stream|
		stream << "RLScore(" << (info[\piece] ? "") << ": " << notes.size << " notes, " << chords.size << " chords, "
			<< this.duration << " quarter notes, " << system << ")"
	}
}
