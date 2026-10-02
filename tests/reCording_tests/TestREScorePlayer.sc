// REScorePlayer: every kind of event fired back (and its fallback when the
// root cannot play), nothing it does recorded, timing on the song's clock,
// loops, muted and soloed voices, snapshots and morphs.
TestREScorePlayer : UnitTest {
	var clock, song, layer, savedRateLimit, savedMainThreadOnly;

	setUp {
		clock = RCTestSupport.clock;
		RCTestSupport.bootSession;
		song = RCSong(\pl, 1);
		layer = song.layer(\core);
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
		savedMainThreadOnly = RETap.mainThreadOnly;
		RETap.mainThreadOnly = false;
	}

	tearDown {
		RETap.mainThreadOnly = savedMainThreadOnly;
		RCTestSupport.reset;
		RCLog.rateLimit = savedRateLimit;
	}

	logHas { |text| ^RCLog.history.any { |e| e[2].contains(text) } }

	restBeat { |name, attrs| ^layer.addBeat(name, [type: \rest, dur_flex: 1] ++ (attrs ? [])) }

	beatRef { |name| ^IdentityDictionary[\rc -> "beat", \song -> "pl", \layer -> "core", \name -> name.asString] }

	action { |beat, name, method, args, extra|
		var ev = (beat: beat, secs: beat / 20, kind: \action, voice: ("core/" ++ name).asSymbol, rc: this.beatRef(name), method: method, args: REScore.encodeValue(args));
		extra !? { |d| d.keysValuesDo { |k, v| ev[k] = v } };
		^ev
	}

	test_fire_action_midi_osc_keyboard_code {
		var b = this.restBeat(\k, [amp: 0.1]);
		var s = REScore(\pl);
		var p = REScorePlayer(s, song);
		var got = nil, oscGot = nil;
		song.midi.control(\knob, 5, 0, "No Such Device", { |x| x / 127 }, { |v, raw| got = [v, raw] });
		song.osc.def(\scene, \init, { |msg| oscGot = msg }, print: false);
		song.makeKeyboard(nil);
		this.assert(p.fire(this.action(0, \k, \set, [\amp, 0.5, nil, \default])), "an action fires");
		this.assertEquals(b.keyProxy(\amp).source, 0.5, "the method ran with the arguments");
		this.assert(p.fire((kind: \midi, name: \knob, raw: 64, value: 0.25)), "a midi event fires");
		this.assertEquals(got, [0.25, 64], "through the mapping's action with the recorded mapped value");
		this.assert(p.fire((kind: \osc, key: \pl_scene_init, path: "/pl/scene/init", args: [1, 2])), "an osc event fires");
		this.assertEquals(oscGot, ['/pl/scene/init', 1, 2], "through the def's function with the recorded arguments");
		this.assert(p.fire((kind: \keyboard, device: \kb, msg: \noteOn, chan: 1, note: 60, value: 100)), "a keyboard event fires");
		this.assertEquals(song.keyboard.notes, [60], "on the song's keyboard state");
		this.assert(p.fire((kind: \keyboard, device: \kb, msg: \noteOff, chan: 1, note: 60, value: 0)));
		this.assertEquals(song.keyboard.notes, [], "note off");
		this.assert(p.fire((kind: \code, text: "~reTestPlayerCode = 7")), "a code line fires");
		this.assertEquals(currentEnvironment[\reTestPlayerCode], 7, "interpreted in the current environment");
		currentEnvironment[\reTestPlayerCode] = nil;
		this.assertEquals(p.fire((kind: \code, text: "1 +")), false, "a line that does not compile is skipped");
		this.assertEquals(p.fire((kind: \code, text: "0.exit", replay: false)), false, "replay: false is skipped");
		this.assertEquals(p.fire((kind: \action, rc: this.beatRef(\nope), method: \set, args: [\amp, 1])), false, "no such beat: skipped");
		this.assertEquals(p.fire((kind: \midi, name: \gone, raw: 1, value: 1)), false, "no such mapping: skipped");
		this.assertEquals(p.fire((kind: \osc, key: \pl_no_def, path: "/x", args: [])), false, "no such def: skipped");
		this.assert(this.logHas("skipped"), "skips are reported");
		this.assert(p.fire((kind: \tempo, tempo: 99)), "a tempo event fires");
		this.assertEquals(clock.tempo, 20, "but does not touch the clock unless followTempo");
	}

	test_interpolation {
		var got = List.new;
		var s = REScore(\pl);
		var p;
		song.midi.control(\knob, 5, 0, "No Such Device", { |x| x }, { |v| got.add(v) });
		s.add((beat: 0, kind: \midi, voice: \knob, name: \knob, raw: 0, value: 0.0));
		s.add((beat: 2, kind: \midi, voice: \knob, name: \knob, raw: 127, value: 1.0));
		s.add((beat: 2.5, kind: \midi, voice: \knob, name: \knob, raw: 127, value: 1.0));
		s.meta[\duration] = 3;
		s.controls[\knob] = IdentityDictionary[\min -> 0, \max -> 1, \warp -> "lin"];
		p = REScorePlayer(s, song);
		p.stepsPerBeat = 4;
		p.play;
		(4 / clock.tempo).wait;
		this.assertEquals(p.fired, 3, "the recorded points");
		this.assert(p.interpolated >= 6, "steps between the first two points: " ++ p.interpolated);
		this.assertEquals(got.first, 0.0);
		this.assert(got.asArray.every { |v, i| i == 0 or: { v >= got[i - 1] } }, "rising: " ++ got);
		this.assert(got.includes(0.5), "halfway at beat 1: " ++ got);
		this.assertEquals(got.last, 1.0);
		got.clear;
		p.interpolate = false;
		p.play;
		(4 / clock.tempo).wait;
		this.assertEquals(got.asArray, [0.0, 1.0, 1.0], "without interpolation: the points only");
	}

	test_commit_warning {
		var s = REScore(\pl);
		var p;
		s.meta[\commits] = IdentityDictionary[\vikn -> "0000000"];
		p = REScorePlayer(s, song);
		p.play;
		(0.5 / clock.tempo).wait;
		this.assert(this.logHas("recorded with other code"), "a mismatching commit is reported");
		p.stop;
	}

	test_fire_raw_midi {
		var got = nil;
		var def = MIDIdef.cc(\re_test_raw_cc, { |val, num, chan, src| got = [num, val, chan, src] });
		var p = REScorePlayer(REScore(\pl), song);
		var rec = song.scoreRecorder;
		rec.arm(inputs: #[\rawMidi]);
		rec.record(snapshotAtStart: false);
		this.assert(p.fire((kind: \rawMidi, msg: \control, device: "nope", src: 9, chan: 2, num: 7, value: 100)), "a raw MIDI event fires");
		this.assertEquals(got, [7, 100, 2, 9], "dispatched through MIDIIn: a MIDIdef of the piece got it, from the recorded uid when the device is absent");
		this.assertEquals(rec.stop.size, 0, "the recorder's own raw hooks stay silent meanwhile");
		rec.disarm;
		def.free;
	}

	test_firing_is_not_recorded {
		var b = this.restBeat(\k, [amp: 0.1]);
		var rec = song.scoreRecorder;
		var p = REScorePlayer(REScore(\pl), song);
		var s;
		rec.arm(inputs: #[\actions, \midi]);
		rec.record(snapshotAtStart: false);
		p.fire(this.action(0, \k, \set, [\amp, 0.5, nil, \default]));
		s = rec.stop;
		this.assertEquals(s.size, 0, "what the player fires is not recorded (RETap.silently)");
		this.assertEquals(b.keyProxy(\amp).source, 0.5, "but happened");
		rec.disarm;
	}

	test_play_timing_loop_mute {
		var b = this.restBeat(\k, [amp: 0.1]);
		var s = REScore(\pl);
		var p = REScorePlayer(s, song);
		var seen = List.new, loops = 0, done = false;
		s.add(this.action(0, \k, \set, [\amp, 0.2, nil, nil]));
		s.add(this.action(1, \k, \set, [\amp, 0.3, nil, nil]));
		s.add(this.action(2, \k, \set, [\legato, 0.5, nil, nil]));
		s.add((beat: 2.5, kind: \midi, voice: \other, name: \gone, raw: 1, value: 1));
		s.meta[\duration] = 4;
		p.onEvent = { |player, ev| seen.add([ev[\beat], player.position.round(0.01)]) };
		p.onDone = { done = true };
		p.play;
		this.assert(p.isPlaying, "playing");
		(4.5 / clock.tempo).wait;
		this.assert(done, "done after the last event");
		this.assert(p.isPlaying.not);
		this.assertEquals(seen.collect(_[0]), [0, 1, 2], "the events in order");
		this.assert(seen.every { |pair| (pair[1] - pair[0]).abs < 0.3 }, "fired close to their beats: " ++ seen);
		this.assertEquals(p.fired, 3);
		this.assertEquals(p.skipped, 1, "the gone mapping skipped");
		this.assertEquals(b.keyProxy(\legato).source, 0.5);
		seen.clear;
		p.mute(['core/k']);
		p.onLoop = { |player, pass| loops = pass };
		p.play(from: 0, to: 2, loop: true);
		(5 / clock.tempo).wait;
		this.assert(p.isPlaying, "a loop keeps playing");
		this.assert(loops >= 2, "passes counted: " ++ loops);
		this.assertEquals(seen.size, 0, "a muted voice fires nothing");
		p.unmute;
		p.solo([\other]);
		(2 / clock.tempo).wait;
		this.assertEquals(seen.size, 0, "solo on another voice: nothing of this one");
		p.solo(nil);
		(2.5 / clock.tempo).wait;
		this.assert(seen.size >= 1, "unsoloed: the voice fires again in the next pass");
		p.stop;
		this.assert(p.isPlaying.not, "stopped");
	}

	test_fallback_to_effects {
		var b = this.restBeat(\k, [amp: 0.1]);
		var s = REScore(\pl);
		var p = REScorePlayer(s, song);
		var codeId = s.add((beat: 0, kind: \code, voice: \code, text: "nil.explode"));
		var okId;
		s.add(this.action(0, \k, \set, [\amp, 0.7, nil, nil], (cause: codeId)));
		okId = s.add((beat: 1, kind: \code, voice: \code, text: "~reTestPlayerOk = 1"));
		s.add(this.action(1, \k, \set, [\amp, 0.9, nil, nil], (cause: okId)));
		p.play;
		(2.5 / clock.tempo).wait;
		this.assertEquals(b.keyProxy(\amp).source, 0.7, "the first line raised: its effect played; the second ran: its effect did not");
		this.assertEquals(currentEnvironment[\reTestPlayerOk], 1);
		currentEnvironment[\reTestPlayerOk] = nil;
		this.assertEquals(p.fired, 2);
		this.assertEquals(p.skipped, 1);
	}

	// A line that fails at replay after doing part of its work: its effects play from where it
	// stopped, never again what it did (the layer gets each beat once); fallback: false plays
	// none, fallback: true all.
	test_fallback_for_what_the_line_did_not_reach {
		var s = REScore(\pl);
		var p = REScorePlayer(s, song);
		var layerRef = IdentityDictionary[\rc -> "layer", \song -> "pl", \name -> "core"];
		var attrs = [type: \rest, dur_flex: 1];
		var id1, id2, id3;
		currentEnvironment[\reTestLayer] = layer;
		id1 = s.add((beat: 0, kind: \code, voice: \code, text: "~reTestLayer.addBeat(\\x1, [type: \\rest, dur_flex: 1]); nil.explode", raised: true));
		s.add((beat: 0, kind: \action, voice: \core, rc: layerRef, method: \addBeat, args: REScore.encodeValue([\x1, attrs]), cause: id1));
		s.add((beat: 0, kind: \action, voice: \core, rc: layerRef, method: \addBeat, args: REScore.encodeValue([\x2, attrs]), cause: id1));
		id2 = s.add((beat: 1, kind: \code, voice: \code, text: "nil.explode", fallback: false));
		s.add((beat: 1, kind: \action, voice: \core, rc: layerRef, method: \addBeat, args: REScore.encodeValue([\y1, attrs]), cause: id2));
		id3 = s.add((beat: 2, kind: \code, voice: \code, text: "~reTestLayer.addBeat(\\z1, [type: \\rest, dur_flex: 1]); nil.explode", fallback: true));
		s.add((beat: 2, kind: \action, voice: \core, rc: layerRef, method: \addBeat, args: REScore.encodeValue([\z1, attrs]), cause: id3));
		s.add((beat: 2, kind: \action, voice: \core, rc: layerRef, method: \addBeat, args: REScore.encodeValue([\z2, attrs]), cause: id3));
		p.play;
		(3.5 / clock.tempo).wait;
		this.assert(layer.beat(\x1).notNil and: { layer.beat(\x2).notNil }, "the line's own addBeat, then the effect it did not reach");
		this.assertEquals(layer.beats.size, 4, "x1 once (not twice), x2, z1, z2: " ++ layer.beats.collect(_.name));
		this.assert(layer.beat(\y1).isNil, "fallback: false: no effect of that line");
		this.assert(this.logHas("failed after 1 action"), "said once");
		this.assert(this.logHas("fallback: false"), "and the refusal");
		this.assertEquals(p.skipped, 3, "the three lines failed");
		this.assertEquals(p.fired, 3, "x2 (the rest), then z1 and z2 (all, asked for)");
		currentEnvironment[\reTestLayer] = nil;
	}

	// A replay starts on the take's own phase of the grid: what the take quantized lands as it did.
	test_phase_alignment {
		var s = REScore(\pl);
		var p = REScorePlayer(s, song);
		s.add((beat: 0, kind: \midi, voice: \nope, name: \nope, raw: 1, value: 1));
		s.meta[\beat0] = 10.25;
		s.meta[\quant] = 4;
		s.meta[\duration] = 0.5;
		p.play;                                        // the take's grid: 4, its phase 0.25
		(4.5 / clock.tempo).wait;
		this.assert(p.startBeat.notNil and: { ((p.startBeat - 10.25) mod: 4).abs < 1e-6 }, "on the take's phase of its grid: " ++ p.startBeat);
		p.play(2);                                     // a grid of 2: the phase kept
		(2.5 / clock.tempo).wait;
		this.assert(((p.startBeat - 10.25) mod: 2).abs < 1e-6, "on the take's phase of a grid of 2: " ++ p.startBeat);
		p.alignPhase = false;
		p.play(2);
		(2.5 / clock.tempo).wait;
		this.assert((p.startBeat mod: 2).abs < 1e-6, "without alignment: the plain grid: " ++ p.startBeat);
		this.assertEquals(REScorePlayer.alignedStart(REScore(\pl), clock, nil).frac, 0.0, "no beat0, no grid: the next beat");
	}

	test_snapshot_and_morph {
		var b = this.restBeat(\k, [amp: 0.1, legato: 0.8]);
		var tpl = RCOrgnsm(\sp, 0, 0, song);
		var rec = song.scoreRecorder;
		var p, s, snapId, morphId, src;
		tpl.addStaticAttrs((seed: 1, loop_time: 4));
		tpl.register;
		rec.arm(inputs: #[\actions]);
		rec.record(snapshotAtStart: false);
		snapId = rec.snapshot(\intro);
		b.set(\amp, 0.6);
		tpl.rPut("loop_time", 8);
		this.assertEquals(rec.recall(\intro).class, Integer, "a recall is recorded as a morph event");
		this.assertEquals(b.keyProxy(\amp).source, 0.1, "and restores the sources");
		this.assertEquals(tpl.staticAttrs[\loop_time], 4, "and the orgnsms' attributes");
		b.set(\amp, 0.6);
		morphId = rec.morph(\intro, beats: 2);
		src = b.keyProxy(\amp).source;
		this.assert(src.isKindOf(Pseq), "a morph ramps numeric sources: " ++ src.asCompileString);
		this.assertEquals(src.asStream.nextN(3), [0.6, 0.6, 0.6], "from the current value");
		s = rec.stop;
		this.assertEquals(s.at(morphId)[\kind], \morph);
		this.assertEquals(s.at(morphId)[\beats], 2);
		this.assertEquals(s.at(morphId)[\snapshot], snapId, "pointing at the snapshot of the same take");
		this.assertEquals(rec.morph(\intro), nil, "not recording: applied, not recorded");
		this.assertEquals(b.keyProxy(\amp).source, 0.1, "applied from the last score");
		b.set(\amp, 0.6);
		p = REScorePlayer(s, song);
		this.assert(p.fire(s.at(morphId)), "a morph event replays");
		this.assert(b.keyProxy(\amp).source.isKindOf(Pseq));
		this.assert(p.fire(s.at(snapId)), "a snapshot event replays");
		this.assertEquals(b.keyProxy(\amp).source, 0.1);
		this.assertEquals(rec.morph(\nope), nil, "unknown snapshot");
		this.assert(this.logHas("no snapshot named"));
		rec.disarm;
	}

	test_apply_state_transport {
		var b = this.restBeat(\k, [amp: 0.1]);
		var state = IdentityDictionary[\beats -> IdentityDictionary['core/k' -> IdentityDictionary[\amp -> 0.3, \playing -> false]]];
		REScorePlayer.applyState(song, state);
		this.assert(b.isPlaying.not, "playing false pauses the beat");
		this.assertEquals(b.keyProxy(\amp).source, 0.3);
		state[\beats]['core/k'][\playing] = true;
		REScorePlayer.applyState(song, state);
		this.assert(b.isPlaying, "playing true resumes it");
		REScorePlayer.applyState(song, IdentityDictionary[\beats -> IdentityDictionary['core/nope' -> IdentityDictionary[\amp -> 1]]]);
		this.assert(this.logHas("no beat core/nope"), "a missing beat is reported");
	}

	test_ramp {
		var pat = REScorePlayer.ramp(0, 1, 4);
		var vals = List.new, done = false;
		this.assert(pat.isKindOf(Pseq) and: { pat.list[0].isKindOf(Pseg) } and: { pat.list[1].isKindOf(Pn) }, "a Pseg then a hold");
		this.assertEquals(pat.list[0].list.list, [0, 1], "the levels (Pstep keeps them in a Pseq)");
		this.assertEquals(pat.list[0].durs.list, [4], "the beats");
		Routine { var r = pat.asStream; 7.do { vals.add(r.next(())); 1.wait }; done = true }.play(clock);
		(9 / clock.tempo).wait;
		this.assert(done, "sampled on the clock");
		this.assertEquals(vals.asArray.collect(_.round(0.01)), [0, 0.25, 0.5, 0.75, 1, 1, 1], "reaches the target in 4 beats and holds it");
	}
}
