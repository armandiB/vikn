// reLation — a network: named nodes holding one musical object each, and arrows between them labelled with
// the context's transformations or relations.
//
// A network says how its objects stand to one another. It is valid when it is a picture of the context:
//   - each arrow's label takes the object at its tail to the object at its head (for a relation: relates them);
//   - the labels compose as the arrows do: two routes between the same nodes have the same composite label.
// On C, E, G the labels T4, I11, T7 pass the first test and fail the second (I11 after T4 is I7): the network
// asserts something false about how T and I compose.
//
//   ~pcs = RLRelationContext.pitchClasses;
//   ~net = RLNetwork(~pcs, [[\x, 9], [\y, 11], [\z, 10]], [[\x, \y, \I8], [\y, \z, \I9], [\x, \z, \T1]]);   // Webern: A, B, Bb
//   ~net.isValid;                              // true
//   ~net.fillings(\x).size;                    // 12: the networks with these labels
//   ~net.transformedBy(RLAutomorphism.affine(~pcs, 1, 4)).objects;     // [1, 3, 2], labels I4, I5, T1
//
//   ~cd = RLRelationContext.cubeDance;         // a chord progression is a network on a chain
//   ~cell = RLNetwork.chain(~cd, [~cd.space.major(2), ~cd.space.chord(2, \aug), ~cd.space.minor(7)], [\U, \U]);
//   ~cell.iterate(RLAutomorphism.transposition(~cd, 5), 2).collect(_.objects);     // "Take a Bow"
//
// A label is given as an arrow, a name of the context, or a word (an Array of names in the order of time).
// A network is a value.

RLNetwork {
	var <context, <nodes, contents, <arrows;

	// contents: [[node, object], ...]; arrows: [[from, to, label], ...]
	*new { |context, contents, arrows| ^super.new.prInit(context, contents, arrows) }

	// A progression: the objects in order, a label between each and the next (nodes named 0, 1, 2 ...).
	*chain { |context, objects, labels|
		^this.new(context, objects.collect { |x, i| [i, x] }, labels.collect { |l, i| [i, i + 1, l] })
	}

	prInit { |ctx, cont, arrs|
		context = ctx;
		nodes = cont.collect(_[0]);
		contents = Dictionary.new;
		cont.do { |pair| contents[pair[0]] = pair[1] };
		arrows = arrs.collect { |a|
			if(contents.includesKey(a[0]).not or: { contents.includesKey(a[1]).not }) {
				Error("RLNetwork: an arrow from % to %, which are not both nodes".format(a[0], a[1])).throw
			};
			[a[0], a[1], this.prArrow(a[2])]
		};
	}

	// labels resolved by the context: a relation context knows names and words, a chord context its transformations
	prArrow { |label|
		^if(context.respondsTo(\arrow)) { context.arrow(label) } {
			if(label.isKindOf(Symbol)) { context.transform(label) } { label }
		}
	}
	prName { |arrow|
		^if(context.respondsTo(\nameOf)) { context.nameOf(arrow) ? arrow.label ? arrow.asString } { arrow.label ? arrow.asString }
	}

	at { |node| ^contents[node] }
	objects { ^nodes.collect { |n| contents[n] } }
	labels { ^arrows.collect(_[2]) }
	labelNames { ^arrows.collect { |a| this.prName(a[2]) } }
	label { |from, to|
		var found = arrows.detect { |a| (a[0] == from) and: { a[1] == to } };
		^found !? { found[2] }
	}

	// The composite label of a route, a list of nodes each joined to the next by an arrow.
	composite { |route|
		var out;
		route.doAdjacentPairs { |a, b|
			var l = this.label(a, b) ?? { Error("RLNetwork: no arrow from % to %".format(a, b)).throw };
			out = if(out.isNil) { l } { l <> out };
		};
		^out
	}

	// every route without a repeated node, from each node, as [from, to, composite label]
	prRoutes {
		var out = Array.new, walk;
		walk = { |start, node, seen, acc|
			arrows.do { |a|
				if((a[0] == node) and: { seen.includes(a[1]).not }) {
					var next = if(acc.isNil) { a[2] } { a[2] <> acc };
					out = out.add([start, a[1], next]);
					walk.value(start, a[1], seen ++ [a[1]], next);
				};
			};
		};
		nodes.do { |n| walk.value(n, n, [n], nil) };
		^out
	}

	// What is wrong with the network, in words: an empty list when it is valid.
	problems {
		var out = Array.new, routes = this.prRoutes;
		arrows.do { |a|
			if(a[2].relates(contents[a[0]], contents[a[1]]).not) {
				out = out.add("% does not take % (at %) to % (at %)".format(this.prName(a[2]), contents[a[0]], a[0], contents[a[1]], a[1]));
			};
		};
		routes.do { |r, i|
			routes.do { |s, j|
				if((j > i) and: { r[0] == s[0] } and: { r[1] == s[1] } and: { r[2] != s[2] }) {
					out = out.add("two routes from % to % compose to % and to %".format(r[0], r[1], this.prName(r[2]), this.prName(s[2])));
				};
			};
		};
		^out
	}
	isValid { ^this.problems.isEmpty }

	// The network moved by an automorphism of the context: contents by its map, labels by what it makes of them.
	transformedBy { |automorphism|
		^RLNetwork(context, nodes.collect { |n| [n, automorphism.value(contents[n])] },
			arrows.collect { |a| [a[0], a[1], automorphism.onArrow(a[2])] })
	}

	// The network and what an automorphism makes of it, again and again: n + 1 networks.
	iterate { |automorphism, n|
		var out = [this];
		n.do { out = out.add(out.last.transformedBy(automorphism)) };
		^out
	}

	// Every valid network with the same shape and labels: each object the context offers is tried at a node,
	// and the others follow along the arrows (a relation may offer several).
	fillings { |node|
		var start = node ? nodes.first, out = Array.new, order = [start], grow = true, fill;
		while { grow } {
			grow = false;
			arrows.do { |a|
				if(order.includes(a[0]) and: { order.includes(a[1]).not }) { order = order.add(a[1]); grow = true };
				if(order.includes(a[1]) and: { order.includes(a[0]).not }) { order = order.add(a[0]); grow = true };
			};
		};
		if(order.size != nodes.size) { Error("RLNetwork: the nodes are not all joined to %".format(start)).throw };
		fill = { |k, chosen|
			var n, cands;
			if(k == order.size) {
				out = out.add(RLNetwork(context, nodes.collect { |name| [name, chosen[name]] }, arrows));
			} {
				n = order[k];
				arrows.do { |a|
					var c;
					if((a[1] == n) and: { chosen.includesKey(a[0]) }) { c = a[2].images(chosen[a[0]]) };
					if((a[0] == n) and: { chosen.includesKey(a[1]) }) { c = a[2].preimages(chosen[a[1]]) };
					if(c.notNil) { cands = if(cands.isNil) { c } { cands.select { |x| c.includesEqual(x) } } };
				};
				cands.do { |x|
					var next = chosen.copy;
					next[n] = x;
					fill.value(k + 1, next);
				};
			};
		};
		context.objects.do { |x| fill.value(1, Dictionary[start -> x]) };
		^out.select(_.isValid)
	}

	// Whether another network of the same shape has the same labels (a strong isography); the pairs
	// [label, its label] between the two (an isography sends the first of each pair to the second).
	hasSameLabels { |that| ^this.prSameShape(that) and: { this.labels == that.labels } }
	labelPairs { |that|
		if(this.prSameShape(that).not) { Error("RLNetwork: two networks of different shapes").throw };
		^arrows.collect { |a, i| [a[2], that.arrows[i][2]] }
	}
	prSameShape { |that| ^(nodes == that.nodes) and: { arrows.collect(_.keep(2)) == that.arrows.collect(_.keep(2)) } }

	// The local isographies to another network of the same shape: one arrow of the context's category at each
	// node, taking this network's object to that one's, such that along every arrow going round either way is
	// the same: nu(head) after label = arrowMap(label) after nu(tail). arrowMap: one of
	// context.category.automorphisms (nil: the labels must be the same). Each answer is [[node, arrow], ...].
	localIsographies { |that, arrowMap|
		var cat = context.category, out = Array.new, cands, try;
		if(this.prSameShape(that).not) { Error("RLNetwork: two networks of different shapes").throw };
		if(arrows.any { |a, i| (if(arrowMap.isNil) { a[2] } { cat.imageOf(arrowMap, a[2]) }) != that.arrows[i][2] }) { ^out };
		cands = nodes.collect { |n| cat.relating(contents[n], that.at(n)) };
		try = { |k, chosen|
			if(k == nodes.size) {
				if(arrows.every { |a, i|
					(chosen[nodes.indexOf(a[1])] <> a[2]) == (that.arrows[i][2] <> chosen[nodes.indexOf(a[0])])
				}) { out = out.add(nodes.collect { |n, i| [n, chosen[i]] }) };
			} {
				cands[k].do { |c| try.value(k + 1, chosen ++ [c]) };
			};
		};
		try.value(0, []);
		^out
	}

	== { |that|
		^that.isKindOf(RLNetwork) and: { nodes == that.nodes } and: { this.objects == that.objects } and: { arrows == that.arrows }
	}
	!= { |that| ^(this == that).not }
	hash { ^[\RLNetwork, nodes, this.objects].hash }

	printOn { |stream|
		stream << "RLNetwork(" << this.objects.collect(_.asString).join(", ") << "; ";
		stream << arrows.collect { |a| "% -%-> %".format(a[0], this.prName(a[2]), a[1]) }.join(", ") << ")";
	}
}
