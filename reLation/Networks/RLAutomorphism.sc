// reLation — an automorphism of a relation context: a way of moving every object that the relations go
// through. What Popoff calls a complete isography.
//
// It is a one-to-one map nu of the context's objects, with the change it makes to the relations: a relation m
// becomes N(m) = nu m nu⁻¹, "move back, do m, move forth", so that nu(m(x)) = N(m)(nu(x)) for every object.
// It is valid when every N(m) is again one of the relations the context generates. Applied to a network it
// moves the contents by nu and the labels by N: the network keeps its shape and its sense.
//
//   ~cd = RLRelationContext.cubeDance;
//   ~t5 = RLAutomorphism.transposition(~cd, 5);        // every chord a fourth up
//   ~t5.isValid;                                       // true
//   ~t5.onArrow(~cd.transform(\U)) == ~cd.transform(\U);    // true: transposing changes no relation
//   ~t5.order;                                         // 12
//
//   ~pcs = RLRelationContext.pitchClasses;             // T and I on the twelve pitch classes
//   ~m = RLAutomorphism.affine(~pcs, 5, 2);            // x -> 5x + 2
//   ~pcs.nameOf(~m.onArrow(~pcs.transform(\I3)));      // I7: I(n) becomes I(5n + 4)
//
// An automorphism is a value: equal to another that moves every object alike.

RLAutomorphism {
	var <context, <images, <>label, indexOf;

	// rule: a Function object -> object, or an Array of [object, image] pairs (objects left out stay in place).
	*new { |context, rule, label| ^super.new.prInit(context, rule, label) }

	prInit { |ctx, rule, lab|
		var objs = ctx.objects, pairs;
		context = ctx;
		label = lab;
		indexOf = Dictionary.new(objs.size * 2);
		objs.do { |x, i| indexOf[x] = i };
		images = if(rule.isKindOf(Function)) { objs.collect { |x| rule.value(x) } } {
			pairs = Dictionary.new;
			rule.do { |p| pairs[p[0]] = p[1] };
			objs.collect { |x| pairs[x] ? x }
		};
		images.do { |y| if(indexOf[y].isNil) { Error("RLAutomorphism: % is not one of the context's objects".format(y)).throw } };
		if(images.asSet.size != objs.size) { Error("RLAutomorphism: the map is not one to one").throw };
	}

	// Chords moved by their roots: every root up by v; every pitch class x turned into k - x. For contexts of
	// chords of an RLEDO (the chord of the same kind on the new tones is found again).
	*transposition { |context, v|
		var system = context.system, space = context.space;
		^this.new(context, { |c| space.canonical(space.chord(system.pcAdd(c.root, v), c.type)) }, "T" ++ v)
	}
	*inversion { |context, k|
		var system = context.system, space = context.space;
		^this.new(context, { |c|
			space.canonical(space.find(c.pcs.collect { |x| system.pcSub(k, x) }))
		}, "I" ++ k)
	}

	// On pitch classes: x -> kx + c.
	*affine { |context, k, c|
		var system = context.system;
		^this.new(context, { |x| system.pcAdd(system.pcScale(x, k), c) }, "%x+%".format(k, c))
	}

	// From a few objects and their images, the rest filled by what the invertible relations force:
	// nu(m(x)) = N(m)(nu(x)) for every relation m that can be undone. seeds: [[x, image], ...];
	// arrowMap: one of context.category.automorphisms (the identity when nil). Objects not reached stay in place.
	*extend { |context, seeds, arrowMap, label|
		var cat = context.category, pairs = Array.new;
		seeds.do { |seed|
			cat.invertibles.do { |m|
				var nm = if(arrowMap.isNil) { m } { cat.imageOf(arrowMap, m) };
				pairs = pairs.add([m.value(seed[0]), nm.value(seed[1])]);
			};
		};
		^this.new(context, pairs, label)
	}

	// Every automorphism of a context: for each automorphism N of its monoid (or for those given, as
	// RLCategory:automorphisms lists them), the one-to-one maps of the objects that turn each generator g into
	// N(g). all gives them, count their number; nil past `max`. Cube Dance has 7776, 648 for each of the 12
	// automorphisms of its monoid.
	*all { |context, max = 20000, arrowMaps|
		var lists = this.prSearch(context, max, arrowMaps, false), objs = context.objects;
		^lists !? { lists.collect { |img| this.new(context, objs.collect { |x, i| [x, objs[img[i]]] }) } }
	}
	*count { |context, max = 1000000, arrowMaps| ^this.prSearch(context, max, arrowMaps, true) }

	// The images are tried object by object, in an order where each new object is next to one already placed,
	// and a choice is kept only while every pair of objects already placed agrees with it.
	*prSearch { |context, max, arrowMaps, countOnly|
		var cat = context.category, objs = context.objects, n = objs.size, names = cat.names, zero = context.quantale.zero;
		var gens = names.collect { |g| cat.generator(g).rows }, order = [0], out = Array.new, count = 0, head = 0;
		while { order.size < n } {
			if(head < order.size) {
				n.do { |y|
					if(order.includes(y).not and: { gens.any { |m| (m[order[head]][y] != zero) or: { m[y][order[head]] != zero } } }) { order = order.add(y) };
				};
				head = head + 1;
			} { order = order.add(Array.series(n).detect { |y| order.includes(y).not }) };
		};
		(arrowMaps ?? { cat.automorphisms }).do { |auto|
			var targets = names.collect { |g| cat.imageOf(auto, cat.generator(g)).rows };
			var img = Array.newClear(n), used = Array.fill(n, false), try;
			try = { |k|
				if(k == n) {
					count = count + 1;
					if(countOnly.not) { out = out.add(img.copy) };
					if(count > max) { ^nil };
				} {
					var x = order[k];
					n.do { |y|
						if(used[y].not) {
							var ok = true;
							gens.do { |m, g|
								var t = targets[g];
								if(ok and: { m[x][x] != t[y][y] }) { ok = false };
								if(ok) {
									k.do { |kk|
										var z = order[kk], w = img[z];
										if(ok and: { (m[x][z] != t[y][w]) or: { m[z][x] != t[w][y] } }) { ok = false };
									};
								};
							};
							if(ok) {
								img[x] = y;
								used[y] = true;
								try.value(k + 1);
								used[y] = false;
							};
						};
					};
				};
			};
			try.value(0);
		};
		^if(countOnly) { count } { out }
	}

	value { |x| ^images[indexOf[x] ?? { Error("RLAutomorphism: % is not one of the context's objects".format(x)).throw }] }

	// What a relation becomes: the pairs (nu(x), nu(y)) for the pairs (x, y) of the relation.
	onArrow { |arrow|
		var objs = context.objects, rel = context.arrow(arrow), rows = { nil ! objs.size } ! objs.size, out;
		rel.rows.do { |row, i|
			var ni = indexOf[images[i]];
			row.do { |v, j| rows[ni][indexOf[images[j]]] = v };
		};
		out = RLRelation(objs, objs, rows, rel.quantale);
		^context.category.canonical(out) ? out
	}

	// Whether every generator becomes one of the context's relations.
	isValid {
		var cat = context.category;
		^context.generators.every { |n| cat.includes(this.onArrow(context.transform(n))) }
	}

	// How it moves the arrows of the context's category: arrows[i] becomes arrows[result[i]]. Comparable with
	// the Arrays of RLCategory:automorphisms. nil when it is not valid.
	arrowMap {
		var cat = context.category, gens = cat.names.collect { |n| cat.indexOf(this.onArrow(cat.generator(n))) };
		^if(gens.includes(nil)) { nil } { cat.extendMap(gens) }
	}

	// This after that; undone; n times; how many times before everything is back.
	<> { |that|
		^RLAutomorphism(context, context.objects.collect { |x| [x, this.value(that.value(x))] },
			if(label.notNil and: { that.label.notNil }) { label ++ " " ++ that.label })
	}
	inverse { ^RLAutomorphism(context, context.objects.collect { |x| [this.value(x), x] }, label !? { "(" ++ label ++ ")⁻¹" }) }
	repeated { |n|
		var out = RLAutomorphism(context, []);
		n.do { out = this <> out };
		^out
	}
	isIdentity { ^images == context.objects }
	order {
		var out = this, n = 1;
		while { out.isIdentity.not } { out = this <> out; n = n + 1 };
		^n
	}

	== { |that| ^that.isKindOf(RLAutomorphism) and: { images == that.images } and: { context.objects == that.context.objects } }
	!= { |that| ^(this == that).not }
	hash { ^[\RLAutomorphism, images].hash }

	printOn { |stream| stream << "RLAutomorphism(" << (label ? (context.objects.size.asString ++ " objects")) << ")" }
}
