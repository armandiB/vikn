// reLation — a fit: a list of chords read as passages, each in one context, at the least total cost.
//
// Any two triads are joined by an arrow in every group context, so "is there an arrow" tells nothing: a reading
// is worth what it saves. Each chord and each step from a chord to the next has a cost in each candidate context,
// each change of context a cost, and the fit is the cheapest way through (a dynamic programme over the chords).
//
// The contexts:
//   a region      one key round the tonic (RLRegions): the chord's tones should be tones of its scale, and a step
//                 costs by its kind of root progression (RLScale:progression: Schoenberg's ascending, descending,
//                 superstrong); a chord with a tone outside the scale is foreign, unless it leads to the next
//                 chord as V leads to I or vii to I (an artificial dominant)
//   \triads       the major and minor triads under P, L, R: a step costs the number of generators between the two
//                 triads (a chord's seventh is left out; a chord that holds no triad is foreign)
// A change from a region to another costs by the class of the new one seen from the old (RLRegions:relation).
//
//   ~fit = RLFit(chords, regions);       // chords: Events with \pcs and \root (pitch classes), and what else they have
//   ~fit.segments;                       // the passages: first, last, kind, region, name, numerals, word, patterns, cost
//   ~fit.cost;                           // the total
//   ~fit.surprises;                      // foreign chords, broken patterns, far changes of region
//   ~fit.agreement;                      // with the keys the chords carry (\key), when an analyst wrote them
//   ~fit.coverage(score);                // per passage, how much of the notes the chords and the scale hold
//   ~fit.report(score).postln;
//
// Two models of the costs (the third argument):
//   hand-set (nil)   one table, RLFit.defaultCosts (pass the entries to change): the kinds of progression and the
//                    classes of regions are Schoenberg's (Structural Functions of Harmony, chapters II and IX), the
//                    numbers on them ours. A fallback, and what the tests use.
//   counted          RLFit.readModel(path): how often each kind of chord follows each in a corpus of analysed
//                    scores, how often and how far the key changes, as bits (minus log2 of the frequency). The cost
//                    of a reading is then the length of its description, and a passage is in a key when saying
//                    so is shorter than not. Count the model on other pieces than the one read.

RLFit {
	classvar <defaultCosts;
	var <chords, <regions, <costs, <span, <system, <states, <path, <cost, <segments, <surprises;
	var triadSpace, triadContext, triadGraph, distances, readings;

	*initClass {
		defaultCosts = IdentityDictionary[
			// a step inside a region, by its root progression
			\same -> 0.25, \ascending -> 1.0, \descending -> 1.5, \superstrong -> 1.5, \other -> 2.0,
			// a chord inside a region: by the degree of its root (I and V say the key most clearly)
			\degrees -> #[0.0, 0.2, 0.3, 0.1, 0.0, 0.2, 0.3],
			\foreign -> 2.0, \artificial -> 0.75,
			// a change of region, times the class of the new one seen from the old; to or from the triads
			\region -> 1.5, \context -> 3.0,
			// a step among the triads: per generator, and at most
			\generator -> 1.0, \far -> 4.0
		];
	}

	// model: nil (the hand-set table), some of its entries to change, or a counted model (RLFit.readModel).
	*new { |chords, regions, model, span = 7| ^super.new.prInit(chords, regions, model, span) }

	// The model the fit prices with. A chord in a region is one of nine kinds: the degree of its root, 0 to 6, when
	// its tones are tones of the scale; 7, an artificial dominant; 8, another foreign chord. For each mode the
	// model has the cost of a first chord of each kind and of a step from each kind to each; the cost of a change
	// to a region of each class (0: none); and the costs among the triads.
	//   handModel: from the hand-set table, a step priced by Schoenberg's kind of root progression.
	//   readModel: counted on a corpus of analysed scores, in bits (HomewareSC, Math/Category_Theory/fit/dcml_costs.py).
	*handModel { |overrides|
		var c = defaultCosts.copy, kinds = #[\same, \superstrong, \descending, \ascending, \descending, \ascending, \superstrong];
		var chord, first, step, one, model = IdentityDictionary.new;
		overrides !? { overrides.keysValuesDo { |k, v| c[k] = v } };
		chord = { |s| case { s < 7 } { c[\degrees][s] } { s == 7 } { c[\artificial] } { c[\foreign] } };
		first = Array.fill(9, chord);
		step = Array.fill(9) { |a|
			Array.fill(9) { |b|
				var move = case
				{ (a < 7) and: { b < 7 } } { c[kinds[(b - a) % 7]] }
				{ (a == 7) and: { b < 7 } } { c[\ascending] }
				{ a == b } { c[\same] }
				{ c[\other] };
				move + chord.(b)
			}
		};
		one = { IdentityDictionary[\first -> first, \step -> step] };
		model[\major] = one.value;
		model[\minor] = one.value;
		model[\change] = IdentityDictionary[\major -> Array.fill(6) { |k| c[\region] * k }, \minor -> Array.fill(6) { |k| c[\region] * k }];
		model[\context] = c[\context];
		model[\generator] = c[\generator];
		model[\far] = c[\far];
		model[\foreign] = c[\foreign];
		model[\same] = c[\same];
		model[\unit] = "hand-set";
		^model
	}
	*readModel { |path|
		var m = if(File.exists(path.asString.standardizePath)) { REJSON.read(path) };
		if(m.isNil or: { m[\major].isNil }) { Error("RLFit: no model at %".format(path)).throw };
		^m
	}

	// Chords for a fit from RLChords: of a space of the system itself, or of a scale's space (given the scale).
	*events { |list, scale, dur = 1|
		var onset = 0;
		^list.collect { |ch, i|
			var d = dur.asArray.wrapAt(i), ev;
			ev = if(scale.notNil) {
				(pcs: scale.realize(ch), root: scale.parentPc(ch.root), label: scale.numeral(ch))
			} {
				(pcs: ch.pcs, root: ch.root, label: ch.name)
			};
			ev[\onset] = onset;
			ev[\dur] = d;
			onset = onset + d;
			ev
		}
	}

	// The runs of a word that repeat a shorter word: [(start:, period:, unit:, length:, repeats:), ...]. At least
	// two units and three steps; nil breaks a run; a unit that is itself a repetition is left to its own period,
	// and a unit of zeros (a root that stays) is no pattern.
	*periods { |word, maxPeriod = 4|
		var out = Array.new, n = word.size;
		Array.series(maxPeriod, 1).do { |p|
			var a = 0;
			while { a < (n - p) } {
				var b = a, len, unit, minimal;
				while { (b < (n - p)) and: { word[b].notNil } and: { word[b] == word[b + p] } } { b = b + 1 };
				if(b > a) {
					len = (b - a) + p;
					unit = word.copyRange(a, a + p - 1);
					minimal = Array.series(p - 1, 1).every { |q| ((p % q) != 0) or: { unit != Array.fill(p) { |i| unit[i % q] } } };
					if(minimal and: { len >= (2 * p) } and: { len >= 3 } and: { unit.every { |u| u == 0 }.not }) {
						out = out.add((start: a, period: p, unit: unit, length: len, repeats: len / p));
					};
					a = b + 1;
				} { a = a + 1 };
			};
		};
		^out.sort { |x, y| x[\start] <= y[\start] }
	}

	prInit { |list, reg, table, sp|
		chords = list.asArray;
		regions = reg;
		system = reg.system;
		span = sp;
		costs = if(table.notNil and: { table[\major].notNil }) { table } { RLFit.handModel(table) };
		this.prTriads;
		this.prStates;
		this.prSolve;
		this.prSegments;
		this.prSurprises;
	}

	prTriads {
		if(system.isFinite) {
			triadSpace = RLChordSpace.triads(system);
			triadContext = RLRelationContext.triads(system);
			triadGraph = triadContext.graph;
			distances = Dictionary.new;
		};
	}

	// a step among the triads as its shortest word of P, L, R, in the order played (\LR: L then R); \same
	prTriadWord { |x, y|
		var word = "";
		if(x == y) { ^\same };
		(triadGraph.shortestPath(x, y) ? []).doAdjacentPairs { |a, b| word = word ++ (triadContext.labels(a, b).first ? "?") };
		^word.asSymbol
	}

	// the major or minor triad on a chord's root, when the chord holds one
	prTriad { |chord|
		var root = chord[\root], pcs = chord[\pcs], has = { |x| pcs.includesEqual(system.pcAdd(root, x)) };
		if(triadSpace.isNil or: { root.isNil }) { ^nil };
		if(has.(system.interval([3, 2])).not) { ^nil };
		^case
		{ has.(system.interval([5, 4])) } { triadSpace.major(root) }
		{ has.(system.interval([6, 5])) } { triadSpace.minor(root) }
		{ nil }
	}

	prDistance { |x, y|
		var row = distances[x] ?? { var r = triadGraph.distances(x); distances[x] = r; r };
		^row[triadGraph.indexOf(y)]
	}

	// every state reads every chord: what the chord is there, what it costs, what the step into it costs
	prStates {
		var n = chords.size, central = regions.central, list;
		list = regions.regions(span).sort { |a, b|
			var ka = [a[0].abs, if(a[1] == central[1]) { 0 } { 1 }, a[0]], kb = [b[0].abs, if(b[1] == central[1]) { 0 } { 1 }, b[0]];
			(ka[0] < kb[0]) or: { (ka[0] == kb[0]) and: { (ka[1] < kb[1]) or: { (ka[1] == kb[1]) and: { ka[2] >= kb[2] } } } }
		};
		states = list.collect { |r| (kind: \region, region: r, scale: regions.scale(r)) };
		if(triadSpace.notNil) { states = states.add((kind: \triads)) };
		readings = states.collect { |st| if(st[\kind] == \region) { this.prReadRegion(st[\scale], st[\region][1]) } { this.prReadTriads } };
	}

	prReadRegion { |scale, mode|
		var n = chords.size, deg, inside, kinds, kind, step, target, table = costs[mode];
		var third = system.interval([5, 4]), minorThird = system.interval([6, 5]), fourth = system.interval([4, 3]);
		var semitone = system.interval([16, 15]), dim = system.pcAdd(minorThird, minorThird);
		deg = chords.collect { |c| c[\root] !? { |r| scale.degreeOf(r) } };
		inside = chords.collect { |c| c[\pcs].every { |pc| scale.holds(pc) } };
		// where a chord leads: the next chord on another root, at most three chords on (a dominant may be held)
		target = chords.collect { |c, i| Array.series(3, i + 1).detect { |j| chords[j].notNil and: { chords[j][\root] != c[\root] } } };
		kinds = chords.collect { |c, i|
			var has = { |x| c[\pcs].includesEqual(system.pcAdd(c[\root], x)) }, next = target[i] !? { |j| chords[j] }, to;
			case
			{ inside[i] } { \inside }
			{ next.isNil or: { c[\root].isNil } or: { next[\root].isNil } or: { inside[target[i]].not } } { \foreign }
			{
				to = system.pcSub(next[\root], c[\root]);
				(has.(third) and: { to == fourth }) or: { has.(minorThird) and: { has.(dim) } and: { to == semitone } }
			} { if(has.(third)) { \dominant } { \leading } }
			{ \foreign }
		};
		// the nine kinds of the model: the root's degree, 7 an artificial dominant, 8 foreign
		kind = chords.collect { |c, i|
			switch(kinds[i], \inside, { deg[i] ? 8 }, \foreign, { 8 }, { 7 })
		};
		// the cost of each chord given the one before, both read in this region (of the first chord alone at 0)
		step = chords.collect { |c, i|
			if(i == 0) { table[\first][kind[0]] } { table[\step][kind[i - 1]][kind[i]] }
		};
		^(deg: deg, kinds: kinds, kind: kind, step: step, target: target)
	}

	prReadTriads {
		var tri = chords.collect { |c| this.prTriad(c) }, step, same = costs[\same] ? 1.0;
		step = chords.collect { |c, i|
			var d;
			case
			{ tri[i].isNil } { costs[\foreign] }
			{ (i == 0) or: { tri[i - 1].isNil } } { costs[\first_triad] ? 0 }
			{
				d = this.prDistance(tri[i - 1], tri[i]);
				d == 0
			} { same }
			{ min(d * costs[\generator], costs[\far]) }
		};
		^(tri: tri, step: step)
	}

	prChange { |a, b|
		^case
		{ (a[\kind] == \region) and: { b[\kind] == \region } } {
			costs[\change][a[\region][1]][if(a === b) { 0 } { regions.relation(a[\region], b[\region]) }]
		}
		{ a === b } { 0 }
		{ costs[\context] }
	}

	prSolve {
		var n = chords.size, s = states.size, best, back, change, last, at;
		if(n == 0) { path = []; cost = 0; ^this };
		change = states.collect { |a| states.collect { |b| this.prChange(a, b) } };
		best = Array.fill(n) { Array.fill(s, inf) };
		back = Array.fill(n) { Array.fill(s, 0) };
		s.do { |k| best[0][k] = readings[k][\step][0] };
		Array.series(n - 1, 1).do { |i|
			s.do { |k|
				// staying is tried first, so that a tie keeps the passage whole; a chord reached from another
				// context is priced as a step of the new one, the chord before read there too (a pivot)
				var low = best[i - 1][k] + change[k][k], from = k;
				s.do { |j|
					var v = best[i - 1][j] + change[j][k];
					if(v < (low - 1e-9)) { low = v; from = j };
				};
				best[i][k] = low + readings[k][\step][i];
				back[i][k] = from;
			};
		};
		last = best[n - 1];
		at = 0;
		s.do { |k| if(last[k] < (last[at] - 1e-9)) { at = k } };
		cost = last[at];
		path = Array.newClear(n);
		Array.series(n, n - 1, -1).do { |i| path[i] = at; at = back[i][at] };
	}

	prSegments {
		var n = chords.size, first = 0;
		segments = Array.new;
		n.do { |i|
			if((i == (n - 1)) or: { path[i + 1] != path[i] }) {
				segments = segments.add(this.prSegment(first, i));
				first = i + 1;
			};
		};
	}

	prSegment { |first, last|
		var k = path[first], st = states[k], read = readings[k], seg, idx = Array.series(last - first + 1, first);
		var what, kept = Array.new, moves;
		seg = (first: first, last: last, kind: st[\kind], state: k,
			from: chords[first][\onset], to: chords[last][\onset] !? { |o| o + (chords[last][\dur] ? 0) });
		// its chords' costs, with what it cost to come to it: the passages' costs add up to the fit's
		seg[\cost] = idx.sum { |i| read[\step][i] + if(i > 0) { this.prChange(states[path[i - 1]], st) } { 0 } };
		if(st[\kind] == \region) {
			seg[\region] = st[\region];
			seg[\scale] = st[\scale];
			seg[\name] = regions.nameOf(st[\region]);
			seg[\relation] = regions.classOf(st[\region]);
			seg[\numerals] = idx.collect { |i| this.prNumeral(st[\scale], read, i) };
			seg[\word] = idx.drop(1).collect { |i|
				if(read[\deg][i].notNil and: { read[\deg][i - 1].notNil }) { (read[\deg][i] - read[\deg][i - 1]) % st[\scale].steps }
			};
			seg[\progressions] = seg[\word].collect { |w| w !? { st[\scale].progressionOf(w) } };
			what = read[\deg];
		} {
			seg[\numerals] = idx.collect { |i| read[\tri][i] !? { |t| t.name } ? "?" };
			seg[\word] = idx.drop(1).collect { |i|
				if(read[\tri][i].notNil and: { read[\tri][i - 1].notNil }) { this.prTriadWord(read[\tri][i - 1], read[\tri][i]) }
			};
			what = read[\tri];
		};
		// patterns are looked for in the moves alone: a root (a triad) that stays is passed over. Each run keeps
		// where it is in the chords: from, to, and next, the chord that follows it on another root (nil at the end).
		idx.do { |i| if(kept.isEmpty or: { what[i] != what[kept.last] }) { kept = kept.add(i) } };
		moves = kept.drop(1).collect { |i| seg[\word][i - first - 1] };
		seg[\patterns] = RLFit.periods(moves).collect { |p|
			p[\from] = kept[p[\start]];
			p[\to] = kept[p[\start] + p[\length]];
			p[\next] = kept[p[\start] + p[\length] + 1];
			p
		};
		^seg
	}

	// a chord's Roman numeral in a scale: V/x and viio/x for artificial dominants, * for another foreign chord
	prNumeral { |scale, read, i|
		var c = chords[i], d = read[\deg][i], kind = read[\kinds][i], has, roman, text;
		var names = #["I", "II", "III", "IV", "V", "VI", "VII"];
		if(c[\root].isNil) { ^"?" };
		has = { |x| c[\pcs].includesEqual(system.pcAdd(c[\root], x)) };
		roman = { |deg, chord|
			var r = names.wrapAt(deg ? 0), hasIn = { |x| chord[\pcs].includesEqual(system.pcAdd(chord[\root], x)) };
			var major = system.interval([5, 4]), d = deg ? 0, small;
			// by its third; a chord without one (a suspension, a six-four over V) by the scale's own, V a dominant
			small = case
			{ hasIn.(major) } { false }
			{ hasIn.(system.interval([6, 5])) } { true }
			{ d == 4 } { false }
			{ system.pcSub(scale.parentPc(d + 2), scale.parentPc(d)) != major };
			if(small) { r = r.toLower };
			r
		};
		if((kind == \dominant) or: { kind == \leading }) {
			^if(kind == \dominant) { "V" } { "viio" } ++ if(c[\pcs].size > 3) { "7" } { "" } ++ "/" ++ roman.(read[\deg][read[\target][i]], chords[read[\target][i]])
		};
		if(d.isNil) { ^"*" ++ system.name(c[\root]) };
		text = roman.(d, c);
		if(scale.isNatural(c[\root]).not) { text = "#" ++ text };
		if(has.(system.interval([3, 2])).not) {
			if(has.(system.interval([6, 5])) and: { has.(system.pcScale(system.interval([6, 5]), 2)) }) { text = text ++ "o" };
			if(has.(system.interval([5, 4])) and: { has.(system.pcScale(system.interval([5, 4]), 2)) }) { text = text ++ "+" };
		};
		if(c[\pcs].size > 3) { text = text ++ "7" };
		if(kind == \foreign) { text = text ++ "*" };
		^text
	}

	// What stands out: a foreign chord that leads nowhere, a pattern of three units or more that stops before its
	// passage does (with what one more step of it would have been), a change to a region of class 3 or beyond.
	prSurprises {
		surprises = Array.new;
		segments.do { |seg, si|
			var read = readings[seg[\state]];
			if(seg[\kind] == \region) {
				Array.series(seg[\last] - seg[\first] + 1, seg[\first]).do { |i|
					if(read[\kinds][i] == \foreign) {
						surprises = surprises.add((kind: \foreign, at: i, segment: si, chord: seg[\numerals][i - seg[\first]]));
					};
				};
				seg[\patterns].do { |pat|
					var lastChord = pat[\to], next = pat[\next], expected, actual, scale = seg[\scale];
					if(next.notNil and: { read[\deg][lastChord].notNil } and: { pat[\repeats] >= 3 }) {
						expected = (read[\deg][lastChord] + pat[\unit].wrapAt(pat[\length])) % scale.steps;
						actual = read[\deg][next];
						surprises = surprises.add((kind: \break, at: next, segment: si, pattern: pat,
							expected: scale.numeral(scale.triads.chord(expected)), actual: seg[\numerals][next - seg[\first]],
							// the steps from the expected root to the one that came, the shorter way round
							by: actual !? { var k = (actual - expected) % scale.steps; if(k > scale.steps.div(2)) { k - scale.steps } { k } }));
					};
				};
			};
			if((si > 0) and: { seg[\kind] == \region } and: { segments[si - 1][\kind] == \region }) {
				var rel = regions.relation(segments[si - 1][\region], seg[\region]);
				if(rel >= 3) { surprises = surprises.add((kind: \far, at: seg[\first], segment: si, relation: rel)) };
			};
		};
		surprises = surprises.sort { |a, b| a[\at] <= b[\at] };
	}

	// whether the fit reads chord i in the key the chord carries (\key); loosely: or in the key it is written
	// "of" (\applied: V7/vi is in vi for a moment)
	prAgrees { |i, loose = false|
		var st = states[path[i]], is = { |key|
			key.notNil and: { st[\kind] == \region } and: { st[\region][1] == key[1] } and: { regions.tonicOf(st[\region]) == regions.tonicOf(key) }
		};
		^is.(chords[i][\key]) or: { loose and: { is.(chords[i][\applied]) } }
	}

	// How long the fit's regions are the keys the chords carry (\key, a region): [quarter notes agreed, labelled].
	// loose: a chord written "of" a degree (\applied) agrees in that degree's key too.
	agreement { |loose = false|
		var agreed = 0, total = 0;
		chords.do { |c, i|
			var d = c[\dur] ? 1;
			if(c[\key].notNil) {
				total = total + d;
				if(this.prAgrees(i, loose)) { agreed = agreed + d };
			};
		};
		^[agreed, total]
	}

	// Where the fit's context is not the key the chords carry: [(first:, last:, state:, key:), ...], run by run.
	differences { |loose = false|
		var out = Array.new, same = { |i| chords[i][\key].isNil or: { this.prAgrees(i, loose) } };
		chords.do { |c, i|
			if(same.(i).not) {
				if(out.notEmpty and: { out.last[\last] == (i - 1) } and: { out.last[\state] == path[i] } and: { out.last[\key] == c[\key] }) {
					out.last[\last] = i
				} {
					out = out.add((first: i, last: i, state: path[i], key: c[\key]))
				};
			};
		};
		^out
	}

	// Per passage, how much of a score's notes its chords hold, and its scale: (chords: [covered, total],
	// scale: [covered, total], natural: [covered, total]) in quarter notes; scale and natural nil among the triads.
	coverage { |score|
		^segments.collect { |seg|
			var held = 0, total = 0, out;
			Array.series(seg[\last] - seg[\first] + 1, seg[\first]).do { |i|
				var c = chords[i], pair = score.coverage(c[\onset], c[\onset] + c[\dur], c[\pcs]);
				held = held + pair[0];
				total = total + pair[1];
			};
			out = (chords: [held, total]);
			if(seg[\kind] == \region) {
				out[\scale] = score.coverage(seg[\from], seg[\to], seg[\scale].tones);
				out[\natural] = score.coverage(seg[\from], seg[\to], seg[\scale].degrees);
			};
			out
		}
	}

	// The name of a passage's context, or of a region: "F minor (t)", "triads".
	title { |seg| ^if(seg[\kind] == \region) { this.regionTitle(seg[\region]) } { "triads" } }
	regionTitle { |r|
		^"% %%".format(system.name(regions.tonicOf(r)), r[1], regions.nameOf(r) !? { |n| " (" ++ n ++ ")" } ? "")
	}

	report { |score|
		var lines = Array.new, cover = score !? { this.coverage(score) }, agree = this.agreement, pct;
		pct = { |pair| if(pair[1] > 0) { (100 * pair[0] / pair[1]).round(0.1).asString ++ " %" } { "-" } };
		lines = lines.add("FIT: % chords, % passages, cost %".format(chords.size, segments.size, cost.round(0.01)));
		if(agree[1] > 0) {
			lines = lines.add("FIT: the analysts' keys for % of the labelled time; % counting the keys their chords are written of (V7/vi)".format(
				pct.(agree), pct.(this.agreement(true))))
		};
		segments.do { |seg, si|
			var c0 = chords[seg[\first]], c1 = chords[seg[\last]], steps = seg[\last] - seg[\first];
			lines = lines.add("% chords % to % (measures % to %): %, cost % (% per step)".format(
				si + 1, seg[\first], seg[\last], c0[\mn] ? "?", c1[\mn] ? "?", this.title(seg), seg[\cost].round(0.01),
				if(steps > 0) { (seg[\cost] / steps).round(0.01) } { "-" }));
			lines = lines.add("    " ++ seg[\numerals].join(" "));
			if(seg[\kind] == \region) {
				lines = lines.add("    steps: " ++ seg[\progressions].collect { |p| (p ? "?").asString }.join(" "));
			} {
				lines = lines.add("    word: " ++ seg[\word].collect { |w| (w ? "?").asString }.join(" "));
			};
			seg[\patterns].do { |pat|
				lines = lines.add("    pattern: % repeated % times, chords % to %".format(pat[\unit], pat[\repeats].round(0.01), pat[\from], pat[\to]));
			};
			cover !? {
				lines = lines.add("    notes held by the chords: %".format(pct.(cover[si][\chords]))
					++ if(cover[si][\scale].notNil) { "; by the scale: % (% without its substitutes)".format(pct.(cover[si][\scale]), pct.(cover[si][\natural])) } { "" });
			};
		};
		this.differences(true).do { |d|
			lines = lines.add("DIFFERS: chords % to % (measures % to %): the fit reads %, the analysts %".format(
				d[\first], d[\last], chords[d[\first]][\mn] ? "?", chords[d[\last]][\mn] ? "?",
				this.title(states[d[\state]]), this.regionTitle(d[\key])));
		};
		surprises.do { |s|
			var c = chords[s[\at]], where = "chord % (measure %)".format(s[\at], c[\mn] ? "?");
			lines = lines.add(switch(s[\kind],
				\foreign, { "SURPRISE: % is foreign to its passage: %".format(where, s[\chord]) },
				\break, { "SURPRISE: % breaks the pattern %: % expected, % came (% steps from it)".format(where, s[\pattern][\unit], s[\expected], s[\actual], s[\by] ? "?") },
				\far, { "SURPRISE: % opens a region of class %".format(where, s[\relation]) }
			));
		};
		^lines.join("\n")
	}

	printOn { |stream| stream << "RLFit(" << chords.size << " chords, " << segments.size << " passages, cost " << cost.round(0.01) << ")" }
}
