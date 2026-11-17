// reLation — a transformation of an RLChordContext's chords: chord -> left * chord * right.
//
// left moves every chord the same way (a transposition or an inversion of the pitch classes); right is read
// from the chord itself (P, L, R and their products). The two sides commute. Transformations compose with
// <>, right to left as functions do: (l <> p).(chord) applies p first.
//
//   ~ctx = RLChordContext(RLChordSpace.triads(RLEDO(12)));
//   ~h = ~ctx.l <> ~ctx.p <> ~ctx.l;          // LPL, the hexatonic pole
//   ~h.(~ctx.space.major(0));                  // Ab minor
//   (~h <> ~h).isIdentity;                     // true
//   (~ctx.t(5) <> ~ctx.r) == (~ctx.r <> ~ctx.t(5));   // true: the two sides commute
//
// A transformation is a value: equal when its two elements are, whatever its label.

RLTransform {
	var <context, <left, <right, <>label;

	*new { |context, left, right, label|
		^super.newCopyArgs(context, left ?? { context.identity }, right ?? { context.identity }, label)
	}

	value { |chord| ^context.chordOf(context.mul(context.mul(left, context.element(chord)), right)) }

	// The same as a list, as relations answer; whether it takes x to y.
	images { |chord| ^[this.value(chord)] }
	relates { |x, y| ^this.value(x) == y }

	// What an RLCategory asks of its arrows: the identity before and after, and whether this can come after that.
	sourceIdentity { ^RLTransform(context) }
	targetIdentity { ^RLTransform(context) }
	follows { |that| ^that.isKindOf(RLTransform) and: { context == that.context } }

	// This after that.
	<> { |that|
		^RLTransform(context, context.mul(left, that.left), context.mul(that.right, right),
			if(label.notNil and: { that.label.notNil }) { label ++ that.label })
	}

	inverse {
		^RLTransform(context, context.inv(left), context.inv(right), label !? { "(" ++ label ++ ")⁻¹" })
	}

	// n times itself.
	repeated { |n|
		var out = RLTransform(context);
		n.do { out = this <> out };
		out.label = label !? { "(" ++ label ++ ")^" ++ n };
		^out
	}

	// Two pairs do the same thing when they differ by a central element passed from one side to the other
	// (in 12 steps T6 on the left is (RP)^2 on the right): equality is about what the transformation does.
	isIdentity { ^context.isCentral(left) and: { left == context.inv(right) } }
	isLeft { ^context.isCentral(right) }
	isRight { ^context.isCentral(left) }

	== { |other|
		var z;
		if(other.isKindOf(RLTransform).not or: { context != other.context }) { ^false };
		z = context.mul(context.inv(other.left), left);
		^context.isCentral(z) and: { z == context.mul(other.right, context.inv(right)) }
	}
	!= { |other| ^(this == other).not }

	hash {
		var best = [left, right];
		context.center.do { |z|
			var cand = [context.mul(left, z), context.mul(context.inv(z), right)];
			if(context.prBefore(cand[1], best[1])) { best = cand };
		};
		^[\RLTransform, best].hash
	}

	printOn { |stream|
		stream << (label ?? { "RLTransform(" ++ left ++ " · " ++ right ++ ")" })
	}
}
