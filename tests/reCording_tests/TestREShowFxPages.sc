// REShowFxPages: the Roto-Control's two pages over the effects page's fixed chain, through the public methods the
// MIDI handlers call (no device, no MIDIClient): the sends to the page, the motors and lights, the panel's state, the
// state sent again (REShowConsole with a show's "fx": TestREShowConsole's test_fx_pages).
TestREShowFxPages : UnitTest {
	var sent, motors, pages, savedRateLimit;

	spec {
		^(device: "No Such Device", mixDead: 0.5,
			chain: ["warp", "kaleidoscope", "colour", "glow"],
			pages: [(name: "shape", cc: 0, effects: ["warp", "kaleidoscope"]), (name: "light", cc: 8, effects: ["colour", "glow"])],
			layouts: (
				warp: [["amount", 0, 0.2, 0.02], ["scale", 0.5, 20, 3, "exp"]],
				kaleidoscope: [["sectors", 1, 24, 6], ["mode", 0, 5, 1, "int"]],
				colour: [["hue", -180, 180, 0], (name: "warmth", lo: -1, hi: 1, rest: 0, set: [["tint_r", 0.5, 1.5], ["tint_b", 1.5, 0.5]])]
			))
	}

	setUp {
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
		sent = List.new;
		motors = List.new;
		pages = REShowFxPages(this.spec);
		pages.sendFunc = { |p, a| sent.add([p.asString] ++ a) };
		pages.midiFunc = { |chan, cc, v| motors.add([chan, cc, v]) };
		pages.timeFunc = { 0 };
	}

	tearDown {
		pages.stop;
		RCLog.rateLimit = savedRateLimit;
	}

	sets { ^sent.select { |m| m[0] == "/fx/set" } }

	test_spec {
		this.assertEquals(pages.chain, ["warp", "kaleidoscope", "colour", "glow"]);
		this.assertEquals(pages.pages.size, 2);
		this.assertEquals(pages.pages[1][\cc], 8);
		this.assertEquals(pages.layout("warp").size, 2);
		this.assertEquals(pages.layout("glow"), [], "an effect without a layout: no knobs");
		this.assertEquals(pages.layout("colour")[1][\targets].size, 2, "a knob over two parameters");
		this.assertEquals(pages.effectOf(0), "warp", "each page starts on its first effect");
		this.assertEquals(pages.effectOf(1), "colour");
		this.assertFloatEquals(pages.knobValue("warp", 0), 0.02, "a knob starts at its rest");
		this.assertFloatEquals(pages.knobValue("warp", 1), 3.0, "an exp knob's rest");
		this.assertEquals(pages.knobValue("kaleidoscope", 1), 1, "an int knob's rest, an Integer");
		this.assertFloatEquals(pages.knobValue("colour", 1), 0.0, "the two-parameter knob's own value");
		this.assertEquals(pages.mix("glow"), 0.0, "every mix starts at 0");
		this.assertEquals(pages.knobValue("warp", 5), nil, "a knob the layout leaves free");
	}

	test_spec_warnings {
		var p = REShowFxPages((chain: ["a"], pages: [(effects: ["a", "b"])], layouts: (a: [["x", 0, 1, 0, "cube"], ["y", 0, 1, 0, "exp"]])));
		this.assert(RCLog.history.any { |e| e[2].contains("b is not in the chain") }, "an effect on a page, not in the chain");
		this.assert(RCLog.history.any { |e| e[2].contains("no curve cube") }, "an unknown curve");
		this.assert(RCLog.history.any { |e| e[2].contains("exp needs lo and hi above 0") }, "exp from 0");
		this.assertEquals(p.layout("a")[0][\curve], \lin, "both fall back to lin");
		this.assertEquals(p.pages[0][\cc], 0, "a page without cc: 8 per page from 0");
		this.assertEquals(REShowFxPages(nil).pages.size, 0, "no spec: nothing");
	}

	test_numbers {
		this.assertFloatEquals(REShowFxPages.valueOf(0.5, 0, 0.2), 0.1);
		this.assertFloatEquals(REShowFxPages.valueOf(0.5, 1, 100, \exp), 10.0, "exp: the middle is the geometric mean");
		this.assertEquals(REShowFxPages.valueOf(0.55, 0, 5, \int), 3);
		this.assertFloatEquals(REShowFxPages.valueOf(2, 0, 1), 1.0, "a place clipped");
		this.assertFloatEquals(REShowFxPages.placeOf(10, 1, 100, \exp), 0.5);
		this.assertFloatEquals(REShowFxPages.placeOf(0.02, 0, 0.2), 0.1);
		this.assertFloatEquals(REShowFxPages.placeOf(5, 0, 1), 1.0, "a value clipped to the range");
		this.assertFloatEquals(REShowFxPages.positionOf(127 + (127 / 128)), 1.0, "the 14-bit top");
		this.assertFloatEquals(REShowFxPages.positionOf(64), 64 * 128 / 16383);
	}

	test_mix_dead_zone {
		var place = { |cc| cc * 128 / 16383 };
		this.assertEquals(pages.mixOf(0), 0.0);
		this.assertEquals(pages.mixOf(place.(0.5)), 0.0, "0.5 of 127: still off");
		this.assertEquals(pages.mixOf(place.(0.3)), 0.0);
		this.assert(pages.mixOf(place.(0.6)) > 0, "just past 0.5: on");
		this.assertFloatEquals(pages.mixOf(place.(127)), 1.0, "127: full");
		this.assertFloatEquals(pages.mixOf(place.(63.75)), 0.5, "linear between");
		this.assertFloatEquals(pages.mixOf(1.0), 1.0);
	}

	test_knobs {
		var v;
		v = pages.knobReceived(0, 0, 0.5);
		this.assertFloatEquals(v, 0.1);
		this.assertEquals(sent.last, ["/fx/set", "warp", "amount", 0.1]);
		pages.knobReceived(0, 7, 1.0);
		this.assertEquals(sent.last, ["/fx/set", "warp", "mix", 1.0], "knob 8: the mix");
		sent.clear;
		this.assertEquals(pages.knobReceived(0, 4, 0.3), nil, "a free knob: nothing");
		this.assert(sent.isEmpty);
		pages.knobReceived(1, 1, 1.0);
		this.assertEquals(sent.asArray, [["/fx/set", "colour", "tint_r", 1.5], ["/fx/set", "colour", "tint_b", 0.5]], "one knob, two parameters");
		pages.knobReceived(1, 0, 0.0);
		this.assertEquals(sent.last, ["/fx/set", "colour", "hue", -180.0]);
	}

	test_select_motors_lights {
		pages.knobReceived(0, 0, 0.25);
		pages.knobReceived(0, 7, 0.6);
		motors.clear;
		this.assertEquals(pages.buttonReceived(0, 1, 127), "kaleidoscope", "button 2 chooses the page's second effect");
		this.assertEquals(pages.effectOf(0), "kaleidoscope");
		this.assertEquals(motors.size, 24, "8 knobs as 14-bit pairs, 8 lights");
		this.assertEquals(motors[0], [0, 0, ((6 - 1) / 23 * 16383).round.asInteger >> 7], "knob 1 to the sectors' rest");
		this.assertEquals(motors[4], [0, 2, 0], "a free knob down");
		this.assertEquals(motors[5], [0, 34, 0], "its LSB");
		this.assertEquals(motors[14], [0, 7, 0], "the mix knob down: kaleidoscope's mix 0");
		this.assertEquals(motors.copyRange(16, 23).collect(_[2]), [0, 127, 0, 0, 0, 0, 0, 0], "button 2 lit");
		this.assert(motors.copyRange(16, 23).every { |m| m[0] == 1 }, "the lights on the buttons' channel");
		motors.clear;
		pages.buttonReceived(0, 0, 0);
		this.assertEquals(pages.effectOf(0), "warp", "a release (0) chooses too");
		this.assertEquals(motors[0], [0, 0, (0.25 * 16383).round.asInteger >> 7], "warp's knob 1 back where it was left");
		this.assertEquals(motors[14], [0, 7, (0.6 * 16383).round.asInteger >> 7], "its mix where it was left");
		this.assertEquals(pages.buttonReceived(0, 5, 127), nil, "a button past the page's effects: nothing");
		this.assertEquals(pages.effectOf(0), "warp");
		this.assertEquals(pages.buttonReceived(1, 1, 127), "glow");
		motors.clear;
		pages.select(1, 1);
		this.assertEquals(motors[0], [0, 8, 0], "page 2's knobs from cc 8; glow has no layout: down");
	}

	test_reset {
		pages.knobReceived(0, 0, 1.0);
		pages.knobReceived(0, 1, 1.0);
		pages.knobReceived(0, 7, 0.7);
		sent.clear; motors.clear;
		this.assertEquals(pages.buttonReceived(0, 7, 127), "warp", "button 8 resets");
		this.assertFloatEquals(pages.knobValue("warp", 0), 0.02);
		this.assertFloatEquals(pages.knobValue("warp", 1), 3.0);
		this.assert(pages.mix("warp") > 0.6, "the mix left as it is");
		this.assertEquals(this.sets.collect { |m| m[2] }, ["amount", "scale"], "the parameters sent, not the mix");
		this.assertEquals(motors.size, 16, "the motors back, no lights");
	}

	test_start_state {
		pages.knobReceived(0, 7, 0.5);
		sent.clear;
		pages.start(false);
		this.assertEquals(sent.first, ["/fx/clear"], "start: a clean page first");
		this.assertEquals(sent.select { |m| m[0] == "/fx/chain" }, [["/fx/chain", "warp", "kaleidoscope", "colour", "glow"]]);
		this.assert(sent.indexOf(sent.detect { |m| m[0] == "/fx/chain" }) == (sent.size - 1), "the chain last: every mix set before");
		this.assertEquals(this.sets.select { |m| m[2] == "mix" }.collect { |m| m[1] }, ["warp", "kaleidoscope", "colour", "glow"], "every mix");
		this.assert(this.sets.select { |m| m[2] == "mix" and: { m[1] != "warp" } }.every { |m| m[3] == 0 }, "at 0");
		this.assertEquals(this.sets.select { |m| m[1] == "colour" }.collect { |m| m[2] }, ["hue", "tint_r", "tint_b", "mix"], "every layout parameter at its place");
		this.assertEquals(motors.size, 48, "both pages' motors and lights");
		sent.clear;
		pages.sendState;
		this.assert(sent.first[0] != "/fx/clear", "sendState alone: no clear");
		this.assertEquals(sent.last[0], "/fx/chain");
	}

	test_refresh {
		var chains = { sent.count { |m| m[0] == "/fx/chain" } };
		pages.tick(5);
		this.assertEquals(chains.value, 0, "not started: no state");
		pages.start(false);
		sent.clear;
		pages.tick(1);
		this.assertEquals(chains.value, 0, "within refresh of the start's state (at the clock's 0): nothing");
		pages.tick(2.5);
		this.assertEquals(chains.value, 1, "refresh seconds on: the whole state again (a page opened late)");
		sent.clear;
		pages.tick(3.1);
		this.assertEquals(chains.value, 0, "within refresh of that one: nothing");
		pages.tick(4.6);
		this.assertEquals(chains.value, 1, "and again");
		this.assert(sent.any { |m| m == ["/fx/set", "glow", "mix", 0.0] }, "every mix in it");
		this.assertEquals(sent.count { |m| m[0] == "/fx/clear" }, 0, "never a clear");
		pages.refresh = 0;
		sent.clear;
		pages.tick(100);
		this.assertEquals(chains.value, 0, "refresh 0: never");
	}

	test_panel {
		var state, json;
		pages.knobReceived(0, 7, 1.0);
		pages.select(0, 1);
		sent.clear;
		pages.tick(10);
		this.assertEquals(sent.size, 1, "a change: the panel's state");
		this.assertEquals(sent[0][0], "/fxpanel/state");
		state = REJSON.parse(sent[0][1]);
		this.assertEquals(state[\pages][0][\effect], "kaleidoscope");
		this.assertEquals(state[\pages][0][\selected], 1);
		this.assertEquals(state[\pages][0][\knobs].size, 8);
		this.assertEquals(state[\pages][0][\knobs][0][\label], "sectors");
		this.assertFloatEquals(state[\pages][0][\knobs][0][\value], 6.0);
		this.assertEquals(state[\pages][0][\knobs][2], nil, "a free knob: null");
		this.assertEquals(state[\pages][0][\knobs][7][\label], "mix");
		this.assertEquals(state[\pages][0][\buttons].size, 8, "7 buttons and the reset");
		this.assertEquals(state[\pages][0][\buttons][0][\effect], "warp");
		this.assertFloatEquals(state[\pages][0][\buttons][0][\mix], 1.0, "warp's mix on its button");
		this.assertEquals(state[\pages][0][\buttons][2], nil, "an empty button");
		this.assertEquals(state[\pages][0][\buttons][7][\label], "reset");
		this.assertEquals(state[\chain].collect { |c| c[\effect] }, ["warp", "kaleidoscope", "colour", "glow"]);
		sent.clear;
		pages.tick(10.5);
		this.assert(sent.isEmpty, "nothing changed within the second: nothing");
		pages.tick(11.1);
		this.assertEquals(sent.size, 1, "every second: again (a panel opened late)");
	}

	// an effect with more than seven knobs: banks of seven, the chosen effect's button going round them
	bankSpec { |more|
		var s = (device: "No Such Device", chain: ["aberration", "warp"],
			pages: [(name: "light", cc: 0, effects: ["aberration", "warp"])],
			layouts: (
				aberration: [["amount", 0, 0.05, 0.005], ["angle", 0, 360, 0], ["radial", 0, 1, 1, "int"], ["power", 0.25, 4, 1], ["spectrum", 0, 1, 0],
					["bright", 0, 1, 0], ["defocus", 0, 1, 0], ["hue", 0, 360, 0], ["basis", 0, 2, 0, "int"], ["jitter", 0, 1, 0]],
				warp: [["amount", 0, 0.2, 0.02]]));
		(more ? ()).keysValuesDo { |k, v| s[k] = v };
		^s
	}

	banked { |more|
		var p = REShowFxPages(this.bankSpec(more));
		p.sendFunc = { |path, a| sent.add([path.asString] ++ a) };
		p.midiFunc = { |chan, cc, v| motors.add([chan, cc, v]) };
		p.timeFunc = { 0 };
		^p
	}

	test_banks_press {
		var p = this.banked, state;
		this.assertEquals(p.bankCount("aberration"), 2, "ten knobs: two banks");
		this.assertEquals(p.bankCount("warp"), 1);
		this.assertEquals(p.bankOf("aberration"), 0);
		this.assertFloatEquals(p.knobValue("aberration", 3), 1.0, "bank 1's fourth knob: power at rest");
		p.buttonReceived(0, 0, 127, 1.0);
		this.assertEquals(p.bankOf("aberration"), 1, "the chosen effect's button pressed: its next bank");
		this.assertEquals(p.knobValue("aberration", 0), 0.0, "knob 1 is now hue");
		this.assertEquals(p.knobValue("aberration", 3), nil, "bank 2 has three knobs: the fourth is free");
		p.buttonReceived(0, 0, 0, 1.05);
		this.assertEquals(p.bankOf("aberration"), 1, "the release (0) is nothing");
		p.buttonReceived(0, 0, 127, 1.06);
		this.assertEquals(p.bankOf("aberration"), 1, "a second message within bankWait: the same press");
		sent.clear; motors.clear;
		p.knobReceived(0, 0, 0.5);
		this.assertEquals(sent.last, ["/fx/set", "aberration", "hue", 180.0], "the knobs drive the bank shown");
		p.knobReceived(0, 7, 1.0);
		this.assertEquals(sent.last, ["/fx/set", "aberration", "mix", 1.0], "the eighth is the mix in every bank");
		motors.clear;
		p.buttonReceived(0, 0, 127, 2.0);
		this.assertEquals(p.bankOf("aberration"), 0, "round to the first");
		this.assertEquals(motors[0], [0, 0, (0.1 * 16383).round.asInteger >> 7], "the motors to bank 1's places: amount at rest");
		this.assertEquals(motors[14][2], 127, "the mix knob where it was left");
		p.buttonReceived(0, 1, 127, 3.0);
		this.assertEquals(p.effectOf(0), "warp");
		p.buttonReceived(0, 1, 127, 4.0);
		this.assertEquals(p.bankOf("warp"), 0, "one bank: its button again changes nothing");
		p.buttonReceived(0, 0, 127, 5.0);
		this.assertEquals(p.bankOf("aberration"), 0, "choosing an effect is not a press on it: the bank it was left on");
		p.setBank(0, 1);
		p.reset(0);
		this.assertFloatEquals(p.knobValue("aberration", 0, 0), 0.005, "the reset takes every bank back");
		this.assertEquals(p.knobValue("aberration", 0, 1), 0.0);
		state = p.panelState;
		this.assertEquals(state[\pages][0][\bank], 1);
		this.assertEquals(state[\pages][0][\banks], 2);
		this.assertEquals(state[\pages][0][\knobs][0][\label], "hue", "the panel shows the bank's knobs");
		this.assertEquals(state[\pages][0][\knobs][7][\label], "mix");
		sent.clear;
		p.sendState;
		this.assertEquals(sent.count { |m| m[0] == "/fx/set" and: { m[1] == "aberration" } }, 11, "the state holds every bank's parameters and the mix");
		p.stop;
	}

	test_banks_lights_and_value {
		var p = this.banked((bankLights: [127, 64])), q;
		p.select(0, 0);
		this.assertEquals(motors.asArray.keep(-8).collect(_[2]), [127, 0, 0, 0, 0, 0, 0, 0], "bank 1's light");
		p.setBank(0, 1);
		this.assertEquals(motors.asArray.keep(-8).collect(_[2]), [64, 0, 0, 0, 0, 0, 0, 0], "bank 2's light");
		p.stop;
		// a button of three states (dark, 64, 127): its value says the bank
		q = this.banked((bankBy: "value", bankValues: [64, 127]));
		q.buttonReceived(0, 0, 127);
		this.assertEquals(q.bankOf("aberration"), 1, "127: the second bank");
		this.assertEquals(motors.asArray.keep(-8)[0][2], 127, "its light the state's value");
		q.buttonReceived(0, 0, 0);
		this.assertEquals(q.bankOf("aberration"), 0, "0 on the chosen effect's button (gone round to dark): the first bank");
		this.assertEquals(motors.asArray.keep(-8)[0][2], 64, "and the button sent the first bank's value");
		q.buttonReceived(0, 0, 63);
		this.assertEquals(q.bankOf("aberration"), 0, "63 is nearest 64");
		q.buttonReceived(0, 1, 127);
		this.assertEquals(q.effectOf(0), "warp", "a value chooses the effect");
		this.assertEquals(q.bankOf("warp"), 0, "an effect of one bank stays on it whatever the value");
		q.stop;
	}

	// the graph of the effects engine's w1: nodes, source nodes, wires, the node shown, looks, values set once
	test_graph {
		var p = REShowFxPages((device: "No Such Device", chain: ["comp", "warp", "blur2"],
			nodes: (cut: "alpha", comp: "blend", blur2: "blur"),
			inputs: (setting: "main", clip: "video:/recordings/x.mp4:follow:1.5"),
			wires: ('comp.b': "cut", 'cut.in': "setting"),
			out: "blur2", look: (layers: 1),
			set: (kaleidoscope: (seam: 1), comp: (mix: 1)),
			pages: [(name: "a", cc: 0, effects: ["warp", "blur2", "cut"])],
			layouts: (warp: [["amount", 0, 0.2, 0.02]], blur: [["radius", 0, 64, 8]])));
		var paths, at;
		p.sendFunc = { |path, a| sent.add([path.asString] ++ a) };
		p.midiFunc = { |chan, cc, v| motors.add([chan, cc, v]) };
		p.timeFunc = { 0 };
		this.assert(RCLog.history.any { |e| e[2].contains("cut is not in the chain") }.not, "a node of the graph on a page, off the chain: no warning");
		this.assertEquals(p.layout("blur2").size, 1, "a made node without a layout takes its effect's");
		p.start(false);
		paths = sent.collect(_[0]);
		at = { |path| paths.indexOfEqual(path) };
		this.assertEquals(sent.first, ["/fx/clear"]);
		this.assertEquals(sent.select { |m| m[0] == "/fx/node" }, [["/fx/node", "blur2", "blur"], ["/fx/node", "comp", "blend"], ["/fx/node", "cut", "alpha"]], "the nodes, by name");
		this.assertEquals(sent.select { |m| m[0] == "/fx/input" }, [["/fx/input", "clip", "video", "/recordings/x.mp4", "follow", 1.5], ["/fx/input", "setting", "main"]], "a video that follows: its mode and its offset after the path");
		this.assertEquals(sent.select { |m| m[0] == "/fx/wire" }, [["/fx/wire", "comp", "b", "cut"], ["/fx/wire", "cut", "in", "setting"]]);
		this.assertEquals(sent.select { |m| m[0] == "/fx/out" }, [["/fx/out", "blur2"]]);
		this.assertEquals(sent.select { |m| m[0] == "/fx/look" }, [["/fx/look", "layers", 1]]);
		this.assert(sent.includesEqual(["/fx/set", "kaleidoscope", "seam", 1]), "a value set once");
		this.assert(sent.includesEqual(["/fx/set", "comp", "mix", 1]), "a node's mix set by the spec");
		this.assertEquals(sent.count { |m| m[0] == "/fx/set" and: { m[1] == "comp" } }, 1, "and no knob's mix sent for it");
		this.assert(sent.includesEqual(["/fx/set", "blur2", "radius", 8.0]), "the made node's knob at rest");
		this.assert(sent.includesEqual(["/fx/set", "cut", "mix", 0.0]), "a page's node off the chain has a mix too");
		this.assert(at.("/fx/node") < at.("/fx/set"), "the nodes before anything is set on them");
		this.assert(at.("/fx/set") < at.("/fx/wire"), "the wires after");
		this.assertEquals(sent.last, ["/fx/chain", "comp", "warp", "blur2"], "the chain last");
		sent.clear;
		p.tick(2.5);
		this.assertEquals(sent.count { |m| m[0] == "/fx/node" }, 3, "the graph again with every state: a page opened late has it");
		this.assertEquals(sent.count { |m| m[0] == "/fx/clear" }, 0);
		p.stop;
	}
}
