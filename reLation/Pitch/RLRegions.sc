// reLation — regions: the keys round one tonic, after Schoenberg's chart of the regions (Structural Functions of
// Harmony, chapters III, IV and IX). One tonality, and every other key a region of it, nearer or farther.
//
// A region is [fifths, mode]: where its tonic stands on the line of fifths from the central tonic, and \major
// or \minor. On the line of fifths G sharp (8) and A flat (-4) are two regions, as on the chart, whatever the
// tuning makes of them. The chart names the regions and puts them in five classes of relationship:
//
//   round a major tonic   1 direct and close      D, SD, sm, m
//                         2 indirect but close    t, sd, v, SM, M, bM, bSM
//                         3 indirect              bm, bsm, MM, Mm, bsmSM, bsmsm
//                         4 indirect and remote   Np, dor, S/T, bMD, bmv
//                         5 distant               every other region
//   round a minor tonic   1 close: M, T, v, sd    2 indirect but close: D, SM    3 indirect: m, sm, SD
//                         4 indirect and remote: #sm, #SM, #m, #M, subT, subt, Np    5 distant
//
//   ~r = RLRegions(RLEDO(12), 0, \major);       // round C major
//   ~r.region(\sm);                             // [3, \minor]: A minor
//   ~r.region(\SMsm);                           // [6, \minor]: the submediant major's submediant minor, F sharp minor
//   ~r.classOf([-4, \major]);                   // 2: A flat major, bSM
//   ~r.classOf([8, \major]);                    // 3: G sharp major, MM, the same tones in 12 steps
//   ~r.relation([1, \major], [3, \minor]);      // 4: A minor seen from G major is its dor
//   ~r.scale(\sm);                              // an RLScale: A minor
//
// The classes are Schoenberg's judgement of the practice up to the end of the 19th century, not a measure.
//
// A region may be of any diatonic mode (RLScale.modes): [1, \mixolydian] is the Mixolydian on G. Major and minor
// regions are two of the seven; the chart knows only them. The modes make one context, in which a region is a
// centre and a signature (which seven fifths its tones are), and two regions are apart by an interval:
//
//   RLRegions.interval(\major, 1, \mixolydian);     // [1, 0]: G Mixolydian has C major's tones, a fifth up
//   RLRegions.interval(\major, 0, \mixolydian);     // [0, -1]: C Mixolydian is C major with one flat more
//   RLRegions.steps(\major, 2, \dorian);            // 1: D Dorian from C major, the same tones
//   RLRegions.classBetween(\major, 1, \mixolydian); // 1: where the chart has no word, the steps
//   ~r.scale([2, \dorian]).degrees;                 // [2, 4, 5, 7, 9, 11, 0]
//   ~r.regions(1, [\major, \mixolydian]);           // the regions a reading may choose from
//   ~r.context(2).graph.distance([0, \major], [0, \dorian]);   // 2: two tones changed

RLRegions {
	classvar <majorChart, <minorChart;
	var <system, <tonic, <mode;

	*initClass {
		// [name, fifths, mode, class]
		majorChart = [
			[\T, 0, \major, 0],
			[\D, 1, \major, 1], [\SD, -1, \major, 1], [\sm, 3, \minor, 1], [\m, 4, \minor, 1],
			[\t, 0, \minor, 2], [\sd, -1, \minor, 2], [\v, 1, \minor, 2], [\SM, 3, \major, 2], [\M, 4, \major, 2],
			[\bM, -3, \major, 2], [\bSM, -4, \major, 2],
			[\bm, -3, \minor, 3], [\bsm, -4, \minor, 3], [\MM, 8, \major, 3], [\Mm, 8, \minor, 3],
			[\bsmSM, -8, \major, 3], [\bsmsm, -8, \minor, 3],
			[\Np, -5, \major, 4], [\dor, 2, \minor, 4], ['S/T', 2, \major, 4], [\bMD, -2, \major, 4], [\bmv, -2, \minor, 4]
		];
		minorChart = [
			[\t, 0, \minor, 0],
			[\M, -3, \major, 1], [\T, 0, \major, 1], [\v, 1, \minor, 1], [\sd, -1, \minor, 1],
			[\D, 1, \major, 2], [\SM, -4, \major, 2],
			[\m, -3, \minor, 3], [\sm, -4, \minor, 3], [\SD, -1, \major, 3],
			['#sm', 3, \minor, 4], ['#SM', 3, \major, 4], ['#m', 4, \minor, 4], ['#M', 4, \major, 4],
			[\subT, -2, \major, 4], [\subt, -2, \minor, 4], [\Np, -5, \major, 4]
		];
	}

	// A mode's place among the seven (RLScale.below), which of the two charts it is seen with (the mode of its
	// tonic triad: a Mixolydian region is a major one, a Dorian a minor), and whether the chart knows it.
	*below { |mode| ^RLScale.below(mode) }
	*third { |mode| ^if(this.below(mode) <= 2) { \major } { \minor } }
	*charted { |mode| ^(mode == \major) or: { mode == \minor } }
	*chart { |mode| ^if(this.third(mode) == \minor) { minorChart } { majorChart } }

	// The interval between two regions in the context of modes: [how many fifths the centre moves, how many the
	// signature does]. [n, 0]: the same tones about another centre (C major, D Dorian, G Mixolydian);
	// [0, n]: the same centre, n tones changed (C major, C Mixolydian); [n, n]: the same mode n fifths away.
	*interval { |fromMode, fifths, toMode| ^[fifths, fifths - (this.below(toMode) - this.below(fromMode))] }

	// How many steps apart two regions are in the context of modes, a step being one of: the same tones about
	// another centre (R), one tone changed about the same centre (S), the same mode a fifth away (T).
	*steps { |fromMode, fifths, toMode|
		var i = this.interval(fromMode, fifths, toMode), dc = i[0], dk = i[1];
		^if(dk == 0) { if(dc == 0) { 0 } { 1 } } {
			dk.abs + if(((dc * dk.sign) >= 0) and: { dc.abs <= dk.abs }) { 0 } { 1 }
		}
	}

	// The class (0 the same key, 1 to 5) and the name (nil when the chart has none) of a key of some mode whose
	// tonic is so many fifths from the tonic of a key of fromMode. Major and minor keys: Schoenberg's chart.
	// A region of another mode, or every region when byModes is true: its steps in the context of modes
	// (1 to 5, 5 for more).
	*classBetween { |fromMode, fifths, toMode, byModes = false|
		if(byModes.not and: { this.charted(fromMode) } and: { this.charted(toMode) }) {
			^this.chart(fromMode).detect { |row| (row[1] == fifths) and: { row[2] == toMode } } !? { |row| row[3] } ? 5
		};
		^if((fifths == 0) and: { fromMode == toMode }) { 0 } { this.steps(fromMode, fifths, toMode).clip(1, 5) }
	}
	*nameBetween { |fromMode, fifths, toMode|
		if(this.charted(fromMode).not or: { this.charted(toMode).not }) { ^nil };
		^this.chart(fromMode).detect { |row| (row[1] == fifths) and: { row[2] == toMode } } !? { |row| row[0] }
	}

	*new { |system, tonic, mode = \major|
		var sys = system ?? { RLEDO(12) };
		^super.newCopyArgs(sys, tonic ?? { sys.pcZero }, mode)
	}

	central { ^[0, mode] }

	// A region from its name: one symbol of the chart, or several in a row, each read from the region before
	// (\SMsm: the submediant major's submediant minor). A region itself is returned as it is.
	region { |name|
		var text, at = [0, mode];
		if(name.isKindOf(SequenceableCollection) and: { name.isString.not }) { ^name };
		text = name.asString;
		while { text.notEmpty } {
			var row = this.class.chart(at[1]).select { |r| text.beginsWith(r[0].asString) }
				.sort { |a, b| a[0].asString.size >= b[0].asString.size }.first;
			if(row.isNil) { Error("RLRegions: no region % round a % tonic (at %)".format(name, mode, text)).throw };
			at = [at[0] + row[1], row[2]];
			text = text.drop(row[0].asString.size);
		};
		^at
	}

	// The class of a region seen from another (both from the central tonic), from the central one, and its name there.
	relation { |from, to|
		var a = this.region(from), b = this.region(to);
		^this.class.classBetween(a[1], b[0] - a[0], b[1])
	}
	classOf { |region| ^this.relation(this.central, region) }
	nameOf { |region, from|
		var a = this.region(from ? this.central), b = this.region(region);
		^this.class.nameBetween(a[1], b[0] - a[0], b[1])
	}

	// A region's tonic as a pitch class of the system, and its scale.
	tonicOf { |region|
		^system.pcAdd(tonic, system.pcScale(system.interval([3, 2]), this.region(region)[0]))
	}
	scale { |region|
		var r = this.region(region), t = this.tonicOf(r);
		^switch(r[1], \minor, { RLScale.minor(system, t) }, \major, { RLScale.major(system, t) }, { RLScale.modal(system, t, r[1]) })
	}

	// Every region whose tonic is at most span fifths from the central one, flat side first: major and minor, or
	// the modes named (\major, \minor, \mixolydian, \dorian ...: RLScale.modes).
	regions { |span = 7, modes|
		var list = modes ? #[\major, \minor];
		^Array.series((2 * span) + 1, span.neg).collect { |f| list.collect { |m| [f, m] } }.flatten(1)
	}

	// The context of modes: these regions (every mode when none is named) under the three relations of
	// *steps. R: the same tones about another centre; S: the same centre, one tone changed; T: the same mode a
	// fifth away. A relation context like another: its graph gives the distances (*steps, inside the span), a
	// piece's regions are a walk on it.
	context { |span = 2, modes|
		var objs = this.regions(span, modes ?? { RLScale.modes });
		var iv = { |x, y| this.class.interval(x[1], y[0] - x[0], y[1]) };
		^RLRelationContext(objs, [
			[\R, { |x, y| var i = iv.(x, y); (i[1] == 0) and: { i[0] != 0 } }],
			[\S, { |x, y| var i = iv.(x, y); (i[0] == 0) and: { i[1].abs == 1 } }],
			[\T, { |x, y| (x[1] == y[1]) and: { (y[0] - x[0]).abs == 1 } }]
		])
	}

	// The names of the chart round this tonic, class by class.
	names { ^this.class.chart(mode).collect(_[0]) }

	printOn { |stream| stream << "RLRegions(" << system.name(tonic) << " " << mode << " in " << system << ")" }
}
