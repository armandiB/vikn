// reCording — HadronLake3's show console (Oct 31 2026): Reaper plays the pieces' stems and sends its
// transport over OSC; this console, sclang without a server, applies one venue offset, sends the show
// clock to HomewareVisuals' effects page through the bridge (RCVisuals), replays each cue's control
// tape chased to that clock (REShowChase) and mixes the Roto-Control's knobs into the tapes' values
// (REShowRoto, REKnobMix).
//
//   c = REShowConsole("~/.../HadronLake3_Show_w0_261031.json");   // or an Event already read
//   c.offset = 0.08; c.live = true;             // the venue offset (seconds, live); the user's bridge (57300), else the environment's
//   c.start;                                    // the port, the cues' tapes read, /fx/chase 1, /fx/cues, a /fx/cue line per cue, the heartbeat
//   c.status; c.time; c.playing; c.cueAt(t);
//   c.timeReceived(12.5); c.transportReceived(1);   // what the OSC handlers call (tests drive them)
//   c.reload;                                   // the show file read again, the cues sent again
//   c.stop;                                     // everything freed, /fx/chase 0
//
// The show file (JSON, REJSON; paths relative to its folder):
//   { "offset": 0.0, "port": 57150, "rate": 15,
//     "cues": [ { "name": "symbolic", "take": "Scores/<take>.json", "video": "symbolic.mp4", "start": 0.0,
//                 "end": 123.4, "videoOffset": 0.0 }, ... ],
//     "roto": { "device": "Roto-Control", "glide": 1.0, "knobs": [ { "cc": 0, "ctl": "symbolic_wave", "op": "add", "range": [-0.5, 0.5] },
//                                                                   { "cc": 1, "set": ["colour", "mix"], "range": [0, 1] } ] } }
// A cue's tape is its take's control tape, named `ctl` by the take's render sidecar (<take>.render.json:
// <take>/<take>.ctl.json, take.sh render --viz); without one the sidecar's `tape` (the .viz.json) is read
// and kept to its /fx/ entries; a cue may name a `tape` itself instead of a take. `end` defaults to
// start + the sidecar's `duration` (else the cue's own `duration`, else the tape's length, said);
// `videoOffset` to 0; `video` is passed to the page as written (a URL it can load). `start` and `end`
// are on the show clock, Reaper's timeline plus the offset (small: write Reaper positions).
//
// Reaper → console (UDP, the console's port, 57150: Reaper's OSC control surface with the default
// patterns): /time <seconds>, /play <0|1>, /stop <0|1>, /pause <0|1>. The transport is also derived
// from /time alone: a time that has not advanced for staleAfter (0.3 s) = paused; one that jumps
// (more than jumpTolerance, 0.5 s, off the extrapolation) = a seek; one that starts advancing in step
// with real time while stopped = playing.
// Console → page (RCVisuals.sendArgs, through the bridge): /fx/chase <0|1> (start, stop); /fx/cues
// (every cue removed) then /fx/cue <name> <video> <start> <end> <videoOffset> per cue, at start and at
// a reload; /fx/time <t>, t = Reaper's time + offset, `rate` times a second while playing, once at a
// seek or a stop (the page lands where Reaper is); /fx/transport <0|1>; and the control tape's entries
// as recorded, at their show time (the heartbeat advances the cue's chase from the previous tick; a
// seek, a start and a cue entered send the state: REShowChase); /fx/start, /fx/stop, /fx/page never.
// A mapped /fx/ctl (a Roto knob's) carries the mix's value in place of the tape's (the chase's filter).
// Every handler and every tick is guarded (RCGuard): one bad entry never kills the console.

REShowConsole {
	var <show, <path, <dir;
	var <port = 57150, <offset = 0.0, <rate = 15, <live = false;
	var <>sendFunc, <>timeFunc;
	var <>heartbeat = true, <>startMidi = true;   // the heartbeat Routine; the roto's MIDI (tests: neither)
	var <>staleAfter = 0.3, <>jumpTolerance = 0.5;
	var <cues, <roto;
	var oscFuncs, routine, <started = false;
	var <playing = false, <lastTime, lastArrival, lastShowTime, <currentCue, <ticks = 0;

	*new { |show| ^super.new.initREShowConsole(show) }

	initREShowConsole { |showarg|
		sendFunc = { |p, args| RCVisuals.sendArgs(p, args) };
		timeFunc = { Main.elapsedTime };
		cues = [];
		if(showarg.isString or: { showarg.isKindOf(Symbol) }) {
			path = showarg.asString.standardizePath;
			dir = path.dirname;
			show = REJSON.read(path);
			if(show.isNil) { RCLog.error(\console, "no show at " ++ path); show = () };
		} {
			show = showarg ? ();
			dir = (show[\dir] ? "").asString;
		};
		if(show.isKindOf(Dictionary).not) { RCLog.error(\console, "a show is a JSON object: % given".format(show.class)); show = () };
		show[\offset] !? { |o| offset = o.asFloat };
		show[\port] !? { |p| port = p.asInteger };
		show[\rate] !? { |r| rate = r.asFloat };
	}

	//////// settings

	port_ { |p|
		port = p.asInteger;
		if(started) { RCLog.warn(\console, "the port changes at the next start") };
	}

	// The venue offset, seconds, live: the show clock is Reaper's time plus it.
	offset_ { |secs| offset = secs.asFloat }

	rate_ { |r| rate = r.asFloat.max(1) }

	// true: the user's bridge (127.0.0.1:57300) whatever the environment says; false: as RCVisuals stands.
	live_ { |flag|
		live = flag == true;
		if(started and: { live }) { RCVisuals.setAddr("127.0.0.1", 57300) };
	}

	//////// the show

	// The port opened and its handlers installed, the cues' tapes read (REVisualsTape.read, synchronous:
	// control tapes are small), the roto started, the page told (/fx/chase 1, /fx/cues, the /fx/cue
	// lines), the heartbeat on SystemClock. Nothing plays until Reaper speaks.
	start {
		if(started) { ^this };
		started = true;
		if(live) { RCVisuals.setAddr("127.0.0.1", 57300) };
		this.prBuild;
		this.prOpenPort;
		this.prSend('/fx/chase', [1]);
		this.prSendCues;
		if(heartbeat) { this.prStartHeartbeat };
		RCLog.post(\console, "started: % cues, port %, offset % s, % a second, % → %:%".format(cues.size, port, offset, rate,
			if(live) { "live" } { "as set" }, RCVisuals.host, RCVisuals.port), true);
		^this
	}

	stop {
		if(started.not) { ^this };
		routine !? (_.stop);
		routine = nil;
		oscFuncs.do(_.free);
		oscFuncs = nil;
		roto !? (_.stop);
		if(playing) { playing = false; this.prSend('/fx/transport', [0]) };
		this.prSend('/fx/chase', [0]);
		started = false;
		RCLog.post(\console, "stopped", true);
		^this
	}

	// The show file read again (when the console was given one), the cues rebuilt and sent again,
	// the chase landed where the clock is.
	reload {
		var wasStarted = started;
		if(path.notNil) {
			show = REJSON.read(path) ?? { RCLog.error(\console, "no show at " ++ path); show };
			show[\offset] !? { |o| offset = o.asFloat };
		};
		roto !? (_.stop);
		this.prBuild;
		if(wasStarted) {
			this.prSendCues;
			lastTime !? { this.prSeek(this.prShowTime(timeFunc.value)) };
		};
		^this
	}

	prBuild {
		var list = show[\cues] ? [];
		if(list.isEmpty) { RCLog.warn(\console, "the show has no cues") };
		cues = list.collect { |c, i| RCGuard.call(\console, nil) { this.prReadCue(c, i) } }.reject(_.isNil);
		cues = cues.sort { |a, b| a[\start] <= b[\start] };
		roto = show[\roto] !? { |spec|
			var r = REShowRoto(spec);
			r.sendFunc = { |p, args| this.prSend(p, args) };
			r.timeFunc = { timeFunc.value };
			if(started and: { startMidi }) { RCGuard.call(\console, nil) { r.start } };
			r
		};
		currentCue = nil;
	}

	// A cue as kept: name, video, start, end, videoOffset, tape (the path read), chase.
	prReadCue { |c, i|
		var cue = (), tapePath, render, fxOnly = false, tape, entries, duration, end;
		cue[\name] = (c[\name] ?? { "cue" ++ (i + 1) }).asString;
		cue[\video] = (c[\video] ? "").asString;
		cue[\start] = (c[\start] ? 0).asFloat;
		cue[\videoOffset] = (c[\videoOffset] ? 0).asFloat;
		if(c[\tape].notNil) {
			tapePath = this.prPath(c[\tape]);
		} {
			if(c[\take].isNil) { RCLog.warn(\console, "cue %: neither take nor tape: it has no control tape".format(cue[\name])) } {
				cue[\take] = this.prPath(c[\take]);
				render = REScore.readRender(cue[\take]);
				if(render.isNil) {
					RCLog.warn(\console, "cue %: no render sidecar next to % (take.sh render --viz): no control tape".format(cue[\name], cue[\take].basename));
				} {
					duration = render[\duration] !? (_.asFloat);
					tapePath = render[\ctl] !? { |p| p = p.asString; if(p.beginsWith("/")) { p } { cue[\take].dirname +/+ p } };
					if(tapePath.isNil or: { File.exists(tapePath).not }) {
						tapePath = render[\tape] !? (_.asString);
						fxOnly = true;
						tapePath !? { RCLog.post(\console, "cue %: no control tape: the visuals tape read for its /fx/ entries".format(cue[\name]), true) };
					};
				};
			};
		};
		entries = [];
		tapePath !? {
			tape = REVisualsTape.read(tapePath);
			if(tape.isNil) { RCLog.warn(\console, "cue %: no tape at %".format(cue[\name], tapePath)) } {
				entries = tape.entries.asArray;
				if(fxOnly) { entries = entries.select { |e| e[1].asString.beginsWith("/fx/") } };
			};
		};
		cue[\tape] = tapePath;
		end = c[\end] !? (_.asFloat);
		if(end.isNil) { c[\duration] !? { |d| end = cue[\start] + d.asFloat } };
		if(end.isNil) { duration !? { |d| end = cue[\start] + d } };
		if(end.isNil) {
			end = cue[\start] + (entries.last !? (_[0]) ? 0);
			RCLog.warn(\console, "cue %: no end, no duration: it ends with its tape, at % s".format(cue[\name], end.round(0.01)));
		};
		cue[\end] = end;
		cue[\chase] = REShowChase(entries, cue[\start]);
		cue[\chase].sendFunc = { |p, args| this.prSend(p, args) };
		cue[\chase].filter = { |e| this.prFilter(e) };
		^cue
	}

	// a path of the show file: relative to its folder
	prPath { |p|
		p = p.asString;
		if(p.beginsWith("~")) { ^p.standardizePath };
		if(p.beginsWith("/")) { ^p };
		^if(dir.isEmpty) { p } { dir +/+ p }
	}

	prSendCues {
		this.prSend('/fx/cues', []);
		cues.do { |cue| this.prSend('/fx/cue', [cue[\name], cue[\video], cue[\start], cue[\end], cue[\videoOffset]]) };
	}

	// a mapped /fx/ctl carries the mix's value; everything else goes as it is
	prFilter { |e|
		var v;
		if(roto.isNil or: { e[1].asString != "/fx/ctl" } or: { e.size < 4 }) { ^e };
		v = roto.tapeValue(e[2], e[3], timeFunc.value);
		if(v.isNil) { ^e };
		^[e[0], e[1], e[2], v]
	}

	prSend { |p, args| RCGuard.call(\console, nil) { sendFunc.value(p, args) } }

	//////// Reaper's side

	prOpenPort {
		var opened = RCGuard.call(\console, false) { thisProcess.openUDPPort(port) };
		if(opened.not) {
			RCLog.error(\console, "cannot open UDP port %: Reaper is not heard (another console? set port before start)".format(port));
			^this
		};
		oscFuncs = [
			OSCFunc({ |msg| RCGuard.call(\console, nil) { this.timeReceived(msg[1]) } }, '/time', recvPort: port),
			OSCFunc({ |msg| RCGuard.call(\console, nil) { this.transportReceived(msg[1]) } }, '/play', recvPort: port),
			OSCFunc({ |msg| RCGuard.call(\console, nil) { if((msg[1] ? 1) != 0) { this.transportReceived(0) } } }, '/stop', recvPort: port),
			OSCFunc({ |msg| RCGuard.call(\console, nil) { if((msg[1] ? 1) != 0) { this.transportReceived(0) } } }, '/pause', recvPort: port)
		].collect(_.permanent_(true));
	}

	// Reaper's time, seconds. A jump off the extrapolation (playing), or a change while stopped, lands
	// the page there; a time advancing in step with real time while stopped starts the transport.
	timeReceived { |secs|
		var now = timeFunc.value, jump = false, play = false, dt, predicted;
		if(secs.isNumber.not) { ^this };
		secs = secs.asFloat;
		if(lastTime.isNil) {
			jump = playing.not;
		} {
			if(playing) {
				predicted = lastTime + (now - lastArrival);
				jump = (secs - predicted).abs > jumpTolerance;
			} {
				if((secs - lastTime).abs > 1e-4) {
					dt = now - lastArrival;
					if(secs > lastTime and: { ((secs - lastTime) - dt).abs <= jumpTolerance }) { play = true } { jump = true };
				};
			};
		};
		lastTime = secs;
		lastArrival = now;
		if(play) { ^this.prPlay(now) };
		if(jump) { this.prSeek(this.prShowTime(now)) };
		^this
	}

	// 1 (or true): playing, 0: stopped or paused.
	transportReceived { |flag|
		var now = timeFunc.value;
		if(flag == true or: { flag.isNumber and: { flag != 0 } }) { ^this.prPlay(now) };
		^this.prStop(now)
	}

	prPlay { |now|
		if(playing) { ^this };
		playing = true;
		lastArrival = now;   // the extrapolation runs from here, at the time Reaper last said
		this.prSend('/fx/transport', [1]);
		this.prSeek(this.prShowTime(now));
	}

	prStop { |now|
		if(playing.not) { ^this };
		playing = false;
		lastShowTime = (lastTime ? 0) + offset;
		this.prSend('/fx/time', [lastShowTime]);
		this.prSend('/fx/transport', [0]);
	}

	// the state at show time t: one /fx/time, the cue's chase sought
	prSeek { |t|
		this.prSend('/fx/time', [t]);
		currentCue = this.cueAt(t);
		currentCue !? { |cue| cue[\chase].seek(t) };
		lastShowTime = t;
	}

	//////// the heartbeat

	prStartHeartbeat {
		routine !? (_.stop);
		routine = Routine {
			loop {
				RCGuard.call(\console, nil) { this.tick(timeFunc.value) };
				(1 / rate.max(1)).wait;
			};
		};
		// from the real time, not the main thread's logical time (frozen in headless sclang)
		SystemClock.schedAbs(Main.elapsedTime + (1 / rate.max(1)), routine);
	}

	// One beat of the heartbeat at `now` (timeFunc's seconds): playing, the show clock sent and the
	// cue's chase advanced from the previous tick (a cue entered: its state); a /time gone stale, the
	// transport derived stopped; the roto's gliding mixes sent. Returns the show time sent, or nil.
	tick { |now|
		var t, cue, sentT;
		now = now ?? { timeFunc.value };
		ticks = ticks + 1;
		if(playing) {
			if((now - lastArrival) > staleAfter) {
				this.prStop(now);
			} {
				t = this.prShowTime(now);
				this.prSend('/fx/time', [t]);
				cue = this.cueAt(t);
				if(cue !== currentCue) {
					currentCue = cue;
					cue !? { cue[\chase].seek(t) };
				} {
					cue !? { if(lastShowTime.notNil and: { t > lastShowTime }) { cue[\chase].advance(lastShowTime, t) } };
				};
				lastShowTime = t;
				sentT = t;
			};
		};
		roto !? { |r| RCGuard.call(\console, nil) { r.tick(now) } };
		^sentT
	}

	// the show clock at `now`: Reaper's time extrapolated while playing, as last said otherwise, plus the offset
	prShowTime { |now|
		if(playing and: { lastArrival.notNil }) { ^lastTime + (now - lastArrival) + offset };
		^(lastTime ? 0) + offset
	}

	//////// reading

	// The show clock now (nil before Reaper said anything).
	time { ^if(lastTime.isNil) { nil } { this.prShowTime(timeFunc.value) } }

	// The cue at show time t: the last started among those holding t (start <= t < end), nil between cues.
	cueAt { |t|
		var found;
		cues.do { |cue| if(cue[\start] <= t and: { t < cue[\end] }) { found = cue } };
		^found
	}

	cueNamed { |name| ^cues.detect { |c| c[\name] == name.asString } }

	// One line posted and returned.
	status {
		var t = this.time, cue = t !? { this.cueAt(t) }, s;
		s = "REShowConsole: % %".format(
			if(started.not) { "not started" } { if(playing) { "playing" } { "stopped" } },
			t !? { "at % s".format(t.round(0.01)) } ? "(no time from Reaper yet)");
		cue !? { s = s ++ ", cue % (% s in, % entries)".format(cue[\name], (t - cue[\start]).round(0.01), cue[\chase].size) };
		if(t.notNil and: { cue.isNil }) { s = s ++ ", between cues" };
		s = s ++ "; offset % s, port %, % a second, % cues".format(offset, port, rate, cues.size);
		roto !? { |r| s = s ++ ", roto % knobs".format(r.size) };
		RCLog.post(\console, s, true);
		^s
	}

	printOn { |stream| stream << "REShowConsole(" << cues.size << " cues" << if(started) { ", started" } { "" } << if(playing) { ", playing)" } { ")" } }
}
