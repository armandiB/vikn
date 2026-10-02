// reCording — Routines the recorder can see. A Routine made with RETap.routine (an
// RERoutine) carries the cause open when it was created (the code line or the input being
// recorded, RETap.frame) and the object whose work it does (by). What it does to the
// reCurrent objects is the program's work, level 2: recorded under that cause when level 2
// is on (REScoreRecorder.level2), and a replay that defers the cause plays it from the take
// instead of running the Routine. A plain Routine's actions are seen by no one, unless a
// tagged one resumed it (a key's stream inside a beat's player, a seeded draw: RETap.taggedThread
// climbs the parents).
//
//   RETap.routine { 8.do { |i| ~b.set(\amp, i / 8); 1.wait } }.play(~song.clock)
//   RETap.task({ ... }, ~song.clock, by: ~batch)
//   REEventStreamPlayer(stream, event, by: beat)       // RCBeat's player: its events' actions are the beat's
//
// Off (nothing armed) they are Routines like any other: the tags cost one slot each.

RERoutine : Routine {
	var <>cause, <>by;

	// Made inside a tagged Routine, a new one inherits its cause and doer (a seeded draw, a
	// worker spawned by a loop); made on the main thread, it takes the cause open now.
	*new { |func, stackSize = 512, by, cause|
		var outer = if(thisThread.isKindOf(RERoutine)) { thisThread } { nil };
		^super.new(func, stackSize)
			.by_(by ?? { outer !? (_.by) })
			.cause_(cause ?? { outer !? (_.cause) ?? { RETap.frame } })
	}
}

// A Task whose Routine is tagged (Task builds its own Routine: this one builds an RERoutine).
RETask : PauseStream {
	*new { |func, clock, by, cause|
		var new;
		new = super.new(RERoutine({ |inval|
			protect { func.value(inval) } { new.streamError }
		}, by: by, cause: cause ?? { RETap.frame }), clock);
		^new
	}

	refresh { stream = originalStream.threadPlayer_(this) }
	storeArgs { ^originalStream.storeArgs ++ if(clock != TempoClock.default) { clock } }
}

// An EventStreamPlayer whose Routine (the one that plays the events) is tagged: what an
// event's play does to the reCurrent objects is the doer's work. The stream itself is
// tagged by whoever makes it (RCBeat: an RERoutine around the pattern).
REEventStreamPlayer : EventStreamPlayer {
	var <>by, <>cause;

	*new { |stream, event, by, cause|
		^super.new(stream, event).by_(by).cause_(cause ?? { RETap.frame }).prTagRoutine
	}

	prTagRoutine {
		routine = RERoutine({ |inTime|
			protect {
				loop { inTime = this.prNext(inTime).yield }
			} { |result|
				if(result.isException) { this.streamError }
			}
		}, by: by, cause: cause);
	}
}
