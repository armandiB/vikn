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
		// the actions below are called by hand, not typed: without code lines among the inputs
		// they are level 1 actions (with code lines, an action with no cause is the program's)
		rec.arm(inputs: #[\actions, \midi, \osc, \keyboard]);
		this.assert(RETap.codeHooksInstalled.not, "no code hooks without \\code");
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

	test_durlist_and_swing_actions {
		var b = this.restBeat(layer, \k);
		var s, ev;
		b.durList_([1, 1, 1, 1]);
		rec.arm(inputs: #[\actions]);
		rec.record(snapshotAtStart: false);
		b.durList.addHit(0.5);
		b.durList.removeHit(2);
		layer.swing.amount = 0.05;
		s = rec.stop;
		this.assertEquals(s.ofKind(\action).collect(_[\method]), [\addHit, \removeHit, \amount_], "dur list edits and swing settings are actions");
		ev = s.at(1);
		this.assertEquals(ev[\rc][\rc], "durList");
		this.assertEquals(ev[\rc][\name], "k", "under the beat that owns the list");
		this.assertEquals(ev[\voice], 'core/k', "in the beat's voice");
		this.assertEquals(REScore.resolve(ev[\rc], song), b.durList, "resolved through the beat");
		this.assertEquals(s.at(3)[\rc][\rc], "swing");
		this.assertEquals(REScore.resolve(s.at(3)[\rc], song), layer.swing, "resolved through the layer");
		this.assertEquals(s.at(3)[\voice], \core, "in the layer's voice");
		this.assertEquals(b.durList.array.size, 5, "the edits happened");
	}

	test_control_snapshots {
		var value = 0.2, s, snapId;
		rec.addControl(\knob, IdentityDictionary[\min -> 0, \max -> 1, \warp -> "lin"], get: { value }, set: { |v| value = v });
		this.assertEquals(rec.controlNames, [\knob]);
		rec.arm(inputs: #[\actions]);
		rec.record(snapshotAtStart: false);
		snapId = rec.snapshot(\a);
		value = 0.9;
		s = rec.stop;
		this.assertEquals(s.at(snapId)[\state][\controls][\knob], 0.2, "a snapshot holds the registered controls' values");
		REScorePlayer.applyState(song, s.at(snapId)[\state]);
		this.assertEquals(value, 0.2, "recalled through the setter");
		value = 1.0;
		REScorePlayer.applyState(song, s.at(snapId)[\state], 2);
		this.assertEquals(value, 1.0, "a morph has not moved yet");
		(3 / clock.tempo).wait;
		this.assertFloatEquals(value, 0.2, "and lands on the snapshot's value after its beats");
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

	// An OSC message carries its time (the bundle's timetag when the sender stamped it):
	// the event is stamped there when it is a little before now, else now.
	test_stamped_inputs {
		var s, osc, nowBeat, clock = song.clock;
		var handler;
		song.osc.def(\scene, \init, { }, print: false);
		handler = OSCdef(\rec_scene_init).func;
		rec.arm;
		rec.record(snapshotAtStart: false);
		1.wait;   // a second into the take (logical time: the Routine's)
		nowBeat = clock.beats - rec.beat0;
		handler.value(['/rec/scene/init', 1], thisThread.seconds, nil, nil);                 // now
		handler.value(['/rec/scene/init', 2], thisThread.seconds - 0.5, nil, nil);           // half a second ago
		handler.value(['/rec/scene/init', 3], thisThread.seconds + 1, nil, nil);             // a clock ahead of ours
		handler.value(['/rec/scene/init', 4], 295096770.76, nil, nil);                       // a timetag of 1: "immediately"
		handler.value(['/rec/scene/init', 5], thisThread.seconds - 60, nil, nil);            // too long ago
		handler.value(['/rec/scene/init', 6], thisThread.seconds - 10000, nil, nil);         // long before the take
		handler.value(['/rec/scene/init', 7], thisThread.seconds - 2, nil, nil);             // a second before the take started
		s = rec.stop;
		osc = s.ofKind(\osc).sort { |a, b| a[\args][0] <= b[\args][0] };
		this.assertEquals(osc.size, 7);
		this.assertFloatEquals(osc[0][\beat], nowBeat, "a message of now: now", 0.05);
		this.assertFloatEquals(osc[1][\beat], nowBeat - (0.5 * clock.tempo), "a stamped message: at its stamp", 0.05);
		this.assertFloatEquals(osc[1][\secs], osc[0][\secs] - 0.5, "the seconds too", 0.05);
		this.assertFloatEquals(osc[2][\beat], nowBeat, "a stamp in the future: now", 0.05);
		this.assertFloatEquals(osc[3][\beat], nowBeat, "an immediate timetag: now", 0.05);
		this.assertFloatEquals(osc[4][\beat], nowBeat, "a stamp beyond maxLag: now", 0.05);
		this.assertFloatEquals(osc[5][\beat], nowBeat, "a stamp long before the take: now", 0.05);
		this.assertFloatEquals(osc[6][\beat], 0, "a stamp before the take's start, within maxLag: at the start", 0.05);
		this.assertFloatEquals(osc[6][\secs], 0, "its seconds too", 0.05);
		this.assert(osc.every { |e| e[\time].isNil }, "the time is not a field of the event");
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

	// A line that raised (codeDump never came: the next line closes its frame) is marked; a
	// line whose OSC message came back as an input of this song is not replayed (the input
	// is); the grid a take started on is in its file.
	test_raised_lines_loopback_and_quant {
		var interp = thisProcess.interpreter;
		var b = this.restBeat(layer, \k, [amp: 0.1]);
		var s, lines, inputs, handler;
		song.osc.def(\scene, \init, { }, print: false);
		handler = OSCdef(\rec_scene_init).func;
		rec.arm(inputs: #[\code, \actions, \osc]);
		rec.record(snapshotAtStart: false);
		interp.preProcessor.value("b.set(\\amp, 0.5); nil.explode", interp);
		b.set(\amp, 0.5);                                                   // what the line did before raising
		interp.preProcessor.value("b.set(\\amp, 0.6)", interp);             // the next line closes the stale frame
		b.set(\amp, 0.6);
		interp.codeDump.value("b.set(\\amp, 0.6)", nil, { }, interp);
		interp.preProcessor.value("NetAddr(\"127.0.0.1\", 32345).sendMsg(\"/rec/scene/init\", 1)", interp);
		interp.codeDump.value("NetAddr(\"127.0.0.1\", 32345).sendMsg(\"/rec/scene/init\", 1)", nil, { }, interp);
		handler.value(['/rec/scene/init', 1], thisThread.seconds, nil, nil);   // the message comes back
		interp.preProcessor.value("NetAddr(\"10.0.0.5\", 9000).sendMsg(\"/other\", 1)", interp);
		interp.codeDump.value("NetAddr(\"10.0.0.5\", 9000).sendMsg(\"/other\", 1)", nil, { }, interp);
		handler.value(['/rec/scene/init', 2], thisThread.seconds, nil, nil);   // an input of its own
		s = rec.stop;
		lines = s.ofKind(\code);
		inputs = s.ofKind(\osc);
		this.assertEquals(lines.size, 4);
		this.assertEquals(lines[0][\raised], true, "a line that raised is marked");
		this.assertEquals(s.causedBy(lines[0][\id]).size, 1, "with the effect it had before raising");
		this.assertEquals(lines[1][\raised], nil, "a line that ran through is not");
		this.assertEquals(lines[2][\replay], false, "a line whose message came back as an input is not replayed");
		this.assertEquals(lines[2][\loopback], inputs[0][\id], "it names the input");
		this.assertEquals(inputs[0][\loopbackOf], lines[2][\id], "and the input names the line");
		this.assertEquals(lines[3][\replay], nil, "a line sending elsewhere replays");
		this.assertEquals(inputs[1][\loopbackOf], nil, "an input of its own is not a loopback");
		this.assertEquals(RETap.frames.size, 0, "no frame left open");
		this.assertEquals(s.meta[\quant], nil, "started now: no grid");
		rec.record([2, 0], snapshotAtStart: false);
		(2.5 / clock.tempo).wait;
		s = rec.stop;
		this.assertEquals(s.meta[\quant], 2, "the grid the take started on");
		this.assertEquals(s.meta[\beat0] mod: 2, 0.0, "on that grid");
	}

	// Level 1 holds what a human did: with code lines recorded, an action with no cause (a
	// clock-scheduled Function) is the program's work, level 2: counted when level 2 is off,
	// written to the companion file when on; the check says what threatens an exact replay.
	test_levels_and_check {
		var interp = thisProcess.interpreter;
		var b = this.restBeat(layer, \k, [amp: 0.1]);
		var s, path, loaded, program;
		rec.root = dir;
		rec.arm(inputs: #[\code, \actions]);
		rec.record(snapshotAtStart: false);
		interp.preProcessor.value("b.set(\\amp, 0.5)", interp);
		b.set(\amp, 0.5);
		interp.codeDump.value("b.set(\\amp, 0.5)", nil, { }, interp);
		interp.preProcessor.value("b.set(\\amp, 1.0.rand)", interp);
		b.set(\amp, 0.4);
		interp.codeDump.value("b.set(\\amp, 1.0.rand)", nil, { }, interp);
		clock.sched(0, { b.set(\legato, 0.5); nil });
		0.3.wait;
		s = rec.stop;
		this.assertEquals(s.size, 4, "two lines and their effects; the scheduled action is not in level 1");
		this.assert(s.events.every { |e| e[\level] == 1 }, "every event is level 1");
		this.assertEquals(s.meta[\unrecorded], 1, "the program action was counted");
		this.assertEquals(s.meta[\levels], nil, "no companion");
		this.assertEquals(s.meta[\randData].size, 3, "the main thread's random state");
		this.assertEquals(s.meta[\inputs], ["actions", "code"], "the inputs recorded");
		this.assert(s.meta[\check].notNil and: { s.meta[\check].any { |t| t.contains("not recorded") } }, "the check says so: " ++ s.meta[\check]);
		this.assert(s.meta[\check].any { |t| t.contains("random") }, "and names the line drawing random numbers");
		this.assert(s.meta[\check].any { |t| t.contains("without code lines") }.not, "code lines were among the inputs");
		rec.arm(inputs: #[\actions]);
		rec.record(snapshotAtStart: false);
		b.set(\amp, 0.2);
		s = rec.stop;
		this.assert(s.meta[\check].any { |t| t.contains("without code lines") }, "actions without code lines: the check warns of scheduled ones: " ++ s.meta[\check]);
		this.assert(this.logHas("check:"), "the check is posted");
		this.assertEquals(rec.lastCheck, s.meta[\check]);
		rec.arm(level2: true);
		rec.record(snapshotAtStart: false);
		interp.preProcessor.value("b.set(\\amp, 0.6)", interp);
		b.set(\amp, 0.6);
		interp.codeDump.value("b.set(\\amp, 0.6)", nil, { }, interp);
		clock.sched(0, { b.set(\legato, 0.7); nil });
		0.3.wait;
		s = rec.stop;
		path = rec.lastPath;
		program = s.ofLevel(2);
		this.assertEquals(s.size, 3, "the line, its effect, the program action");
		this.assertEquals(program.size, 1);
		this.assertEquals(program[0][\method], \set);
		this.assertEquals(program[0][\cause], nil, "a scheduled Function: no cause (level 2 with a cause comes with the tagged Routines)");
		this.assertEquals(s.meta[\levels], [1, 2]);
		this.assertEquals(s.meta[\unrecorded], nil);
		this.assertEquals(s.meta[\check], nil, "nothing to report");
		this.assert(File.exists(REScore.companionPath(path)), "the companion file next to the take");
		loaded = REScore.read(path, levels: 1);
		this.assertEquals(loaded.size, 2, "level 1 alone");
		loaded = REScore.read(path);
		this.assertEquals(loaded.size, 3, "with the companion");
		this.assertEquals(loaded.ofLevel(2).size, 1);
		this.assertEquals(loaded.ofLevel(2)[0][\id], program[0][\id], "one id space");
		this.assertEquals(loaded.rootOf(loaded.ofLevel(2)[0]), nil, "an orphan has no root");
		this.assertEquals(loaded.rootOf(loaded.at(2)), loaded.at(1), "an effect's root is its line");
		File.delete(REScore.companionPath(path));
		this.assertEquals(REScore.read(path).size, 2, "a missing companion: level 1 alone, warned");
		this.assert(this.logHas("program file"), "warned");
	}

	// A tagged Routine's actions are level 2 under the line that made it; a beat's player tags
	// what its pattern does with the beat; a plain Routine is seen by no one; the rate guard
	// sub-samples a doer that writes too fast.
	test_tagged_routines_and_rate_guard {
		var interp = thisProcess.interpreter;
		var b = this.restBeat(layer, \k, [amp: 0.1]);
		var b2 = this.restBeat(layer, \j, [amp: Pfunc { b.set(\legato, 0.3); 0.1 }]);
		var s, program, byBeat, byLine, r, lineId;
		currentEnvironment[\reTestBeat] = b;
		b2.stop;   // a beat plays from its creation: the line below plays it again, under its own cause
		rec.arm(inputs: #[\code, \actions], level2: true);
		rec.record(snapshotAtStart: false);
		interp.preProcessor.value("~reTestR = RETap.routine { ~reTestBeat.set(\\amp, 0.5); 0.05.wait; ~reTestBeat.set(\\amp, 0.6) }.play(clock)", interp);
		r = RETap.routine({ b.set(\amp, 0.5); 0.05.wait; b.set(\amp, 0.6) }).play(clock);
		interp.codeDump.value("~reTestR = ...", nil, { }, interp);
		lineId = rec.score.events.last[\id];
		interp.preProcessor.value("b2.play", interp);
		b2.play;
		interp.codeDump.value("b2.play", nil, { }, interp);
		RETap.mainThreadOnly = true;   // the real rule while the Routines run: a plain one is not the main thread
		Routine({ b.set(\amp, 0.7) }).play(clock);                    // plain: seen by no one
		0.5.wait;
		RETap.mainThreadOnly = false;
		b2.stop;
		s = rec.stop;
		program = s.ofLevel(2);
		byLine = program.select { |e| e[\cause] == lineId };
		byBeat = program.select { |e| e[\by].notNil };
		this.assertEquals(byLine.collect { |e| e[\args][1] }, [0.5, 0.6], "the tagged Routine's actions, level 2 under the line that made it");
		this.assertEquals(byLine[0][\by], nil, "no doer given");
		this.assert(program.every { |e| e[\args][1] != 0.7 }, "the plain Routine's action is seen by no one");
		this.assert(byBeat.size >= 2, "the beat's pattern acted, tagged: " ++ byBeat.size);
		this.assertEquals(byBeat[0][\by], IdentityDictionary[\rc -> "beat", \song -> "rec", \layer -> "core", \name -> "j"], "by the beat");
		this.assertEquals(byBeat[0][\method], \set);
		this.assertEquals(s.rootOf(byBeat[0]), s.events.detect { |e| e[\text] == "b2.play" }, "under the line that played the beat");
		this.assertEquals(s.ofLevel(1).select { |e| e[\kind] == \action }.collect { |e| e[\method] }, [\play], "level 1: the play, the line's effect");
		this.assertEquals(s.meta[\check], nil, "nothing to report");
		rec.rateGuard = 10;
		rec.record(snapshotAtStart: false);
		RETap.routine({ 40.do { |i| b.set(\amp, i / 40) }; 0.2.wait; b.set(\amp, 1) }, by: \burst).play(clock);   // 0.2 beat: past the quantum
		0.3.wait;
		s = rec.stop;
		program = s.ofLevel(2);
		this.assert(program.size < 20 and: { program.size >= 11 }, "the guard let the first 10 through, then one per quantum: " ++ program.size);
		this.assertEquals(program.last[\args][1], 1, "the later event, past the quantum, kept: " ++ program.collect { |e| [e[\beat].round(0.01), e[\args][1]] });
		this.assertEquals(program.last[\by], "burst", "a doer by name");
		this.assert(this.logHas("rate guard"), "said once");
		rec.rateGuard = nil;
		rec.record(snapshotAtStart: false);
		interp.preProcessor.value("Routine { 1.wait }.play(clock); Pbind(\\degree, 1).play(clock)", interp);
		interp.codeDump.value("Routine { 1.wait }.play(clock); Pbind(\\degree, 1).play(clock)", nil, { }, interp);
		s = rec.stop;
		this.assert(s.meta[\check].any { |t| t.contains("plain Routine") }, "the check flags a plain Routine: " ++ s.meta[\check]);
		this.assert(s.meta[\check].any { |t| t.contains("pattern by hand") }, "and a pattern played by hand");
		currentEnvironment[\reTestBeat] = nil;
		currentEnvironment[\reTestR] = nil;
	}

	// Level 3: the server's messages, on when asked; an RETapAddr stands in for the server's
	// address while a take records, the messages are kept under the cause open (a line, a
	// tagged Routine's), the ones that make no sound left out, the companion and the defs
	// written at stop.
	test_level3_server_messages {
		var interp = thisProcess.interpreter;
		var dead = Server.named[\reDead] ?? { Server(\reDead, NetAddr("127.0.0.1", 57996)) };   // nothing listens there
		var b = this.restBeat(layer, \k, [amp: 0.1]);
		var s, path, srv, lineId, dir3, r;
		song.server = dead;
		rec.root = dir;
		rec.arm(inputs: #[\code, \actions]);
		rec.record(snapshotAtStart: false);
		dead.sendBundle(0.2, ['/s_new', \default, 1000, 0, 1, \freq, 440]);
		s = rec.stop;
		this.assertEquals(s.ofLevel(3).size, 0, "level 3 off: no server event");
		this.assert(dead.addr.isKindOf(RETapAddr).not, "and nothing stood in for the server's address");
		rec.arm(level3: true);
		rec.record(snapshotAtStart: false);
		this.assert(dead.addr.isKindOf(RETapAddr), "recording with level 3: an RETapAddr stands in");
		interp.preProcessor.value("dead.sendBundle(0.2, [\\s_new])", interp);
		dead.sendBundle(0.2, ['/s_new', \default, 1000, 0, 1, \freq, 440], [15, 1000, \amp, 0.1]);
		interp.codeDump.value("dead.sendBundle(0.2, [\\s_new])", nil, { }, interp);
		lineId = rec.score.ofKind(\code).last[\id];
		dead.sendMsg('/sync', 7);          // makes no sound: left out
		dead.sendMsg('/n_free', 1000);     // from no line: no cause
		r = RETap.routine({ dead.sendBundle(nil, ['/n_set', 1000, \freq, 220]) }, b).play(clock);
		0.2.wait;
		s = rec.stop;
		this.assert(dead.addr.isKindOf(RETapAddr).not, "stop puts the real address back");
		srv = s.ofLevel(3);
		this.assertEquals(srv.size, 3, "the bundle under the line, the free, the tagged Routine's set: " ++ srv.size);
		this.assertEquals(srv[0][\msgs], [['/s_new', \default, 1000, 0, 1, \freq, 440], ['/n_set', 1000, \amp, 0.1]], "the messages, the numbered command named");
		this.assertEquals(srv[0][\cause], lineId, "under the line");
		this.assertEquals(srv[0][\latency], 0.2, "the bundle's time kept");
		this.assert(srv[0][\secs] >= 0.2, "due at the bundle's time");
		this.assertEquals(srv[0][\kind], \server);
		this.assertEquals(srv[1][\msgs], [['/n_free', 1000]]);
		this.assertEquals(srv[1][\cause], nil, "the free has no cause");
		this.assertEquals(srv[2][\by], IdentityDictionary[\rc -> "beat", \song -> "rec", \layer -> "core", \name -> "k"], "the tagged Routine's message, by the beat");
		this.assertEquals(s.meta[\levels], [1, 3], "the take says its levels");
		path = rec.lastPath;
		this.assert(File.exists(REScore.companionPath(path, 3)), "the level 3 companion next to the take");
		this.assertEquals(REScore.read(path).ofLevel(3).size, 0, "read loads level 3 only when asked");
		this.assertEquals(REScore.read(path, levels: 3).ofLevel(3).size, 3);
		this.assertEquals(REScore.decodeValue(REScore.read(path, levels: 3).ofLevel(3).detect { |e| e[\msgs].size == 2 }[\msgs][0]), ['/s_new', \default, 1000, 0, 1, \freq, 440], "a message read back decodes to what was sent (its file form until then, as args; the file sorted by beat: the free, due at once, comes first)");
		dir3 = REScore.defsPath(path);
		this.assert(File.exists(dir3 +/+ "default.scsyndef"), "the def the take names is written next to it: " ++ dir3);
		// a def sent as bytes during the take (a NodeProxy's temp def, not in the library): kept, written at stop
		rec.record(snapshotAtStart: false);
		dead.sendMsg('/d_recv', SynthDef(\re_tmp_def_x, { Out.ar(0, DC.ar(0)) }).asBytes);
		dead.sendBundle(0.2, ['/s_new', \re_tmp_def_x, 1001, 0, 1]);
		s = rec.stop;
		this.assertEquals(s.ofLevel(3).size, 1, "the bytes are not a level 3 event, the synth is");
		this.assert(SynthDescLib.global[\re_tmp_def_x].isNil, "the def is not in the library");
		this.assert(File.exists(REScore.defsPath(rec.lastPath) +/+ "re_tmp_def_x.scsyndef"), "its bytes written next to the take all the same");
		this.assertEquals(REScoreRecorder.defNameIn(SynthDef(\re_tmp_def_y, { Out.ar(0, DC.ar(0)) }).asBytes), \re_tmp_def_y, "the name read from a def's bytes");
		this.assert(REScoreRecorder.defNameIn(Int8Array[1, 2, 3]).isNil, "nil for bytes that are no def");
		song.server = nil;
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
		rec.arm(voices: (lead: [b1, b2], fx: [\knob]), inputs: #[\actions, \midi]);   // actions by hand: no code lines
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
		if(record) { r.arm(inputs: #[\actions, \midi, \osc, \keyboard]); r.record(snapshotAtStart: true) };   // actions by hand: no code lines
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
