// reLation — a map of time: every time point t sent to a t + b. A shift (a = 1) and a stretch (b = 0) are the
// automorphisms of the time points with their pulses (Popoff and Yust): a shift changes no pulse, a stretch
// by a multiplies every period by a. Applied to a metre network (RLNetwork:transformedBy) a shift is a
// displacement, a stretch an augmentation.
//
//   ~later = RLTimeMap(1, 1/12);            // a sixteenth of a 6/8 bar later
//   ~later.value(5/2);                      // 31/12
//   ~later.onArrow(RLPulse(1/2));           // RLPulse(1/2)
//   RLTimeMap(2, 0).onArrow(RLPulse(1/2));  // RLPulse(1): twice as slow
//
// It is the left element [b, a] of RLTimeSpans, acting on points in place of spans. A value.

RLTimeMap {
	var <a, <b, <>label;

	*new { |a = 1, b = 0|
		if(a == 0) { Error("RLTimeMap: a stretch by 0 sends every time to one point").throw };
		^super.newCopyArgs(a, b)
	}

	value { |t| ^(a * t) + b }
	onArrow { |pulse| ^pulse.scaled(a) }

	// This after that; undone.
	<> { |that| ^RLTimeMap(a * that.a, (a * that.b) + b) }
	inverse { ^RLTimeMap(1 / a, b.neg / a) }
	isIdentity { ^((a - 1).abs < 1e-12) and: { b.abs < 1e-12 } }

	// equal when a and b are, read to the tenth decimal (what hash reads too)
	== { |that| ^that.isKindOf(RLTimeMap) and: { a.round(1e-10) == that.a.round(1e-10) } and: { b.round(1e-10) == that.b.round(1e-10) } }
	!= { |that| ^(this == that).not }
	hash { ^[\RLTimeMap, a.round(1e-10), b.round(1e-10)].hash }

	printOn { |stream| stream << "RLTimeMap(" << a << ", " << b << ")" }
}
