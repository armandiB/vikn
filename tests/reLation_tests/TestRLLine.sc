// reLation, a line over chords: RLLine and RLRecall. No server. The models here are made by hand, each table
// saying one thing, so that what a draw must give is known; counted models are made and measured in HomewareSC
// (Math/Category_Theory/fit/jazz_know.py, jazz_rhythm.py).
TestRLLine : UnitTest {

	prEven { |rows, n| ^Array.fill(rows) { Array.fill(n, 1 / n) } }
	// a row that allows one value only
	prOnly { |n, value| ^Array.fill(n) { |i| if(i == value) { 1.0 } { 0.0 } } }
	prPitch { |experts, tables, weights|
		^IdentityDictionary[\pitches -> [48, 72], \experts -> experts, \weights -> (weights ? IdentityDictionary.new), \tables -> tables]
	}
	prRhythm { |experts, tables, weights|
		^IdentityDictionary[\experts -> experts, \weights -> (weights ? IdentityDictionary.new), \tables -> tables,
			\sounding -> Array.fill(18) { [1.0, 0.0, 0.0] }]
	}
	prNotes { |n, dur = 0.5| ^Array.fill(n) { |i| [i * dur, dur] } }

	test_what_a_line_recalls {
		var r = RLRecall(5);
		this.assertEquals(r.chances([]), 0.2 ! 5, "with nothing seen, every value alike");
		r.learn([], 3);
		this.assert(r.chances([])[3] > r.chances([])[0], "a value that came is likelier");
		this.assertFloatEquals(r.chances([]).sum, 1.0, "the chances sum to one");
		r.learn([3], 1);
		r.learn([3, 1], 4);
		r.learn([1, 4], 3);
		r.learn([4, 3], 1);
		this.assertEquals(r.chances([3]).maxIndex, 1, "after a 3 came a 1, twice");
		this.assertEquals(r.chances([3, 1]).maxIndex, 4, "after 3 then 1 came a 4");
		this.assertEquals(r.chances([0, 3]).maxIndex, 1, "a longer memory that never came leans on the shorter one");
		this.assert(r.chances([2]).maxIndex != 2, "after a value never seen, what came most often");
		this.assertEquals(RLRecall(5, 1).depth, 1, "the depth of its memory");
	}

	test_gaps_and_chords_as_the_tables_see_them {
		this.assertEquals([1, 6, 12, 48].collect { |u| RLLine.gapKind(u) }, [0, 4, 7, 12], "a common gap is a kind of its own");
		this.assertEquals([5, 13, 30, 60, 200].collect { |u| RLLine.gapKind(u) }, [13, 14, 15, 16, 17], "the others by size");
		this.assertEquals([[0, 4, 7], [0, 4, 7, 10], [0, 3, 7, 10], [0, 3, 6, 9], [0, 4, 7, 11]].collect { |pcs| RLLine.chordKind((root: 0, pcs: pcs)) },
			[0, 1, 2, 3, 0], "a chord's kind: major, dominant, minor, diminished");
		this.assertEquals(RLLine.chordKind((root: 7, pcs: [7, 11, 2, 5])), 1, "from its root: G7");
	}

	test_a_pitch_from_its_chord_key_and_place {
		var chord = this.prEven(8, 12), key = this.prEven(2, 12), place = this.prEven(1, 49), line, notes = this.prNotes(12);
		var chords = [(onset: 0, dur: 3, root: 0, pcs: [0, 4, 7]), (onset: 3, dur: 3, root: 5, pcs: [5, 9, 0])], out;
		// a major chord (kind 0), on or off the beat: its root, third and fifth alone
		2.do { |b| chord[b] = Array.fill(12) { |i| if(#[0, 4, 7].includes(i)) { 1 / 3 } { 0.0 } } };
		line = RLLine(this.prPitch(["chord"], IdentityDictionary[\chord -> chord]));
		out = line.pitches(notes, chords, seed: 7);
		this.assertEquals(out.size, 12, "a pitch a note");
		this.assert(out.keep(6).every { |p| #[0, 4, 7].includes(p % 12) }, "over C, tones of C");
		this.assert(out.drop(6).every { |p| #[5, 9, 0].includes(p % 12) }, "over F, tones of F");
		this.assert(out.every { |p| (p >= 48) and: { p <= 72 } }, "in the model's range");
		this.assertEquals(line.pitches(notes, chords, seed: 7), out, "the same seed, the same line");
		this.assert(line.pitches(notes, chords, lowest: 60, highest: 64, seed: 7).every { |p| (p >= 60) and: { p <= 64 } }, "in a range of one's own");
		// the key: the tonic alone
		key[0] = this.prOnly(12, 0);
		line = RLLine(this.prPitch(["key"], IdentityDictionary[\key -> key]));
		this.assert(line.pitches(notes, chords, [2, \major], seed: 1).every { |p| (p % 12) == 2 }, "with a key that allows its tonic alone, D");
		this.assertEquals(line.pitches(notes, chords, seed: 1).size, 12, "with no key the key says nothing");
		// the register: the median alone
		place[0] = this.prOnly(49, 24);
		line = RLLine(this.prPitch(["place"], IdentityDictionary[\place -> place]));
		this.assertEquals(line.pitches(notes, chords, median: 65, seed: 1), 65 ! 12, "with a register that allows the median alone");
		this.assertEquals(line.pitches(notes, chords, first: 50, median: 65, seed: 1)[0], 50, "a first pitch given is kept");
	}

	test_a_pitch_from_the_steps_before {
		var step = this.prEven(2, 49), three = this.prEven(652, 49), line, notes = this.prNotes(5), phrases;
		// the step alone: a tone up, a minor third up after a rest
		step[0] = this.prOnly(49, 26);
		step[1] = this.prOnly(49, 27);
		line = RLLine(this.prPitch(["step"], IdentityDictionary[\step -> step]));
		this.assertEquals(line.pitches(notes, nil, first: 60), [60, 62, 64, 66, 68], "a tone up each time");
		phrases = [[0, 0.5], [0.5, 0.5], [3, 0.5], [3.5, 0.5]];
		this.assertEquals(line.pitches(phrases, nil, first: 60), [60, 62, 65, 67], "a rest of a beat or more starts a phrase: another table");
		// given the two steps before: after two tones up, a minor third down; with less behind it, a tone up
		three[625 + 25] = this.prOnly(49, 26);
		three[625 + 14] = this.prOnly(49, 26);
		three[(14 * 25) + 14] = this.prOnly(49, 21);
		line = RLLine(this.prPitch(["three"], IdentityDictionary[\three -> three]));
		this.assertEquals(line.pitches(notes, nil, first: 60).keep(4), [60, 62, 64, 61], "up a tone, up a tone, then down a minor third");
	}

	test_a_line_takes_up_its_own_motives {
		var line = RLLine(this.prPitch(["motive"], IdentityDictionary.new)), r = RLRecall(49), note, p;
		r.learn([], 26);
		r.learn([26], 26);
		r.learn([26, 26], 21);
		note = (prev: 64, afterRest: false, median: 60, recalled: r.chances([26, 26]));
		p = line.chances(note);
		this.assertEquals(48 + p.maxIndex, 61, "after two tones up it said a minor third down: likeliest again");
		this.assertFloatEquals(p.sum, 1.0, "chances that sum to one");
		this.assertEquals(line.chances((prev: 64, afterRest: false, median: 60)).asSet.size, 1, "with nothing recalled every pitch alike");
		this.assertEquals(line.pitches(this.prNotes(8), nil, seed: 5).size, 8, "and a line is drawn with it");
	}

	test_staying_on_a_pitch_and_the_weights_to_draw_with {
		// the steps say: mostly the same pitch again, else a tone up or down; stay says: said twice, move on
		var row = Array.fill(49) { |i| switch(i, 24, { 0.8 }, 26, { 0.1 }, 22, { 0.1 }, { 0.0 }) };
		var stay = Array.fill(8) { |r| if(r == 0) { [0.5, 0.5] } { [1.0, 0.0] } }, tables, line, out, model;
		tables = IdentityDictionary[\step -> [row, row], \stay -> stay];
		line = RLLine(this.prPitch(["step", "stay"], tables));
		out = line.pitches(this.prNotes(40), nil, first: 60, seed: 11);
		this.assert(out.slide(3).clump(3).every { |t| (t[0] != t[1]) or: { t[1] != t[2] } }, "never the same pitch three times running");
		this.assert(out.slide(2).clump(2).any { |t| t[0] == t[1] }, "though twice it may");
		this.assertEquals(line.chances((prev: 60, same: 1, median: 60))[12], 0.0, "a pitch said twice has no chance of a third time");
		// a model may say other weights for drawing than those that tell real lines best
		model = this.prPitch(["step", "stay"], tables, IdentityDictionary[\step -> 1.0, \stay -> 0.0]);
		this.assert(RLLine(model).chances((prev: 60, same: 1, median: 60))[12] > 0.5, "with no weight, stay says nothing");
		model[\draw] = IdentityDictionary[\stay -> 1.0];
		this.assertEquals(RLLine(model).chances((prev: 60, same: 1, median: 60))[12], 0.0, "its weight for drawing is taken where the model gives one");
	}

	test_a_rhythm {
		var gap = [this.prOnly(97, 5)], lands = [this.prOnly(13, 0)], tempo = this.prEven(4, 97), line, out;
		var pitch = this.prPitch(["place"], IdentityDictionary[\place -> this.prEven(1, 49)]);
		line = RLLine(pitch, this.prRhythm(["gap"], IdentityDictionary[\gap -> gap]));
		out = line.rhythm(0, 4);
		this.assertEquals(out.collect(_[0]), (0, 0.5 .. 4), "a gap of half a beat alone: a note every half beat, the last at the end");
		this.assertFloatEquals(out[0][1], 0.475, "a note that sounds all of its gap: a little under it");
		this.assertEquals(line.rhythm(2, 3).collect(_[0]), [2, 2.5, 3], "from another start");
		line = RLLine(pitch, this.prRhythm(["lands"], IdentityDictionary[\lands -> lands]));
		this.assert(line.rhythm(0, 32, seed: 3).every { |n| n[0].frac == 0 }, "notes that may land on a beat alone");
		// the tempo: a fast one allows a beat alone, and with no tempo given it says nothing
		tempo[3] = this.prOnly(97, 11);
		line = RLLine(pitch, this.prRhythm(["gap", "tempo"], IdentityDictionary[\gap -> this.prEven(1, 97), \tempo -> tempo]));
		this.assertEquals(line.rhythm(0, 4, 260, 1).collect(_[0]), [0, 1, 2, 3, 4], "at 260 beats a minute, a note a beat");
		this.assert(line.rhythm(0, 16, nil, 1).size > 1, "with no tempo, the gaps alone");
		this.assertException({ RLLine(pitch).rhythm(0, 4) }, Error, "no rhythm without a model of it");
	}

	test_a_line_over_chords_and_as_a_pattern {
		var step = [this.prOnly(49, 26), this.prOnly(49, 26)], gap = [this.prOnly(97, 11)], line, notes, stream, ev;
		line = RLLine(this.prPitch(["step"], IdentityDictionary[\step -> step]), this.prRhythm(["gap"], IdentityDictionary[\gap -> gap]));
		notes = line.draw([(onset: 0, dur: 4, root: 0, pcs: [0, 4, 7])], 0, 3, first: 60, seed: 2);
		this.assertEquals(notes.collect(_[\onset]), [0, 1, 2, 3], "a note a beat");
		this.assertEquals(notes.collect(_[\midinote]), [60, 62, 64, 66], "a tone up each");
		this.assertFloatEquals(notes[0][\dur], 0.95, "each sounding nearly its beat");
		this.assertEquals(RLLine.deltas(notes, 4), [1, 1, 1, 1], "the time from each note to the next, the last to the end");
		stream = RLLine.pbind(notes, 4).asStream;
		ev = stream.next(());
		this.assertEquals([ev[\midinote], ev[\dur]], [60, 1], "as a Pbind: its first event");
		this.assert(line.asString.contains("step") and: { line.asString.contains("gap") }, "it says what it draws with");
		this.assertException({ RLLine(this.prPitch(["mood"], IdentityDictionary.new)) }, Error, "an expert it does not know is refused");
		this.assertException({ RLLine(nil) }, Error, "and no model");
	}
}
