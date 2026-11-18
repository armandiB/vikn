// reLation — voice leading: how the voices of one chord move to the tones of the next.
//
// A voicing is an Array of pitches, one per voice, in the order of the voices (not sorted). minimal moves
// each voice to one tone of the target so that the total motion is least; each voice then takes the nearest
// pitch of its tone. A least motion never needs crossed voices (Tymoczko, "The geometry of musical chords",
// 2006), so it is one of the rotations of the two chords sorted round the pitch-class circle.
//
//   ~edo = RLEDO(12);
//   RLVoiceLeading.minimal(~edo, [60, 64, 67], [0, 3, 7]);   // [60, 63, 67]: C major to C minor, E down a step
//   RLVoiceLeading.minimal(~edo, [60, 64, 67], [5, 9, 0]);   // [60, 65, 69]: C major to F major
//   RLVoiceLeading.cents(~edo, [60, 64, 67], [60, 65, 69]);  // 300.0: the motion in cents
//
// Sizes are in cents, so that motions in different systems compare.

RLVoiceLeading {

	// The voicing of the target pitch classes nearest to a voicing, voice by voice (as many tones as voices).
	*minimal { |system, voices, pcs|
		var n = voices.size, vh, th, vorder, torder, best, bestCost = inf;
		if(pcs.size != n) { Error("RLVoiceLeading: % voices for % tones".format(n, pcs.size)).throw };
		if(n == 0) { ^[] };
		vh = voices.collect { |p| system.pcHeight(system.pc(p)) };
		th = pcs.collect { |c| system.pcHeight(c) };
		vorder = Array.series(n).sort { |i, j| vh[i] <= vh[j] };
		torder = Array.series(n).sort { |i, j| th[i] <= th[j] };
		n.do { |k|
			var cost = 0, assign = Array.newClear(n);
			n.do { |i|
				var v = vorder[i], t = torder[(i + k) % n], d = (th[t] - vh[v]).abs;
				cost = cost + min(d, 1 - d);
				assign[v] = t;
			};
			if(cost < (bestCost - 1e-12)) { bestCost = cost; best = assign };
		};
		^voices.collect { |p, v| system.nearest(pcs[best[v]], system.height(p)) }
	}

	// The same by trying every assignment of voices to tones: for tests, and for chords of up to six voices.
	*exhaustive { |system, voices, pcs|
		var n = voices.size, vh, th, best, bestCost = inf, order = Array.series(n);
		if(n == 0) { ^[] };
		vh = voices.collect { |p| system.pcHeight(system.pc(p)) };
		th = pcs.collect { |c| system.pcHeight(c) };
		n.factorial.asInteger.do { |i|
			var perm = order.permute(i), cost = 0;
			n.do { |v| var d = (th[perm[v]] - vh[v]).abs; cost = cost + min(d, 1 - d) };
			if(cost < (bestCost - 1e-12)) { bestCost = cost; best = perm };
		};
		^voices.collect { |p, v| system.nearest(pcs[best[v]], system.height(p)) }
	}

	// More voices than tones: each voice goes to a tone, every tone is taken, the total motion is least. Tried
	// through every way of doubling the tones (three for four voices on a triad).
	*minimalDoubled { |system, voices, pcs|
		var n = voices.size, m = pcs.size, best, bestCost = inf;
		if(n == m) { ^this.minimal(system, voices, pcs) };
		if(n < m) { Error("RLVoiceLeading: % voices cannot take % tones".format(n, m)).throw };
		this.prDoublings(m, n - m).do { |extra|
			var v = this.minimal(system, voices, pcs ++ extra.collect { |i| pcs[i] });
			var cost = this.cents(system, voices, v);
			if(cost < (bestCost - 1e-9)) { bestCost = cost; best = v };
		};
		^best
	}

	// the multisets of k indices below m, as sorted Arrays
	*prDoublings { |m, k|
		var out = [[]];
		k.do { out = out.collect { |c| Array.series(m - (c.last ? 0), c.last ? 0).collect { |i| c ++ [i] } }.flatten(1) };
		^out
	}

	// The voicings of a list of chords, from a voicing of the first (close position at the origin when none is
	// given): each chord by the least motion from the one before. More voices than tones double some.
	*through { |chords, from|
		var first = chords.first, system = first.system, v = from ?? { this.closePosition(first) }, vpcs, out;
		vpcs = v.collect { |p| system.pc(p) };
		if((first.pcs.every { |c| vpcs.includesEqual(c) } and: { vpcs.every { |c| first.pcs.includesEqual(c) } }).not) {
			Error("RLVoiceLeading: the voicing % is not the chord %".format(v, first)).throw
		};
		out = [v];
		chords.drop(1).do { |chord|
			v = this.minimalDoubled(system, v, chord.pcs);
			out = out.add(v);
		};
		^out
	}

	// The least total motion between two chords of as many tones, in cents; in steps of their RLEDO.
	*between { |x, y|
		var system = x.system, from = x.pcs.collect { |c| system.nearest(c, 0.0) };
		^this.cents(system, from, this.minimal(system, from, y.pcs))
	}
	*stepsBetween { |x, y| ^(this.between(x, y) / x.system.stepCents).round.asInteger }

	// Every way the tones of one chord can go to the tones of another of the same size, as the sorted steps
	// moved (voices that stay are left out): C major to E minor gives [1] among others. In an RLEDO.
	*moveSets { |x, y|
		var system = x.system, a = x.pcs, b = y.pcs, n, out = Array.new;
		if(system.isKindOf(RLEDO).not) { Error("RLVoiceLeading.moveSets: steps are counted in an RLEDO, not in %".format(system)).throw };
		if(a.size != b.size) { ^out };
		n = system.steps;
		a.size.factorial.asInteger.do { |i|
			var perm = b.permute(i), moves = Array.new;
			a.do { |p, k|
				var d = (perm[k] - p) % n;
				d = min(d, n - d);
				if(d != 0) { moves = moves.add(d) };
			};
			moves = moves.sort;
			if(out.includesEqual(moves).not) { out = out.add(moves) };
		};
		^out
	}

	// Between two sets of pitch classes of any sizes, doublings allowed on both sides (Tymoczko's dynamic
	// programme): [cents, pairs], the least total motion and the [from, to] pairs that make it. Every tone of
	// each side is in a pair.
	*withDoublings { |system, pcsA, pcsB|
		var ha = pcsA.collect { |c| system.pcHeight(c) }, hb = pcsB.collect { |c| system.pcHeight(c) };
		var aOrder, best, bestCost = inf, dist;
		dist = { |x, y| var d = (x - y).abs; min(d, 1 - d) };
		aOrder = Array.series(pcsA.size).sort { |i, j| ((ha[i] - ha[0]) % 1) <= ((ha[j] - ha[0]) % 1) };
		aOrder = aOrder ++ [aOrder[0]];
		pcsB.size.do { |rot|
			var bOrder = Array.series(pcsB.size).sort { |i, j| ((hb[i] - hb[rot]) % 1) <= ((hb[j] - hb[rot]) % 1) };
			var m, n, e, back, size, pairs, ij;
			bOrder = bOrder ++ [bOrder[0]];
			m = aOrder.size;
			n = bOrder.size;
			e = { inf ! n } ! m;
			back = { nil ! n } ! m;
			m.do { |i|
				n.do { |j|
					var d = dist.(ha[aOrder[i]], hb[bOrder[j]]), v = inf, from;
					if((i == 0) and: { j == 0 }) { e[i][j] = d } {
						if((i > 0) and: { e[i - 1][j] < v }) { v = e[i - 1][j]; from = [i - 1, j] };
						if((j > 0) and: { e[i][j - 1] < v }) { v = e[i][j - 1]; from = [i, j - 1] };
						if((i > 0) and: { j > 0 } and: { e[i - 1][j - 1] < v }) { v = e[i - 1][j - 1]; from = [i - 1, j - 1] };
						e[i][j] = v + d;
						back[i][j] = from;
					};
				};
			};
			size = e[m - 1][n - 1] - dist.(ha[aOrder[0]], hb[bOrder[0]]);
			if(size < (bestCost - 1e-12)) {
				bestCost = size;
				pairs = Array.new;
				ij = [m - 1, n - 1];
				while { ij.notNil } {
					pairs = pairs.add([pcsA[aOrder[ij[0]]], pcsB[bOrder[ij[1]]]]);
					ij = back[ij[0]][ij[1]];
				};
				// the last pair repeats the first: the two lists were closed into circles
				best = pairs.reverse.drop(-1);
			};
		};
		^[bestCost * 1200, best]
	}

	// How far each voice moves between two voicings, in cents, signed.
	*displacements { |system, from, to|
		^to.collect { |q, i| (system.height(q) - system.height(from[i])) * 1200 }
	}

	// The size of the motion between two voicings in cents: \l1 the sum, \l2 the Euclidean length, \linf the
	// largest move.
	*cents { |system, from, to, measure = \l1|
		var d = this.displacements(system, from, to).abs;
		^switch(measure,
			\l1, { d.sum },
			\l2, { d.squared.sum.sqrt },
			\linf, { d.maxItem ? 0 },
			{ Error("RLVoiceLeading: no measure %".format(measure)).throw }
		)
	}

	// A chord in close position: its tones in the order of its pattern, the root nearest a height (in octaves
	// above the origin), each next tone above the last.
	*closePosition { |chord, height = 0.0|
		var system = chord.system, out = Array.new, last;
		chord.pcs.do { |pc, i|
			var pitch = if(i == 0) { system.nearest(pc, height) } { system.above(pc, last) };
			out = out.add(pitch);
			last = system.height(pitch);
		};
		^out
	}
}
