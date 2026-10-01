// The edit operations of REScore: every one answers a new score and leaves the
// receiver as it was; beats and seconds move together through the tempo map.
TestREScoreEdit : UnitTest {
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

	// tempo 2 beats/s for 4 beats, then 4: 4 beats = 2 s, 8 beats = 3 s
	score {
		var s = REScore(\e);
		s.meta[\tempo] = 2;
		s.tempoMap.add([0, 2]);
		s.tempoMap.add([4, 4]);
		s.add((beat: 0, secs: 0, kind: \snapshot, name: \start, state: IdentityDictionary.new));
		[0, 1, 2, 3, 4, 6, 8].do { |b| s.add(this.midi(b, \knob, b / 8)) };
		s.add(this.set(1.3, 'core/k', \amp, 0.1));
		s.add((beat: 2, secs: 1, kind: \snapshot, name: \mid, state: IdentityDictionary.new));
		s.add((beat: 5, secs: 2.25, kind: \code, voice: \code, text: "x"));
		s.events.do { |e| e[\secs] = s.beatsToSecs(e[\beat]) };
		s.meta[\duration] = 8;
		^s
	}

	beats { |s, voice| ^s.eventsOf(voice).collect(_[\beat]) }

	test_beatsToSecs {
		var s = this.score;
		this.assertEquals(s.beatsToSecs(0), 0);
		this.assertEquals(s.beatsToSecs(2), 1.0, "2 beats at 2 beats/s");
		this.assertEquals(s.beatsToSecs(4), 2.0);
		this.assertEquals(s.beatsToSecs(8), 3.0, "4 more beats at 4 beats/s");
		this.assertEquals(s.beatsToSecs(-2), -1.0, "before the map: the first tempo");
		s.tempoMap.clear;
		this.assertEquals(s.beatsToSecs(6), 3.0, "no map: the meta tempo");
	}

	test_shifted_and_scaled {
		var s = this.score;
		var m = s.shifted(1);
		this.assertEquals(this.beats(m, \knob), [1, 2, 3, 4, 5, 7, 9], "every event one beat later");
		this.assertEquals(m.at(2)[\secs], 0.5, "seconds follow the tempo map (the point of beat 0, now at 1)");
		this.assertEquals(m.meta[\duration], 9, "the duration too");
		this.assertEquals(this.beats(s, \knob), [0, 1, 2, 3, 4, 6, 8], "the receiver untouched");
		m = s.shifted(-1.5);
		this.assertEquals(this.beats(m, \knob), [0.5, 1.5, 2.5, 4.5, 6.5], "what lands before 0 is dropped");
		this.assertEquals(m.at(1)[\beat], 0, "a snapshot there is kept at 0");
		this.assertEquals(m.at(1)[\kind], \snapshot);
		this.assert(RCLog.history.any { |e| e[2].contains("dropped") }, "and the drop is reported");
		m = s.shifted(2, voices: [\knob]);
		this.assertEquals(m.at(9)[\beat], 1.3, "another voice stays");
		this.assertEquals(this.beats(m, \knob).first, 2);
		m = s.scaled(0.5);
		this.assertEquals(this.beats(m, \knob), [0, 0.5, 1, 1.5, 2, 3, 4], "halved");
		this.assertEquals(m.meta[\duration], 4);
		m = s.scaled(2, origin: 4);
		this.assertEquals(this.beats(m, \knob), [-4, -2, 0, 2, 4, 8, 12], "stretched around beat 4 (no dropping here)");
	}

	test_quantized {
		var s = this.score;
		var m = s.quantized(1);
		this.assertEquals(m.at(9)[\beat], 1, "1.3 to the beat");
		this.assertEquals(m.at(9)[\secs], 0.5);
		m = s.quantized(1, strength: 0.5);
		this.assertFloatEquals(m.at(9)[\beat], 1.15, "half the way");
		m = s.quantized(1, kinds: [\midi]);
		this.assertEquals(m.at(9)[\beat], 1.3, "other kinds stay");
	}

	test_trimmed {
		var s = this.score;
		var m = s.trimmed(3, 7);
		this.assertEquals(this.beats(m, \knob), [0, 1, 3], "beats 3, 4, 6 rebased to 0");
		this.assertEquals(m.events.collect(_[\kind]).first, \snapshot, "the latest snapshot before the start kept");
		this.assertEquals(m.events.first[\name], \mid, "the one at beat 2, not the start one");
		this.assertEquals(m.events.first[\beat], 0, "at 0");
		this.assertEquals(m.meta[\duration], 4);
		this.assertEquals(m.tempoMap.asArray, [[0, 2], [1, 4]], "the tempo map rebased");
		this.assertEquals(m.events.last[\secs], 0.5 + 0.5, "seconds through the rebased map: 1 beat at 2, 2 beats at 4");
		this.assertEquals(m.ofKind(\code).size, 1, "the code line at 5 is inside");
		this.assertEquals(s.size, 11, "the receiver untouched");
	}

	test_thinned_and_smoothed {
		var s = REScore(\t);
		var m;
		[[0, 0], [1, 0.1], [2, 0.2], [3, 0.5], [4, 0.6], [5, 0.7]].do { |p| s.add(this.midi(p[0], \knob, p[1])) };
		s.add(this.midi(0, \other, 0.5));
		m = s.thinned(0.01);
		this.assertEquals(this.beats(m, \knob), [0, 2, 3, 5], "the points on a line go, the ends and the corners stay");
		this.assertEquals(m.eventsOf(\other).size, 1, "a lone point stays");
		this.assertEquals(s.eventsOf(\knob).size, 6, "the receiver untouched");
		m = s.smoothed(3);
		this.assertEquals(m.eventsOf(\knob).collect { |e| e[\value].round(0.001) }, [0.05, 0.1, 0.267, 0.433, 0.6, 0.65], "a 3-point average, shorter at the ends");
		this.assertEquals(m.eventsOf(\knob)[1][\raw], nil, "a changed value drops the raw CC");
	}

	test_voices_and_kinds {
		var s = this.score;
		var m = s.withoutVoices([\knob]);
		this.assertEquals(m.eventsOf(\knob).size, 0);
		this.assertEquals(m.size, 4);
		m = s.onlyVoices(['core/k']);
		this.assertEquals(m.voiceNames, ['core/k'], "snapshots have no voice and stay");
		this.assertEquals(m.size, 3);
		m = s.withoutKinds([\snapshot, \code]);
		this.assertEquals(m.kinds, [\action, \midi]);
		m = s.renamedVoice(\knob, \lead);
		this.assertEquals(this.beats(m, \lead).size, 7);
		this.assertEquals(m.eventsOf(\knob).size, 0);
	}

	test_replacedSegment_and_valueAt {
		var s = this.score;
		var key = [\midi, \knob];
		var m = s.replacedSegment(key, 2, 6, [[2, 0.9], [3.5, 0.8]]);
		this.assertEquals(m.curvePoints(key), [[0, 0], [1, 0.125], [2, 0.9], [3.5, 0.8], [6, 0.75], [8, 1]], "the span's points replaced");
		this.assertEquals(m.eventsOf(\knob).detect { |e| e[\beat] == 3.5 }[\kind], \midi, "shaped like the control's events");
		this.assertEquals(m.eventsOf(\knob).detect { |e| e[\beat] == 3.5 }[\secs], 1.75, "with their seconds");
		this.assertEquals(s.valueAt(key, 2.5), 0.25, "the last point at or before");
		this.assertEquals(s.valueAt(key, -1), 0, "before the first: the first");
		this.assertEquals(s.valueAt([\midi, \nope], 1), nil);
		m = s.replacedSegment([\midi, \nope], 0, 1, [[0, 1]]);
		this.assertEquals(m.size, s.size, "an unknown control: nothing replaced, reported");
		this.assert(RCLog.history.any { |e| e[2].contains("nothing replaced") });
		m = s.replacedSegment([\set, "k", \amp], 0, 8, [[4, 0.4]]);
		this.assertEquals(m.eventsOf('core/k').collect(_[\beat]), [4], "an action control replaced");
		this.assertEquals(m.eventsOf('core/k')[0][\args], [\amp, 0.4, nil, nil], "the value in the arguments");
	}
}
