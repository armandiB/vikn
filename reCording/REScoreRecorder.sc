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

	// quant: nil → now; else that grid on the song's clock (one Function scheduled).
	record { |quant, snapshotAtStart|
		if(state == \off) { this.arm };
		if(state == \recording) { RCLog.warn(\score, "% already recording".format(song.name)); ^this };
		snapshotAtStart !? { |b| startSnapshot = b };
		score = REScore(song, piece, version);
		if(quant.isNil) {
			this.prStart;
		} {
			this.clock.schedAbs(quant.asQuant.nextTimeOnGrid(this.clock), { this.prStart; nil });
			RCLog.post(\score, "% recording at the next %".format(song.name, quant));
		};
	}

	prStart {
		var c = this.clock;
		if(state != \armed or: { score.isNil }) { ^this };
		beat0 = c.beats;
		time0 = c.seconds;
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

	// Ends the take: the REScore, written to <root>/<stamp>_<song> <version>.json when root is set.
	stop {
		var s = score;
		if(state != \recording) { RCLog.warn(\score, "% is not recording".format(song.name)); ^nil };
		s.meta[\duration] = this.clock.beats - beat0;
		s.meta[\commits] = this.prCommits;
		state = \armed;
		score = nil;
		lastScore = s;
		if(root.notNil) {
			RCGuard.call(\score, nil) { s.write(REScore.pathFor(root, song.name, version)) };
		} {
			RCLog.warn(\score, "no root folder: the score was not written (lastScore holds it: lastScore.write(path))");
		};
		RCLog.post(\score, "% stopped: % events over % beats".format(song.name, s.size, s.duration.round(0.01)));
		^s
	}

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
