// reLation — chords as elements of a group: the extensions of the roots by the chord types.
//
// Once a base chord is chosen, the chord whose root is z above the base's and whose type differs by h is the
// element [z, h]. The groups that move the chords simply transitively, contain the transpositions and respect
// the types multiply as
//
//   [z1, h1] [z2, h2] = [z1 + phi(h1)(z2) + zeta(h1, h2), h1 h2]
//
// phi(h) an automorphism of the roots for each type element h, zeta a pitch class for each pair. Three ways in:
//
//   RLExtension(12, p, q)                       Z_n by Z_2, two numbers: phi(1) multiplies by q, zeta(1, 1) = p.
//                                               A group when q q = 1 and p (q - 1) = 0 modulo n. Eight in 12:
//                                               (0, 11) is the dihedral one (left T, I; right P, L, R), (0, 1),
//                                               (1, 1), (0, 5), (0, 7), (2, 7), (3, 5), (6, 11); two in a prime division.
//   RLExtension.cyclic(system, types, p, q)     the roots of any pitch system by any number of types permuted in a
//                                               cycle: phi(h) = q h times, zeta(h, k) = p when h + k passes the
//                                               number of types. q is a multiplier, or on a lattice a matrix; a
//                                               group when q taken `types` times is the identity and q keeps p.
//   RLExtension.general(system, table, phi, zeta)   any finite group of types, by its table.
//
//   ~ext = RLExtension(12, 2, 7);
//   ~ext.orders;                              // [[1, 1], [2, 1], [3, 2], [4, 6], [6, 2], [12, 12]]: Z3 x Q8
//   ~space = RLChordSpace.triads(RLEDO(12));  ~base = ~space.major(0);
//   ~ext.right([3, 1], ~space.minor(7), ~base);     // multiplied on the right: read from the chord
//   ~ext.left([3, 1], ~space.minor(7), ~base);      // on the left
//   RLExtension.classes(12);                  // one (p, q) for each of the eight groups
//
//   ~just = RLJust([3, 5]);                   // on the lattice: thirds and fifths exchanged, p = 15/8
//   ~swap = RLExtension.cyclic(~just, 2, [1, 1], [[0, 1], [1, 0]]);
//   ~swap.isGroup;                            // true
//
// Moving the base chord changes which element each chord is, so the same element then acts differently: the
// base is an argument of every action. With strict: false data that give no group are accepted: the product
// is then not associative and the two actions no longer commute.

RLExtension {
	var <system, <table, phi, zeta, <p, <q, valid;

	// Z_n by Z_2.
	*new { |n = 12, p = 0, q, strict = true|
		^this.cyclic(RLEDO(n), 2, p % n, (q ?? { n - 1 }) % n, strict)
	}

	// The roots of a pitch system by `types` types in a cycle. p: a pitch class (the zero when nil);
	// q: an Integer multiplier, or for a lattice a matrix (an Array of rows); -1 when nil (inversion).
	*cyclic { |system, types = 2, p, q, strict = true|
		var out = super.new.prCyclic(system, types, p ?? { system.pcZero }, q ? -1);
		if(strict and: { out.isGroup.not }) {
			Error("RLExtension: (p, q) = (%, %) gives no group on % with % types (q taken % times must be the identity, and q must keep p); strict: false takes it as it is".format(
				out.p, out.q, system, types, types)).throw
		};
		^out
	}

	// Any finite group of types. table: table[h][k] is the product of the type elements h and k (0 the identity);
	// phi: one automorphism of the roots per type element (multipliers or matrices); zeta: zeta[h][k] a pitch
	// class for each pair, or a Function (h, k) -> pitch class.
	*general { |system, table, phi, zeta, strict = true|
		var out = super.new.prGeneral(system, table, phi, zeta);
		if(strict and: { out.isGroup.not }) {
			Error("RLExtension: these data give no group (the table must be a group's, phi a homomorphism to automorphisms, zeta a cocycle that is zero on the identity); strict: false takes them as they are").throw
		};
		^out
	}

	*isValid { |n, p, q, types = 2|
		var power = 1;
		types.do { power = (power * q) % n };
		^(power == (1 % n)) and: { ((p * (q - 1)) % n) == 0 }
	}

	// Z_n by `types` types in a cycle: every valid (p, q); one for each group, told apart by the orders of their
	// elements (enough for two types in 12 steps).
	*valid { |n, types = 2|
		var out = Array.new;
		n.do { |p| n.do { |q| if(this.isValid(n, p, q, types)) { out = out.add([p, q]) } } };
		^out
	}
	*classes { |n, types = 2|
		var out = Array.new, seen = Array.new;
		this.valid(n, types).do { |pq|
			var orders = this.cyclic(RLEDO(n), types, pq[0], pq[1]).orders;
			if(seen.includesEqual(orders).not) { seen = seen.add(orders); out = out.add(pq) };
		};
		^out
	}

	prCyclic { |sys, types, pp, qq|
		var auto, power;
		system = sys;
		p = pp;
		q = qq;
		table = Array.fill(types) { |h| Array.fill(types) { |k| (h + k) % types } };
		auto = this.prAuto(qq);
		power = this.prIdentityAuto;
		phi = Array.fill(types) { |h| var now = power; power = this.prCompose(auto, power); now };
		zeta = Array.fill(types) { |h| Array.fill(types) { |k| if((h + k) >= types) { pp } { system.pcZero } } };
	}

	prGeneral { |sys, tab, autos, zet|
		system = sys;
		table = tab;
		phi = autos.collect { |a| this.prAuto(a) };
		zeta = Array.fill(tab.size) { |h| Array.fill(tab.size) { |k| if(zet.isKindOf(Function)) { zet.value(h, k) } { zet[h][k] } } };
	}

	// An automorphism of the roots is kept as the images of the generators: for an equal division the image of
	// one step, for a lattice the image of each unit vector.
	prRank { ^if(system.isFinite) { nil } { system.pcZero.size } }
	prAuto { |x|
		var rank = this.prRank;
		^if(rank.isNil) { x % system.steps } {
			if(x.isKindOf(SequenceableCollection)) { x.flop } { Array.fill(rank) { |i| Array.fill(rank) { |j| if(i == j) { x } { 0 } } } }
		}
	}
	prIdentityAuto { ^this.prAuto(1) }
	prApply { |auto, pc|
		var out;
		^if(this.prRank.isNil) { (auto * pc) % system.steps } {
			out = system.pcZero;
			pc.do { |e, i| out = out + (auto[i] * e) };
			out
		}
	}
	prCompose { |a, b|        // a after b
		^if(this.prRank.isNil) { (a * b) % system.steps } { b.collect { |image| this.prApply(a, image) } }
	}

	types { ^table.size }
	n { ^if(system.isFinite) { system.steps } }

	// Whether the data give a group: the table is a group's, phi sends products to composites and the identity
	// to the identity, zeta is zero on the identity and a cocycle.
	isGroup {
		^valid ?? {
			var m = table.size, zero = system.pcZero, ok = true, hs = Array.series(m);
			ok = table[0] == hs and: { table.every { |row, h| (row[0] == h) and: { row.asSet.size == m } } };
			if(ok) { hs.do { |h| hs.do { |k| hs.do { |l| if(table[table[h][k]][l] != table[h][table[k][l]]) { ok = false } } } } };
			if(ok) { ok = phi[0] == this.prIdentityAuto };
			if(ok) { hs.do { |h| hs.do { |k| if(this.prCompose(phi[h], phi[k]) != phi[table[h][k]]) { ok = false } } } };
			if(ok) { hs.do { |h| if((zeta[0][h] != zero) or: { zeta[h][0] != zero }) { ok = false } } };
			if(ok) {
				hs.do { |h| hs.do { |k| hs.do { |l|
					if(system.pcAdd(this.prApply(phi[h], zeta[k][l]), zeta[h][table[k][l]]) != system.pcAdd(zeta[h][k], zeta[table[h][k]][l])) { ok = false };
				} } };
			};
			valid = ok;
			ok
		}
	}

	identity { ^[system.pcZero, 0] }

	// The elements of a finite system: every root with every type.
	elements {
		var pcs;
		if(system.isFinite.not) { Error("RLExtension: % has no list of all its elements".format(system)).throw };
		pcs = system.pcs;
		^Array.fill(table.size * pcs.size) { |i| [pcs[i % pcs.size], i.div(pcs.size)] }
	}

	mul { |g, h|
		^[system.pcAdd(system.pcAdd(g[0], this.prApply(phi[g[1]], h[0])), zeta[g[1]][h[1]]), table[g[1]][h[1]]]
	}

	// The element that undoes g on both sides, or nil (a structure that is not a group may have none).
	inv { |g|
		var hi, id = this.identity;
		^if(this.isGroup) {
			hi = table[g[1]].indexOf(0);
			[this.prApply(phi[hi], system.pcNeg(system.pcAdd(g[0], zeta[g[1]][hi]))), hi]
		} {
			if(system.isFinite) { this.elements.detect { |h| (this.mul(g, h) == id) and: { this.mul(h, g) == id } } }
		}
	}

	// g taken k times, each new factor on the right; how many times before the identity (nil when never).
	pow { |g, k|
		var out = this.identity;
		k.do { out = this.mul(out, g) };
		^out
	}
	order { |g|
		var out = g, k = 1, id = this.identity, most = if(system.isFinite) { 2 * table.size * system.size } { table.size };
		while { (out != id) and: { k <= most } } { out = this.mul(out, g); k = k + 1 };
		^if(out == id) { k }
	}

	// How many elements have each order, as [order, count] pairs (order 0: the identity is never reached).
	orders {
		var d = Dictionary.new;
		this.elements.do { |g| var o = this.order(g) ? 0; d[o] = (d[o] ? 0) + 1 };
		^d.asSortedArray
	}

	// Over every triple of a finite system: how many fail (a b) c = a (b c); whether none does.
	failures {
		var els = this.elements, count = 0;
		els.do { |a| els.do { |b| els.do { |c|
			if(this.mul(this.mul(a, b), c) != this.mul(a, this.mul(b, c))) { count = count + 1 };
		} } };
		^count
	}
	isAssociative {
		var els = this.elements;
		els.do { |a| els.do { |b| els.do { |c|
			if(this.mul(this.mul(a, b), c) != this.mul(a, this.mul(b, c))) { ^false };
		} } };
		^true
	}

	// A chord as an element, given the base chord; and back. Chords of a space with as many types as the
	// extension, on its pitch system.
	element { |chord, base|
		^[system.pcSub(chord.root, base.root), table[table[base.typeIndex].indexOf(0)][chord.typeIndex]]
	}
	chordOf { |g, base| ^base.space.chord(system.pcAdd(base.root, g[0]), base.space.typeAt(table[base.typeIndex][g[1]])) }

	// A chord multiplied by g on the left, on the right.
	left { |g, chord, base| ^this.chordOf(this.mul(g, this.element(chord, base)), base) }
	right { |g, chord, base| ^this.chordOf(this.mul(this.element(chord, base), g), base) }

	prData { ^[table, phi, zeta] }
	== { |that| ^that.isKindOf(RLExtension) and: { system == that.system } and: { this.prData == that.prData } }
	!= { |that| ^(this == that).not }
	hash { ^[\RLExtension, system.hash, this.prData].hash }

	printOn { |stream|
		if(p.notNil and: { system.isKindOf(RLEDO) } and: { table.size == 2 }) {
			stream << "RLExtension(" << system.steps << ", p: " << p << ", q: " << q << ")"
		} {
			stream << "RLExtension(" << system << ", " << table.size << " types";
			if(p.notNil) { stream << ", p: " << p << ", q: " << q };
			stream << ")";
		}
	}
}
