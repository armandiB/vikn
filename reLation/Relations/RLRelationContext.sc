// reLation — a context of relations: a list of objects and the named relations that move them.
//
// Where an RLChordContext moves every chord to one chord, a relation may offer several, or none: an
// augmented triad is one step from six triads. The context answers the same questions (names, transform,
// act, neighbours), act with a list of any length.
//
//   ~cd = RLRelationContext.cubeDance;          // the 24 triads and 4 augmented triads of 12 steps; U, P, L
//   ~aug = ~cd.space.chord(0, \aug);
//   ~cd.act(\U, ~aug);                          // C major, E major, Ab major, C# minor, F minor, A minor
//   ~cd.act(\P, ~cd.space.major(0));            // [C minor]
//   ~cd.labels(~cd.space.major(0), ~aug);       // [U]
//   ~cd.category.size;                          // 40: the monoid U, P, L generate
//   ~cd.graph.distance(~cd.space.major(0), ~cd.space.minor(6));    // 5 steps, and 5 semitones of voice leading
//
// cubeDance(RLEDO(15)) is the same graph in 15 steps (35 chords); triads(system) the 2n triads of a division
// under P, L, R as relations (the chicken-wire).

RLRelationContext {
	var <objects, <names, named, <quantale, <space, generatorNames, categoryCache, pitchSystem;

	// relations: [[name, RLRelation], ...], or a rule (x, y) -> value in place of a relation.
	*new { |objects, relations, quantale, space| ^super.new.prInit(objects, relations, quantale, space) }

	prInit { |objs, relations, q, sp|
		objects = objs;
		quantale = q ? RLQuantale.boolean;
		space = sp;
		names = Array.new;
		named = IdentityDictionary.new;
		relations.do { |pair|
			var name = pair[0].asSymbol, rel = pair[1];
			if(rel.isKindOf(RLRelation).not) { rel = RLRelation.on(objects, rel, quantale) };
			rel.label = rel.label ? name.asString;
			names = names.add(name);
			named[name] = rel;
		};
		generatorNames = names.copy;
	}

	// The triads nearest 4:5:6 of an equal division under P, L, R, each a relation with one image.
	*triads { |system|
		var ctx = RLChordContext(RLChordSpace.triads(system ?? { RLEDO(12) })), all = ctx.space.all;
		^this.new(all, [\P, \L, \R].collect { |n| [n, RLRelation.ofFunction(all, { |x| ctx.transform(n).value(x) })] }, nil, ctx.space)
	}

	// Cube Dance (Douthett and Steinbach) and its analogues: in a division of 3k steps, the two nearly even
	// triads (steps k, k-1, k+1 and its inversion: major and minor in 12) and the even one (k, k, k: augmented),
	// with the three relations that move one voice by one step:
	//   P  n major - n minor              L  n major - (n + k) minor          (both keep an augmented triad)
	//   U  n major - the augmented triad on n, n minor - the one on n - 1
	*cubeDance { |system|
		var sys = system ?? { RLEDO(12) }, k, sp, chords, aug, p = Array.new, l = Array.new, u = Array.new, both;
		if(sys.isKindOf(RLEDO).not or: { (sys.steps % 3) != 0 }) {
			Error("RLRelationContext.cubeDance: an RLEDO of 3k steps is needed, not %".format(sys)).throw
		};
		k = sys.steps.div(3);
		sp = RLChordSpace(sys, [[\major, [0, k, (2 * k) - 1]], [\minor, [0, k - 1, (2 * k) - 1]], [\aug, [0, k, 2 * k]]]);
		chords = sp.distinct;
		aug = { |n| sp.chord(n % k, \aug) };
		both = { |list, x, y| list.add([x, y]).add([y, x]) };
		sys.steps.do { |n|
			p = both.(p, sp.chord(n, \major), sp.chord(n, \minor));
			l = both.(l, sp.chord(n, \major), sp.chord(n + k, \minor));
			u = both.(u, sp.chord(n, \major), aug.(n));
			u = both.(u, sp.chord(n, \minor), aug.(n - 1));
		};
		k.do { |n| p = p.add([aug.(n), aug.(n)]); l = l.add([aug.(n), aug.(n)]) };
		^this.new(chords, [[\U, u], [\P, p], [\L, l]].collect { |pair| [pair[0], RLRelation.fromPairs(chords, chords, pair[1])] }, nil, sp)
	}

	// The chords of a space, each once, joined when the voices of one reach the other by these moves ([1]: one
	// voice by one step of the division): the relation is named S. The graphs of Douthett and Steinbach.
	*singleStep { |space, moves = #[1], name = \S|
		var chords = space.distinct;
		^this.new(chords, [[name, RLRelation.voiceMoves(chords, moves)]], nil, space)
	}

	// Power Towers: the 36 dominant, minor and half-diminished seventh chords of 12 steps and the 3 diminished
	// sevenths, one voice by one semitone.
	*powerTowers {
		^this.singleStep(RLChordSpace(RLEDO(12), [[\dom7, [0, 4, 7, 10]], [\min7, [0, 3, 7, 10]], [\hdim7, [0, 3, 6, 10]], [\dim7, [0, 3, 6, 9]]]))
	}

	// The pitch classes of an equal division under transposition and inversion: T0, T1 ... and I0, I1 ...
	// by name (I5 sends x to 5 - x), T1 and I0 as generators. Klumpenhouwer networks live here.
	*pitchClasses { |system|
		var sys = system ?? { RLEDO(12) }, pcs = sys.pcs, rels = Array.new;
		pcs.do { |n| rels = rels.add([("T" ++ n).asSymbol, RLRelation.ofFunction(pcs, { |x| sys.pcAdd(x, n) })]) };
		pcs.do { |n| rels = rels.add([("I" ++ n).asSymbol, RLRelation.ofFunction(pcs, { |x| sys.pcSub(n, x) })]) };
		^this.new(pcs, rels).prGenerators([\T1, \I0]).prSystem(sys)
	}

	// Noll's monoid of a chord: the maps x -> ux + v of an equal division's pitch classes that send a set of
	// them into itself, each named as '3x+7'. Eight maps for C major in 12 steps.
	*preserving { |system, pcs|
		var sys = system ?? { RLEDO(12) }, all = sys.pcs, n = sys.steps, rels = Array.new;
		n.do { |u|
			n.do { |v|
				if(pcs.every { |x| pcs.includes(((u * x) + v) % n) }) {
					rels = rels.add(["%x+%".format(u, v).asSymbol, RLRelation.ofFunction(all, { |x| ((u * x) + v) % n })]);
				};
			};
		};
		^this.new(all, rels).prSystem(sys)
	}

	// How far each object belongs to a set the relations keep: for each object, in their order, the arrows of
	// the category that bring it into the set (truthValues), and their share of all the arrows, 1 for the
	// members of the set (belonging): a graded membership, to use as control data.
	truthValues { |subset| ^objects.collect { |x| this.category.truthValue(x, subset) } }
	belonging { |subset| ^this.truthValues(subset).collect { |tv| tv.size / this.category.size } }

	prGenerators { |list| generatorNames = list; categoryCache = nil }
	prSystem { |sys| pitchSystem = sys }

	system { ^pitchSystem ?? { space !? { space.system } } }

	// An arrow from what names it: a relation, a name, or a word (an Array of names, in the order of time).
	arrow { |label|
		var rel;
		^case
		{ label.isKindOf(RLRelation) } { label }
		{ label.isKindOf(SequenceableCollection) and: { label.isKindOf(String).not } } {
			rel = this.identityTransform;
			label.do { |w| rel = this.arrow(w) <> rel };
			rel
		}
		{ this.transform(label) }
	}

	// The name of an arrow: the first name of the context that does the same, else its word in the generators
	// (read right to left), else nil.
	nameOf { |arrow|
		var known = names.detect { |n| named[n] == arrow };
		^if(known.notNil) { known.asString } { this.category.canonical(arrow) !? { |a| a.label } }
	}

	// A relation by its name; the names given at the start; the relation of each object to itself.
	transform { |name| ^named[name.asSymbol] ?? { Error("RLRelationContext: no relation named %".format(name)).throw } }
	identityTransform { ^RLRelation.identity(objects, quantale) }
	generators { ^generatorNames.copy }

	// Name the relation a word does (its steps in the order of time).
	define { |name, word|
		var rel = RLRelation.identity(objects, quantale);
		word.do { |w| rel = this.prResolve(w) <> rel };
		rel.label = name.asString;
		if(named[name.asSymbol].isNil) { names = names.add(name.asSymbol) };
		named[name.asSymbol] = rel;
		^rel
	}

	prResolve { |w| ^if(w.isKindOf(RLRelation)) { w } { this.transform(w) } }

	// What a relation offers from an object: a list.
	act { |name, x| ^this.prResolve(name).images(x) }

	// The objects one generator away, as [name, object] pairs (an object itself left out).
	neighbours { |x|
		var out = Array.new;
		generatorNames.do { |n| named[n].images(x).do { |y| if(y != x) { out = out.add([n, y]) } } };
		^out
	}

	// The generators that relate x to y.
	labels { |x, y| ^generatorNames.select { |n| named[n].relates(x, y) } }

	// The first generator relating each object of a list to the next (nil where none does); whether a word
	// fits a list, step by step.
	wordOf { |list|
		var out = Array.new;
		list.doAdjacentPairs { |x, y| out = out.add(this.labels(x, y).first) };
		^out
	}
	isWalk { |list, word|
		var ok = (list.size == (word.size + 1));
		if(ok) { word.do { |w, i| if(this.prResolve(w).relates(list[i], list[i + 1]).not) { ok = false } } };
		^ok
	}

	// The join of some relations (all the generators when none is named).
	union { |which|
		var list = (which ? generatorNames).collect { |n| this.transform(n) }, out = list.first;
		list.drop(1).do { |r| out = out | r };
		^out
	}

	// The category the generators generate (kept once made).
	category { |maxSize = 4000|
		^categoryCache ?? {
			categoryCache = RLCategory(generatorNames.collect { |n| [n, named[n]] }, maxSize);
			categoryCache
		}
	}

	// The graph of some relations (all the generators when none is named): see RLGraph for cost.
	graph { |which, cost| ^RLGraph(this.union(which), cost) }

	printOn { |stream| stream << "RLRelationContext(" << objects.size << " objects, " << generatorNames << ")" }
}
