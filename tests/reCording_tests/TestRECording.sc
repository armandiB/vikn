// Offline: prepare / start / session are refused (server not running) and
// touch nothing; paths, tracks, bus resolution and song slots are pure.
TestRECording : UnitTest {
	var song, savedRateLimit, dir;

	setUp {
		RCTestSupport.bootSession;
		song = RCSong(\re, 1);
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
		dir = PathName.tmp +/+ "re_test_" ++ UniqueID.next;
	}

	tearDown {
		RCTestSupport.reset;
		RCLog.rateLimit = savedRateLimit;
	}

	logHas { |text| ^RCLog.history.any { |e| e[2].contains(text) } }

	test_take_paths {
		this.assertEquals(RETake.path("/r", "sub", "AmbiX Out", "w1", "aiff", "260924_120000"), "/r/sub/260924_120000_AmbiX Out w1.aiff", "root/subfolder/stamp_name version.format");
		this.assertEquals(RETake.path("/r", "", "n", "", "wav", "s"), "/r/s_n.wav", "no subfolder, no version: no separators added");
		this.assertEquals(RETake.path("/r", "a/b", \n, 'v2', "aiff", "s"), "/r/a/b/s_n v2.aiff", "symbols accepted");
		this.assert(RETake.path("/r", "", "n").contains("_n.aiff"), "stamp defaults to now");
	}

	test_recorder_tracks_and_buses {
		var bus = Bus.audio(Server.default, 2);
		var proxy = NodeProxy.new(Server.default, \audio, 4);
		var r = RERecorder(Server.default, dir, "sub", "w1", "aiff", 16);
		this.assert(r.addTrack('AmbiX Out', proxy) === r, "addTrack is chainable");
		r.addTrack(\stereo, bus).addTrack(\raw, 7, 3);
		this.assertEquals(r.trackNames, ['AmbiX Out', \stereo, \raw], "tracks in order");
		this.assertEquals(r.trackChannels(r.track('AmbiX Out')), 4, "channels from a NodeProxy");
		this.assertEquals(r.trackChannels(r.track(\stereo)), 2, "channels from a Bus");
		this.assertEquals(r.trackChannels(r.track(\raw)), 3, "explicit channels");
		this.assertEquals(r.addTrack(\index, 9).trackChannels(r.track(\index)), 16, "an index takes the recorder's channels");
		this.assertEquals(r.prBusIndex(bus), bus.index, "Bus → index");
		this.assertEquals(r.prBusIndex(proxy), proxy.bus.index, "NodeProxy → its bus index");
		this.assertEquals(r.prBusIndex(7), 7, "Integer passes");
		this.assertEquals(r.pathFor(\stereo, "s"), dir +/+ "sub" +/+ "s_stereo w1.aiff", "pathFor");
		r.addTrack(\stereo, bus, 1);
		this.assertEquals(r.trackNames, ['AmbiX Out', \raw, \index, \stereo], "re-adding a name replaces its track");
		this.assert(r.isPrepared.not and: { r.isRecording.not }, "nothing prepared");
		this.assert(r.clock.isNil, "no clock until a song adopts it");
		r.prepare;
		this.assert(this.logHas("server not running") and: { r.isPrepared.not }, "prepare refused offline");
		this.assert(File.exists(dir).not, "no folder made");
		r.start([4, 0]);
		this.assert(r.isRecording.not, "start refused offline");
		r.stop;
		r.free;
		bus.free;
		proxy.clear;
	}

	test_replay_targets {
		var proxy = NodeProxy.new(Server.default, \audio, 4);
		var rep = REReplay(Server.default, "/nope/take.aiff", 4, ampDb: -6);
		this.assertEquals(rep.targetKind, nil, "no target yet");
		this.assert(rep.into(proxy) === rep, "into is chainable");
		this.assertEquals(rep.targetKind, \proxy, "NodeProxy target");
		this.assertEquals(rep.into(Bus.audio(Server.default, 4)).targetKind, \bus, "Bus target");
		this.assertEquals(rep.into(3, Group.basicNew(Server.default)).targetKind, \bus, "index target");
		rep.into("x");
		this.assert(this.logHas("neither a NodeProxy nor a Bus"), "bad target reported");
		rep.into(proxy);
		rep.prepare;
		this.assert(this.logHas("prepare: server not running") and: { rep.isPrepared.not }, "prepare refused offline");
		rep.start([4, 0]);
		this.assert(this.logHas("not prepared"), "start refused when not prepared");
		rep.set(\amp, 0.5);
		this.assertEquals(proxy.nodeMap.at(\amp), nil, "set before prepare touches nothing");
		rep.process = { |sig| sig * 2 };
		this.assert(rep.process.notNil, "process stored");
		rep.free;
		proxy.clear;
	}

	test_session_and_song_slots {
		var r = RERecorder(Server.default, dir).addTrack(\a, 0);
		var rep = REReplay(Server.default, "/nope.aiff", 2);
		var r2 = RERecorder(Server.default, dir).addTrack(\b, 1);
		this.assertEquals(RETake.session(r, rep, 1, 30), 100, "total = minutes, seconds and the margin");
		this.assert(this.logHas("session: server not running"), "refused offline");
		song.addRecorder(\take, r);
		this.assert(r.clock === song.clock, "the song's clock is adopted");
		this.assert(song.recorder(\take) === r, "recorder lookup");
		song.addRecorder(\take, r2);
		this.assert(song.recorder(\take) === r2 and: { song.recorders.size == 1 }, "replacing frees the previous one");
		song.addReplay(\take, rep);
		this.assert(rep.clock === song.clock and: { song.replay(\take) === rep }, "replay adopted");
		song.clearAll;
		this.assertEquals(song.recorders.size + song.replays.size, 0, "clearAll drops recorders and replays");
	}
}
