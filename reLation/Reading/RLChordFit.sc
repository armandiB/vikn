// reLation — the chords of a score, read from its notes alone: the notes cut into spans, each under one
// chord, at the least total cost.
//
// The same idea as RLFit one level down. A span of notes can be put under any chord, so "does a chord hold
// them" tells nothing: a chord is worth the notes it saves describing. Under a chord a note costs little while
// it is one of the chord's tones and more while it is not (how much depends on its length and on how it moves:
// a short note that comes and goes by step is a passing note half the time, one that leaps is nearly always a
// tone of the chord), by the time it sounds: a bass struck eight times weighs what it weighs held. The
// chord itself costs its kind, its root, the tones of it that do not sound, where its lowest note sits, and
// where in the bar it starts (a chord starts on a downbeat more often than off the beat). The reading is the
// cheapest way through (a dynamic programme over the places a chord may change: the note onsets on a grid of
// the bar).
//
// Notes are taken as spelled (their place on the line of fifths, RLScore's \tpc), so a diminished seventh
// has one root, the one its spelling stacks in thirds, and an augmented sixth is no dominant seventh.
//
//   ~chords = RLChordFit(score);                  // score: an RLScore; nothing an analyst wrote is read
//   ~chords.chords;                               // Events as RLFit takes them: onset, dur, mn, root, pcs, bass, type, label, cost
//   ~chords.cost;
//   ~chords.candidates(8, 12);                    // the cheapest chords for a span of time, with their costs
//   ~chords.agreement;                            // with the score's analysed chords, afterwards: (root: [agreed, total], chord: [...])
//   ~chords.breakdown;                            // where the roots differ, how the two chords stand
//   RLFit(~chords.chords, score.regions);         // then the passages
//
// Two models of the costs (the second argument):
//   hand-set (nil)   RLChordFit.defaultModel: nine kinds of chord, numbers near what Beethoven's sonatas give
//   counted          RLChordFit.readModel(path): how much of their time notes are tones of their chord (by
//                    their length), how often a chord sounds each of its tones, what its lowest note is, where
//                    in the bar chords start, and the kinds of chord with their frequencies, counted on
//                    analysed scores (HomewareSC, Math/Category_Theory/fit/dcml_notes.py). Count it on other
//                    pieces than the one read.
// A kind of chord is a set of tones in fifths from the root: [0, 1, 4] a major triad, [-3, 0, 1] a minor one.

RLChordFit {
	classvar <defaultModel;
	var <score, <model, <grain, <maxSpan, <rate, <system, <chords, <cost, <times;
	var tOffsets, tRoles, tIn, tOut, tBits, tNames, cin, cout, presentBits, absentBits, bassBits, rootBits, startBits, stayBits;
	var notes, moves, places, stayCum, sliceKeys, sliceWeights, sliceSavings, sliceBase, sliceTotal, sliceLow, span = 24, width;

	*initClass {
		defaultModel = IdentityDictionary[
			\source -> "hand-set",
			\bounds -> #[0.25, 0.5, 1.0, 2.0],
			\inside -> #[0.69, 0.76, 0.84, 0.92, 0.93],
			// by length (rows) and by how the note moves: by leap, reached by step, left by step, by step both ways
			\motion -> #[[0.93, 0.86, 0.79, 0.48], [0.97, 0.93, 0.81, 0.49], [0.96, 0.95, 0.85, 0.67], [0.97, 0.96, 0.90, 0.85], [0.92, 0.95, 0.93, 0.94]],
			\present -> #[0.94, 0.87, 0.79, 0.95],
			\bass -> #[0.5, 0.25, 0.11, 0.04, 0.1],
			\change -> #[0.79, 0.43, 0.16, 0.12],
			\types -> [
				IdentityDictionary[\name -> "M", \offsets -> #[0, 1, 4], \p -> 0.42], IdentityDictionary[\name -> "m", \offsets -> #[-3, 0, 1], \p -> 0.18],
				IdentityDictionary[\name -> "Mm7", \offsets -> #[-2, 0, 1, 4], \p -> 0.22], IdentityDictionary[\name -> "o", \offsets -> #[-6, -3, 0], \p -> 0.06],
				IdentityDictionary[\name -> "o7", \offsets -> #[-9, -6, -3, 0], \p -> 0.05], IdentityDictionary[\name -> "mm7", \offsets -> #[-3, -2, 0, 1], \p -> 0.03],
				IdentityDictionary[\name -> "%7", \offsets -> #[-6, -3, -2, 0], \p -> 0.02], IdentityDictionary[\name -> "MM7", \offsets -> #[0, 1, 4, 5], \p -> 0.01],
				IdentityDictionary[\name -> "+", \offsets -> #[0, 4, 8], \p -> 0.01]
			]
		];
	}

	// grain: a chord may change where a note starts on this grid of the bar, in quarter notes (0.5: every eighth).
	// maxSpan: the longest span tried under one chord, in quarter notes (longer chords come out as spans joined).
	// rate: how many notes' worth of evidence a quarter note of sound is (4: each sixteenth note of it is one).
	// The one number here not counted but chosen: 4 to 6 gave the most of the analysts' roots on three first
	// movements of Beethoven (sonatas 2, 5, 10); the best value differs from movement to movement (1 to 12).
	// grain nil: the grid the model was counted on (0.5 when it does not say).
	*new { |score, model, grain, maxSpan = 8, rate = 4| ^super.new.prInit(score, model, grain, maxSpan, rate) }

	*readModel { |path|
		var m = if(File.exists(path.asString.standardizePath)) { REJSON.read(path) };
		if(m.isNil or: { m[\types].isNil }) { Error("RLChordFit: no model at %".format(path)).throw };
		^m
	}

	// A place on the line of fifths by name: 0 "C", 1 "G", -1 "F", -4 "Ab", 6 "F#".
	*spell { |tpc|
		var acc = ((tpc + 1) / 7).floor.asInteger;
		^"FCGDAEB"[(tpc + 1) % 7].asString ++ if(acc > 0) { "#".dup(acc).join } { "b".dup(acc.neg).join }
	}

	// What a tone is in its chord, by its fifths from the root: 0 the root, 1 the third, 2 the fifth, 3 the
	// seventh; nil for another tone (a fourth held over a root).
	*role { |offset| ^#[0, nil, 1, nil, 2, nil, 3][(offset * 4) % 7] }

	// How each note moves: 0 reached and left by leap (or by nothing), 1 reached by step, 2 left by step, 3 by
	// step both ways. A step: at most a tone, to a note that starts where this one ends, from a note that ends
	// where this one starts (within a fiftieth of a quarter note), whatever the voice.
	prMoves {
		var step = system.pitchInterval([9, 8]), n = notes.size, on = notes.collect { |x| x[\onset] }, ends, endTimes, lower;
		ends = notes.collect { |x| [x[\onset] + x[\dur], x[\pitch]] }.sort { |a, b| a[0] <= b[0] };
		endTimes = ends.collect { |e| e[0] };
		// the first place whose value is not under v, the values rising
		lower = { |arr, v| var a = 0, b = arr.size, m; while { a < b } { m = (a + b).div(2); if(arr[m] < v) { a = m + 1 } { b = m } }; a };
		^notes.collect { |x|
			var start = x[\onset], end = start + x[\dur], p = x[\pitch], i = lower.(on, end - 0.02), k = lower.(endTimes, start - 0.02);
			var left = false, came = false, d;
			while { (i < n) and: { left.not } and: { on[i] <= (end + 0.02) } } {
				d = (notes[i][\pitch] - p).abs;
				if((d > 0) and: { d <= step }) { left = true };
				i = i + 1;
			};
			while { (k < n) and: { came.not } and: { endTimes[k] <= (start + 0.02) } } {
				d = (ends[k][1] - p).abs;
				if((d > 0) and: { d <= step }) { came = true };
				k = k + 1;
			};
			(if(came) { 1 } { 0 }) + (if(left) { 2 } { 0 })
		}
	}

	// The beat of a time signature in quarter notes ("3/4": 1, "2/2": 2, "6/8": 1.5), and a place in the bar:
	// 0 the downbeat, 1 another beat, 2 a half beat, 3 elsewhere.
	*beat { |timesig|
		var parts = timesig.asString.split($/), n, d;
		if(parts.size != 2) { ^1.0 };
		n = parts[0].asInteger;
		d = parts[1].asInteger.max(1);
		^if(#[6, 9, 12].includes(n) and: { d >= 8 }) { 12 / d } { 4 / d }
	}
	*place { |pos, beat|
		var on = { |unit| ((pos / unit) - (pos / unit).round).abs < 1e-6 };
		^case { pos.abs < 1e-6 } { 0 } { on.(beat) } { 1 } { on.(beat / 2) } { 2 } { 3 }
	}
	// A whole bar of a time signature, in quarter notes ("3/4": 3, "6/8": 3).
	*barLength { |timesig|
		var parts = timesig.asString.split($/);
		^if(parts.size != 2) { 4.0 } { parts[0].asInteger * 4 / parts[1].asInteger.max(1) }
	}

	// Where the bar of each row of the score's measures starts: a bar cut in two (a repeat sign inside it) starts
	// where its first part does, and a first bar shorter than its time signature (an upbeat) is counted from
	// where the whole bar would start.
	prBarStarts {
		var measures = score.measures, starts = Array.newClear(measures.size), group = 0, first, nominal, len = 0, k = 0;
		measures.do { |m, i|
			if((i == 0) or: { measures[i - 1][0] != m[0] }) { group = m[1] };
			starts[i] = group;
		};
		if(measures.notEmpty) {
			first = measures[0];
			nominal = RLChordFit.barLength(first[3]);
			while { (k < measures.size) and: { measures[k][0] == first[0] } } { len = len + measures[k][2]; k = k + 1 };
			if(len < (nominal - 1e-6)) { k.do { |x| starts[x] = first[1] + len - nominal } };
		};
		^starts
	}

	prInit { |sc, md, gr, ms, rt|
		score = sc;
		system = sc.system;
		model = md ? defaultModel;
		grain = gr ?? { model[\grain] ? 0.5 };
		maxSpan = ms;
		rate = rt;
		width = (2 * span) + 1;
		this.prModel;
		this.prNotes;
		this.prSolve;
	}

	prModel {
		var bits = { |p| p.clip(1e-9, 1).log2.neg }, kinds = model[\types], total = kinds.sum { |t| t[\p] };
		var change = model[\change] ? #[0.5, 0.5, 0.5, 0.5];
		// a note's chance of being a tone of its chord: by its length and how it moves when the model says
		// (\motion), by its length alone otherwise
		cin = if(model[\motion].notNil) { model[\motion].collect { |row| row.collect { |p| bits.(p) } } } { model[\inside].collect { |p| bits.(p) ! 4 } };
		cout = if(model[\motion].notNil) { model[\motion].collect { |row| row.collect { |p| bits.(1 - p) } } } { model[\inside].collect { |p| bits.(1 - p) ! 4 } };
		presentBits = model[\present].collect { |p| bits.(p) };
		absentBits = model[\present].collect { |p| bits.(1 - p) };
		bassBits = model[\bass].collect { |p| bits.(p) };
		rootBits = system.steps.log2;
		startBits = change.collect { |p| bits.(p) };
		stayBits = change.collect { |p| bits.(1 - p) };
		tOffsets = kinds.collect { |t| t[\offsets].asArray.collect(_.asInteger) };
		tRoles = tOffsets.collect { |offs| offs.collect { |o| RLChordFit.role(o) } };
		tIn = tOffsets.collect { |offs| offs.size.log2 };
		tOut = tOffsets.collect { |offs| (system.steps - offs.size).max(1).log2 };
		tBits = kinds.collect { |t| bits.(t[\p] / total) };
		// a kind with a tone that is no third, fifth or seventh of its root (a fourth or a sixth held over it) says so:
		// "M(64)" is the six-four over a root, not the triad of that name
		tNames = kinds.collect { |t, i|
			var figures = IdentityDictionary[-1 -> "4", 3 -> "6", -4 -> "b6", 2 -> "2", -5 -> "b2", 6 -> "#4"], held;
			held = tOffsets[i].select { |o| RLChordFit.role(o).isNil }.collect { |o| figures[o] ? o.asString };
			t[\name].asString ++ if(held.isEmpty) { "" } { "(" ++ held.sort { |x, y| x >= y }.join ++ ")" }
		};
	}

	// The places a chord may start, and between each two of them what sounds: per tone (a place on the line
	// of fifths), the time it sounds and what that time saves when the tone is a chord's; the lowest note.
	prNotes {
		var bounds = model[\bounds], measures = score.measures, barStarts = this.prBarStarts, mi = 0, s0 = 0, last = nil, ns;
		notes = score.notes.select { |n| n[\tpc].notNil and: { n[\tpc].abs <= span } }.sort { |a, b| a[\onset] <= b[\onset] };
		moves = this.prMoves;
		times = Array.new;
		places = Array.new;
		notes.do { |n|
			var t = n[\onset], pos, steps;
			if(last.isNil or: { t > (last + 1e-9) }) {
				last = t;
				while { ((mi + 1) < measures.size) and: { measures[mi + 1][1] <= (t + 1e-9) } } { mi = mi + 1 };
				pos = t - if(measures.isEmpty) { 0 } { barStarts[mi] };
				steps = pos / grain;
				if(times.isEmpty or: { (steps - steps.round).abs < 1e-6 }) {
					times = times.add(t);
					places = places.add(RLChordFit.place(pos, RLChordFit.beat(if(measures.isEmpty) { "4/4" } { measures[mi][3] })));
				};
			};
		};
		times = times.add(notes.collect { |n| n[\onset] + n[\dur] }.maxItem ? 0);
		places = places.add(0);
		// what it costs that no chord starts at the places before each one
		stayCum = Array.newClear(times.size + 1);
		stayCum[0] = 0;
		times.size.do { |i| stayCum[i + 1] = stayCum[i] + stayBits[places[i]] };
		ns = max(times.size - 1, 0);
		sliceKeys = Array.fill(ns) { Array.new };
		sliceWeights = Array.fill(ns) { Array.new };
		sliceSavings = Array.fill(ns) { Array.new };
		sliceBase = Array.fill(ns, 0.0);
		sliceTotal = Array.fill(ns, 0.0);
		sliceLow = Array.fill(ns) { [inf, nil] };
		notes.do { |n, k|
			var a = n[\onset], b = a + n[\dur], idx = n[\tpc] + span, c = bounds.count { |x| n[\dur] >= (x - 1e-9) }, mv = moves[k], m;
			while { ((s0 + 1) < ns) and: { times[s0 + 1] <= (a + 1e-9) } } { s0 = s0 + 1 };
			m = s0;
			while { (m < ns) and: { times[m] < (b - 1e-9) } } {
				var w = (min(b, times[m + 1]) - max(a, times[m])) * rate;
				// a note that ends a hair after a place (a tuplet's rounding) does not sound in the next span
				if(w > (0.01 * rate)) {
					sliceKeys[m] = sliceKeys[m].add(idx);
					sliceWeights[m] = sliceWeights[m].add(w);
					sliceSavings[m] = sliceSavings[m].add(w * (cout[c][mv] - cin[c][mv]));
					sliceBase[m] = sliceBase[m] + (w * cout[c][mv]);
					sliceTotal[m] = sliceTotal[m] + w;
					if(n[\pitch] < sliceLow[m][0]) { sliceLow[m] = [n[\pitch], idx] };
				};
				m = m + 1;
			};
		};
	}

	// Every chord for what has been counted, priced with the way to it: a chord that starts here after the
	// cheapest reading so far (gi + startCost + its kind and its root), or the chord a reading already ends on
	// here, which goes on (arr[id] + sameCost). A chord's root is a tone that sounds. id: root * kinds + kind;
	// -1 when nothing sounds. Returns [the least total, its id, whether it goes on, the chords within `within`
	// of the least as [total, id, goes on]]; all: every chord, cheapest first.
	prBest { |count, saving, keys, total, base, lowKey, arr, gi = 0, startCost = 0, sameCost = 0, within, all = false|
		var bestCost = inf, bestId = nil, bestOn = false, nT = tOffsets.size, list = Array.new;
		var goesOn = arr.notNil and: { arr.notEmpty };
		if(total <= 0) { ^if(all) { [] } { [gi, -1, false, [[gi, -1, false]]] } };
		keys.do { |r|
			nT.do { |ti|
				var offs = tOffsets[ti], roles = tRoles[ti], sum = 0.0, nIn = 0.0, pres = 0.0, bassRole = 4, id = (r * nT) + ti, v, t, on = false, prev;
				offs.do { |o, x|
					var idx = r + o, role = roles[x], c = if((idx >= 0) and: { idx < width }) { count[idx] } { 0 };
					if(c > 0) {
						sum = sum + saving[idx];
						nIn = nIn + c;
						if(role.notNil) { pres = pres + presentBits[role] };
						if(idx == lowKey) { bassRole = role ? 4 };
					} {
						if(role.notNil) { pres = pres + absentBits[role] };
					};
				};
				v = base - sum + (nIn * tIn[ti]) + ((total - nIn) * tOut[ti]) + pres + bassBits[bassRole];
				t = gi + startCost + tBits[ti] + rootBits + v;
				if(goesOn) {
					prev = arr[id];
					if(prev.notNil and: { (prev[0] + sameCost + v) < t }) { t = prev[0] + sameCost + v; on = true };
				};
				if(t < bestCost) { bestCost = t; bestId = id; bestOn = on };
				if(all or: { within.notNil and: { t < (bestCost + within) } }) { list = list.add([t, id, on]) };
			};
		};
		if(all) { ^list.sort { |a, b| a[0] <= b[0] } };
		^[bestCost, bestId, bestOn, if(within.isNil) { [] } { list.select { |x| x[0] < (bestCost + within) } }]
	}

	// The cheapest way through, exactly: at each place the least cost of a reading up to it, and, for every chord
	// a reading may end on there, the least cost of one that does (only those from which going on could beat a
	// fresh start are kept). A span is at most maxSpan long, so a longer chord is spans in a row: the second
	// pays for going on, not for a new chord.
	prSolve {
		var nb = times.size, best, arrs, argmin, segs = Array.new, j, c, margin, lows;
		chords = Array.new;
		cost = 0;
		if(nb < 2) { ^this };
		best = Array.fill(nb, inf);
		arrs = Array.fill(nb) { IdentityDictionary.new };   // per place: chord id -> [cost, the place it came from, whether it went on]
		argmin = Array.newClear(nb);
		best[0] = 0;
		// a reading that ends on a chord is worth keeping while going on from it could beat starting that chord anew
		margin = (startBits - stayBits).maxItem.max(0) + tBits.maxItem + rootBits + 1e-6;
		(nb - 1).do { |i|
			if(best[i] < inf) {
				var t0 = times[i], count = Array.fill(width, 0.0), saving = Array.fill(width, 0.0), keys = List.new;
				var total = 0.0, base = 0.0, low = inf, lowKey = nil, jj = i + 1, res, v, m, stay;
				var startCost = if(i == 0) { 0 } { startBits[places[i]] }, sameCost = if(i == 0) { 0 } { stayBits[places[i]] };
				while { (jj < nb) and: { (jj == (i + 1)) or: { (times[jj] - t0) <= (maxSpan + 1e-9) } } } {
					m = jj - 1;
					sliceKeys[m].do { |idx, x|
						if(count[idx] <= 0) { keys.add(idx) };
						count[idx] = count[idx] + sliceWeights[m][x];
						saving[idx] = saving[idx] + sliceSavings[m][x];
					};
					total = total + sliceTotal[m];
					base = base + sliceBase[m];
					if(sliceLow[m][0] < low) { low = sliceLow[m][0]; lowKey = sliceLow[m][1] };
					res = this.prBest(count, saving, keys, total, base, lowKey, arrs[i], best[i], startCost, sameCost, margin);
					// and that no chord starts at the places inside the span
					stay = stayCum[jj] - stayCum[i + 1];
					v = res[0] + stay;
					if(v < (best[jj] - 1e-9)) { best[jj] = v; argmin[jj] = res[1] };
					res[3].do { |cand|
						var t = cand[0] + stay, old = arrs[jj][cand[1]];
						if((t < (best[jj] + margin)) and: { old.isNil or: { t < (old[0] - 1e-9) } }) { arrs[jj][cand[1]] = [t, i, cand[2]] };
					};
					jj = jj + 1;
				};
			};
		};
		cost = best[nb - 1];
		j = nb - 1;
		c = argmin[j];
		while { j > 0 } {
			var e = arrs[j][c], i = e[1], before = if(e[2]) { arrs[i][c][0] } { best[i] };
			segs = segs.addFirst([times[i], times[j], if(c == -1) { nil } { c }, e[0] - before]);
			if(e[2].not) { c = argmin[i] };
			j = i;
		};
		// Spans in a row under one chord are one chord; a span where nothing sounds is none. A chord sounds as
		// long as its notes do (\dur) and holds until the next place (\span: the harmony goes on through a rest,
		// as an analyst writes it); it has its lowest note.
		lows = Array.new;
		segs.do { |s|
			var info;
			if(s[2].notNil) {
				info = this.prSpan(s[0], s[1]);
				if(chords.notEmpty and: { chords.last[\id] == s[2] } and: { (chords.last[\onset] + chords.last[\dur] - s[0]).abs < 1e-6 }) {
					chords.last[\dur] = info[0] - chords.last[\onset];
					chords.last[\span] = s[1] - chords.last[\onset];
					chords.last[\cost] = chords.last[\cost] + s[3];
					if(info[1].notNil and: { lows.last.isNil or: { info[1][\pitch] < lows.last[\pitch] } }) { lows[lows.size - 1] = info[1] };
				} {
					chords = chords.add(this.prChord(s[2], s[0], info[0], s[3]).put(\span, s[1] - s[0]));
					lows = lows.add(info[1]);
				};
			};
		};
		chords.do { |ch, k| ch[\bass] = lows[k] !? { |n| n[\pc] } };
	}

	// What sounds in a span, a note's last hair left out: [where its sound ends, its lowest note].
	prSpan { |from, to|
		var low = nil, last = from;
		score.sounding(from, to).do { |pair|
			if(pair[1] > 0.01) {
				if(low.isNil or: { pair[0][\pitch] < low[\pitch] }) { low = pair[0] };
				last = max(last, min(to, pair[0][\onset] + pair[0][\dur]));
			};
		};
		^[if(last > (from + 1e-9)) { last } { to }, low]
	}

	prChord { |id, from, to, bits|
		var nT = tOffsets.size, r = id.div(nT) - span, ti = id % nT, fifth = system.pitchInterval([3, 2]);
		var order = tOffsets[ti].copy.sort { |a, b| ((a * 4) % 7) <= ((b * 4) % 7) }, tpcs = order.collect { |o| r + o };
		var pcOf = { |tpc| system.pc(tpc * fifth) };
		^(onset: from, dur: to - from, mn: score.measureAt(from), root: pcOf.(r), pcs: tpcs.collect(pcOf), tpc: r, tpcs: tpcs,
			type: tNames[ti], label: RLChordFit.spell(r) ++ " " ++ tNames[ti], cost: bits, id: id)
	}

	// Every chord for a span of time read alone, cheapest first: [[cost, id, false], ...].
	prCandidates { |from, to|
		var count = Array.fill(width, 0.0), saving = Array.fill(width, 0.0), keys = List.new, total = 0.0, base = 0.0, low = inf, lowKey = nil;
		var bounds = model[\bounds];
		notes.do { |nt, k|
			var a = max(nt[\onset], from), b = min(nt[\onset] + nt[\dur], to), w = (b - a) * rate, idx = nt[\tpc] + span, c, mv = moves[k];
			if(w > (0.01 * rate)) {
				c = bounds.count { |x| nt[\dur] >= (x - 1e-9) };
				if(count[idx] <= 0) { keys.add(idx) };
				count[idx] = count[idx] + w;
				saving[idx] = saving[idx] + (w * (cout[c][mv] - cin[c][mv]));
				total = total + w;
				base = base + (w * cout[c][mv]);
				if(nt[\pitch] < low) { low = nt[\pitch]; lowKey = idx };
			};
		};
		^this.prBest(count, saving, keys, total, base, lowKey, nil, 0, 0, 0, nil, true)
	}

	// The cheapest chords for a span of time, read alone: [(label:, cost:, root:, pcs:, type:), ...], at most n.
	candidates { |from, to, n = 3|
		^this.prCandidates(from, to).keep(n).collect { |pair|
			var c = this.prChord(pair[1], from, to, pair[0]);
			(label: c[\label], cost: pair[0], root: c[\root], pcs: c[\pcs], type: c[\type])
		}
	}

	// The chords again, each with the other chords its span could be (\alternatives): the cheapest ones for the
	// span read alone, at most n, that cost less than `within` more than the chord itself does there. Each has
	// \extra: what the span's notes cost more under it (less, when negative: the chord before or the place in
	// the bar had decided otherwise). For a fit that chooses between them by their context: RLFit.choosing.
	withAlternatives { |within = 8, n = 2|
		^chords.collect { |c|
			var list = this.prCandidates(c[\onset], c[\onset] + (c[\span] ? c[\dur])), own = list.detect { |pair| pair[1] == c[\id] }, out = c.copy;
			out[\alternatives] = if(own.isNil) { [] } {
				list.select { |pair| (pair[1] != c[\id]) and: { (pair[0] - own[0]) < within } }.keep(n).collect { |pair|
					var alt = this.prChord(pair[1], c[\onset], c[\onset] + c[\dur], c[\cost] + (pair[0] - own[0]));
					alt[\span] = c[\span];
					alt[\bass] = c[\bass];
					alt[\extra] = pair[0] - own[0];
					alt
				}
			};
			out
		}
	}

	// How long these chords have the roots, and the roots and tones, of chords written by an analyst (the
	// score's own when none are given): (root: [quarter notes agreed, labelled], chord: [agreed, labelled]).
	// ours: another list of chords to score than the reading's own (the ones a fit chose, RLFit.choosing).
	agreement { |labelled, ours|
		var rootTime = 0, chordTime = 0, total = 0;
		(labelled ? score.chords).do { |c|
			var a = c[\onset], b = a + c[\dur];
			if(c[\root].notNil) {
				total = total + c[\dur];
				(ours ? chords).do { |m|
					var lo = max(a, m[\onset]), hi = min(b, m[\onset] + (m[\span] ? m[\dur]));
					if((hi > (lo + 1e-9)) and: { m[\root] == c[\root] }) {
						rootTime = rootTime + (hi - lo);
						if(m[\pcs].asSet == c[\pcs].asSet) { chordTime = chordTime + (hi - lo) };
					};
				};
			};
		};
		^(root: [rootTime, total], chord: [chordTime, total])
	}

	// Where the roots are not an analyst's: [(from:, to:, mn:, ours:, theirs:, relation:), ...], run by run.
	// relation, how the two chords' tones stand: \same (the same tones under another root: a six-four chord read
	// as its bass or as its fourth), \part (ours is a part of theirs: a root that does not sound), \holds (theirs
	// is a part of ours), \shares (two tones or more in common), \other.
	differences { |labelled, ours|
		var out = Array.new;
		(labelled ? score.chords).do { |c|
			var a = c[\onset], b = a + c[\dur];
			if(c[\root].notNil) {
				(ours ? chords).do { |m|
					var lo = max(a, m[\onset]), hi = min(b, m[\onset] + (m[\span] ? m[\dur])), ours, theirs, common, relation;
					if((hi > (lo + 1e-9)) and: { m[\root] != c[\root] }) {
						ours = m[\pcs].asSet;
						theirs = c[\pcs].asSet;
						// one run while it is the same chord of ours against the same chord of theirs (an inversion
						// written as a new chord is the same chord: its root and its tones)
						if(out.notEmpty and: { (out.last[\to] - lo).abs < 1e-6 } and: { out.last[\chord] === m } and: { out.last[\theirs] == c[\label] }
							and: { out.last[\root] == c[\root] } and: { out.last[\tones] == theirs }) {
							out.last[\to] = hi
						} {
							common = (ours & theirs).size;
							relation = case
							{ ours == theirs } { \same }
							{ common == ours.size } { \part }
							{ common == theirs.size } { \holds }
							{ common >= 2 } { \shares }
							{ \other };
							out = out.add((from: lo, to: hi, mn: score.measureAt(lo), ours: m[\label], theirs: c[\label], relation: relation,
								chord: m, root: c[\root], tones: theirs))
						};
					};
				};
			};
		};
		^out
	}

	// The time of those differences by their relation, and no chord of ours at all: relation -> quarter notes.
	breakdown { |labelled, ours|
		var out = IdentityDictionary.new, theirs = labelled ? score.chords, covered = 0, total = 0;
		this.differences(theirs, ours).do { |d| out[d[\relation]] = (out[d[\relation]] ? 0) + (d[\to] - d[\from]) };
		theirs.do { |c|
			if(c[\root].notNil) {
				total = total + c[\dur];
				(ours ? chords).do { |m| covered = covered + max(0, min(c[\onset] + c[\dur], m[\onset] + (m[\span] ? m[\dur])) - max(c[\onset], m[\onset])) };
			};
		};
		out[\none] = max(0, total - covered);
		out[\total] = total;
		^out
	}

	report { |labelled, ours|
		var lines = Array.new, pct = { |pair| if(pair[1] > 0) { (100 * pair[0] / pair[1]).round(0.1).asString ++ " %" } { "-" } };
		var theirs = labelled ? score.chords, agree;
		lines = lines.add("CHORDS: % chords from % notes, cost %; costs: %".format((ours ? chords).size, notes.size, cost.round(0.01), model[\source] ? "?"));
		if(theirs.notEmpty) {
			agree = this.agreement(theirs, ours);
			lines = lines.add("CHORDS: the analysts' roots for % of the labelled time, their roots and tones for % (they wrote % chords)".format(
				pct.(agree[\root]), pct.(agree[\chord]), theirs.size));
			agree = this.breakdown(theirs, ours);
			lines = lines.add("CHORDS: another root, of the labelled time: the same tones %, ours a part of theirs %, theirs a part of ours %, two tones or more shared %, other %; no chord here %".format(
				*[\same, \part, \holds, \shares, \other, \none].collect { |k| pct.([agree[k] ? 0, agree[\total]]) }));
			this.differences(theirs, ours).do { |d|
				lines = lines.add("DIFFERS: measure %, % to %: % here, the analysts % (%)".format(d[\mn] ? "?", d[\from].round(0.01), d[\to].round(0.01), d[\ours], d[\theirs], d[\relation]));
			};
		};
		^lines.join("\n")
	}

	printOn { |stream| stream << "RLChordFit(" << chords.size << " chords from " << (notes.size) << " notes, cost " << cost.round(0.01) << ")" }
}
