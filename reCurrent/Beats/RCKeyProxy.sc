// reCurrent — one key of a beat's pattern: its source (a static value, a
// Function, a Pattern or a Stream), its seed and its live edits.
//
// Replaces the PatternProxy + Pseed + Pcollect stack that PbindProxy gave
// every key of an RCBeat. That stack ran two to four Routines per key per
// event (a static value alone costs two: Object.streamArg wraps it in a
// Routine and PatternProxy in another) and a closure per value. RCKeyStream,
// the stream of a proxy, reads a static value with .next (a Ref dereferences,
// an Event composes, as under PatternProxy) and runs every other source in
// one Routine of its own: Routine { |inval| source.embedInStream(inval) }
// with randSeed = seed when the key is seeded (Pseed's own construction),
// sharing the creating thread's random state otherwise, as any new Routine
// does. Seeded output is bit-identical to the old stack (TestRCKeyProxy holds
// goldens built from it). A Stream given as a value is used as is.
//
// Why a Routine and not an inline call with the key's random state swapped
// in: a Routine that never seeded itself shares its parents' random state
// (one array up to the thread that last called randSeed_), so writing a
// state into the Pbind's thread reaches the main thread and everything
// drawing there. A Routine resumes in a tenth of a microsecond.
//
// Live edits: setSource(source, quant, seed). quant nil: the new source gives
// the next value. A number, [beats, phase] or Quant: the grid time is taken at
// the first pull after the edit, the old source plays until then, gets one
// more (discarded) pull at the switch and the new one takes over; a later edit
// whose grid comes first wins and drops the earlier one; an old source that
// ends before its grid hands over at once. That is PatternProxy's
// constrainStream / PfinQuant timing, event for event (TestRCKeyProxy runs
// both in lockstep on a clock). lastValue is recorded for every non-static
// source (RCUtil.isStatic), as the mirror did.

RCKeyProxy : Pattern {
	var <key, <source, <seed, <>clock, <quant, <mirror;
	var <version = 0, <lastValue, <activeStream;

	*new { |key, source, seed, clock, mirror = true|
		^super.new.initRCKeyProxy(key, source, seed, clock, mirror)
	}

	initRCKeyProxy { |keyarg, sourcearg, seedarg, clockarg, mirrorarg|
		key = keyarg.asSymbol;
		source = sourcearg;
		seed = seedarg;
		clock = clockarg;
		mirror = mirrorarg;
	}

	// A live edit: every stream of this proxy switches to newSource at newQuant
	// (nil: at its next value; a number, [beats, phase] or Quant: on that grid
	// of `clock`). newSeed nil leaves the new source unseeded, as RCBeat.set does.
	setSource { |newSource, newQuant, newSeed|
		source = newSource;
		quant = newQuant;
		seed = newSeed;
		version = version + 1;
	}

	asStream { ^RCKeyStream(this) }

	embedInStream { |inval|
		var stream = this.asStream, val;
		while { (val = stream.next(inval)).notNil } { inval = val.yield };
		^inval
	}

	// How a stream treats a source: \stream (used as is), \pattern (a Pattern,
	// or a Function called per event: one Routine), \static (read with .next).
	*classify { |value|
		if(value.isKindOf(Stream)) { ^\stream };
		if(value.isKindOf(Pattern) or: { value.isKindOf(Function) }) { ^\pattern };
		^\static
	}

	// The thread of the stream that last produced a value: the Routine of a
	// Pattern or Function source, nil for a static.
	thread { ^activeStream !? (_.thread) }
	randData { ^activeStream !? (_.randData) }
	randData_ { |data| activeStream !? (_.randData_(data)) }

	prRecord { |value, stream|
		lastValue = value;
		activeStream = stream;
	}

	storeArgs { ^[key, source, seed] }
	printOn { |stream| stream << "RCKeyProxy(" << key << ")" }
}

// The stream of an RCKeyProxy (see there), pulled from the Pbind's thread.
RCKeyStream : Stream {
	var proxy, seenVersion;
	var kind, staticSource, inner, thread, recording;
	var pending;   // edits detected at earlier pulls, oldest first: [source, seed, grid beat or nil]

	*new { |proxy| ^super.new.initRCKeyStream(proxy) }

	initRCKeyStream { |proxyarg|
		proxy = proxyarg;
		seenVersion = proxy.version;
		pending = List.new;
		this.prInstall(proxy.source, proxy.seed);
	}

	prInstall { |source, seed|
		kind = RCKeyProxy.classify(source);
		recording = proxy.mirror and: { RCUtil.isStatic(source).not };
		staticSource = nil; inner = nil; thread = nil;
		switch(kind,
			\pattern, {
				var func;
				if(source.isKindOf(Function)) { func = source } {
					if(source.isKindOf(Pfunc) and: { source.resetFunc.isNil }) { func = source.nextFunc };
				};
				if(func.notNil) {
					// a Function or a Pfunc, called per event in its own Routine until it
					// returns nil, as FuncStream.embedInStream does, minus FuncStream's
					// environment switch (a Routine already runs in its creation environment)
					inner = Routine { |inval| var val; while { (val = func.value(inval)).notNil } { inval = val.yield } };
				} {
					if(seed.notNil) {
						inner = Routine { |inval| source.embedInStream(inval) };
					} {
						inner = source.asStream;
						// a stream that is no thread would run in the Pbind's thread: give it
						// a Routine, as PatternProxy's stack did
						if(inner.isKindOf(Thread).not) { inner = Routine { |inval| source.embedInStream(inval) } };
					};
				};
				if(seed.notNil) { inner.randSeed = seed };
				thread = inner;
			},
			\stream, {
				inner = source;
				if(inner.isKindOf(Thread)) { thread = inner };
			},
			{ staticSource = source }
		);
	}

	next { |inval|
		var value, index;
		if(proxy.version != seenVersion) {
			seenVersion = proxy.version;
			if(proxy.quant.isNil) {
				pending = List[[proxy.source, proxy.seed, nil]];   // replaces every pending grid edit
			} {
				pending.add([proxy.source, proxy.seed, proxy.quant.asQuant.nextTimeOnGrid(this.prClock)]);
			};
		};
		if(pending.notEmpty) {
			index = this.prDueIndex;
			if(index.notNil) {
				if(pending[index][2].notNil) { this.prPull(inval) };   // PfinQuant's last pull of the old source, discarded
				this.prSwitchTo(index);
			} {
				value = this.prPull(inval);
				if(value.notNil) { ^this.prRecord(value) };
				this.prSwitchTo(0);   // the old source ended before its grid: the oldest edit takes over now
			};
		};
		value = this.prPull(inval);
		^this.prRecord(value)
	}

	prClock { ^proxy.clock ? thisThread.clock }

	// The latest pending edit whose time has come (a nil grid beat is "now").
	prDueIndex {
		var beats;
		forBy(pending.size - 1, 0, -1) { |i|
			var due = pending[i][2];
			if(due.isNil) { ^i };
			beats = beats ?? { this.prClock.beats };
			if(beats >= due) { ^i };
		};
		^nil
	}

	// Install the pending edit at `index`; the earlier ones are superseded, later ones stay pending.
	prSwitchTo { |index|
		var edit = pending[index];
		(index + 1).do { pending.removeAt(0) };
		this.prInstall(edit[0], edit[1]);
	}

	prPull { |inval|
		if(kind == \static) { ^staticSource.next(inval) };
		^inner.next(inval)
	}

	prRecord { |value|
		if(recording and: { value.notNil }) { proxy.prRecord(value, this) };
		^value
	}

	thread { ^thread }
	randData { ^thread !? (_.randData) }
	randData_ { |data| thread !? { |t| t.randData = data } }
	reset { inner !? (_.reset) }
}
