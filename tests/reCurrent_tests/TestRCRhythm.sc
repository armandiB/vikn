TestRCRhythm : UnitTest {

	test_euclid {
		this.assertEquals(RCRhythm.euclid(3, 8), [1, 0, 0, 1, 0, 0, 1, 0], "E(3,8)");
		// Bresenham form, a rotation of the canonical x.xx.xx. — identical to BlockBeats' euclidean_r
		this.assertEquals(RCRhythm.euclid(5, 8), [1, 0, 1, 0, 1, 1, 0, 1], "E(5,8)");
		this.assertEquals(RCRhythm.euclid(0, 4), [0, 0, 0, 0], "E(0,4) is silent");
		this.assertEquals(RCRhythm.euclid(4, 4), [1, 1, 1, 1], "E(4,4)");
		this.assertEquals(RCRhythm.euclid(3, 8, 1), [0, 1, 0, 0, 1, 0, 0, 1], "shift rotates");
		this.assertEquals(RCRhythm.euclid(3, 0), [], "no pulses → empty");
	}

	test_durListFromBinary {
		this.assertEquals(RCRhythm.durListFromBinary([1, 0, 0, 1, 0]), [3, 2], "hits become durations");
		this.assertEquals(RCRhythm.durListFromBinary([0, 0, 1, 1]).collect(_.value), [2, 1, 1], "leading zeros → leading rest");
		this.assert(RCRhythm.durListFromBinary([0, 1])[0].isRest, "leading element is a Rest");
		this.assertEquals(RCRhythm.durListFromBinaryWithAux([1, 0, 1], [10, 11, 12]), [[2, 1], [10, 12]], "aux values collected per hit");
		this.assertEquals(RCRhythm.euclidDurList(3, 8), [3, 3, 2], "euclidean dur list");
	}

	test_cumdur {
		var subseq = [1, 1, [1, 2, 1], nil, [true, false, true], \x, [], 1];
		var res = RCRhythm.cumdurFromSubseq(subseq);
		this.assertEquals(res[0], [1, 4], "onsets from shift 1");
		this.assertEquals(res[1], [0, 2], "indices of kept hits");
		this.assertEquals(res[2], 1, "duration of the last kept hit");
		res = RCRhythm.cumdurFromSubseq(subseq, loopTime: 4);
		this.assertEquals(res[0], [0, 1], "wrapped and sorted within the loop");
		this.assertEquals(res[1], [2, 0], "indices follow the sort");
		res = RCRhythm.cumdurFromSubseq(subseq, timeMult: 2);
		this.assertEquals(res[0], [2, 8], "timeMult scales shift and durations");
		this.assertEquals(RCRhythm.cumdurFromSubseq([1, 0, [1, 1], nil, [false, false]]), [[], [], nil], "all masked → empty");
		this.assertEquals(RCRhythm.cumdurFromSubseq([1, 0, Pseq([1]), nil, [true]]), [[], [], nil], "pattern durations refused, no crash");
	}

	test_durFromCumdur {
		this.assertEquals(RCRhythm.durFromCumdur([1, 4], 1), [[3, 1], 1], "durations and shift");
		this.assertEquals(RCRhythm.durFromCumdur([0, 2]), [[2], 0], "without last duration");
		this.assertEquals(RCRhythm.durFromCumdur([]), [[], 0], "empty");
		this.assert(RCRhythm.differentiateRestSafe([Rest(1), 3])[1].isRest, "rest-ness of the previous onset carries over");
		this.assertEquals(RCRhythm.differentiateRestSafe([1, 3]).collect(_.value), [1, 2], "plain differences");
	}
}
