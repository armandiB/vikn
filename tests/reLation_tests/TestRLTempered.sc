// reLation, temperaments between the lattice and the equal divisions: RLTempered. No server.
// HomewareSC's Math/Category_Theory/PROGRESS.md, "Rank-2 temperaments".
TestRLTempered : UnitTest {
	var just, mt, pump, cycles;

	setUp {
		just = RLJust([3, 5]);
		mt = RLTempered.meantone;
		pump = [\R, \L, \R, \P, \R, \L, \R, \L];
		cycles = [[\P, \L] ! 3, [\P, \R] ! 4, [\R, \L] ! 12].collect(_.flat);
	}

	test_meantone_is_a_chain_of_fifths {
		var chain = Array.series(41, -20).collect { |k| mt.pcScale([1, 0], k) }, different = Array.new;
		chain.do { |x| if(different.includesEqual(x).not) { different = different.add(x) } };
		this.assertEquals([mt.interval([3, 2]), mt.interval([5, 4])], [[1, 0], [0, 1]], "the fifth and the major third, as on the lattice");
		this.assertEquals(mt.pcScale([1, 0], 4), [0, 1], "four fifths are a major third");
		this.assert(mt.tempers([81, 80]) and: { mt.tempers([128, 125]).not }, "the syntonic comma is nothing, the diesis is not");
		this.assertEquals(mt.interval([10, 9]), mt.interval([9, 8]), "the two whole tones are one: the mean tone");
		this.assertEquals([mt.rank, mt.isFinite, mt.size], [1, false, inf], "one direction left, no end");
		this.assertEquals(different.size, 41, "41 fifths in a row are 41 different pitch classes");
		this.assertEquals([4, 5, 8].collect { |k| mt.name(mt.pcScale([1, 0], k)) }, ["5/4", "15/8", "25/16"], "a pitch class reads as a ratio");
		this.assertEquals(mt.pitchAdd(mt.pitchInterval([3, 2]), mt.pitchInterval([3, 2])), mt.pitchInterval([9, 4]), "pitches add");
		this.assertEquals(mt.pc(mt.nearest([3, 0], 2.2)), [3, 0], "the pitch of a class nearest a height is of that class");
		this.assertFloatEquals(mt.height(mt.nearest([3, 0], 2.2)), 2.2, "within half an octave of it", 0.5);
	}

	test_tunings {
		var fifth = { |sys| sys.pcHeight([1, 0]) * 1200 }, ls = RLTempered.named(\meantone), comma = 1200 * ((4 * 3.log2) - 5.log2 - 4);
		this.assertFloatEquals(fifth.(mt), 696.578, "quarter-comma: a fifth of 696.58 cents", 0.001);
		this.assertFloatEquals(mt.pcHeight([0, 1]) * 1200, 1200 * 1.25.log2, "its major third pure", 1e-9);
		this.assertFloatEquals(RLTempered.meantone(1/3).pcHeight(mt.interval([6, 5])) * 1200, 1200 * 1.2.log2, "third-comma: the minor third pure", 1e-9);
		this.assertFloatEquals(fifth.(RLTempered.meantone(0)), 1200 * 1.5.log2, "no narrowing: pure fifths", 1e-9);
		this.assertFloatEquals(RLTempered.meantone(0).pcHeight([0, 1]) * 1200, 1200 * (81 / 64).log2, "and the third of four of them, 81/64", 1e-9);
		this.assertFloatEquals((1200 * 1.5.log2) - fifth.(ls), comma * 4 / 17, "nearest the just sizes: the fifth narrowed by 4/17 of the comma", 1e-9);
		this.assertArrayFloatEquals(RLTempered(just, [[81, 80]], [[5, 4]]).sizes, mt.sizes, "a pure major third asked for is quarter-comma", 1e-12);
		this.assertFloatEquals(fifth.(RLTempered(just, [[81, 80]], nil, [19 / 12, 28 / 12])), 700, "12 equal steps are a tuning of meantone", 1e-9);
		this.assertException({ RLTempered(just, [[81, 80]], nil, [3.log2, 5.log2]) }, Error, "the just sizes are refused: the comma is not nothing there");
		this.assertException({ RLTempered(just, [[81, 80]], [[5, 4], [3, 2]]) }, Error, "two pure intervals are one too many");
		this.assert((mt == RLTempered.meantone) and: { mt != RLTempered.meantone(1/3) } and: { mt.hash == RLTempered.meantone.hash },
			"a value: equal when the commas and the tuning are");
	}

	test_walks_in_meantone {
		var ctx = RLChordContext(RLChordSpace.triads(mt)), c = ctx.space.major, third = RLTempered.meantone(1/3);
		var drifts = cycles.collect { |w| RLWalk(ctx, c, w).drift };
		this.assert(RLWalk(ctx, c, pump).isClosed, "the pump closes, whatever the fifth");
		this.assertEquals(drifts, [[0, -3], [0, -3], [0, -3]], "the three cycles end on one and the same pitch class");
		this.assertFloatEquals(mt.cents([0, -3]), 41.059, "the diesis: 41.06 cents with pure major thirds", 0.001);
		this.assertFloatEquals(third.cents([0, -3]), 62.565, "62.57 with pure minor thirds", 0.001);
		this.assertEquals([mt.commaName([0, -3]), third.commaName([0, -3])], ["diesis 128/125", "major diesis 648/625"],
			"named after the just comma of its size in the tuning");
		this.assertEquals(mt.commaName([4, -1]), nil, "the comma tempered out has no name: it is nothing");
		this.assertEquals(RLWalk(ctx, c, pump).voices.last.collect { |p| mt.pc(p) }, [[0, 1], [1, 0], [0, 0]], "the pump's voices end one inversion higher");
		this.assert((ctx.t([1, 0]) <> ctx.p) == (ctx.p <> ctx.t([1, 0])), "transpositions commute with P, as everywhere");
		this.assertEquals(ctx.r.(ctx.l.(c)), ctx.space.major([1, 0]), "L then R: a fifth up");
	}

	test_between_the_lattice_and_the_divisions {
		var jc = RLChordContext(RLChordSpace.triads(just)), mc = RLChordContext(RLChordSpace.triads(mt)), up = RLMorphism.temper(jc, mc);
		var patch = (-3..3).dup(2).allTuples.collect { |x| [jc.space.major(x), jc.space.minor(x)] }.flatten(1);
		this.assert(up.isValid, "from the lattice to meantone: a morphism");
		[12, 19, 31].do { |n|
			var ec = RLChordContext(RLChordSpace.triads(RLEDO(n))), down = RLMorphism.temper(mc, ec), direct = RLMorphism.temper(jc, ec);
			this.assert(mt.supports(RLEDO(n)) and: { down.isValid }, "from meantone to % steps: a morphism".format(n));
			this.assert(patch.every { |x| down.(up.(x)) == direct.(x) }, "the val of % goes through meantone".format(n));
			this.assert(down.lift(RLWalk(ec, ec.space.major(0), pump)).isClosed, "the pump of % steps lifts to a closed walk of meantone".format(n));
		};
		this.assertEquals(RLMorphism.temper(mc, RLChordContext(RLChordSpace.triads(RLEDO(12)))).lift(
			RLWalk(RLChordContext(RLChordSpace.triads(RLEDO(12))), RLChordSpace.triads(RLEDO(12)).major(0), cycles[0])).drift, [0, -3],
			"the hexatonic cycle of 12 steps lifts to meantone a diesis away");
		this.assert(mt.supports(RLEDO(22)).not and: { mt.supports(RLEDO(34)).not }, "22 and 34 steps are no meantones");
		this.assertEquals(mt.divisions(34), [5, 7, 12, 19, 24, 26, 31], "the equal divisions up to 34 that are");
		this.assertException({ RLMorphism.temper(mc, RLChordContext(RLChordSpace.triads(RLEDO(22)))) }, Error, "no morphism from meantone to 22 steps");
		this.assertException({ mt.temperPc(RLEDO(22), [1, 0]) }, Error, "nor a map of its pitch classes");
	}

	test_two_commas_make_a_division {
		var twelve = RLTempered(just, [[81, 80], [128, 125]]), ctx = RLChordContext(RLChordSpace.triads(twelve)), c = ctx.space.major;
		this.assertEquals([twelve.isFinite, twelve.size, twelve.pcs.size, twelve.rank], [true, 12, 12, 0], "the syntonic comma and the diesis gone: twelve pitch classes");
		this.assertEquals(twelve.pcs.collect { |p| just.temperPc(RLEDO(12), p) }.sort, Array.series(12), "one for each step of 12");
		this.assertArrayFloatEquals(twelve.sizes * 1200, [1900, 2800], "its only tuning is the equal one", 1e-6);
		this.assertEquals(twelve.halves, [[2, 1]], "one tritone, written 45/32");
		this.assert(([pump] ++ cycles).every { |w| RLWalk(ctx, c, w).isClosed }, "the pump and the three cycles all close");
		this.assertEquals(ctx.center.size, 1, "the tritone is central");
		this.assert(ctx.t([2, 1]) == ((ctx.r <> ctx.p) <> (ctx.r <> ctx.p)), "T6 is R P R P, as in 12 steps");
		this.assertEquals(RLTempered(just, [[81, 80], [3125, 3072]]).size, 19, "the syntonic and the magic comma gone: 19");
		this.assertEquals(RLTempered(RLJust([3, 5, 7]), [[81, 80], [126, 125], [64, 63]]).size, 12, "three commas of the 7-limit: 12 again");
		this.assertEquals(RLTempered(RLJust([3, 5, 7]), [[81, 80], [126, 125], [225, 224]]).rank, 1,
			"the marvel comma is the syntonic comma over the starling comma: said as a third comma, it changes nothing");
		this.assertException({ RLTempered(just, [[81, 80], [81, 40]]) }, Error, "commas that would make the octave nothing are refused");
		this.assertEquals(RLTempered(just, [[81, 80], [6561, 6400]]).rank, 1, "a comma said twice counts once");
	}

	// which of the four tetrad walks close: the rows of the divisions' table (TestRLTetrads), explained
	test_seven_limit {
		var words = [[\K12, \K01, \K12, \K01, \K12, \K02, \K12, \K02, \K01, \K13], [\K12, \K01, \K12, \K01, \K13, \K01],
			[\K01, \K03, \K01, \K03], [\K12, \K01, \K12, \K01, \K02, \K12, \K01, \K13]];
		var closes = { |system|
			var ctx = RLChordContext(RLChordSpace.tetrads(system));
			words.collect { |w| RLWalk(ctx, ctx.space.otonal, w).isClosed }
		};
		var sm = RLTempered.named(\septimalMeantone), pajara = RLTempered.named(\pajara);
		this.assertEquals([sm.rank, sm.divisions(60)], [1, [12, 19, 31, 43, 50]], "septimal meantone: a chain, and its equal divisions");
		this.assertEquals(closes.(sm), [true, false, false, false], "it closes the marvel walk only");
		this.assertEquals(closes.(sm), closes.(RLEDO(31)), "as 31 steps do");
		this.assertEquals(closes.(pajara), [true, true, true, false], "pajara closes three");
		this.assertEquals(closes.(pajara), closes.(RLEDO(22)), "as 22 steps do");
		this.assert(pajara.supports(RLEDO(22)) and: { pajara.supports(RLEDO(12)) } and: { sm.supports(RLEDO(22)).not }, "22 is a pajara, 12 both, 22 no meantone");
		this.assertEquals([RLTempered.named(\marvel).rank, closes.(RLTempered.named(\marvel))], [2, [true, false, false, false]],
			"the marvel comma alone gone: two directions left, its walk closed");
	}

	test_other_temperaments {
		var dim = RLTempered.named(\diminished), aug = RLTempered.named(\augmented);
		this.assertEquals([dim.rank, dim.halves], [1, [[2, -2]]], "648/625 gone: four minor thirds are an octave, two of them a tritone");
		this.assertEquals(RLChordContext(RLChordSpace.triads(dim)).center.size, 1, "central in its context, though the temperament has no end");
		this.assertEquals(aug.halves, [], "128/125 gone: three major thirds are an octave, and no tritone");
		this.assertEquals(aug.pcScale([0, 1], 3), [0, 0], "three major thirds");
		this.assert(RLWalk(RLChordContext(RLChordSpace.triads(aug)), RLChordSpace.triads(aug).major, cycles[0]).isClosed, "the hexatonic cycle closes there");
		this.assertException({ RLTempered.named(\nothing) }, Error, "a name that is not known");
		this.assertEquals(RLTempered(just, []).interval([81, 80]), [4, -1], "no comma: the lattice itself");
	}
}
