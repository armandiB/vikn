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
	classvar <>serverCommands;   // the server messages level 3 keeps (what makes sound: nodes, groups, buses, buffers, defs by path)
	classvar <>unsafeCode;   // a code line containing one of these is kept but not replayed
	classvar <>maxLag = 10;  // seconds: an input stamped earlier than that by its sender is stamped now
	classvar <>loopbackWindow = 0.2;   // seconds: an OSC input this soon after a line that sends OSC to this song is that line's message

	var <song, <>root, <>version, <>piece;
	var <state = \off;      // \off, \armed, \recording
	var <scope, <inputs, <voiceOf, <voices;
	var <>voicesByLayer = false;   // a beat's default voice: its layer (true) or layer/name (false)
	var <controls;                 // name → spec (min, max, warp, default, unit), written into every take
	var controlGetters, controlSetters;   // name → { value } / { |value| }: snapshots and recalls of registered controls
	var <score, <lastScore, <lastPath, <beat0, <time0, lastTempo;
	var recordQuant, loopbackLine;
	var <>level2 = false;   // record the program's work too (level 2, the companion file): off, it is only counted
	var <>level3 = false;   // record the server's messages too (level 3, <take>.l3.json, the defs next to it): off, nothing is installed
	var tempDefs;           // level 3: the bytes of the defs sent during the take (a NodeProxy's temp def) by name, written next to it at stop
	var savedAddr;          // the server's address while an RETapAddr stands in for it (level 3, recording)
	var unrecorded = 0;     // program actions seen on the main thread while level 2 is off
	var <lastCheck;         // what threatened the last take's exact replay (REScore.check)
	var <>rateGuard;        // events per second a doer may write at level 2 before it is sub-sampled (nil: no guard)
	var <>rateQuantum = 0.0625;   // beats: under the guard, one event per object, method and key per quantum
	var rateCounts, rateKept, rateWarned;
	var <>startSnapshot = true;
	var <player, overdubbing;
	var <>onEvent;   // { |recorder, event| } after each recorded event (a view's live feed)
	var <>onStart, <>onStop;   // { |recorder| } as a take starts, { |recorder, score, path| } as it stops (written or not)

	*initClass {
		allInputs = #[\midi, \osc, \keyboard, \actions, \code, \rawMidi];
		unsafeCode = #["exit", "recompile", "boot", "quit", "unixCmd", "systemCmd", "shutdown"];
		serverCommands = IdentitySet.newFrom(#['/s_new', '/s_newargs', '/n_set', '/n_setn', '/n_fill', '/n_map', '/n_mapn', '/n_mapa', '/n_mapan',
			'/n_free', '/n_run', '/n_before', '/n_after', '/n_order', '/g_new', '/g_head', '/g_tail', '/g_freeAll', '/g_deepFree', '/p_new',
			'/c_set', '/c_setn', '/c_fill', '/b_alloc', '/b_allocRead', '/b_allocReadChannel', '/b_read', '/b_readChannel', '/b_write',
			'/b_free', '/b_close', '/b_zero', '/b_set', '/b_setn', '/b_fill', '/b_gen', '/d_load', '/d_loadDir', '/d_free']);
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
		controls = IdentityDictionary.new;
		controlGetters = IdentityDictionary.new;
		controlSetters = IdentityDictionary.new;
	}

	clock { ^song.clock }
	isArmed { ^state != \off }
	isRecording { ^state == \recording }

	//////// arming

	// scope: nil (the whole song), or objects (layers, beats, batches, orgnsms,
	// fobjects, crawlers, the song's midi/osc/keyboard) and mapping, def or
	// device names; inputs: kinds of event, every one but \rawMidi by default
	// (\rawMidi, every MIDI message of every device as sent, replaces \midi
	// and \keyboard: their mappings fire again when the raw messages replay);
	// voices: name → the objects and names grouped under it (nil keeps the
	// voices added so far, an empty Event clears them); voicesByLayer: a
	// beat's default voice is its layer; level2: record the program's work
	// too (false: counted, reported by the check); level3: the server's
	// messages too (an RETapAddr stands in for the server's address while a
	// take records, the defs it names are written next to the take).
	arm { |scope, inputs, voices, voicesByLayer, level2, level3|
		this.prSetScope(scope);
		this.prSetInputs(inputs);
		voices !? { this.prSetVoices(voices) };
		voicesByLayer !? { |b| this.voicesByLayer = b };
		level2 !? { |b| this.level2 = b };
		level3 !? { |b| this.level3 = b };
		if(state == \off) { state = \armed };
		RETap.add(this);
		if(this.inputs.includes(\code)) { RETap.enableCode(this) } { RETap.disableCode(this) };
		if(this.inputs.includes(\rawMidi)) { RETap.enableRawMidi(this) } { RETap.disableRawMidi(this) };
		RCLog.post(\score, "% armed: %".format(song.name, this.inputs.asArray.sort { |a, b| a.asString <= b.asString }));
	}

	disarm {
		if(state == \recording) { this.stop };
		this.prRemoveServerTap;
		RETap.disableCode(this);
		RETap.disableRawMidi(this);
		RETap.remove(this);
		score = nil;
		state = \off;
		RCLog.post(\score, "% disarmed".format(song.name));
	}

	// A voice of your own: objects and names (mappings, defs, devices) grouped
	// under a name, added to the voices so far (a member moves to the new voice).
	addVoice { |name, members|
		var d = ();
		d[name.asSymbol] = members;
		this.prAddVoices(d);
	}

	removeVoice { |name|
		name = name.asSymbol;
		voiceOf.keys.copy.do { |k| if(voiceOf[k] == name) { voiceOf.removeAt(k) } };
		voices.removeAt(name);
	}

	// The voice an object or a name would be recorded under.
	voiceFor { |objOrName|
		^if(objOrName.isKindOf(Symbol) or: { objOrName.isKindOf(String) }) { this.prVoiceForName(objOrName) } { this.prVoiceFor(objOrName) }
	}

	// A registered control: its spec goes into every take (name → (min:, max:, warp:,
	// default:, unit:); a ControlSpec is accepted), a view scales its curve by it, a morph
	// ramps along its warp; with `get` ({ value }) a snapshot holds its value, with `set`
	// ({ |value| }) a recall or a morph brings it back.
	addControl { |name, spec, get, set|
		name = name.asSymbol;
		spec !? {
			controls[name] = if(spec.isKindOf(ControlSpec)) {
				IdentityDictionary[\min -> spec.minval, \max -> spec.maxval, \warp -> spec.warp.asSpecifier.asString, \default -> spec.default, \unit -> spec.units.asString]
			} { spec };
		};
		get !? { controlGetters[name] = get };
		set !? { controlSetters[name] = set };
	}

	controlGetter { |name| ^controlGetters[name.asSymbol] }
	controlSetter { |name| ^controlSetters[name.asSymbol] }
	controlNames { ^controlGetters.keys.asArray.sort { |a, b| a.asString <= b.asString } }

	free { this.disarm }

	prSetScope { |scopearg|
		scope = scopearg !? { |s| IdentitySet.newFrom(s.asArray.collect { |x| if(x.isKindOf(String)) { x.asSymbol } { x } }) };
	}

	prSetInputs { |inputsarg|
		inputs = IdentitySet.newFrom((inputsarg ?? { allInputs.reject(_ == \rawMidi) }).asArray.collect(_.asSymbol));
		inputs.do { |k| if(allInputs.includes(k).not) { RCLog.warn(\score, "unknown input kind % (one of %)".format(k, allInputs)) } };
		if(inputs.includes(\rawMidi) and: { inputs.includes(\midi) or: { inputs.includes(\keyboard) } }) {
			RCLog.post(\score, "rawMidi replaces midi and keyboard (their mappings fire again when the raw messages replay)");
			inputs.remove(\midi);
			inputs.remove(\keyboard);
		};
	}

	prSetVoices { |voicesarg|
		voiceOf.clear;
		voices.clear;
		this.prAddVoices(voicesarg);
	}

	prAddVoices { |voicesarg|
		voicesarg !? { |dict|
			dict.keysValuesDo { |voice, members|
				voice = voice.asSymbol;
				members = members.asArray;
				members.do { |m| voiceOf[if(m.isKindOf(String)) { m.asSymbol } { m }] = voice };
				voices[voice] = IdentityDictionary[\objects -> ((voices[voice] !? (_[\objects]) ? []) ++ members.collect { |m| REScore.rcRef(m) ?? { m.asString } })];
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
		recordQuant = quant;
		loopbackLine = nil;
		unrecorded = 0;
		rateCounts = IdentityDictionary.new;
		rateKept = IdentityDictionary.new;
		rateWarned = IdentitySet.new;
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
		tempDefs = nil;
		beat0 = c.beats - offsetBeat;
		time0 = c.seconds - (offsetBeat * c.beatDur);   // the time of the score's beat 0
		lastTempo = c.tempo;
		score.meta[\beat0] = beat0;
		score.meta[\time0] = time0;
		score.meta[\tempo] = lastTempo;
		score.meta[\latency] = song.server.latency;
		// the grid the take started on: a replay starts on the same phase of it (REScorePlayer.alignedStart)
		recordQuant !? { |q| score.meta[\quant] = q.asQuant.quant };
		// the main thread's random state: a replay draws from it, so a typed `rand` gives what it gave
		score.meta[\randData] = Array.newFrom(thisProcess.mainThread.randData);
		score.meta[\inputs] = inputs.asArray.collect(_.asString).sort;
		score.tempoMap.add([0, lastTempo]);
		voices.keysValuesDo { |k, v| score.voices[k] = v };
		controls.keysValuesDo { |k, v| score.controls[k] = v };
		state = \recording;
		if(level3) { this.prInstallServerTap };
		onStart !? { |f| RCGuard.call(\score, nil) { f.value(this) } };
		if(startSnapshot) { this.snapshot(\start) };
		RCLog.post(\score, "% recording from beat %".format(song.name, beat0));
	}

	// Level 3: the server's address is an RETapAddr from the take's start to its stop, the
	// real one kept and put back.
	prInstallServerTap {
		var srv = song.server;
		if(srv.isNil or: { srv.addr.isKindOf(RETapAddr) }) { ^this };
		savedAddr = srv.addr;
		srv.addr = RETapAddr(savedAddr, this);
		RCLog.post(\score, "% level 3: the server's messages are recorded".format(song.name));
	}

	prRemoveServerTap {
		savedAddr !? { |a|
			var srv = song.server;
			if(srv.notNil and: { srv.addr.isKindOf(RETapAddr) }) { srv.addr = a };
		};
		savedAddr = nil;
	}

	// Ends the take: the REScore, written to <root>/<stamp>_<song> <version>.json
	// when root is set. An overdub ends with the merged score instead (the
	// original file stays, the merge is a new one).
	stop {
		var s = score, od = overdubbing, stopBeat;
		if(state != \recording) { RCLog.warn(\score, "% is not recording".format(song.name)); ^nil };
		stopBeat = this.clock.beats - beat0;
		this.prRemoveServerTap;
		s.meta[\duration] = stopBeat;
		s.meta[\commits] = this.prCommits;
		root !? { s.meta[\root] = root };
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
		if(unrecorded > 0) { s.meta[\unrecorded] = unrecorded };
		lastCheck = s.check;
		if(lastCheck.notEmpty) { s.meta[\check] = lastCheck } { s.meta.removeAt(\check) };
		lastScore = s;
		if(root.notNil) {
			lastPath = RCGuard.call(\score, nil) { s.write(REScore.pathFor(root, song.name, version)) };
			lastPath !? { |p| if(s.ofLevel(3).notEmpty) { RCGuard.call(\score, nil) { this.prWriteDefs(s, p) } } };
		} {
			RCLog.warn(\score, "no root folder: the score was not written (lastScore holds it: lastScore.write(path))");
		};
		RCLog.post(\score, "% stopped: % events over % beats".format(song.name, s.size, s.duration.round(0.01)));
		lastCheck.do { |text| RCLog.warn(\score, "check: " ++ text) };
		onStop !? { |f| RCGuard.call(\score, nil) { f.value(this, s, lastPath) } };
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
		// on the score's own phase of the grid, so that what it quantized lands as it did
		at = quant !? { |q| REScorePlayer.alignedStart(scorearg, this.clock, q) } ?? { this.clock.beats };
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
		^this.prAdd(ev)
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
		if(controlGetters.notEmpty) {
			st[\controls] = IdentityDictionary.new;
			controlGetters.keysValuesDo { |k, get|
				if(this.prInputInScope(\control, k)) {
					RCGuard.call(\score, nil) { st[\controls][k] = REScore.encodeValue(get.value) };
				};
			};
		};
		ev = this.prEvent(\snapshot, nil);
		ev[\name] = name.asSymbol;
		ev[\state] = st;
		^this.prAdd(ev)
	}

	prBeatState { |b|
		var res = IdentityDictionary.new;
		b.keyOrder.do { |k| b.keyProxy(k) !? { |p| res[k] = REScore.encodeValue(p.source) } };
		b.realDur !? { |d| res[\real_dur] = REScore.encodeValue(d) };
		res[\playing] = b.isPlaying;
		^res
	}

	//////// taps (RETap, main thread, guarded)

	// time: when the input carries its own time (an OSC bundle's timetag: a page stamps a
	// gesture at the touch, before the network), the event is stamped there, as long as it
	// is at most maxLag seconds before now (a timetag of 1, "immediately", or a sender's
	// clock that is off, stamps now); never before the take's start.
	prEvent { |kind, voice, time|
		var c = this.clock;
		var ev = IdentityDictionary.new;
		var tempo = c.tempo;
		var now = c.seconds, lag;
		if(tempo != lastTempo) {
			lastTempo = tempo;
			score.tempoMap.add([c.beats - beat0, tempo]);
			this.prAdd(IdentityDictionary[\beat -> (c.beats - beat0), \secs -> (now - time0), \kind -> \tempo, \tempo -> tempo]);
		};
		lag = time !? { |t| if(t.isNumber) { now - t } { nil } };
		if(lag.notNil and: { lag > 0 } and: { lag <= maxLag }) {
			ev[\beat] = (c.secs2beats(time) - beat0).max(0);
			ev[\secs] = (time - time0).max(0);
		} {
			ev[\beat] = c.beats - beat0;
			ev[\secs] = now - time0;
		};
		ev[\kind] = kind;
		ev[\level] = 1;
		voice !? { ev[\voice] = voice };
		^ev
	}

	// Into the score, then to onEvent (guarded: a view must not break a take).
	prAdd { |ev|
		var id = score.add(ev);
		onEvent !? { |f| RCGuard.call(\score, nil) { f.value(this, score.at(id)) } };
		^id
	}

	// An action on the main thread. Under a code line: its effect (level 1). With no cause
	// while code lines are recorded, it is the program's work (a clock-scheduled Function, a
	// handler of a server reply): level 2, so that level 1 holds only what a human did.
	// Without code lines among the inputs, a typed action cannot be told from a scheduled
	// one: it stays a level 1 action, as asked.
	tapAction { |obj, method, args, frame|
		if(state != \recording or: { inputs.includes(\actions).not }) { ^this };
		if(frame.isNil and: { inputs.includes(\code) }) { ^this.tapProgram(obj, method, args, nil) };
		if(this.prInScope(obj).not) { ^this };
		this.prAdd(this.prActionEvent(obj, method, args, 1, frame));
	}

	// The program's work (level 2): recorded in the companion when level2 is on, else counted
	// for the check. thread: the tagged Routine it ran in, when it did (its cause and doer).
	tapProgram { |obj, method, args, thread|
		var ev, by;
		if(state != \recording) { ^this };
		if(level2.not) { unrecorded = unrecorded + 1; ^this };
		if(this.prInScope(obj).not) { ^this };
		by = thread !? (_.by);
		if(rateGuard.notNil and: { this.prRateExceeded(by, obj, method, args) }) { ^this };
		ev = this.prActionEvent(obj, method, args, 2, thread !? (_.cause));
		by !? { |b| ev[\by] = REScore.rcRef(b) ?? { b.asString } };
		this.prAdd(ev);
	}

	// The rate guard (off unless rateGuard is set): a doer writing more than rateGuard events
	// in a second of the clock is sub-sampled from then on in that second, one event per
	// object, method and first argument (the key) per rateQuantum beat; said once per doer.
	prRateExceeded { |by, obj, method, args|
		var c = this.clock;
		var doer = by ? \program;
		var second = c.seconds.floor;
		var counts = rateCounts[doer] ?? { rateCounts[doer] = [second, 0] };
		var key, last, beat;
		if(counts[0] != second) { counts[0] = second; counts[1] = 0 };
		counts[1] = counts[1] + 1;
		if(counts[1] <= rateGuard) { ^false };
		if(rateWarned.includes(doer).not) {
			rateWarned.add(doer);
			RCLog.warn(\score, "rate guard: % writes more than % events/s: one per % beat per control kept".format(doer, rateGuard, rateQuantum));
		};
		key = (obj.identityHash.asString ++ "/" ++ method ++ "/" ++ (args !? (_[0]))).asSymbol;
		beat = c.beats;
		last = rateKept[key];
		if(last.notNil and: { (beat - last) < rateQuantum }) { ^true };
		rateKept[key] = beat;
		^false
	}

	// A bundle to the server (level 3, on when level3 is): its messages that make sound (the
	// serverCommands; a def's bytes, a sync, a query are left out), at the time they are due (the
	// bundle's offset, the server latency most of the time), under the cause open on the main
	// thread (a line, an input) or the tagged Routine's, with its doer.
	tapServer { |time, msgs|
		var ev, kept, thread, off = time ? 0;
		if(state != \recording or: { level3.not }) { ^this };
		msgs.do { |m| this.prKeepDef(m) };   // a def sent as bytes (a NodeProxy's temp def): kept for the take's defs folder
		kept = msgs.collect { |m| this.prServerMessage(m) }.reject(_.isNil);
		if(kept.isEmpty) { ^this };
		ev = this.prEvent(\server, nil);
		ev[\level] = 3;
		ev[\beat] = ev[\beat] + (off * this.clock.tempo);
		ev[\secs] = ev[\secs] + off;
		if(off != 0) { ev[\latency] = off };
		ev[\msgs] = kept;
		thread = RETap.taggedThread;
		if(thread.notNil) {
			thread.cause !? { |f| f[\ids][this] !? { |id| ev[\cause] = id } };
			thread.by !? { |b| ev[\by] = REScore.rcRef(b) ?? { b.asString } };
		} {
			if(RETap.isMainThread) { RETap.frame !? { |f| f[\ids][this] !? { |id| ev[\cause] = id } } };
		};
		this.prAdd(ev);
	}

	prServerMessage { |m|
		var name;
		if(m.isKindOf(SequenceableCollection).not or: { m.isEmpty }) { ^nil };
		name = RETapAddr.commandName(m[0]);
		if(serverCommands.includes(name).not) { ^nil };
		if(m.any { |x| x.isKindOf(Int8Array) }) { ^nil };
		^REScore.encodeValue([name] ++ m[1..].asArray)
	}

	// A /d_recv with the def's bytes (a NodeProxy's temp def, sent as a line sets a source): the
	// bytes kept by the def's name, so that the take's defs folder holds what the library does not.
	prKeepDef { |m|
		var bytes;
		if(m.isKindOf(SequenceableCollection).not or: { m.size < 2 } or: { RETapAddr.commandName(m[0]) != '/d_recv' }) { ^this };
		bytes = m[1];
		if(bytes.isKindOf(Int8Array).not) { ^this };
		this.class.defNameIn(bytes) !? { |name|
			tempDefs = tempDefs ?? { IdentityDictionary.new };
			tempDefs[name] = bytes;
		};
	}

	// The name of the first def in a SynthDef file's bytes ("SCgf", int32 version, int16 count, a
	// pstring name), nil when the bytes are not one.
	*defNameIn { |bytes|
		var len;
		if(bytes.size < 12 or: { bytes[0..3].collect(_.asAscii).join != "SCgf" }) { ^nil };
		len = bytes[10].asInteger;
		if(len <= 0 or: { bytes.size < (11 + len) }) { ^nil };
		^bytes.copyRange(11, 10 + len).collect(_.asAscii).join.asSymbol
	}

	// The SynthDefs a take's level 3 names (/s_new), written next to it as <take>.defs/<name>.scsyndef
	// from the library, or from the bytes sent during the take (a NodeProxy's temp def): a level 3
	// render loads them from there and needs nothing else of the piece.
	prWriteDefs { |s, path|
		var dir = REScore.defsPath(path), names = IdentitySet.new, written = 0, missing = List.new;
		s.ofLevel(3).do { |e| (e[\msgs] ? []).do { |m|   // in memory or in file form (a score read back)
			if(m.size > 1 and: { REScore.decodeValue(m[0]) == '/s_new' }) { names.add(REScore.decodeValue(m[1]).asString.asSymbol) };
		} };
		if(names.isEmpty) { ^this };
		File.mkdir(dir);
		names.do { |name|
			var def = SynthDescLib.global[name] !? (_.def), bytes = tempDefs !? (_[name]);
			case
			{ def.notNil } { def.writeDefFile(dir); written = written + 1 }
			{ bytes.notNil } { File.use(dir +/+ name ++ ".scsyndef", "wb", { |f| f.write(bytes) }); written = written + 1 }
			{ missing.add(name) };
		};
		RCLog.post(\score, "% level 3: % def(s) written to %".format(song.name, written, dir.basename));
		if(missing.notEmpty) { RCLog.warn(\score, "% def(s) the take names are not in the library (not written): %".format(missing.size, missing.asArray)) };
	}

	prActionEvent { |obj, method, args, level, frame|
		var enc = REScore.encode(args);
		var ev = this.prEvent(\action, this.prVoiceFor(obj));
		ev[\level] = level;
		ev[\rc] = REScore.rcRef(obj) ?? { REScore.encodeValue(obj) };
		ev[\method] = method;
		ev[\args] = enc[0];
		if(enc[1].not) { ev[\replay] = false };
		frame !? { |f| f[\ids][this] !? { |id| ev[\cause] = id } };
		^ev
	}

	tapInput { |kind, inputSong, data, parentFrame, frame|
		var ev, name, id, line;
		if(state != \recording or: { inputs.includes(kind).not }) { ^this };
		if(kind != \rawMidi and: { inputSong !== song }) { ^this };   // raw MIDI belongs to no song
		name = data[\name] ?? { data[\key] } ?? { data[\device] };
		if(this.prInputInScope(kind, name).not) { ^this };
		ev = this.prEvent(kind, this.prVoiceForName(name ? kind), data[\time]);
		data.keysValuesDo { |k, v| if(k != \time) { ev[k] = REScore.encodeValue(v) } };
		parentFrame !? { |f| f[\ids][this] !? { |id| ev[\cause] = id } };
		// a message a code line sent to this song comes back here, after the line: the line is
		// not replayed (the input replays through its def), and both say so
		line = this.prLoopbackLine(kind, ev, parentFrame);
		line !? { ev[\loopbackOf] = line[\id] };
		id = this.prAdd(ev);
		frame[\ids][this] = id;
		line !? { score.at(line[\id]) !? { |l| l[\replay] = false; l[\loopback] = id } };
	}

	tapCode { |text, parentFrame, frame|
		var ev, id;
		if(state != \recording or: { inputs.includes(\code).not }) { ^this };
		ev = this.prEvent(\code, this.prVoiceForName(\code));
		ev[\text] = text;
		if(unsafeCode.any { |pat| text.contains(pat) }) { ev[\replay] = false };
		parentFrame !? { |f| f[\ids][this] !? { |id| ev[\cause] = id } };
		id = this.prAdd(ev);
		frame[\ids][this] = id;
		// a line that sends OSC: should its message come back as an input of this song within
		// loopbackWindow, tapInput marks the line
		loopbackLine = if(this.prSendsOsc(text)) { IdentityDictionary[\id -> id, \text -> text, \until -> (this.clock.seconds + loopbackWindow)] } { nil };
	}

	dropCode { |frame|
		frame[\ids][this] !? { |id| score !? { |s| s.remove(id) } };
	}

	// A line that raised never reached codeDump (RETap closes its frame at the next line): the
	// take says so. Its effects are what ran before the raise; the player plays, of them, only
	// what the line does not reach again.
	codeRaised { |frame|
		frame[\ids][this] !? { |id| score !? { |s| s.at(id) !? { |ev| ev[\raised] = true } } };
	}

	prSendsOsc { |text| ^#["sendMsg", "sendBundle", "sendRaw"].any { |p| text.contains(p) } }

	// The pending sending line when this OSC input is its message: a source (no cause), within the
	// window, and the line names the input's path, or a loopback address (127.0.0.1, localhost,
	// localAddr, the session's port).
	prLoopbackLine { |kind, ev, parentFrame|
		var line = loopbackLine;
		if(kind != \osc or: { parentFrame.notNil } or: { line.isNil }) { ^nil };
		if(this.clock.seconds > line[\until]) { loopbackLine = nil; ^nil };
		if(this.prLoopsBack(line[\text], ev).not) { ^nil };
		loopbackLine = nil;
		^line
	}

	prLoopsBack { |text, ev|
		var port = song.session !? { |se| se.localAddr !? (_.port) };
		if(ev[\path].notNil and: { text.contains(ev[\path].asString) }) { ^true };
		^#["127.0.0.1", "localhost", "localAddr"].any { |p| text.contains(p) } or: { port.notNil and: { text.contains(port.asString) } }
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
		{ obj.isKindOf(RCDurList) } { ^[obj] ++ (obj.owner !? { |b| this.prOwners(b) } ? []) }
		{ obj.isKindOf(RCSwing) } { ^[obj] ++ (obj.layer !? { |l| this.prOwners(l) } ? []) }
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
		surface = switch(kind, \midi, { song.midi }, \osc, { song.osc }, \keyboard, { song.keyboard }, \rawMidi, { song.midi });
		^(surface.notNil and: { scope.includes(surface) }) or: { scope.includes(song) }
	}

	prVoiceFor { |obj|
		^voiceOf[obj] ?? {
			case
			{ obj.isKindOf(RCBeat) } { this.prBeatVoice(obj) }
			{ obj.isKindOf(RCDurList) } { obj.owner !? { |b| this.prVoiceFor(b) } ? \durList }
			{ obj.isKindOf(RCSwing) } { obj.layer !? (_.key) ? \swing }
			{ obj.isKindOf(RCLayer) } { obj.key }
			{ obj.isKindOf(RCSong) } { obj.name }
			{ obj.isKindOf(RCBatch) } { obj.name }
			{ obj.isKindOf(RCOrgnsm) } { obj.batch !? (_.name) ?? { obj.name } }
			{ obj.isKindOf(RCFObject) } { (obj.name ? obj.synthDefName).asSymbol }
			{ obj.isKindOf(RCCrawler) } { obj.batch !? (_.name) ? \crawler }
			{ obj.class.name }
		}
	}

	prBeatVoice { |b| ^if(voicesByLayer) { b.layer.key } { (b.layer.key.asString ++ "/" ++ b.name).asSymbol } }

	prVoiceForName { |name| ^voiceOf[name.asSymbol] ?? { name.asSymbol } }

	printOn { |stream| stream << "REScoreRecorder(" << song.name << ", " << state << ")" }
}
