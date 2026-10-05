// RERenderedPlayer: a take with a render next to it (the sidecar), nothing fired but the tempo
// events, the route of the WAV into the chain, the refusals and warnings without a server.
TestRERenderedPlayer : UnitTest {
	var clock, song, root, path, sidecar, wav, savedRateLimit, savedMainThreadOnly, savedTempo;

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
		root = PathName.tmp +/+ "re_rendered_test_" ++ UniqueID.next;
		File.mkdir(root);
		path = root +/+ "260101_120000_rendered w0.json";
		sidecar = REScore.renderPath(path);
		wav = root +/+ "260101_120000_rendered w0.wav";
	}

	tearDown {
		clock.tempo = savedTempo;
		RETap.mainThreadOnly = savedMainThreadOnly;
		RCTestSupport.reset;
		RCLog.rateLimit = savedRateLimit;
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
		var d = (format: "re-render", version: 1, take: path.basename, wav: wav.basename, channels: channels, hoa: hoa,
			decode: if(hoa == "acn-sn3d") { "ambix" } { "raw" }, outBus: 0, latency: 0.2, sampleRate: 48000, tempo: 20,
			startBeat: 0, startSecs: 0, duration: 3.15, tail: 3, level: 2, tape: "260101_120000_rendered w0.viz.json");
		extra !? { |e| e.keysValuesDo { |k, v| d[k] = v } };
		REJSON.write(d, sidecar, 2, 2);
		^d
	}

	test_render_paths_and_sidecar {
		var s = this.prTake, d;
		this.assertEquals(REScore.renderPath(path), path.drop(-5) ++ ".render.json");
		this.assertEquals(REScore.tapePath(path), path.drop(-5) ++ ".viz.json");
		this.assertEquals(REScore.statePath(path), path.drop(-5) ++ ".state");
		this.assert(REScore.isCompanion(sidecar) and: { REScore.isCompanion(REScore.tapePath(path)) } and: { REScore.isCompanion(REScore.companionPath(path, 2)) }, "the render's files are companions");
		this.assert(REScore.isCompanion(path).not, "a take is not");
		this.assert(REScore.readRender(path).isNil, "no sidecar: nil");
		this.assert(RERenderedPlayer.fromTake(path, song).isNil and: { this.logHas("no render next to") }, "fromTake without a render: nil, said");
		this.prSidecar;
		d = REScore.readRender(path);
		this.assertEquals(d[\format], "re-render");
		this.assertEquals(d[\wav], wav, "a relative wav made absolute against the take's folder");
		this.assertEquals(d[\tape], root +/+ "260101_120000_rendered w0.viz.json");
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
		this.assert(this.logHas("the clock is at tempo 10") and: { this.logHas("the take at 20") }, "a tempo mismatch warned about");
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
}
