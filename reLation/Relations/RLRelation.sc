// reLation — a relation between two finite lists of objects, with values in a quantale.
//
// source and target are Arrays of objects (chords, pitch classes, anything with == and hash); rows[i][j] is
// the value of "source[i] is related to target[j]", the quantale's zero when it is not. A boolean relation
// (the default) holds 0 and 1. Unlike a function, a relation may give an object several images, or none.
//
//   ~space = RLChordSpace.triads(RLEDO(12));
//   ~near = RLRelation.voiceMoves(~space.all, [1]);       // one voice moves by one step
//   ~near.images(~space.major(0));                        // [E minor, C minor]
//   (~near <> ~near).images(~space.major(0));             // two such steps: C major, E major, Ab major
//   ~near.repeated(3).relates(~space.major(0), ~space.minor(8));   // true
//
// Relations compose with <>, right to left: (s <> r) relates x to z when r relates x to some y and s relates
// that y to z, with the value  join over y of  s(y, z) * r(x, y)  in the quantale.
//
// A relation is a value: equal to another with the same lists, quantale and values, whatever its label.

RLRelation {
	var <source, <target, <rows, <quantale, <>label, sIndex, tIndex, hashCache;

	*new { |source, target, rows, quantale, label|
		var q = quantale ? RLQuantale.boolean;
		^super.newCopyArgs(source, target, rows.collect { |r| r.collect { |v| q.coerce(v) } }, q, label).prCheck
	}

	// rows already made of the quantale's values; the index tables of the lists passed on
	*prMake { |source, target, rows, quantale, label, sIndex, tIndex|
		^super.newCopyArgs(source, target, rows, quantale, label, sIndex, tIndex)
	}

	prCheck {
		if((rows.size != source.size) or: { rows.any { |r| r.size != target.size } }) {
			Error("RLRelation: the rows do not fit % sources and % targets".format(source.size, target.size)).throw
		};
	}

	// From a rule: func(x, y) answers a value, true, false or nil.
	*between { |source, target, func, quantale, label|
		^this.new(source, target, source.collect { |x| target.collect { |y| func.value(x, y) } }, quantale, label)
	}
	*on { |objects, func, quantale, label| ^this.between(objects, objects, func, quantale, label) }

	// From a list of [x, y] or [x, y, value].
	*fromPairs { |source, target, pairs, quantale, label|
		var q = quantale ? RLQuantale.boolean, out;
		out = this.prMake(source, target, { q.zero ! target.size } ! source.size, q, label);
		pairs.do { |p|
			var i = out.prS(p[0]), j = out.prT(p[1]);
			out.rows[i][j] = q.join(out.rows[i][j], if(p.size > 2) { q.coerce(p[2]) } { q.unit });
		};
		^out
	}

	*identity { |objects, quantale|
		var q = quantale ? RLQuantale.boolean;
		^this.prMake(objects, objects, objects.collect { |x, i| objects.collect { |y, j| if(i == j) { q.unit } { q.zero } } }, q, "id")
	}

	// A function as a relation: x is related to func(x), which must be one of the objects.
	*ofFunction { |objects, func, quantale, label|
		^this.fromPairs(objects, objects, objects.collect { |x| [x, func.value(x)] }, quantale, label)
	}

	// Chords related by a voice leading whose moves are these numbers of steps, the other voices staying:
	// [1] one voice by one step, [1, 1] two voices by one step each, [1, 2]... In 12 steps Douthett and
	// Steinbach's P(m, n) is m ones and n twos. The chords belong to one RLEDO.
	*voiceMoves { |chords, moves, quantale, label|
		var want = moves.asArray.copy.sort;
		^this.on(chords, { |x, y| RLVoiceLeading.moveSets(x, y).any { |m| m == want } }, quantale, label)
	}
	*pmn { |chords, m, n, quantale|
		^this.voiceMoves(chords, (1 ! m) ++ (2 ! n), quantale, "P(%,%)".format(m, n))
	}

	// Chords that share exactly k tones (and are not the same chord).
	*commonTones { |chords, k = 2, quantale, label|
		^this.on(chords, { |x, y| (x != y) and: { x.commonPcs(y).size == k } }, quantale, label)
	}

	prS { |x|
		sIndex = sIndex ?? { RLRelation.prIndexOf(source) };
		^sIndex[x] ?? { Error("RLRelation: % is not one of the source objects".format(x)).throw }
	}
	prT { |y|
		tIndex = tIndex ?? {
			if(target === source) { sIndex = sIndex ?? { RLRelation.prIndexOf(source) }; sIndex } { RLRelation.prIndexOf(target) }
		};
		^tIndex[y] ?? { Error("RLRelation: % is not one of the target objects".format(y)).throw }
	}
	*prIndexOf { |objects|
		var d = Dictionary.new(objects.size * 2);
		objects.do { |x, i| d[x] = i };
		^d
	}

	// The value at (x, y); whether x is related to y; what x is related to; what is related to y.
	at { |x, y| ^rows[this.prS(x)][this.prT(y)] }
	relates { |x, y| ^rows[this.prS(x)][this.prT(y)] != quantale.zero }
	images { |x|
		var zero = quantale.zero, out = Array.new;
		rows[this.prS(x)].do { |v, j| if(v != zero) { out = out.add(target[j]) } };
		^out
	}
	preimages { |y|
		var zero = quantale.zero, j = this.prT(y), out = Array.new;
		rows.do { |r, i| if(r[j] != zero) { out = out.add(source[i]) } };
		^out
	}

	// The one image of x, when the relation acts there as a function.
	value { |x|
		var im = this.images(x);
		if(im.size != 1) { Error("RLRelation: % has % images under %, not one".format(x, im.size, label ? "the relation")).throw };
		^im[0]
	}

	// Whether this can come after that: it starts on the list where that ends.
	follows { |that| ^(source === that.target) or: { source == that.target } }

	// This after that.
	<> { |that|
		var zero = quantale.zero, width = target.size, out;
		if(this.follows(that).not) { Error("RLRelation: % does not start where % ends".format(label ? "this", that.label ? "that")).throw };
		if(quantale !== that.quantale) { Error("RLRelation: two quantales, % and %".format(quantale, that.quantale)).throw };
		out = that.rows.collect { |rrow|
			var acc = Array.fill(width, zero);
			rrow.do { |rv, y|
				if(rv != zero) {
					rows[y].do { |sv, z|
						if(sv != zero) { acc[z] = quantale.join(acc[z], quantale.mul(sv, rv)) };
					};
				};
			};
			acc
		};
		^RLRelation.prMake(that.source, target, out, quantale,
			if(label.notNil and: { that.label.notNil }) { label ++ that.label }, that.prSIndex, tIndex)
	}

	prSIndex { ^sIndex }

	sourceIdentity { ^RLRelation.identity(source, quantale) }
	targetIdentity { ^RLRelation.identity(target, quantale) }

	// n times itself (a relation of one list to itself).
	repeated { |n|
		var out = this.sourceIdentity;
		n.do { out = this <> out };
		out.label = label !? { "(" ++ label ++ ")^" ++ n };
		^out
	}

	// The same pairs read backwards.
	converse {
		^RLRelation.prMake(target, source, target.collect { |y, j| rows.collect { |r| r[j] } }, quantale,
			label !? { "(" ++ label ++ ")°" }, tIndex, sIndex)
	}

	// Value by value: the join of two relations; this where that has nothing.
	| { |that|
		this.prSameShape(that);
		^RLRelation.prMake(source, target, rows.collect { |r, i| r.collect { |v, j| quantale.join(v, that.rows[i][j]) } },
			quantale, nil, sIndex, tIndex)
	}
	without { |that|
		var zero = quantale.zero;
		this.prSameShape(that);
		^RLRelation.prMake(source, target, rows.collect { |r, i| r.collect { |v, j| if(that.rows[i][j] != zero) { zero } { v } } },
			quantale, nil, sIndex, tIndex)
	}

	// Included in that: every value below that's.
	<= { |that|
		this.prSameShape(that);
		^rows.every { |r, i| r.every { |v, j| quantale.leq(v, that.rows[i][j]) } }
	}

	prSameShape { |that|
		if((quantale !== that.quantale) or: { this.prSameLists(that).not }) {
			Error("RLRelation: two relations on different lists or quantales").throw
		};
	}
	prSameLists { |that|
		^((source === that.source) or: { source == that.source }) and: { (target === that.target) or: { target == that.target } }
	}

	// The same pairs with other values, in another quantale: func(x, y, value) for each related pair.
	valued { |newQuantale, func|
		var zero = quantale.zero;
		^RLRelation.prMake(source, target, rows.collect { |r, i|
			r.collect { |v, j| if(v != zero) { newQuantale.coerce(func.value(source[i], target[j], v)) } { newQuantale.zero } }
		}, newQuantale, label, sIndex, tIndex)
	}

	// The join of the identity and of every power: with lawvere costs, the least cost of a walk between two
	// objects (inf when none joins them); with a boolean relation, whether a walk joins them at all.
	closure { |maxPower = 256|
		var acc = this.sourceIdentity, next, count = 0;
		while {
			next = acc | (this <> acc);
			(next != acc) and: { count < maxPower }
		} { acc = next; count = count + 1 };
		^acc
	}

	// The related pairs as [x, y, value]; how many; how many images each source has.
	pairs {
		var zero = quantale.zero, out = Array.new;
		rows.do { |r, i| r.do { |v, j| if(v != zero) { out = out.add([source[i], target[j], v]) } } };
		^out
	}
	pairCount { var zero = quantale.zero; ^rows.sum { |r| r.count { |v| v != zero } } }
	degrees { var zero = quantale.zero; ^rows.collect { |r| r.count { |v| v != zero } } }

	isTotal { ^this.degrees.every { |d| d >= 1 } }
	isFunction { ^this.degrees.every { |d| d == 1 } }
	isBijection { ^this.isFunction and: { this.converse.isFunction } }
	isSymmetric { ^this.follows(this) and: { rows.every { |r, i| r.every { |v, j| v == rows[j][i] } } } }
	isIdentity { ^this.follows(this) and: { this == this.sourceIdentity } }

	== { |that|
		^that.isKindOf(RLRelation) and: { quantale === that.quantale } and: { rows == that.rows } and: { this.prSameLists(that) }
	}
	!= { |that| ^(this == that).not }

	hash {
		^hashCache ?? {
			var h = quantale.name.hash;
			rows.do { |r| r.do { |v| h = ((h << 5) bitXor: (h >> 27)) bitXor: v.hash } };
			hashCache = h;
			h
		}
	}

	printOn { |stream|
		stream << "RLRelation(" << (label ? "") << ": " << this.pairCount << " pairs, " << source.size << " to " << target.size;
		if(quantale !== RLQuantale.boolean) { stream << ", " << quantale.name };
		stream << ")";
	}
}
