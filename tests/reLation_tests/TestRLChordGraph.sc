// reLation, what the chord graph page of HomewareVisuals is sent (its protocol: public/lib/chordgraph-viz.js
// there): the graph laid out here, then a walk on it. No server.
TestRLChordGraph : UnitTest {
	var cd, sp, graph, got, saved;

	setUp {
		cd = RLRelationContext.cubeDance;
		sp = cd.space;
		graph = RLGraph.voiceLeading(cd.union);
		got = Array.new;
		saved = RCVisuals.sink;
		RCVisuals.sink = { |path, args| got = got.add([path] ++ args) };
	}

	tearDown { RCVisuals.sink = saved }

	test_edges_of_a_graph {
		var edges = graph.edges, one = RLGraph(RLRelation.fromPairs([\a, \b, \c], [\a, \b, \c], [[\a, \b], [\b, \a], [\c, \a]]));
		this.assertEquals([edges.size, graph.edgeCount], [48, 48], "Cube Dance: 48 edges, each listed once");
		this.assert(edges.every { |e| (e[0] < e[1]) and: { e[3] } and: { e[2] == 1 } }, "the smaller place first, both ways, one step of one voice");
		this.assertEquals(graph.objects[graph.indexOf(sp.minor(7))], sp.minor(7), "the place of a chord among the vertices");
		this.assertEquals(graph.indexOf(RLChordSpace.triads(RLEDO(19)).major(0)), nil, "nil for what is not a vertex");
		this.assertEquals(one.edges, [[0, 1, 1, true], [2, 0, 1, false]], "an edge that goes one way keeps its direction");
	}

	test_voice_leading_zones {
		var places = RLChordGraph.zones(graph), zone = { |x| x.pcs.sum % 12 }, pt = RLRelationContext.powerTowers, ptGraph = pt.graph;
		var next = { |g| g.edges.every { |e| [1, 11].includes((zone.(g.objects[e[0]]) - zone.(g.objects[e[1]])) % 12) } };
		this.assertArrayFloatEquals(places[graph.indexOf(sp.chord(0, \aug))], [0, -0.78], "C augmented, alone in zone 0, at the top", 0.0001);
		this.assertArrayFloatEquals(places[graph.indexOf(sp.chord(1, \aug))], [0.78, 0], "C# augmented a quarter turn on: zone 3", 0.0001);
		this.assertEquals([zone.(sp.minor(0)), zone.(sp.major(0))], [10, 11], "C minor and C major in the two zones before the top");
		this.assert(next.(graph), "every edge of Cube Dance joins two neighbouring zones");
		this.assert(next.(ptGraph), "and every edge of Power Towers");
		this.assertEquals(RLChordGraph.zones(ptGraph).collect { |p| p.squared.sum.sqrt.round(0.01) }.asSet.size, 5,
			"four chords on a ray at four distances, a diminished seventh alone at a fifth");
		this.assert(places.every { |p| p.squared.sum.sqrt <= 1.0001 }, "all inside the unit circle");
	}

	test_other_layouts {
		var cycle = graph.hamiltonianCycle, ring = RLChordGraph.layout(graph, \cycle), circle = RLChordGraph.circle(28);
		this.assertEquals(RLChordGraph.circle(4), [[0, -1], [1, 0], [0, 1], [-1, 0]], "a circle: the first at the top, then clockwise");
		this.assertEquals(RLChordGraph.layout(graph, \circle), circle, "the vertices in their order");
		this.assertEquals(cycle.keep(28).collect { |x| ring[graph.indexOf(x)] }, circle, "in the order of a walk through all of them");
		this.assertEquals(RLChordGraph.layout(graph, { |x, i| [i / 28, 0] })[14], [0.5, 0], "by a rule");
	}

	test_the_page_messages {
		var path = graph.shortestPath(sp.major(0), sp.major(2)), voicings = RLVoiceLeading.through(path, [60, 64, 67]);
		var nodes, edges, c = graph.indexOf(sp.major(0)), cm = graph.indexOf(sp.minor(0)), ats;
		RLChordGraph.show(graph, \zones, "Cube Dance", cd);
		nodes = got.select { |m| m[0] == '/chordgraph/node' };
		edges = got.select { |m| m[0] == '/chordgraph/edge' };
		this.assertEquals(got.keep(2), [['/chordgraph/clear'], ['/chordgraph/title', "Cube Dance"]], "a clear, then the title");
		this.assertEquals([nodes.size, edges.size, got.size], [28, 48, 78], "28 vertices and 48 edges");
		this.assertEquals(nodes[c].drop(4), [0, "C"], "C major: type 0, its short name");
		this.assertEquals(nodes[cm].drop(4), [1, "Cm"], "C minor");
		this.assertEquals(nodes[graph.indexOf(sp.chord(0, \aug))].drop(4), [2, "C+"], "C augmented");
		this.assertEquals(edges.detect { |m| (m[1] == min(c, cm)) and: { m[2] == max(c, cm) } }.drop(3), [1, 1, "P"],
			"the edge from C major to C minor: one step, both ways, named P");
		got = Array.new;
		RLChordGraph.walk(graph, path, voicings);
		ats = got.select { |m| m[0] == '/chordgraph/at' };
		this.assertEquals(got[0], ['/chordgraph/reset'], "a walk: first the last one forgotten");
		this.assertEquals(ats.size, 7, "C major to D major in six steps: seven chords");
		this.assertEquals(ats[0], ['/chordgraph/at', c, 0, 6000, 6400, 6700], "the place of the chord, the step, the voices in cents");
		this.assertEquals((ats[6].drop(3).sum - ats[0].drop(3).sum).abs, 600, "six steps of one semitone, all up or all down");
		this.assertEquals(ats[6].keep(3), ['/chordgraph/at', graph.indexOf(sp.major(2)), 6], "the last on D major");
		this.assertException({ RLChordGraph.args(graph, RLChordSpace.triads(RLEDO(19)).major(0)) }, Error, "a chord that is not a vertex is refused");
		this.assertEquals(RLChordGraph.label(RLRelationContext.powerTowers.space.chord(2, \hdim7)), "Dm7b5", "short names of seventh chords");
	}
}
