// (Nov 6 2026) A pass recorded over a take (REScoreRecorder.overdub) must survive what the take replays and what
// the hand does around it. Found on FrissonFixed's Cue w1 passes: a `~rec.stop` typed at the end of a pass is
// recorded as a code line of that pass; a later pass's base replayed it, and the recorder's stop then stopped the
// player from inside the player's own routine (Routine:stop on thisThread is `nil.alwaysYield`): frozen mid-call,
// inside server.bind, the take lost, the server's address left a BundleNetAddr keeping every later message,
// RETap left silent. Also: a second overdub while a start is pending, a stop before the start, a last path that
// outlived its take, a player whose stop raises; Cmd-period, the server's quit, the base's end.
//
// The song's server is a collecting one (no scsynth) counted as running, so the player bundles as it would live.
TestREScorePassSafety : UnitTest {
	var clock, server, addr, song, rec, savedRateLimit, savedMainThreadOnly, dir;

	setUp {
		clock = RCTestSupport.clock;
		RCTestSupport.reset;
		addr = RECollectAddr("127.0.0.1", 57993);
		server = Server(\re_pass_safety_test, addr);
		server.statusWatcher.serverRunning = true;
		server.statusWatcher.notified = true;
		RCSession.boot(server, clock, oscPort: nil, initMidi: false);
		song = RCSong(\pass, 1);
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
		savedMainThreadOnly = RETap.mainThreadOnly;
		RETap.mainThreadOnly = false;
		dir = PathName.tmp +/+ "re_pass_safety_" ++ UniqueID.next;
		File.mkdir(dir);
		rec = song.scoreRecorder;
		rec.root = dir;
		Library.put(\rePassSafetyRec, rec);
	}

	tearDown {
		rec.player !? (_.stop);
		rec.disarm;
		if(server.addr !== addr) { server.addr = addr };   // a frozen bundle left in front of the collector
		REBundleWatch.unwatch(server);
		Library.put(\rePassSafetyRec, nil);
		RETap.mainThreadOnly = savedMainThreadOnly;
		RCTestSupport.reset;
		RCLog.rateLimit = savedRateLimit;
		(dir +/+ "*").pathMatch.do { |f| File.delete(f) };
		File.delete(dir);
		server.remove;
	}

	logHas { |text| ^RCLog.history.any { |e| e[2].asString.contains(text) } }

	// beats on the shared clock, as seconds of the test's routine
	beats { |b| (b / clock.tempo).wait }

	// waits until cond holds, at most secs; whether it held
	waitFor { |cond, secs = 3|
		var t = 0;
		while { cond.value.not and: { t < secs } } { 0.05.wait; t = t + 0.05 };
		^cond.value
	}

	prPlayerState { |p| ^"(state %, position %, fired %, skipped %, clock tempo %)".format(p.state, p.position, p.fired, p.skipped, clock.tempo) }

	// A first pass of `len` beats. typedStop: its stop typed as the IDE types it, the line opening a code frame
	// while the take records (so the take holds it, as live), then running. Its take's path.
	prFirstPass { |typedStop = false, len = 4|
		rec.version = "first";
		rec.arm(inputs: #[\code, \midi]);
		rec.record(snapshotAtStart: false);
		this.beats(len);
		if(typedStop) {
			RETap.beginCode("Library.at('rePassSafetyRec').stop;");
			rec.stop;
			RETap.endCode(true);
		} { rec.stop };
		^rec.lastPath
	}

	prStopLine { |score| ^score.events.detect { |e| e[\kind] == \code and: { e[\text].asString.contains(".stop") } } }

	// The case of Nov 6, end to end: the base holds the stop line typed at the end of the pass before.
	test_a_typed_stop_line_leaves_a_later_pass_alone {
		var path = this.prFirstPass(true), line, player, out;
		line = this.prStopLine(REScore.read(path));
		this.assert(line.notNil, "the typed stop line is in its take, as live");
		this.assertEquals(line !? (_[\replay]), false, "the line that stopped the take is marked never to replay");
		this.assertEquals(line !? (_[\recorder]), "stop", "marked as the recorder's line, read back");
		this.assert(this.logHas("not replayed").not, "the take's check does not count it as a line of the music");
		rec.version = "second";
		rec.overdub(path, mode: \keep, merge: false);
		player = rec.player;
		this.beats(8);   // past the stop line (beat 4) and the base's end
		this.assert(rec.isRecording, "the new pass still records");
		this.assert(this.waitFor { player.isPlaying.not }, "the base player ended, not frozen " ++ this.prPlayerState(player));
		this.assertEquals(player.skipped, 0, "the recorder's line is not counted as a skipped event (a render's check)");
		this.assert(server.addr.hasBundle.not, "the server's address is not left inside a bundle");
		server.sendMsg("/rePassSafetyProbe");
		this.assert(addr.bundles.any { |b| b.drop(1).any { |m| m.asArray.first.asString == "/rePassSafetyProbe" } },
			"a message sent after the replay reaches the server");
		RETap.input(\midi, song, (name: \rePassSafetyKnob, raw: 64, value: 0.5), {});
		out = rec.stop;
		this.assert(rec.lastPath.notNil and: { rec.lastPath != path } and: { File.exists(rec.lastPath ? "") },
			"the new pass written: " ++ rec.lastPath);
		this.assert(out.notNil and: { out.events.any { |e| e[\kind] == \midi } },
			"an input after the replay is still recorded (the tap not left silent)");
	}

	// The takes recorded before the fix hold their stop lines unmarked: replayed, they are refused.
	test_an_old_take_s_stop_line_is_refused {
		var path = this.prFirstPass(true), old, oldPath, player;
		old = REScore.read(path);
		this.prStopLine(old).removeAt(\replay);
		this.prStopLine(old).removeAt(\recorder);
		oldPath = old.write(dir +/+ "old take.json");
		rec.version = "second";
		rec.overdub(oldPath, mode: \keep, merge: false);
		player = rec.player;
		this.beats(8);
		this.assert(rec.isRecording, "the replayed stop refused: the pass still records");
		this.assert(this.logHas("asked by a replayed line"), "the refusal posted");
		this.assert(player.isPlaying.not, "the base player ended, not frozen");
		this.assert(server.addr.hasBundle.not, "no bundle left open");
		rec.stop;
		this.assert(rec.lastPath.notNil and: { rec.lastPath != oldPath }, "the pass written");
	}

	// A second overdub while the first one's start is pending (before its bar): refused, one base player.
	test_a_second_overdub_while_the_start_is_pending {
		var path = this.prFirstPass, p1, r;
		rec.version = "second";
		rec.overdub(path, mode: \keep, merge: false, quant: 64);
		p1 = rec.player;
		this.assert(rec.isPending, "the start waits for its bar");
		r = rec.overdub(path, mode: \keep, merge: false, quant: 64);
		this.assert(r.isNil, "the second overdub refused: nil, not the recorder");
		this.assert(this.logHas("already waits for its start"), "the refusal posted");
		this.assert(rec.player === p1, "one base player");
		rec.stop;
		this.assert(p1.isPlaying.not, "the stop before the start stops the base player");
	}

	// A stop before the start (before the pass's first bar) cancels it: nothing records later.
	test_a_stop_before_the_start_cancels_it {
		var path = this.prFirstPass, player;
		rec.version = "second";
		rec.overdub(path, mode: \keep, merge: false, quant: 64);
		player = rec.player;
		this.assert(rec.stop.isNil, "nothing returned: nothing was recorded");
		this.beats(70);
		this.assert(rec.isRecording.not and: { rec.isPending.not }, "the cancelled start never fires");
		this.assert(player.isPlaying.not, "its base player stopped");
		this.assert(this.logHas("the start cancelled"), "the cancel is posted");
		this.assertEquals(rec.lastPath, nil, "no file named for it");
	}

	// A second record while one records is refused, nil.
	test_a_record_while_recording_is_refused {
		rec.arm(inputs: #[\code]);
		rec.record(snapshotAtStart: false);
		this.assert(rec.record(snapshotAtStart: false).isNil, "refused: nil");
		this.assert(this.logHas("already recording"), "the refusal posted");
		rec.stop;
	}

	// The last path names the last take written, never an older one while a new take records.
	test_the_last_path_belongs_to_its_take {
		var first = this.prFirstPass;
		rec.version = "second";
		rec.record(snapshotAtStart: false);
		this.assert(rec.lastPath.isNil, "no last path while a new take records (it named " ++ first ++ ")");
		this.beats(2);
		rec.stop;
		this.assert(rec.lastPath.notNil and: { rec.lastPath != first }, "the new take's path after its stop");
	}

	// A take is written even when something stop does after it raises (here the base player's onStop).
	test_stop_writes_the_take_even_when_the_player_raises {
		var path = this.prFirstPass;
		rec.version = "second";
		rec.overdub(path, mode: \keep, merge: false);
		this.beats(2);
		rec.player.onStop = { Error("rePassSafety: the player's stop raised").throw };
		try { rec.stop } { };
		this.assert(rec.lastPath.notNil and: { rec.lastPath != path } and: { File.exists(rec.lastPath ? "") },
			"the pass written although the player's stop raised: " ++ rec.lastPath);
		this.assert(rec.isRecording.not, "the take stopped");
	}

	// The base's end, said loudly while the pass records.
	test_the_base_s_end_is_said {
		var path = this.prFirstPass(len: 2);
		rec.version = "second";
		rec.overdub(path, mode: \keep, merge: false);
		this.beats(6);
		this.assert(rec.isRecording, "the pass records on");
		this.assert(this.logHas("THE BASE HAS ENDED"), "the end posted");
		rec.stop;
	}

	// Cmd-period during a pass: the take stopped and written, marked interrupted; the player says it stopped.
	test_cmd_period_during_a_pass {
		var path = this.prFirstPass, player, written;
		rec.version = "second";
		rec.overdub(path, mode: \keep, merge: false);
		player = rec.player;
		this.beats(1);
		player.doOnCmdPeriod;
		this.assert(player.isPlaying.not, "the player stopped");
		rec.doOnCmdPeriod;
		this.assert(rec.isRecording.not, "the take stopped");
		written = rec.lastPath !? { |p| REScore.read(p) };
		this.assert(written.notNil and: { rec.lastPath != path }, "the take written: " ++ rec.lastPath);
		this.assertEquals(written !? { |w| w.meta[\interrupted] }, "cmdPeriod", "marked interrupted, read back");
		this.assert(this.logHas("CMD-PERIOD DURING THE TAKE"), "said loudly");
	}

	// Cmd-period before the start: the start cancelled.
	test_cmd_period_before_the_start {
		var path = this.prFirstPass;
		rec.version = "second";
		rec.overdub(path, mode: \keep, merge: false, quant: 64);
		rec.doOnCmdPeriod;
		this.assert(rec.isPending.not and: { rec.isRecording.not }, "the start cancelled");
		this.assert(this.logHas("Cmd-period before the take's start"), "said loudly");
		this.beats(70);
		this.assert(rec.isRecording.not, "it never fires");
	}

	// The server's quit during a take: said loudly, the beat noted, the take recording on.
	test_the_server_s_quit_during_a_take {
		var written;
		rec.version = "first";
		rec.arm(inputs: #[\code]);
		rec.record(snapshotAtStart: false);
		this.beats(1);
		rec.doOnServerQuit(server);
		this.assert(rec.isRecording, "the take records on");
		this.assert(this.logHas("THE SERVER STOPPED DURING THE TAKE"), "said loudly");
		rec.stop;
		written = REScore.read(rec.lastPath);
		this.assert(written.meta[\serverQuit].notNil, "the beat noted, read back");
	}

	// A bundle left open: seen once, then recovered at the next look (the status watcher's \bundling notices).
	test_a_bundle_left_open_is_recovered {
		server.openBundle;
		server.sendMsg("/rePassSafetyHeld");
		this.assert(REBundleWatch.check(server).not, "the first look only notes it");
		this.assert(REBundleWatch.check(server), "the same bundle at the next look: recovered");
		this.assert(server.addr === addr, "the server's own address back");
		this.assert(this.logHas("inside one bundle"), "said loudly");
		server.sendMsg("/rePassSafetyAfter");
		this.assert(addr.bundles.any { |b| b.drop(1).any { |m| m.asArray.first.asString == "/rePassSafetyAfter" } },
			"messages reach the server again");
		this.assert(addr.bundles.any { |b| b.drop(1).any { |m| m.asArray.first.asString == "/rePassSafetyHeld" } }.not,
			"the held one dropped, not sent late");
	}
}
