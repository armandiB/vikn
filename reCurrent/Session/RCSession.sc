// reCurrent — the rig: one server, one clock, one OSC port, MIDI, songs.
//
// Nothing here runs at class-load time. A piece bootstrap calls
//   RCSession.boot(s, oscPort: 32345);
// which is idempotent: a running clock is never re-created, an already open
// port is fine, MIDI is initialized once. Tests use
//   RCSession.boot(Server.default, TempoClock.new, oscPort: nil, initMidi: false)

RCSession {
	classvar <default;
	var <server, <clock, <oscPort, <localAddr, <songs;
	var <midiInitialized = false, <booted = false;

	*boot { |server, clock, oscPort = 32345, initMidi = true|
		default = default ?? { super.new.initRCSession };
		default.prBoot(server, clock, oscPort, initMidi);
		^default
	}

	// Emergency handle: stop every beat of every song. Frees nothing.
	*killAll { default !? { |s| s.killAll } }

	// Forget the default session (tests). Songs are freed.
	*reset { default !? { |s| s.free }; default = nil }

	initRCSession {
		songs = IdentityDictionary.new;
	}

	prBoot { |serverarg, clockarg, oscPortarg, initMidi|
		server = serverarg ? server ? Server.default;
		if(clockarg.notNil) {
			clock = clockarg;
		} {
			if(clock.isNil or: { clock.isRunning.not }) {
				clock = LinkClock(nil, queueSize: 4096).latency_(server.latency).permanent_(true);
				RCLog.post(\session, "created LinkClock (tempo %)".format(clock.tempo));
			} {
				RCLog.info(\session, "keeping the running clock");
			};
		};
		oscPort = oscPortarg;
		if(oscPort.notNil) {
			if(thisProcess.openUDPPort(oscPort)) {
				RCLog.post(\session, "OSC port % open".format(oscPort));
			} {
				RCLog.error(\session, "could not open OSC port % (held by another process? sudo lsof -i :%)".format(oscPort, oscPort));
			};
		};
		localAddr = localAddr ?? { NetAddr("127.0.0.1") };
		if(initMidi) { this.initMidi };
		booted = true;
	}

	initMidi {
		if(MIDIClient.initialized.not) {
			RCGuard.call(\session, nil) { MIDIClient.init };
		};
		if(MIDIClient.initialized) {
			RCGuard.call(\session, nil) { MIDIIn.connectAll };
			midiInitialized = true;
			RCLog.post(\session, "MIDI ready, % source(s)".format(MIDIClient.sources.size));
		} {
			RCLog.error(\session, "MIDIClient.init failed, MIDI control unavailable");
		};
	}

	registerSong { |song|
		songs[song.name] !? { |old|
			if(old !== song) {
				RCLog.warn(\session, "replacing song %".format(song.name));
				old.free;
			};
		};
		songs[song.name] = song;
	}

	unregisterSong { |song|
		if(songs[song.name] === song) { songs.removeAt(song.name) };
	}

	song { |name| ^songs[name.asSymbol] }

	killAll {
		songs.do(_.killAllBeats);
		RCLog.post(\session, "killed all beats in % song(s)".format(songs.size));
	}

	free {
		songs.copy.do(_.free);
		songs.clear;
		if(default === this) { default = nil };
	}

	printOn { |stream|
		stream << "RCSession(" << server << ", " << songs.size << " songs)"
	}
}
