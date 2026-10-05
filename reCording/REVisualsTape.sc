// reCording — the visuals tape: what a piece sends to HomewareVisuals through RCVisuals, with the
// time of each message, as HomewareVisuals' scripts/viz-record.mjs records it from the bridge:
//   [[seconds, "/address", arg, ...], ...]
// one entry per line in the file, plain JSON (Symbols as strings: the pages read plain strings).
// A render records one through the sink (the offline clock's seconds), shifts it to the take's
// start (the WAV's timeline) and writes it next to the render as <take>.viz.json; a rendered take
// played live (RERenderedPlayer) plays it back through RCVisuals, and a page plays it alone with
// ?replay=<url>.
//
//   t = REVisualsTape.record({ clock.seconds });   // the sink installed, the previous one kept
//   ... the render runs ...
//   t.finish;                                      // the previous sink back
//   t.shifted(startSecs).write(path);
//   t = REVisualsTape.read(path);
//   t.play(atSecs);                                // on SystemClock, each entry at atSecs + its seconds
//   t.stop;

REVisualsTape {
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

	// The file read back: times and numeric arguments as numbers (sclang's parseJSON answers
	// every scalar as a String), the rest as strings. Nil when the file is missing or malformed.
	*read { |path|
		var text, list, t;
		path = path.asString.standardizePath;
		if(File.exists(path).not) { RCLog.warn(\tape, "no tape at " ++ path); ^nil };
		text = File.readAllString(path);
		list = text.parseJSON;
		if(list.isNil or: { list.isSequenceableCollection.not or: { list.isString } }) { RCLog.warn(\tape, "not a tape: " ++ path); ^nil };
		t = this.new;
		list.do { |e|
			if(e.isSequenceableCollection and: { e.isString.not } and: { e.size >= 2 }) {
				t.entries.add([this.prNumber(e[0]) ? 0, e[1].asString] ++ e[2..].collect { |v| this.prNumber(v) ? v });
			};
		};
		^t
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

	// A Routine on clock (SystemClock) from atSecs (now), each entry sent at atSecs + its seconds
	// through RCVisuals.sendArgs, or func.(path, args); the entries before `from` seconds are
	// skipped (a take played from a later beat). Returns the Routine.
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
		clock.schedAbs(atSecs ?? { clock.seconds }, routine);
		^routine
	}

	stop {
		routine !? (_.stop);
		routine = nil;
	}

	isPlaying { ^routine.notNil }

	printOn { |stream| stream << "REVisualsTape(" << entries.size << " entries, " << this.duration.round(0.01) << " s" << if(isRecording) { ", recording)" } { ")" } }
}
