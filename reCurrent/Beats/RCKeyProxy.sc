// reCurrent — one key of a beat's pattern: its source (a static value, a
// Function or Pfunc, a Pattern or a Stream), its seed and its live edits.
//
// Replaces the PatternProxy + Pseed + Pcollect stack that PbindProxy gave
// every key of an RCBeat. That stack ran two to four Routines per key per
// event (a static value alone costs two: Object.streamArg wraps it in a
// Routine and PatternProxy in another). RCKeyStream, the stream of a proxy,
// reads a static value with .next (a Ref dereferences, an Event composes, as
// under PatternProxy), calls a Function or a Pfunc inline in the Pbind's thread
// with the key's own random state swapped in, and keeps one Routine only for a
// real Pattern. Seeded output is bit-identical to the old stack (TestRCKeyProxy
// holds goldens built from it):
//   - a seeded Pattern runs in Routine { |inval| pattern.embedInStream(inval) }
//     with randSeed = seed, Pseed's own construction;
//   - a seeded Function draws from the state randSeed_(seed) produces, the
//     state Pseed's thread had, kept per key between calls;
//   - an unseeded key starts from a copy of the creating thread's state, what
//     a new Routine inherits;
//   - a Stream given as a value is used as is, never re-seeded.
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

	// How a stream treats a source: \function (a Function, or a Pfunc without a
	// reset function, called inline), \stream (used as is), \pattern (one
	// Routine), \static (read with .next).
	*classify { |value|
		if(value.isKindOf(Function)) { ^\function };
		if(value.isKindOf(Pfunc) and: { value.resetFunc.isNil }) { ^\function };
		if(value.isKindOf(Stream)) { ^\stream };
		if(value.isKindOf(Pattern)) { ^\pattern };
		^\static
	}

	// The thread of the stream that last produced a value: a Pattern source's
	// Routine, nil for a Function or a static (they run inline).
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
	var kind, staticSource, func, envir, randState, inner, thread, recording;
	var pending;   // edits detected at earlier pulls, oldest first: [source, seed, grid beat or nil]

	*new { |proxy| ^super.new.initRCKeyStream(proxy) }

	initRCKeyStream { |proxyarg|
		proxy = proxyarg;
		seenVersion = proxy.version;
		pending = List.new;
		this.prInstall(proxy.source, proxy.seed);
	}

	prInstall { |source, seed|
		var routine;
		kind = RCKeyProxy.classify(source);
		recording = proxy.mirror and: { RCUtil.isStatic(source).not };
		staticSource = nil; func = nil; envir = nil; randState = nil; inner = nil; thread = nil;
		switch(kind,
			\function, {
				func = if(source.isKindOf(Pfunc)) { source.nextFunc } { source };
				envir = currentEnvironment;
				if(seed.notNil) {
					routine = Routine { };
					routine.randSeed = seed;
					randState = routine.randData;
				} {
					randState = thisThread.randData;
				};
			},
			\pattern, {
				if(seed.notNil) {
					inner = Routine { |inval| source.embedInStream(inval) };
					inner.randSeed = seed;
				} {
					inner = source.asStream;
					// a stream that is no thread (a Pfunc with a reset function) would
					// draw from this thread's state: give it its own copy, as a Routine has
					if(inner.isKindOf(Thread).not) { inner = Routine { |inval| source.embedInStream(inval) } };
				};
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
		var value;
		switch(kind,
			\function, {
				thisThread.randData = randState;
				value = if(currentEnvironment === envir) { func.value(inval) } { envir.use { func.value(inval) } };
				randState = thisThread.randData;
			},
			\static, { value = staticSource.next(inval) },
			{ value = inner.next(inval) }
		);
		^value
	}

	prRecord { |value|
		if(recording and: { value.notNil }) { proxy.prRecord(value, this) };
		^value
	}

	thread { ^thread }

	randData {
		if(kind == \function) { ^randState };
		^thread !? (_.randData)
	}

	randData_ { |data|
		if(kind == \function) { randState = data } { thread !? { |t| t.randData = data } };
	}

	reset { inner !? (_.reset) }
}
