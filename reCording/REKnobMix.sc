// reCording — one parameter's mix of a tape value T (a take's control tape: what the piece sent as
// /fx/ctl <name> <value>) and a knob K (the Roto-Control's, 0..1), for HadronLake3's show console
// (REShowRoto, REShowConsole). The output is one value a parameter, made here and sent as the page's
// /fx/ctl (the page keeps the last value it got and ignores /fx/set while a parameter is mapped).
//
//   m = REKnobMix(\add, -0.5, 0.5);        // out = T + lo + k·(hi - lo)
//   m = REKnobMix(\scale, 0, 2);           // out = T × (lo + k·(hi - lo))
//   m.tape_(0.3, now); m.knob_(0.75, now); m.value(now);
//   m.state_(2, now);                      // T sampled and held, K live
//   m.state_(1, now); m.moving(now);       // T converges to its live value over `glide` seconds: send while moving
//
// A knob never moved contributes nothing (add: 0; scale: 1). For \add with no tape value yet, T
// counts 0 (the knob's offset alone); for \scale with no tape value nothing is sent (value is nil).
// The three states a button chooses: 1 = T live ⊕ K live; 2 = T sampled and held at the value it had
// when the state was entered ⊕ K live (a hold entered before any tape value holds nothing: T counts
// 0 for \add, nil for \scale, until the hold is left); 3 = T live ⊕ K held. Leaving a hold, the held
// source converges LINEARLY from its held value to its live value over `glide` seconds (1.0), then
// follows it; entering a hold mid-glide holds the value the glide stands at. Every method takes `now`
// in seconds (any clock, the same one throughout): tests need no clock.

REKnobMix {
	var <op, <lo, <hi, <>glide;
	var <tape, <knob;           // the live sources: nil until one arrived
	var <state = 1;
	var heldTape, heldKnob;     // the values held in states 2 and 3 (heldKnob: the knob's contribution, not k)
	var tapeGlide, knobGlide;   // [from, startSecs] of a source converging to its live value, nil when none

	*new { |op = \add, lo = 0, hi = 1, glide = 1.0|
		^super.new.initREKnobMix(op, lo, hi, glide)
	}

	initREKnobMix { |oparg, loarg, hiarg, glidearg|
		op = oparg.asSymbol;
		if(#[\add, \scale].includes(op).not) {
			RCLog.warn(\knobmix, "unknown op %: \\add used".format(op));
			op = \add;
		};
		lo = (loarg ? 0).asFloat;
		hi = (hiarg ? 1).asFloat;
		glide = (glidearg ? 1.0).asFloat.max(0);
	}

	//////// the sources

	tape_ { |v, now|
		tape = v !? (_.asFloat);
	}

	knob_ { |k, now|
		knob = k !? { k.asFloat.clip(0, 1) };
	}

	// 1, 2 or 3 (anything else: 1). Leaving a hold starts the held source's glide from the value it
	// held to its live value; entering one holds the source where it stands (mid-glide included).
	state_ { |n, now|
		var new = if(#[1, 2, 3].includes(n)) { n } { 1 };
		now = now ? 0;
		if(new == state) { ^this };
		// the tape
		if(state == 2 and: { new != 2 }) {
			tapeGlide = if(heldTape.notNil and: { tape.notNil } and: { glide > 0 } and: { heldTape != tape }) { [heldTape, now] } { nil };
			heldTape = nil;
		};
		if(new == 2 and: { state != 2 }) {
			heldTape = this.prTapeNow(now);
			tapeGlide = nil;
		};
		// the knob
		if(state == 3 and: { new != 3 }) {
			knobGlide = if(glide > 0 and: { heldKnob != this.contribution(knob) }) { [heldKnob, now] } { nil };
			heldKnob = nil;
		};
		if(new == 3 and: { state != 3 }) {
			heldKnob = this.prKnobNow(now);
			knobGlide = nil;
		};
		state = new;
	}

	//////// the output

	// The output now: nil before anything arrived, and for \scale without a tape value.
	value { |now|
		var t, k;
		now = now ? 0;
		if(tape.isNil and: { knob.isNil }) { ^nil };
		t = this.prTapeNow(now);
		k = this.prKnobNow(now);
		if(op == \scale) {
			if(t.isNil) { ^nil };
			^t * k
		};
		^(t ? 0) + k
	}

	// True while a glide runs (the console then sends at its rate), and once more when one has just
	// ended, so that the live value itself goes out.
	moving { |now|
		var res = false;
		now = now ? 0;
		if(tapeGlide.notNil) {
			res = true;
			if(this.prFrac(tapeGlide, now) >= 1) { tapeGlide = nil };
		};
		if(knobGlide.notNil) {
			res = true;
			if(this.prFrac(knobGlide, now) >= 1) { knobGlide = nil };
		};
		^res
	}

	isHolding { ^state != 1 }

	// The knob's contribution to the output for a position k: nothing when it never moved.
	contribution { |k|
		if(k.isNil) { ^if(op == \scale) { 1.0 } { 0.0 } };
		^lo + (k * (hi - lo))
	}

	// the tape's effective value now: held, gliding, or live
	prTapeNow { |now|
		var f;
		if(state == 2) { ^heldTape };
		if(tapeGlide.notNil and: { tape.notNil }) {
			f = this.prFrac(tapeGlide, now);
			if(f < 1) { ^tapeGlide[0] + ((tape - tapeGlide[0]) * f) };
		};
		^tape
	}

	// the knob's effective contribution now: held, gliding, or live
	prKnobNow { |now|
		var f, live = this.contribution(knob);
		if(state == 3) { ^heldKnob ?? { this.contribution(nil) } };
		if(knobGlide.notNil) {
			f = this.prFrac(knobGlide, now);
			if(f < 1) { ^knobGlide[0] + ((live - knobGlide[0]) * f) };
		};
		^live
	}

	prFrac { |g, now| ^if(glide <= 0) { 1 } { ((now - g[1]) / glide).clip(0, 1) } }

	printOn { |stream|
		stream << "REKnobMix(" << op << ", " << lo << ".." << hi << ", state " << state;
		tape !? { stream << ", T " << tape };
		knob !? { stream << ", k " << knob };
		stream << ")";
	}
}
