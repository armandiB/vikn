// reCurrent — swing function of a layer.
//
// value(t) returns the time offset (in beats) added to the event that starts
// at running time t. Default shape, as in BlockBeats' global_swing_fun:
//   amount * sin(pi * (t % mod)) + shift
// A custom `func` receives (t, swing) and replaces the default shape.
// Non-finite results are replaced by 0 so a bad function can never break the
// dur pipeline.

RCSwing {
	var <>amount, <>mod, <>shift, <>func;

	*new { |amount = 0, mod = 1, shift = 0, func|
		^super.newCopyArgs(amount, mod, shift, func)
	}

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
