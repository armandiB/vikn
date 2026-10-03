// REJSON: every scalar type round-trips exactly through sclang's String-only
// parseJSON, output is stable (key order) and shaped (inline depth), files are
// written under folders made on the way.
TestREJSON : UnitTest {
	var savedRateLimit;

	setUp {
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown {
		RCLog.rateLimit = savedRateLimit;
	}

	roundTrip { |obj| ^REJSON.parse(REJSON.stringify(obj)) }

	// What the reader builds: IdentityDictionaries (know = false, so an Event
	// literal is not == to one), recursively.
	plain { |obj|
		var res;
		if(obj.isKindOf(Dictionary)) {
			res = IdentityDictionary.new;
			obj.keysValuesDo { |k, v| res[k.asSymbol] = this.plain(v) };
			^res
		};
		if(obj.isKindOf(SequenceableCollection) and: { obj.isString.not }) { ^obj.collect { |el| this.plain(el) } };
		^obj
	}

	test_scalars_written {
		this.assertEquals(REJSON.stringify(nil), "null");
		this.assertEquals(REJSON.stringify(true), "true");
		this.assertEquals(REJSON.stringify(false), "false");
		this.assertEquals(REJSON.stringify(42), "42");
		this.assertEquals(REJSON.stringify(-3), "-3");
		this.assertEquals(REJSON.stringify(2.0), "2.0", "an integral float keeps its .0");
		this.assertEquals(REJSON.stringify(0.1), "0.1", "the shortest string that reads back");
		this.assertEquals(REJSON.stringify(1/3).asFloat, 1/3, "17 digits when needed");
		this.assertEquals(REJSON.stringify(1e-7), "1e-07", "exponent form as sclang prints it");
		this.assertEquals(REJSON.stringify("a\"b\\c\nd\té"), "\"a\\\"b\\\\c\\nd\\té\"", "escapes; UTF-8 bytes pass through");
		this.assertEquals(REJSON.stringify(\amp), "\"\\\\amp\"", "a Symbol is a string with a leading backslash");
		this.assertEquals(REJSON.stringify("42"), "{\"str\": \"42\"}", "a number-like String is wrapped");
		this.assertEquals(REJSON.stringify("true"), "{\"str\": \"true\"}", "a Boolean-like String is wrapped");
		this.assertEquals(REJSON.stringify("\\x"), "{\"str\": \"\\\\x\"}", "a String with a leading backslash is wrapped");
		this.assertEquals(REJSON.stringify(inf), "null", "a non-finite number is written as null");
		this.assert(RCLog.history.any { |e| e[2].contains("non-finite") }, "and warned about");
	}

	test_scalars_round_trip {
		[nil, true, false, 0, 42, -3, 2147483647, 0.1, 1/3, 1e-7, 1e20, -0.5, "", "x y", "42", "1.5", "true", "\\x", "a\"b\\c\nd\té", \amp, '', 'with space'].do { |x|
			var back = this.roundTrip(x);
			this.assertEquals(back, x, "round trip of " ++ x.asCompileString);
			this.assertEquals(back.class, x.class, "class kept for " ++ x.asCompileString);
		};
		this.assertEquals(this.roundTrip(2.0), 2.0);
		this.assert(this.roundTrip(2.0).isKindOf(Float), "2.0 comes back as a Float");
		this.assert(this.roundTrip(2147483648.0).isKindOf(Float), "above 2^31: a Float, not an overflowed Integer");
	}

	test_containers_round_trip {
		var obj = (format: "re-score", events: [(id: 1, beat: 0.0, args: [\amp, 0.5, nil, \default]), (id: 2, text: "~x = 1")], empty: [], none: ());
		var back = this.roundTrip(obj);
		this.assertEquals(back, this.plain(obj), "nested arrays and objects");
		this.assert(back.isKindOf(IdentityDictionary), "an object reads back as an IdentityDictionary");
		this.assertEquals(back[\events][0][\args], [\amp, 0.5, nil, \default], "symbols and nil inside arrays");
		this.assertEquals(REJSON.stringify([]), "[]");
		this.assertEquals(REJSON.stringify(()), "{}");
		this.assertEquals(this.roundTrip([]), [], "empty array");
		this.assertEquals(this.roundTrip(()), IdentityDictionary.new, "empty object");
		this.assertEquals(this.roundTrip(List[1, 2]), [1, 2], "a List is an array");
		this.assertEquals(REJSON.stringify(Dictionary["a" -> 1]), "{\"a\": 1}", "string keys");
		this.assertEquals(this.roundTrip(Dictionary["a" -> 1]), IdentityDictionary[\a -> 1], "read back with Symbol keys");
	}

	test_pretty_and_key_order {
		var obj = (events: [(id: 2, beat: 1.0, kind: "x"), (beat: 2.0, id: 3)], format: "re-score", zed: 1, alpha: 2);
		var text = REJSON.stringify(obj, indent: 2, inlineDepth: 2, keyOrder: #[\format, \events, \id, \beat]);
		var expected = "{\n  \"format\": \"re-score\",\n  \"events\": [\n    {\"id\": 2, \"beat\": 1.0, \"kind\": \"x\"},\n    {\"id\": 3, \"beat\": 2.0}\n  ],\n  \"alpha\": 2,\n  \"zed\": 1\n}";
		this.assertEquals(text, expected, "keyOrder first in that order, the rest sorted; nodes at depth 2 on one line");
		this.assertEquals(REJSON.parse(text), this.plain(obj), "reads back");
		this.assertEquals(REJSON.stringify(obj, keyOrder: #[\format]).keep(21), "{\"format\": \"re-score\"", "compact by default");
		this.assertEquals(REJSON.stringify((b: 1, a: [1, 2])), "{\"a\": [1, 2], \"b\": 1}", "sorted keys, compact separators");
	}

	test_parse_errors {
		this.assertEquals(REJSON.parse(nil), nil, "nil text");
		this.assertEquals(REJSON.read(PathName.tmp +/+ "re_json_no_such_file.json"), nil, "missing file → nil");
		this.assert(RCLog.history.any { |e| e[2].contains("no file") }, "and reported");
	}

	test_write_and_read {
		var dir = PathName.tmp +/+ "re_json_" ++ UniqueID.next;
		var path = dir +/+ "deeper" +/+ "take.json";
		var obj = (song: \demo, events: [(id: 1, beat: 0.5, text: "~a = \\amp")]);
		var written = REJSON.write(obj, path, indent: 2, inlineDepth: 2);
		this.assertEquals(written, path.standardizePath, "returns the path");
		this.assert(File.exists(path), "file written under folders made on the way");
		this.assertEquals(REJSON.read(path), this.plain(obj), "reads back equal");
		File.delete(path);
		File.delete(dir +/+ "deeper");
		File.delete(dir);
	}

	test_write_fails_naming_the_path {
		var path = "/re_json_" ++ UniqueID.next ++ ".json";   // the root folder is not writable
		var error = try { REJSON.write((a: 1), path); nil } { |e| e };
		this.assert(error.notNil and: { error.isKindOf(PrimitiveFailedError).not }, "an Error of its own, not a failed primitive");
		this.assert(error.notNil and: { error.errorString.contains(path) }, "it names the path");
	}
}
