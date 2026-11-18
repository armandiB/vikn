// reLation — time points and the pulses between them: the context of metre networks (Popoff and Yust).
//
// A time point is a number (a time in bars, in beats, in seconds). The arrows are pulses (RLPulse): M(d)
// relates two points a whole number of d apart. A metre network is an RLNetwork on this context: its nodes
// hold time points, its arrows say which pulse joins them, and two routes compose alike (to the gcd).
// Networks are carried into one another (RLNetwork:isMorphismTo) in the two ways metre has of being at odds
// with itself: a pulse included in a finer one (grouping), and a shift of time (displacement).
//
//   ~time = RLTimePoints.new;                 // Brahms, "Auf dem See", bars 7 and 8, the bar as the unit
//   ~top = RLNetwork(~time, [[\E, 5/2], [\G, 3]], [[\E, \G, 1/2]]);
//   ~voice = RLNetwork(~time, [[\E, 5/2], [\F, 17/6], [\G, 3]], [[\E, \F, 1/6], [\F, \G, 1/6]]);
//   ~top.isMorphismTo(~voice);                // true: M(1/2) is included in M(1/6), the half bar in three eighths
//   ~piano = RLNetwork(~time, [[\E, 31/12], [\G, 37/12]], [[\E, \G, 1/2]]);
//   ~top.isMorphismTo(~piano, nil, ~time.shift(1/12));    // true: the same a sixteenth later, a displacement
//   ~time.dissonance([1/6, 5/2], [1/4, 31/12]);           // grouping: eighths against dotted eighths
//   ~time.common([1/6, 5/2], [1/4, 31/12]);               // [1/2, 17/6]: the points both layers sound
//
// Shifting and stretching time are the automorphisms (RLTimeMap). A label is a pulse, its period, or a name
// such as 'M1/6'. On a cycle of n pulses, RLMetre has the same relations as finite ones.

RLTimePoints {

	*new { ^super.new }

	// A pulse from what names it: an RLPulse, a period, a name ('M1/6', "1/6"), or a word of them (the gcd).
	arrow { |label|
		var out;
		^case
		{ label.isKindOf(RLPulse) } { label }
		{ label.isNumber } { RLPulse(label) }
		{ label.isKindOf(SequenceableCollection) and: { label.isKindOf(String).not } } {
			out = RLPulse(0);
			label.do { |w| out = this.arrow(w) <> out };
			out
		}
		{ RLPulse(this.prParse(label.asString)) }
	}
	prParse { |text|
		var parts = (if(text.first == $M) { text.drop(1) } { text }).split($/), number, values;
		// digits, with one point at most
		number = { |p|
			p.notEmpty and: { p.every { |c| c.isDecDigit or: { c == $. } } } and: { p.count { |c| c == $. } <= 1 } and: { p.any { |c| c.isDecDigit } }
		};
		if(parts.isEmpty or: { parts.size > 2 } or: { parts.every { |p| number.value(p) }.not }) {
			Error("RLTimePoints: no pulse named %".format(text)).throw
		};
		values = parts.collect(_.asFloat);
		if((values.size == 2) and: { values[1] == 0 }) { Error("RLTimePoints: no pulse named % (a division by 0)".format(text)).throw };
		^values[0] / (values[1] ? 1)
	}

	// what RLNetwork asks of a context
	transform { |name| ^this.arrow(name) }
	identityTransform { ^RLPulse(0) }
	nameOf { |arrow| ^arrow.name }
	system { ^nil }

	pulse { |period| ^RLPulse(period) }

	// The maps of time t -> a t + b: a shift, a stretch, both.
	affine { |a = 1, b = 0| ^RLTimeMap(a, b) }
	shift { |b| ^RLTimeMap(1, b) }
	stretch { |a| ^RLTimeMap(a, 0) }

	// The finest pulse that holds a list of time points: every two of them a whole number of its periods apart.
	pulseOf { |points|
		var d = 0;
		points.doAdjacentPairs { |x, y| d = RLPulse.gcd(d, (y - x).abs) };
		^RLPulse(d)
	}

	// A pulse layer is [period, phase], or a period alone: the points phase + k period. A layer of period 0
	// is its one point. Its points in a window.
	layer { |layer, from = 0, to = 1|
		var l = this.prLayer(layer);
		^RLPulse(l[0]).points(from, to, l[1])
	}
	// the period as RLPulse keeps it (its sign dropped, the nearest fraction)
	prLayer { |x| ^if(x.isNumber) { [RLPulse(x).period, 0] } { [RLPulse(x[0]).period, x[1] ? 0] } }

	// How two pulse layers stand to one another:
	//   \consonance     the points of the slower are all points of the faster
	//   \displacement   their periods would allow that, and they are shifted apart (Krebs)
	//   \grouping       neither period is a whole multiple of the other
	// One point alone (period 0) is the slowest of layers: on the other layer or off it.
	dissonance { |a, b|
		var la = this.prLayer(a), lb = this.prLayer(b), slow, fast, length = { |l| if(l[0] == 0) { inf } { l[0] } };
		#slow, fast = if(length.value(la) >= length.value(lb)) { [la, lb] } { [lb, la] };
		^if(RLPulse(slow[0]) <= RLPulse(fast[0])) {
			if(RLPulse(fast[0]).relates(fast[1], slow[1])) { \consonance } { \displacement }
		} { \grouping }
	}

	// The points two layers both sound, as a layer [period, its first point from the first layer's phase on]:
	// the period is their lcm. nil when they share no point.
	common { |a, b|
		var la = this.prLayer(a), lb = this.prLayer(b), g, small, big, diff, k;
		if(la[0] == 0) { ^if(RLPulse(lb[0]).relates(lb[1], la[1])) { [0, la[1]] } { nil } };
		if(lb[0] == 0) { ^if(RLPulse(la[0]).relates(la[1], lb[1])) { [0, lb[1]] } { nil } };
		g = RLPulse.gcd(la[0], lb[0]);
		if(RLPulse(g).relates(la[1], lb[1]).not) { ^nil };
		// counted in g: the first layer steps by `small`, the second by `big`, their phases are `diff` apart;
		// the step k of the first layer is on the second when k small is diff, modulo big
		small = (la[0] / g).round.asInteger;
		big = (lb[0] / g).round.asInteger;
		diff = ((lb[1] - la[1]) / g).round;
		k = if(big == 1) { 0 } { ((diff % big) * this.prInverse(small % big, big)) % big };
		^[RLPulse.lcm(la[0], lb[0]).round(1e-10), (la[1] + (k * la[0])).round(1e-10)]
	}
	// the whole number that, times a, is 1 modulo m (a and m with no common divisor)
	prInverse { |a, m|
		var r0 = m, r1 = a, t0 = 0, t1 = 1, q, next;
		while { r1 != 0 } {
			q = r0.div(r1);
			next = r0 - (q * r1); r0 = r1; r1 = next;
			next = t0 - (q * t1); t0 = t1; t1 = next;
		};
		^t0 % m
	}

	== { |that| ^that.isKindOf(RLTimePoints) }
	!= { |that| ^(this == that).not }
	hash { ^\RLTimePoints.hash }

	printOn { |stream| stream << "RLTimePoints" }
}
