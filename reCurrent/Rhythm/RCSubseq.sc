// reCurrent — one resolved subsequence of a rhythm (a value object).
//
// BlockOrgnsm passed these around as positional 8-slot arrays; the fields are
// named here but at(i)/put(i, v) keep the old indices working:
//   0 priority, 1 shift, 2 durs, 3 params, 4 mask, 5 name, 6 keysIgnoreOrder, 7 timeMult
// durs: Array of durations (numbers/Rests) or a Pattern; params: Event key →
// Array (per hit) or Pattern; mask: Array of Booleans or a Pattern.
// Equality and hash are structural so subseqs can live in Bags and Sets.

RCSubseq {
	var <>priority, <>shift, <>durs, <>params, <>mask, <>name, <>keysIgnoreOrder, <>timeMult;

	*new { |priority = 1, shift = 0, durs, params, mask, name, keysIgnoreOrder, timeMult = 1|
		^super.newCopyArgs(priority, shift, durs, params ?? { () }, mask, name, keysIgnoreOrder ? [], timeMult)
	}

	*fromArray { |array|
		var a = array ++ (nil ! (8 - array.size).max(0));
		^this.new(a[0] ? 1, a[1] ? 0, a[2], a[3], a[4], a[5], a[6], a[7] ? 1)
	}

	at { |i|
		^switch(i,
			0, { priority }, 1, { shift }, 2, { durs }, 3, { params },
			4, { mask }, 5, { name }, 6, { keysIgnoreOrder }, 7, { timeMult }
		)
	}

	put { |i, val|
		switch(i,
			0, { priority = val }, 1, { shift = val }, 2, { durs = val }, 3, { params = val },
			4, { mask = val }, 5, { name = val }, 6, { keysIgnoreOrder = val }, 7, { timeMult = val }
		);
	}

	asArray { ^[priority, shift, durs, params, mask, name, keysIgnoreOrder, timeMult] }
	size { ^8 }
	isArraySeq { ^durs.isKindOf(SequenceableCollection) }
	numHits { ^if(this.isArraySeq) { durs.size } { nil } }

	== { |other| ^other.isKindOf(RCSubseq) and: { this.asArray == other.asArray } }
	!= { |other| ^(this == other).not }
	hash { ^this.asArray.hash }

	copy { ^this.class.new(priority, shift, durs, params, mask, name, keysIgnoreOrder, timeMult) }

	deepCopy {
		^this.class.new(priority, shift, durs.deepCopy, params.deepCopy, mask.deepCopy, name, keysIgnoreOrder.deepCopy, timeMult)
	}

	printOn { |stream|
		stream << "RCSubseq(" << name << ", shift " << shift << ", " << (this.numHits ? "pattern") << " hits)"
	}
}
