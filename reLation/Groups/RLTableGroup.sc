// reLation — a finite group given by its table: the roots of an extension when they do not commute.
//
// The elements are the places 0 .. size - 1, 0 the identity; table[a][b] is the product a b. It answers what
// RLExtension asks of the roots (pcZero, pcAdd, pcNeg ...: the "sum" is the product, in that order), so a group
// of transformations can be the roots of a larger group:
//
//   ~ti = RLTableGroup.of(RLRelationContext.pitchClasses(RLEDO(12)).category);     // T and I on 12 notes: 24 elements
//   ~ti.isAbelian;                            // false
//   ~ti.orders;                               // [[1, 1], [2, 13], [3, 2], [4, 2], [6, 2], [12, 4]]
//   RLTableGroup.dihedral(4).automorphisms.size;      // 8
//   RLTableGroup.quaternion.orders;           // [[1, 1], [2, 1], [4, 6]]
//
// An automorphism is the list of the images of the elements, in their order.

RLTableGroup {
	var <table, <names, inverses;

	// table: the products; names: a name for each element, for printing. Refused when it is no group's table
	// with its identity first.
	*new { |table, names|
		^super.new.prInit(table, names)
	}

	*cyclic { |n| ^this.new(Array.fill(n) { |a| Array.fill(n) { |b| (a + b) % n } }, Array.fill(n) { |a| a.asString }) }

	// The dihedral group of 2n elements: r^i is i, s r^i is n + i (s the reflection, s r = r^-1 s).
	*dihedral { |n|
		var mul = { |a, b|
			var fa = a.div(n), ia = a % n, fb = b.div(n), ib = b % n;
			// (s^fa r^ia)(s^fb r^ib) = s^(fa+fb) r^(±ia + ib): moving r^ia past s turns it over
			(((fa + fb) % 2) * n) + ((if(fb == 1) { ia.neg } { ia } + ib) % n)
		};
		^this.new(Array.fill(2 * n) { |a| Array.fill(2 * n) { |b| mul.(a, b) } },
			Array.fill(2 * n) { |a| if(a < n) { "r" ++ a } { "sr" ++ (a - n) } })
	}

	// The quaternion group: 1, -1, i, -i, j, -j, k, -k.
	*quaternion {
		// an element is a sign and one of 1, i, j, k; the products of the units, as [sign, unit]
		var units = [[[1, 0], [1, 1], [1, 2], [1, 3]], [[1, 1], [-1, 0], [1, 3], [-1, 2]], [[1, 2], [-1, 3], [-1, 0], [1, 1]], [[1, 3], [1, 2], [-1, 1], [-1, 0]]];
		var mul = { |a, b|
			var p = units[a.div(2)][b.div(2)], sign = p[0] * if((a % 2) == 1) { -1 } { 1 } * if((b % 2) == 1) { -1 } { 1 };
			(p[1] * 2) + if(sign < 0) { 1 } { 0 }
		};
		^this.new(Array.fill(8) { |a| Array.fill(8) { |b| mul.(a, b) } }, ["1", "-1", "i", "-i", "j", "-j", "k", "-k"])
	}

	// The group an RLCategory is, when it is one (a group of transformations or of relations): its arrows in
	// their order, the identity moved to the front.
	*of { |category|
		var t, id, order, place;
		if(category.isGroup.not) { Error("RLTableGroup: % is not a group".format(category)).throw };
		t = category.table;
		id = category.indexOf(category.identities[0]);
		order = [id] ++ Array.series(t.size).reject { |i| i == id };
		place = Array.newClear(t.size);
		order.do { |i, k| place[i] = k };
		^this.new(order.collect { |i| order.collect { |j| place[t[i][j]] } },
			order.collect { |i| var w = category.wordOf(category.arrows[i]); if(w.isEmpty) { "e" } { w.join("") } })
	}

	prInit { |tab, labels|
		var n = tab.size, all = Array.series(n);
		table = tab;
		names = labels ?? { all.collect(_.asString) };
		if((tab[0] != all) or: { tab.any { |row, a| (row[0] != a) or: { row.asSet.size != n } } }) {
			Error("RLTableGroup: not a group's table with its identity first").throw
		};
		all.do { |a| all.do { |b| all.do { |c|
			if(tab[tab[a][b]][c] != tab[a][tab[b][c]]) { Error("RLTableGroup: the table is not associative").throw };
		} } };
		inverses = all.collect { |a| tab[a].indexOf(0) };
	}

	size { ^table.size }
	isFinite { ^true }
	pcs { ^Array.series(table.size) }

	// what RLExtension asks of the roots
	pcZero { ^0 }
	pcAdd { |a, b| ^table[a][b] }
	pcNeg { |a| ^inverses[a] }
	pcSub { |a, b| ^table[a][inverses[b]] }
	name { |a| ^names[a] }

	mul { |a, b| ^table[a][b] }
	inv { |a| ^inverses[a] }
	isAbelian { ^table.every { |row, a| row.every { |x, b| x == table[b][a] } } }
	order { |a|
		var x = a, k = 1;
		while { x != 0 } { x = table[x][a]; k = k + 1 };
		^k
	}
	orders {
		var d = Dictionary.new;
		table.size.do { |a| var o = this.order(a); d[o] = (d[o] ? 0) + 1 };
		^d.asSortedArray
	}
	center { ^this.pcs.select { |a| this.pcs.every { |b| table[a][b] == table[b][a] } } }

	// The automorphism x -> a x a^-1, and whether a list of images is an automorphism.
	inner { |a| ^Array.fill(table.size) { |x| table[table[a][x]][inverses[a]] } }
	isAutomorphism { |images|
		var n = table.size;
		^(images.size == n) and: { images.asSet.size == n } and: {
			Array.series(n).every { |a| Array.series(n).every { |b| images[table[a][b]] == table[images[a]][images[b]] } }
		}
	}

	// A few elements that generate the group, taken greedily; and what they generate.
	generators {
		var gens = Array.new, reached = [0];
		this.pcs.do { |a|
			if(reached.includes(a).not) { gens = gens.add(a); reached = this.span(gens) };
		};
		^gens
	}
	span { |gens|
		var out = [0], head = 0;
		while { head < out.size } {
			gens.do { |g| var x = table[out[head]][g]; if(out.includes(x).not) { out = out.add(x) } };
			head = head + 1;
		};
		^out
	}

	// Every automorphism: the images of a few generators are tried among the elements of the same order, and
	// each choice that extends to the whole table is kept.
	automorphisms {
		var gens = this.generators, cands = gens.collect { |g| this.pcs.select { |x| this.order(x) == this.order(g) } };
		var out = Array.new, n = table.size;
		if(gens.isEmpty) { ^[[0]] };
		cands.allTuples.do { |choice|
			var images = Array.newClear(n), queue = [0], head = 0, ok = true;
			images[0] = 0;
			// spread the choice over the group: the image of x g is the image of x times the image of g
			while { ok and: { head < queue.size } } {
				var x = queue[head];
				gens.do { |g, i|
					var y = table[x][g], img = table[images[x]][choice[i]];
					if(images[y].isNil) { images[y] = img; queue = queue.add(y) } { if(images[y] != img) { ok = false } };
				};
				head = head + 1;
			};
			if(ok and: { this.isAutomorphism(images) }) { out = out.add(images) };
		};
		^out
	}

	== { |that| ^that.isKindOf(RLTableGroup) and: { table == that.table } }
	!= { |that| ^(this == that).not }
	hash { ^[\RLTableGroup, table].hash }

	printOn { |stream| stream << "RLTableGroup(" << table.size << " elements" << if(this.isAbelian) { "" } { ", not commutative" } << ")" }
}
