// reLation — a line over chords: a rhythm and pitches drawn note by note from what a note knows.
//
// The reverse of a reading: where RLFit prices the chords a piece has, RLLine draws notes over chords it is
// given. A note's pitch is drawn from a product of small counted tables ("experts"), each saying how likely a
// pitch is from one point of view, each raised to a weight: the steps before it, the chord under it, the key,
// the register, what the line itself has done so far after the same steps (its motives: RLRecall), and how long
// it has stayed on one pitch. A note's start is drawn the same way: the gaps before it, where in the beat it
// lands, the tempo. The tables are counted on solos and kept where solos not counted cost fewer bits with them,
// or where drawn lines are nearer the players' in what they are made of (HomewareSC, Math/Category_Theory/fit:
// jazz_know.py and jazz_rhythm.py write the two models, jazz_shape.py measures drawn lines; counted on bebop,
// hard bop, cool and swing solos of the Weimar Jazz Database, a note's pitch costs 2.98 bits there and Charlie
// Parker's, never counted, 2.87; lines drawn come back to their own figures as often as his do).
//
//   ~line = RLLine.read(pitchModelPath, rhythmModelPath);
//   ~chords = [(onset: 0, dur: 4, root: 5, pcs: [5, 9, 0, 3]), (onset: 4, dur: 4, root: 10, pcs: [10, 2, 5, 8])];   // F7, Bb7
//   ~notes = ~line.draw(~chords, 0, 8, key: [5, \major], tempo: 180, seed: 3);      // Events: onset, dur, midinote
//   RLLine.pbind(~notes, 8).play(TempoClock(3));
//   // in a piece (reCurrent): the line as a beat
//   ~beat = ~song.layer(\core).addBeat(\line, [instrument: \default, dur_flex: RLLine.deltas(~notes, 8), midinote: Pseq(~notes.collect(_[\midinote]), inf)]);
//
//   ~line.pitches([[0, 0.5], [0.5, 0.5], [1, 1]], ~chords, [5, \major]);   // pitches for a rhythm of one's own
//   ~line.rhythm(0, 8, 180);                                              // a rhythm alone: [[onset, dur], ...]
//
// Pitches are MIDI notes and pitch classes those of twelve steps: the models are counted on such notes. Time is
// in beats. What is not modelled: the phrase (when a line breathes, how long it runs: three counts of it gave
// nothing), dynamics, articulation beyond how long a note sounds within its gap.

RLLine {
	classvar <common;
	var <pitchModel, <rhythmModel, <low, <high;

	*initClass { common = #[1, 2, 3, 4, 6, 8, 9, 12, 16, 18, 24, 36, 48] }

	*new { |pitchModel, rhythmModel| ^super.newCopyArgs(pitchModel, rhythmModel).prInit }
	*read { |pitchPath, rhythmPath| ^this.new(this.readModel(pitchPath), rhythmPath !? { this.readModel(rhythmPath) }) }
	*readModel { |path|
		var m = if(File.exists(path.asString.standardizePath)) { REJSON.read(path) };
		if(m.isNil or: { m[\experts].isNil }) { Error("RLLine: no model at %".format(path)).throw };
		^m
	}

	prInit {
		if(pitchModel.isNil or: { pitchModel[\experts].isNil }) { Error("RLLine: a model of the pitches is needed (jazz_know.py --write)").throw };
		low = pitchModel[\pitches][0].asInteger;
		high = pitchModel[\pitches][1].asInteger;
		this.pitchExperts.do { |n|
			if(#[\step, \two, \three, \chord, \key, \place, \motive, \stay].includes(n).not) { Error("RLLine: no expert named % for a pitch".format(n)).throw }
		};
		rhythmModel !? {
			this.rhythmExperts.do { |n|
				if(#[\gap, \last, \two, \lands, \tempo].includes(n).not) { Error("RLLine: no expert named % for a rhythm".format(n)).throw }
			}
		};
	}

	// The experts of each model, in its order, and a table's row.
	pitchExperts { ^pitchModel[\experts].collect(_.asSymbol) }
	rhythmExperts { ^(rhythmModel !? { |m| m[\experts] } ? []).collect(_.asSymbol) }
	// An expert's weight for drawing: the model's, but the one it says for drawing when it says one (\draw): the
	// weights that tell real lines best flatten each table, and a line drawn with them may stick on a pitch.
	prWeight { |model, name| ^(model[\draw] !? { |d| d[name] }) ? model[\weights][name] ? 1.0 }
	prRow { |model, name, ctx| ^model[\tables][name][ctx] }

	// A gap in twelfths of a beat as what came before: the common ones each their own, the others by size.
	*gapKind { |u|
		var i = common.indexOf(u);
		^i ?? { common.size + if(u < 12) { 0 } { if(u < 24) { 1 } { if(u < 48) { 2 } { if(u <= 96) { 3 } { 4 } } } } }
	}
	// A chord's kind from its tones above its root: 0 major, 1 dominant, 2 minor, 3 diminished.
	*chordKind { |chord|
		var root = chord[\root], above = chord[\pcs].collect { |pc| (pc - root) % 12 };
		^case
		{ above.includes(3) and: { above.includes(6) } } { 3 }
		{ above.includes(3) } { 2 }
		{ above.includes(4) and: { above.includes(10) } } { 1 }
		{ 0 }
	}

	// A rhythm from start to end, in beats: [[onset, how long it sounds], ...]. tempo in beats a minute (nil:
	// what the tempo says is left out). The first note is at start; a gap that would pass the end ends it.
	rhythm { |start = 0, end = 16, tempo, seed, heat = 1|
		var m = rhythmModel, names = this.rhythmExperts, k = this.class.common.size + 5, long = 96;
		var q = (start * 12).round.asInteger, stop = (end * 12).round.asInteger, starts, going = true;
		var speed = tempo !? { if(tempo < 120) { 0 } { if(tempo < 180) { 1 } { if(tempo < 240) { 2 } { 3 } } } };
		if(m.isNil) { Error("RLLine: a model of the rhythm is needed (jazz_rhythm.py --write)").throw };
		seed !? { thisThread.randSeed = seed };
		starts = [q];
		while { going } {
			var n = starts.size, before = if(n >= 2) { starts[n - 1] - starts[n - 2] } { 0 }, before2 = if(n >= 3) { starts[n - 2] - starts[n - 3] } { 0 };
			var last = if(before > 0) { this.class.gapKind(before) } { k }, phase = q % 12, z = Array.fill(long + 1, 0.0), p, gap;
			names.select { |name| this.prWeight(m, name) != 0 }.do { |name|
				var w = this.prWeight(m, name), row;
				switch(name,
					\gap, { z = z + (w * this.prRow(m, name, 0).log) },
					\last, { z = z + (w * this.prRow(m, name, last).log) },
					\two, {
						row = this.prRow(m, name, if((before > 0) and: { before2 > 0 }) { (last * k) + this.class.gapKind(before2) } { (k * k) + last });
						z = z + (w * row.log)
					},
					\lands, {
						row = this.prRow(m, name, 0);
						z = z + (w * Array.fill(long + 1) { |c| row[if(c < long) { (phase + c + 1) % 12 } { 12 }] }.log)
					},
					\tempo, { speed !? { z = z + (w * this.prRow(m, name, speed).log) } }
				);
			};
			z[long] = -inf;
			p = ((z - z.maxItem) / heat).exp.normalizeSum;
			gap = p.windex + 1;
			if((q + gap) > stop) { going = false } { q = q + gap; starts = starts.add(q) };
		};
		// how long each note sounds within its gap: all of it, about half, a short note
		^starts.collect { |s, i|
			var gap = if((i + 1) < starts.size) { starts[i + 1] - s } { 12 };
			var share = #[0.95, 0.55, 0.25][m[\sounding][this.class.gapKind(gap)].normalizeSum.windex];
			[s / 12, max(gap * share, 1) / 12]
		}
	}

	// The chances of every pitch (low to high) for one note, given what it knows: an Event with \prev, \before,
	// \before2 (the pitches before it in its phrase, nil when none), \afterRest, \root and \kind (its chord, nil
	// when none), \beat (whether it starts on one), \tonic and \minor (the key, nil when none), \median, \same
	// (how many times the pitch before had been said already), and \recalled (the chances its own line gives
	// each step, from an RLRecall; nil when none).
	chances { |note, heat = 1, lowest, highest|
		var m = pitchModel, lo = lowest ? low, hi = highest ? high, pitches = (low..high), z = Array.fill(pitches.size, 0.0);
		var prev = note[\prev], before = note[\before], before2 = note[\before2], after = if(note[\afterRest] == true) { 1 } { 0 };
		var step = { |p| (p - prev).clip(-24, 24) + 24 }, a, b, two;
		if(prev.notNil) {
			a = before !? { (prev - before).clip(-12, 12) + 12 };
			b = before2 !? { (before - before2).clip(-12, 12) + 12 };
			two = a ? (25 + after);
		};
		// an expert of no weight says nothing, whatever its table holds (0 * -inf is not a number)
		this.pitchExperts.select { |name| this.prWeight(m, name) != 0 }.do { |name|
			var w = this.prWeight(m, name), row;
			switch(name,
				\step, { prev !? { row = this.prRow(m, name, after); z = z + (w * pitches.collect { |p| row[step.(p)] }.log) } },
				\two, { prev !? { row = this.prRow(m, name, two); z = z + (w * pitches.collect { |p| row[step.(p)] }.log) } },
				\three, {
					prev !? {
						row = this.prRow(m, name, if(a.notNil and: { b.notNil }) { (a * 25) + b } { 625 + two });
						z = z + (w * pitches.collect { |p| row[step.(p)] }.log)
					}
				},
				\chord, {
					note[\root] !? { |root|
						row = this.prRow(m, name, ((note[\kind] ? 0) * 2) + if(note[\beat] == true) { 1 } { 0 });
						z = z + (w * pitches.collect { |p| row[(p - root) % 12] }.log)
					}
				},
				\key, {
					note[\tonic] !? { |tonic|
						row = this.prRow(m, name, if(note[\minor] == true) { 1 } { 0 });
						z = z + (w * pitches.collect { |p| row[(p - tonic) % 12] }.log)
					}
				},
				\place, { row = this.prRow(m, name, 0); z = z + (w * pitches.collect { |p| row[(p - note[\median]).clip(-24, 24) + 24] }.log) },
				\motive, { note[\recalled] !? { |r| z = z + (w * pitches.collect { |p| r[step.(p)] }.log) } },
				\stay, {
					prev !? {
						row = this.prRow(m, name, (note[\same] ? 0).min(7));
						z = z + (w * pitches.collect { |p| row[if(p == prev) { 1 } { 0 }] }.log)
					}
				}
			);
		};
		z = pitches.collect { |p, i| if((p >= lo) and: { p <= hi }) { z[i] / heat } { -inf } };
		^(z - z.maxItem).exp.normalizeSum
	}

	// Pitches for a rhythm: notes are [onset, how long it sounds] in beats, in order. chords: Events with \onset,
	// \dur, \root and \pcs (pitch classes of twelve steps), in order; key: [tonic pitch class, \major or \minor],
	// or nil; first: the first pitch (drawn like the others when nil); median: the pitch the line stays about
	// (the middle of its range when nil); lowest, highest: its range (the model's when nil). A rest of a beat or
	// more starts a new phrase. Returns the MIDI notes.
	pitches { |notes, chords, key, first, median, lowest, highest, seed, heat = 1|
		var lo = lowest ? low, hi = highest ? high, mid = median ?? { (lo + hi) div: 2 }, line = Array.new(notes.size);
		var recall = if(this.pitchExperts.includes(\motive)) { RLRecall(49, 4, pitchModel[\lean] ? 1.0) }, last = [], run = 0, same = 0, ci = 0, list = chords ? [];
		seed !? { thisThread.randSeed = seed };
		notes.do { |n, i|
			var onset = n[0], after = (i == 0) or: { (onset - (notes[i - 1][0] + notes[i - 1][1])) >= 1.0 }, chord, note, p, pitch;
			while { ((ci + 1) < list.size) and: { (list[ci][\onset] + list[ci][\dur]) <= (onset + 1e-6) } } { ci = ci + 1 };
			chord = list[ci] !? { |c| if(((c[\onset] - 1e-6) <= onset) and: { onset < (c[\onset] + c[\dur]) }) { c } };
			run = if(after) { 0 } { run + 1 };
			if(after) { last = [] };
			// how many times the pitch before had been said already
			same = if((i < 2) or: { line[i - 1] != line[i - 2] }) { 0 } { (same + 1).min(7) };
			note = (prev: if(i > 0) { line[i - 1] }, before: if(run >= 2) { line[i - 2] }, before2: if(run >= 3) { line[i - 3] }, same: same,
				afterRest: after, root: chord !? { |c| c[\root] }, kind: chord !? { |c| this.class.chordKind(c) },
				beat: (onset - onset.round).abs < 0.02, tonic: key !? { |k| k[0] }, minor: key !? { |k| k[1] == \minor }, median: mid,
				recalled: if(recall.notNil and: { i > 0 } and: { after.not }) { recall.chances(last) });
			pitch = if((i == 0) and: { first.notNil }) { first } {
				p = this.chances(note, heat, lo, hi);
				low + p.windex
			};
			line = line.add(pitch);
			if(recall.notNil and: { i > 0 } and: { after.not }) {
				var value = (pitch - line[i - 1]).clip(-24, 24) + 24;
				recall.learn(last, value);
				last = (last ++ [value]).keep(recall.depth.neg);
			};
		};
		^line
	}

	// A line over chords from start to end, in beats: a rhythm, then pitches on it. Events with \onset, \dur (how
	// long the note sounds) and \midinote.
	draw { |chords, start = 0, end = 16, key, first, median, lowest, highest, tempo, seed, heat = 1|
		var notes, line;
		seed !? { thisThread.randSeed = seed };
		notes = this.rhythm(start, end, tempo, nil, heat);
		line = this.pitches(notes, chords, key, first, median, lowest, highest, nil, heat);
		^notes.collect { |n, i| (onset: n[0], dur: n[1], midinote: line[i]) }
	}

	// From a line's notes: the time from each note to the next (the last to the end), for a beat's durations or
	// a Pbind's \dur; and the line as a Pbind (the rest before its first note left out).
	*deltas { |notes, end|
		^notes.collect { |n, i| if((i + 1) < notes.size) { notes[i + 1][\onset] } { end ? (n[\onset] + n[\dur]) } - n[\onset] }
	}
	*pbind { |notes, end, repeats = 1|
		^Pbind(\midinote, Pseq(notes.collect(_[\midinote]), repeats), \dur, Pseq(this.deltas(notes, end), repeats),
			\sustain, Pseq(notes.collect(_[\dur]), repeats))
	}

	printOn { |stream|
		stream << "RLLine(pitches: " << this.pitchExperts.join(", ");
		rhythmModel !? { stream << "; rhythm: " << this.rhythmExperts.join(", ") };
		stream << ")"
	}
}

// What a line has done so far: after the same last values (its last steps, its last gaps), what it did next.
// Orders 0 (how often each value came) to depth, each leaning on the one below; with nothing seen, every value
// alike. A figure that comes back in a line is one it has already said: RLLine draws with it, so that a line
// takes up its own motives.
//
//   ~r = RLRecall(49);                 // 49 values: steps of -24 to 24 semitones
//   ~r.learn([], 26); ~r.learn([26], 26); ~r.learn([26, 26], 21);     // up a tone, up a tone, down a minor third
//   ~r.chances([26, 26])[21];          // after two tones up, the minor third down is now the likeliest

RLRecall {
	var <size, <depth, <lean, seen;

	*new { |size, depth = 4, lean = 1.0| ^super.newCopyArgs(size, depth, lean).prInit }
	prInit { seen = Array.fill(depth + 1) { Dictionary.new } }

	// The chances of each value after these last values (the most recent last; at most depth are looked at).
	chances { |last|
		var p = Array.fill(size, 1 / size);
		(min(depth, last.size) + 1).do { |k|
			var c = seen[k][last.keep(k.neg)];
			c !? { p = (c + (lean * p)) / (c.sum + lean) };
		};
		^p
	}

	// What came after these last values.
	learn { |last, value|
		(min(depth, last.size) + 1).do { |k|
			var key = last.keep(k.neg), c = seen[k][key];
			if(c.isNil) { c = Array.fill(size, 0); seen[k][key] = c };
			c[value] = c[value] + 1;
		};
	}

	printOn { |stream| stream << "RLRecall(" << size << " values, depth " << depth << ")" }
}
