// reLation, reading a piece: RLStylePrior, what a piece is read with. No server. The priors here are made by
// hand; counted ones are made and checked in HomewareSC (Math/Category_Theory/fit/style_counts.py).
TestRLStylePrior : UnitTest {

	prChords { |scale, degrees| ^RLFit.events(degrees.collect { |d| scale.triads.chord(d) }, scale) }

	test_a_prior_from_a_model {
		var hand = RLStylePrior.hand, edo = RLEDO(12), c = RLScale.major(edo), chords = this.prChords(c, [0, 3, 4, 0]);
		this.assertEquals(hand.kinds, 9, "the hand-set model has nine kinds of chord");
		this.assertEquals(hand.tables, [\first, \step, \change], "and counts no rests");
		this.assertEquals(hand.formal[\kinds], "the degree of the root", "what it assumes, read from its switches when it does not say");
		this.assertEquals(RLStylePrior.hand((added: true)).formal[\sevenths], "tones added to the triad", "a switch changes what it says");
		this.assertEquals(hand.depends.size, 0, "it depends on nothing");
		this.assertFloatEquals(hand.fit(chords, RLRegions(edo)).cost, RLFit(chords, RLRegions(edo)).cost, "its fit is RLFit's");
		this.assertFloatEquals(RLFit(chords, RLRegions(edo), hand).cost, RLFit(chords, RLRegions(edo)).cost, "and RLFit takes a prior where it takes a model");
		this.assert(RLStylePrior(hand) === hand, "a prior of a prior is itself");
	}

	test_what_a_prior_says {
		var model = RLFit.handModel, prior;
		model[\prior] = IdentityDictionary[
			\name -> "the classical composers but mozart",
			\depends -> IdentityDictionary[\era -> "classical", \composer -> nil, \left_out -> "mozart"],
			\formal -> IdentityDictionary[\keys -> ["major", "minor"], \kinds -> "the key's triads"],
			\habits -> IdentityDictionary[\tables -> ["first", "step", "change"]],
			\not_yet -> ["metre"]
		];
		prior = RLStylePrior(model);
		this.assertEquals(prior.name, "the classical composers but mozart", "its name");
		this.assertEquals(prior.era, "classical", "the era it depends on");
		this.assertEquals(prior.composer, nil, "no composer");
		this.assertEquals(prior.formal[\kinds], "the key's triads", "its formal assumptions, as written");
		this.assertEquals(prior.habits[\tables], ["first", "step", "change"], "its counted habits, apart");
		this.assertEquals(prior.notYet, ["metre"], "and what it does not hold yet");
	}

	test_a_prior_of_modal_music {
		var plain = RLStylePrior.hand, modal = RLStylePrior.hand((modes: [\major, \minor, \dorian]));
		this.assertEquals(plain.modes, [\major, \minor], "a prior reads major and minor keys");
		this.assertEquals(plain.isModal, false, "and is not modal");
		this.assertEquals(modal.modes, [\major, \minor, \dorian], "or the modes it names");
		this.assert(modal.isModal, "then it is modal");
		this.assertEquals(modal.formal[\keys], ["major", "minor", "dorian"], "and says so among its formal assumptions");
		this.assert(modal.asString.contains("dorian"), "and in its name");
	}

	test_the_prior_that_describes_shortest {
		// two priors: one at home with steps a fourth up, one that finds them dear; a ring of fifths chooses the first
		var edo = RLEDO(12), c = RLScale.major(edo), chords = this.prChords(c, [0, 3, 6, 2, 5, 1, 4, 0]);
		var fond = RLStylePrior.hand((ascending: 0.1)), averse = RLStylePrior.hand((ascending: 4.0)), choice;
		choice = RLStylePrior.choose([averse, fond], chords, RLRegions(edo));
		this.assert(choice[0] === fond, "the prior under which the chords cost least");
		this.assertFloatEquals(choice[1].cost, fond.cost(chords, RLRegions(edo)), "with its fit");
		this.assert(fond.cost(chords, RLRegions(edo)) < averse.cost(chords, RLRegions(edo)), "it costs less under it");
	}
}
