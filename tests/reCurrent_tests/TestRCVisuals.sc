TestRCVisuals : UnitTest {
	var clock, song, savedRateLimit, savedSink, savedEnabled, log;

	setUp {
		clock = RCTestSupport.clock;
		RCTestSupport.bootSession;
		song = RCSong(\viz, 1);
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
		savedSink = RCVisuals.sink;
		savedEnabled = RCVisuals.enabled;
		log = List.new;
		RCVisuals.sink = { |path, args| log.add([path, args]) };
		RCVisuals.enabled = true;
		RCVisuals.resetCount;
	}

	tearDown {
		RCVisuals.freeRelays;
		RCVisuals.sink = savedSink;
		RCVisuals.enabled = savedEnabled;
		RCTestSupport.reset;
		RCLog.rateLimit = savedRateLimit;
	}

	logHas { |text| ^RCLog.history.any { |entry| entry[2].contains(text) } }

	test_send_and_enabled {
		this.assert(RCVisuals.send('/a', 1, 2.5), "send returns true");
		this.assertEquals(log.asArray, [['/a', [1, 2.5]]], "path and args reach the sink");
		this.assert(RCVisuals.sendArgs('/b', nil), "no args is fine");
		this.assertEquals(log.last, ['/b', []], "nil args become an empty array");
		RCVisuals.enabled = false;
		this.assertEquals(RCVisuals.send('/c'), false, "disabled: nothing sent");
		this.assertEquals(log.size, 2, "...and nothing sunk");
		RCVisuals.enabled = true;
		this.assertEquals(RCVisuals.sent, 2, "the counter counts what went out");
	}

	test_addr {
		var a = RCVisuals.setAddr("127.0.0.1", 57121);
		this.assertEquals([a.ip, a.port], ["127.0.0.1", 57121], "the bridge address");
		this.assert(RCVisuals.addr === a, "addr keeps the NetAddr");
	}

	test_sendIn {
		RCVisuals.sendIn(0, '/now', [1]);
		this.assertEquals(log.size, 1, "delay 0 sends synchronously");
		RCVisuals.sendIn(0.05, '/later', [2]);
		this.assertEquals(log.size, 1, "a delayed send is not out before the thread yields");
		this.wait({ log.size == 2 }, "the delayed send arrives", 2);
		this.assertEquals(log.last, ['/later', [2]], "...with its path and args");
	}

	test_eventDelay {
		var layer = song.layer(\core);
		var b = RCBeat(layer, \tb, [type: \rest, dur_flex: 1, timingOffset: 2, lag: 0.01]);
		var ev = b.asStream.next(Event.default);
		this.assertFloatEquals(RCVisuals.eventDelay((latency: 0.3, lag: 0.05)), 0.35, "an explicit latency plus the lag");
		this.assertFloatEquals(RCVisuals.eventDelay((latency: 0.3, timingOffset: 0)), 0.3, "a zero timing offset adds nothing");
		this.assertFloatEquals(RCVisuals.eventDelay(ev), Server.default.latency + 0.01 + (2 * clock.beatDur), "a beat's event: its server's latency, the lag, the timing offset in beats of its clock");
		b.free(post: false);
	}

	test_notePfunc_sends_when_the_sound_starts {
		var layer = song.layer(\core);
		var calls = 0;
		var b = RCBeat(layer, \tn, [type: \note, dur_flex: Pseq([Rest(1), 1, 1]), freq: 440, amp: 0.1,
			viz: RCVisuals.notePfunc('/n', { |ev| calls = calls + 1; [ev.freq, ev.amp] })]);
		var stream = b.asStream;
		var rest, note;
		rest = stream.next(Event.default);
		this.assert(rest.isRest, "the first event is a rest");
		this.assertEquals(calls, 0, "the function is not called for a rest");
		note = stream.next(Event.default);
		this.assertEquals(note[\viz], 0, "the key's own value is 0");
		this.assertEquals(calls, 1, "the function is called once for a note");
		this.assertEquals(log.size, 0, "nothing is sent before the server latency has passed");
		this.wait({ log.size == 1 }, "the note message arrives", 2);
		this.assertEquals(log[0], ['/n', [440, 0.1]], "path and the function's args");
		RCVisuals.enabled = false;
		stream.next(Event.default);
		this.assertEquals(calls, 1, "disabled: the function is not even called");
		b.free(post: false);
	}

	test_notePfunc_is_guarded {
		var layer = song.layer(\core);
		var b = RCBeat(layer, \tg, [type: \note, dur_flex: 1, viz: RCVisuals.notePfunc('/n', { nil.explode })]);
		var ev = b.asStream.next(Event.default);
		this.assertEquals(ev[\viz], 0, "the event is built despite the failing function");
		this.assert(this.logHas("explode"), "the error is reported");
		this.assertEquals(log.size, 0, "nothing is sent");
		b.free(post: false);
	}

	test_relay {
		var here = NetAddr.localAddr;
		var fake = (addr: here);   // any object answering .addr
		var f = RCVisuals.relay(\t, '/t/reply', '/t/out', fake);
		this.assert(f.isKindOf(OSCFunc) and: { f.permanent }, "a permanent OSCFunc");
		this.assertEquals(RCVisuals.relayKeys, [\t], "registered under its key");
		here.sendMsg('/t/reply', 1000, 5, 0.5, 0.25);
		this.wait({ log.size == 1 }, "the reply is forwarded", 2);
		this.assertEquals(log[0], ['/t/out', [5, 0.5, 0.25]], "replyID and values, the node id dropped");
		RCVisuals.relay(\t, '/t/reply', '/t/out2', fake, { |msg| msg[3..] });
		here.sendMsg('/t/reply', 1000, 5, 0.5, 0.25);
		this.wait({ log.size == 2 }, "the redefined relay forwards", 2);
		this.assertEquals(log[1], ['/t/out2', [0.5, 0.25]], "argFunc shapes the args; one relay per key");
		RCVisuals.relay(\skip, '/t/reply', '/t/out3', fake, { nil });
		here.sendMsg('/t/reply', 1000, 5, 0.5, 0.25);
		this.wait({ log.size == 3 }, "the keyed relay still forwards", 2);
		0.2.wait;
		this.assertEquals(log.size, 3, "a nil argFunc result sends nothing");
		this.assert(RCVisuals.unrelay(\skip), "unrelay frees a known key");
		this.assertEquals(RCVisuals.unrelay(\skip), false, "...once");
		RCVisuals.freeRelays;
		this.assertEquals(RCVisuals.relayKeys, [], "freeRelays empties the table");
	}
}
