// reLation, what the network page of HomewareVisuals is sent (its protocol: public/lib/network-viz.js there).
// No server.
TestRLNetworkPage : UnitTest {
	var got, saved;

	setUp {
		got = Array.new;
		saved = RCVisuals.sink;
		RCVisuals.sink = { |path, args| got = got.add([path] ++ args) };
	}

	tearDown { RCVisuals.sink = saved }

	test_take_a_bow_as_frames {
		var cd = RLRelationContext.cubeDance, sp = cd.space, t5 = RLAutomorphism.transposition(cd, 5);
		var cell = RLNetwork(cd, [[\a, sp.major(2)], [\b, sp.chord(2, \aug)], [\c, sp.minor(7)]], [[\a, \b, \U], [\b, \c, \U], [\a, \c, [\U, \U]]]);
		var cells = cell.iterate(t5, 2), nodes, arrows;
		RLNetworkPage.show(cells, "Take a Bow", nil, ["one", "two", "three"]);
		nodes = got.select { |m| m[0] == '/network/node' };
		arrows = got.select { |m| m[0] == '/network/arrow' };
		this.assertEquals(got.keep(3), [['/network/clear'], ['/network/title', "Take a Bow"], ['/network/caption', 0, "one"]], "a clear, the title, then each frame with its caption");
		this.assertEquals([nodes.size, arrows.size, got.size], [9, 9, 23], "three frames of three nodes and three arrows");
		this.assertEquals(nodes.collect(_.last), ["D", "D+", "Gm", "G", "Eb+", "Cm", "C", "C+", "Fm"], "the chords of the three cells");
		this.assertEquals(nodes[0].copyRange(1, 2), [0, 0], "frame 0, node 0");
		this.assertArrayFloatEquals(nodes[0].copyRange(3, 4), [0, -0.8], "at the top", 0.0001);
		this.assertEquals(arrows.keep(3).collect { |m| m.drop(2) }, [[0, 1, "U"], [1, 2, "U"], [0, 2, cells[0].labelNames[2].asString]], "the arrows by the places of their nodes, with their labels");
		this.assertEquals(arrows.collect(_.last).asSet.size, 2, "the same two labels in every frame: this automorphism changes none");
	}

	test_one_network_and_the_frame_that_plays {
		var cd = RLRelationContext.cubeDance, sp = cd.space;
		RLNetworkPage.show(RLNetwork.chain(cd, [sp.major(0), sp.minor(0)], [\P]), nil, [[-0.5, 0], [0.5, 0]]);
		this.assertEquals(got, [['/network/clear'], ['/network/node', 0, 0, -0.5, 0, "C"], ['/network/node', 0, 1, 0.5, 0, "Cm"], ['/network/arrow', 0, 0, 1, "P"]],
			"one network, places given");
		got = Array.new;
		RLNetworkPage.at(3);
		RLNetworkPage.at;
		this.assertEquals(got, [['/network/at', 3], ['/network/at', -1]], "the frame that plays, and none");
	}
}
