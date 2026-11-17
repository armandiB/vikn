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
