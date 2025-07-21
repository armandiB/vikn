PbrownStart : Pbrown {
	var <>start;

	*new { arg lo=0.0, hi=1.0, step=0.125, start, length=inf;
		^super.newCopyArgs(lo, hi, step, length, start)
	}

	storeArgs { ^[lo,hi,step,length,start] }

	embedInStream { arg inval;
		var cur;
		var loStr = lo.asStream, loVal;
		var hiStr = hi.asStream, hiVal;
		var stepStr = step.asStream, stepVal;
		var starting = false;

		if(start.notNil) {
			starting = true;
			cur = start;
		} {
			loVal = loStr.next(inval);
			hiVal = hiStr.next(inval);
			stepVal = stepStr.next(inval);
			cur = rrand(loVal, hiVal);
			if(loVal.isNil or: { hiVal.isNil } or: { stepVal.isNil }) { ^inval };
		};

		length.value(inval).do {
			if(starting) {
				starting = false;
				inval = cur.yield;
			} {
				loVal = loStr.next(inval);
				hiVal = hiStr.next(inval);
				stepVal = stepStr.next(inval);
				if(loVal.isNil or: { hiVal.isNil } or: { stepVal.isNil }) { ^inval };
				cur = this.calcNext(cur, stepVal).fold(loVal, hiVal);
				inval = cur.yield;
			};
		};

		^inval;
	}
}

PgbrownStart : PbrownStart {
	calcNext { arg cur, step;
		^cur * (1 + step.xrand2)
	}
}

PconstRestSafe : Pconst {
	embedInStream { arg inval;
		var delta, elapsed = 0.0, nextElapsed, str=pattern.asStream,
		localSum = sum.value(inval);
		loop ({
			delta = str.next(inval);
			if(delta.isNil) {
				inval = Rest(localSum - elapsed).yield;
				^inval
			};
			nextElapsed = elapsed + if(delta.isRest) {delta.value} {delta};
			if (nextElapsed.round(tolerance) >= localSum) {
				inval = if(delta.isRest) {Rest(localSum - elapsed)} {localSum - elapsed}.yield;
				^inval
			}{
				elapsed = nextElapsed;
				inval = delta.yield;
			};
		});
	}
}

PparNoDefault : Ppar {
	embedInStream { arg inval;
		var assn;
		var priorityQ = PriorityQueue.new;

		repeats.value(inval).do({ arg j;
			var outval, stream, nexttime, now = 0.0;

			this.initStreams(priorityQ);

			// if first event not at time zero
			if (priorityQ.notEmpty and: { (nexttime = priorityQ.topPriority) > 0.0 }) {
				outval = Event.silentNoDefault(nexttime, inval);
				inval = outval.yield;
				now = nexttime;
			};

			while { priorityQ.notEmpty } {
				stream = priorityQ.pop;
				outval = stream.next(inval).asEvent;
				if (outval.isNil) {
					nexttime = priorityQ.topPriority;
					if (nexttime.notNil, {
						// that child stream ended, so rest until next one
						outval = Event.silentNoDefault(nexttime - now, inval);
						inval = outval.yield;
						now = nexttime;
					},{
						priorityQ.clear;
					});
				} {
					// requeue stream
					priorityQ.put(now + outval.delta, stream);
					nexttime = priorityQ.topPriority;
					outval.put(\delta, nexttime - now);

					inval = outval.yield;
					// inval ?? { this.purgeQueue(priorityQ); ^nil.yield };
					now = nexttime;
				};
			};
		});
		^inval;
	}
}

+ Event {
	*silentNoDefault { |dur(1.0), inEvent|
		var delta;
		if(inEvent.isNil) { inEvent = Event.new }
		{ inEvent = inEvent.copy };
		delta = dur * (inEvent[\stretch] ? 1);
		if(dur.isRest.not) { dur = Rest(dur) };
		// Not changing parent event to default event
		inEvent.put(\dur, dur).put(\delta, delta);
		^inEvent
	}
}

// Fix for Bag while waiting pull request approval
+ Bag {
	copy { ^this.class.newCopyArgs(contents.copy) }
}
