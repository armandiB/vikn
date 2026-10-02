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
// found, its code raises), the effects recorded under it play instead, but
// not the ones the root did again before failing (a code line's actions are
// watched: what ran is not played twice); fallback: true on the event plays
// them all, fallback: false none. What cannot play is skipped with one
// warning each. A replay starts on the take's own phase of the grid
// (alignedStart), so what the take quantized lands as it did. A deferred
// voice (defer) or event (defer: true) is not fired: its effects play, and
// the program's events under it (level 2) when the take holds them.

REScorePlayer {
	classvar <orgnsmIdentityKeys;

	var <score, <song, <>clock;
	var <state = \stopped, routine, <startBeat, <from = 0, <to, <loop = false, <passes = 0;
	var <muted, <soloed, <deferred;
	var <>onEvent, <>onLoop, <>onDone;
	var <>followTempo = false, <>useLatency = true;
	var <>interpolate = false, <>stepsPerBeat = 16;   // a continuous control ramps to its next point (off: the points, as played)
	var <>alignPhase = true;   // play starts on the take's phase of the grid (quant, else the take's own, else the beat)
	var <>playOrphans = false;   // program events with no cause (level 2, from nothing a human did) play too
	var <fired = 0, <skipped = 0, <interpolated = 0, <deferredCount = 0, warned, failedRoots, ranBeforeFail, ramps, nextOf, commitsChecked = false;

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
		deferred = IdentitySet.new;
		warned = IdentitySet.new;
		failedRoots = IdentitySet.new;
		ranBeforeFail = IdentityDictionary.new;
		ramps = List.new;
		nextOf = IdentityDictionary.new;
	}

	//////// transport

	// quant: the grid on the song's clock, the start on the take's own phase of it
	// (alignPhase; nil: the take's grid, else the beat); atBeat: an absolute clock
	// beat instead. from / to: score beats (to nil: the end); loop: play
	// [from, to] again and again (to nil: the score's duration).
	play { |quant, from = 0, to, loop = false, atBeat|
		var events;
		if(state == \playing) { this.stop };
		this.prSetSpan(from, to, loop);
		events = this.prSortedEvents;
		fired = 0;
		skipped = 0;
		interpolated = 0;
		deferredCount = 0;
		passes = 0;
		warned.clear;
		failedRoots.clear;
		ranBeforeFail.clear;
		ramps.clear;
		this.prIndexControls(events);
		this.prCheckCommits;
		routine = Routine { this.prRun(events) };
		// the take's random state (the main thread's at its start): a replayed line that draws
		// draws what it drew, as long as the same lines and inputs run in the same order
		score.meta[\randData] !? { |d| RCGuard.call(\player, nil) { routine.randData = Int32Array.newFrom(d) } };
		state = \playing;
		case
		{ atBeat.notNil } { clock.schedAbs(atBeat, routine) }
		{ alignPhase } { clock.schedAbs(this.class.alignedStart(score, clock, quant), routine) }
		{ routine.play(clock, quant) };
	}

	// The next beat on the grid (quant's, else the take's own, else 1) with the take's
	// phase of it (beat0 mod the grid): a line or a Routine the take quantized to that grid
	// lands at the same place.
	*alignedStart { |score, clock, quant|
		var grid = quant !? { |q| q.asQuant.quant } ?? { score.meta[\quant] } ? 1;
		var phase;
		if(grid.isNumber.not or: { grid <= 0 }) { ^clock.beats };
		phase = score.meta[\beat0] !? { |b| b mod: grid } ? 0;
		^Quant(grid, phase).nextTimeOnGrid(clock)
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
	// A deferred voice's sources are not fired: their effects play, and the program events under
	// them when the take holds its level 2 (an event can say so itself: defer: true).
	defer { |voices| voices.asArray.do { |v| deferred.add(v.asSymbol) } }
	undefer { |voices| if(voices.isNil) { deferred.clear } { voices.asArray.do { |v| deferred.remove(v.asSymbol) } } }

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
					this.prWaitUntil(to, lastBeat);
					passes = passes + 1;
					startBeat = startBeat + len;
					idx = (events.detectIndex { |e| e[\beat] >= from }) ? events.size;
					lastBeat = from;
					ramps.clear;
					onLoop.value(this, passes);
				} {
					state = \stopped;
					routine = nil;
					onDone.value(this);
					^this
				};
			} {
				this.prWaitUntil(ev[\beat], lastBeat);
				lastBeat = ev[\beat];
				this.prPlayEvent(ev);
				idx = idx + 1;
			};
		};
	}

	// Waits until `beat` on the clock; on the way, every stepsPerBeat, the controls on a
	// ramp get their interpolated values.
	prWaitUntil { |beat, lastBeat|
		var now = lastBeat;
		var step = 1 / stepsPerBeat;
		while { ramps.notEmpty and: { (beat - now) > step } } {
			step.wait;
			now = now + step;
			this.prRampStep(now);
		};
		if(beat > now) { (beat - now).wait };
	}

	prRampStep { |now|
		ramps.copy.do { |r|
			var a = r[\from], b = r[\to];
			var t;
			if(now >= b[\beat]) {
				ramps.remove(r);
			} {
				t = ((now - a[\beat]) / (b[\beat] - a[\beat])).clip(0, 1);
				if(this.fire(this.prInterpolated(a, b, t))) { interpolated = interpolated + 1 };
			};
		};
	}

	// The next point of every continuous input control (a MIDI, OSC, raw MIDI or keyboard
	// value: what a knob or a slider sent), for the ramps. An action on an object (a beat's
	// key source) is not ramped: each step would be an edit of a pattern.
	prIndexControls { |events|
		var last = Dictionary.new;
		nextOf.clear;
		events.do { |e|
			var k;
			if(#[\midi, \osc, \rawMidi, \keyboard].includes(e[\kind]) and: { REScore.isContinuous(e) }) {
				k = REScore.controlKey(e);
				last[k] !? { |prev| nextOf[prev[\id]] = e };
				last[k] = e;
			};
		};
	}

	// A copy of `a` with its value `t` of the way to `b`'s, along the control's spec when
	// the score carries one (score.controls, by the event's key or name), else linear.
	prInterpolated { |a, b, t|
		var va = REScore.controlValue(a), vb = REScore.controlValue(b);
		var spec = score.controls[a[\key]] ?? { score.controls[a[\name]] };
		var cs = spec !? { this.class.controlSpecFor(spec) };
		var v = if(cs.notNil) { cs.map(cs.unmap(va) + ((cs.unmap(vb) - cs.unmap(va)) * t)) } { va + ((vb - va) * t) };
		var ev = REScore.withControlValue(a, v);
		ev[\interpolated] = true;
		^ev
	}

	prPlayEvent { |ev|
		var voice = ev[\voice];
		var ok, next;
		if(voice.notNil and: { muted.includes(voice) or: { soloed.notEmpty and: { soloed.includes(voice).not } } }) { ^this };
		if((ev[\level] ? 1) >= 2) {
			if(this.prProgramPlays(ev).not) { ^this };
		} {
			if(ev[\cause].notNil) {
				if(failedRoots.includes(ev[\cause]).not) { ^this };   // its root played
				if(this.prRootDidIt(ev)) { ^this };                    // its root did it again before failing
			} {
				if(this.prDeferred(ev)) {                              // not fired: its effects play instead
					failedRoots.add(ev[\id]);
					deferredCount = deferredCount + 1;
					^this
				};
			};
		};
		ramps.copy.do { |r| if(r[\to] === ev) { ramps.remove(r) } };
		ok = this.fire(ev);
		if(ok) {
			fired = fired + 1;
			onEvent.value(this, ev);
			if(interpolate) {
				next = nextOf[ev[\id]];
				if(next.notNil and: { (next[\beat] - ev[\beat]) > (1 / stepsPerBeat) } and: { REScore.controlValue(next) != REScore.controlValue(ev) }) {
					ramps.add(IdentityDictionary[\from -> ev, \to -> next]);
				};
			};
		} {
			skipped = skipped + 1;
			if(ev[\cause].isNil) { this.prRootFailed(ev) };
		};
	}

	// A root that could not play: its effects play instead, but not the ones it did again
	// before failing (prFireCode counts a line's actions); fallback: false on the event asks
	// for none, fallback: true for all of them.
	prRootFailed { |ev|
		switch(ev[\fallback],
			false, { this.prWarnOnce(("nofallback_" ++ ev[\id]).asSymbol, "% event % failed: its effects are not played (fallback: false)".format(ev[\kind], ev[\id])) },
			true, { failedRoots.add(ev[\id]); ranBeforeFail.removeAt(ev[\id]) },
			{
				failedRoots.add(ev[\id]);
				ranBeforeFail[ev[\id]] !? { |n|
					if(n > 0) { this.prWarnOnce(("partly_" ++ ev[\id]).asSymbol, "% event % failed after % action(s): those are not played again, the rest of its effects are".format(ev[\kind], ev[\id], n)) };
				};
			}
		);
	}

	// The effect's place among its root's effects (by id, as recorded) is below what the
	// root did again before failing.
	prRootDidIt { |ev|
		var n = ranBeforeFail[ev[\cause]];
		var effects;
		if(n.isNil or: { n <= 0 }) { ^false };
		effects = score.causedBy(ev[\cause]).sort { |a, b| a[\id] <= b[\id] };
		^(effects.indexOf(ev) ? inf) < n
	}

	prDeferred { |ev| ^ev[\defer] == true or: { ev[\voice].notNil and: { deferred.includes(ev[\voice]) } } }

	// A program event (level 2) plays when the level 1 event it descends from was deferred or
	// failed without doing anything first (else the program does its work itself); one with
	// no root only when playOrphans is set.
	prProgramPlays { |ev|
		var root = score.rootOf(ev);
		if(root.isNil) { ^playOrphans };
		^failedRoots.includes(root[\id]) and: { (ranBeforeFail[root[\id]] ? 0) == 0 }
	}

	prWarnOnce { |key, text|
		if(warned.includes(key).not) {
			warned.add(key);
			RCLog.warn(\player, text);
		};
	}

	// The take was recorded with other code: said once per player.
	prCheckCommits {
		var commits = score.meta[\commits];
		var now, mismatches = List.new;
		if(commits.isNil or: { commitsChecked }) { ^this };
		commitsChecked = true;
		now = IdentityDictionary[\vikn -> REScore.gitHead(REScore.filenameSymbol.asString.dirname)];
		score.meta[\root] !? { |r| now[\homeware] = REScore.gitHead(r) };
		commits.keysValuesDo { |k, v| now[k] !? { |h| if(h != v) { mismatches.add("% % (now %)".format(k, v, h)) } } };
		if(mismatches.notEmpty) {
			RCLog.warn(\player, "the take was recorded with other code: %".format(mismatches.join(", ")));
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
					\rawMidi, { this.prFireRawMidi(ev) },
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

	// Dispatched through MIDIIn as the device would: every MIDIdef and MIDIFunc fires as it
	// did (the recorder's own raw hooks are silent meanwhile). The source is the device of
	// the same name when present, else the recorded uid.
	prFireRawMidi { |ev|
		var src = RETap.deviceUid(ev[\device]) ? ev[\src] ? 0;
		var chan = ev[\chan] ? 0, num = ev[\num] ? 0, val = ev[\value] ? 0;
		^RCGuard.call(\player, false) {
			switch(ev[\msg],
				\noteOn, { MIDIIn.doNoteOnAction(src, chan, num, val) },
				\noteOff, { MIDIIn.doNoteOffAction(src, chan, num, val) },
				\control, { MIDIIn.doControlAction(src, chan, num, val) },
				\bend, { MIDIIn.doBendAction(src, chan, val) },
				\touch, { MIDIIn.doTouchAction(src, chan, val) },
				\polytouch, { MIDIIn.doPolyTouchAction(src, chan, num, val) },
				\program, { MIDIIn.doProgramAction(src, chan, val) }
			);
			true
		}
	}

	// The line's actions are watched (RETap.observe): when it raises, the count of what it did
	// first decides which of its recorded effects still play (prRootDidIt).
	prFireCode { |ev|
		var func = ev[\text].asString.compile;
		var ran = 0, ok;
		if(func.isNil) { ^this.prSkip(ev, "does not compile") };
		ok = RETap.observe({ ran = ran + 1 }) { RCGuard.call(\player, false) { func.value; true } };
		if(ok.not and: { ev[\id].notNil }) { ranBeforeFail[ev[\id]] = ran };
		^ok
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
		// the registered controls (REScoreRecorder.addControl with set:): at once, or ramped
		// along their spec's warp
		state[\controls] !? { |ctls|
			var rec = song.scoreRecorder;
			ctls.keysValuesDo { |name, v|
				var set = rec.controlSetter(name), get = rec.controlGetter(name), from;
				v = REScore.decodeValue(v, song);
				if(set.isNil) {
					RCLog.warn(\player, "snapshot: no setter for the control %".format(name));
				} {
					from = get !? { RCGuard.call(\player, nil) { get.value } };
					if(beats > 0 and: { v.isNumber } and: { from.isNumber }) {
						this.rampControl(set, from, v, beats, rec.controls[name], song.clock);
					} {
						RCGuard.call(\player, nil) { set.value(v) };
					};
				};
			};
		};
	}

	// A source that moves from one value to another over `beats`, then holds.
	*ramp { |fromValue, toValue, beats, curve = \lin|
		^Pseq([Pseg([fromValue, toValue], [beats], curve), Pn(toValue, inf)], 1)
	}

	// set.value called stepsPerBeat times a beat from one value to another, along the
	// spec's warp (a dictionary with min, max, warp; nil: linear), on clock.
	*rampControl { |set, from, to, beats, spec, clock, stepsPerBeat = 16|
		var steps = max(2, (beats * stepsPerBeat).round.asInteger);
		var cs = spec !? { this.controlSpecFor(spec) };
		var u0 = cs !? (_.unmap(from)) ? from, u1 = cs !? (_.unmap(to)) ? to;
		^Routine {
			steps.do { |i|
				var u = u0 + ((u1 - u0) * ((i + 1) / steps));
				(beats / steps).wait;
				RCGuard.call(\player, nil) { set.value(cs !? (_.map(u)) ? u) };
			};
		}.play(clock ? TempoClock.default)
	}

	// A ControlSpec from a take's spec dictionary (min, max, warp: "lin", "exp" or a number).
	*controlSpecFor { |spec|
		var warp = spec[\warp] ? \lin;
		var min = spec[\min] ? 0, max = spec[\max] ? 1;
		if(warp.isNumber.not) { warp = warp.asSymbol };
		if(warp == \exp and: { min <= 0 or: { max <= 0 } }) { warp = \lin };
		^ControlSpec(min, max, warp)
	}

	printOn { |stream| stream << "REScorePlayer(" << (score.song ? "?") << ", " << state << ")" }
}
