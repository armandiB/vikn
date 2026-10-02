// REScoreVisuals: the feed to HomewareVisuals through RCVisuals' sink: a take recorded (take,
// event, head, stop), a take played (take, events in chunks, played, head, stopPlay), the hooks
// chained and put back.
TestREScoreVisuals : UnitTest {
	var clock, song, layer, rec, viz, msgs, savedSink, savedEnabled, savedRateLimit, savedMainThreadOnly;

	setUp {
		clock = RCTestSupport.clock;
		RCTestSupport.bootSession;
		song = RCSong(\viz, 1);
		layer = song.layer(\core);
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
		savedMainThreadOnly = RETap.mainThreadOnly;
		RETap.mainThreadOnly = false;
		savedSink = RCVisuals.sink;
		savedEnabled = RCVisuals.enabled;
		RCVisuals.enabled = true;
		msgs = List.new;
		RCVisuals.sink = { |path, args| msgs.add([path, REJSON.parse(args[0].asString)]) };
		rec = song.scoreRecorder;
	}

	tearDown {
		viz !? (_.detach);
		rec.disarm;
		RCVisuals.sink = savedSink;
		RCVisuals.enabled = savedEnabled;
		RETap.mainThreadOnly = savedMainThreadOnly;
		RCTestSupport.reset;
		RCLog.rateLimit = savedRateLimit;
	}

	of { |path| ^msgs.select { |m| m[0] == path }.collect(_[1]) }

	test_recording_feed {
		var b = layer.addBeat(\k, [type: \rest, dur_flex: 1, amp: 0.1]);
		var own = 0, take, ev, s;
		rec.onEvent = { own = own + 1 };
		viz = REScoreVisuals(rec);
		rec.arm(inputs: #[\actions]);
		rec.record(snapshotAtStart: false);
		b.set(\amp, 0.5);
		0.25.wait;
		s = rec.stop;
		take = this.of("/score/take");
		this.assertEquals(take.size, 1, "a take message at the start");
		this.assertEquals(take[0][\state], "recording");
		this.assertEquals(take[0][\song], "viz", "the song's name as a plain string");
		this.assertEquals(take[0][\tempo], clock.tempo);
		ev = this.of("/score/event");
		this.assertEquals(ev.size, 1, "the action as an event");
		this.assertEquals(ev[0][\kind], "action");
		this.assertEquals(ev[0][\voice], "core/k");
		this.assertEquals(ev[0][\control], "set k amp", "the control key as words");
		this.assertEquals(ev[0][\value], 0.5);
		this.assert(this.of("/score/head").size >= 2, "the recording head moved (" ++ this.of("/score/head").size ++ ")");
		this.assertEquals(this.of("/score/head")[0][\state], "recording");
		this.assertEquals(this.of("/score/stop").size, 1, "a stop message");
		this.assertEquals(this.of("/score/stop")[0][\events], s.size);
		this.assertEquals(own, 1, "the recorder's own onEvent still ran");
		viz.detach;
		this.assert(rec.onStart.isNil and: { rec.onStop.isNil }, "detach puts the hooks back");
		rec.record(snapshotAtStart: false);
		b.set(\amp, 0.6);
		rec.stop;
		this.assertEquals(this.of("/score/take").size, 1, "nothing more sent after detach");
		this.assertEquals(own, 2, "the recorder's own onEvent kept");
	}

	test_player_feed {
		var b = layer.addBeat(\k, [type: \rest, dur_flex: 1, amp: 0.1]);
		var s = REScore(song), p, evs, played;
		3.do { |i| s.add((beat: i * 0.1, secs: i * 0.1 / clock.tempo, kind: \action, voice: 'core/k', rc: REScore.rcRef(b), method: \set, args: REScore.encodeValue([\amp, 0.2 + (i / 10), nil, nil]))) };
		s.meta[\duration] = 0.5;
		s.meta[\tempo] = clock.tempo;
		REScoreVisuals.chunk = 2;
		p = REScorePlayer(s, song);
		p.alignPhase = false;
		viz = REScoreVisuals(nil, p);
		p.play(0);
		0.8.wait;
		this.assertEquals(this.of("/score/take").size, 1, "the loaded take announced");
		this.assertEquals(this.of("/score/take")[0][\state], "loaded");
		this.assertEquals(this.of("/score/take")[0][\events], 3);
		evs = this.of("/score/events");
		this.assertEquals(evs.size, 2, "the events in two chunks of two");
		this.assertEquals(evs[0][\of], 2);
		this.assertEquals(evs[0][\events].size, 2);
		this.assertEquals(evs[1][\events][0][\value], 0.4, "the last event's value");
		played = this.of("/score/played");
		this.assertEquals(played.size, 3, "every event fired was reported");
		this.assertEquals(played[0][\id], s.events[0][\id]);
		this.assert(this.of("/score/head").size >= 1, "the playhead moved");
		this.assertEquals(this.of("/score/stopPlay").size, 1, "the end of the take reported");
		REScoreVisuals.chunk = 40;
	}
}
