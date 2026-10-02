// REScore.merge: how an overdub take joins a score under each mode, loop
// folding, ids and causes; the control keys and curves behind it.
TestREScoreMerge : UnitTest {
	var savedRateLimit;

	setUp {
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown {
		RCLog.rateLimit = savedRateLimit;
	}

	midi { |beat, name, value| ^(beat: beat, kind: \midi, voice: name, name: name, raw: (value * 127).asInteger, value: value) }
	set { |beat, voice, key, value| ^(beat: beat, kind: \action, voice: voice, rc: IdentityDictionary[\rc -> "beat", \layer -> "core", \name -> voice.asString.split($/)[1]], method: \set, args: [key, value, nil, nil]) }
	note { |beat, voice, note, on = true| ^(beat: beat, kind: \keyboard, voice: voice, device: voice, msg: if(on) { \noteOn } { \noteOff }, chan: 1, note: note, value: 100) }

	// A: a knob curve over 8 beats, a set, notes.
	scoreA {
		var a = REScore(\m);
		[0, 2, 4, 6].do { |b| a.add(this.midi(b, \knob, b / 8)) };
		a.add(this.set(1, 'core/k', \amp, 0.1));
		a.add(this.set(5, 'core/k', \amp, 0.5));
		a.add(this.note(3, \kb, 60));
		a.add(this.note(3.5, \kb, 60, false));
		a.meta[\duration] = 8;
		^a
	}

	beatsOf { |score, voice| ^score.eventsOf(voice).collect(_[\beat]) }

	// A time-based pattern (Pseg) sampled once per beat on the test clock.
	sampleOverBeats { |pat, n|
		var clock = RCTestSupport.clock;
		var vals = List.new;
		var done = false;
		Routine { var r = pat.asStream; n.do { vals.add(r.next(())); 1.wait }; done = true }.play(clock);
		((n + 2) / clock.tempo).wait;
		this.assert(done, "sampled");
		^vals.asArray
	}

	test_control_keys_and_values {
		this.assertEquals(REScore.controlKey(this.midi(0, \knob, 0.5)), [\midi, \knob]);
		this.assertEquals(REScore.controlKey(this.set(0, 'core/k', \amp, 0.1)), [\set, "k", \amp]);
		this.assertEquals(REScore.controlKey(this.note(0, \kb, 60)), nil, "a note controls nothing");
		this.assertEquals(REScore.controlKey((kind: \keyboard, msg: \cc, device: \kb, num: 1, value: 3)), [\cc, \kb, 1]);
		this.assertEquals(REScore.controlValue(this.midi(0, \knob, 0.5)), 0.5);
		this.assertEquals(REScore.controlValue(this.set(0, 'core/k', \amp, 0.1)), 0.1);
		this.assert(REScore.isContinuous(this.midi(0, \knob, 0.5)));
		this.assert(REScore.isContinuous(this.set(0, 'core/k', \amp, Pwhite(0, 1))).not, "a pattern value is not continuous");
		this.assert(REScore.isContinuous(this.note(0, \kb, 60)).not);
	}

	test_curves_and_notes {
		var a = this.scoreA;
		var keys = a.controlKeys;
		var pat, vals, notes;
		this.assertEquals(keys.size, 2, "two controls");
		this.assertEquals(a.curvePoints([\midi, \knob]), [[0, 0], [2, 0.25], [4, 0.5], [6, 0.75]]);
		pat = a.curvePattern([\midi, \knob]);
		this.assert(pat.isKindOf(Pseq) and: { pat.list[0].isKindOf(Pseg) } and: { pat.list[1].isKindOf(Pn) }, "a Pseg then a hold");
		this.assertEquals(pat.list[0].list.list, [0, 0.25, 0.5, 0.75], "the levels (Pstep keeps them in a Pseq)");
		this.assertEquals(pat.list[0].durs.list, [2, 2, 2], "the beats between points");
		vals = this.sampleOverBeats(pat, 9);
		this.assertEquals(vals.collect(_.round(0.01)), [0, 0.13, 0.25, 0.38, 0.5, 0.63, 0.75, 0.75, 0.75], "a Pseg is a function of time: sampled every beat");
		a.add(this.midi(2, \late, 0.2));
		pat = a.curvePattern([\midi, \late]);
		this.assertEquals(pat.list[0].list.list, [0.2, 0.2], "a first point after beat 0: its value held until then");
		this.assertEquals(pat.list[0].durs.list, [2]);
		this.assertEquals(a.curvePattern([\midi, \nope]), nil, "no points: nil");
		notes = a.notePattern(\kb);
		this.assert(notes.isKindOf(Pbind), "notes as a Pbind");
		this.assertEquals(notes.asStream.next(()).midinote, 60);
		this.assertEquals(notes.asStream.next(()).sustain, 0.5, "sustain to the note off");
		this.assertEquals(a.notePattern(\nope), nil);
	}

	test_merge_replace_keep_touch {
		var a = this.scoreA;
		var b = REScore(\m);
		var m;
		b.add(this.midi(2.5, \knob, 0.9));
		b.add(this.midi(3.5, \knob, 0.8));
		b.add(this.note(5, \kb, 64));
		b.meta[\duration] = 6;
		m = REScore.merge(a, b, [\knob], \replace, [2, 6]);
		this.assertEquals(this.beatsOf(m, \knob), [0, 2.5, 3.5, 6], "replace: the voice's old events in the punch are gone, the new ones in");
		this.assertEquals(this.beatsOf(m, \kb), [3, 3.5, 5], "another voice: its new events added, the old ones kept");
		this.assertEquals(this.beatsOf(m, 'core/k'), [1, 5], "untouched voices kept");
		this.assertEquals(m.size, a.size - 2 + 3);
		this.assertEquals(a.size, 8, "the original is untouched");
		this.assertEquals(m.meta[\overdubs].size, 1, "the overdub noted");
		this.assertEquals(m.meta[\overdubs][0][\mode], "replace");
		this.assertEquals(m.meta[\overdubs][0][\punch], [2, 6]);
		m = REScore.merge(a, b, nil, \keep, nil);
		this.assertEquals(this.beatsOf(m, \knob), [0, 2, 2.5, 3.5, 4, 6], "keep: everything stays");
		this.assertEquals(m.size, a.size + b.size);
		m = REScore.merge(a, b, nil, \touch, nil);
		this.assertEquals(this.beatsOf(m, \knob), [0, 2, 2.5, 3.5, 4, 6], "touch: only the old points between the first and last touch go (none here)");
		this.assertEquals(this.beatsOf(m, \kb), [3, 3.5, 5], "touch: a note is just added");
		b.add(this.midi(4.5, \knob, 0.7));
		m = REScore.merge(a, b, nil, \touch, nil);
		this.assertEquals(this.beatsOf(m, \knob), [0, 2, 2.5, 3.5, 4.5, 6], "touch: the old point at 4 is inside the touch [2.5, 4.5] and goes");
		b.remove(b.events.last[\id]);
		m = REScore.merge(a, b, nil, nil, [2, 6]);
		this.assertEquals(this.beatsOf(m, \knob), [0, 2, 2.5, 3.5, 4, 6], "auto: touch for the continuous control");
		this.assertEquals(this.beatsOf(m, \kb), [5], "auto: replace for the notes within the punch");
		this.assert(m.events.every { |e| e[\id].notNil } and: { m.events.collect(_[\id]).asSet.size == m.size }, "ids unique");
		this.assertEquals(m.events.collect(_[\beat]), m.events.collect(_[\beat]).sort, "sorted");
	}

	test_merge_causes_and_loop_folding {
		var a = REScore(\m);
		var b = REScore(\m);
		var codeId, m;
		a.meta[\duration] = 4;
		codeId = b.add((beat: 1, kind: \code, voice: \code, text: "x"));
		b.add((beat: 1, kind: \action, voice: 'core/k', cause: codeId, rc: IdentityDictionary[\rc -> "beat", \layer -> "core", \name -> "k"], method: \play, args: []));
		b.add((beat: 2, kind: \action, voice: 'core/k', cause: 99, rc: IdentityDictionary[\rc -> "beat", \layer -> "core", \name -> "k"], method: \pause, args: []));
		m = REScore.merge(a, b, nil, \keep, nil);
		this.assertEquals(m.at(2)[\cause], 1, "causes follow the renewed ids");
		this.assertEquals(m.at(3)[\cause], nil, "a cause that is not in the take is dropped");
		b = REScore(\m);
		[0.5, 1.5, 2.5, 3.5].do { |beat| b.add(this.midi(beat, \knob, beat)) };   // two passes of a 2-beat loop
		b.add(this.note(0.25, \kb, 60));
		b.add(this.note(2.75, \kb, 62));
		m = REScore.merge(a, b, nil, \keep, nil, [0, 2]);
		this.assertEquals(this.beatsOf(m, \knob), [0.5, 0.5, 1.5, 1.5], "keep: both passes folded into the loop");
		this.assertEquals(this.beatsOf(m, \kb), [0.25, 0.75], "notes folded too");
		m = REScore.merge(a, b, nil, \replace, nil, [0, 2]);
		this.assertEquals(m.eventsOf(\knob).collect(_[\value]), [2.5, 3.5], "replace: the last pass of the voice only");
		this.assertEquals(this.beatsOf(m, \kb), [0.75], "the last pass of the notes' voice");
		m = REScore.merge(a, b, nil, nil, nil, [0, 2]);
		this.assertEquals(m.eventsOf(\knob).collect(_[\value]), [2.5, 3.5], "auto: the last pass of the control");
		this.assertEquals(m.eventsOf(\kb).collect(_[\note]), [62], "auto: the last pass of the notes");
	}
}
