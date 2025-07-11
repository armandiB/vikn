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
