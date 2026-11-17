// reLation — chords as elements of a group: the extensions of the roots Z_n by the two types Z_2.
//
// Once a base chord is chosen, the chord whose root is z steps above the base's and whose type differs by h is
// the element [z, h]. The groups that move the 2n chords simply transitively, contain the transpositions and
// respect the types are given by two numbers p and q:
//
//   [z1, 0] [z2, h] = [z1 + z2, h]           [z1, 1] [z2, h] = [z1 + q z2 + (p when h is 1), 1 + h]
//
// with q q = 1 and p (q - 1) = 0 modulo n. In 12 steps there are eight such groups: (p, q) = (0, 11) is the
// dihedral one, whose left action is T and I and whose right action is P, L, R (RLChordContext); (0, 1) the
// direct product, (1, 1) the cyclic group of 24, and (0, 5), (0, 7), (2, 7), (3, 5), (6, 11). A prime division
// has two, the cyclic and the dihedral.
//
//   ~ext = RLExtension(12, 2, 7);
//   ~ext.orders;                              // [[1, 1], [2, 1], [3, 2], [4, 6], [6, 2], [12, 12]]: Z3 x Q8
//   ~space = RLChordSpace.triads(RLEDO(12));  ~base = ~space.major(0);
//   ~ext.right([3, 1], ~space.minor(7), ~base);     // multiplied on the right: read from the chord
//   ~ext.left([3, 1], ~space.minor(7), ~base);      // on the left
//   RLExtension.classes(12);                  // one (p, q) for each of the eight groups
//
// Moving the base chord changes which element each chord is, so the same element then acts differently: the
// base is an argument of every action. With strict: false any (p, q) is accepted: the product is then not
// associative and the two actions no longer commute (the ChucK piece of 2019 ran such structures).

RLExtension {
	var <n, <p, <q;

	*new { |n = 12, p = 0, q, strict = true|
		var qq = (q ?? { n - 1 }) % n, pp = p % n;
		if(strict and: { this.isValid(n, pp, qq).not }) {
			Error("RLExtension: (p, q) = (%, %) is not a group in % steps (q q = 1 and p (q - 1) = 0 are needed); strict: false takes it as it is".format(pp, qq, n)).throw
		};
		^super.newCopyArgs(n, pp, qq)
	}

	*isValid { |n, p, q| ^(((q * q) % n) == (1 % n)) and: { ((p * (q - 1)) % n) == 0 } }

	// Every valid (p, q); one for each group, told apart by the orders of their elements (enough in 12 steps).
	*valid { |n|
		var out = Array.new;
		n.do { |p| n.do { |q| if(this.isValid(n, p, q)) { out = out.add([p, q]) } } };
		^out
	}
	*classes { |n|
		var out = Array.new, seen = Array.new;
		this.valid(n).do { |pq|
			var orders = this.new(n, pq[0], pq[1]).orders;
			if(seen.includesEqual(orders).not) { seen = seen.add(orders); out = out.add(pq) };
		};
		^out
	}

	isGroup { ^RLExtension.isValid(n, p, q) }
	identity { ^[0, 0] }
	elements { ^Array.fill(2 * n) { |i| [i % n, i.div(n)] } }

	mul { |g, h|
		^[(g[0] + (if(g[1] == 0) { h[0] } { q * h[0] }) + (if((g[1] == 1) and: { h[1] == 1 }) { p } { 0 })) % n, (g[1] + h[1]) % 2]
	}

	// The element that undoes g on both sides, or nil (a structure that is not a group may have none).
	inv { |g| ^this.elements.detect { |h| (this.mul(g, h) == [0, 0]) and: { this.mul(h, g) == [0, 0] } } }

	// g taken k times, each new factor on the right; how many times before the identity (nil when never).
	pow { |g, k|
		var out = [0, 0];
		k.do { out = this.mul(out, g) };
		^out
	}
	order { |g|
		var out = g, k = 1;
		while { (out != [0, 0]) and: { k <= (4 * n) } } { out = this.mul(out, g); k = k + 1 };
		^if(out == [0, 0]) { k }
	}

	// How many elements have each order, as [order, count] pairs (order 0: the identity is never reached).
	orders {
		var d = Dictionary.new;
		this.elements.do { |g| var o = this.order(g) ? 0; d[o] = (d[o] ? 0) + 1 };
		^d.asSortedArray
	}

	// Over every triple: whether (a b) c is always a (b c), and how many triples fail.
	failures {
		var els = this.elements, count = 0;
		els.do { |a| els.do { |b| els.do { |c|
			if(this.mul(this.mul(a, b), c) != this.mul(a, this.mul(b, c))) { count = count + 1 };
		} } };
		^count
	}
	isAssociative { ^this.failures == 0 }

	// A chord as an element, given the base chord; and back. Chords of a two-type space of an RLEDO of n steps.
	element { |chord, base| ^[(chord.root - base.root) % n, (chord.typeIndex - base.typeIndex) % 2] }
	chordOf { |g, base| ^base.space.chord(base.root + g[0], base.space.typeAt((base.typeIndex + g[1]) % 2)) }

	// A chord multiplied by g on the left, on the right.
	left { |g, chord, base| ^this.chordOf(this.mul(g, this.element(chord, base)), base) }
	right { |g, chord, base| ^this.chordOf(this.mul(this.element(chord, base), g), base) }

	== { |that| ^that.isKindOf(RLExtension) and: { n == that.n } and: { p == that.p } and: { q == that.q } }
	!= { |that| ^(this == that).not }
	hash { ^[\RLExtension, n, p, q].hash }

	printOn { |stream| stream << "RLExtension(" << n << ", p: " << p << ", q: " << q << ")" }
}
