// reCording — the visuals feed: what a song's recorder records and what its player plays, sent
// to HomewareVisuals through RCVisuals (OSC 57121 → the bridge → a page), so that a page draws
// the performance's score as it is made and as it replays (public/lib/score-viz.js there,
// public/score/).
//
//   ~viz = REScoreVisuals(~song.scoreRecorder);   // attached to the recorder: every take from now on
//   ~viz.attachPlayer(~p);                        // and to a player: its take, its playhead
//   ~viz.detach;
//
// Protocol: one JSON string argument per message (REJSON, as the rig's feed), every path under
// /score (`prefix`):
//   /score/take     {name, song, state: "recording" | "loaded", beat0, tempo, latency, duration, voices, controls, events, from, to, loop}
//                   a take starts recording, or a take is loaded to play (its events follow)
//   /score/events   {name, chunk, of, events: [...]}       the loaded take's events, `chunk` of `of`
//   /score/event    {id, beat, secs, kind, level, voice, control, value, name, path, method, msg, text, cause, defer, replay}
//                   one event recorded (level 1; `control` is REScore's control key as words,
//                   `value` the value it sets, `text` a code line's first characters)
//   /score/played   {id, beat}                             one event fired by the player
//   /score/head     {name, beat, state: "recording" | "playing"}   the recording head or the
//                   playhead, headRate times a second
//   /score/stop     {name, duration, events, path}         the take stopped (path when written)
//   /score/stopPlay {name}                                 the replay stopped, or ended
// The hooks chain: a recorder's or a player's own onStart, onEvent, onStop, onPlay and onDone
// keep running, and detach puts them back. Nothing of it records anything.

REScoreVisuals {
	classvar <>prefix = "/score";
	classvar <>chunk = 40;        // events per /score/events message (an OSC packet stays under 64 kB)
	classvar <>headRate = 10;     // head messages per second
	classvar <>textLength = 80;   // characters of a code line kept in an event message
	var <recorder, <player, saved, head, <name;

	*new { |recorder, player| ^super.new.initREScoreVisuals(recorder, player) }

	initREScoreVisuals { |rec, p|
		saved = IdentityDictionary.new;
		rec !? { this.attach(rec) };
		p !? { this.attachPlayer(p) };
	}

	attach { |rec|
		this.detachRecorder;
		recorder = rec;
		saved[\recStart] = rec.onStart;
		saved[\recEvent] = rec.onEvent;
		saved[\recStop] = rec.onStop;
		rec.onStart = { |r| saved[\recStart].value(r); this.prRecStart(r) };
		rec.onEvent = { |r, ev| saved[\recEvent].value(r, ev); this.prRecEvent(r, ev) };
		rec.onStop = { |r, s, path| saved[\recStop].value(r, s, path); this.prRecStop(r, s, path) };
	}

	attachPlayer { |p|
		this.detachPlayer;
		player = p;
		saved[\play] = p.onPlay;
		saved[\playEvent] = p.onEvent;
		saved[\playStop] = p.onStop;
		saved[\playDone] = p.onDone;
		p.onPlay = { |pl| saved[\play].value(pl); this.prPlay(pl) };
		p.onEvent = { |pl, ev| saved[\playEvent].value(pl, ev); this.prPlayed(pl, ev) };
		p.onStop = { |pl| saved[\playStop].value(pl); this.prPlayStop(pl) };
		p.onDone = { |pl| saved[\playDone].value(pl); this.prPlayStop(pl) };
	}

	detachRecorder {
		recorder !? { |r| r.onStart = saved[\recStart]; r.onEvent = saved[\recEvent]; r.onStop = saved[\recStop] };
		recorder = nil;
	}

	detachPlayer {
		player !? { |p| p.onPlay = saved[\play]; p.onEvent = saved[\playEvent]; p.onStop = saved[\playStop]; p.onDone = saved[\playDone] };
		player = nil;
		this.prStopHead;
	}

	detach { this.detachRecorder; this.detachPlayer }

	// the page reads plain strings: a Symbol goes as its name (REJSON would mark it with a backslash)
	send { |path, dict| ^RCVisuals.send(prefix ++ path, REJSON.stringify(this.class.plain(dict))) }

	*plain { |x|
		case
		{ x.isKindOf(Symbol) } { ^x.asString }
		{ x.isKindOf(Dictionary) } { var d = IdentityDictionary.new; x.keysValuesDo { |k, v| d[k.asSymbol] = this.plain(v) }; ^d }
		{ x.isKindOf(SequenceableCollection) and: { x.isString.not } } { ^x.collect { |v| this.plain(v) } };
		^x
	}

	//////// the recorder

	prRecStart { |r|
		var s = r.score;
		name = (r.song.name.asString ++ " " ++ Date.localtime.stamp).asSymbol;
		this.send("/take", (name: name, song: r.song.name, state: "recording", beat0: r.beat0, tempo: r.clock.tempo, latency: s.meta[\latency],
			voices: r.voices.keys.asArray.collect(_.asString).sort, controls: r.controls, events: 0));
		this.prStartHead({ (name: name, beat: r.clock.beats - r.beat0, state: "recording") }, { r.isRecording });
	}

	prRecEvent { |r, ev| if((ev[\level] ? 1) == 1) { this.send("/event", this.class.eventDict(ev)) } }

	prRecStop { |r, s, path|
		this.prStopHead;
		this.send("/stop", (name: name, duration: s.duration, events: s.size, path: path !? (_.asString)));
	}

	//////// the player

	prPlay { |p|
		var s = p.score, evs = s.events.asArray.select { |e| (e[\level] ? 1) == 1 }, n = (evs.size / chunk).ceil.max(1);
		name = (s.meta[\song].asString ++ " " ++ (s.meta[\created] ? "")).asSymbol;
		this.send("/take", (name: name, song: s.meta[\song], state: "loaded", beat0: s.meta[\beat0], tempo: s.meta[\tempo], latency: s.meta[\latency],
			duration: s.duration, voices: s.voiceNames.collect(_.asString), controls: s.controls, events: evs.size, from: p.from, to: p.to, loop: p.loop));
		evs.clump(chunk).do { |c, i| this.send("/events", (name: name, chunk: i, of: n, events: c.collect { |e| this.class.eventDict(e) })) };
		this.prStartHead({ (name: name, beat: p.position ? p.from, state: "playing") }, { p.isPlaying });   // before the first event: the start
	}

	prPlayed { |p, ev| this.send("/played", (id: ev[\id], beat: ev[\beat])) }

	prPlayStop { |p|
		this.prStopHead;
		this.send("/stopPlay", (name: name));
	}

	//////// the head: a Routine on AppClock while the condition holds

	prStartHead { |dictFunc, whileFunc|
		var tick = { RCGuard.call(\visuals, nil) { var d = dictFunc.value; if(d[\beat].notNil) { this.send("/head", d) } } };
		this.prStopHead;
		tick.value;   // at once: the start (a short take may end before the clock's first tick)
		head = Routine {
			while { whileFunc.value } {
				(1 / headRate).wait;
				if(whileFunc.value) { tick.value };
			};
		}.play(AppClock);
	}

	prStopHead { head !? (_.stop); head = nil }

	// A compact event for the page: what lanes need (REScore's control key as words, the value
	// it sets), the names, a code line's first characters.
	*eventDict { |ev|
		var d = IdentityDictionary.new, key = REScore.controlKey(ev), value = REScore.controlValue(ev);
		d[\id] = ev[\id];
		d[\beat] = ev[\beat];
		d[\secs] = ev[\secs];
		d[\kind] = ev[\kind];
		d[\level] = ev[\level] ? 1;
		ev[\voice] !? { |v| d[\voice] = v };
		ev[\cause] !? { |c| d[\cause] = c };
		key !? { d[\control] = key.collect { |x| var s = x.asString; if(s.beginsWith("\\")) { s.drop(1) } { s } }.join(" ") };
		if(value.isNumber) { d[\value] = value };
		#[\name, \path, \method, \msg, \defer, \replay].do { |k| ev[k] !? { |v| d[k] = v } };
		ev[\text] !? { |t| d[\text] = t.asString.keep(textLength) };
		^d
	}

	printOn { |stream| stream << "REScoreVisuals(" << (recorder !? (_.song) !? (_.name)) << ", " << (player !? { "a player" } ? "no player") << ")" }
}
