// reLation — networks shown on HomewareVisuals' network page (/network/): side by side, each a frame of nodes
// holding an object and arrows with their labels.
//
// A network moved again and again by an automorphism is a row of frames: the same arrows, other chords in the
// nodes; where the automorphism changes the labels, one sees that too. The page only draws. Messages go through
// RCVisuals, as every page's do.
//
//   ~cd = RLRelationContext.cubeDance;  ~sp = ~cd.space;
//   ~cell = RLNetwork.chain(~cd, [~sp.major(2), ~sp.chord(2, \aug), ~sp.minor(7)], [\U, \U]);
//   ~cells = ~cell.iterate(RLAutomorphism.transposition(~cd, 5), 11);          // "Take a Bow": a fourth up, 12 cells
//   RLNetworkPage.show(~cells, "Take a Bow", captions: ~cells.collect { |c, k| "cell " ++ (k + 1) });
//   RLNetworkPage.at(3);                                                        // the frame that plays

RLNetworkPage {

	// The places of n nodes: round a circle, the first at the top (three make a triangle).
	*places { |n| ^RLChordGraph.circle(n) * 0.8 }

	// What a node shows: a chord by its short name, a pitch class by its note's name, a network (in a network
	// of networks) by what its own nodes show, anything else as it prints.
	*text { |network, x|
		var system = network.context.system;
		^case
		{ x.isKindOf(RLChord) } { RLChordGraph.label(x) }
		{ x.isKindOf(RLNetwork) } { x.nodes.collect { |n| this.text(x, x.at(n)) }.join(" ") }
		{ system.notNil and: { x.isNumber } } { system.name(x) }
		{ x.asString }
	}

	// One network or a list of them, each in its frame: every node (its place, what it holds), every arrow
	// (its two nodes, the name of its label). places: one [x, y] for each node, between -1 and 1, y downwards,
	// the same in every frame; captions: one text for each frame.
	*show { |networks, title, places, captions|
		var list = if(networks.isKindOf(RLNetwork)) { [networks] } { networks };
		RCVisuals.send('/network/clear');
		title !? { RCVisuals.send('/network/title', title.asString) };
		list.do { |net, f|
			var where = places ?? { this.places(net.nodes.size) }, names = net.labelNames;
			captions !? { RCVisuals.send('/network/caption', f, captions[f].asString) };
			net.nodes.do { |node, i|
				RCVisuals.send('/network/node', f, i, where[i][0], where[i][1], this.text(net, net.at(node)));
			};
			net.arrows.do { |a, k|
				RCVisuals.send('/network/arrow', f, net.nodes.indexOf(a[0]), net.nodes.indexOf(a[1]), names[k].asString);
			};
		};
	}

	// A network of networks (a network on RLRelationContext.networks): the networks its nodes hold, each in its
	// frame under its node's name and the moves that reach it, then the plan in a last frame, each of its nodes
	// showing the notes of its network.
	*nested { |network, title, places|
		var names = network.labelNames;
		var captions = network.nodes.collect { |n|
			var into = network.arrows.collect { |a, k| if(a[1] == n) { "% of %".format(names[k], a[0]) } }.reject(_.isNil);
			if(into.isEmpty) { n.asString } { "%: %".format(n, into.join(", ")) }
		};
		^this.show(network.nodes.collect { |n| network.at(n) } ++ [network], title, places, captions ++ ["the network of these networks"])
	}

	// The frame that plays; nil for none.
	*at { |frame| ^RCVisuals.send('/network/at', frame ? -1) }
}
