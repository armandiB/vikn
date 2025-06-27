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

PMergeTuples : Pattern {
    var <>patterns;

    *new { |patterns| ^super.newCopyArgs(patterns) }

    embedInStream { |event|
        var streams = patterns.collect(_.asStream);
        var clocks = Array.fill(patterns.size, 0.0);
        var nextVals = streams.collect(_.next);

        loop {
            var active = nextVals.collect(_.notNil);
			var i_min, val;
            if (active.includes(true).not) { ^nil };

            // Find the stream with the earliest clock
			i_min = clocks.select({|item, i| active[i]}).minIndex;
            val = nextVals[i_min];
            val.yield;
            clocks[i_min] = clocks[i_min] + val[0];
            nextVals[i_min] = streams[i_min].next;
        }
    }
}
