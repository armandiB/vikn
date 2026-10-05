// REScoreEditor: the transport and the editing of a folder of takes for a page, driven over OSC
// on <prefix>/rec/... (the working copy next to the take, undo, play as edited, save new, revert,
// save in place), the feed through a function, free.
TestREScoreEditor : UnitTest {
	var clock, song, layer, rec, ed, root, addr, feeds, savedRateLimit, savedMainThreadOnly;

	setUp {
		clock = RCTestSupport.clock;
		RCTestSupport.bootSession;
		song = RCSong(\edtest, 1);
		layer = song.layer(\core);
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
		savedMainThreadOnly = RETap.mainThreadOnly;
		RETap.mainThreadOnly = false;
		root = PathName.tmp +/+ "re_editor_test_" ++ Date.getDate.stamp;
		File.mkdir(root);
		rec = song.scoreRecorder(root: root, version: "t");
		feeds = List.new;
		addr = NetAddr("127.0.0.1", NetAddr.langPort);
	}

	tearDown {
		ed !? (_.free);
		rec.disarm;
		RETap.mainThreadOnly = savedMainThreadOnly;
		RCTestSupport.reset;
		RCLog.rateLimit = savedRateLimit;
		(root +/+ "*").pathMatch.do { |p| File.delete(p) };
		File.delete(root);
	}

	// a take with three sets of the beat's amp
	prTake {
		var b = layer.addBeat(\k, [type: \rest, dur_flex: 1, amp: 0.1]);
		rec.arm(inputs: #[\actions]);
		rec.record(snapshotAtStart: false);
		3.do { |i| b.set(\amp, 0.2 + (i / 10)); 0.1.wait };
		^rec.stop
	}

	test_osc_editing_round_trip {
		var s = this.prTake, path = rec.lastPath, working, ev1, saved;
		ed = REScoreEditor(song, rec, root, "/edtest", "t", feed: { |kind, dict| feeds.add([kind, dict]) });
		working = REScoreEditor.workingPath(path);
		ev1 = s.events[0];
		this.assertEquals(ed.takes, [path], "the take listed, no companion");
		this.assertEquals(ed.resolveTake("last"), path, "last is the recorder's last take");
		this.assertEquals(ed.resolveTake(path.basename), path, "a take by its file name");
		this.assert(ed.resolveTake("nothing.json").isNil, "an unknown take resolves to nil");
		// an edit from the page: the working copy next to the take
		addr.sendMsg('/edtest/rec/edit', path, REJSON.stringify((op: "moved", id: ev1[\id], beat: ev1[\beat] + 0.5)));
		0.2.wait;
		this.assert(ed.editing.notNil and: { File.exists(working) }, "the working copy written");
		this.assertFloatEquals(REScore.read(working).at(ev1[\id])[\beat], ev1[\beat] + 0.5, "it holds the move", 0.001);
		this.assertEquals(feeds.select { |f| f[0] == \edit }.size, 1, "an edit feed");
		this.assertEquals(feeds.last[1][\ops], 1);
		this.assertEquals(feeds.last[1][\working], working.basename);
		addr.sendMsg('/edtest/rec/edit', path, REJSON.stringify((op: "removed", ids: [ev1[\id]])));
		0.2.wait;
		this.assert(REScore.read(working).at(ev1[\id]).isNil and: { ed.editing[\ops].size == 2 }, "a second operation");
		addr.sendMsg('/edtest/rec/undo', path);
		0.2.wait;
		this.assert(REScore.read(working).at(ev1[\id]).notNil and: { ed.editing[\ops].size == 1 }, "undo: the last operation taken back");
		// Play plays the edited take, the hook sees the player, stopPlay stops it
		addr.sendMsg('/edtest/rec/play', path.basename);
		0.1.wait;   // the take lasts 0.3 s at the test clock's tempo
		this.assert(ed.player.notNil and: { ed.player.isPlaying }, "Play replays");
		this.assertFloatEquals(ed.player.score.at(ev1[\id])[\beat], ev1[\beat] + 0.5, "as edited", 0.001);
		this.assertEquals(ed.playingPath, path);
		this.assertEquals(ed.state[\playing], path, "the state names the take playing");
		this.assertEquals(ed.state[\editing][\ops], 1, "and the operations");
		addr.sendMsg('/edtest/rec/stopPlay');
		0.2.wait;
		this.assert(ed.player.isPlaying.not, "stopPlay stops it");
		// save in new file: the edit in a new take, the original as it was, the copy gone
		addr.sendMsg('/edtest/rec/save', path, "new");
		0.3.wait;
		saved = ed.takes.reject(_ == path);
		this.assertEquals(saved.size, 1, "a new take");
		this.assert(saved[0].basename.contains(" t edit.json"), "named after the version: " ++ saved[0].basename);
		this.assertFloatEquals(REScore.read(saved[0]).at(ev1[\id])[\beat], ev1[\beat] + 0.5, "it holds the edit", 0.001);
		this.assertFloatEquals(REScore.read(path).at(ev1[\id])[\beat], ev1[\beat], "the original as it was", 0.001);
		this.assert(ed.editing.isNil and: { File.exists(working).not }, "the copy gone");
		this.assertEquals(feeds.last[1][\saved], saved[0].basename, "the feed names the file saved");
		// revert drops the copy
		addr.sendMsg('/edtest/rec/edit', path, REJSON.stringify((op: "changed", id: ev1[\id], value: 0.123)));
		0.2.wait;
		addr.sendMsg('/edtest/rec/revert', path);
		0.2.wait;
		this.assert(ed.editing.isNil and: { File.exists(working).not }, "revert: the copy dropped");
		// save in place
		addr.sendMsg('/edtest/rec/edit', path, REJSON.stringify((op: "changed", id: ev1[\id], value: 0.123)));
		0.2.wait;
		addr.sendMsg('/edtest/rec/save', path);
		0.3.wait;
		this.assert(ed.editing.isNil and: { File.exists(working).not }, "save in place: the copy gone");
		this.assertFloatEquals(REScore.controlValue(REScore.read(path).at(ev1[\id])), 0.123, "the take itself holds the edit", 0.001);
		// free: the defs gone, a message changes nothing
		ed.free;
		addr.sendMsg('/edtest/rec/edit', path, REJSON.stringify((op: "changed", id: ev1[\id], value: 0.5)));
		0.2.wait;
		this.assert(ed.editing.isNil and: { File.exists(working).not }, "after free nothing listens");
		ed = nil;
	}

	// a take with a render next to it: its files are not takes, Play plays the render when its WAV
	// exists (playRendered \auto), always with true, never with false
	test_rendered_take {
		var s = this.prTake, path = rec.lastPath, sidecar = REScore.renderPath(path), wav = path.drop(-5) ++ ".wav";
		REJSON.write((format: "re-render", version: 1, take: path.basename, wav: wav.basename, channels: 16, hoa: "acn-n3d", latency: 0.2, duration: 1, tempo: 20), sidecar, 2, 2);
		File.use(REScore.tapePath(path), "w", { |f| f.write("[]") });
		ed = REScoreEditor(song, rec, root, "/edtest", "t", feed: { |kind, dict| feeds.add([kind, dict]) });
		this.assertEquals(ed.takes, [path], "the sidecar and the tape are not listed");
		ed.play(path);
		this.assert(ed.player.isKindOf(REScorePlayer) and: { ed.player.isKindOf(RERenderedPlayer).not }, "no WAV: the program plays the take");
		this.assert(ed.isPlayingRender.not);
		ed.stopPlay;
		File.use(wav, "w", { |f| f.write("") });
		ed.play(path);
		this.assert(ed.player.isKindOf(RERenderedPlayer), "the WAV there: the render plays (refused here without a server)");
		this.assert(ed.isPlayingRender);
		this.assertEquals(ed.state[\rendered], true, "the state says so");
		ed.stopPlay;
		ed.playRendered = false;
		ed.play(path);
		this.assert(ed.player.isKindOf(RERenderedPlayer).not, "playRendered false: the program");
		ed.stopPlay;
		ed.playRendered = true;
		File.delete(wav);
		ed.play(path);
		this.assert(ed.player.isKindOf(RERenderedPlayer), "playRendered true: the render even without its WAV");
		ed.stopPlay;
		ed.free;
		ed = nil;
	}

	test_own_feed_and_default_prefix {
		var s = this.prTake, path = rec.lastPath, got = List.new, def, own = 0;
		rec.onEvent = { own = own + 1 };
		// no feed function: the state goes to /rigfeed/state on feedPort, events are chained on the recorder
		def = OSCFunc({ |msg| got.add([msg[0], REJSON.parse(msg[1].asString)]) }, '/rigfeed/state', recvPort: NetAddr.langPort);
		ed = REScoreEditor(song, rec, root, feedPort: NetAddr.langPort);
		this.assertEquals(ed.prefix, "/edtest", "the prefix is the song's name");
		0.6.wait;
		this.assert(got.size >= 2, "the state fed on its own (" ++ got.size ++ ")");
		this.assertEquals(got.last[1][\recorder], "armed");
		this.assertEquals(got.last[1][\takes], [path]);
		this.assertEquals(got.last[1][\lastTake], path);
		this.assert(rec.onEvent.notNil and: { rec.onEvent.isKindOf(Function) }, "the recorder's onEvent chained");
		ed.free;
		def.free;
		rec.onEvent.value(rec, (id: 1, beat: 0, kind: \action));
		this.assertEquals(own, 1, "free put the recorder's own onEvent back");
		ed = nil;
	}
}
