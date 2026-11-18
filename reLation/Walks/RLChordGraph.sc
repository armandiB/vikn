// reLation — a graph of chords and a walk on it, shown on HomewareVisuals' chord graph page (/chordgraph/).
//
// The page only draws: the vertices, their places, the edges and their costs are all sent from here, so that
// any graph the classes compute can be looked at (Cube Dance and its analogues, the triads under P, L, R,
// Power Towers ...) and no rule is written twice. Messages go through RCVisuals, as every page's do.
//
//   ~cd = RLRelationContext.cubeDance;
//   ~g = RLGraph.voiceLeading(~cd.union);
//   RLChordGraph.show(~g, \zones, "Cube Dance", ~cd);        // the graph, by voice-leading zones
//   ~path = ~g.shortestPath(~cd.space.major(0), ~cd.space.major(2));
//   RLChordGraph.walk(~g, ~path, RLVoiceLeading.through(~path, [60, 64, 67]));     // a walk, with its voices
//
//   // in a beat: each chord reaches the page when its sound starts (put the key after freq)
//   ~walker = RLWalker(~cd, ~cd.space.major(0), [60, 64, 67]);
//   ~beat = ~song.layer(\core).addBeat(\harmony, [instrument: \default, dur_flex: 2,
//       freq: ~walker.asPattern(Pseq([\P, \L, \U, \U], inf)), graph: RLChordGraph.pfunc(~g, ~walker)]);
//
// Layouts: \zones puts a chord at the angle of the sum of its pitch classes (its voice-leading zone: a voice
// moving by one step moves the chord to the next zone), the chords of one zone on one ray; \circle the vertices
// in their order; \cycle in the order of a walk through all of them; or a Function (object, index) -> [x, y],
// or the places themselves (x and y between -1 and 1, y downwards).

RLChordGraph {
	classvar suffixes;

	*initClass {
		suffixes = IdentityDictionary[\major -> "", \minor -> "m", \aug -> "+", \dom7 -> "7", \min7 -> "m7",
			\hdim7 -> "m7b5", \dim7 -> "dim7", \otonal -> "7", \utonal -> "m7b5"];
	}

	// The short name of a vertex: for a chord its root and a suffix (C, Cm, C+, C7, Cm7, Cm7b5, Cdim7).
	*label { |x|
		^if(x.isKindOf(RLChord)) {
			x.system.name(x.root) ++ (suffixes[x.type] ?? { " " ++ x.type })
		} { x.asString }
	}

	*circle { |n| ^Array.fill(n) { |i| var a = 2pi * i / n; [a.sin, a.cos.neg].round(1e-4) } }

	// Voice-leading zones: the angle is the sum of a chord's pitch classes round the division, the chords of a
	// zone spread along its ray in the order of the vertices.
	*zones { |graph|
		var objects = graph.objects, n, sums, counts = Dictionary.new, seen = Dictionary.new;
		if(objects.every { |x| x.isKindOf(RLChord) and: { x.system.isKindOf(RLEDO) } }.not) { ^this.circle(objects.size) };
		n = objects.first.system.steps;
		sums = objects.collect { |x| x.pcs.sum % n };
		sums.do { |s| counts[s] = (counts[s] ? 0) + 1 };
		^objects.collect { |x, i|
			var s = sums[i], k = seen[s] ? 0, a = 2pi * s / n;
			var r = if(counts[s] == 1) { 0.78 } { 0.56 + (0.44 * k / (counts[s] - 1)) };
			seen[s] = k + 1;
			[r * a.sin, r * a.cos.neg].round(1e-4)
		}
	}

	*layout { |graph, how = \zones|
		var cycle;
		^switch(how,
			\zones, { this.zones(graph) },
			\circle, { this.circle(graph.size) },
			\cycle, {
				cycle = graph.hamiltonianCycle;
				if(cycle.isNil) { this.circle(graph.size) } {
					var ring = this.circle(graph.size), out = Array.newClear(graph.size);
					cycle.keep(graph.size).do { |x, k| out[graph.indexOf(x)] = ring[k] };
					out
				}
			},
			{ if(how.isKindOf(Function)) { graph.objects.collect { |x, i| how.value(x, i) } } { how } }
		)
	}

	// Send a graph: a clear, a title, each vertex (its place, the number of its type, its short name), each
	// edge (its ends, its cost, whether it goes both ways, the names of the context's relations that hold
	// there). Returns the places.
	*show { |graph, layout = \zones, title, context|
		var places = this.layout(graph, layout);
		RCVisuals.send('/chordgraph/clear');
		title !? { RCVisuals.send('/chordgraph/title', title.asString) };
		graph.objects.do { |x, i|
			RCVisuals.send('/chordgraph/node', i, places[i][0], places[i][1], if(x.isKindOf(RLChord)) { x.typeIndex } { 0 }, this.label(x));
		};
		graph.edges.do { |e|
			var name = if(context.isNil) { "" } { context.labels(graph.objects[e[0]], graph.objects[e[1]]).join("") };
			RCVisuals.send('/chordgraph/edge', e[0], e[1], e[2], e[3].binaryValue, name);
		};
		^places
	}

	// The arguments of /chordgraph/at: the place of the chord among the vertices, the number of the step, then
	// the heights of the voices in cents above the system's origin (none when no voicing is given).
	*args { |graph, chord, k = 0, voicing|
		var i = graph.indexOf(chord), system;
		if(i.isNil) { Error("RLChordGraph: % is not a vertex of this graph".format(chord)).throw };
		system = if(chord.isKindOf(RLChord)) { chord.system };
		^[i, k] ++ if(voicing.isNil or: { system.isNil }) { [] } { voicing.collect { |p| (system.height(p) * 1200).round(0.01) } }
	}

	*at { |graph, chord, k = 0, voicing| ^RCVisuals.sendArgs('/chordgraph/at', this.args(graph, chord, k, voicing)) }

	// A whole walk at once: the walk forgotten, then every chord (with its voices when voicings are given).
	*walk { |graph, chords, voicings|
		RCVisuals.send('/chordgraph/reset');
		chords.do { |c, k| this.at(graph, c, k, voicings !? { voicings[k] }) };
	}

	// For a beat or a Pbind: a key whose value sends the walker's chord and voices when each event's sound starts.
	*pfunc { |graph, walker|
		^RCVisuals.notePfunc('/chordgraph/at', { |ev| this.args(graph, walker.chord, walker.count, walker.voicing) })
	}
}
