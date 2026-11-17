// reLation — a chord space: a pitch system and the chord types we allow in it.
//
// A type is a name and a pattern, the pitch-class intervals of its tones above the root, root first.
// RLChordSpace.triads(system) has the triad nearest 4:5:6 and its inversion:
//
//   major   [0, a, b]        a the system's 5/4, b its 3/2
//   minor   [0, b - a, b]    the same intervals upside down
//
//   ~space = RLChordSpace.triads(RLEDO(12));
//   ~space.pattern(\minor);          // [0, 3, 7]
//   ~space.major(0).pcs;             // [0, 4, 7]
//   ~space.find([9, 0, 4]);          // A minor
//   ~space.all.size;                 // 24
//
// A space is a value. Its chords are RLChords.

RLChordSpace {
	var <system, <types;

	*new { |system, types|
		^super.newCopyArgs(system, types.asArray.collect { |t| [t[0].asSymbol, t[1].asArray] })
	}

	// The triad nearest third : fifth above a root, and its inversion.
	*triads { |system, third = #[5, 4], fifth = #[3, 2]|
		var a = system.interval(third), b = system.interval(fifth), z = system.pcZero;
		^this.new(system, [[\major, [z, a, b]], [\minor, [z, system.pcSub(b, a), b]]])
	}

	typeNames { ^types.collect(_[0]) }
	typeIndex { |name| ^types.detectIndex { |t| t[0] == name } }
	typeAt { |index| ^types[index][0] }

	pattern { |name|
		var i = this.typeIndex(name.asSymbol);
		if(i.isNil) { Error("RLChordSpace: no chord type % (%)".format(name, this.typeNames)).throw };
		^types[i][1]
	}

	chord { |root, type| ^RLChord(this, root, type ? types[0][0]) }
	major { |root| ^this.chord(root ?? { system.pcZero }, \major) }
	minor { |root| ^this.chord(root ?? { system.pcZero }, \minor) }

	// Every chord of a finite system, type by type.
	all {
		if(system.isFinite.not) { Error("RLChordSpace: % has no list of all its chords".format(system)).throw };
		^types.collect { |t| system.pcs.collect { |r| this.chord(r, t[0]) } }.flatten(1)
	}

	// The chord with these pitch classes, in any order, or nil.
	find { |pcs|
		var found;
		types.do { |t|
			pcs.do { |root|
				var c;
				if(found.isNil) {
					c = this.chord(root, t[0]);
					if(c.hasPcs(pcs)) { found = c };
				};
			};
		};
		^found
	}

	== { |other| ^other.isKindOf(RLChordSpace) and: { system == other.system } and: { types == other.types } }
	!= { |other| ^(this == other).not }
	hash { ^[\RLChordSpace, system.hash, types].hash }

	printOn { |stream| stream << "RLChordSpace(" << system << ", " << this.typeNames << ")" }
}
