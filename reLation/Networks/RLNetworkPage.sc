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
				RCVisuals.send('/network/node', f, i, where[i][0], where[i][1], RLChordGraph.label(net.at(node)));
			};
			net.arrows.do { |a, k|
				RCVisuals.send('/network/arrow', f, net.nodes.indexOf(a[0]), net.nodes.indexOf(a[1]), names[k].asString);
			};
		};
	}

	// The frame that plays; nil for none.
	*at { |frame| ^RCVisuals.send('/network/at', frame ? -1) }
}
