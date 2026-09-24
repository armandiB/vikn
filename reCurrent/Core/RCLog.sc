// reCurrent — tagged, rate-limited logging.
//
// Every message goes through one tag. When the same tag fires again within
// `rateLimit` seconds the message is swallowed and counted; the next message
// that gets through carries "(+N suppressed)". This keeps a misbehaving
// pattern from flooding the post window (which stalls the IDE) while still
// reporting every distinct problem at least once per second.
//
// Usage:
//   RCLog.post(\beat, "started");
//   RCLog.warn(\dur, "clamped");
//   RCLog.error(\osc, "no handler");
//   RCLog.exception(\crawler, err, "next_val");   // formats an Exception
//   RCLog.warn(\dur, { "dur % clamped".format(x) });   // formatted only when emitted
//
// A message may be a Function: it is evaluated only when the line gets
// through, so a per-event call site pays nothing while rate-limited.
// The limiter is keyed by tag then level (no Symbol is interned per call)
// and forgets tags silent for more than a minute once it holds maxTags.

RCLog {
	classvar <>verbose = true;        // RCLog.info is silent when false
	classvar <>rateLimit = 1.0;       // seconds, per tag
	classvar <>historySize = 200;     // number of emitted lines kept in `history`
	classvar <>maxTags = 512;         // limiter entries kept before pruning
	classvar <>timeFunc;              // { seconds } — replaceable for tests
	classvar <lastTimes, <suppressedCounts, <history;

	*initClass {
		lastTimes = IdentityDictionary.new;
		suppressedCounts = IdentityDictionary.new;
		history = List.new;
		timeFunc = { Main.elapsedTime };
	}

	*reset {
		lastTimes.clear;
		suppressedCounts.clear;
		history.clear;
	}

	// force: bypass the rate limit (for bounded lifecycle messages such as
	// "beat stopped", never for per-event messages).
	*post { |tag, msg, force = false| ^this.prEmit(tag, msg, \post, force) }
	*info { |tag, msg, force = false| if(verbose) { ^this.prEmit(tag, msg, \post, force) }; ^false }
	*warn { |tag, msg, force = false| ^this.prEmit(tag, msg, \warn, force) }
	*error { |tag, msg, force = false| ^this.prEmit(tag, msg, \error, force) }

	*exception { |tag, err, context, force = false|
		var text = this.describe(err);
		context !? { text = context.asString ++ ": " ++ text };
		^this.prEmit(tag, text, \error, force)
	}

	// Human-readable one-liner for an Exception (or anything else).
	*describe { |err|
		if(err.respondsTo(\errorString)) {
			^err.errorString.asString.replace("ERROR: ", "")
		};
		^err.asString
	}

	*format { |tag, msg| ^"RC[%]: %".format(tag, msg) }

	// Returns true if the line was emitted, false if rate-limited.
	// The limit is per (tag, level): a flood of warnings never hides an error.
	*prEmit { |tag, msg, level, force = false|
		var now = timeFunc.value;
		var perTag, last, text, suppressed, counts;
		if(tag.isKindOf(String)) { tag = tag.asSymbol };   // identity keys below
		perTag = lastTimes[tag] ?? { var d = IdentityDictionary.new; lastTimes[tag] = d; d };
		last = perTag[level];
		if(force.not and: { last.notNil } and: { (now - last) < rateLimit }) {
			counts = suppressedCounts[tag] ?? { var d = IdentityDictionary.new; suppressedCounts[tag] = d; d };
			counts[level] = (counts[level] ? 0) + 1;
			^false
		};
		perTag[level] = now;
		if(lastTimes.size > maxTags) { this.prPrune(now) };
		suppressed = suppressedCounts[tag] !? { |d| d.removeAt(level) };
		text = this.format(tag, msg.value);
		if(suppressed.notNil and: { suppressed > 0 }) {
			text = text ++ " (+% suppressed)".format(suppressed)
		};
		history.add([now, level, text]);
		while { history.size > historySize } { history.removeAt(0) };
		switch(level,
			\warn, { text.warn },
			\error, { text.error },
			{ text.postln }
		);
		^true
	}

	// Drop the limiter entries of tags silent for more than a minute; if that
	// is not enough, start over (worst case: one extra line per tag).
	*prPrune { |now|
		lastTimes.keys.copy.do { |tag|
			var times = lastTimes[tag];
			if(times.isEmpty or: { times.values.maxItem < (now - 60) }) {
				lastTimes.removeAt(tag);
				suppressedCounts.removeAt(tag);
			};
		};
		if(lastTimes.size > maxTags) {
			lastTimes.clear;
			suppressedCounts.clear;
		};
	}
}
