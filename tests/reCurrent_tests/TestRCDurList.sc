TestRCDurList : UnitTest {

	setUp { RCLog.reset }

	test_basics {
		var d = RCDurList([1, Rest(0.5), 0.5]);
		this.assertEquals(d.size, 3, "size");
		this.assertEquals(d.totalDur, 2, "total counts rests");
	}

	test_removeHit {
		var d = RCDurList([1, 1, 1]);
		d.removeHit(1);
		this.assert(d[1].isRest, "hit becomes a rest");
		this.assertEquals(d[1].value, 1, "same length");
		d.removeHit(4);
		this.assert(d[1].isRest, "position wraps (4 % 3 = 1, already a rest → warned)");
		this.assertEquals(d.totalDur, 3, "total unchanged");
	}

	test_addHit_splits {
		var d = RCDurList([2, 2]);
		d.addHit(1);
		this.assertEquals(d.asArray, [1, 1, 2], "first hit split at 1");
		d = RCDurList([Rest(2), 2]);
		d.addHit(0.5);
		this.assert(d[0].isRest and: { d[0].value == 0.5 }, "rest keeps its rest-ness when split");
		this.assertEquals(d[1], 1.5, "new hit takes the remainder");
	}

	test_addHit_on_boundary {
		var d = RCDurList([Rest(1), 1]);
		d.addHit(0);
		this.assert(d[0].isRest.not, "boundary on a rest un-rests it");
		d.addHit(1);
		this.assertEquals(d.asArray, [1, 1], "boundary on a hit leaves the list alone (warned)");
		d.addHit(5);
		this.assertEquals(d.asArray, [1, 1], "wraps beyond the total");
	}

	test_addHit_guards {
		var d = RCDurList([]);
		d.addHit(1);
		this.assertEquals(d.size, 0, "empty list untouched");
		d = RCDurList([Rest(0)]);
		d.addHit(1);
		this.assertEquals(d.asArray.collect(_.value), [0], "zero total untouched");
	}

	test_totalDur_truncates_and_extends {
		var d = RCDurList([1, 1, 1, 1]);
		d.totalDur = 2.5;
		this.assertEquals(d.asArray, [1, 1, 0.5], "truncated, last hit shortened");
		d.totalDur = 4;
		this.assertEquals(d.asArray, [1, 1, 2], "extended by stretching the last hit");
		d = RCDurList([1, Rest(1), 1]);
		d.totalDur = 1.5;
		this.assert(d[1].isRest and: { d[1].value == 0.5 }, "rest-ness kept when truncating");
		d.totalDur = -1;
		this.assertEquals(d.size, 2, "non-positive total ignored");
	}

	test_duplicate {
		var d = RCDurList([1, Rest(2)]);
		d.duplicate(2);
		this.assertEquals(d.size, 6, "two extra copies");
		this.assert(d[3].isRest, "rests copied");
		this.assertEquals(d.totalDur, 9, "total tripled");
	}
}
