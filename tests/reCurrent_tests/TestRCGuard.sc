TestRCGuard : UnitTest {
	var savedStrict;

	setUp {
		savedStrict = RCGuard.strict;
		RCGuard.strict = false;
		RCLog.reset;
	}

	tearDown {
		RCGuard.strict = savedStrict;
	}

	test_call_returns_value {
		this.assertEquals(RCGuard.call(\t, -1) { 41 + 1 }, 42, "value returned when no error");
	}

	test_call_fallback_on_error {
		var res = RCGuard.call(\t, { 7 }) { nil.thisDoesNotExist };
		this.assertEquals(res, 7, "fallback.value returned on error");
		this.assertEquals(RCGuard.call(\t, \fb) { Error("boom").throw }, \fb, "plain fallback value");
	}

	test_callWithError_passes_error {
		var seen;
		RCGuard.callWithError(\t, { |err| seen = err }) { Error("boom").throw };
		this.assert(seen.isKindOf(Error), "handler receives the exception");
	}

	test_strict_rethrows {
		var caught = false;
		RCGuard.strict = true;
		try { RCGuard.call(\t, 0) { Error("boom").throw } } { |err| caught = true };
		this.assert(caught, "strict mode rethrows");
	}

	test_wrap {
		var f = RCGuard.wrap(\t, 0) { |a, b| a / b };
		this.assertEquals(f.(6, 3), 2, "wrapped function passes arguments");
		this.assertEquals(f.(1, nil), 0, "wrapped function falls back on error");
	}

	test_finite {
		var nan = 0 / 0;
		this.assertEquals(RCGuard.finite(3), 3, "finite number untouched");
		this.assertEquals(RCGuard.finite(nan, 5), 5, "NaN replaced");
		this.assertEquals(RCGuard.finite(inf, 5), 5, "inf replaced");
		this.assertEquals(RCGuard.finite([1, nan, [inf, 2]], 0), [1, 0, [0, 2]], "nested arrays sanitized");
		this.assertEquals(RCGuard.finite(\sym), \sym, "non-numbers untouched");
		this.assert(RCGuard.isFinite([1, [2, 3]]), "isFinite true");
		this.assert(RCGuard.isFinite([1, nan]).not, "isFinite false");
	}

	test_boundedLoop {
		var n = 0;
		var finished = RCGuard.boundedLoop(10, \t, { n < 3 }, { n = n + 1 });
		this.assert(finished, "loop ends normally");
		this.assertEquals(n, 3, "body ran 3 times");
		n = 0;
		finished = RCGuard.boundedLoop(5, \t, { true }, { n = n + 1 });
		this.assert(finished.not, "cap reached → false");
		this.assertEquals(n, 5, "body ran exactly maxIter times");
	}
}
