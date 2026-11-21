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

	test_the_least_cost_when_a_chord_outlasts_a_span {
		// C major for a quarter note, then E minor for eight and a half: more than a span holds, so two spans in a row
		var em = [[52, 4], [67, 1], [71, 5]];
		var rows = this.prBlock(0, 1, [[48, 0], [64, 4], [67, 1]]) ++ this.prBlock(1, 0.5, em) ++ this.prBlock(1.5, 8, em);
		var fit = RLChordFit(this.prScore(rows, 3));
		this.assertEquals(fit.chords.collect { |c| [c[\label], c[\onset], c[\dur]] }, [["C M", 0, 1], ["E m", 1, 8.5]],
			"E minor from where it starts: the reading that ends on it there is kept, though another is cheaper up to there");
		this.assertFloatEquals(fit.chords.sum { |c| c[\cost] }, fit.cost, "and the costs add up");
	}

	test_runs_of_differences_without_labels {
		var theirs = [(onset: 0, dur: 4, root: 9, pcs: [9, 0, 4]), (onset: 4, dur: 4, root: 2, pcs: [2, 6, 9])];
		var fit = RLChordFit(this.prScore(this.prBlock(0, 8, [[48, 0], [64, 4], [67, 1]]), 2)), b = fit.breakdown(theirs);
		this.assertEquals(fit.differences(theirs).collect { |d| [d[\from], d[\to], d[\relation]] }, [[0, 4, \shares], [4, 8, \other]],
			"two chords of theirs are two runs, with labels or without");
		this.assertEquals([b[\shares], b[\other]], [4, 4], "each with its own time");
	}

	test_a_note_a_hair_too_long {
		var rows = [[0, 4.0005, 36, 0]] ++ this.prBlock(0, 4, [[64, 4], [67, 1]]) ++ this.prBlock(4, 4, [[53, -1], [57, 3]]);
		var fit = RLChordFit(this.prScore(rows, 2));
		this.assertEquals(this.prLabels(fit), ["C M", "F M"], "C major, then F and A alone: F major");
		this.assertEquals(fit.chords[1][\bass], 5, "whose lowest note is F, not the C that ended a hair into it");
		this.assertEquals(fit.candidates(4, 8, 1).first[\label], "F M", "the span read alone says the same");
	}

	test_a_chord_ends_with_its_last_note {
		var c = [[48, 0], [64, 4], [67, 1]], fit = RLChordFit(this.prScore(this.prBlock(0, 1, c) ++ this.prBlock(20, 1, c), 6));
		this.assertEquals(fit.chords.collect { |x| [x[\label], x[\onset], x[\dur]] }, [["C M", 0, 1], ["C M", 20, 1]],
			"nineteen quarter notes of silence are no chord");
		this.assertEquals(fit.chords.collect { |x| x[\span] }, [20, 1], "though the first holds until the next, as an analyst would write it");
		this.assertEquals(fit.agreement([(onset: 0, dur: 20, root: 0, pcs: [0, 4, 7])])[\root], [20, 20], "and is scored so");
	}

	test_a_kind_with_a_held_tone_says_so {
		var model = RLChordFit.defaultModel.copy, fit;
		model[\types] = model[\types] ++ [IdentityDictionary[\name -> "M", \offsets -> #[-1, 0, 3], \p -> 0.03]];
		fit = RLChordFit(this.prScore(this.prBlock(0, 4, [[43, 1], [60, 0], [64, 4]]), 1), model);
		this.assertEquals(fit.candidates(0, 4, 2).collect { |c| c[\label] }, ["C M", "G M(64)"], "the six-four over G is not the triad on G");
	}

	test_an_upbeat_and_a_bar_cut_in_two {
		var note = { |on| (onset: on.asFloat, dur: 1.0, pitch: 0, pc: 0, tpc: 0, staff: 1, mn: 0) };
		var measures = [[0, 0.0, 1.0, "4/4"], [1, 1.0, 2.0, "4/4"], [1, 3.0, 2.0, "4/4"], [2, 5.0, 4.0, "4/4"]];
		var fit = RLChordFit(RLScore(RLEDO(12), [0, 1, 3, 5].collect(note), [], [], measures, IdentityDictionary[\tpc -> 0, \mode -> \major, \duration -> 9.0]));
		this.assertEquals(fit.prBarStarts, [-3, 1, 1, 5], "the upbeat counted from where its bar would start, the second half of a cut bar from its first");
		this.assertEquals(RLChordFit.barLength("6/8"), 3, "a bar of 6/8 in quarter notes");
	}

	test_the_other_chords_a_span_could_be {
		// G B D F with B the lowest: the dominant seventh, and, dearer by the G left unexplained, the diminished
		// triad on B (a whole bar of G outside the chord: far dearer, so the margin asked is wide)
		var rows = this.prBlock(0, 4, [[59, 5], [62, 2], [65, -1], [67, 1]]);
		var fit = RLChordFit(this.prScore(rows, 1)), with = fit.withAlternatives(200, 8), alts = with.first[\alternatives];
		this.assertEquals(this.prLabels(fit), ["G Mm7"], "G B D F, B the lowest: the dominant seventh over its third");
		this.assertEquals(alts.collect { |a| a[\label] }.keep(2), ["G M", "B o"], "the nearest: the triad without its seventh, the diminished triad on B");
		this.assert(alts.every { |a| a[\extra] > 0 }, "each dearer in the notes, by its extra");
		this.assert((alts.collect { |a| a[\extra] }.differentiate.drop(1)).every { |d| d >= 0 }, "the nearest first");
		this.assertEquals([alts.first[\onset], alts.first[\dur], alts.first[\span]], [0, 4, 4], "on the same span");
		this.assertEquals(fit.withAlternatives(0.001).first[\alternatives], [], "none within a thousandth of a bit");
		this.assertEquals(fit.agreement([(onset: 0, dur: 4, root: 11, pcs: [11, 2, 5])], [alts.detect { |a| a[\label] == "B o" }])[\root], [4, 4],
			"another list of chords is scored like the reading's own");
	}

	test_how_notes_move {
		// C D E in a row, then G alone a bar later
		var fit = RLChordFit(this.prScore([[0, 1, 60, 0], [1, 1, 62, 2], [2, 1, 64, 4], [4, 1, 67, 1]], 2)), plain, old;
		this.assertEquals(fit.prMoves, [2, 3, 1, 0], "left by step, by step both ways, reached by step, by leap (or by nothing)");
		// a model that says nothing of motion prices a note by its length alone, as before
		old = RLChordFit.defaultModel.copy;
		old.removeAt(\motion);
		plain = RLChordFit(this.prCadence, old);
		this.assertEquals(this.prLabels(plain), ["C M", "F M", "G Mm7", "C M"], "a model without motion still reads");
	}

	test_a_passing_note_is_cheap {
		// under C major held a bar: D between C and E (it comes and goes by step), or D reached and left by leap
		var held = this.prBlock(0, 4, [[48, 0], [52, 4], [55, 1]]);
		var passing = RLChordFit(this.prScore(held ++ [[0, 1, 72, 0], [1, 1, 74, 2], [2, 2, 76, 4]], 1));
		var leaping = RLChordFit(this.prScore(held ++ [[0, 1, 79, 1], [1, 1, 74, 2], [2, 2, 79, 1]], 1));
		this.assertEquals([this.prLabels(passing), this.prLabels(leaping)], [["C M"], ["C M"]], "C major both times");
		this.assert(passing.cost < leaping.cost, "the D that passes by step costs less to leave outside the chord than the D that leaps");
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
