// reLation — a quantale: the values a relation can take, and how they combine.
//
// A relation with values says more than yes or no about two objects: a cost, a strength. A quantale gives
// the two operations that composing relations needs: mul combines the values met along a walk, join
// chooses between walks. zero is "not related" (it absorbs mul and is neutral for join), unit the value of
// staying in place.
//
//   quantale         values     along a walk (mul)   between walks (join)   zero   unit
//   boolean          0, 1       and                  or                     0      1
//   lawvere          [0, inf]   sum                  min                    inf    0
//   multiplicative   [0, 1]     product              max                    0      1
//   interval         [0, 1]     min                  max                    0      1
//
//   RLQuantale.lawvere.mul(2, 3);     // 5: costs add along a walk
//   RLQuantale.lawvere.join(2, 3);    // 2: the cheaper walk wins
//
// With the Lawvere quantale, composing relations is the min-plus product: the powers of a relation of costs
// hold the cheapest walks of each length (RLRelation:closure, the distance).

RLQuantale {
	classvar <boolean, <lawvere, <multiplicative, <interval;
	var <name, <zero, <unit, mulFunc, joinFunc;

	*initClass {
		boolean = this.new(\boolean, 0.0, 1.0, { |a, b| min(a, b) }, { |a, b| max(a, b) });
		lawvere = this.new(\lawvere, inf, 0.0, { |a, b| a + b }, { |a, b| min(a, b) });
		multiplicative = this.new(\multiplicative, 0.0, 1.0, { |a, b| a * b }, { |a, b| max(a, b) });
		interval = this.new(\interval, 0.0, 1.0, { |a, b| min(a, b) }, { |a, b| max(a, b) });
	}

	// A quantale of one's own: mulFunc and joinFunc take two values.
	*new { |name, zero, unit, mulFunc, joinFunc| ^super.newCopyArgs(name, zero, unit, mulFunc, joinFunc) }

	*named { |name|
		^switch(name.asSymbol,
			\boolean, { boolean }, \lawvere, { lawvere }, \multiplicative, { multiplicative }, \interval, { interval },
			{ Error("RLQuantale: no quantale named %".format(name)).throw })
	}

	mul { |a, b| ^mulFunc.value(a, b) }
	join { |a, b| ^joinFunc.value(a, b) }

	// The order of the values: a is below b when joining them gives b (in lawvere, a larger cost is below).
	leq { |a, b| ^joinFunc.value(a, b) == b }

	// A value from what a rule answers: true is unit, false and nil are zero, a number is itself.
	coerce { |v|
		^if(v == true) { unit } { if(v.isNil or: { v == false }) { zero } { v.asFloat } }
	}

	printOn { |stream| stream << "RLQuantale." << name }
}
