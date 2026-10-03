// REOfflineClock: items run in order at their beats when the clock is advanced, a Routine's
// wait reschedules it, the main thread's time is the clock's while an item runs, a tempo
// change keeps the seconds continuous; RECollectAddr keeps the messages with the clock's time.
TestREOfflineClock : UnitTest {
	test_scheduling_and_waits {
		var c = REOfflineClock(tempo: 2);
		var seen = List.new;
		c.schedAbs(1, { seen.add([\a, c.beats, thisThread.beats, thisThread.clock === c]); nil });
		c.sched(0.5, { seen.add([\b, c.beats]); nil });
		c.advanceTo(0.25);
		c.play(Routine { 3.do { |i| seen.add([\r, i, thisThread.beats]); 2.wait } }, 1);   // on the next beat: 1, then 3, 5
		c.schedAbs(2, { seen.add([\c, c.beats]); 1 });                                     // again every beat: 2, 3, 4...
		this.assertEquals(c.nextDue, 0.5);
		this.assertEquals(seen.size, 0, "nothing due before 0.5");
		c.advanceTo(4);
		this.assertEquals(seen.collect { |x| [if(x[0] == \r) { x[2] } { x[1] }, x[0]] }.sort { |p, q| if(p[0] == q[0]) { p[1] <= q[1] } { p[0] <= q[0] } }.collect(_[1]),
			[\b, \a, \r, \c, \c, \r, \c], "every item at its beat: b at 0.5, a and r at 1, c at 2, c and r at 3, c at 4");
		this.assertEquals(seen.detect { |x| x[0] == \a }[1], 1, "the clock stands at the item's beat");
		this.assertEquals(seen.detect { |x| x[0] == \a }[2], 1, "and so does the thread");
		this.assertEquals(seen.detect { |x| x[0] == \a }[3], true, "on this clock");
		this.assertEquals(seen.select { |x| x[0] == \r }.collect(_[2]), [1, 3], "the Routine's waits reschedule it on this clock");
		this.assertEquals(c.beats, 4, "the clock stands at the target");
		this.assertEquals(c.seconds, 2, "seconds follow the tempo");
		this.assert(thisThread.clock !== c, "the thread's clock is restored");
		c.advanceTo(6);
		this.assertEquals(seen.select { |x| x[0] == \r }.size, 3, "the Routine ended");
		this.assertEquals(seen.select { |x| x[0] == \c }.size, 5, "the repeating item ran at 2, 3, 4, 5, 6");
	}

	test_tempo_and_grid {
		var c = REOfflineClock(tempo: 1);
		c.advanceTo(3);
		c.tempo = 2;
		this.assertEquals(c.seconds, 3, "the seconds do not jump at a tempo change");
		c.advanceTo(5);
		this.assertEquals(c.seconds, 4, "then run at the new tempo");
		this.assertEquals(c.secs2beats(4.5), 6);
		this.assertEquals(c.beats2secs(7), 5);
		this.assertEquals(c.nextTimeOnGrid(4), 8);
		this.assertEquals(c.nextTimeOnGrid(4, 1), 5, "on the grid now: now (as a TempoClock)");
		this.assertEquals(c.timeToNextBeat(1), 0);
		c.advanceTo(5.5);
		this.assertEquals(c.nextTimeOnGrid(4, 1), 9);
		this.assertEquals(c.timeToNextBeat(1), 0.5);
		this.assertEquals(c.beatDur, 0.5);
	}

	test_collect_addr {
		var c = REOfflineClock(tempo: 2);
		var a = RECollectAddr("127.0.0.1", 57999, c);
		var s;
		a.sendMsg('/s_new', \default, 1000, 0, 1);
		c.advanceTo(2);
		a.sendBundle(0.2, ['/n_set', 1000, \freq, 660], ['/n_set', 1000, \amp, 0.2]);
		a.sendBundle(nil, ['/n_free', 1000]);
		this.assertEquals(a.bundles.size, 3);
		this.assertEquals(a.bundles[0][0], 0, "sent before any step: at 0");
		this.assertEquals(a.bundles[1][0], 1.2, "the clock's seconds plus the bundle's latency");
		this.assertEquals(a.bundles[1].size, 3, "both messages kept together");
		this.assertEquals(a.bundles[2][0], 1);
		s = a.score([SynthDescLib.global[\default].def]);
		this.assertEquals(s.score.size, 5, "Score's own default group, the def, then the bundles by time");
		this.assertEquals(s.score[0][1][0], "/g_new");
		this.assertEquals(s.score[1][0], 0.0);
		this.assertEquals(s.score[1][1][0], '/d_recv');
		this.assertEquals(s.score.collect(_[0]), [0.0, 0.0, 0, 1, 1.2], "sorted");
	}

	// A NodeProxy source set by an item of the clock (a take's code line) is collected at once, at
	// that moment: the definition first, the proxy's synth a server latency later (OSCBundleExt).
	test_proxy_source_set_while_replaying {
		var c = REOfflineClock(tempo: 2);
		var a = RECollectAddr("127.0.0.1", 57997, c);
		var s = Server(\re_offline_proxy_test, a);
		var p, before, defs, synths;
		var isCmd = { |m, num, name| m[0] == num or: { m[0].asString == name } };
		var collected = { |from, num, name| a.bundles.copyRange(from, a.bundles.size - 1).select { |b| b[1..].any { |m| isCmd.(m, num, name) } } };
		s.statusWatcher.serverRunning = true;   // as take_render.scd: counts as running, nothing answers
		s.statusWatcher.notified = true;
		p = NodeProxy.audio(s, 2);
		c.schedAbs(4, { p.source = { SinOsc.ar(440, 0, 0.1) ! 2 }; nil });
		c.advanceTo(3);
		before = a.bundles.size;
		c.advanceTo(6);
		defs = collected.(before, 5, "/d_recv");
		synths = collected.(before, 9, "/s_new");
		this.assertEquals(defs.size, 1, "the source's definition is collected during the replay");
		this.assertEquals(defs[0] !? (_[0]), 2.0, "at the item's seconds (beat 4 at tempo 2)");
		this.assertEquals(synths.size, 1, "and the proxy's synth");
		this.assertEquals(synths[0] !? (_[0]), 2.0 + s.latency, "a server latency later");
		// as REScorePlayer fires a take's code line: inside server.bind (a BundleNetAddr around the collector)
		c.schedAbs(8, { s.bind { p.source = { Saw.ar(220, 0.1) ! 2 } }; nil });
		before = a.bundles.size;
		c.advanceTo(10);
		defs = collected.(before, 5, "/d_recv");
		synths = collected.(before, 9, "/s_new");
		this.assertEquals(defs.collect(_[0]), [4.0], "inside a bind: the definition at the item's seconds");
		this.assertEquals(synths.collect(_[0]), [4.0 + s.latency], "the synth in the bind's bundle, a latency later");
		s.remove;
	}
}
