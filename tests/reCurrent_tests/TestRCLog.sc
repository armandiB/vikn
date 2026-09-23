TestRCLog : UnitTest {
	var savedTimeFunc, savedRateLimit, now;

	setUp {
		savedTimeFunc = RCLog.timeFunc;
		savedRateLimit = RCLog.rateLimit;
		now = 100.0;
		RCLog.timeFunc = { now };
		RCLog.rateLimit = 1.0;
		RCLog.reset;
	}

	tearDown {
		RCLog.timeFunc = savedTimeFunc;
		RCLog.rateLimit = savedRateLimit;
		RCLog.reset;
	}

	test_rate_limit_per_tag {
		this.assert(RCLog.post(\a, "one"), "first message emitted");
		this.assert(RCLog.post(\a, "two").not, "same tag within rateLimit suppressed");
		this.assert(RCLog.post(\b, "other tag"), "different tag not affected");
		this.assert(RCLog.error(\a, "an error"), "a different level under the same tag is not suppressed");
		now = now + 1.5;
		this.assert(RCLog.post(\a, "three"), "emitted again after rateLimit");
		this.assert(RCLog.history.last[2].contains("(+1 suppressed)"), "suppressed count reported");
		now = now + 1.5;
		RCLog.post(\a, "four");
		this.assert(RCLog.history.last[2].contains("suppressed").not, "counter reset after reporting");
	}

	test_format_and_describe {
		this.assertEquals(RCLog.format(\tag, "msg"), "RC[tag]: msg", "format");
		this.assert(RCLog.describe(Error("boom")).contains("boom"), "describe an Error");
		this.assertEquals(RCLog.describe(42), "42", "describe anything else");
	}

	test_history_bounded {
		var saved = RCLog.historySize;
		RCLog.historySize = 3;
		5.do { |i| now = now + 2; RCLog.post(\h, i) };
		this.assertEquals(RCLog.history.size, 3, "history trimmed to historySize");
		RCLog.historySize = saved;
	}

	test_info_respects_verbose {
		var saved = RCLog.verbose;
		RCLog.verbose = false;
		this.assert(RCLog.info(\v, "hidden").not, "info silent when not verbose");
		RCLog.verbose = true;
		now = now + 2;
		this.assert(RCLog.info(\v, "shown"), "info emitted when verbose");
		RCLog.verbose = saved;
	}
}
