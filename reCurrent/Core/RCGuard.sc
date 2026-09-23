// reCurrent — failure isolation helpers.
//
// The library calls a lot of user code (OSC/MIDI handlers, transparency
// functions, note algorithms, computed static attributes...). Every such call
// goes through RCGuard.call so that an error in one of them is reported once
// (rate-limited, tagged) and replaced by a fallback value instead of killing
// the caller. `strict = true` rethrows instead, for tests and development.
//
//   RCGuard.call(\transparency, 0) { fobject.transparencyFunc.(zpos, pos) }
//   handler = RCGuard.wrap(\osc, nil) { |msg| ... };   // guarded Function

RCGuard {
	classvar <>strict = false;

	// Evaluate `func`; on error log it under `tag` and return `fallback.value`.
	*call { |tag, fallback, func|
		^try {
			func.value
		} { |err|
			if(strict) { err.throw };
			RCLog.exception(tag, err);
			fallback.value
		}
	}

	// Same, but the fallback function receives the error.
	*callWithError { |tag, fallback, func|
		^try {
			func.value
		} { |err|
			if(strict) { err.throw };
			RCLog.exception(tag, err);
			fallback.value(err)
		}
	}

	// Returns a Function whose every call is guarded.
	*wrap { |tag, fallback, func|
		^{ |...args| this.call(tag, fallback) { func.valueArray(args) } }
	}

	// Replace NaN / inf (recursively in collections) by `fallback`, with a warning.
	*finite { |x, fallback = 0, tag = \finite|
		if(x.isNumber) {
			if(x.isNaN or: { x.abs == inf }) {
				RCLog.warn(tag, "non-finite value % replaced by %".format(x, fallback));
				^fallback
			};
			^x
		};
		if(x.isSequenceableCollection and: { x.isKindOf(RawArray).not }) {
			^x.collect { |el| this.finite(el, fallback, tag) }
		};
		^x
	}

	*isFinite { |x|
		if(x.isNumber) { ^(x.isNaN or: { x.abs == inf }).not };
		if(x.isSequenceableCollection and: { x.isKindOf(RawArray).not }) {
			^x.every { |el| this.isFinite(el) }
		};
		^true
	}

	// while-loop with an iteration cap. Returns true when the condition ended
	// the loop, false when the cap did (an error is logged under `tag`).
	*boundedLoop { |maxIter, tag, condFunc, bodyFunc|
		var i = 0;
		while { condFunc.value } {
			if(i >= maxIter) {
				RCLog.error(tag, "loop exceeded % iterations, aborted".format(maxIter));
				^false
			};
			bodyFunc.value(i);
			i = i + 1;
		};
		^true
	}
}
