// reLation — a style prior: what a piece is read with, and what that depends on.
//
// The rules a reading follows are not the same for all music: a minor triad on the fifth degree is rare in
// Beethoven and plain in Monteverdi, a seventh is a dissonance in Corelli and a colour in a blues. A style prior
// holds what a reading assumes of a piece, and says what it depends on: mostly the era, the composer, the type
// of music. It has two parts, kept apart:
//
//   formal   the assumptions of theory, stated and not counted: which keys there are, what a minor key holds,
//            what a chord is and what kind it is of in a key, whether a seventh is of the chord or added to it,
//            what a dominant is, how far keys are from each other. Two styles differ formally where they state
//            one of these otherwise (modes, a tonic that is a dominant seventh, chords not built in thirds).
//   habits   what is counted on music, in bits: how often each kind of chord comes and follows each, how the key
//            changes, how long a chord lasts, what a piece opens and closes on.
// and notYet: what a reading also rests on and no prior holds yet (metre, which tones stand against a chord in a
// tuning, the tuning itself).
//
// A prior is made by counting (HomewareSC, Math/Category_Theory/fit/style_counts.py: one for each era, with or
// without a composer) and read from its file; RLFit takes it where it takes a model.
//
//   ~prior = RLStylePrior.read(path);
//   ~prior.depends;                          // (era: "classical", composer: nil, type: nil, left_out: "mozart")
//   ~prior.formal[\kinds];                   // "the key's triads: ..."
//   ~prior.habits[\tables];                  // [first, step, change, opens, closes, lasts]
//   ~fit = ~prior.fit(chords, regions);      // RLFit(chords, regions, ~prior)
//   ~prior.cost(chords, regions);            // the bits these chords cost under it
//   RLStylePrior.choose([~a, ~b, ~c], chords, regions);   // the prior that describes them shortest, and its fit

RLStylePrior {
	var <model;

	*new { |model|
		if(model.isKindOf(RLStylePrior)) { ^model };
		if(model.isNil or: { model[\major].isNil }) { Error("RLStylePrior: a counted model is needed (RLFit.readModel, RLFit.handModel)").throw };
		^super.newCopyArgs(model)
	}
	*read { |path| ^this.new(RLFit.readModel(path)) }
	// the hand-set costs as a prior: no habit counted, the formal part as RLFit has it built in
	*hand { |overrides| ^this.new(RLFit.handModel(overrides)) }

	prSaid { |key| ^(model[\prior] !? { |p| p[key] }) }
	name { ^(this.prSaid(\name) ? model[\source] ? model[\unit] ? "a prior").asString }
	// what it depends on: era, composer, type, and what was left out of its counts
	depends { ^this.prSaid(\depends) ? IdentityDictionary.new }
	era { ^this.depends[\era] !? { |e| e.asString } }
	composer { ^this.depends[\composer] !? { |c| c.asString } }
	// the assumptions of theory it states; what it counted; what it does not hold yet
	formal { ^this.prSaid(\formal) ? this.prFormal }
	habits { ^this.prSaid(\habits) ? IdentityDictionary[\tables -> this.tables] }
	notYet { ^this.prSaid(\not_yet) ? [] }
	// a model that does not say its assumptions (the hand-set one, an older file): read from its switches
	prFormal {
		^IdentityDictionary[
			\keys -> ["major", "minor"],
			\kinds -> if(model[\forms] == true) { "the key's triads" } { "the degree of the root" },
			\sevenths -> if(model[\added] == true) { "tones added to the triad" } { "tones of the chord like the others" }
		]
	}
	tables { ^[\first, \step, \opens, \closes, \lasts].select { |k| model[\major][k].notNil } ++ [\change] }
	// how many kinds of chord a key has under it
	kinds { ^model[\major][\first].size }

	fit { |chords, regions, span = 7| ^RLFit(chords, regions, model, span) }
	homed { |chords, regions, span = 7| ^RLFit.homed(chords, regions, model, span) }
	cost { |chords, regions, span = 7| ^this.fit(chords, regions, span).cost }

	// Of several priors, the one under which these chords cost least, with its fit: [prior, fit]. A piece nobody
	// placed is of the style that describes it shortest.
	*choose { |priors, chords, regions, span = 7|
		var best, bestFit;
		priors.do { |p|
			var prior = this.new(p), fit = prior.fit(chords, regions, span);
			if(bestFit.isNil or: { fit.cost < (bestFit.cost - 1e-9) }) { best = prior; bestFit = fit };
		};
		^[best, bestFit]
	}

	printOn { |stream| stream << "RLStylePrior(" << this.name << "; " << this.kinds << " kinds; " << this.tables.join(", ") << ")" }
}
