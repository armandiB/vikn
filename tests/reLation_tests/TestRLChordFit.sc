// reLation — RLChordFit: the chords of a score read from its notes alone.

TestRLChordFit : UnitTest {
	// a score in 4/4 from rows [onset, dur, midi, tpc] (tpc: the place on the line of fifths, C 0, G 1, F -1)
	prScore { |rows, bars = 4|
		var sys = RLEDO(12);
		var notes = rows.collect { |r|
			var pitch = RLScore.pitchOf(sys, r[2], r[3]);
			(onset: r[0].asFloat, dur: r[1].asFloat, pitch: pitch, pc: sys.pc(pitch), tpc: r[3], staff: 1, mn: (r[0] / 4).floor.asInteger + 1)
		};
		^RLScore(sys, notes, [], [], Array.fill(bars) { |i| [i + 1, i * 4.0, 4.0, "4/4"] },
			IdentityDictionary[\tpc -> 0, \mode -> \major, \duration -> (bars * 4.0), \piece -> "test"])
	}
	// [midi, tpc] pairs sounding together
	prBlock { |onset, dur, tones| ^tones.collect { |t| [onset, dur, t[0], t[1]] } }
	prLabels { |fit| ^fit.chords.collect { |c| c[\label] } }
	prCadence {
		var c = [[48, 0], [64, 4], [67, 1], [72, 0]], f = [[53, -1], [65, -1], [69, 3], [72, 0]], g7 = [[55, 1], [62, 2], [65, -1], [71, 5]];
		^this.prScore(this.prBlock(0, 4, c) ++ this.prBlock(4, 4, f) ++ this.prBlock(8, 4, g7) ++ this.prBlock(12, 4, c))
	}

	test_block_chords {
		var score = this.prCadence, fit = RLChordFit(score), read;
		this.assertEquals(this.prLabels(fit), ["C M", "F M", "G Mm7", "C M"], "four chords, a bar each");
		this.assertEquals(fit.chords.collect { |x| x[\onset] }, [0.0, 4.0, 8.0, 12.0], "where they start");
		this.assertEquals(fit.chords[2][\pcs], [7, 11, 2, 5], "the dominant seventh's tones from its root up");
		this.assertEquals(fit.chords.collect { |x| x[\bass] }, [0, 5, 7, 0], "their lowest notes");
		this.assertFloatEquals(fit.chords.sum { |x| x[\cost] }, fit.cost, "the chords' costs add up to the reading's");
		read = RLFit(fit.chords, score.regions);
		this.assertEquals(read.segments.first[\numerals], ["I", "IV", "V7", "I"], "and RLFit reads them in C major");
		this.assertEquals(read.agreementWith([(onset: 0, dur: 8, key: [0, \major]), (onset: 8, dur: 8, key: [1, \major])]), [8, 16],
			"its key against keys written on another time line: half the time");
	}

	test_passing_notes_stay_under_the_chord {
		var up = [[72, 0], [74, 2], [76, 4], [77, -1], [79, 1], [77, -1], [76, 4], [74, 2]].collect { |t, i| [i * 0.5, 0.5, t[0], t[1]] };
		var down = [[71, 5], [72, 0], [74, 2], [76, 4], [77, -1], [76, 4], [74, 2], [71, 5]].collect { |t, i| [4 + (i * 0.5), 0.5, t[0], t[1]] };
		var score = this.prScore(this.prBlock(0, 4, [[48, 0], [52, 4], [55, 1]]) ++ up ++ this.prBlock(4, 4, [[43, 1], [53, -1], [59, 5]]) ++ down, 2);
		this.assertEquals(this.prLabels(RLChordFit(score)), ["C M", "G Mm7"], "a scale in eighth notes over a held chord makes no chords of its own");
	}

	test_a_bass_struck_again_weighs_what_it_weighs_held {
		var top = this.prBlock(0, 4, [[64, 4], [67, 1]]) ++ this.prBlock(4, 4, [[65, -1], [69, 3]]);
		var held = [[0, 4, 48, 0], [4, 4, 48, 0]], struck = Array.fill(16) { |i| [i * 0.5, 0.5, 48, 0] };
		var a = RLChordFit(this.prScore(top ++ held, 2)), b = RLChordFit(this.prScore(top ++ struck, 2));
		this.assertEquals(this.prLabels(a), ["C M", "F M"], "C in the bass under C major, then under F major");
		this.assertEquals(this.prLabels(b), this.prLabels(a), "the same chords when the bass is struck every eighth note");
	}

	test_spelling_gives_the_root {
		var a = RLChordFit(this.prScore(this.prBlock(0, 4, [[59, 5], [62, 2], [65, -1], [68, -4]]), 1));
		var b = RLChordFit(this.prScore(this.prBlock(0, 4, [[56, 8], [59, 5], [62, 2], [65, -1]]), 1));
		this.assertEquals(this.prLabels(a), ["B o7"], "B D F A flat: a diminished seventh on B");
		this.assertEquals(this.prLabels(b), ["G# o7"], "G sharp B D F, the same four keys of a piano: on G sharp");
		this.assertEquals(a.chords.first[\pcs].asSet, b.chords.first[\pcs].asSet, "the same pitch classes of twelve");
	}

	test_candidates {
		var fit = RLChordFit(this.prScore(this.prBlock(0, 4, [[43, 1], [60, 0], [64, 4]]), 1)), c = fit.candidates(0, 4, 3);
		this.assertEquals(c.first[\label], "C M", "G under C and E: the triad on C over its fifth, with the hand-set kinds");
		this.assertEquals(c.size, 3, "as many as asked");
		this.assert((c[0][\cost] <= c[1][\cost]) and: { c[1][\cost] <= c[2][\cost] }, "the cheapest first");
		this.assertEquals(fit.candidates(8, 12), [], "nothing where nothing sounds");
	}

	test_against_written_chords {
		var theirs = [(onset: 0, dur: 4, root: 0, pcs: [0, 4, 7], label: "I"), (onset: 4, dur: 4, root: 5, pcs: [5, 9, 0], label: "IV"),
			(onset: 8, dur: 4, root: 2, pcs: [2, 5, 9], label: "ii"), (onset: 12, dur: 4, root: 0, pcs: [0, 4, 7, 11], label: "I7")];
		var fit = RLChordFit(this.prCadence), a = fit.agreement(theirs), b = fit.breakdown(theirs);
		this.assertEquals(a[\root], [12, 16], "three roots of four");
		this.assertEquals(a[\chord], [8, 16], "two chords of four: the last has another tone");
		this.assertEquals(fit.differences(theirs).collect { |d| [d[\from], d[\to], d[\ours], d[\theirs], d[\relation]] }, [[8, 12, "G Mm7", "ii", \shares]],
			"where the roots differ, and how the two chords stand: D and F in common");
		this.assertEquals([b[\shares], b[\none], b[\total]], [4, 0, 16], "the same by time");
	}

	test_places_in_the_bar {
		this.assertEquals(["4/4", "2/2", "3/4", "6/8", "12/8"].collect { |ts| RLChordFit.beat(ts) }, [1, 2, 1, 1.5, 1.5], "the beat, in quarter notes");
		this.assertEquals([0, 2, 1, 0.5, 0.25].collect { |pos| RLChordFit.place(pos, 2) }, [0, 1, 2, 3, 3], "in 2/2: the downbeat, a beat, a half beat, elsewhere");
	}

	test_spelling_and_roles {
		this.assertEquals([0, 1, -1, -4, 6, 13, -8].collect { |t| RLChordFit.spell(t) }, ["C", "G", "F", "Ab", "F#", "F##", "Fb"], "places on the line of fifths by name");
		this.assertEquals([0, 4, -3, 1, -6, -2, 5, -1, 3].collect { |o| RLChordFit.role(o) }, [0, 1, 1, 2, 2, 3, 3, nil, nil], "a tone's part in its chord, by its fifths from the root");
	}

	test_no_model_at_a_path {
		this.assertException({ RLChordFit.readModel("/nowhere/notes.json") }, Error, "no model there");
	}
}
