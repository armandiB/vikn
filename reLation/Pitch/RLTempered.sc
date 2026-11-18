// reLation — a temperament: just intonation with some commas declared to be nothing, between the lattice
// and the equal divisions.
//
// Tempering a comma out folds the lattice along it. With one comma of RLJust([3, 5]) gone, the plane of fifths
// and thirds becomes a chain (meantone: 81/80, so that four fifths make a major third); with two it becomes a
// ring, an equal division. A pitch class is still written as a point of the lattice, the one of its class
// that the echelon form of the commas picks (for meantone: 0 to 3 fifths, any number of thirds), so it reads
// as a ratio: 5/4, 15/8, 25/16. A pitch has the exponent of 2 in front, as in RLJust.
//
// The commas fix which notes are the same, not how large the steps are: that is the tuning, a size for each
// prime under which every comma is nothing. Given none, it is the one nearest the just sizes (least squares);
// `pure` names intervals to keep exact instead (as many as the temperament leaves free).
//
//   ~mt = RLTempered.meantone;                       // quarter-comma: 81/80 gone, 5/4 pure
//   ~mt.interval([3, 2]);  ~mt.interval([5, 4]);     // [1, 0] and [0, 1]
//   ~mt.pcScale([1, 0], 4);                          // [0, 1]: four fifths are a major third
//   ~mt.cents(~mt.interval([3, 2]));                 // -503.42 (a fourth down): the fifth is 696.58 cents
//   ~mt.supports(RLEDO(31));                         // true: 31 steps are a meantone
//   ~mt.divisions(34);                               // [5, 7, 12, 19, 24, 26, 31]: the equal divisions that are meantones
//   RLTempered(RLJust([3, 5]), [[81, 80], [128, 125]]).size;      // 12
//
// A temperament is a pitch system: chords, contexts, walks and voice leading work in it as in any other, and
// RLMorphism.temper goes from the lattice to it and from it to the divisions it supports.

RLTempered : RLPitchSystem {
	classvar <known;
	var <just, <commas, <basis, pivots, <sizes, <>label;

	*initClass {
		known = IdentityDictionary[
			\meantone -> [[3, 5], [[81, 80]]], \augmented -> [[3, 5], [[128, 125]]], \diminished -> [[3, 5], [[648, 625]]],
			\schismatic -> [[3, 5], [[32805, 32768]]], \porcupine -> [[3, 5], [[250, 243]]], \magic -> [[3, 5], [[3125, 3072]]],
			\kleismic -> [[3, 5], [[15625, 15552]]], \septimalMeantone -> [[3, 5, 7], [[81, 80], [126, 125]]],
			\marvel -> [[3, 5, 7], [[225, 224]]], \pajara -> [[3, 5, 7], [[50, 49], [64, 63]]]
		];
	}

	// just: an RLJust; commas: ratios [numerator, denominator]; pure: ratios to keep exact; or sizes: the size of
	// each of the just system's primes in octaves (every comma must then be nothing).
	*new { |just, commas, pure, sizes|
		^super.new.prInit(just ?? { RLJust([3, 5]) }, commas, pure, sizes)
	}

	// A temperament by its name (the keys of RLTempered.known), in its least-squares tuning or with pure intervals.
	*named { |name, pure|
		var data = known[name.asSymbol] ?? { Error("RLTempered: no temperament named % (%)".format(name, known.keys.asArray.sort)).throw };
		^this.new(RLJust(data[0]), data[1], pure).label_(name.asString)
	}

	// Meantone with its fifth narrowed by a fraction of the syntonic comma: 1/4 (major thirds pure), 1/3 (minor
	// thirds pure), 0 (fifths pure: the Pythagorean tuning, its thirds four fifths) ...
	*meantone { |fraction = 0.25|
		var l3 = 3.log2, comma = (4 * l3) - 5.log2 - 4, t3 = l3 - (fraction * comma);
		^this.new(RLJust([3, 5]), [[81, 80]], nil, [t3, (4 * t3) - 4]).label_("meantone")
	}

	prInit { |j, ratios, pure, given|
		just = j;
		commas = ratios.collect { |r| r.asArray.collect(_.asInteger) };
		this.prEchelon(commas.collect { |r| just.pitchInterval(r) });
		this.prTune(pure, given);
	}

	// The commas as pitches of the just system, in echelon form: each row starts (after the exponent of 2) with
	// zeros up to its pivot, positive, and the rows above are reduced below that pivot. Reducing a pitch by
	// these rows gives one pitch for each class.
	prEchelon { |rows|
		var m = rows.collect(_.copy), k = just.rank, r = 0, piv = Array.new;
		Array.series(k, 1).do { |col|
			var active = true;
			while { active } {
				var idx = Array.series(m.size - r, r).select { |i| m[i][col] != 0 }, best, rest;
				if(idx.isEmpty) { active = false } {
					best = idx[idx.collect { |i| m[i][col].abs }.minIndex];
					m.swap(r, best);
					rest = Array.series(m.size - r - 1, r + 1).select { |i| m[i][col] != 0 };
					if(rest.isEmpty) {
						if(m[r][col] < 0) { m[r] = m[r].neg };
						r.do { |i| m[i] = m[i] - (m[r] * (m[i][col] / m[r][col]).floor.asInteger) };
						piv = piv.add(col);
						r = r + 1;
						active = false;
					} {
						rest.do { |i| m[i] = m[i] - (m[r] * (m[i][col] / m[r][col]).floor.asInteger) };
					};
				};
			};
		};
		if(Array.series(m.size - r, r).any { |i| m[i].any { |x| x != 0 } }) {
			Error("RLTempered: these commas would make the octave nothing").throw
		};
		basis = m.keep(r);
		pivots = piv;
	}

	prSolve { |a, b|
		var n = b.size, m = a.collect { |row, i| row.collect(_.asFloat) ++ [b[i].asFloat] }, x = Array.newClear(n);
		n.do { |c|
			var best = Array.series(n - c, c).collect { |i| m[i][c].abs }.maxIndex + c;
			if(m[best][c].abs < 1e-12) { Error("RLTempered: the commas and the pure intervals do not fix a tuning").throw };
			m.swap(c, best);
			Array.series(n - c - 1, c + 1).do { |i| m[i] = m[i] - (m[c] * (m[i][c] / m[c][c])) };
		};
		Array.series(n).reverse.do { |i|
			var sum = m[i][n];
			Array.series(n - i - 1, i + 1).do { |jj| sum = sum - (m[i][jj] * x[jj]) };
			x[i] = sum / m[i][i];
		};
		^x
	}

	prTune { |pure, given|
		var logs = just.primes.collect(_.log2), odd = basis.collect { |row| row.copyToEnd(1) }, twos = basis.collect(_[0]), y;
		sizes = case
		{ given.notNil } { given.asArray.collect(_.asFloat) }
		{ pure.notNil } {
			if((odd.size + pure.size) != just.rank) {
				Error("RLTempered: % intervals must be kept pure here, not %".format(just.rank - odd.size, pure.size)).throw
			};
			this.prSolve(odd ++ pure.collect { |r| just.interval(r) }, twos.neg ++ pure.collect { |r| (just.interval(r) * logs).sum })
		}
		{ odd.isEmpty } { logs }
		{
			y = this.prSolve(odd.collect { |ri| odd.collect { |rj| (ri * rj).sum } }, odd.collect { |ri, i| (ri * logs).sum + twos[i] });
			logs - odd.collect { |ri, i| ri * y[i] }.sum
		};
		if(sizes.size != just.rank) { Error("RLTempered: one size for each of the primes % is needed".format(just.primes)).throw };
		basis.do { |row| if((row[0] + (row.copyToEnd(1) * sizes).sum).abs > 1e-9) {
			Error("RLTempered: in this tuning the comma % is not nothing".format(just.name(row.copyToEnd(1)))).throw
		} };
	}

	// The pitch of a class of pitches of the just system, and the pitch class of a class of its pitch classes.
	reduce { |pitch|
		var p = pitch;
		basis.do { |row, i| p = p - (row * (p[pivots[i]] / row[pivots[i]]).floor.asInteger) };
		^p
	}
	reducePc { |pc| ^this.reduce([0] ++ pc).copyToEnd(1) }

	primes { ^just.primes }
	rank { ^just.rank - basis.size }        // the directions left free among the pitch classes

	pcZero { ^0 ! just.rank }
	pcAdd { |a, b| ^this.reducePc(a + b) }
	pcNeg { |a| ^this.reducePc(a.neg) }
	pcScale { |a, k| ^this.reducePc(a * k) }
	pc { |pitch| ^this.reducePc(pitch.copyToEnd(1)) }
	pcHeight { |pc| var h = (pc * sizes).sum; ^h - h.floor }
	height { |pitch| ^pitch[0] + (pitch.copyToEnd(1) * sizes).sum }
	nearest { |pc, height| var r = this.reducePc(pc); ^this.reduce([(height - (r * sizes).sum).round.asInteger] ++ r) }

	pitchInterval { |ratio| ^this.reduce(just.pitchInterval(ratio)) }
	interval { |ratio| ^this.reducePc(just.interval(ratio)) }
	pitchAdd { |a, b| ^this.reduce(a + b) }
	pitchNeg { |a| ^this.reduce(a.neg) }
	octave { ^[1] ++ (0 ! just.rank) }

	name { |pc| ^just.name(this.reducePc(pc)) }

	// Whether an interval (a ratio) is nothing here.
	tempers { |ratio| ^this.interval(ratio) == this.pcZero }

	// The name of the comma or small step a pitch class is here, nil for the zero. Several just commas are one
	// pitch class once a comma is tempered out (in meantone the diesis 128/125, the major diesis 648/625, the
	// diaschisma and the schisma): the one whose just size is nearest the size it has in this tuning is named.
	commaName { |pc|
		var odd = basis.collect { |row| row.copyToEnd(1) }, here = this.cents(this.reducePc(pc)), best, bestGap = inf;
		if(this.reducePc(pc) == this.pcZero) { ^nil };
		if(odd.isEmpty) { ^just.commaName(pc) };
		(-3..3).dup(odd.size).allTuples.do { |ns|
			var x = this.reducePc(pc), name, gap;
			ns.do { |n, i| x = x + (odd[i] * n) };
			name = just.commaName(x);
			if(name.notNil) {
				gap = min((just.cents(x) - here).abs, (just.cents(x) + here).abs);
				if(gap < bestGap) { bestGap = gap; best = name };
			};
		};
		^best
	}

	// The pitch classes other than zero that are their own opposite (a tritone, when the temperament has one).
	halves {
		var odd = basis.collect { |row| row.copyToEnd(1) }, out = Array.new;
		if(odd.isEmpty) { ^out };
		[0, 1].dup(odd.size).allTuples.do { |ns|
			var x = 0 ! just.rank, h;
			ns.do { |n, i| x = x + (odd[i] * n) };
			if(x.every(_.even)) {
				h = this.reducePc(x.div(2));
				if((h != this.pcZero) and: { out.includesEqual(h).not }) { out = out.add(h) };
			};
		};
		^out
	}

	// An equal division tempers out every comma of this temperament: it is one of its tunings.
	supports { |edo| ^basis.every { |row| just.temperPitch(edo, row) == 0 } }
	divisions { |max = 72| ^Array.series((max - 4).max(0), 5).select { |n| this.supports(RLEDO(n)) } }

	// A pitch class or a pitch in an equal division this temperament supports, through the division's val.
	temperPc { |edo, pc|
		if(this.supports(edo).not) { Error("RLTempered: % does not temper out the commas of %".format(edo, this)).throw };
		^just.temperPc(edo, pc)
	}
	temperPitch { |edo, pitch|
		if(this.supports(edo).not) { Error("RLTempered: % does not temper out the commas of %".format(edo, this)).throw };
		^just.temperPitch(edo, pitch)
	}

	isFinite { ^basis.size == just.rank }
	size { ^if(this.isFinite) { basis.collect { |row, i| row[pivots[i]] }.product } { inf } }
	pcs {
		if(this.isFinite.not) { Error("RLTempered: % has no list of all its pitch classes".format(this)).throw };
		^basis.collect { |row, i| Array.series(row[pivots[i]]) }.allTuples
	}

	prData { ^[basis, sizes.round(1e-12)] }
	== { |other| ^other.isKindOf(RLTempered) and: { just == other.just } and: { this.prData == other.prData } }
	!= { |other| ^(this == other).not }
	hash { ^[\RLTempered, just.hash, this.prData].hash }

	printOn { |stream|
		stream << "RLTempered(" << (label !? { label ++ ": " } ? "") << just << " without "
		<< commas.collect { |c| "%/%".format(c[0], c[1]) }.join(", ") << ")"
	}
}
