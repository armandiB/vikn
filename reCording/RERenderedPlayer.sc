// reCording — a rendered take played live: the WAV that scripts/take.sh render --viz wrote (the
// field, into the song's chain) and its visuals tape (<take>.viz.json, to HomewareVisuals through
// RCVisuals), from the take's aligned start on the song's clock, next to the takes the program
// plays live. No event is fired (the render made the sound), the tempo events apart; every event
// is still reported (onEvent: the score page's played marks) and the head runs (REScoreVisuals).
//
//   ~p = RERenderedPlayer.fromTake(path, ~song);   // the take and its sidecar, <take>.render.json
//   ~viz.attachPlayer(~p); ~p.play([4, 0]);
//   ~p.stop; ~p.free;
//
// Timing: WAV time 0 is the take's start on the render's clock. The render bundled its sounds with
// its latency λr, the live server plays with λl: the WAV is cued λr in and its synth sent in a
// latency bundle at the start beat (seconds S0), so WAV time w sounds at S0 + w - λr + λl, and a
// tape message at t goes out at S0 + t - λr + λl, the same offset: the messages sent when a sound
// starts land with it whatever the two latencies; a message a rig sends without latency (gone,
// clear, hair) is off by λl - λr, nothing with the one server-options file. The WAV cannot stretch:
// the clock must be at the take's tempo (warned), and the take's tempo events are applied (the
// render's code lines changed its clock that way; they are not fired here).
// Routing (routing: \auto, \signal or \out): a raw render (hoa acn-n3d, the chain's channels) is
// summed into the chain's signal stage and decoded live as the instruments are; an AmbiX render
// (acn-sn3d) or a stereo one goes onto the chain's output bus after the decoder (an AmbiX one only
// when the live chain decodes to \ambix: render with --decode raw otherwise). The chain's buses and
// groups are looked up at play: a scene init rebuilds them.

RERenderedPlayer : REScorePlayer {
	var <render, <replay, <tape, <>routing = \auto, <>tapeClock, <route, <mediaSecs, fromSecs = 0, preparing = false;

	*new { |score, song, render| ^super.new(score, song).initRERenderedPlayer(render) }

	// The take at path with the render next to it (REScore.readRender); nil without one.
	*fromTake { |path, song|
		var s = REScore.read(path), r;
		if(s.isNil) { ^nil };
		r = REScore.readRender(path);
		if(r.isNil) { RCLog.error(\rendered, "no render next to % (scripts/take.sh render --viz)".format(path)); ^nil };
		^this.new(s, song, r)
	}

	initRERenderedPlayer { |r|
		render = if(r.isString) { REJSON.read(r) } { r };
		followTempo = true;
		tapeClock = SystemClock;
	}

	latency { ^((render !? (_[\latency])) ? 0).asFloat }
	wav { ^render !? { |r| r[\wav] !? (_.asString) } }
	duration { ^((render !? (_[\duration])) ? 0).asFloat }

	// The route of the WAV into the song: \signal (summed before the decoder), \out (after it),
	// nil when the render cannot play through this chain.
	prRoute { |chain|
		var hoa = render[\hoa] !? (_.asString), chans = (render[\channels] ? 16).asInteger;
		if(routing != \auto) { ^routing };
		if(chain.isNil) { ^\out };
		if(hoa == "acn-n3d" and: { chans == chain.numChannels }) { ^\signal };
		if(hoa == "acn-sn3d") { ^if(chain.decode == \ambix and: { chans == chain.playChannels }) { \out } { nil } };
		^\out
	}

	play { |quant, from = 0, to, loop = false, atBeat|
		var takeTempo = score.meta[\tempo], chain = song.ambi;
		if(render.isNil) { ^RCLog.error(\rendered, "no render: nothing to play") };
		if(loop) { RCLog.warn(\rendered, "a rendered take does not loop: played once"); loop = false };
		if(takeTempo.notNil and: { (clock.tempo - takeTempo).abs > 1e-6 }) {
			RCLog.warn(\rendered, "the clock is at tempo %, the take at %: the head drifts from the sound".format(clock.tempo, takeTempo));
		};
		if((this.latency - song.server.latency).abs > 1e-6) {
			RCLog.warn(\rendered, "rendered with latency %, the server's is %: the messages a rig sends without latency land % s off".format(this.latency, song.server.latency, (song.server.latency - this.latency).round(0.001)));
		};
		if(song.server.serverRunning.not) { ^RCLog.error(\rendered, "the server is not running: the render's WAV needs it") };
		if(this.wav.isNil or: { File.exists(this.wav).not }) { ^RCLog.error(\rendered, "no WAV at " ++ this.wav) };
		route = this.prRoute(chain);
		if(route.isNil) {
			^RCLog.error(\rendered, "an AmbiX render (%) plays only through a chain decoding to ambix (this one decodes to %): render it with --decode raw".format(render[\hoa], chain.decode));
		};
		if(state == \playing) { this.stop };
		preparing = true;
		this.prPrepare(from ? 0, chain, { if(preparing) { preparing = false; this.prPlayNow(quant, from, to, atBeat) } });
	}

	prPlayNow { |quant, from, to, atBeat| ^super.play(quant, from, to, false, atBeat) }

	// The WAV cued from the take's beat `from` plus the render's latency, the tape read, the route set.
	prPrepare { |from, chain, action|
		var sf = SoundFile.openRead(this.wav), sr, frame;
		if(sf.isNil) { preparing = false; ^RCLog.error(\rendered, "cannot open " ++ this.wav) };
		sr = sf.sampleRate;
		sf.close;
		fromSecs = from / (score.meta[\tempo] ? clock.tempo);
		frame = ((fromSecs + this.latency) * sr).asInteger;
		tape = render[\tape] !? { |p| REVisualsTape.read(p) };
		if(tape.isNil) { RCLog.warn(\rendered, "no visuals tape for %: the sound alone".format(render[\take])) };
		replay !? (_.free);
		replay = REReplay(song.server, this.wav, (render[\channels] ? 16).asInteger, clock);
		if(route == \out) {
			replay.into(chain !? (_.outBus) ? 0, chain !? (_.playGroup)).addAction_(\addToTail);
		} {
			replay.into(chain.bus(\signal), chain.stageGroups[\signal]).addAction_(\addToHead);
		};
		replay.prepare(frame, action);
	}

	// In the Routine, at the aligned start beat: the WAV in a latency bundle, the tape on its clock.
	prStarted {
		mediaSecs = thisThread.seconds - this.latency + song.server.latency - fromSecs;
		song.server.bind { replay.startNow };
		tape !? { |t| if(t.isEmpty.not) { t.play(mediaSecs, tapeClock, nil, fromSecs) } };
		RCLog.post(\rendered, "% plays its render through the chain's % (% tape entries)".format(render[\take], route, tape !? (_.size) ? 0));
	}

	// The last event reported: the sound and the tape go on to the render's end.
	prEnded {
		var rest = (mediaSecs ? thisThread.seconds) + this.duration - thisThread.seconds;
		if(rest > 0) { (rest * clock.tempo).wait };
		this.prStopMedia;
		super.prEnded;
	}

	stop {
		super.stop;
		preparing = false;
		this.prStopMedia;
	}

	prStopMedia {
		tape !? (_.stop);
		replay !? (_.stop);
	}

	free {
		this.stop;
		replay !? (_.free);
		replay = nil;
	}

	// Nothing is fired (the render made the sound) but the tempo events; the rest counts as played.
	fire { |ev|
		if(ev[\kind] == \tempo) { ^super.fire(ev) };
		if(ev[\replay] == false) { ^false };
		^true
	}

	printOn { |stream| stream << "RERenderedPlayer(" << (render !? (_[\take]) ? "no render") << ", " << (state ? "idle") << ")" }
}
