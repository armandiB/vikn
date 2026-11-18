// reLation, the pitch systems and the voice leading: RLEDO, RLJust, RLVoiceLeading. No server.
// The numbers are those of HomewareSC's Math/Category_Theory/THEORY.md (§ 3) and of its checks.
TestRLEDO : UnitTest {

	test_vals_of_seven_divisions {
		var expected = [[12, 7, 4], [19, 11, 6], [22, 13, 7], [31, 18, 10], [34, 20, 11], [41, 24, 13], [53, 31, 17]];
		expected.do { |row|
			var edo = RLEDO(row[0]);
			this.assertEquals([edo.interval([3, 2]), edo.interval([5, 4])], row.drop(1), "% steps: the fifth and the major third".format(row[0]));
		};
	}

	test_commas_tempered_or_not {
		this.assertEquals(RLEDO(12).interval([81, 80]), 0, "12 tempers the syntonic comma out");
		this.assertEquals(RLEDO(12).interval([128, 125]), 0, "and the diesis");
		this.assertEquals(RLEDO(19).interval([81, 80]), 0, "19 tempers the syntonic comma out");
		this.assertEquals(RLEDO(19).interval([128, 125]), 1, "but not the diesis");
		this.assertEquals(RLEDO(22).interval([81, 80]), 1, "22 keeps the syntonic comma");
		this.assertEquals(RLEDO(53).interval([81, 80]), 1, "53 too");
	}

	test_pitches_and_heights {
		var edo = RLEDO(12);
		this.assertEquals(edo.pc(-5), 7, "a pitch's class");
		this.assertEquals(edo.nearest(7, 0.0), -5, "the G nearest the origin is below it");
		this.assertEquals(edo.above(7, 0.0), 7, "the G above the origin");
		this.assertEquals(edo.nearest(0, 1.0), 12, "the C an octave up");
		this.assertFloatEquals(edo.height(18), 1.5, "a height in octaves");
		this.assertFloatEquals(edo.freq(9, 261.6255653006), 440.0, "A above a C at 261.63 Hz", 0.001);
		this.assertFloatEquals(edo.cents(7), -500.0, "the shorter way round: a fifth is a fourth down");
		this.assertFloatEquals(RLEDO(19).cents(1), 1200 / 19, "one step of 19");
		this.assertEquals(edo.pcSub(2, 7), 7, "pitch classes subtract round the circle");
	}

	test_mapping_and_names {
		this.assertEquals(RLEDO(17).interval([5, 4]), 5, "17's nearest major third");
		this.assertEquals(RLEDO(17, (5: 40)).interval([5, 4]), 6, "its other one, by a mapping");
		this.assertEquals(RLEDO(12).name(10), "Bb", "note names in 12");
		this.assertEquals(RLEDO(19).name(10), "10", "numbers elsewhere");
	}

	test_a_system_is_a_value {
		var set = Set.new;
		this.assert(RLEDO(12) == RLEDO(12), "equal by their numbers");
		this.assert(RLEDO(12) != RLEDO(19), "different steps");
		this.assert(RLEDO(17) != RLEDO(17, (5: 40)), "different mappings");
		set.add(RLEDO(12)); set.add(RLEDO(12)); set.add(RLEDO(19));
		this.assertEquals(set.size, 2, "usable as keys");
	}
}

TestRLJust : UnitTest {

	test_intervals_as_exponents {
		var just = RLJust([3, 5]);
		this.assertEquals(just.interval([3, 2]), [1, 0], "a fifth");
		this.assertEquals(just.interval([5, 4]), [0, 1], "a major third");
		this.assertEquals(just.interval([6, 5]), [1, -1], "a minor third");
		this.assertEquals(just.interval([5, 3]), [-1, 1], "a major sixth");
		this.assertEquals(just.interval([81, 80]), [4, -1], "the syntonic comma");
		this.assertEquals(just.pitchInterval([3, 2]), [-1, 1, 0], "with the exponent of 2 in front");
		this.assertEquals(RLJust([3, 5, 7]).interval([7, 4]), [0, 0, 1], "the 7-limit lattice");
		this.assertException({ just.interval([7, 4]) }, Error, "a ratio outside the lattice is refused");
	}

	test_cents_names_and_commas {
		var just = RLJust([3, 5]);
		this.assertFloatEquals(just.cents([4, -1]), 21.5063, "syntonic comma", 0.001);
		this.assertFloatEquals(just.cents([0, -3]), 41.0589, "diesis", 0.001);
		this.assertFloatEquals(just.cents([4, -4]), 62.5651, "major diesis", 0.001);
		this.assertFloatEquals(just.cents([-12, 0]), -23.46, "twelve fifths down: a Pythagorean comma", 0.001);
		this.assertEquals(just.name([-1, 1]), "5/3", "a pitch class by its ratio");
		this.assertEquals(just.name([0, 0]), "1/1", "the origin");
		this.assertEquals(just.commaName([4, -1]), "syntonic comma 81/80", "a comma by its name");
		this.assertEquals(just.commaName([-4, 1]), "syntonic comma 81/80", "in either direction");
		this.assertEquals(just.commaName([1, 1]), "diatonic semitone 16/15", "the small steps are named too: 15/8 is 16/15 down");
		this.assertEquals(just.commaName([2, 1]), nil, "45/32 has no name here");
	}

	test_pitches_and_heights {
		var just = RLJust([3, 5]);
		this.assertEquals(just.nearest([1, 0], 0.0), [-2, 1, 0], "the fifth nearest the origin is 3/4");
		this.assertEquals(just.above([1, 0], 0.0), [-1, 1, 0], "the fifth above it is 3/2");
		this.assertFloatEquals(just.ratio([-1, 1, 0]), 1.5, "its ratio");
		this.assertFloatEquals(just.freq([-2, 0, 1], 400), 500, "5/4 above 400 Hz");
		this.assertEquals(just.pc([3, -1, 2]), [-1, 2], "a pitch's class drops the exponent of 2");
	}

	test_tempering {
		var just = RLJust([3, 5]), k12;
		this.assertEquals(just.temperPc(RLEDO(12), [4, -1]), 0, "12: the syntonic comma goes to 0");
		this.assertEquals(just.temperPc(RLEDO(12), [0, 3]), 0, "12: the diesis goes to 0");
		this.assertEquals(just.temperPc(RLEDO(19), [0, 3]), 18, "19: the diesis is a step");
		this.assertEquals(just.temperPc(RLEDO(12), [-1, 1]), 9, "5/3 is A");
		this.assertEquals(just.temperPitch(RLEDO(12), [-1, 1, 0]), 7, "3/2 is 7 steps up");
		k12 = just.kernel(RLEDO(12), 4);
		this.assert(k12.includesEqual([4, -1]) and: { k12.includesEqual([0, 3]) } and: { k12.includesEqual([4, -4]) }, "the kernel of 12 holds its commas");
		this.assert(k12.every { |x| ((x[0] % 4) == 0) and: { ((x[1] + x[0].div(4)) % 3) == 0 } },
			"and only sums of the syntonic comma (4, -1) and the diesis (0, 3)");
		this.assertEquals(just.kernel(RLEDO(12), 4).first, [4, 2], "the smallest first: the diaschisma");
		this.assert(just.kernel(RLEDO(19), 4).includesEqual([4, -1]) and: { just.kernel(RLEDO(19), 4).includesEqual([0, 3]).not },
			"19 keeps the syntonic comma only");
	}

	test_a_system_is_a_value {
		this.assert(RLJust([3, 5]) == RLJust([3, 5]), "equal by their primes");
		this.assert(RLJust([3, 5]) != RLJust([3, 5, 7]), "another lattice");
		this.assert(RLJust([3, 5]) != RLEDO(12), "another kind");
	}
}

TestRLVoiceLeading : UnitTest {

	test_least_motion_between_triads {
		var edo = RLEDO(12);
		this.assertEquals(RLVoiceLeading.minimal(edo, [60, 64, 67], [0, 3, 7]), [60, 63, 67], "C major to C minor: the third down");
		this.assertEquals(RLVoiceLeading.minimal(edo, [60, 64, 67], [5, 9, 0]), [60, 65, 69], "C major to F major");
		this.assertEquals(RLVoiceLeading.minimal(edo, [67, 60, 64], [9, 0, 4]), [69, 60, 64], "the voices keep their order in the Array");
		this.assertFloatEquals(RLVoiceLeading.cents(edo, [60, 64, 67], [60, 65, 69]), 300.0, "three semitones in all");
		this.assertFloatEquals(RLVoiceLeading.cents(edo, [60, 64, 67], [60, 65, 69], \linf), 200.0, "the largest move");
		this.assertFloatEquals(RLVoiceLeading.cents(edo, [60, 64, 67], [60, 65, 69], \l2), 5.sqrt * 100, "the Euclidean length");
		this.assertEquals(RLVoiceLeading.displacements(edo, [60, 64, 67], [60, 65, 69]).round.asInteger, [0, 100, 200], "voice by voice");
	}

	// Tymoczko's theorem: the least motion is one of the rotations. Checked against every assignment.
	test_rotations_find_the_least_motion {
		var systems = [RLEDO(12), RLEDO(19), RLEDO(31)], bad = 0, count = 0;
		thisThread.randSeed = 1;
		systems.do { |edo|
			[3, 4, 5].do { |n|
				40.do {
					var voices = { (edo.steps * 4) + (edo.steps * 2).rand } ! n;
					var pcs = { edo.steps.rand } ! n;
					var a = RLVoiceLeading.minimal(edo, voices, pcs), b = RLVoiceLeading.exhaustive(edo, voices, pcs);
					count = count + 1;
					if((RLVoiceLeading.cents(edo, voices, a) - RLVoiceLeading.cents(edo, voices, b)).abs > 1e-6) { bad = bad + 1 };
				};
			};
		};
		this.assertEquals(bad, 0, "the rotations and the exhaustive search agree on % random pairs".format(count));
	}

	test_on_the_lattice {
		var just = RLJust([3, 5]);
		var c = [[0, 0, 0], [-2, 0, 1], [-1, 1, 0]];                       // 1/1, 5/4, 3/2
		var am = RLVoiceLeading.minimal(just, c, [[-1, 1], [0, 0], [0, 1]]);   // 5/3, 1/1, 5/4
		this.assertEquals(am, [[0, 0, 0], [-2, 0, 1], [0, -1, 1]], "C major to A minor: the fifth up to 5/3");
		this.assertFloatEquals(RLVoiceLeading.cents(just, c, am), 182.4037, "by a minor tone, 10/9", 0.001);
	}

	test_close_position {
		var space = RLChordSpace.triads(RLEDO(12));
		this.assertEquals(RLVoiceLeading.closePosition(space.major(0)), [0, 4, 7], "from the origin");
		this.assertEquals(RLVoiceLeading.closePosition(space.major(0), 5.0), [60, 64, 67], "five octaves up");
		this.assertEquals(RLVoiceLeading.closePosition(space.minor(9), 5.0), [57, 60, 64], "the root nearest the height");
	}

	test_between_two_chords {
		var space = RLChordSpace.triads(RLEDO(12)), c = space.major(0);
		this.assertFloatEquals(RLVoiceLeading.between(c, space.major(5)), 300.0, "C major to F major: three semitones");
		this.assertEquals(RLVoiceLeading.stepsBetween(c, space.minor(5)), 2, "C major to F minor: two");
		this.assertEquals(RLVoiceLeading.stepsBetween(c, space.major(6)), 6, "C major to F# major: six");
		this.assert(RLVoiceLeading.moveSets(c, space.minor(4)).includesEqual([1]), "C major to E minor: one voice by one step");
		this.assert(RLVoiceLeading.moveSets(c, space.minor(9)).includesEqual([2]), "to A minor: one voice by two");
		this.assert(RLVoiceLeading.moveSets(c, space.major(1)).includesEqual([1, 1, 1]), "to C# major: every voice by one");
		this.assertEquals(RLVoiceLeading.stepsBetween(RLChordSpace.triads(RLEDO(19)).major(0), RLChordSpace.triads(RLEDO(19)).minor(0)), 1, "in 19 steps P is one step");
	}

	// Tymoczko 2006: (0, 0, 4, 6) to (10, 0, 6, 6) beats every voice leading without doublings
	test_doublings {
		var edo = RLEDO(12), four;
		this.assertEquals(RLVoiceLeading.minimalDoubled(edo, [0, 0, 4, 6], [6, 10, 0]), [-2, 0, 6, 6], "four voices on three tones");
		this.assertFloatEquals(RLVoiceLeading.cents(edo, [0, 0, 4, 6], [-2, 0, 6, 6]), 400.0, "four semitones");
		this.assertFloatEquals(RLVoiceLeading.cents(edo, [0, 4, 6], RLVoiceLeading.minimal(edo, [0, 4, 6], [6, 10, 0])), 600.0, "six without the doubling");
		this.assertEquals(RLVoiceLeading.minimalDoubled(edo, [60, 64, 67], [5, 9, 0]), RLVoiceLeading.minimal(edo, [60, 64, 67], [5, 9, 0]), "as many voices as tones: the same");
		this.assertException({ RLVoiceLeading.minimalDoubled(edo, [60, 64], [5, 9, 0]) }, Error, "fewer voices than tones are refused");
		four = RLVoiceLeading.through(RLWalk(RLChordContext(RLChordSpace.triads(edo)), RLChordSpace.triads(edo).major(0), [\R, \L, \R, \P]).chords, [48, 60, 64, 67]);
		this.assert(four.every { |v| v.size == 4 }, "a walk in four voices");
		this.assertEquals(four.last.collect { |p| p % 12 }.asSet, Set[2, 6, 9], "each chord complete: D major at the end");
	}

	test_doublings_on_both_sides {
		var edo = RLEDO(12), bad = 0, count = 0, brute;
		// every set of pairs that leaves no tone out, by trying them all
		brute = { |a, b|
			var best = inf;
			(1..((2 ** (a.size * b.size)).asInteger - 1)).do { |mask|
				var usedA = Array.fill(a.size, false), usedB = Array.fill(b.size, false), cost = 0;
				a.size.do { |i|
					b.size.do { |j|
						if(mask.bitTest((i * b.size) + j)) {
							var d = (a[i] - b[j]) % 12;
							usedA[i] = true; usedB[j] = true;
							cost = cost + min(d, 12 - d);
						};
					};
				};
				if(usedA.every { |x| x } and: { usedB.every { |x| x } } and: { cost < best }) { best = cost };
			};
			best
		};
		this.assertFloatEquals(RLVoiceLeading.withDoublings(edo, [0, 4, 6], [6, 10, 0])[0], 400.0, "the paper's example");
		this.assertEquals(RLVoiceLeading.withDoublings(edo, [0, 4, 6], [6, 10, 0])[1].size, 4, "in four pairs");
		this.assertFloatEquals(RLVoiceLeading.withDoublings(edo, [4, 7, 11, 0], [4, 8, 11, 3])[0], 300.0, "C major seventh to E major seventh: three, by doubling E");
		thisThread.randSeed = 5;
		[[3, 3, 40], [2, 3, 30], [3, 2, 30], [3, 4, 8]].do { |row|
			row[2].do {
				var a = Array.series(12).scramble.keep(row[0]), b = Array.series(12).scramble.keep(row[1]);
				count = count + 1;
				if((RLVoiceLeading.withDoublings(edo, a, b)[0] - (brute.(a, b) * 100)).abs > 1e-6) { bad = bad + 1 };
			};
		};
		this.assertEquals(bad, 0, "the dynamic programme finds the least motion on % random pairs of sets".format(count));
	}
}
