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
			\id, \beat, \secs, \kind, \voice, \cause, \rc, \method, \args, \name, \key, \path, \device, \msg, \chan, \note, \num, \raw,
			\value, \text, \replay, \state];
		metaKeys = #[\song, \piece, \wersion, \created, \sc, \commits, \root, \beat0, \time0, \tempo, \latency, \duration, \overdubs];
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

	//////// editing: every operation answers a new score, the receiver untouched, ids kept

	// Seconds of a beat through the tempo map ([[beat, tempo], ...], piecewise constant).
	beatsToSecs { |beat|
		var map = tempoMap.asArray;
		var secs = 0;
		if(map.isEmpty) { ^beat / (meta[\tempo] ? 1) };
		map.do { |pair, i|
			var start = pair[0], tempo = pair[1];
			var end = map[i + 1] !? (_[0]) ? inf;
			if(beat > start) { secs = secs + ((min(beat, end) - start) / tempo) };
		};
		if(beat < map[0][0]) { secs = (beat - map[0][0]) / map[0][1] };
		^secs
	}

	prSelected { |e, voices, kinds|
		^(voices.isNil or: { voices.includes(e[\voice]) }) and: { kinds.isNil or: { kinds.includes(e[\kind]) } }
	}

	prSet { |names| ^names !? { IdentitySet.newFrom(names.asArray.collect(_.asSymbol)) } }

	// func: beat → beat, on the selected events (voices, kinds: nil = all).
	retimed { |func, voices, kinds|
		var res = this.copy;
		var vs = this.prSet(voices), ks = this.prSet(kinds);
		res.events.do { |e|
			if(this.prSelected(e, vs, ks)) {
				e[\beat] = func.value(e[\beat], e);
				e[\secs] = res.beatsToSecs(e[\beat]);
			};
		};
		res.sort;
		^res
	}

	// Moved by beats (a negative shift undoes a latency); what lands before 0 is dropped,
	// a snapshot there is kept at 0.
	shifted { |beats, voices, kinds|
		var res = this.retimed({ |b| b + beats }, voices, kinds);
		var dropped = 0;
		res.events.copy.do { |e|
			if(e[\beat] < 0) {
				if(e[\kind] == \snapshot) { e[\beat] = 0; e[\secs] = 0 } { res.remove(e[\id]); dropped = dropped + 1 };
			};
		};
		if(dropped > 0) { RCLog.post(\score, "shifted by %: % event(s) before the start dropped".format(beats, dropped)) };
		res.sort;
		res.meta[\duration] !? { |d| res.meta[\duration] = max(0, d + beats) };
		^res
	}

	// Stretched around origin.
	scaled { |factor, voices, kinds, origin = 0|
		var res = this.retimed({ |b| ((b - origin) * factor) + origin }, voices, kinds);
		res.meta[\duration] !? { |d| res.meta[\duration] = ((d - origin) * factor) + origin };
		^res
	}

	// To the nearest grid (beats), fully (strength 1) or part of the way.
	quantized { |grid = 0.25, voices, kinds, strength = 1|
		^this.retimed({ |b| b + ((b.round(grid) - b) * strength) }, voices, kinds)
	}

	// The events of [from, to), rebased to 0; the latest snapshot before from is kept at 0.
	trimmed { |from = 0, to|
		var res = this.copy;
		var last = res.events.select { |e| e[\kind] == \snapshot and: { e[\beat] < from } }.last;
		to = to ?? { this.duration };
		res.events.copy.do { |e|
			if(e !== last and: { e[\beat] < from or: { e[\beat] >= to } }) { res.remove(e[\id]) };
		};
		res.events.do { |e| e[\beat] = max(0, e[\beat] - from) };
		res.prRebaseTempoMap(from);
		res.events.do { |e| e[\secs] = res.beatsToSecs(e[\beat]) };
		res.meta[\duration] = to - from;
		res.sort;
		// the kept snapshot leads the events at 0 whatever its id: the state comes first
		last !? { res.events.remove(last); res.events.addFirst(last) };
		^res
	}

	prRebaseTempoMap { |from|
		var map = tempoMap.asArray.sort { |a, b| a[0] <= b[0] };
		var before = map.select { |p| p[0] <= from };
		var after = map.select { |p| p[0] > from };
		var res = List.new;
		before.last !? { |p| res.add([0, p[1]]) };
		after.do { |p| res.add([p[0] - from, p[1]]) };
		tempoMap = res;
	}

	// Without the points of a continuous control that lie within tolerance of the line
	// between their kept neighbours; the first and last points of each control stay.
	thinned { |tolerance = 0.001, voices|
		var res = this.copy;
		var vs = this.prSet(voices);
		var groups = Dictionary.new;
		res.events.do { |e|
			var k = this.class.controlKey(e);
			if(k.notNil and: { this.class.isContinuous(e) } and: { vs.isNil or: { vs.includes(e[\voice]) } }) {
				groups[k] = (groups[k] ? []).add(e);
			};
		};
		groups.do { |points|
			var kept = points.first;
			points.do { |e, i|
				var next = points[i + 1];
				var interp, span;
				if(i > 0 and: { next.notNil }) {
					span = next[\beat] - kept[\beat];
					interp = if(span <= 0) { this.class.controlValue(kept) } {
						this.class.controlValue(kept) + ((this.class.controlValue(next) - this.class.controlValue(kept)) * ((e[\beat] - kept[\beat]) / span))
					};
					if((this.class.controlValue(e) - interp).abs <= tolerance) { res.remove(e[\id]) } { kept = e };
				};
			};
		};
		^res
	}

	// The values of the continuous controls averaged over a window of points (odd, centred).
	smoothed { |window = 3, voices|
		var res = this.copy;
		var vs = this.prSet(voices);
		var groups = Dictionary.new;
		var half = (window.asInteger div: 2).max(1);
		res.events.do { |e|
			var k = this.class.controlKey(e);
			if(k.notNil and: { this.class.isContinuous(e) } and: { vs.isNil or: { vs.includes(e[\voice]) } }) {
				groups[k] = (groups[k] ? []).add(e);
			};
		};
		groups.do { |points|
			var values = points.collect { |e| this.class.controlValue(e) };
			points.do { |e, i|
				var lo = max(0, i - half), hi = min(values.size - 1, i + half);
				this.class.prSetControlValue(e, values[lo..hi].sum / (hi - lo + 1));
			};
		};
		^res
	}

	withoutVoices { |voices|
		var res = this.copy;
		var vs = this.prSet(voices);
		res.events.copy.do { |e| if(vs.includes(e[\voice])) { res.remove(e[\id]) } };
		vs.do { |v| res.voices.removeAt(v) };
		^res
	}

	onlyVoices { |voices|
		var vs = this.prSet(voices);
		^this.withoutVoices(this.voiceNames.reject { |v| vs.includes(v) })
	}

	withoutKinds { |kinds|
		var res = this.copy;
		var ks = this.prSet(kinds);
		res.events.copy.do { |e| if(ks.includes(e[\kind])) { res.remove(e[\id]) } };
		^res
	}

	renamedVoice { |old, new|
		var res = this.copy;
		old = old.asSymbol;
		new = new.asSymbol;
		res.events.do { |e| if(e[\voice] == old) { e[\voice] = new } };
		res.voices[old] !? { |v| res.voices.removeAt(old); res.voices[new] = v };
		^res
	}

	// The control's events in [from, to) replaced by points [[beat, value], ...], shaped like
	// its first recorded event (kind, voice, receiver, path...).
	replacedSegment { |controlKey, from, to, points|
		var res = this.copy;
		var template = res.events.detect { |e| this.class.controlKey(e) == controlKey };
		if(template.isNil) { RCLog.warn(\score, "no event controls %: nothing replaced".format(controlKey)); ^res };
		res.events.copy.do { |e|
			if(this.class.controlKey(e) == controlKey and: { e[\beat] >= from } and: { e[\beat] < to }) { res.remove(e[\id]) };
		};
		points.do { |p|
			var ev = IdentityDictionary.new;
			template.keysValuesDo { |k, v| ev[k] = RCUtil.copyTree(v) };
			ev.removeAt(\id);
			ev.removeAt(\cause);
			ev[\beat] = p[0];
			ev[\secs] = res.beatsToSecs(p[0]);
			this.class.prSetControlValue(ev, p[1]);
			res.add(ev);
		};
		res.sort;
		^res
	}

	// The control's value at a beat: its last point at or before it, else its first.
	valueAt { |controlKey, beat|
		var points = this.curvePoints(controlKey);
		var before;
		if(points.isEmpty) { ^nil };
		before = points.select { |p| p[0] <= beat }.last;
		^(before ? points.first)[1]
	}

	// A copy of a control's event with another value (what a replay interpolates with).
	*withControlValue { |e, value|
		var ev = IdentityDictionary.new;
		e.keysValuesDo { |k, v| ev[k] = v };
		ev[\args] = ev[\args] !? (_.copy);
		this.prSetControlValue(ev, value);
		^ev
	}

	*prSetControlValue { |e, value|
		switch(e[\kind],
			\midi, { e[\value] = value; e.removeAt(\raw) },
			\osc, { e[\args] = [value] ++ ((e[\args] ? []).drop(1)) },
			\action, { e[\args] = (e[\args] ? [nil]).copy; e[\args][1] = value },
			\keyboard, { e[\value] = value },
			\rawMidi, { e[\value] = value }
		);
	}

	//////// controls: the events that set one value, and their curves

	// What an event controls: [\midi, name], [\osc, key], [\set, beatName, key],
	// [\rPut, orgnsmName, key], [\setArg, fobjectName, key], [\cc, device, num],
	// [\bend | \touch, device, chan]; nil for anything else (a note, a transport
	// action, a code line...).
	*controlKey { |e|
		var rc = e[\rc], args = e[\args];
		switch(e[\kind],
			\midi, { ^[\midi, e[\name]] },
			\osc, { ^[\osc, e[\key]] },
			\action, {
				if(e[\method] == \set or: { e[\method] == \rPut } or: { e[\method] == \setArg }) {
					^[e[\method], rc !? (_[\name]), args !? (_[0])]
				};
				^nil
			},
			\keyboard, {
				if(e[\msg] == \cc) { ^[\cc, e[\device], e[\num]] };
				if(e[\msg] == \bend or: { e[\msg] == \touch }) { ^[e[\msg], e[\device], e[\chan]] };
				^nil
			},
			\rawMidi, {
				if(e[\msg] == \control) { ^[\cc, e[\device], e[\num]] };
				if(e[\msg] == \bend or: { e[\msg] == \touch }) { ^[e[\msg], e[\device], e[\chan]] };
				^nil
			}
		);
		^nil
	}

	// The value such an event sets (nil for the others).
	*controlValue { |e|
		switch(e[\kind],
			\midi, { ^e[\value] },
			\osc, { ^e[\args] !? (_[0]) },
			\action, { ^e[\args] !? (_[1]) },
			\keyboard, { ^e[\value] },
			\rawMidi, { ^e[\value] }
		);
		^nil
	}

	// A continuous control: one that sets a number.
	*isContinuous { |e| ^this.controlKey(e).notNil and: { this.controlValue(e).isNumber } }

	controlKeys {
		var res = Set.new;
		events.do { |e| this.class.controlKey(e) !? { |k| res.add(k) } };
		^res.asArray
	}

	// [[beat, value], ...] of one control, in order.
	curvePoints { |controlKey|
		^events.select { |e| this.class.controlKey(e) == controlKey }.collect { |e| [e[\beat], this.class.controlValue(e)] }.asArray
	}

	// The control's curve as a pattern: its first value held until its first
	// point, then Pseg between points (curve: \lin, \exp or a number), the
	// last value held forever.
	curvePattern { |controlKey, curve = \lin|
		var points = this.curvePoints(controlKey).select { |p| p[1].isNumber };
		var levels, durs;
		if(points.isEmpty) { ^nil };
		levels = points.collect(_[1]);
		durs = points.collect(_[0]).differentiate.drop(1).max(0);
		if(points[0][0] > 0) {
			levels = [points[0][1]] ++ levels;
			durs = [points[0][0]] ++ durs;
		};
		if(durs.isEmpty) { ^Pn(points[0][1], inf) };
		^Pseq([Pseg(levels, durs, curve), Pn(points.last[1], inf)], 1)
	}

	// The keyboard notes of a voice as a Pbind: midinote, dur (to the next
	// note, 1 for the last), sustain (to the note's own noteOff, else dur),
	// amp (velocity / 127), chan; starting at the first note.
	notePattern { |voice|
		var ons = events.select { |e| e[\kind] == \keyboard and: { e[\voice] == voice.asSymbol } and: { e[\msg] == \noteOn } };
		var offs = events.select { |e| e[\kind] == \keyboard and: { e[\voice] == voice.asSymbol } and: { e[\msg] == \noteOff } };
		var durs, sustains;
		if(ons.isEmpty) { ^nil };
		durs = ons.collect { |e, i| if(i < (ons.size - 1)) { ons[i + 1][\beat] - e[\beat] } { 1 } };
		sustains = ons.collect { |e, i|
			var off = offs.detect { |o| o[\note] == e[\note] and: { o[\chan] == e[\chan] } and: { o[\beat] >= e[\beat] } };
			off !? { |o| max(o[\beat] - e[\beat], 0.01) } ? durs[i]
		};
		^Pbind(
			\midinote, Pseq(ons.collect(_[\note])),
			\dur, Pseq(durs),
			\sustain, Pseq(sustains),
			\amp, Pseq(ons.collect { |e| (e[\value] ? 100) / 127 }),
			\chan, Pseq(ons.collect { |e| e[\chan] ? 0 })
		)
	}

	//////// overdub: merging a new take into a score

	// A copy of `a` with the events of `b` merged in. voices: the overdubbed
	// voices (nil: every voice of b); mode: \replace drops a's events of those
	// voices within punch [in, out] (out nil: b's duration), \keep drops
	// nothing, \touch drops a's events of each control b touched between b's
	// first and last touch of it, nil (auto) is touch for continuous controls
	// and replace for the voice's other events; b's events of other voices are
	// added. loopSpan [from, to]: b was recorded over a loop, its beats fold
	// into the span and, unless \keep, only the last pass of each voice (each
	// control for touch and auto) remains. Ids are renewed, causes follow.
	*merge { |a, b, voices, mode, punch, loopSpan|
		var res = a.copy;
		var idMap = IdentityDictionary.new;
		var bEvents = b.events.asArray;
		var pin = punch !? (_[0]) ? 0;
		var pout = punch !? (_[1]) ?? { b.duration };
		var voiceSet;
		if(loopSpan.notNil) { bEvents = this.prFold(bEvents, loopSpan, mode) };
		voiceSet = IdentitySet.newFrom((voices ?? { b.voiceNames }).asArray.collect(_.asSymbol));
		voiceSet.do { |v|
			var newOnes = bEvents.select { |e| e[\voice] == v };
			switch(mode,
				\replace, { res.events.copy.do { |e| if(e[\voice] == v and: { e[\beat] >= pin } and: { e[\beat] < pout }) { res.remove(e[\id]) } } },
				\keep, { },
				\touch, { this.prRemoveTouched(res, v, newOnes, false, pin, pout) },
				{ this.prRemoveTouched(res, v, newOnes, true, pin, pout) }
			);
		};
		bEvents.do { |e|
			var ev = IdentityDictionary.new;
			e.keysValuesDo { |k, val| ev[k] = val };
			ev.removeAt(\id);
			ev[\cause] !? { |c| if(idMap[c].notNil) { ev[\cause] = idMap[c] } { ev.removeAt(\cause) } };
			idMap[e[\id]] = res.add(ev);
		};
		res.sort;
		res.meta[\overdubs] = (res.meta[\overdubs] ? []) ++ [IdentityDictionary[
			\created -> b.meta[\created], \voices -> voiceSet.asArray.collect(_.asString).sort,
			\mode -> (mode ? \auto).asString, \punch -> [pin, pout]]];
		^res
	}

	*prRemoveTouched { |res, voice, newOnes, auto, pin, pout|
		var spans = Dictionary.new;   // control key → [first, last] touch
		var others = false;
		newOnes.do { |e|
			var k = this.controlKey(e);
			var span;
			if(k.notNil and: { auto.not or: { this.isContinuous(e) } }) {
				span = spans[k];
				spans[k] = if(span.isNil) { [e[\beat], e[\beat]] } { [min(span[0], e[\beat]), max(span[1], e[\beat])] };
			} {
				others = true;
			};
		};
		res.events.copy.do { |e|
			var k, span;
			if(e[\voice] == voice) {
				k = this.controlKey(e);
				span = k !? { spans[k] };
				if(span.notNil and: { e[\beat] >= span[0] } and: { e[\beat] <= span[1] }) {
					res.remove(e[\id]);
				} {
					if(auto and: { others } and: { k.isNil or: { this.isContinuous(e).not } }
						and: { e[\beat] >= pin } and: { e[\beat] < pout }) { res.remove(e[\id]) };
				};
			};
		};
	}

	// Beats folded into [from, to]; for every mode but \keep only the last pass
	// of each voice (each control, for touch and auto) remains.
	*prFold { |events, span, mode|
		var from = span[0], len = span[1] - span[0];
		var lastPass = Dictionary.new;
		var folded;
		if(len <= 0) { ^events };
		folded = events.collect { |e|
			var ev = IdentityDictionary.new;
			var pass = 0;
			e.keysValuesDo { |k, v| ev[k] = v };
			if(e[\beat] >= from) {
				pass = ((e[\beat] - from) / len).floor.asInteger;
				ev[\beat] = from + ((e[\beat] - from) mod: len);
			};
			ev[\pass] = pass;
			ev
		};
		if(mode == \keep) { ^folded.do { |ev| ev.removeAt(\pass) } };
		folded.do { |ev|
			var key = this.prPassKey(ev, mode);
			lastPass[key] = max(lastPass[key] ? 0, ev[\pass]);
		};
		folded = folded.select { |ev| ev[\pass] == lastPass[this.prPassKey(ev, mode)] };
		folded.do { |ev| ev.removeAt(\pass) };
		^folded
	}

	*prPassKey { |ev, mode|
		if(mode == \replace) { ^[ev[\voice]] };
		^[ev[\voice], if(mode.isNil and: { this.isContinuous(ev).not }) { nil } { this.controlKey(ev) }]
	}

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
		{ obj.isKindOf(RCDurList) and: { obj.owner.isKindOf(RCBeat) } } {
			^IdentityDictionary[\rc -> "durList", \song -> obj.owner.songName.asString, \layer -> obj.owner.layer.key.asString, \name -> obj.owner.name.asString]
		}
		{ obj.isKindOf(RCSwing) and: { obj.layer.notNil } } { ^IdentityDictionary[\rc -> "swing", \song -> obj.layer.songName.asString, \layer -> obj.layer.key.asString] }
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
			\durList, { ^s !? { |x| x.layer(ref[\layer] ? \core) !? { |l| l.beat(name) !? (_.durList) } } },
			\swing, { ^s !? { |x| x.layer(ref[\layer] ? \core) !? (_.swing) } },
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
