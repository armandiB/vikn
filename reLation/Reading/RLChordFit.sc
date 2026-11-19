// reLation — the chords of a score, read from its notes alone: the notes cut into spans, each under one
// chord, at the least total cost.
//
// The same idea as RLFit one level down. A span of notes can be put under any chord, so "does a chord hold
// them" tells nothing: a chord is worth the notes it saves describing. Under a chord a note costs little while
// it is one of the chord's tones and more while it is not (how much depends on its length: a short note is a
// passing note more often), by the time it sounds: a bass struck eight times weighs what it weighs held. The
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
	var notes, places, stayCum, sliceKeys, sliceWeights, sliceSavings, sliceBase, sliceTotal, sliceLow, span = 24, width;

	*initClass {
		defaultModel = IdentityDictionary[
			\source -> "hand-set",
			\bounds -> #[0.25, 0.5, 1.0, 2.0],
			\inside -> #[0.69, 0.76, 0.84, 0.92, 0.93],
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
	*new { |score, model, grain = 0.5, maxSpan = 8, rate = 4| ^super.new.prInit(score, model, grain, maxSpan, rate) }

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

	prInit { |sc, md, gr, ms, rt|
		score = sc;
		system = sc.system;
		model = md ? defaultModel;
		grain = gr;
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
		cin = model[\inside].collect { |p| bits.(p) };
		cout = model[\inside].collect { |p| bits.(1 - p) };
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
		tNames = kinds.collect { |t| t[\name].asString };
	}

	// The places a chord may start, and between each two of them what sounds: per tone (a place on the line
	// of fifths), the time it sounds and what that time saves when the tone is a chord's; the lowest note.
	prNotes {
		var bounds = model[\bounds], measures = score.measures, mi = 0, s0 = 0, last = nil, ns;
		notes = score.notes.select { |n| n[\tpc].notNil and: { n[\tpc].abs <= span } }.sort { |a, b| a[\onset] <= b[\onset] };
		times = Array.new;
		places = Array.new;
		notes.do { |n|
			var t = n[\onset], pos, steps;
			if(last.isNil or: { t > (last + 1e-9) }) {
				last = t;
				while { ((mi + 1) < measures.size) and: { measures[mi + 1][1] <= (t + 1e-9) } } { mi = mi + 1 };
				pos = t - if(measures.isEmpty) { 0 } { measures[mi][1] };
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
		notes.do { |n|
			var a = n[\onset], b = a + n[\dur], idx = n[\tpc] + span, c = bounds.count { |x| n[\dur] >= (x - 1e-9) }, m;
			while { ((s0 + 1) < ns) and: { times[s0 + 1] <= (a + 1e-9) } } { s0 = s0 + 1 };
			m = s0;
			while { (m < ns) and: { times[m] < (b - 1e-9) } } {
				var w = (min(b, times[m + 1]) - max(a, times[m])) * rate;
				if(w > 1e-9) {
					sliceKeys[m] = sliceKeys[m].add(idx);
					sliceWeights[m] = sliceWeights[m].add(w);
					sliceSavings[m] = sliceSavings[m].add(w * (cout[c] - cin[c]));
					sliceBase[m] = sliceBase[m] + (w * cout[c]);
					sliceTotal[m] = sliceTotal[m] + w;
					if(n[\pitch] < sliceLow[m][0]) { sliceLow[m] = [n[\pitch], idx] };
				};
				m = m + 1;
			};
		};
	}

	// The cheapest chord for what has been counted, [cost, id]; every chord tried, cheapest first, when all is true.
	// A chord's root is a tone that sounds. id: root * kinds + kind. startCost: what it costs that a chord starts
	// here; sameCost: that the chord before goes on (lastId) instead.
	prBest { |count, saving, keys, total, base, lowKey, lastId, startCost = 0, sameCost = 0, all = false|
		var bestCost = inf, bestId = nil, nT = tOffsets.size, list = if(all) { Array.new };
		if(total <= 0) { ^if(all) { [] } { [0, nil] } };
		keys.do { |r|
			nT.do { |ti|
				var offs = tOffsets[ti], roles = tRoles[ti], sum = 0.0, nIn = 0.0, pres = 0.0, bassRole = 4, id = (r * nT) + ti, v;
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
				v = base - sum + (nIn * tIn[ti]) + ((total - nIn) * tOut[ti]) + pres + bassBits[bassRole]
					+ if(id == lastId) { sameCost } { startCost + tBits[ti] + rootBits };
				if(v < bestCost) { bestCost = v; bestId = id };
				if(all) { list = list.add([v, id]) };
			};
		};
		^if(all) { list.sort { |a, b| a[0] <= b[0] } } { [bestCost, bestId] }
	}

	prSolve {
		var nb = times.size, best, from, pick, segs = Array.new, j;
		chords = Array.new;
		cost = 0;
		if(nb < 2) { ^this };
		best = Array.fill(nb, inf);
		from = Array.newClear(nb);
		pick = Array.newClear(nb);
		best[0] = 0;
		(nb - 1).do { |i|
			if(best[i] < inf) {
				var t0 = times[i], count = Array.fill(width, 0.0), saving = Array.fill(width, 0.0), keys = List.new;
				var total = 0.0, base = 0.0, low = inf, lowKey = nil, lastId = pick[i] !? { |p| p[0] }, jj = i + 1, res, v, m;
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
					res = this.prBest(count, saving, keys, total, base, lowKey, lastId, startCost, sameCost);
					// and that no chord starts at the places inside the span
					v = best[i] + res[0] + (stayCum[jj] - stayCum[i + 1]);
					if(v < (best[jj] - 1e-9)) { best[jj] = v; from[jj] = i; pick[jj] = [res[1], v - best[i]] };
					jj = jj + 1;
				};
			};
		};
		cost = best[nb - 1];
		j = nb - 1;
		while { j > 0 } {
			var i = from[j];
			segs = segs.addFirst([times[i], times[j], pick[j][0], pick[j][1]]);
			j = i;
		};
		// spans in a row under one chord are one chord; a span where nothing sounds is none
		segs.do { |s|
			if(s[2].notNil) {
				if(chords.notEmpty and: { chords.last[\id] == s[2] } and: { (chords.last[\onset] + chords.last[\dur] - s[0]).abs < 1e-6 }) {
					chords.last[\dur] = s[1] - chords.last[\onset];
					chords.last[\cost] = chords.last[\cost] + s[3];
				} {
					chords = chords.add(this.prChord(s[2], s[0], s[1], s[3]));
				};
			};
		};
		chords.do { |c|
			var low = nil;
			score.sounding(c[\onset], c[\onset] + c[\dur]).do { |pair| if(low.isNil or: { pair[0][\pitch] < low[\pitch] }) { low = pair[0] } };
			c[\bass] = low !? { low[\pc] };
		};
	}

	prChord { |id, from, to, bits|
		var nT = tOffsets.size, r = id.div(nT) - span, ti = id % nT, fifth = system.pitchInterval([3, 2]);
		var order = tOffsets[ti].copy.sort { |a, b| ((a * 4) % 7) <= ((b * 4) % 7) }, tpcs = order.collect { |o| r + o };
		var pcOf = { |tpc| system.pc(tpc * fifth) };
		^(onset: from, dur: to - from, mn: score.measureAt(from), root: pcOf.(r), pcs: tpcs.collect(pcOf), tpc: r, tpcs: tpcs,
			type: tNames[ti], label: RLChordFit.spell(r) ++ " " ++ tNames[ti], cost: bits, id: id)
	}

	// The cheapest chords for a span of time, read alone: [(label:, cost:, root:, pcs:, type:), ...], at most n.
	candidates { |from, to, n = 3|
		var count = Array.fill(width, 0.0), saving = Array.fill(width, 0.0), keys = List.new, total = 0.0, base = 0.0, low = inf, lowKey = nil;
		var bounds = model[\bounds];
		notes.do { |nt|
			var a = max(nt[\onset], from), b = min(nt[\onset] + nt[\dur], to), w = (b - a) * rate, idx = nt[\tpc] + span, c;
			if(w > 1e-9) {
				c = bounds.count { |x| nt[\dur] >= (x - 1e-9) };
				if(count[idx] <= 0) { keys.add(idx) };
				count[idx] = count[idx] + w;
				saving[idx] = saving[idx] + (w * (cout[c] - cin[c]));
				total = total + w;
				base = base + (w * cout[c]);
				if(nt[\pitch] < low) { low = nt[\pitch]; lowKey = idx };
			};
		};
		^this.prBest(count, saving, keys, total, base, lowKey, nil, 0, 0, true).keep(n).collect { |pair|
			var c = this.prChord(pair[1], from, to, pair[0]);
			(label: c[\label], cost: pair[0], root: c[\root], pcs: c[\pcs], type: c[\type])
		}
	}

	// How long these chords have the roots, and the roots and tones, of chords written by an analyst (the
	// score's own when none are given): (root: [quarter notes agreed, labelled], chord: [agreed, labelled]).
	agreement { |labelled|
		var rootTime = 0, chordTime = 0, total = 0;
		(labelled ? score.chords).do { |c|
			var a = c[\onset], b = a + c[\dur];
			if(c[\root].notNil) {
				total = total + c[\dur];
				chords.do { |m|
					var lo = max(a, m[\onset]), hi = min(b, m[\onset] + m[\dur]);
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
	differences { |labelled|
		var out = Array.new;
		(labelled ? score.chords).do { |c|
			var a = c[\onset], b = a + c[\dur];
			if(c[\root].notNil) {
				chords.do { |m|
					var lo = max(a, m[\onset]), hi = min(b, m[\onset] + m[\dur]), ours, theirs, common, relation;
					if((hi > (lo + 1e-9)) and: { m[\root] != c[\root] }) {
						if(out.notEmpty and: { (out.last[\to] - lo).abs < 1e-6 } and: { out.last[\ours] == m[\label] } and: { out.last[\theirs] == c[\label] }) {
							out.last[\to] = hi
						} {
							ours = m[\pcs].asSet;
							theirs = c[\pcs].asSet;
							common = (ours & theirs).size;
							relation = case
							{ ours == theirs } { \same }
							{ common == ours.size } { \part }
							{ common == theirs.size } { \holds }
							{ common >= 2 } { \shares }
							{ \other };
							out = out.add((from: lo, to: hi, mn: score.measureAt(lo), ours: m[\label], theirs: c[\label], relation: relation))
						};
					};
				};
			};
		};
		^out
	}

	// The time of those differences by their relation, and no chord of ours at all: relation -> quarter notes.
	breakdown { |labelled|
		var out = IdentityDictionary.new, theirs = labelled ? score.chords, covered = 0, total = 0;
		this.differences(theirs).do { |d| out[d[\relation]] = (out[d[\relation]] ? 0) + (d[\to] - d[\from]) };
		theirs.do { |c|
			if(c[\root].notNil) {
				total = total + c[\dur];
				chords.do { |m| covered = covered + max(0, min(c[\onset] + c[\dur], m[\onset] + m[\dur]) - max(c[\onset], m[\onset])) };
			};
		};
		out[\none] = max(0, total - covered);
		out[\total] = total;
		^out
	}

	report { |labelled|
		var lines = Array.new, pct = { |pair| if(pair[1] > 0) { (100 * pair[0] / pair[1]).round(0.1).asString ++ " %" } { "-" } };
		var theirs = labelled ? score.chords, agree;
		lines = lines.add("CHORDS: % chords from % notes, cost %; costs: %".format(chords.size, notes.size, cost.round(0.01), model[\source] ? "?"));
		if(theirs.notEmpty) {
			agree = this.agreement(theirs);
			lines = lines.add("CHORDS: the analysts' roots for % of the labelled time, their roots and tones for % (they wrote % chords)".format(
				pct.(agree[\root]), pct.(agree[\chord]), theirs.size));
			agree = this.breakdown(theirs);
			lines = lines.add("CHORDS: another root, of the labelled time: the same tones %, ours a part of theirs %, theirs a part of ours %, two tones or more shared %, other %; no chord here %".format(
				*[\same, \part, \holds, \shares, \other, \none].collect { |k| pct.([agree[k] ? 0, agree[\total]]) }));
			this.differences(theirs).do { |d|
				lines = lines.add("DIFFERS: measure %, % to %: % here, the analysts % (%)".format(d[\mn] ? "?", d[\from].round(0.01), d[\to].round(0.01), d[\ours], d[\theirs], d[\relation]));
			};
		};
		^lines.join("\n")
	}

	printOn { |stream| stream << "RLChordFit(" << chords.size << " chords from " << (notes.size) << " notes, cost " << cost.round(0.01) << ")" }
}
