// REOfflineTap: a rig's SendReply tap on an offline server writes into a buffer instead: the
// /b_alloc and the /s_new in one bundle at the clock's time, the buffer sized from endSecs, the
// creation time read back from the collected bundles, the /b_write lines and the index at the end.
TestREOfflineTap : UnitTest {
	var clock, addr, server, savedEnd, savedMax, savedRateLimit;

	setUp {
		clock = REOfflineClock(tempo: 2);
		addr = RECollectAddr("127.0.0.1", 57996, clock);
		server = Server(\re_offline_tap_test, addr, ServerOptions.new.numOutputBusChannels_(2));
		server.statusWatcher.serverRunning = true;   // as take_render.scd: counts as running, nothing answers
		server.statusWatcher.notified = true;
		savedEnd = REOfflineTap.endSecs;
		savedMax = REOfflineTap.maxRate;
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
		REOfflineTap.clear(server);
	}

	tearDown {
		REOfflineTap.clear(server);
		REOfflineTap.endSecs = savedEnd;
		REOfflineTap.maxRate = savedMax;
		RCLog.rateLimit = savedRateLimit;
		server.remove;
	}

	isCmd { |m, num, name| ^m.isSequenceableCollection and: { m[0] == num or: { m[0].asString == name } } }

	test_offline_server_and_refusal {
		var live = Server(\re_offline_tap_live, NetAddr("127.0.0.1", 57995));
		this.assert(REOfflineTap.isOffline(server), "a server on an RECollectAddr is offline");
		this.assert(REOfflineTap.isOffline(live).not, "a server on a plain NetAddr is not");
		this.assert(try { REOfflineTap(live, \x, \k, '/k/state', [0], 20, 4); false } { |e| e.isKindOf(Error) }, "new refuses a live server");
		server.bind {
			this.assert(REOfflineTap.isOffline(server), "inside a bind the collector is found behind the BundleNetAddr");
		};
		live.remove;
	}

	test_def_builds {
		var def = SynthDef(\re_offline_tap_test_def, { |posBus = 0, levelBus = 0, rate = 20, bufnum = 0|
			REOfflineTap.record(bufnum, rate, In.kr(posBus, 96) ++ In.kr(levelBus, 32));
		});
		var wr = def.children.detect { |u| u.isKindOf(BufWr) };
		this.assert(wr.notNil, "the def holds a BufWr");
		this.assertEquals(wr.rate, \control);
		this.assertEquals(wr.inputs.size, 3 + 129, "bufnum, phase, loop, then the counter and 128 latched values");
		this.assertEquals(wr.inputs[2], 0, "no loop: the frames past the end go to the last one");
		this.assert(wr.inputs[3..].every { |u| u.rate == \control }, "every written channel at control rate");
	}

	test_messages_frames_and_start_time {
		var t, bundles, line, idx;
		REOfflineTap.endSecs = 10;
		clock.advanceTo(4);   // 2 s
		t = REOfflineTap(server, \re_offline_tap_test_def, \lsys, '/lsys/state', [32], 20, 128);
		this.assertEquals(t.frames, (8 * 20) + 2, "frames: the seconds left to endSecs at the rate, two spare");
		this.assertEquals(t.numChannels, 129);
		this.assertEquals(t.allocMsg, ['/b_alloc', t.bufnum, t.frames, 129], "four arguments: no nil in a Score line");
		this.assertEquals(t.fileName, "lsys_32_" ++ t.synth.nodeID ++ ".wav");
		this.assertEquals(REOfflineTap.taps(server), [t], "registered for the server");
		this.assert(t.startSecsIn(addr.bundles).isNil, "not sent yet");
		// as a rig sends it: a bundle through the server, at the clock's time plus the latency
		server.sendBundle(server.latency, *t.msgs(Group.basicNew(server, 1), [\posBus, 8, \levelBus, 104, \rate, 20]));
		bundles = addr.bundles;
		this.assertEquals(bundles.size, 1, "one bundle");
		this.assertEquals(bundles[0][0], 2 + server.latency);
		this.assert(this.isCmd(bundles[0][1], nil, "/b_alloc"), "the /b_alloc first");
		this.assert(this.isCmd(bundles[0][2], 9, "/s_new"), "then the /s_new");
		this.assertEquals(bundles[0][2][2], t.synth.nodeID);
		this.assertEquals(bundles[0][2][4], 1, "in the target group");
		this.assertEquals(bundles[0][2].copyRange(5, bundles[0][2].size - 1), [\posBus, 8, \levelBus, 104, \rate, 20, \bufnum, t.bufnum], "its arguments, the bufnum last");
		this.assertEquals(t.startSecsIn(bundles), 2 + server.latency, "the creation time read back by node id");
		// inside a bind (a take's code line replayed): the same bundle, through the BundleNetAddr
		clock.advanceTo(6);
		server.bind { server.sendBundle(nil, *REOfflineTap(server, \re_offline_tap_test_def, \lsys, '/lsys/state', [64], 20, 128).msgs(Group.basicNew(server, 1), [\rate, 20])) };
		this.assertEquals(addr.bundles.size, 2);
		this.assertEquals(addr.bundles[1][0], 3 + server.latency, "the bind's bundle, a latency after the item");
		this.assertEquals(REOfflineTap.taps(server).last.frames, (7 * 20) + 2, "a later tap has fewer frames");
		this.assertEquals(REOfflineTap.taps(server).last.startSecsIn(addr.bundles), 3 + server.latency);
	}

	test_rate_cap_and_default_end {
		var t;
		REOfflineTap.endSecs = nil;
		REOfflineTap.maxRate = 10;
		t = REOfflineTap(server, \re_offline_tap_test_def, \plankton, '/orgnsm/state', [0], 40, 32);
		this.assertEquals(t.rate, 10, "the rate capped");
		this.assertEquals(t.frames, (REOfflineTap.defaultSecs * 10) + 2, "the default length when endSecs is unset");
		this.assert(RCLog.history.any { |e| e[1] == \warn and: { e[2].contains("endSecs") } }, "and a warning");
	}

	test_write_lines_and_index {
		var t1, t2, w;
		REOfflineTap.endSecs = 20;
		clock.advanceTo(2);
		t1 = REOfflineTap(server, \re_offline_tap_test_def, \lsys, '/lsys/state', [0], 20, 128);
		server.sendBundle(0.2, *t1.msgs(Group.basicNew(server, 1), [\rate, 20]));
		clock.advanceTo(8);
		t2 = REOfflineTap(server, \re_offline_tap_test_def, \plankton, '/orgnsm/state', [8], 20, 32);   // never sent
		w = REOfflineTap.writeLines(server, addr.bundles, "/tmp/state", 30, 0.5);
		this.assertEquals(w[\format], "re-state");
		this.assertEquals(w[\lines].size, 1, "one line per tap sent");
		this.assertEquals(w[\lines][0][0], 30, "at the time given");
		this.assertEquals(w[\lines][0][1], ['/b_write', t1.bufnum, "/tmp/state" +/+ t1.fileName, "wav", "float", -1, 0, 0]);
		this.assertEquals(w[\index].size, 1);
		this.assertEquals(w[\index][0][\key], \lsys);
		this.assertEquals(w[\index][0][\path], "/lsys/state");
		this.assertEquals(w[\index][0][\head], [0]);
		this.assertEquals(w[\index][0][\file], t1.fileName);
		this.assertEquals(w[\index][0][\rate], 20);
		this.assertEquals(w[\index][0][\values], 128);
		this.assertFloatEquals(w[\index][0][\t0], 1 + 0.2 - 0.5, "t0: the /s_new's seconds minus the take's start", 1e-9);
		this.assertFloatEquals(w[\index][0][\createdAt], 1 - 0.5, "createdAt: the clock's seconds at new, minus the start", 1e-9);
		this.assertEquals(w[\index][0][\frames], t1.frames);
		this.assertEquals(w[\index][0][\nodeID], t1.synth.nodeID);
		this.assert(RCLog.history.any { |e| e[1] == \warn and: { e[2].contains(t2.fileName) } }, "the tap never sent is warned about");
		REOfflineTap.clear(server);
		this.assertEquals(REOfflineTap.writeLines(server, addr.bundles, "/tmp/state", 30)[\lines], [], "cleared: nothing to write");
	}
}
