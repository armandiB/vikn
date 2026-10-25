// RERenderedPlayer: a take with a render next to it (the sidecar), nothing fired but the tempo
// events, the route of the WAV into the chain, the refusals and warnings without a server.
TestRERenderedPlayer : UnitTest {
	var clock, song, root, path, sidecar, wav, savedRateLimit, savedMainThreadOnly, savedTempo, savedSink, sent;

	setUp {
		clock = RCTestSupport.clock;
		RCTestSupport.bootSession;
		song = RCSong(\rendered, 1);
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
		savedMainThreadOnly = RETap.mainThreadOnly;
		RETap.mainThreadOnly = false;
		savedTempo = clock.tempo;
		savedSink = RCVisuals.sink;   // the fx cue goes through RCVisuals: into a list, never onto the network
		sent = List.new;
		RCVisuals.sink = { |p, a| sent.add([p.asString, a]) };
		root = PathName.tmp +/+ "re_rendered_test_" ++ UniqueID.next;
		File.mkdir(root);
		path = root +/+ "260101_120000_rendered w0.json";
		sidecar = REScore.renderPath(path);
		File.mkdir(REScore.renderDir(path));   // the render's folder, <take>/ next to the take
		wav = REScore.renderDir(path) +/+ "260101_120000_rendered w0.wav";
		REVisualsTape.clearCache;
	}

	tearDown {
		RCVisuals.sink = savedSink;
		clock.tempo = savedTempo;
		RETap.mainThreadOnly = savedMainThreadOnly;
		RCTestSupport.reset;
		RCLog.rateLimit = savedRateLimit;
		(root +/+ "*/*").pathMatch.do { |p| File.delete(p) };   // the render's folder first
		(root +/+ "*").pathMatch.do { |p| File.delete(p) };
		File.delete(root);
	}

	logHas { |text| ^RCLog.history.any { |e| e[2].contains(text) } }

	// a take of three code lines and a tempo event, tempo 20 (the test clock's)
	prTake {
		var s = REScore(\rendered);
		s.meta[\tempo] = 20;
		s.meta[\latency] = 0.2;
		s.meta[\beat0] = 0;
		s.add((id: 1, beat: 0, secs: 0, kind: \code, level: 1, voice: \code, text: "~re_rendered_flag = 1;"));
		s.add((id: 2, beat: 1, secs: 0.05, kind: \tempo, level: 1, tempo: 20));
		s.add((id: 3, beat: 2, secs: 0.1, kind: \code, level: 1, voice: \code, text: "~re_rendered_flag = 2;"));
		s.write(path);
		^s
	}

	prSidecar { |hoa = "acn-n3d", channels = 16, extra|
		var d = (format: "re-render", version: 1, take: path.basename, wav: wav.dirname.basename +/+ wav.basename, channels: channels, hoa: hoa,
			decode: if(hoa == "acn-sn3d") { "ambix" } { "raw" }, outBus: 0, latency: 0.2, sampleRate: 48000, tempo: 20,
			startBeat: 0, startSecs: 0, duration: 3.15, tail: 3, level: 2, tape: "260101_120000_rendered w0/260101_120000_rendered w0.viz.json");
		extra !? { |e| e.keysValuesDo { |k, v| d[k] = v } };
		REJSON.write(d, sidecar, 2, 2);
		^d
	}

	test_render_paths_and_sidecar {
		var s = this.prTake, d;
		this.assertEquals(REScore.renderPath(path), path.drop(-5) ++ ".render.json");
		this.assertEquals(REScore.renderDir(path), path.drop(-5), "the render's folder named after the take");
		this.assertEquals(REScore.tapePath(path), path.drop(-5) +/+ "260101_120000_rendered w0.viz.json", "the tape in it");
		this.assertEquals(REScore.statePath(path), path.drop(-5) +/+ "260101_120000_rendered w0.state");
		this.assert(REScore.isCompanion(sidecar) and: { REScore.isCompanion(REScore.tapePath(path)) } and: { REScore.isCompanion(REScore.companionPath(path, 2)) }, "the render's files are companions");
		this.assert(REScore.isCompanion(path).not, "a take is not");
		this.assert(REScore.readRender(path).isNil, "no sidecar: nil");
		this.assert(RERenderedPlayer.fromTake(path, song).isNil and: { this.logHas("no render next to") }, "fromTake without a render: nil, said");
		this.prSidecar;
		d = REScore.readRender(path);
		this.assertEquals(d[\format], "re-render");
		this.assertEquals(d[\wav], wav, "a relative wav made absolute against the take's folder");
		this.assertEquals(d[\tape], REScore.tapePath(path));
		this.assertEquals(d[\latency], 0.2);
		this.assert(REScore.hasRender(path).not, "no WAV yet");
		File.use(wav, "w", { |f| f.write("") });
		this.assert(REScore.hasRender(path), "the WAV exists");
		File.use(sidecar, "w", { |f| f.write("{\"format\": \"other\"}") });
		this.assert(REScore.readRender(path).isNil and: { this.logHas("not a render sidecar") }, "another format is ignored, said");
	}

	test_from_take_fires_nothing_but_tempo {
		var s = this.prTake, p;
		this.prSidecar;
		p = RERenderedPlayer.fromTake(path, song);
		this.assert(p.isKindOf(RERenderedPlayer));
		this.assertEquals(p.render[\take], path.basename);
		this.assertEquals(p.latency, 0.2);
		this.assertEquals(p.wav, wav);
		this.assert(p.followTempo, "the take's tempo events are applied");
		~re_rendered_flag = nil;
		this.assert(p.fire(s.at(1)), "a code line counts as played");
		this.assert(~re_rendered_flag.isNil, "but runs nothing");
		this.assert(p.fire((kind: \action, rc: (rc: "beat", layer: "core", name: "k"), method: \set, args: [])), "an action too");
		this.assertEquals(p.fire((kind: \code, text: "1", replay: false)), false, "a line marked replay: false is not");
		clock.tempo = 10;
		this.assert(p.fire((kind: \tempo, tempo: 20)), "a tempo event fires");
		this.assertEquals(clock.tempo, 20, "and sets the clock");
	}

	test_route {
		var s = this.prTake, p;
		this.prSidecar;
		p = RERenderedPlayer.fromTake(path, song);
		this.assertEquals(p.prRoute(nil), \out, "no chain: the output bus");
		song.addOutput(\main, RAOutputChain(Server.default, 3, decode: \ambix, outBus: 0));
		this.assertEquals(p.prRoute(song.ambi), \signal, "a raw render of the chain's channels: summed before the decoder");
		p = RERenderedPlayer(s, song, this.prSidecar("acn-sn3d"));
		this.assertEquals(p.prRoute(song.ambi), \out, "an AmbiX render on an AmbiX chain: after the decoder");
		p = RERenderedPlayer(s, song, this.prSidecar("acn-sn3d"));
		song.ambi.decode = \binaural;
		this.assert(p.prRoute(song.ambi).isNil, "an AmbiX render on a binaural chain: nowhere");
		p = RERenderedPlayer(s, song, this.prSidecar(nil, 2));
		this.assertEquals(p.prRoute(song.ambi), \out, "a stereo render: the output bus");
		p = RERenderedPlayer(s, song, this.prSidecar("acn-n3d", 9));
		this.assertEquals(p.prRoute(song.ambi), \out, "a raw render of another order: the output bus");
		p.routing = \signal;
		this.assertEquals(p.prRoute(song.ambi), \signal, "routing set by hand");
	}

	test_play_refusals_and_warnings {
		var s = this.prTake, p, played = 0;
		this.prSidecar("acn-n3d", 16, (latency: 0.05));
		p = RERenderedPlayer.fromTake(path, song);
		p.onPlay = { played = played + 1 };
		p.play(loop: true);
		this.assert(this.logHas("does not loop"), "loop warned about");
		this.assert(this.logHas("rendered with latency 0.05"), "the latency mismatch warned about");
		this.assert(this.logHas("server is not running"), "no server: refused");
		this.assert(p.isPlaying.not and: { played == 0 }, "nothing played");
		clock.tempo = 10;
		RCLog.reset;
		p.play;
		this.assert(this.logHas("the clock is at tempo 10") and: { this.logHas("the take at 20") }, "a tempo mismatch said");
		this.assert(p.isPlaying.not);
	}

	test_player_hooks_in_the_parent {
		var s = this.prTake, p = REScorePlayer(s, song), started = 0, done = 0, seen = List.new;
		p.onDone = { done = done + 1 };
		p.onEvent = { |pl, ev| seen.add(ev[\id]) };
		p.alignPhase = false;
		p.play(0);
		0.3.wait;
		this.assertEquals(done, 1, "the parent's end hook ran once");
		this.assertEquals(seen.asArray, [1, 2, 3], "every event reported (a tempo event counts as played even when not followed)");
		this.assert(p.isPlaying.not);
	}

	// \clock: from the aligned start on the song's clock, the head on a clock of its own at the
	// take's tempo, the take's tempo events on it, the song's clock untouched; dropped at stop
	test_head_on_its_own_clock {
		var s = REScore(\rendered), p;
		clock.tempo = 10;
		s.meta[\tempo] = 20;
		s.meta[\latency] = 0.2;
		s.meta[\beat0] = 0;
		s.tempoMap.add([0, 20]);
		s.add((id: 1, beat: 0, secs: 0, kind: \code, level: 1, voice: \code, text: "nil"));
		s.add((id: 2, beat: 2, secs: 0.1, kind: \tempo, level: 1, tempo: 40));
		s.add((id: 3, beat: 30, secs: 0.8, kind: \code, level: 1, voice: \code, text: "nil"));
		p = RERenderedPlayer(s, song, this.prSidecar);
		this.assertEquals(p.tempoMode, \clock, "\\clock by default");
		p.prPlayNow(1, 0, nil, nil);   // the start without a server: no WAV, no tape
		this.assert(p.ownClock.isNil, "no clock of its own before the start");
		this.assertEquals(p.headTempo, 20, "the head moves at the take's tempo");
		0.35.wait;
		this.assert(p.ownClock.notNil, "a clock of its own from the start");
		this.assertEquals(p.runClock, p.ownClock, "the head on it");
		this.assertEquals(p.ownClock.tempo, 40, "the take's tempo event applied to it");
		this.assertEquals(clock.tempo, 10, "the song's clock untouched");
		this.assert(p.position > 6, "the head at the take's tempo (the song's clock would be under 3.5): " ++ p.position);
		p.stop;
		this.assert(p.ownClock.isNil and: { p.runClock == clock }, "dropped at stop");
		this.assertEquals(p.position, nil);
	}

	// The effects page's cue: '/fx/start' with the WAV seconds of the take's `from` as the start
	// sounds, '/fx/stop' at stop; the onStart hook with the media's seconds; nothing with fxCue off.
	test_fx_cue_and_onStart {
		var s = this.prTake, p, got;
		p = RERenderedPlayer(s, song, this.prSidecar);
		p.onStart = { |pl, media, from| got = [pl, media, from] };
		p.prPlayNow(1, 2, nil, nil);   // from beat 2: 0.1 s at tempo 20
		0.35.wait;
		this.assert(got.notNil and: { got[0] === p }, "the hook ran with the player");
		this.assertEquals(got[2], 0.1, "the WAV seconds of the take's `from`");
		this.assert(got[1].isNumber and: { (got[1] + got[2] - clock.seconds).abs < 0.5 }, "the media's 0 placed so that `from` sounds about now");
		this.assertEquals(sent.select { |m| m[0] == "/fx/start" }.collect(_[1]), [[0.1]], "'/fx/start' with the seconds, once");
		p.stop;
		this.assertEquals(sent.select { |m| m[0] == "/fx/stop" }.size, 1, "'/fx/stop' at stop");
		p.stop;
		this.assertEquals(sent.select { |m| m[0] == "/fx/stop" }.size, 1, "not again");
		sent.clear;
		p = RERenderedPlayer(s, song, this.prSidecar).fxCue_(false);
		p.prPlayNow(1, 0, nil, nil);
		0.3.wait;
		p.stop;
		this.assertEquals(sent.select { |m| m[0].beginsWith("/fx/") }.size, 0, "fxCue off: nothing sent");
	}

	// \take: the song's clock at the take's tempo from the start, the head on it, the former tempo
	// back at stop
	test_take_mode_on_the_song_clock {
		var s = this.prTake, p;
		clock.tempo = 10;
		p = RERenderedPlayer(s, song, this.prSidecar).tempoMode_(\take);
		p.prPlayNow(1, 0, nil, nil);
		0.25.wait;
		this.assert(p.ownClock.isNil, "no clock of its own");
		this.assertEquals(clock.tempo, 20, "the song's clock at the take's tempo");
		p.stop;
		this.assertEquals(clock.tempo, 10, "and back at stop");
	}

	// waits for done.value (a load's action) at most 5 s
	prWait { |done| var t0 = Main.elapsedTime; while { done.value.not and: { (Main.elapsedTime - t0) < 5 } } { 0.02.wait } }

	// the tape read ahead once, kept while its options stay, read again when they change, none
	// without visuals; another player on the take reads the cache
	test_load_keeps_the_tape {
		var s = this.prTake, p, q, t1, reads, done = false;
		var tape = REVisualsTape.new;
		20.do { |i| tape.add(i / 10, '/lsys/note', [i]); tape.add(i / 10, '/lsys/state', [0, 0.5]) };
		tape.write(REScore.tapePath(path));
		this.prSidecar;
		p = RERenderedPlayer.fromTake(path, song);
		this.assert(p.tape.isNil and: { p.isLoaded.not }, "fromTake reads no tape");
		reads = REVisualsTape.reads;
		p.load({ done = true });
		this.prWait({ done });
		this.assert(done and: { p.tape.size == 40 } and: { p.isLoaded }, "loaded: % entries".format(p.tape !? (_.size)));
		t1 = p.tape;
		done = false;
		p.load({ done = true });
		this.assert(done and: { p.tape === t1 }, "loaded again: the same tape at once");
		this.assertEquals(REVisualsTape.reads, reads + 1, "the file read once");
		p.tapeSkip = ['/lsys/state'];
		this.assert(p.isLoaded.not, "other options: to load again");
		done = false;
		p.load({ done = true });
		this.prWait({ done });
		this.assertEquals(p.tape.size, 20, "the state frames left out");
		p.visuals = false;
		done = false;
		p.load({ done = true });
		this.assert(done and: { p.tape.isNil } and: { p.isLoaded }, "visuals false: no tape, nothing to wait for");
		q = RERenderedPlayer.fromTake(path, song);
		reads = REVisualsTape.reads;
		done = false;
		q.load({ done = true });
		this.prWait({ done });
		this.assertEquals(q.tape.size, 40, "another player on the take");
		this.assertEquals(REVisualsTape.reads, reads, "from the cache");
	}

	// two loads with other options under way: the first one's waiters wait for the second
	test_load_options_changed_while_loading {
		var s = this.prTake, p, first = false, second = false;
		var tape = REVisualsTape.new;
		20.do { |i| tape.add(i / 10, '/lsys/note', [i]); tape.add(i / 10, '/lsys/state', [0, 0.5]) };
		tape.write(REScore.tapePath(path));
		this.prSidecar;
		p = RERenderedPlayer.fromTake(path, song);
		p.load({ first = true });
		p.tapeSkip = ['/lsys/state'];
		p.load({ second = true });
		this.prWait({ first and: { second } });
		this.assert(first and: { second }, "both waiters called");
		this.assertEquals(p.tape.size, 20, "with the last options");
	}
}
