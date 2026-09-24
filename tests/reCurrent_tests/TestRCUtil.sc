TestRCUtil : UnitTest {

	test_kvAt_and_keys {
		var kv = [\a, 1, \b, 2];
		this.assertEquals(RCUtil.kvAt(kv, \b), 2, "kvAt finds a value");
		this.assertEquals(RCUtil.kvAt(kv, \zz), nil, "kvAt returns nil when absent");
		this.assertEquals(RCUtil.kvKeys(kv), [\a, \b], "kvKeys lists keys in order");
		this.assert(RCUtil.kvIncludesKey(kv, \a), "kvIncludesKey true");
		this.assert(RCUtil.kvIncludesKey(kv, \c).not, "kvIncludesKey false");
	}

	test_kvReplace {
		var kv = [\a, 1, \b, 2];
		this.assertEquals(RCUtil.kvReplace(kv, \b, 20), [\a, 1, \b, 20], "replace existing");
		this.assertEquals(RCUtil.kvReplace(kv, \c, 3), [\a, 1, \b, 2], "absent key ignored by default");
		this.assertEquals(RCUtil.kvReplace(kv, \c, 3, addEndIfNotFound: true), [\a, 1, \b, 2, \c, 3], "absent key appended");
		this.assertEquals(RCUtil.kvReplaceMany(kv, [\a, \c], [10, 30], true), [\a, 10, \b, 2, \c, 30], "vector replace + append");
	}

	test_kvPutAll_overrides_and_preserves_order {
		var res = RCUtil.kvPutAll([\a, 1, \b, 2, \c, 3], [\b, 20, \d, 4]);
		this.assertEquals(res, [\a, 1, \c, 3, \b, 20, \d, 4], "overridden keys move to the end with their new value");
	}

	test_asKV {
		this.assertEquals(RCUtil.asKV([\a, 1, \b, 2]), [\a, 1, \b, 2], "kv array passes through");
		this.assertEquals(RCUtil.asKV([\a -> 1, \b -> 2]), [\a, 1, \b, 2], "associations flatten");
		this.assertEquals(RCUtil.asKV(nil), [], "nil → empty");
		this.assertEquals(RCUtil.asKV((a: 1)).size, 2, "Event → pairs");
	}

	test_asKV_warns_for_unordered_dictionaries {
		var savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
		RCUtil.asKV((a: 1, b: 2), \t);
		this.assert(RCLog.history.any { |e| e[2].contains("hash order") }, "a multi-key Event with a warnTag warns");
		RCLog.reset;
		RCUtil.asKV((a: 1), \t);
		RCUtil.asKV([\a, 1, \b, 2], \t);
		RCUtil.asKV((a: 1, b: 2));
		this.assertEquals(RCLog.history.size, 0, "single key, kv arrays and calls without a tag stay silent");
		RCLog.rateLimit = savedRateLimit;
	}

	test_rPut_rGet {
		var d = (x: (y: (z: 1)));
		this.assertEquals(RCUtil.rGet(d, [\x, \y, \z]), 1, "rGet nested");
		this.assertEquals(RCUtil.rGet(d, [\x, \nope, \z]), nil, "rGet missing path → nil");
		RCUtil.rPut(d, [\x, \y, \z], 2);
		this.assertEquals(d.x.y.z, 2, "rPut nested");
		RCUtil.rPut(d, \top, 5);
		this.assertEquals(d.top, 5, "rPut single key");
		this.assertEquals(RCUtil.rGet(d, \top), 5, "rGet single key");
	}

	test_isStatic {
		this.assert(RCUtil.isStatic(1), "number");
		this.assert(RCUtil.isStatic(\a), "symbol");
		this.assert(RCUtil.isStatic(nil), "nil");
		this.assert(RCUtil.isStatic("str"), "string");
		this.assert(RCUtil.isStatic([1, 2, [3, 4]]), "nested number array");
		this.assert(RCUtil.isStatic(#[1, 2]), "literal array");
		this.assert(RCUtil.isStatic(Ref(Pseq([1]))), "Ref is opaque");
		this.assert(RCUtil.isStatic((a: 1, b: [1, 2])), "event of numbers");
		this.assert(RCUtil.isStatic(Pseq([1])).not, "pattern");
		this.assert(RCUtil.isStatic({ 1 }).not, "function");
		this.assert(RCUtil.isStatic(Routine { 1.yield }).not, "routine");
		this.assert(RCUtil.isStatic([1, Pwhite(0, 1)]).not, "array containing a pattern");
		this.assert(RCUtil.isStatic((a: Pwhite(0, 1))).not, "event containing a pattern");
	}

	test_cartesianProduct {
		var saved = RCUtil.maxProductSize;
		this.assertEquals(RCUtil.cartesianProduct([[1, 2], [3, 4]]), [[1, 3], [1, 4], [2, 3], [2, 4]], "first array varies slowest");
		this.assertEquals(RCUtil.cartesianProduct([]), [[]], "empty input");
		this.assertEquals(RCUtil.cartesianProductProducts([[1, 2], [3, 4]]), [3, 4, 6, 8], "products");
		RCUtil.maxProductSize = 3;
		this.assertEquals(RCUtil.cartesianProduct([[1, 2], [3, 4]]), [], "cap exceeded → empty");
		RCUtil.maxProductSize = saved;
	}

	test_l1Vectors {
		var res = RCUtil.l1Vectors(2, 2);
		var brute = RCUtil.cartesianProduct([(-2..2), (-2..2)]).select { |v| v.abs.sum < 2 };
		this.assertEquals(res.size, 5, "2-D vectors with L1 < 2");
		this.assert(res.every { |v| brute.includesEqual(v) } and: { brute.every { |v| res.includesEqual(v) } }, "matches brute force");
		this.assertEquals(RCUtil.l1Vectors(3, 0), [], "t = 0 → none");
		this.assertEquals(RCUtil.l1Vectors(1, 1), [[0]], "1-D, t = 1 → origin only");
	}

	test_bagDifference {
		var res = RCUtil.bagDifference(Bag[1, 1, 2, 3], Bag[1, 3, 3]);
		this.assertEquals(res.asSet, Set[1, 2], "items with a positive count difference");
	}

	test_digitsInBase {
		this.assertEquals(RCUtil.digitsInBase(0.5, 2, 3), [1, 0, 0], "0.5 in base 2");
		this.assertEquals(RCUtil.digitsInBase(0.75, 2, 2), [1, 1], "0.75 in base 2");
		this.assertEquals(RCUtil.digitsInBase(0.75, 2, 0), [], "zero digits → empty, not two");
		this.assertEquals(RCUtil.digitsInBase(0.75, 2, nil), [], "nil digits → empty");
	}

	test_reservedKeys {
		this.assert(RCUtil.isReservedKey(\release), "release shadows Object:release");
		this.assert(RCUtil.isReservedKey(\size), "size shadows Object:size");
		this.assert(RCUtil.isReservedKey(\value), "value shadows Object:value");
		this.assert(RCUtil.isReservedKey(\freq).not, "freq is free");
		this.assert(RCUtil.isReservedKey(\decay).not, "decay is free");
		this.assert(RCUtil.isReservedKey(\dur_params).not, "dur_params is free");
		this.assert(RCUtil.isReservedKey(\delta).not, "delta is whitelisted");
	}
}
