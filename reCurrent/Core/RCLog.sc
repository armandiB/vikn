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

RCLog {
	classvar <>verbose = true;        // RCLog.info is silent when false
	classvar <>rateLimit = 1.0;       // seconds, per tag
	classvar <>historySize = 200;     // number of emitted lines kept in `history`
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
		var key = (tag.asString ++ "/" ++ level).asSymbol;
		var now = timeFunc.value;
		var last = lastTimes[key];
		var text, suppressed;
		if(force.not and: { last.notNil } and: { (now - last) < rateLimit }) {
			suppressedCounts[key] = (suppressedCounts[key] ? 0) + 1;
			^false
		};
		lastTimes[key] = now;
		suppressed = suppressedCounts.removeAt(key);
		text = this.format(tag, msg);
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
}
