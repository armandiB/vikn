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

	*chart { |mode| ^if(mode == \minor) { minorChart } { majorChart } }

	// The class (0 the same key, 1 to 5) and the name (nil when the chart has none) of a key of some mode whose
	// tonic is so many fifths from the tonic of a key of fromMode.
	*classBetween { |fromMode, fifths, toMode|
		^this.chart(fromMode).detect { |row| (row[1] == fifths) and: { row[2] == toMode } } !? { |row| row[3] } ? 5
	}
	*nameBetween { |fromMode, fifths, toMode|
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
		^if(r[1] == \minor) { RLScale.minor(system, t) } { RLScale.major(system, t) }
	}

	// Every region whose tonic is at most span fifths from the central one, flat side first.
	regions { |span = 7|
		^Array.series((2 * span) + 1, span.neg).collect { |f| [[f, \major], [f, \minor]] }.flatten(1)
	}

	// The names of the chart round this tonic, class by class.
	names { ^this.class.chart(mode).collect(_[0]) }

	printOn { |stream| stream << "RLRegions(" << system.name(tonic) << " " << mode << " in " << system << ")" }
}
