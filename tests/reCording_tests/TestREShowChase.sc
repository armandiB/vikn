// REShowChase: a control tape chased to the show clock: seek sends the state at a time (the last
// entry per state key, in tape order, transients and dropped addresses left out), advance streams
// a window, the filter hook rewrites an entry before it goes out.
TestREShowChase : UnitTest {
	var sent, entries;

	setUp {
		sent = List.new;
		entries = [
			[0.0, "/fx/chain", "colour", "blur"],
			[0.5, "/fx/set", "colour", "mix", 0.2],
			[1.0, "/fx/ctl", "wave", 0.1],
			[1.5, "/fx/rain", 1, 0.5, 0.2, 0.1],
			[2.0, "/fx/set", "colour", "mix", 0.6],
			[2.0, "/fx/set", "blur", "radius", 3],
			[2.5, "/fx/ctl", "wave", 0.4],
			[3.0, "/fx/start", 12.0],
			[3.5, "/fx/map", "wave", "blur", "radius", 0, 10],
			[4.0, "/fx/on", "glow", 1],
			[4.5, "/fx/clear"],
			[5.0, "/fx/ctl", "kick", 1],
			[5.5, "/fx/look", "hud", 0],
			[6.0, "/fx/page", "/x.html"],
			[6.5, "/fx/drop", 0.5, 0.5],
			[7.0, "/fx/level", 0.5, 2],
			[7.0, "/fx/layer", 1, 0.3],
			[8.0, "/fx/unmap", "blur", "radius"]
		];
	}

	prChase { |start = 10|
		var c = REShowChase(entries, start);
		c.sendFunc = { |p, a| sent.add([p.asString] ++ a) };
		^c
	}

	test_keys {
		this.assertEquals(REShowChase.keyOf([0, "/fx/chain", "a", "b"]), '/fx/chain', "the address alone");
		this.assertEquals(REShowChase.keyOf([0, "/fx/clear"]), '/fx/clear');
		this.assertEquals(REShowChase.keyOf([0, "/fx/level", 0.5]), '/fx/level');
		this.assertEquals(REShowChase.keyOf([0, "/fx/ctl", "wave", 0.1]), '/fx/ctl|wave', "a control by name");
		this.assertEquals(REShowChase.keyOf([0, "/fx/set", "colour", "mix", 0.2]), '/fx/set|colour|mix', "a parameter by effect and name");
		this.assertEquals(REShowChase.keyOf([0, "/fx/glide", "colour", "mix", 2]), '/fx/glide|colour|mix');
		this.assertEquals(REShowChase.keyOf([0, "/fx/map", "wave", "blur", "radius", 0, 10]), '/fx/map|blur|radius', "a map by the effect and parameter it drives, not the control");
		this.assertEquals(REShowChase.keyOf([0, "/fx/on", "glow", 1]), '/fx/on|glow');
		this.assertEquals(REShowChase.keyOf([0, "/fx/points", "kaleidoscope", 0.5, 0.5]), '/fx/points|kaleidoscope');
		this.assertEquals(REShowChase.keyOf([0, "/fx/layer", 1, 0.3]), '/fx/layer|1', "a layer by index");
		this.assertEquals(REShowChase.keyOf([0, "/fx/look", "hud", 0]), '/fx/look|hud');
		this.assertEquals(REShowChase.keyOf([0, "/fx/unmap", "blur", "radius"]), '/fx/unmap|blur', "anything else: the address and its first string argument");
		this.assertEquals(REShowChase.keyOf([0, '/fx/unmap']), '/fx/unmap', "no string argument: the address alone (a Symbol address too)");
		this.assertEquals(REShowChase.keyOf([0, "/fx/rain", 1]), \transient);
		this.assertEquals(REShowChase.keyOf([0, "/fx/drop", 0.5, 0.5]), \transient);
		[["/fx/start", 1], ["/fx/stop"], ["/fx/page", "/x.html"]].do { |e| this.assertEquals(REShowChase.keyOf([0] ++ e), \dropped, e[0] ++ " dropped") };
	}

	test_seek_sends_the_last_per_key_in_tape_order {
		var c = this.prChase(10), n;
		n = c.seek(14.2);
		this.assertEquals(n, 6, "six keys stand at 4.2 s of the tape");
		this.assertEquals(sent.asArray, [
			["/fx/chain", "colour", "blur"],
			["/fx/set", "colour", "mix", 0.6],
			["/fx/set", "blur", "radius", 3],
			["/fx/ctl", "wave", 0.4],
			["/fx/map", "wave", "blur", "radius", 0, 10],
			["/fx/on", "glow", 1]
		], "the last of each key, in the order the tape has them; the rain and the start left out");
		this.assertEquals(c.sent, 6);
	}

	test_seek_at_an_entry_includes_it {
		var c = this.prChase(10);
		c.seek(10.5);
		this.assertEquals(sent.asArray, [["/fx/chain", "colour", "blur"], ["/fx/set", "colour", "mix", 0.2]], "an entry at t counts");
		sent.clear;
		this.assertEquals(c.seek(9.99), 0, "before the cue: nothing");
	}

	test_seek_whole_tape {
		var c = this.prChase(10);
		c.seek(100);
		this.assertEquals(sent.collect(_[0]).asArray, ["/fx/chain", "/fx/set", "/fx/set", "/fx/ctl", "/fx/map", "/fx/on", "/fx/clear", "/fx/ctl", "/fx/look", "/fx/level", "/fx/layer", "/fx/unmap"],
			"every key once, the clear among them in its place, no start, page, rain or drop");
		this.assertEquals(sent[7], ["/fx/ctl", "kick", 1], "another control is another key");
	}

	test_advance_window {
		var c = this.prChase(10), n;
		n = c.advance(11.0, 12.0);
		this.assertEquals(n, 3);
		this.assertEquals(sent.collect(_[0]).asArray, ["/fx/rain", "/fx/set", "/fx/set"], "from < t <= to: the entry at 11.0 is out, the two at 12.0 in; a transient streams");
		sent.clear;
		this.assertEquals(c.advance(12.9, 13.1), 0, "/fx/start dropped");
		this.assertEquals(c.advance(16.0, 16.6), 1, "the page at 16.0 is out (and dropped), the drop at 16.5 goes");
		this.assertEquals(sent.last, ["/fx/drop", 0.5, 0.5]);
		sent.clear;
		this.assertEquals(c.advance(12.0, 12.0), 0, "an empty window");
		this.assertEquals(c.advance(12.0, 11.0), 0, "a window backwards");
		this.assertEquals(c.advance(17.9, 30), 1, "to the end");
		this.assertEquals(sent.last, ["/fx/unmap", "blur", "radius"]);
	}

	test_filter_replaces_or_drops {
		var c = this.prChase(10);
		c.filter = { |e| if(e[1] == "/fx/ctl" and: { e[2] == "wave" }) { [e[0], e[1], e[2], 9] } { e } };
		c.seek(14.2);
		this.assertEquals(sent[3], ["/fx/ctl", "wave", 9], "the filter's entry goes out in place of the tape's");
		this.assertEquals(sent[1], ["/fx/set", "colour", "mix", 0.6], "the others as they are");
		sent.clear;
		c.filter = { |e| if(e[1] == "/fx/ctl") { nil } { e } };
		this.assertEquals(c.advance(10.9, 11.1), 0, "nil: dropped");
		c.filter = { |e| e.explode };
		this.assertEquals(c.advance(11.9, 12.1), 0, "an erring filter drops its entry, the chase goes on");
		this.assertEquals(c.advance(16.9, 17.1), 0);
		c.filter = nil;
		this.assertEquals(c.advance(16.9, 17.1), 2, "...and sends again without it");
	}

	test_default_send_goes_through_rcvisuals {
		var saved = RCVisuals.sink, got = List.new, c;
		RCVisuals.sink = { |p, a| got.add([p, a]) };
		c = REShowChase(entries, 0);
		c.advance(0.9, 1.1);
		RCVisuals.sink = saved;
		this.assertEquals(got.asArray, [['/fx/ctl', ["wave", 0.1]]], "RCVisuals.sendArgs with the address a Symbol");
	}

	test_empty_and_unsorted_input {
		var c = REShowChase(nil, 5);
		this.assertEquals(c.size, 0);
		this.assertEquals(c.seek(10), 0);
		this.assertEquals(c.advance(0, 10), 0);
		this.assertEquals(c.end, 5.0, "an empty tape ends at its start");
		c = this.prChase(0);
		this.assertEquals(c.end, 8.0, "start plus the last entry's seconds");
	}
}
