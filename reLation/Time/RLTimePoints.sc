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
		var parts = (if(text.first == $M) { text.drop(1) } { text }).split($/);
		if(parts.isEmpty or: { parts.size > 2 } or: { parts.any { |p| p.isEmpty or: { p.every { |c| c.isDecDigit or: { c == $. } }.not } } }) {
			Error("RLTimePoints: no pulse named %".format(text)).throw
		};
		^parts[0].asFloat / (if(parts.size == 2) { parts[1].asFloat } { 1 })
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

	// A pulse layer is [period, phase], or a period alone: the points phase + k period. Its points in a window.
	layer { |layer, from = 0, to = 1|
		var l = this.prLayer(layer);
		^RLPulse(l[0]).points(from, to, l[1])
	}
	prLayer { |x| ^if(x.isNumber) { [x, 0] } { [x[0], x[1] ? 0] } }

	// How two pulse layers stand to one another:
	//   \consonance     the points of the slower are all points of the faster
	//   \displacement   their periods would allow that, and they are shifted apart (Krebs)
	//   \grouping       neither period is a whole multiple of the other
	dissonance { |a, b|
		var la = this.prLayer(a), lb = this.prLayer(b), slow, fast;
		#slow, fast = if(la[0] >= lb[0]) { [la, lb] } { [lb, la] };
		^if(RLPulse(slow[0]) <= RLPulse(fast[0])) {
			if(RLPulse(fast[0]).relates(fast[1], slow[1])) { \consonance } { \displacement }
		} { \grouping }
	}

	// The points two layers both sound, as a layer [period, one of its points]: the period is their lcm. nil
	// when they share no point.
	common { |a, b|
		var la = this.prLayer(a), lb = this.prLayer(b), period, count, point;
		if(RLPulse(RLPulse.gcd(la[0], lb[0])).relates(la[1], lb[1]).not) { ^nil };
		period = RLPulse.lcm(la[0], lb[0]);
		count = (period / la[0]).round.asInteger;
		point = Array.fill(count) { |k| la[1] + (k * la[0]) }.detect { |x| RLPulse(lb[0]).relates(lb[1], x) };
		^[period.round(1e-10), point.round(1e-10)]
	}

	== { |that| ^that.isKindOf(RLTimePoints) }
	!= { |that| ^(this == that).not }
	hash { ^\RLTimePoints.hash }

	printOn { |stream| stream << "RLTimePoints" }
}
