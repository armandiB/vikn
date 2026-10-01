// reCurrent — swing function of a layer.
//
// value(t) returns the time offset (in beats) added to the event that starts
// at running time t. Default shape, as in BlockBeats' global_swing_fun:
//   amount * sin(pi * (t % mod)) + shift
// A custom `func` receives (t, swing) and replaces the default shape.
// Non-finite results are replaced by 0 so a bad function can never break the
// dur pipeline. Setting amount, mod or shift is recorded (RETap) under the
// layer that holds the swing (`layer`, set by RCLayer).

RCSwing {
	var <amount, <mod, <shift, <>func;
	var <>layer;   // the RCLayer it belongs to, nil for a free swing

	*new { |amount = 0, mod = 1, shift = 0, func|
		^super.newCopyArgs(amount, mod, shift, func)
	}

	amount_ { |a| if(RETap.active) { RETap.action(this, \amount_, [a]) }; amount = a }
	mod_ { |m| if(RETap.active) { RETap.action(this, \mod_, [m]) }; mod = m }
	shift_ { |s| if(RETap.active) { RETap.action(this, \shift_, [s]) }; shift = s }

	value { |t|
		var res;
		if(func.notNil) {
			res = RCGuard.call(\swing, 0) { func.value(t, this) };
		} {
			res = if(mod.isNil or: { mod <= 0 }) { shift } { (amount * sin(pi * (t % mod))) + shift };
		};
		if(res.isNumber.not or: { res.isNaN } or: { res.abs == inf }) {
			RCLog.warn(\swing, "non-finite swing value % at t=%, using 0".format(res, t));
			^0
		};
		^res
	}

	copy { ^this.class.new(amount, mod, shift, func) }

	printOn { |stream|
		stream << "RCSwing(" << amount << ", " << mod << ", " << shift << ")"
	}
}
