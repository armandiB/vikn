// REScoreRecorder: off means off (the identity test), scope and input kinds,
// actions through the taps (one event per action, nested calls not recorded),
// inputs with their causes, code lines through the interpreter hooks,
// snapshots and voices, the quantized start and the file at stop.
//
// The suite runs in a Routine, where the tap ignores calls: tests set
// RETap.mainThreadOnly to false and one test checks the rule itself.
TestREScoreRecorder : UnitTest {
	var clock, song, layer, rec, savedRateLimit, savedMainThreadOnly, dir;

	setUp {
		clock = RCTestSupport.clock;
		RCTestSupport.bootSession;
		song = RCSong(\rec, 1);
		layer = song.layer(\core);
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
		savedMainThreadOnly = RETap.mainThreadOnly;
		RETap.mainThreadOnly = false;
		dir = PathName.tmp +/+ "re_rec_" ++ UniqueID.next;
		rec = song.scoreRecorder;
	}

	tearDown {
		rec.disarm;
		RETap.mainThreadOnly = savedMainThreadOnly;
		RCTestSupport.reset;
		RCLog.rateLimit = savedRateLimit;
	}

	logHas { |text| ^RCLog.history.any { |e| e[2].contains(text) } }

	// The log lines that are not the recorder's own.
	prLogLines { ^RCLog.history.collect { |e| e[2] }.reject { |t| t.contains("RC[score]") or: { t.contains("RC[tap]") } } }

	restBeat { |l, name, attrs| ^l.addBeat(name, [type: \rest, dur_flex: 1] ++ (attrs ? [])) }

	test_off_means_off {
		var b;
		this.assertEquals(RETap.active, false, "nothing armed: the tap is inactive");
		this.assertEquals(rec.state, \off);
		this.assert(RETap.codeHooksInstalled.not, "no interpreter hook installed");
		b = this.restBeat(layer, \k, [amp: 0.1]);
		b.set(\amp, 0.2);
		this.assertEquals(rec.score, nil, "nothing kept");
		this.assertEquals(song.scoreRecorder, rec, "one recorder per song");
	}

	test_arm_record_actions_stop {
		var b, s;
		rec.arm;
		this.assert(RETap.active, "armed: the tap is active");
		this.assertEquals(rec.state, \armed);
		this.assert(RETap.codeHooksInstalled, "code hooks installed with \\code among the inputs");
		b = this.restBeat(layer, \k, [amp: 0.1]);
		this.assertEquals(rec.score, nil, "armed, not recording: nothing kept");
		rec.record;
		this.assertEquals(rec.state, \recording);
		this.assertEquals(rec.score.size, 1, "the start snapshot");
		this.assertEquals(rec.score.at(1)[\kind], \snapshot);
		this.assertEquals(rec.score.at(1)[\state][\beats]['core/k'][\amp], 0.1, "with the beats' key sources");
		b.set(\amp, 0.5);
		b.setAll([amp: 0.3, legato: 0.5]);
		b.pause;
		b.resume;
		this.restBeat(layer, \k2);
		song.pauseAllBeats;
		song.resumeAllBeats;
		layer.deleteBeat(\k2);
		s = rec.stop;
		this.assertEquals(rec.state, \armed, "stop leaves the recorder armed");
		this.assertEquals(rec.lastScore, s, "and keeps the score");
		this.assertEquals(s.ofKind(\action).collect { |e| e[\method] },
			[\set, \setAll, \pause, \resume, \addBeat, \pauseAllBeats, \resumeAllBeats, \deleteBeat],
			"one event per action; the calls inside (prSet, prPlay, prFree...) are not recorded");
		this.assertEquals(s.at(2)[\rc], IdentityDictionary[\rc -> "beat", \song -> "rec", \layer -> "core", \name -> "k"], "the receiver");
		this.assertEquals(s.at(2)[\args], [\amp, 0.5, nil, \default], "the arguments as called");
		this.assertEquals(s.at(2)[\voice], 'core/k', "voice: layer/name");
		this.assertEquals(s.at(3)[\args], [[\amp, 0.3, \legato, 0.5], nil, \default]);
		this.assertEquals(s.eventsOf(\rec).size, 2, "song actions under the song's name");
		this.assertEquals(s.at(6)[\method], \addBeat);
		this.assertEquals(s.at(6)[\voice], \core, "layer actions under the layer's name");
		this.assertEquals(s.at(6)[\args][0], \k2);
		this.assert(s.at(2)[\beat] >= 0 and: { s.at(2)[\secs] >= 0 }, "timed from the start");
		this.assertEquals(s.meta[\song], "rec");
		this.assertEquals(s.meta[\tempo], clock.tempo);
		this.assert(s.meta[\duration] >= 0, "the duration");
		this.assert(s.meta[\commits][\vikn].notNil, "the vikn commit");
		this.assert(this.logHas("not written"), "no root: not written, warned");
		rec.disarm;
		this.assertEquals(rec.state, \off);
		this.assertEquals(RETap.active, false, "disarmed: inactive again");
		this.assert(RETap.codeHooksInstalled.not, "hooks removed");
	}

	test_voice_conveniences_and_controls {
		var b1 = this.restBeat(layer, \a);
		var b2 = this.restBeat(layer, \b);
		var s;
		song.midi.control(\k1, 1, 0, "Dev A", { |x| x }, { });
		song.midi.control(\k2, 2, 0, "Dev B", { |x| x }, { });
		this.assertEquals(song.midi.names("Dev A"), [\k1], "the mappings of a device");
		this.assertEquals(song.midi.names, [\k1, \k2], "every mapping");
		rec.arm(voices: (lead: [b1]), inputs: #[\actions]);
		rec.addVoice(\fx, [b2, \knob]);
		rec.addVoice(\devA, song.midi.names("Dev A"));
		this.assertEquals(rec.voiceFor(b1), \lead);
		this.assertEquals(rec.voiceFor(b2), \fx, "added after arm");
		this.assertEquals(rec.voiceFor(\knob), \fx, "a name too");
		this.assertEquals(rec.voiceFor(\k1), \devA, "a device's mappings as one voice");
		rec.arm(inputs: #[\actions]);
		this.assertEquals(rec.voiceFor(b2), \fx, "arm without voices keeps them");
		rec.addVoice(\lead, [b2]);
		this.assertEquals(rec.voiceFor(b2), \lead, "a member moves to the new voice");
		rec.removeVoice(\lead);
		this.assertEquals(rec.voiceFor(b1), 'core/a', "removed: the default again");
		rec.arm(voices: (), inputs: #[\actions]);
		this.assertEquals(rec.voiceFor(\knob), \knob, "an empty Event clears them");
		rec.arm(voicesByLayer: true, inputs: #[\actions]);
		this.assertEquals(rec.voiceFor(b1), \core, "voicesByLayer: a beat's default voice is its layer");
		rec.addControl(\knob, ControlSpec(20, 2000, \exp, 0, 440, "Hz"));
		rec.addControl(\mix, IdentityDictionary[\min -> 0, \max -> 1]);
		rec.record(snapshotAtStart: false);
		b1.set(\amp, 0.5);
		s = rec.stop;
		this.assertEquals(s.at(1)[\voice], \core);
		this.assertEquals(s.controls[\knob][\warp], "exp", "a ControlSpec becomes a spec");
		this.assertEquals(s.controls[\knob][\max], 2000);
		this.assertEquals(s.controls[\knob][\unit], "Hz");
		this.assertEquals(s.controls[\mix][\max], 1, "a dictionary as it is");
		this.assertEquals(s.voices[\lead], nil, "a removed voice is not in the take");
		rec.voicesByLayer = false;
	}

	test_raw_midi {
		var got = nil, s;
		var def = MIDIdef.noteOn(\re_test_raw, { |vel, note, chan, src| got = [note, vel, chan] });
		rec.arm(inputs: #[\rawMidi, \midi, \actions]);
		this.assert(this.logHas("rawMidi replaces"), "rawMidi replaces midi and keyboard");
		this.assert(rec.inputs.includes(\midi).not and: { rec.inputs.includes(\rawMidi) });
		this.assert(RETap.rawMidiInstalled, "the raw hooks are installed");
		rec.record(snapshotAtStart: false);
		MIDIIn.doNoteOnAction(7, 1, 60, 100);
		MIDIIn.doControlAction(7, 1, 7, 64);
		MIDIIn.doBendAction(7, 1, 8192);
		MIDIIn.doNoteOffAction(7, 1, 60, 0);
		s = rec.stop;
		this.assertEquals(s.ofKind(\rawMidi).collect(_[\msg]), [\noteOn, \control, \bend, \noteOff], "every message as sent");
		this.assertEquals([s.at(1)[\num], s.at(1)[\value], s.at(1)[\chan], s.at(1)[\src]], [60, 100, 1, 7], "note, velocity, channel, source");
		this.assertEquals(s.at(1)[\voice], \midi, "voice: the device's name (unknown uid: midi)");
		this.assertEquals(got, [60, 100, 1], "a MIDIdef of the piece still fired");
		this.assertEquals(REScore.controlKey(s.at(2)), [\cc, "midi", 7], "a CC is a control");
		this.assert(REScore.isContinuous(s.at(2)));
		this.assertEquals(REScore.controlKey(s.at(1)), nil, "a note is not");
		rec.disarm;
		this.assert(RETap.rawMidiInstalled.not, "disarm removes the hooks");
		def.free;
	}

	test_onEvent_feed {
		var b = this.restBeat(layer, \k, [amp: 0.1]);
		var seen = List.new;
		rec.onEvent = { |r, ev| seen.add([r, ev[\kind], ev[\id]]) };
		rec.arm(inputs: #[\actions]);
		rec.record(snapshotAtStart: true);
		b.set(\amp, 0.5);
		rec.snapshot(\x);
		rec.onEvent = { nil.explode };
		b.set(\amp, 0.6);
		rec.stop;
		this.assertEquals(seen.collect(_[1]), [\snapshot, \action, \snapshot], "every recorded event reaches onEvent, in order");
		this.assert(seen.every { |x| x[0] === rec }, "with the recorder");
		this.assertEquals(seen.collect(_[2]), [1, 2, 3], "after the id is assigned");
		this.assertEquals(rec.lastScore.size, 4, "a failing hook is reported and the take goes on");
		this.assert(this.logHas("explode"));
		rec.onEvent = nil;
	}

	test_scope_and_inputs {
		var other = song.layer(\details);
		var b1 = this.restBeat(layer, \a);
		var b2 = this.restBeat(other, \b);
		var s;
		rec.arm(scope: [layer], inputs: #[\actions]);
		this.assert(RETap.codeHooksInstalled.not, "no code input: no hooks");
		rec.record(snapshotAtStart: false);
		b1.set(\amp, 0.2);
		b2.set(\amp, 0.3);
		song.pauseAllBeats;
		s = rec.stop;
		this.assertEquals(s.size, 1, "only the layer in scope (the song itself is out)");
		this.assertEquals(s.at(1)[\voice], 'core/a');
		rec.arm(scope: [b2, song.midi], inputs: #[\actions, \midi]);
		rec.record(snapshotAtStart: false);
		b1.set(\amp, 0.2);
		b2.set(\amp, 0.3);
		s = rec.stop;
		this.assertEquals(s.size, 1, "an object in scope");
		this.assertEquals(s.at(1)[\voice], 'details/b');
		rec.arm(inputs: #[\actions, \nope]);
		this.assert(this.logHas("unknown input kind"), "an unknown input kind is reported");
	}

	test_inputs_and_causes {
		var got = nil;
		var b = this.restBeat(layer, \k, [amp: 0.1]);
		var s, mid, osc;
		song.midi.control(\knob, 5, 0, "No Such Device", { |x| x / 127 }, { |v, raw| got = v; b.set(\amp, v) });
		song.osc.def(\scene, \init, { |msg| b.set(\amp, 0.9) }, print: false);
		song.osc.editDef(\core, \k, \louder, \amp, 0.8, print: false, printModif: false);
		rec.arm;
		rec.record(snapshotAtStart: false);
		MIDIdef(\rc_rec_knob).func.value(64, 5, 0, nil);
		OSCdef(\rec_scene_init).func.value(['/rec/scene/init', 1], 0, nil, nil);
		OSCdef(\rec_k_louder).func.value;
		s = rec.stop;
		this.assertEquals(s.kinds, [\midi, \osc], "inputs recorded; the actions they trigger are not");
		this.assertEquals(s.size, 3);
		mid = s.ofKind(\midi)[0];
		this.assertEquals(mid[\name], \knob);
		this.assertEquals(mid[\raw], 64, "the raw CC value");
		this.assertFloatEquals(mid[\value], 64 / 127, "the mapped value");
		this.assertEquals(mid[\voice], \knob, "voice: the mapping's name");
		this.assertFloatEquals(got, 64 / 127, "the action ran");
		this.assertEquals(b.keyProxy(\amp).source, 0.8, "the handlers ran");
		osc = s.ofKind(\osc);
		this.assertEquals(osc[0][\key], \rec_scene_init);
		this.assertEquals(osc[0][\path], "/rec/scene/init");
		this.assertEquals(osc[0][\args], [1], "the message arguments");
		this.assertEquals(osc[0][\voice], \rec_scene_init, "voice: the def's key");
		this.assertEquals(osc[1][\args], nil, "called without a message: no arguments");
		this.assertEquals(RETap.frames.size, 0, "no cause left open");
	}

	test_code_lines {
		var interp = thisProcess.interpreter;
		var savedPre = interp.preProcessor;
		var b = this.restBeat(layer, \k, [amp: 0.1]);
		var s;
		rec.arm(inputs: #[\code, \actions]);
		rec.record(snapshotAtStart: false);
		this.assertEquals(interp.preProcessor.value("b.set(\\amp, 0.5)", interp), "b.set(\\amp, 0.5)", "the hook returns the code unchanged");
		b.set(\amp, 0.5);
		interp.codeDump.value("b.set(\\amp, 0.5)", nil, { }, interp);
		interp.preProcessor.value("0.exit", interp);
		interp.codeDump.value("0.exit", nil, { }, interp);
		interp.preProcessor.value("1 +", interp);
		interp.codeDump.value("1 +", nil, nil, interp);
		s = rec.stop;
		this.assertEquals(s.ofKind(\code).collect { |e| e[\text] }, ["b.set(\\amp, 0.5)", "0.exit"], "code lines recorded; one that did not compile is dropped");
		this.assertEquals(s.at(1)[\voice], \code);
		this.assertEquals(s.at(2)[\kind], \action, "the action the line triggered");
		this.assertEquals(s.at(2)[\cause], 1, "caused by the line");
		this.assertEquals(s.at(3)[\replay], false, "an unsafe line is kept but not replayed");
		this.assertEquals(RETap.frames.size, 0, "no frame left open");
		rec.disarm;
		this.assert(RETap.codeHooksInstalled.not, "disarm removes the hooks");
		this.assert(interp.preProcessor === savedPre, "the interpreter's preProcessor is restored");
	}

	test_main_thread_rule_and_silently {
		var b = this.restBeat(layer, \k);
		var s;
		RETap.mainThreadOnly = true;
		rec.arm(inputs: #[\actions]);
		rec.record(snapshotAtStart: false);
		b.set(\amp, 0.5);
		s = rec.stop;
		this.assertEquals(s.size, 0, "a call from a Routine is the program's own work: not recorded");
		rec.record(snapshotAtStart: false);
		clock.sched(0, { b.set(\amp, 0.8); nil });
		0.3.wait;
		s = rec.stop;
		this.assertEquals(s.size, 1, "a clock-scheduled Function runs on the main thread: recorded");
		this.assertEquals(s.at(1)[\args][1], 0.8);
		RETap.mainThreadOnly = false;
		rec.record(snapshotAtStart: false);
		RETap.silently { b.set(\amp, 0.6) };
		b.set(\amp, 0.7);
		s = rec.stop;
		this.assertEquals(s.size, 1, "silently: not recorded");
		this.assertEquals(s.at(1)[\args][1], 0.7);
	}

	test_snapshot_and_voices {
		var b1 = this.restBeat(layer, \a, [amp: 0.1]);
		var b2 = this.restBeat(layer, \b);
		var s;
		rec.arm(voices: (lead: [b1, b2], fx: [\knob]));
		rec.record(snapshotAtStart: false);
		b1.set(\amp, 0.2);
		b2.set(\amp, 0.3);
		rec.snapshot(\intro);
		s = rec.stop;
		this.assertEquals(s.at(1)[\voice], \lead, "a voice named at arm");
		this.assertEquals(s.at(2)[\voice], \lead);
		this.assertEquals(s.voices[\lead][\objects].size, 2, "the voice's objects in the file");
		this.assertEquals(s.voices[\fx][\objects], ["knob"]);
		this.assertEquals(s.at(3)[\kind], \snapshot);
		this.assertEquals(s.at(3)[\name], \intro);
		this.assertEquals(s.at(3)[\state][\beats]['core/a'][\amp], 0.2, "the beats' current sources");
		this.assertEquals(s.at(3)[\state][\beats]['core/a'][\playing], true);
		this.assertEquals(s.at(3)[\state][\beats]['core/b'][\real_dur], 1, "the dur source");
		this.assertEquals(s.at(3)[\state][\seed], 1, "the song's seed");
		this.assertEquals(rec.snapshot(\late), nil, "not recording: no snapshot");
	}

	test_quantized_start_and_file {
		var s, files;
		rec.root = dir;
		rec.version = "w0";
		rec.arm(inputs: #[\actions]);
		rec.record([1, 0], snapshotAtStart: false);
		this.assertEquals(rec.state, \armed, "waiting for the grid");
		0.3.wait;
		this.assertEquals(rec.state, \recording, "started on the grid");
		this.assertEquals(rec.beat0, rec.beat0.round(1), "on a whole beat");
		s = rec.stop;
		this.assertEquals(s.meta[\beat0], rec.beat0);
		files = (dir +/+ "*_rec w0.json").pathMatch;
		this.assertEquals(files.size, 1, "written under root as <stamp>_<song> <version>.json");
		this.assertEquals(REScore.read(files[0]).meta, s.meta, "and reads back");
		files.do { |f| File.delete(f) };
		File.delete(dir);
	}

	// An overdub: the old take plays while a new one is recorded over it; stop
	// merges them (the old voice's events in the span replaced here).
	test_overdub {
		var b = this.restBeat(layer, \k, [amp: 0.1]);
		var s, merged, player;
		rec.arm(inputs: #[\actions]);
		rec.record(snapshotAtStart: false);
		b.set(\amp, 0.2);
		(1 / clock.tempo).wait;
		b.set(\amp, 0.3);
		(1 / clock.tempo).wait;
		s = rec.stop;
		this.assertEquals(s.size, 2);
		rec.overdub(s, ['core/k'], \replace, [0.5, 4]);
		player = rec.player;
		(0.3 / clock.tempo).wait;   // the start is one scheduled Function on the clock
		this.assert(rec.isRecording and: { player.isPlaying }, "both started");
		this.assert(player.muted.includes('core/k'), "replace: the voice is muted while overdubbing");
		b.set(\amp, 0.9);
		(0.3 / clock.tempo).wait;
		merged = rec.stop;
		this.assert(player.isPlaying.not, "stop stops the player");
		this.assertEquals(merged.eventsOf('core/k').collect { |e| e[\args][1] }, [0.2, 0.9], "the old event in the punch replaced by the new one, the rest kept");
		this.assert(merged.eventsOf('core/k').last[\beat] >= 0.5 and: { merged.eventsOf('core/k').last[\beat] < 4 },
			"the new event's beat on the score's timeline (the punch-in plus the wait): " ++ merged.eventsOf('core/k').last[\beat]);
		this.assertEquals(merged.events.collect(_[\id]), [1, 3], "ids: the old one kept, the new one renewed after the highest");
		this.assertEquals(merged.meta[\overdubs].size, 1);
		this.assertEquals(rec.lastScore, merged, "the merge is the last score");
		this.assertEquals(s.size, 2, "the original take is untouched");
	}

	// The same scripted session with recording off and on: the same sources,
	// seeds, beats and log lines; the score is the only difference.
	runScript { |record|
		var sng = RCSong(\idt, 7);
		var l = sng.layer(\core);
		var b = this.restBeat(l, \k, [amp: Pwhite(0.1, 0.3)]);
		var r = sng.scoreRecorder;
		var res;
		if(record) { r.arm; r.record(snapshotAtStart: true) };
		b.set(\amp, Pseq([0.2, 0.4], inf), seed: 3);
		b.setAll([legato: 0.5]);
		this.restBeat(l, \k2);
		b.pause;
		b.resume;
		sng.osc.def(\x, \y, { b.set(\legato, 0.9) }, print: false);
		OSCdef(\idt_x_y).func.value(['/idt/x/y'], 0, nil, nil);
		res = [b.keyProxy(\amp).source.asCompileString, b.keyProxy(\amp).seed, b.keyProxy(\legato).source,
			l.beats.keys.asArray.sort, sng.allBeats.count(_.isPlaying)];
		if(record) { res = res.add(r.stop) };
		^res
	}

	test_identity_off_and_on {
		var rand0 = thisThread.randData;
		var off, on, logOff, logOn, s;
		RCLog.reset;
		off = this.runScript(false);
		logOff = this.prLogLines;
		this.assertEquals(thisThread.randData, rand0, "off: this thread's random state untouched");
		RCTestSupport.reset;
		RCTestSupport.bootSession;
		RCLog.reset;
		on = this.runScript(true);
		logOn = this.prLogLines;
		s = on.pop;
		this.assertEquals(on, off, "the same sources, seeds, beats and players: recording changed nothing");
		this.assertEquals(thisThread.randData, rand0, "on: this thread's random state untouched");
		this.assertEquals(logOn, logOff, "the same log lines, the recorder's own aside");
		this.assertEquals(s.ofKind(\action).collect { |e| e[\method] }, [\set, \setAll, \addBeat, \pause, \resume], "the score holds the actions");
		this.assertEquals(s.ofKind(\osc).size, 1, "and the input");
		this.assertEquals(s.ofKind(\snapshot).size, 1, "and the start snapshot");
	}
}
