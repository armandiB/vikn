// REReplay: the cue buffer's messages, on a collecting server (no scsynth): a replay freed and another
// prepared on the number it gave back must reach the server free first, then the new cue.
TestREReplay : UnitTest {
	var server, addr, savedRateLimit;

	setUp {
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		addr = RECollectAddr("127.0.0.1", 57998, REOfflineClock(tempo: 1));
		server = Server(\re_replay_test, addr);
		server.statusWatcher.serverRunning = true;
		server.statusWatcher.notified = true;
	}

	tearDown {
		server.remove;
		RCLog.rateLimit = savedRateLimit;
	}

	// every message the server received, completion messages left inside theirs
	prMessages { ^addr.bundles.asArray.collect { |b| b.drop(1) }.flatten(1) }

	// a command's name whether sent as "/b_alloc" or \b_free
	prCmd { |m| ^m[0].asString.reject { |c| c == $/ }.asSymbol }

	test_freed_then_cued_again_on_the_same_number {
		var bus = Bus(\audio, 0, 2, server), r1, r2, n, msgs, freeAt, allocAt;
		r1 = REReplay(server, "/nonexistent/take.wav", 2).into(bus);
		r1.prepare(0);
		0.2.wait;   // the prepare's routine (its sync answers at once here)
		n = r1.buffer.bufnum;
		r1.free;
		r2 = REReplay(server, "/nonexistent/take.wav", 2).into(bus);
		r2.prepare(0);
		0.2.wait;
		this.assertEquals(r2.buffer.bufnum, n, "the new cue on the number the first gave back (the case that broke)");
		msgs = this.prMessages;
		this.assert(msgs.select { |m| this.prCmd(m) == \b_close }.every { |m| m[2].isNil },
			"no free sent as the close's completion (it ran after what the server had queued meanwhile)");
		freeAt = msgs.detectIndex { |m| this.prCmd(m) == \b_free and: { m[1] == n } };
		allocAt = msgs.detectIndex { |m, i| this.prCmd(m) == \b_alloc and: { m[1] == n } and: { i > (freeAt ? -1) } };
		this.assert(freeAt.notNil and: { allocAt.notNil } and: { freeAt < allocAt },
			"the old buffer freed before the new one is allocated on its number: % then %".format(freeAt, allocAt));
		r2.free;
	}
}
