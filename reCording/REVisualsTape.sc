// reCording — the visuals tape: what a piece sends to HomewareVisuals through RCVisuals, with the
// time of each message, as HomewareVisuals' scripts/viz-record.mjs records it from the bridge:
//   [[seconds, "/address", arg, ...], ...]
// one entry per line in the file, plain JSON (Symbols as strings: the pages read plain strings).
// A render records one through the sink (the offline clock's seconds), shifts it to the take's
// start (the WAV's timeline) and writes it next to the render's WAV, <take>/<take>.viz.json
// (REScore.tapePath); a rendered take played live (RERenderedPlayer) plays it back through
// RCVisuals, and a page plays it alone with ?replay=<url>.
//
//   t = REVisualsTape.record({ clock.seconds });   // the sink installed, the previous one kept
//   ... the render runs ...
//   t.finish;                                      // the previous sink back
//   t.shifted(startSecs).write(path);
//   t = REVisualsTape.read(path);
//   t = REVisualsTape.read(path, skip: ['/lsys/state'], rate: ('/lsys/state': 20));   // less of it
//   REVisualsTape.load(path, action: { |t| ... });  // the same without stopping the interpreter
//   t.play(atSecs);                                // on SystemClock, each entry at atSecs + its seconds
//   t.stop;

REVisualsTape {
	classvar <>chunkBytes = 1048576;   // the text a read compiles at once (a load yields between two)
	classvar <>cacheSize = 2, cache, <reads = 0;   // the last tapes read, by path, size, date and options; files parsed
	var <entries, <timeFunc, savedSink, <isRecording = false, routine;

	*new { ^super.new.initREVisualsTape }

	initREVisualsTape { entries = List.new }

	//////// recording

	// Installs the sink: every message RCVisuals sends goes here with timeFunc.value as its time
	// (the calling thread's seconds by default). Returns the tape.
	*record { |timeFunc| ^this.new.record(timeFunc) }

	record { |func|
		if(isRecording) { ^this };
		timeFunc = func ?? { { thisThread.seconds } };
		savedSink = RCVisuals.sink;
		RCVisuals.sink = { |path, args| this.add(timeFunc.value, path, args) };
		isRecording = true;
		^this
	}

	// The previous sink back.
	finish {
		if(isRecording) {
			RCVisuals.sink = savedSink;
			savedSink = nil;
			isRecording = false;
		};
		^this
	}

	add { |secs, path, args|
		entries.add([secs.asFloat, path.asString] ++ REScoreVisuals.plain((args ? []).asArray));
		^this
	}

	// Entries from elsewhere (the state taps' frames, merged): added, then sorted by time (stable).
	addEntries { |list|
		list.do { |e| entries.add(e) };
		^this.sort
	}

	sort {
		entries = entries.asArray.sort { |a, b| a[0] <= b[0] }.as(List);
		^this
	}

	size { ^entries.size }
	isEmpty { ^entries.isEmpty }
	duration { ^entries.last !? (_[0]) ? 0 }

	// A new tape with every time minus secs, clamped at 0 (as a render's score lines are).
	shifted { |secs|
		var t = this.class.new;
		entries.do { |e| t.entries.add([(e[0] - secs).max(0)] ++ e[1..]) };
		^t
	}

	// address → count
	countBy {
		var d = Dictionary.new;
		entries.do { |e| d[e[1]] = (d[e[1]] ? 0) + 1 };
		^d
	}

	//////// the file

	write { |path|
		var f;
		path = path.asString.standardizePath;
		f = File(path, "w");
		if(f.isOpen.not) { Error("REVisualsTape: cannot write " ++ path).throw };
		protect {
			f.write("[");
			entries.do { |e, i|
				if(i > 0) { f.write(",") };
				f.write("\n");
				f.write(this.class.prEntryString(e));
			};
			f.write("\n]\n");
		} { f.close };
		^path
	}

	*prEntryString { |e|
		^"[" ++ e.collect { |v| this.prValueString(v) }.join(",") ++ "]"
	}

	*prValueString { |v|
		case
		{ v.isNumber } { ^if(v.isNaN or: { v == inf } or: { v == -inf }) { "0" } { if(v.isInteger) { v.asString } { v.asFloat.asString } } }
		{ v.isString } { ^this.prQuote(v) }
		{ v.isKindOf(Symbol) } { ^this.prQuote(v.asString) }
		{ v == true } { ^"true" }
		{ v == false } { ^"false" }
		{ v.isNil } { ^"null" }
		{ v.isSequenceableCollection } { ^"[" ++ v.collect { |x| this.prValueString(x) }.join(",") ++ "]" };
		^this.prQuote(v.asString)
	}

	*prQuote { |s|
		var out = "\"";
		s.do { |c|
			switch(c,
				$", { out = out ++ "\\\"" },
				$\\, { out = out ++ "\\\\" },
				$\n, { out = out ++ "\\n" },
				$\r, { out = out ++ "\\r" },
				$\t, { out = out ++ "\\t" },
				{ if(c.ascii < 32) { out = out ++ "\\u" ++ c.ascii.asHexString(4).toLower } { out = out ++ c } }
			);
		};
		^out ++ "\""
	}

	// The file read back: times and numbers as numbers, strings as strings. The file is read whole,
	// cut into pieces of about chunkBytes at line ends (a JSON string holds no line end), and each
	// piece compiled as an sclang literal array, #[...]: a JSON array of numbers and strings is one,
	// and the compiler types the numbers itself. (String:parseJSON answered every scalar as a String,
	// typed back by two regular expressions each: Garden's tape of Oct 24 2026, 323 MB and 46M values,
	// took minutes; compiled, 4.5 s.) A piece with JSON's \u escapes, which sclang does not read, or
	// one the compiler refuses goes through parseJSON. The file is compiled: read the tapes you made.
	// skip: addresses left out. rate: (address: frames a second), an address's entries thinned per
	// first argument (a tap's frames: /lsys/state's start slot). The last cacheSize tapes read are
	// kept (by path, size, date and options): reading one again is a copy. Nil when the file is
	// missing or not a tape.
	*read { |path, skip, rate| ^this.prRead(path, skip, rate, false) }

	// The same in a Routine on AppClock that yields between two pieces, so that the interpreter goes
	// on scheduling meanwhile (progress posted); action.(tape) at the end. Returns the Routine.
	*load { |path, skip, rate, action|
		^Routine { action.value(this.prRead(path, skip, rate, true)) }.play(AppClock)
	}

	*clearCache { cache = nil }

	*prCacheKey { |path, skip, rate| ^[path, File.fileSize(path), File.mtime(path), skip, rate].asCompileString }

	prSetEntries { |list| entries = list }

	*prRead { |path, skip, rate, yield = false|
		var key, hit, text, list, pos, start, end, b, piece, got, t0 = Main.elapsedTime, step = 0;
		var skipSet, rates, last = IdentityDictionary.new;
		path = path.asString.standardizePath;
		if(File.exists(path).not) { RCLog.warn(\tape, "no tape at " ++ path); ^nil };
		key = this.prCacheKey(path, skip, rate);
		hit = cache !? { |c| c.detect { |pair| pair[0] == key } };
		if(hit.notNil) { ^this.new.prSetEntries(hit[1].copy) };
		skipSet = skip !? { |s| s.asArray.collect(_.asSymbol).as(IdentitySet) };
		rates = rate !? { |r| var d = IdentityDictionary.new; r.keysValuesDo { |k, v| d[k.asSymbol] = v }; d };
		text = File.readAllString(path);
		pos = text.find("[");
		end = text.size - 1;
		while { end > 0 and: { text[end] != $] } } { end = end - 1 };
		if(pos.isNil or: { end <= pos }) { RCLog.warn(\tape, "not a tape: " ++ path); ^nil };
		pos = pos + 1;
		start = pos;
		list = List.new;
		while { pos < end } {
			b = (text.find("\n", false, (pos + chunkBytes).min(end)) ? end).min(end);
			piece = text.copyRange(pos, b - 1);
			while { piece.notEmpty and: { piece.last.isSpace or: { piece.last == $, } } } { piece = piece.drop(-1) };
			if(piece.notEmpty) {
				got = this.prPiece(piece);
				if(got.isNil) { RCLog.warn(\tape, "not a tape (the text from character %): %".format(pos, path)); ^nil };
				got.do { |e| this.prKeep(e, skipSet, rates, last) !? { |kept| list.add(kept) } };
			};
			pos = b + 1;
			if(yield) {
				if(((pos - start) / (end - start) * 10).floor > step) {
					step = ((pos - start) / (end - start) * 10).floor;
					RCLog.post(\tape, "reading %: %/10".format(path.basename, step.min(10).asInteger), true);
				};
				0.wait;
			};
		};
		reads = reads + 1;
		cache = ([[key, list]] ++ (cache ? [])).keep(cacheSize.max(0));
		RCLog.post(\tape, "read % entries from % in % s".format(list.size, path.basename, (Main.elapsedTime - t0).round(0.01)), true);
		^this.new.prSetEntries(list.copy)
	}

	// A piece of the file (entries separated by commas) as an Array of entries, nil when unreadable.
	*prPiece { |piece|
		var got;
		if(piece.find("\\u").isNil) { got = ("#[" ++ piece ++ "]").interpret };
		if(got.isArray) { ^if(piece.find("null").notNil) { got.collect { |e| this.prNulls(e) } } { got } };
		got = try { ("[" ++ piece ++ "]").parseJSON } { nil };
		if(got.isNil or: { got.isSequenceableCollection.not } or: { got.isString }) { ^nil };
		^got.collect { |e|
			if(e.isSequenceableCollection and: { e.isString.not }) {
				e.collect { |v, i| if(i == 1) { v } { this.prNumber(v) ? v } }
			} { e }
		}
	}

	// JSON's null compiled as the Symbol 'null': nil, as written
	*prNulls { |e| ^if(e.isArray and: { e.includes('null') }) { e.collect { |v| if(v == 'null') { nil } { v } } } { e } }

	// An entry as kept: [secs, "/address", args...], nil when it is not one, skipped or thinned out.
	*prKeep { |e, skipSet, rates, last|
		var addr, fps, d, lt;
		if(e.isArray.not or: { e.size < 2 }) { ^nil };
		if(e[0].isNumber.not or: { e[1].isString.not }) { e = [this.prNumber(e[0]) ? 0, e[1].asString] ++ e[2..] };
		if(skipSet.isNil and: { rates.isNil }) { ^e };
		addr = e[1].asSymbol;
		if(skipSet.notNil and: { skipSet.includes(addr) }) { ^nil };
		fps = rates !? { |r| r[addr] };
		if(fps.notNil and: { fps > 0 }) {
			d = last[addr] ?? { last[addr] = Dictionary.new };
			lt = d[e[2] ? \none];
			if(lt.notNil and: { (e[0] - lt) < (fps.reciprocal - 1e-9) }) { ^nil };
			d[e[2] ? \none] = e[0];
		};
		^e
	}

	// a number when the string is one, else nil
	*prNumber { |v|
		if(v.isNumber) { ^v };
		if(v.isString.not) { ^nil };
		if("^-?[0-9]+$".matchRegexp(v)) { ^v.asInteger };
		if("^-?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?$".matchRegexp(v)) { ^v.asFloat };
		^nil
	}

	//////// playback

	// A Routine on clock (SystemClock): atSecs (now) is the time of the tape's 0, each entry sent
	// at atSecs + its seconds through RCVisuals.sendArgs, or func.(path, args); the entries before
	// `from` seconds are skipped (a take played from a later beat: the Routine starts at atSecs +
	// from, where the first kept entry can land). Returns the Routine.
	play { |atSecs, clock, func, from = 0|
		var list = entries.asArray.select { |m| m[0] >= from };
		var send = func ?? { { |path, args| RCVisuals.sendArgs(path.asSymbol, args) } };
		clock = clock ? SystemClock;
		this.stop;
		routine = Routine {
			var last = from;
			list.do { |m|
				var dt = m[0] - last;
				if(dt > 0) { dt.wait };
				last = m[0];
				RCGuard.call(\tape, nil) { send.value(m[1], m[2..]) };
			};
			routine = nil;
		};
		clock.schedAbs((atSecs ?? { clock.seconds }) + from, routine);
		^routine
	}

	stop {
		routine !? (_.stop);
		routine = nil;
	}

	isPlaying { ^routine.notNil }

	printOn { |stream| stream << "REVisualsTape(" << entries.size << " entries, " << this.duration.round(0.01) << " s" << if(isRecording) { ", recording)" } { ")" } }
}
