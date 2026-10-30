// REShowConsole: a show of one cue whose control tape is a small file, Reaper's messages driven
// through the public methods (timeReceived, transportReceived) and the heartbeat's tick, the sends
// captured; then the real port, the heartbeat and a take's render sidecar.
TestREShowConsole : UnitTest {
	var sent, root, tapePath, now, savedRateLimit, savedSink;

	setUp {
		var t = REVisualsTape.new;
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
		savedSink = RCVisuals.sink;
		sent = List.new;
		now = 0;
		root = PathName.tmp +/+ "re_console_test_" ++ UniqueID.next;
		File.mkdir(root);
		tapePath = root +/+ "one.ctl.json";
		t.add(0, '/fx/chain', ["colour"]);
		t.add(1, '/fx/ctl', ["wave", 0.2]);
		t.add(2, '/fx/ctl', ["wave", 0.6]);
		t.add(2.5, '/fx/set', ["colour", "mix", 0.3]);
		t.add(3, '/fx/start', [0]);
		t.add(4, '/fx/ctl', ["kick", 1]);
		t.add(6, '/lsys/node', [1, 2, 3]);
		t.write(tapePath);
		REVisualsTape.clearCache;
	}

	tearDown {
		RCVisuals.sink = savedSink;
		RCLog.rateLimit = savedRateLimit;
		(root +/+ "*/*").pathMatch.do { |p| File.delete(p) };
		(root +/+ "*").pathMatch.do { |p| File.delete(p) };
		File.delete(root);
	}

	logHas { |text| ^RCLog.history.any { |e| e[2].contains(text) } }

	prShow {
		^(offset: 0.5, cues: [(name: "one", video: "one.mp4", start: 10, end: 20, tape: tapePath)],
			roto: (device: "No Such Device", glide: 1.0, knobs: [(cc: 0, ctl: "wave", op: \add, range: [-1, 1])]))
	}

	// a console with no heartbeat, no MIDI, the test's clock, its sends into `sent`; a /time never
	// goes stale (the tests feed times at their own pace: test_transport_derived_from_time sets it back)
	prConsole { |show|
		var c = REShowConsole(show ?? { this.prShow });
		c.heartbeat = false;
		c.startMidi = false;
		c.staleAfter = 1000;
		c.timeFunc = { now };
		c.sendFunc = { |p, a| sent.add([p.asString] ++ a) };
		^c
	}

	addresses { ^sent.collect(_[0]).asArray }

	// the sends as expected, numbers within 1e-6 (show times are sums of floats)
	assertSent { |expected, message = ""|
		var got = sent.asArray, same;
		same = got.size == expected.size and: {
			got.every { |m, i|
				var e = expected[i];
				m.size == e.size and: { m.every { |v, j| if(v.isNumber and: { e[j].isNumber }) { (v - e[j]).abs < 1e-6 } { v == e[j] } } }
			}
		};
		this.assert(same, message ++ if(same) { "" } { "\n  expected %\n  got      %".format(expected, got) });
	}

	test_start_sends_the_cues {
		var c = this.prConsole;
		this.assertEquals(c.offset, 0.5, "the show's offset");
		this.assertEquals(c.port, 57150, "the default port");
		this.assertEquals(c.rate, 15);
		c.start;
		this.assertEquals(c.cues.size, 1);
		this.assertEquals(c.cues[0][\chase].size, 7, "the tape read whole: a cue naming a tape itself is not filtered");
		this.assertSent([["/fx/chase", 1], ["/fx/cues"], ["/fx/cue", "one", "one.mp4", 10.0, 20.0, 0.0]], "chase on, the cues removed, one line per cue");
		this.assert(c.started and: { c.playing.not });
		this.assertEquals(c.time, nil, "no time from Reaper yet");
		this.assert(c.status.contains("no time from Reaper"));
		this.assertEquals(c.cueAt(10), c.cues[0]);
		this.assertEquals(c.cueAt(20), nil, "the end is out");
		this.assertEquals(c.cueAt(9.9), nil);
		this.assertEquals(c.cueNamed("one"), c.cues[0]);
		sent.clear;
		c.stop;
		this.assertSent([["/fx/chase", 0]]);
		this.assert(c.started.not);
		c.stop;
		this.assertEquals(sent.size, 1, "stopped twice: once");
	}

	test_time_seek_advance_and_jump {
		var c = this.prConsole;
		c.start;
		sent.clear;
		c.timeReceived(11.0);
		this.assertSent([["/fx/time", 11.5], ["/fx/chain", "colour"], ["/fx/ctl", "wave", 0.2]],
			"the first time while stopped lands the page: the show clock with the offset, the cue's state at it");
		this.assertEquals(c.playing, false, "a time alone does not start the transport");
		this.assertEquals(c.time, 11.5);
		sent.clear;
		c.transportReceived(1);
		this.assertEquals(this.addresses, ["/fx/transport", "/fx/time", "/fx/chain", "/fx/ctl"], "play: transport 1, then the state where Reaper is");
		this.assertEquals(sent[0], ["/fx/transport", 1]);
		this.assert(c.playing);
		sent.clear;
		now = 0.5;
		this.assertEquals(c.tick(now), 12.0, "the show clock extrapolated: 11 + 0.5 + the offset");
		this.assertSent([["/fx/time", 12.0], ["/fx/ctl", "wave", 0.6]], "the heartbeat sends the time and the window's entries");
		sent.clear;
		now = 1.0;
		c.timeReceived(12.0);
		this.assertEquals(sent.size, 0, "a time on the extrapolation: no seek");
		c.tick(now);
		this.assertSent([["/fx/time", 12.5], ["/fx/set", "colour", "mix", 0.3]]);
		sent.clear;
		now = 1.2;
		c.timeReceived(15.0);
		this.assertSent([["/fx/time", 15.5], ["/fx/chain", "colour"], ["/fx/ctl", "wave", 0.6], ["/fx/set", "colour", "mix", 0.3], ["/fx/ctl", "kick", 1]],
			"a jump: one time, the state at it (the last wave, the start dropped)");
		sent.clear;
		now = 1.3;
		c.tick(now);
		this.assertSent([["/fx/time", 15.6]], "between entries: the time alone");
		this.assert(c.status.contains("playing at 15.6") and: { c.status.contains("cue one") });
		sent.clear;
		now = 1.4;
		c.transportReceived(0);
		this.assertSent([["/fx/time", 15.5], ["/fx/transport", 0]], "stop: the time where Reaper stands, transport 0");
		this.assertEquals(c.playing, false);
		this.assertEquals(c.tick(2.0), nil, "stopped: no time sent");
		this.assertEquals(sent.size, 2);
		c.stop;
	}

	test_offset_is_live {
		var c = this.prConsole;
		c.start;
		c.timeReceived(10.0);
		c.transportReceived(1);
		sent.clear;
		c.offset = 0.25;
		now = 1.0;
		c.tick(now);
		this.assertEquals(sent[0], ["/fx/time", 11.25], "the offset applies at the next tick");
		c.stop;
	}

	test_cue_boundaries {
		var show = this.prShow, c;
		show[\cues] = show[\cues] ++ [(name: "two", video: "two.mp4", start: 30, end: 40, tape: tapePath)];
		c = this.prConsole(show);
		c.start;
		this.assertEquals(c.cues.collect(_[\name]), ["one", "two"]);
		sent.clear;
		c.timeReceived(24.0);
		this.assertSent([["/fx/time", 24.5]], "between cues: the time, no state");
		c.transportReceived(1);
		sent.clear;
		now = 5.1;
		c.tick(now);
		this.assertSent([["/fx/time", 29.6]]);
		now = 5.6;
		c.tick(now);
		this.assertSent([["/fx/time", 29.6], ["/fx/time", 30.1], ["/fx/chain", "colour"]], "the next cue entered by playing: its state at the time");
		this.assertEquals(c.currentCue[\name], "two");
		sent.clear;
		now = 6.6;
		c.tick(now);
		this.assertSent([["/fx/time", 31.1], ["/fx/ctl", "wave", 0.2]], "then advanced");
		c.stop;
	}

	test_transport_derived_from_time {
		var c = this.prConsole;
		c.staleAfter = 0.3;
		c.start;
		c.timeReceived(11.0);
		c.transportReceived(1);
		now = 0.1;
		c.timeReceived(11.1);
		now = 0.2;
		c.tick(now);
		this.assert(c.playing, "a fresh time: playing");
		sent.clear;
		now = 0.6;
		c.tick(now);
		this.assertSent([["/fx/time", 11.6], ["/fx/transport", 0]], "no time for 0.3 s: paused, the page left where Reaper last was");
		this.assertEquals(c.playing, false);
		sent.clear;
		now = 1.0;
		c.timeReceived(11.1);
		this.assertEquals(sent.size, 0, "the same time again while stopped: nothing");
		now = 1.1;
		c.timeReceived(11.2);
		this.assert(c.playing, "a time advancing in step with real time: playing");
		this.assertEquals(this.addresses[0], "/fx/transport");
		sent.clear;
		c.transportReceived(0);
		now = 2.0;
		c.timeReceived(5.0);
		this.assertEquals(sent.asArray[2..], [["/fx/time", 5.5]], "a time far off while stopped: a seek (the cursor moved), not a start");
		this.assertEquals(c.playing, false);
		this.assertEquals(c.cueAt(5.5), nil);
		c.stop;
	}

	test_roto_mixes_the_tape {
		var c = this.prConsole;
		c.start;
		this.assert(c.roto.notNil and: { c.roto.started.not }, "the roto built, its MIDI not started (startMidi false)");
		c.timeReceived(12.5);
		sent.clear;
		c.roto.knobReceived(0, 1.0, 0);
		this.assertSent([["/fx/ctl", "wave", 1.6]], "the knob at the top adds 1 to the tape's last value, 0.6");
		sent.clear;
		c.timeReceived(11.0);
		this.assertSent([["/fx/time", 11.5], ["/fx/chain", "colour"], ["/fx/ctl", "wave", 1.2]], "a mapped ctl in the tape carries the mix's value: 0.2 + 1");
		sent.clear;
		c.roto.buttonReceived(0, 60, 0);   // the tape held at 0.2
		sent.clear;
		c.timeReceived(12.5);
		this.assertSent([["/fx/time", 13.0], ["/fx/chain", "colour"], ["/fx/ctl", "wave", 1.2], ["/fx/set", "colour", "mix", 0.3]], "held: the tape's 0.6 still goes out as 0.2 + 1");
		c.roto.buttonReceived(0, 0, 1);    // the hold left at 1
		sent.clear;
		now = 1.5;
		c.tick(now);
		this.assertSent([["/fx/ctl", "wave", 1.4]], "stopped, the heartbeat still sends the glide: half way from 0.2 to 0.6, plus 1");
		c.stop;
	}

	test_reload {
		var show = this.prShow, c = this.prConsole(show);
		c.start;
		c.timeReceived(11.0);
		sent.clear;
		show[\cues][0][\video] = "other.mp4";
		c.reload;
		this.assertSent([["/fx/cues"], ["/fx/cue", "one", "other.mp4", 10.0, 20.0, 0.0], ["/fx/time", 11.5], ["/fx/chain", "colour"], ["/fx/ctl", "wave", 0.2]],
			"the cues sent again, the chase landed where the clock is");
		c.stop;
	}

	// a cue naming a take: the control tape through the render sidecar, `end` from its duration; the
	// sidecar itself as the take; a take without a `ctl` tape: the visuals tape kept to its /fx/
	// entries; cues without an end (open), without anything, with a missing sidecar
	test_cues_from_takes {
		var take = root +/+ "260101_120000_show w0.json", c, show, viz, cueLines;
		var renderDir = REScore.renderDir(take);
		File.mkdir(renderDir);
		REJSON.write((format: "re-render", version: 1, take: take.basename, wav: "x.wav", duration: 7.25, ctl: tapePath), REScore.renderPath(take), 2, 2);
		viz = renderDir +/+ (renderDir.basename ++ ".viz.json");
		File.copy(tapePath, viz);
		show = (dir: root, cues: [
			(name: "a", take: take.basename, start: 100),
			(name: "b", tape: "one.ctl.json", start: 200, duration: 3),
			(name: "c", start: 300),
			(name: "d", take: "nothing.json", start: 400),
			(name: "e", tape: "one.ctl.json", start: 500),
			(name: "f", take: REScore.renderPath(take).basename, start: 600),
			(name: "g", take: "nothing.render.json", start: 700)
		]);
		c = this.prConsole(show);
		c.start;
		this.assertEquals(c.cues.size, 7);
		this.assertEquals(c.cues[0][\tape], tapePath, "the sidecar's ctl tape");
		this.assertEquals(c.cues[0][\sidecar], REScore.renderPath(take), "the sidecar found next to the take");
		this.assertEquals(c.cues[0][\end], 107.25, "start plus the render's duration");
		this.assertEquals(c.cues[0][\chase].size, 7);
		this.assertEquals(c.cues[1][\tape], root +/+ "one.ctl.json", "a path relative to the show's folder");
		this.assertEquals(c.cues[1][\end], 203.0, "the cue's own duration");
		this.assertEquals(c.cues[2][\chase].size, 0, "no take, no tape: an empty chase");
		this.assertEquals(c.cues[2][\end], inf, "no end, no duration: open");
		this.assert(this.logHas("neither take nor tape") and: { this.logHas("no end, no duration: open") });
		this.assertEquals(c.cues[3][\chase].size, 0, "a take without a render: said, no tape");
		this.assert(this.logHas("no render sidecar next to nothing.json"));
		this.assertEquals(c.cues[4][\end], inf, "a tape but no end: open too (the tape's length says nothing about the audio's)");
		this.assertEquals(c.cues[5][\tape], tapePath, "the sidecar itself as the take: its ctl tape");
		this.assertEquals(c.cues[5][\take], take, "the take named by the sidecar, in its folder");
		this.assertEquals(c.cues[5][\end], 607.25, "its duration");
		this.assertEquals(c.cues[5][\chase].size, 7);
		this.assertEquals(c.cues[6][\chase].size, 0, "a sidecar path that is not there: no tape");
		this.assert(this.logHas("no render sidecar at " ++ root +/+ "nothing.render.json"), "said with its path");
		this.assertEquals(c.cues[6][\end], inf);
		cueLines = sent.select { |m| m[0] == "/fx/cue" };
		this.assertEquals(cueLines.collect(_[1]), ["a", "b", "c", "d", "e", "f", "g"]);
		this.assertEquals(cueLines[2][4], REShowConsole.openEnd, "an open cue's end goes to the page as openEnd (1e9 s), never inf");
		this.assertEquals(cueLines[0][4], 107.25);
		this.assertEquals(c.cueAt(350), c.cues[2], "an open cue holds every later time...");
		this.assertEquals(c.cueAt(450), c.cues[3], "...until a later cue starts");
		this.assertEquals(c.cueAt(1e8), c.cues[6], "the last open cue for ever");
		c.stop;
		// the same take without a ctl tape: the visuals tape, /fx/ entries alone
		REJSON.write((format: "re-render", version: 1, take: take.basename, wav: "x.wav", duration: 7.25, tape: renderDir.basename +/+ viz.basename), REScore.renderPath(take), 2, 2);
		REVisualsTape.clearCache;
		c = this.prConsole((dir: root, cues: [(name: "a", take: take.basename, start: 100)]));
		c.start;
		this.assertEquals(c.cues[0][\tape], viz);
		this.assertEquals(c.cues[0][\chase].size, 6, "the /lsys/ entry left out");
		this.assert(this.logHas("visuals tape read for its /fx/ entries"));
		c.stop;
	}

	// a cue with no sidecar, no tape, no end: the console runs, the cue is current from its start,
	// nothing is sent for it but /fx/time, no error; and the transport before any time
	test_open_cue_without_anything {
		var c = this.prConsole((cues: [(name: "bare", start: 10), (name: "next", start: 30, end: 40, tape: tapePath)]));
		c.start;
		this.assertSent([["/fx/chase", 1], ["/fx/cues"], ["/fx/cue", "bare", "", 10.0, REShowConsole.openEnd, 0.0], ["/fx/cue", "next", "", 30.0, 40.0, 0.0]]);
		this.assertEquals(c.cues[0][\end], inf);
		this.assertEquals(c.cueAt(9.9), nil);
		this.assertEquals(c.cueAt(10), c.cues[0], "current from its start");
		this.assertEquals(c.cueAt(29.9), c.cues[0]);
		this.assertEquals(c.cueAt(30), c.cues[1], "a later cue takes over");
		this.assertEquals(c.cueAt(40), c.cues[0], "and the open cue holds again after it");
		sent.clear;
		c.transportReceived(1);
		this.assertSent([["/fx/transport", 1]], "play before any time: the transport alone, no clock to seek");
		this.assertEquals(c.time, nil);
		this.assertEquals(c.tick(0.1), nil, "a tick without a time: nothing");
		this.assertEquals(sent.size, 1);
		now = 0.2;
		c.timeReceived(9.0);
		this.assertSent([["/fx/transport", 1], ["/fx/time", 9.0]], "the first time lands the page: between cues, the time alone");
		sent.clear;
		now = 2.0;
		c.tick(now);
		this.assertSent([["/fx/time", 10.8]], "the bare cue entered: its (empty) state, the time alone");
		this.assertEquals(c.currentCue[\name], "bare");
		this.assert(c.status.contains("cue bare") and: { c.status.contains("open") });
		sent.clear;
		now = 3.0;
		c.tick(now);
		this.assertSent([["/fx/time", 11.8]], "and on");
		this.assert(RCLog.history.any { |e| e[1] == \error }.not, "no error logged: %".format(RCLog.history.select { |e| e[1] == \error }.collect(_[2])));
		sent.clear;
		c.transportReceived(0);
		this.assertSent([["/fx/time", 9.0], ["/fx/transport", 0]], "stop: where Reaper last said");
		c.stop;
	}

	test_show_from_a_file {
		var showPath = root +/+ "show.json", c;
		REJSON.write((offset: 0.1, port: 57170, rate: 10, cues: [(name: "one", video: "one.mp4", start: 10, end: 20, tape: "one.ctl.json")]), showPath, 2, 3);
		c = REShowConsole(showPath);
		c.heartbeat = false;
		c.startMidi = false;
		c.sendFunc = { |p, a| sent.add([p.asString] ++ a) };
		this.assertEquals([c.offset, c.port, c.rate], [0.1, 57170, 10.0]);
		this.assertEquals(c.dir, root, "paths relative to the show file's folder");
		c.start;
		this.assertEquals(c.cues[0][\tape], root +/+ "one.ctl.json");
		this.assertEquals(c.cues[0][\chase].size, 7);
		c.stop;
		c = REShowConsole(root +/+ "missing.json");
		this.assert(this.logHas("no show at"));
		this.assertEquals(c.cues.size, 0);
	}

	// Reaper's messages over the real port and the heartbeat on SystemClock
	test_udp_port_and_heartbeat {
		var c = REShowConsole(this.prShow), port = 57160, addr, sends;
		while { thisProcess.openUDPPort(port).not and: { port < 57200 } } { port = port + 1 };
		c.port = port;
		c.startMidi = false;
		c.rate = 20;
		c.sendFunc = { |p, a| sent.add([p.asString] ++ a) };
		c.start;
		addr = NetAddr("127.0.0.1", port);
		addr.sendMsg('/time', 3.25);
		this.wait({ c.lastTime == 3.25 }, "/time received on the console's port", 2);
		this.assertEquals(c.time, 3.75);
		addr.sendMsg('/play', 1);
		this.wait({ c.playing }, "/play 1 starts the transport", 2);
		sent.clear;
		addr.sendMsg('/time', 3.3);
		addr.sendMsg('/time', 3.35);
		0.25.wait;
		sends = sent.select { |m| m[0] == "/fx/time" };
		this.assert(sends.size >= 3, "the heartbeat sends the time at its rate (% in 0.25 s)".format(sends.size));
		this.assert(sends.every { |m| m[1] >= 3.8 and: { m[1] < 4.3 } }, "the show clock extrapolated from Reaper's time, with the offset");
		addr.sendMsg('/stop', 1);
		this.wait({ c.playing.not }, "/stop 1 stops it", 2);
		this.assertEquals(sent.last, ["/fx/transport", 0]);
		addr.sendMsg('/play', 1);
		this.wait({ c.playing }, "playing again", 2);
		addr.sendMsg('/pause', 1);
		this.wait({ c.playing.not }, "/pause 1 stops it", 2);
		addr.sendMsg('/stop', 0);
		0.05.wait;
		this.assertEquals(c.playing, false, "/stop 0 says nothing");
		c.stop;
		this.assertEquals(sent.last, ["/fx/chase", 0]);
		addr.sendMsg('/time', 9.0);
		0.1.wait;
		this.assertFloatEquals(c.lastTime, 3.35, "stopped: the handlers are gone (OSC floats are 32-bit)");
	}

	test_default_send_goes_through_rcvisuals {
		var got = List.new, c = REShowConsole(this.prShow);
		RCVisuals.sink = { |p, a| got.add([p, a]) };
		c.heartbeat = false;
		c.startMidi = false;
		c.start;
		this.assertEquals(got[0], ['/fx/chase', [1]], "RCVisuals.sendArgs, the address a Symbol");
		c.stop;
		RCVisuals.sink = savedSink;
	}
}
