// reCording — records what you do to a song into an REScore: the inputs
// (MIDI mappings, OSC defs, the keyboard), the actions on its reCurrent
// objects, the code lines of the IDE, snapshots; see the reCording guide
// ("Scores"). One per song, through song.scoreRecorder.
//
//   ~rec = ~song.scoreRecorder(root: ~piece_dir +/+ "Scores", version: "w1");
//   ~rec.arm;                   // or arm(scope: [~song.layer(\core), ~batch], inputs: #[\midi, \actions])
//   ~rec.record([4, 0]);        // the take starts on the next bar (nil: now)
//   ~rec.snapshot(\intro);
//   ~rec.stop;                  // -> the REScore, written under root when there is one
//   ~rec.disarm;
//
// Off (not armed): nothing installed, nothing checked beyond RETap.active.
// Armed: the taps reach it, nothing is kept. Recording: events are appended,
// the file is written at stop. The recorder only observes: no random draw,
// no clock scheduling (the quantized start is one Function on the song's
// clock), no server message.

REScoreRecorder {
	classvar <>allInputs;
	classvar <>unsafeCode;   // a code line containing one of these is kept but not replayed

	var <song, <>root, <>version, <>piece;
	var <state = \off;      // \off, \armed, \recording
	var <scope, <inputs, <voiceOf, <voices;
	var <score, <lastScore, <beat0, <time0, lastTempo;
	var <>startSnapshot = true;
	var <player, overdubbing;

	*initClass {
		allInputs = #[\midi, \osc, \keyboard, \actions, \code];
		unsafeCode = #["exit", "recompile", "boot", "quit", "unixCmd", "systemCmd", "shutdown"];
	}

	*new { |song, root, version = "", piece|
		^super.new.initREScoreRecorder(song, root, version, piece)
	}

	initREScoreRecorder { |songarg, rootarg, versionarg, piecearg|
		song = songarg;
		root = rootarg !? (_.asString);
		version = versionarg.asString;
		piece = piecearg !? (_.asString);
		voiceOf = IdentityDictionary.new;
		voices = IdentityDictionary.new;
	}

	clock { ^song.clock }
	isArmed { ^state != \off }
	isRecording { ^state == \recording }

	//////// arming

	// scope: nil (the whole song), or objects (layers, beats, batches, orgnsms,
	// fobjects, crawlers, the song's midi/osc/keyboard) and mapping or def
	// names; inputs: kinds of event, all by default; voices: name → the objects
	// and names grouped under it.
	arm { |scope, inputs, voices|
		this.prSetScope(scope);
		this.prSetInputs(inputs);
		this.prSetVoices(voices);
		if(state == \off) { state = \armed };
		RETap.add(this);
		if(this.inputs.includes(\code)) { RETap.enableCode(this) } { RETap.disableCode(this) };
		RCLog.post(\score, "% armed: %".format(song.name, this.inputs.asArray.sort { |a, b| a.asString <= b.asString }));
	}

	disarm {
		if(state == \recording) { this.stop };
		RETap.disableCode(this);
		RETap.remove(this);
		score = nil;
		state = \off;
		RCLog.post(\score, "% disarmed".format(song.name));
	}

	free { this.disarm }

	prSetScope { |scopearg|
		scope = scopearg !? { |s| IdentitySet.newFrom(s.asArray.collect { |x| if(x.isKindOf(String)) { x.asSymbol } { x } }) };
	}

	prSetInputs { |inputsarg|
		inputs = IdentitySet.newFrom((inputsarg ? allInputs).asArray.collect(_.asSymbol));
		inputs.do { |k| if(allInputs.includes(k).not) { RCLog.warn(\score, "unknown input kind % (one of %)".format(k, allInputs)) } };
	}

	prSetVoices { |voicesarg|
		voiceOf.clear;
		voices.clear;
		voicesarg !? { |dict|
			dict.keysValuesDo { |voice, members|
				voice = voice.asSymbol;
				members = members.asArray;
				members.do { |m| voiceOf[if(m.isKindOf(String)) { m.asSymbol } { m }] = voice };
				voices[voice] = IdentityDictionary[\objects -> members.collect { |m| REScore.rcRef(m) ?? { m.asString } }];
			};
		};
	}

	//////// recording

	// quant: nil → now; else that grid on the song's clock (one Function
	// scheduled); atBeat: an absolute clock beat instead. offsetBeat: the
	// score beat of the start (an overdub from the middle of a score).
	record { |quant, snapshotAtStart, atBeat, offsetBeat = 0|
		if(state == \off) { this.arm };
		if(state == \recording) { RCLog.warn(\score, "% already recording".format(song.name)); ^this };
		snapshotAtStart !? { |b| startSnapshot = b };
		score = REScore(song, piece, version);
		case
		{ atBeat.notNil } { this.clock.schedAbs(atBeat, { this.prStart(offsetBeat); nil }) }
		{ quant.isNil } { this.prStart(offsetBeat) }
		{
			this.clock.schedAbs(quant.asQuant.nextTimeOnGrid(this.clock), { this.prStart(offsetBeat); nil });
			RCLog.post(\score, "% recording at the next %".format(song.name, quant));
		};
	}

	prStart { |offsetBeat = 0|
		var c = this.clock;
		if(state != \armed or: { score.isNil }) { ^this };
		beat0 = c.beats - offsetBeat;
		time0 = c.seconds - (offsetBeat * c.beatDur);   // the time of the score's beat 0
		lastTempo = c.tempo;
		score.meta[\beat0] = beat0;
		score.meta[\time0] = time0;
		score.meta[\tempo] = lastTempo;
		score.meta[\latency] = song.server.latency;
		score.tempoMap.add([0, lastTempo]);
		voices.keysValuesDo { |k, v| score.voices[k] = v };
		state = \recording;
		if(startSnapshot) { this.snapshot(\start) };
		RCLog.post(\score, "% recording from beat %".format(song.name, beat0));
	}

	// Ends the take: the REScore, written to <root>/<stamp>_<song> <version>.json
	// when root is set. An overdub ends with the merged score instead (the
	// original file stays, the merge is a new one).
	stop {
		var s = score, od = overdubbing, stopBeat;
		if(state != \recording) { RCLog.warn(\score, "% is not recording".format(song.name)); ^nil };
		stopBeat = this.clock.beats - beat0;
		s.meta[\duration] = stopBeat;
		s.meta[\commits] = this.prCommits;
		state = \armed;
		score = nil;
		overdubbing = nil;
		if(od.notNil) {
			player.stop;
			s = RCGuard.call(\score, s) {
				var punch = [od[\from], od[\to] ? stopBeat];
				REScore.merge(od[\score], s, od[\voices], od[\mode], punch, if(od[\loop]) { punch } { nil })
			};
		};
		lastScore = s;
		if(root.notNil) {
			RCGuard.call(\score, nil) { s.write(REScore.pathFor(root, song.name, version)) };
		} {
			RCLog.warn(\score, "no root folder: the score was not written (lastScore holds it: lastScore.write(path))");
		};
		RCLog.post(\score, "% stopped: % events over % beats".format(song.name, s.size, s.duration.round(0.01)));
		^s
	}

	// Plays `score` on the song while recording a new take over it; stop merges
	// the two (REScore.merge) and writes the merge. voices: the voices
	// overdubbed (nil: all); mode: \replace (their old events are muted from
	// the punch-in and gone from the merge), \keep (added), \touch (replaced
	// per control while touched), nil (touch for continuous controls, replace
	// for the rest); punch: [in, out] in score beats (nil: from the start to
	// the stop); loop: [in, out] again and again, each pass an overdub; quant:
	// both start on that grid of the song's clock (nil: now).
	overdub { |scorearg, voices, mode, punch, loop = false, quant|
		var from, to, at;
		if(state == \recording) { RCLog.warn(\score, "% already recording".format(song.name)); ^this };
		if(state == \off) { this.arm };
		from = punch !? (_[0]) ? 0;
		to = punch !? (_[1]);
		if(loop and: { to.isNil }) { to = scorearg.duration };
		at = quant !? { |q| q.asQuant.nextTimeOnGrid(this.clock) } ?? { this.clock.beats };
		player = REScorePlayer(scorearg, song);
		if(mode == \replace) { player.mute(voices ?? { scorearg.voiceNames }) };
		overdubbing = IdentityDictionary[\score -> scorearg, \voices -> voices, \mode -> mode, \from -> from, \to -> to, \loop -> loop];
		this.record(nil, false, atBeat: at, offsetBeat: from);
		player.play(nil, from, to, loop, atBeat: at);
		RCLog.post(\score, "% overdubbing % from beat %".format(song.name, voices ? "every voice", from));
	}

	// Sets the state of a snapshot (by name, the latest one of the current
	// take, else of the last score) at once, or over `beats`; recorded as a
	// morph event (beats 0: a recall).
	morph { |name, beats = 0|
		var source = score ? lastScore;
		var snap = source !? { |s| s.events.reverse.detect { |e| e[\kind] == \snapshot and: { e[\name] == name.asSymbol } } };
		var ev;
		if(snap.isNil) { RCLog.warn(\score, "no snapshot named %".format(name)); ^nil };
		REScorePlayer.applyState(song, snap[\state], beats);
		if(state != \recording) { ^nil };
		ev = this.prEvent(\morph, nil);
		ev[\name] = name.asSymbol;
		ev[\beats] = beats;
		if(source === score) { ev[\snapshot] = snap[\id] } { ev[\state] = snap[\state] };
		^score.add(ev)
	}

	recall { |name| ^this.morph(name, 0) }

	prCommits {
		var res = IdentityDictionary.new;
		REScore.gitHead(REScore.filenameSymbol.asString.dirname) !? { |h| res[\vikn] = h };
		root !? { |r| REScore.gitHead(r) !? { |h| res[\homeware] = h } };
		^res
	}

	// The state of what is in scope, under name: the sources of every key of
	// the beats, the static attributes of the orgnsms, the song's seed.
	snapshot { |name = \snapshot|
		var ev, st;
		if(state != \recording) { RCLog.warn(\score, "% is not recording: no snapshot".format(song.name)); ^nil };
		st = IdentityDictionary.new;
		st[\seed] = song.seed;
		st[\beats] = IdentityDictionary.new;
		song.allBeats.do { |b|
			if(b.isFreed.not and: { this.prInScope(b) }) { st[\beats][this.prBeatVoice(b)] = this.prBeatState(b) };
		};
		st[\orgnsms] = IdentityDictionary.new;
		song.registry.all.do { |o|
			if(o.isFreed.not and: { this.prInScope(o) }) { st[\orgnsms][o.name] = REScore.encodeValue(o.staticAttrs) };
		};
		ev = this.prEvent(\snapshot, nil);
		ev[\name] = name.asSymbol;
		ev[\state] = st;
		^score.add(ev)
	}

	prBeatState { |b|
		var res = IdentityDictionary.new;
		b.keyOrder.do { |k| b.keyProxy(k) !? { |p| res[k] = REScore.encodeValue(p.source) } };
		b.realDur !? { |d| res[\real_dur] = REScore.encodeValue(d) };
		res[\playing] = b.isPlaying;
		^res
	}

	//////// taps (RETap, main thread, guarded)

	prEvent { |kind, voice|
		var c = this.clock;
		var ev = IdentityDictionary.new;
		var tempo = c.tempo;
		if(tempo != lastTempo) {
			lastTempo = tempo;
			score.tempoMap.add([c.beats - beat0, tempo]);
			score.add(IdentityDictionary[\beat -> (c.beats - beat0), \secs -> (c.seconds - time0), \kind -> \tempo, \tempo -> tempo]);
		};
		ev[\beat] = c.beats - beat0;
		ev[\secs] = c.seconds - time0;
		ev[\kind] = kind;
		voice !? { ev[\voice] = voice };
		^ev
	}

	tapAction { |obj, method, args, frame|
		var ev, enc;
		if(state != \recording or: { inputs.includes(\actions).not }) { ^this };
		if(this.prInScope(obj).not) { ^this };
		enc = REScore.encode(args);
		ev = this.prEvent(\action, this.prVoiceFor(obj));
		ev[\rc] = REScore.rcRef(obj) ?? { REScore.encodeValue(obj) };
		ev[\method] = method;
		ev[\args] = enc[0];
		if(enc[1].not) { ev[\replay] = false };
		frame !? { |f| f[\ids][this] !? { |id| ev[\cause] = id } };
		score.add(ev);
	}

	tapInput { |kind, inputSong, data, parentFrame, frame|
		var ev, name;
		if(state != \recording or: { inputs.includes(kind).not }) { ^this };
		if(inputSong !== song) { ^this };
		name = data[\name] ?? { data[\key] } ?? { data[\device] };
		if(this.prInputInScope(kind, name).not) { ^this };
		ev = this.prEvent(kind, this.prVoiceForName(name ? kind));
		data.keysValuesDo { |k, v| ev[k] = REScore.encodeValue(v) };
		parentFrame !? { |f| f[\ids][this] !? { |id| ev[\cause] = id } };
		frame[\ids][this] = score.add(ev);
	}

	tapCode { |text, parentFrame, frame|
		var ev;
		if(state != \recording or: { inputs.includes(\code).not }) { ^this };
		ev = this.prEvent(\code, this.prVoiceForName(\code));
		ev[\text] = text;
		if(unsafeCode.any { |pat| text.contains(pat) }) { ev[\replay] = false };
		parentFrame !? { |f| f[\ids][this] !? { |id| ev[\cause] = id } };
		frame[\ids][this] = score.add(ev);
	}

	dropCode { |frame|
		frame[\ids][this] !? { |id| score !? { |s| s.remove(id) } };
	}

	//////// scope and voices

	prInScope { |obj|
		if(scope.isNil) { ^true };
		^this.prOwners(obj).any { |o| scope.includes(o) }
	}

	// The object and what holds it.
	prOwners { |obj|
		case
		{ obj.isKindOf(RCBeat) } { ^[obj, obj.layer, obj.song] }
		{ obj.isKindOf(RCLayer) } { ^[obj, obj.song] }
		{ obj.isKindOf(RCOrgnsm) } { ^[obj, obj.batch, obj.layer, obj.song].reject(_.isNil) }
		{ obj.isKindOf(RCBatch) } { ^[obj, obj.song] }
		{ obj.isKindOf(RCFObject) } { ^[obj, obj.song] }
		{ obj.isKindOf(RCCrawler) } { ^[obj, obj.batch].reject(_.isNil) }
		{ ^[obj] };
	}

	prInputInScope { |kind, name|
		var surface;
		if(scope.isNil) { ^true };
		if(name.notNil and: { scope.includes(name.asSymbol) }) { ^true };
		surface = switch(kind, \midi, { song.midi }, \osc, { song.osc }, \keyboard, { song.keyboard });
		^(surface.notNil and: { scope.includes(surface) }) or: { scope.includes(song) }
	}

	prVoiceFor { |obj|
		^voiceOf[obj] ?? {
			case
			{ obj.isKindOf(RCBeat) } { this.prBeatVoice(obj) }
			{ obj.isKindOf(RCLayer) } { obj.key }
			{ obj.isKindOf(RCSong) } { obj.name }
			{ obj.isKindOf(RCBatch) } { obj.name }
			{ obj.isKindOf(RCOrgnsm) } { obj.batch !? (_.name) ?? { obj.name } }
			{ obj.isKindOf(RCFObject) } { (obj.name ? obj.synthDefName).asSymbol }
			{ obj.isKindOf(RCCrawler) } { obj.batch !? (_.name) ? \crawler }
			{ obj.class.name }
		}
	}

	prBeatVoice { |b| ^(b.layer.key.asString ++ "/" ++ b.name).asSymbol }

	prVoiceForName { |name| ^voiceOf[name.asSymbol] ?? { name.asSymbol } }

	printOn { |stream| stream << "REScoreRecorder(" << song.name << ", " << state << ")" }
}
