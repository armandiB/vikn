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

	*post { |tag, msg| ^this.prEmit(tag, msg, \post) }
	*info { |tag, msg| if(verbose) { ^this.prEmit(tag, msg, \post) }; ^false }
	*warn { |tag, msg| ^this.prEmit(tag, msg, \warn) }
	*error { |tag, msg| ^this.prEmit(tag, msg, \error) }

	*exception { |tag, err, context|
		var text = this.describe(err);
		context !? { text = context.asString ++ ": " ++ text };
		^this.prEmit(tag, text, \error)
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
	*prEmit { |tag, msg, level|
		var key = tag.asSymbol;
		var now = timeFunc.value;
		var last = lastTimes[key];
		var text, suppressed;
		if(last.notNil and: { (now - last) < rateLimit }) {
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
