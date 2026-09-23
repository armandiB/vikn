// Minimal stand-in for a beat, so the session layer can be tested without RCBeat.
RCTestFakeBeat {
	var <name, <freed = false, <paused = false;
	*new { |name| ^super.newCopyArgs(name.asSymbol) }
	free { freed = true }
	pause { paused = true }
	resume { paused = false }
	lastValue { |key, default| ^if(key == \known) { 42 } { default.value } }
}

TestRCSession : UnitTest {
	var clock;

	setUp {
		RCSession.reset;
		clock = TempoClock.new;
		RCSession.boot(Server.default, clock, oscPort: nil, initMidi: false);
		RCLog.reset;
	}

	tearDown {
		RCSession.reset;
		clock.stop;
	}

	test_boot_is_idempotent {
		var s1 = RCSession.default;
		var s2 = RCSession.boot(Server.default, oscPort: nil, initMidi: false);
		this.assert(s1 === s2, "boot returns the same session");
		this.assert(s2.clock === clock, "a running clock is kept");
		this.assert(s2.booted, "booted flag");
		this.assertEquals(s2.oscPort, nil, "no OSC port requested");
	}

	test_song_registration_and_replacement {
		var a = RCSong(\t, 1);
		var b;
		this.assert(RCSession.default.song(\t) === a, "song registered");
		b = RCSong(\t, 2);
		this.assert(RCSession.default.song(\t) === b, "same name replaces");
		this.assertEquals(RCSession.default.songs.size, 1, "only one song under that name");
		b.free;
		this.assertEquals(RCSession.default.songs.size, 0, "free unregisters");
	}

	test_song_defaults {
		var song = RCSong(\d, 1994);
		this.assertEquals(song.layers.keys.asArray.sort, [\core, \details, \meta], "default layers");
		this.assert(song.clock === clock, "song clock from session");
		this.assert(song.layer(\core).clock === clock, "layer clock from song");
		this.assertEquals(song.layer(\core).seed, 1994, "layer seed from song");
		this.assertEquals(song.layer(\nope), nil, "unknown layer → nil");
		this.assert(song.layer(\core).swing !== song.layer(\details).swing, "layers own separate swings");
		this.assertEquals(song.outArray, [0], "outArray default");
	}

	test_song_requires_session {
		var failed = false;
		RCSession.reset;
		try { RCSong(\x, 1) } { failed = true };
		this.assert(failed, "RCSong without a session throws a clear error");
	}

	test_layer_beat_registry {
		var song = RCSong(\l, 1);
		var layer = song.layer(\core);
		var b1 = RCTestFakeBeat(\kick), b1bis = RCTestFakeBeat(\kick), b2 = RCTestFakeBeat(\snare);
		layer.registerBeat(b1);
		layer.registerBeat(b2);
		this.assert(layer.beat(\kick) === b1, "beat lookup");
		layer.registerBeat(b1bis);
		this.assert(b1.freed, "same-name registration frees the old beat");
		this.assert(layer.beat(\kick) === b1bis, "and stores the new one");
		layer.unregisterBeat(b1);
		this.assert(layer.beat(\kick) === b1bis, "unregistering a stale beat does not remove the live one");
		this.assertEquals(layer.history.asArray, [\kick, \snare, \kick], "history keeps every registration");
		this.assertEquals(layer.beatValue(\kick, \known, -1), 42, "beatValue delegates to the beat");
		this.assertEquals(layer.beatValue(\ghost, \known, { -1 }), -1, "beatValue default for unknown beat");
		layer.pauseAll;
		this.assert(b2.paused, "pauseAll");
		layer.resumeAll;
		this.assert(b2.paused.not, "resumeAll");
		this.assertEquals(song.allBeats.size, 2, "allBeats across layers");
		this.assert(layer.deleteBeat(\snare), "deleteBeat true");
		this.assert(b2.freed, "deleteBeat frees");
		this.assert(layer.deleteBeat(\snare).not, "deleteBeat false when absent");
		song.killAllBeats;
		this.assert(b1bis.freed, "killAllBeats frees remaining beats");
		this.assertEquals(layer.beats.size, 0, "killAll also drops the registry entries");
	}

	test_swing {
		var sw = RCSwing(0.5, 2, 0.1);
		this.assertFloatEquals(sw.value(0), 0.1, "t=0 → shift only");
		this.assertFloatEquals(sw.value(0.5), 0.5 * sin(pi * 0.5) + 0.1, "sine shape");
		this.assertFloatEquals(sw.value(2.5), sw.value(0.5), "periodic in mod");
		this.assertEquals(RCSwing(1, 0, 0.2).value(3), 0.2, "mod 0 → shift");
		this.assertEquals(RCSwing(func: { |t| 0 / 0 }).value(1), 0, "NaN from a custom func → 0");
		this.assertEquals(RCSwing(func: { |t, s| t * 2 }).value(1.5), 3.0, "custom func");
		this.assertEquals(RCSwing(func: { nil.foo }).value(1), 0, "error in custom func → 0");
	}

	test_session_killAll {
		var song = RCSong(\k, 1);
		var b = RCTestFakeBeat(\x);
		song.layer(\meta).registerBeat(b);
		RCSession.killAll;
		this.assert(b.freed, "RCSession.killAll frees beats in every song");
	}
}
