// reCording — plays an REScore back on its song: every event at its recorded
// beat on the song's clock, from one Routine that waits between events;
// nothing it does is recorded (RETap.silently) and, with the server running,
// each event's messages go in a server.latency bundle, in line with the
// pattern events.
//
//   p = REScorePlayer(score, ~song);
//   p.play([4, 0]);                              // from the start, on the next bar
//   p.play(from: 8, to: 16, loop: true);         // a span, again and again
//   p.mute(['core/kick']); p.solo([\lead]); p.stop;
//   p.onEvent = { |player, ev| ... }; p.onLoop = { |player, pass| }; p.onDone = { |player| }
//   p.fire(score.at(12));                        // one event, now
//
// How an event replays: a MIDI event through its mapping's action with the
// recorded mapped value (RCMidi.replay), an OSC event through its def's
// function with the recorded arguments, an action by calling the method on
// the object resolved by name (REScore.resolve), a keyboard message on the
// song's keyboard state, a code line compiled and run in the current
// environment, a snapshot by setting the recorded sources (applyState), a
// morph by ramping to them. An event marked replay: false is skipped. When a
// root event cannot be fired (its mapping or def is gone, its object is not
// found, its code raises), the effects recorded under it play instead; what
// cannot play is skipped with one warning each.

REScorePlayer {
	classvar <orgnsmIdentityKeys;

	var <score, <song, <>clock;
	var <state = \stopped, routine, <startBeat, <from = 0, <to, <loop = false, <passes = 0;
	var <muted, <soloed;
	var <>onEvent, <>onLoop, <>onDone;
	var <>followTempo = false, <>useLatency = true;
	var <fired = 0, <skipped = 0, warned, failedRoots;

	*initClass {
		orgnsmIdentityKeys = #[\orgnsm, \tribe, \o_species, \orgnsm_name, \tribe_name, \seed];
	}

	*new { |score, song| ^super.new.initREScorePlayer(score, song) }

	initREScorePlayer { |scorearg, songarg|
		score = scorearg;
		song = songarg;
		clock = song.clock;
		muted = IdentitySet.new;
		soloed = IdentitySet.new;
		warned = IdentitySet.new;
		failedRoots = IdentitySet.new;
	}

	//////// transport

	// quant: the grid on the song's clock (nil: now); atBeat: an absolute clock
	// beat instead. from / to: score beats (to nil: the end); loop: play
	// [from, to] again and again (to nil: the score's duration).
	play { |quant, from = 0, to, loop = false, atBeat|
		var events;
		if(state == \playing) { this.stop };
		this.prSetSpan(from, to, loop);
		events = this.prSortedEvents;
		fired = 0;
		skipped = 0;
		passes = 0;
		warned.clear;
		failedRoots.clear;
		routine = Routine { this.prRun(events) };
		state = \playing;
		if(atBeat.notNil) { clock.schedAbs(atBeat, routine) } { routine.play(clock, quant) };
	}

	prSetSpan { |fromarg, toarg, looparg|
		from = fromarg ? 0;
		loop = looparg ? false;
		to = toarg ?? { if(loop) { score.duration } { nil } };
		if(loop and: { to <= from }) {
			RCLog.warn(\player, "loop span [%, %] is empty: playing once".format(from, to));
			loop = false;
		};
	}

	prSortedEvents {
		^score.events.asArray.sort { |a, b|
			if(a[\beat] == b[\beat]) { a[\id] <= b[\id] } { a[\beat] <= b[\beat] }
		}
	}

	stop {
		if(state != \playing) { ^this };
		routine !? (_.stop);
		routine = nil;
		state = \stopped;
	}

	isPlaying { ^state == \playing }

	// The score beat the playhead is at, nil when stopped.
	position { ^if(state == \playing and: { startBeat.notNil }) { clock.beats - startBeat + from } { nil } }

	mute { |voices| voices.asArray.do { |v| muted.add(v.asSymbol) } }
	unmute { |voices| if(voices.isNil) { muted.clear } { voices.asArray.do { |v| muted.remove(v.asSymbol) } } }
	solo { |voices| if(voices.isNil) { soloed.clear } { voices.asArray.do { |v| soloed.add(v.asSymbol) } } }

	prRun { |events|
		var idx, lastBeat, len, ev;
		startBeat = thisThread.beats;
		idx = (events.detectIndex { |e| e[\beat] >= from }) ? events.size;
		lastBeat = from;
		len = to !? { to - from };
		while { state == \playing } {
			ev = events[idx];
			if(ev.isNil or: { to.notNil and: { ev[\beat] >= to } }) {
				if(loop) {
					if(to > lastBeat) { (to - lastBeat).wait };
					passes = passes + 1;
					startBeat = startBeat + len;
					idx = (events.detectIndex { |e| e[\beat] >= from }) ? events.size;
					lastBeat = from;
					onLoop.value(this, passes);
				} {
					state = \stopped;
					routine = nil;
					onDone.value(this);
					^this
				};
			} {
				if(ev[\beat] > lastBeat) { (ev[\beat] - lastBeat).wait };
				lastBeat = ev[\beat];
				this.prPlayEvent(ev);
				idx = idx + 1;
			};
		};
	}

	prPlayEvent { |ev|
		var voice = ev[\voice];
		var ok;
		if(voice.notNil and: { muted.includes(voice) or: { soloed.notEmpty and: { soloed.includes(voice).not } } }) { ^this };
		if(ev[\cause].notNil and: { failedRoots.includes(ev[\cause]).not }) { ^this };   // its root played
		ok = this.fire(ev);
		if(ok) {
			fired = fired + 1;
			onEvent.value(this, ev);
		} {
			skipped = skipped + 1;
			if(ev[\cause].isNil) { failedRoots.add(ev[\id]) };
		};
	}

	//////// firing

	// Replays one event now. Returns true when it was fired.
	fire { |ev|
		if(ev[\replay] == false) { ^this.prSkip(ev, "marked replay: false") };
		^RETap.silently {
			this.prBundled {
				switch(ev[\kind],
					\action, { this.prFireAction(ev) },
					\midi, { this.prFireMidi(ev) },
					\osc, { this.prFireOsc(ev) },
					\keyboard, { this.prFireKeyboard(ev) },
					\code, { this.prFireCode(ev) },
					\snapshot, { this.prFireSnapshot(ev) },
					\morph, { this.prFireMorph(ev) },
					\tempo, { if(followTempo) { clock.tempo = ev[\tempo] }; true },
					{ this.prSkip(ev, "unknown kind " ++ ev[\kind]) }
				)
			}
		}
	}

	// With the server running, what func sends goes in a latency bundle.
	prBundled { |func|
		var res;
		if(useLatency and: { song.server.serverRunning }) {
			song.server.bind { res = func.value };
			^res
		};
		^func.value
	}

	prSkip { |ev, why|
		var key = (ev[\kind].asString ++ " " ++ why).asSymbol;
		if(warned.includes(key).not) {
			warned.add(key);
			RCLog.warn(\player, "skipped % event %: %".format(ev[\kind], ev[\id], why));
		};
		^false
	}

	prFireAction { |ev|
		var ref = ev[\rc];
		var obj, args;
		if(ref.isNil) { ^this.prSkip(ev, "no receiver") };
		obj = REScore.resolve(ref, song);
		if(obj.isNil) { ^this.prSkip(ev, "no % named %".format(ref[\rc], ref[\name])) };
		args = REScore.decodeValue(ev[\args], song) ? [];
		^RCGuard.call(\player, false) { obj.performList(ev[\method], args); true }
	}

	prFireMidi { |ev|
		if(song.midi.replay(ev[\name], REScore.decodeValue(ev[\value], song), ev[\raw])) { ^true };
		^this.prSkip(ev, "no mapping named " ++ ev[\name])
	}

	prFireOsc { |ev|
		var def = OSCdef.all[ev[\key].asSymbol];
		var args;
		if(def.isNil or: { def.func.isNil }) { ^this.prSkip(ev, "no OSCdef " ++ ev[\key]) };
		args = REScore.decodeValue(ev[\args], song) ? [];
		^RCGuard.call(\player, false) { def.func.value([ev[\path].asSymbol] ++ args, clock.seconds, nil, nil); true }
	}

	prFireKeyboard { |ev|
		var kb = song.keyboard;
		if(kb.isNil) { ^this.prSkip(ev, "the song has no keyboard") };
		^RCGuard.call(\player, false) {
			switch(ev[\msg],
				\noteOn, { kb.noteOn(ev[\value], ev[\note], ev[\chan]) },
				\noteOff, { kb.noteOff(ev[\value], ev[\note], ev[\chan]) },
				\bend, { kb.bend(ev[\value], ev[\chan]) },
				\touch, { kb.touch(ev[\value], ev[\chan]) },
				\cc, { kb.cc(ev[\value], ev[\num], ev[\chan]) }
			);
			true
		}
	}

	prFireCode { |ev|
		var func = ev[\text].asString.compile;
		if(func.isNil) { ^this.prSkip(ev, "does not compile") };
		^RCGuard.call(\player, false) { func.value; true }
	}

	prFireSnapshot { |ev|
		this.class.applyState(song, ev[\state], 0);
		^true
	}

	prFireMorph { |ev|
		var state = ev[\state] ?? { ev[\snapshot] !? { |id| score.at(id) !? (_[\state]) } };
		if(state.isNil) { ^this.prSkip(ev, "no snapshot to morph to") };
		this.class.applyState(song, state, ev[\beats] ? 0);
		^true
	}

	//////// snapshots

	// Sets what a snapshot recorded: the beats' key sources (a numeric source
	// ramps to a numeric target over `beats` when given), their transport, the
	// orgnsms' static attributes (identity keys aside), the song's seed.
	*applyState { |song, state, beats = 0|
		if(state.isNil) { ^this };
		state[\seed] !? { |s| song.seed = s };
		(state[\beats] ? IdentityDictionary.new).keysValuesDo { |voice, keys|
			var parts = voice.asString.split($/);
			var layer = song.layers[parts[0].asSymbol];
			var b = layer !? { |l| parts[1] !? { |n| l.beat(n.asSymbol) } };
			if(b.isNil) {
				RCLog.warn(\player, "snapshot: no beat %".format(voice));
			} {
				keys.keysValuesDo { |k, v|
					if(k != \playing) {
						v = REScore.decodeValue(v, song);
						if(beats > 0 and: { v.isNumber } and: { b.keyProxy(k).notNil } and: { b.keyProxy(k).source.isNumber }) {
							b.set(k, this.ramp(b.keyProxy(k).source, v, beats), quant: nil);
						} {
							b.set(k, v, quant: nil);
						};
					};
				};
				keys[\playing] !? { |playing|
					if(playing and: { b.isPlaying.not }) { if(b.player.notNil) { b.resume } { b.play } };
					if(playing.not and: { b.isPlaying }) { b.pause };
				};
			};
		};
		(state[\orgnsms] ? IdentityDictionary.new).keysValuesDo { |name, attrs|
			var o = song.registry.all.detect { |x| x.name == name.asSymbol };
			if(o.isNil) {
				RCLog.warn(\player, "snapshot: no orgnsm %".format(name));
			} {
				attrs = REScore.decodeValue(attrs, song);
				attrs.keysValuesDo { |k, v| if(orgnsmIdentityKeys.includes(k).not) { o.rPut(k, v) } };
			};
		};
	}

	// A source that moves from one value to another over `beats`, then holds.
	*ramp { |fromValue, toValue, beats, curve = \lin|
		^Pseq([Pseg([fromValue, toValue], [beats], curve), Pn(toValue, inf)], 1)
	}

	printOn { |stream| stream << "REScorePlayer(" << (score.song ? "?") << ", " << state << ")" }
}
