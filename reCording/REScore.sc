// reCording — a score: what you did to a song, as events with the beat they
// happened on and the metadata that replays them, in one JSON file (REJSON;
// the format is in the reCording guide, "The take file"). Made by
// REScoreRecorder, played by REScorePlayer, edited by its own methods.
//
//   s = REScore(~song, piece: "HadronLake3", wersion: "w1");
//   s.add((beat: 1.0, secs: 0.5, kind: \action, voice: 'core/kick',
//       rc: REScore.rcRef(~kick), method: \set, args: REScore.encodeValue([\amp, 0.5])));   // ids assigned
//   s.write(REScore.pathFor(~piece_dir +/+ "Scores", ~song.name, "w1"));
//   REScore.read(path); s.events; s.at(id); s.eventsOf('core/kick'); s.duration
//
// Values are kept in their file form (encodeValue when recorded, decodeValue
// when replayed): numbers, strings, symbols, arrays, Events as objects,
// other dictionaries as {dict: ...}, patterns and functions with source as
// {sc: <compile string>}, reCurrent objects as {rc: <kind>, name: ...}
// references resolved through the song (or the environment: batches and
// crawlers live in variables), anything else as {ref: <print string>}, which
// marks the event replay: false. The fields kind, voice, method, name, key and
// msg are Symbols in memory and plain strings in the file.

REScore {
	classvar <>format = "re-score", <>formatVersion = 1;
	classvar <keyOrder, <metaKeys, <symbolFields, <scClasses;

	var <meta, <events, <voices, <controls, <tempoMap;
	var index, nextId = 1;

	*initClass {
		keyOrder = #[\format, \version, \song, \piece, \wersion, \created, \sc, \commits, \beat0, \time0, \tempo, \latency,
			\duration, \tempoMap, \voices, \controls, \events,
			\id, \beat, \secs, \kind, \voice, \cause, \rc, \method, \args, \name, \key, \path, \msg, \chan, \note, \raw,
			\value, \text, \replay, \state];
		metaKeys = #[\song, \piece, \wersion, \created, \sc, \commits, \beat0, \time0, \tempo, \latency, \duration];
		symbolFields = #[\kind, \voice, \method, \name, \key, \msg];
		// classes whose compile string is the value itself (a Function needs its source, see prEncode)
		scClasses = [Pattern, Ref, Env, Rest, Quant, ControlSpec, Association, Char, Class, Point, Rect, Interval, Tuning, Scale];
	}

	*new { |song, piece, wersion|
		^super.new.initREScore(song, piece, wersion)
	}

	initREScore { |songarg, piecearg, wersionarg|
		meta = IdentityDictionary.new;
		if(songarg.isKindOf(RCSong)) { songarg = songarg.name };
		songarg !? { |x| meta[\song] = x.asString };
		piecearg !? { |x| meta[\piece] = x.asString };
		wersionarg !? { |x| meta[\wersion] = x.asString };
		meta[\created] = Date.localtime.stamp;
		meta[\sc] = Main.version;
		events = List.new;
		voices = IdentityDictionary.new;
		controls = IdentityDictionary.new;
		tempoMap = List.new;
		index = IdentityDictionary.new;
	}

	song { ^meta[\song] }

	//////// events

	// Appends an event (an Event or a dictionary, copied as an IdentityDictionary),
	// giving it an id when it has none (or one already used). Returns the id.
	add { |event|
		var ev = IdentityDictionary.new;
		var id;
		event.keysValuesDo { |k, v| ev[k.asSymbol] = v };
		id = ev[\id];
		if(id.isNil or: { index[id].notNil }) {
			if(id.notNil) { RCLog.warn(\score, "event id % already used: reassigned".format(id)) };
			id = nextId;
			ev[\id] = id;
		};
		nextId = max(nextId, id + 1);
		events.add(ev);
		index[id] = ev;
		^id
	}

	remove { |id|
		var ev = index.removeAt(id);
		ev !? { events.remove(ev) };
		^ev
	}

	at { |id| ^index[id] }
	size { ^events.size }
	isEmpty { ^events.isEmpty }
	last { ^events.last }

	// Beats: the recorded duration, else the last event's beat.
	duration { ^meta[\duration] ?? { if(events.isEmpty) { 0 } { events.last[\beat] ? 0 } } }

	eventsOf { |voice| voice = voice.asSymbol; ^events.select { |e| e[\voice] == voice } }
	ofKind { |kind| kind = kind.asSymbol; ^events.select { |e| e[\kind] == kind } }
	between { |fromBeat, toBeat| ^events.select { |e| e[\beat] >= fromBeat and: { e[\beat] < toBeat } } }
	causedBy { |id| ^events.select { |e| e[\cause] == id } }
	voiceNames { ^(events.collect { |e| e[\voice] }.reject(_.isNil) ++ voices.keys.asArray).asSet.asArray.sort { |a, b| a.asString <= b.asString } }
	kinds { ^events.collect { |e| e[\kind] }.asSet.asArray.sort { |a, b| a.asString <= b.asString } }

	// By beat, equal beats by id (recording appends in order; edits may not).
	sort {
		events = List.newFrom(events.asArray.sort { |a, b|
			if(a[\beat] == b[\beat]) { a[\id] <= b[\id] } { a[\beat] <= b[\beat] }
		});
	}

	copy { ^this.class.fromDict(RCUtil.copyTree(this.asDict)) }

	//////// the file

	// <root>/<stamp>_<song> <version>.json (RETake.path)
	*pathFor { |root, song, version = "", stamp|
		if(song.isKindOf(RCSong)) { song = song.name };
		^RETake.path(root, "", song, version, "json", stamp)
	}

	asDict {
		var d = IdentityDictionary.new;
		d[\format] = format;
		d[\version] = formatVersion;
		meta.keysValuesDo { |k, v| d[k] = v };
		d[\tempoMap] = tempoMap.asArray;
		d[\voices] = voices;
		d[\controls] = controls;
		d[\events] = events.collect { |e| this.prEventOut(e) }.asArray;
		^d
	}

	prEventOut { |e|
		var out = IdentityDictionary.new;
		e.keysValuesDo { |k, v| out[k] = if(symbolFields.includes(k) and: { v.isKindOf(Symbol) }) { v.asString } { v } };
		^out
	}

	*prEventIn { |e|
		var ev = IdentityDictionary.new;
		e.keysValuesDo { |k, v| ev[k.asSymbol] = if(symbolFields.includes(k.asSymbol) and: { v.isString }) { v.asSymbol } { v } };
		^ev
	}

	*fromDict { |d|
		var s;
		if(d.isNil) { ^nil };
		if(d[\format] != format) { RCLog.error(\score, "not a % file (format %)".format(format, d[\format])); ^nil };
		if((d[\version] ? 1) > formatVersion) { RCLog.warn(\score, "file version % is newer than this reader (%)".format(d[\version], formatVersion)) };
		s = this.new;
		metaKeys.do { |k| d[k] !? { |v| s.meta[k] = v } };
		(d[\tempoMap] ? []).do { |pair| s.tempoMap.add(pair) };
		(d[\voices] ? IdentityDictionary.new).keysValuesDo { |k, v| s.voices[k.asSymbol] = v };
		(d[\controls] ? IdentityDictionary.new).keysValuesDo { |k, v| s.controls[k.asSymbol] = v };
		(d[\events] ? []).do { |e| s.add(this.prEventIn(e)) };
		^s
	}

	asJSONString { ^REJSON.stringify(this.asDict, 2, 2, keyOrder) }
	*fromJSONString { |text| ^this.fromDict(REJSON.parse(text)) }

	// Returns the path written.
	write { |path|
		var p = REJSON.write(this.asDict, path, 2, 2, keyOrder);
		RCLog.post(\score, "wrote % events to %".format(events.size, p));
		^p
	}

	*read { |path| ^this.fromDict(REJSON.read(path)) }

	// Short commit of the git checkout holding dir, nil outside one.
	*gitHead { |dir|
		var out;
		if(dir.isNil) { ^nil };
		out = RCGuard.call(\score, nil) {
			("git -C " ++ dir.asString.shellQuote ++ " rev-parse --short HEAD 2>/dev/null").unixCmdGetStdOut
		};
		out = out !? (_.stripWhiteSpace);
		^if(out.isNil or: { out.isEmpty }) { nil } { out }
	}

	//////// the value codec

	// [encoded, replayable]
	*encode { |x|
		var state = IdentityDictionary[\replay -> true];
		var v = this.prEncode(x, state, 0);
		^[v, state[\replay]]
	}

	*encodeValue { |x| ^this.prEncode(x, IdentityDictionary[\replay -> true], 0) }

	*prDict { |key, value|
		var d = IdentityDictionary.new;
		d[key] = value;
		^d
	}

	*prEncode { |x, state, depth|
		var ref, str, src;
		if(depth > 32) { state[\replay] = false; ^this.prDict(\ref, "too deep") };
		case
		{ x.isNil or: { x.isKindOf(Boolean) } or: { x.isString } or: { x.isKindOf(Symbol) } } { ^x }
		{ x.isNumber } {
			if(x.isNaN or: { x.abs == inf }) { ^this.prDict(\sc, x.asCompileString) };
			^x
		}
		{ x.isKindOf(Event) } { ^this.prEncodeDict(x, state, depth) }
		{ x.isKindOf(Dictionary) } { ^this.prDict(\dict, this.prEncodeDict(x, state, depth)) }
		{ x.isKindOf(SequenceableCollection) } { ^x.collect { |el| this.prEncode(el, state, depth + 1) } }
		{ x.isKindOf(Function) } {
			src = x.def.sourceCode;
			if(src.notNil) { ^this.prDict(\sc, src) };
			state[\replay] = false;
			^this.prDict(\ref, "a Function without source")
		}
		{ (ref = this.rcRef(x)).notNil } { ^ref }
		{ scClasses.any { |c| x.isKindOf(c) } } {
			str = x.asCompileString;
			if(str.compile.notNil) { ^this.prDict(\sc, str) };
			state[\replay] = false;
			^this.prDict(\ref, str)
		}
		{
			state[\replay] = false;
			^this.prDict(\ref, x.asString)
		};
	}

	*prEncodeDict { |dict, state, depth|
		var res = IdentityDictionary.new;
		dict.keysValuesDo { |k, v| res[k.asSymbol] = this.prEncode(v, state, depth + 1) };
		^res
	}

	// The encoded value back: {sc} interpreted (guarded), {rc} resolved through
	// song (left as the reference when it cannot be), {ref} left as it is,
	// objects as Events, {dict} as an IdentityDictionary.
	*decodeValue { |x, song|
		var res;
		case
		{ x.isKindOf(Dictionary) } {
			if(x.size == 1 and: { x[\sc].isString }) { ^this.prInterpret(x[\sc]) };
			if(x[\rc].notNil) { ^this.resolve(x, song) ? x };
			if(x.size == 1 and: { x[\ref].notNil }) { ^x };
			if(x.size == 1 and: { x[\dict].isKindOf(Dictionary) }) {
				res = IdentityDictionary.new;
				x[\dict].keysValuesDo { |k, v| res[k.asSymbol] = this.decodeValue(v, song) };
				^res
			};
			res = Event.new;
			x.keysValuesDo { |k, v| res[k.asSymbol] = this.decodeValue(v, song) };
			^res
		}
		{ x.isKindOf(SequenceableCollection) and: { x.isString.not } } { ^x.collect { |el| this.decodeValue(el, song) } }
		{ ^x };
	}

	// A compile string back to its value: nil (reported) when it does not
	// compile or raises.
	*prInterpret { |str|
		var func = str.compile;
		if(func.isNil) { RCLog.error(\score, "cannot compile: %".format(str)); ^nil };
		^RCGuard.call(\score, nil) { func.value }
	}

	*isRef { |x| ^x.isKindOf(Dictionary) and: { x[\rc].notNil or: { x[\ref].notNil } } }

	//////// reCurrent object references

	*rcRef { |obj|
		case
		{ obj.isKindOf(RCBeat) } { ^IdentityDictionary[\rc -> "beat", \song -> obj.songName.asString, \layer -> obj.layer.key.asString, \name -> obj.name.asString] }
		{ obj.isKindOf(RCLayer) } { ^IdentityDictionary[\rc -> "layer", \song -> obj.songName.asString, \name -> obj.key.asString] }
		{ obj.isKindOf(RCSong) } { ^IdentityDictionary[\rc -> "song", \name -> obj.name.asString] }
		{ obj.isKindOf(RCBatch) } { ^IdentityDictionary[\rc -> "batch", \song -> obj.song.name.asString, \name -> obj.name.asString] }
		{ obj.isKindOf(RCOrgnsm) } { ^IdentityDictionary[\rc -> "orgnsm", \song -> obj.song.name.asString, \name -> obj.name.asString] }
		{ obj.isKindOf(RCFObject) } { ^IdentityDictionary[\rc -> "fobject", \song -> obj.song.name.asString, \name -> (obj.name ? obj.synthDefName).asString] }
		{ obj.isKindOf(RCCrawler) } { ^IdentityDictionary[\rc -> "crawler", \keys -> obj.attrKeys.collect(_.asString)] }
		{ ^nil };
	}

	// The live object of a reference: beats, layers, orgnsms and fobjects
	// through the song (the reference's, through the default session, when none
	// is given), batches and crawlers by the variable that holds them.
	*resolve { |ref, song|
		var kind = ref[\rc].asSymbol;
		var name = ref[\name] !? (_.asSymbol);
		var s = song ?? { RCSession.default !? { |se| ref[\song] !? { |n| se.song(n) } } };
		switch(kind,
			\song, { ^s },
			\layer, { ^s !? { |x| x.layer(name) } },
			\beat, { ^s !? { |x| x.layer(ref[\layer] ? \core) !? { |l| l.beat(name) } } },
			\orgnsm, { ^s !? { |x| x.registry.all.detect { |o| o.name == name } } },
			\fobject, { ^s !? { |x| x.registry.fobject(name) } },
			\batch, { ^this.prFindInEnvironment { |x| x.isKindOf(RCBatch) and: { x.name == name } } },
			\crawler, { ^this.prFindInEnvironment { |x|
				x.isKindOf(RCCrawler) and: { x.attrKeys.collect(_.asString) == (ref[\keys] ? []).collect(_.asString) }
			} }
		);
		RCLog.warn(\score, "unknown reference kind %".format(kind));
		^nil
	}

	*prFindInEnvironment { |cond|
		currentEnvironment.keysValuesDo { |k, v| if(cond.value(v)) { ^v } };
		if(currentEnvironment !== topEnvironment) {
			topEnvironment.keysValuesDo { |k, v| if(cond.value(v)) { ^v } };
		};
		^nil
	}

	printOn { |stream|
		stream << "REScore(" << (meta[\song] ? "?") << ", " << events.size << " events, " << this.duration << " beats)"
	}
}
