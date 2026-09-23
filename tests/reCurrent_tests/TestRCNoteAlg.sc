TestRCNoteAlg : UnitTest {
	var savedRateLimit;

	setUp {
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
		thisThread.randSeed = 42;
	}

	tearDown { RCLog.rateLimit = savedRateLimit }

	stattrs1d {
		var major = [0, 2, 4, 5, 7, 9, 11];
		^(scale: 12.collect { |i| i }, aroh: major, avaroh: major, octaves: nil,
			notealg_maxrandomjump: 5, notealg_maxothersjump: 7, notealg_startnotefunc: { |ppo| 60 })
	}

	test_fireflyNote1d {
		var alg = RCNoteAlg.fireflyNote1d;
		var st = this.stattrs1d;
		var major = [0, 2, 4, 5, 7, 9, 11];
		this.assertEquals(alg.(nil, [], \random, st), 60, "start note from the start function");
		this.assertEquals(alg.(60, [], \same, st), 60, "origin same");
		20.do {
			var n = alg.(60, [], \random, st);
			this.assert((n - 60).abs <= 5 and: { major.includes(n % 12) }, "random step within the jump and in the scale (%)".format(n));
		};
		this.assertEquals(alg.(60, [62, 63, 100, 60], \others, st), 62, "others: in scale, within jump, not the same note");
		this.assertEquals(alg.(60, [63, 100], \others, st), 60, "others: nothing acceptable → prev");
		st.octaves = [5];
		this.assert(alg.(60, [], \random, st).div(12) == 5, "allowed octaves respected");
		st.aroh = [];
		st.avaroh = [];
		this.assertEquals(alg.(60, [], \random, st), 60, "empty scale → prev, no crash");
		this.assertEquals(alg.(60, [], \weird, st), 60, "unknown origin → prev");
	}

	stattrsTonnetz {
		^(tonnetz_mult_factors: [2, 3/2, 5/4], tonnetz_mult_factors_mask: [0, 1, 1],
			visibility_freq_ratio: 4, max_velocity: 1, repuls_radius: 1,
			repuls_amount_func: { |radius, other, new| if((other - new).abs.sum <= radius) { 1 } { 0 } },
			repuls_proba_func: { |minOthers, amount, proba| if(amount > minOthers) { proba * 0.1 } { proba } },
			velocity_proba_func: { |dist, maxVel| 1 }, temperature: 0, temperature_weight: 1,
			notealg_start_tpos_func: { [0, 0, 0] })
	}

	test_fireflyTonnetz {
		var alg = RCNoteAlg.fireflyTonnetz(false);
		var st = this.stattrsTonnetz;
		var field = { |prev, move, proba, mask| proba };
		var start = alg.(nil, [], field, st);
		var res;
		this.assertEquals(start, [[0, 0, 0], 1], "start position and its frequency multiplier");
		// one other firefly above and to the right: the walk moves towards it, at most one step
		res = alg.([[0, 0, 0], 1], [[[0, 2, 2], 1.5, 1]], field, st);
		this.assert(res[0].abs.sum <= 1 and: { res[0].every { |x| x >= 0 } }, "one step towards the centre of mass (%)".format(res[0]));
		this.assertFloatEquals(res[1], ([2, 3/2, 5/4] ** res[0]).product, "frequency multiplier from the position");
		res = alg.([[0, 0, 0], 1], [[[0, 5, 5], 100, 1]], field, st);
		this.assertEquals(res[0], [0, 0, 0], "an invisible firefly (frequency ratio too large) attracts nothing");
		res = alg.([[0, 0, 0], 1], [], { nil.explode }, st);
		this.assertEquals(res[0], [0, 0, 0], "a failing force field → stay, no crash");
		res = alg.([[0, 0, 0], 1], [], { 0 }, st);
		this.assertEquals(res[0], [0, 0, 0], "zero probabilities → stay");
		st.temperature = 1;
		res = alg.([[0, 0, 0], 1], [], field, st);
		this.assert(res[0].abs.sum <= 1 and: { res[0][0] == 0 }, "temperature moves only in the masked dimensions");
	}

	test_tonnetzPositions {
		var tmf = [2, 3/2, 5/4, 7/4, 11/8, 13/8];
		var pos = RCNoteAlg.tonnetzPositions([1, 3/2, 5/4, 9/8, 16/9, 7/4], tmf);
		this.assertEquals(pos[0], [0, 0, 0, 0, 0, 0], "unison");
		this.assertEquals(pos[1], [0, 1, 0, 0, 0, 0], "fifth");
		this.assertEquals(pos[2], [0, 0, 1, 0, 0, 0], "major third");
		this.assertEquals(pos[3], [-1, 2, 0, 0, 0, 0], "9/8 = two fifths down an octave");
		this.assertEquals(pos[4], [2, -2, 0, 0, 0, 0], "16/9 = two fifths down, two octaves up");
		this.assertEquals(pos[5], [0, 0, 0, 1, 0, 0], "harmonic seventh");
	}

	test_scaleForceField {
		var tmf = [2, 3/2, 5/4, 7/4, 11/8, 13/8];
		var ratios = [1, 3/2, 5/4];
		var field = RCNoteAlg.scaleForceField([3, 0, 0, 0, 0, 0], 2, [0, 1, 2], ratios, tmf);
		var onScale = field.([0, 0, 0, 0, 0, 0], [0, 1, 0, 0, 0, 0], 1, [0, 1, 1, 1, 1, 1]);
		var offScale = field.([0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 1, 0], 1, [0, 1, 1, 1, 1, 1]);
		var stay = field.([0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0], 1, [0, 1, 1, 1, 1, 1]);
		this.assert(onScale > offScale, "moving onto a scale degree is favoured over leaving the scale");
		this.assert(stay.isNumber and: { stay.isNaN.not }, "a zero move does not divide by zero");
	}
}
