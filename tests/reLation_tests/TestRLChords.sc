// reLation, chords and what moves them: RLChordSpace, RLChord, RLChordContext, RLTransform. No server.
// Against the neo-Riemannian tables of 12 steps, opycleid's README, and THEORY.md § 1.1 and § 3.1.
TestRLChordSpace : UnitTest {

	test_triads_of_three_systems {
		var just = RLChordSpace.triads(RLJust([3, 5]));
		this.assertEquals(RLChordSpace.triads(RLEDO(12)).types, [[\major, [0, 4, 7]], [\minor, [0, 3, 7]]], "12 steps");
		this.assertEquals(RLChordSpace.triads(RLEDO(19)).types, [[\major, [0, 6, 11]], [\minor, [0, 5, 11]]], "19 steps");
		this.assertEquals(just.pattern(\major), [[0, 0], [0, 1], [1, 0]], "just: 1/1, 5/4, 3/2");
		this.assertEquals(just.pattern(\minor), [[0, 0], [1, -1], [1, 0]], "just: 1/1, 6/5, 3/2");
	}

	test_chords_of_a_space {
		var space = RLChordSpace.triads(RLEDO(12));
		this.assertEquals(space.all.size, 24, "24 triads in 12");
		this.assertEquals(RLChordSpace.triads(RLEDO(19)).all.size, 38, "38 in 19");
		this.assertEquals(space.major(0).pcs, [0, 4, 7], "C major's tones");
		this.assertEquals(space.minor(9).pcs, [9, 0, 4], "A minor's, root first");
		this.assertEquals(space.find([9, 0, 4]), space.minor(9), "a chord found by its tones");
		this.assertEquals(space.find([4, 9, 0]), space.minor(9), "in any order");
		this.assertEquals(space.find([0, 4, 8]), nil, "the augmented triad is not in this space");
		this.assertEquals(space.major(0).commonPcs(space.minor(9)), [0, 4], "C major and A minor share two tones");
		this.assertException({ space.chord(0, \diminished) }, Error, "an unknown type is refused");
	}

	test_a_chord_is_a_value {
		var a = RLChordSpace.triads(RLEDO(12)), b = RLChordSpace.triads(RLEDO(12)), set = Set.new;
		this.assert(a.major(0) == b.major(0), "equal across equal spaces");
		this.assert(a.major(0) != a.minor(0), "another type");
		this.assert(a.major(0) != RLChordSpace.triads(RLEDO(19)).major(0), "another system");
		this.assertEquals(a.chord(12, \major), a.major(0), "the root is reduced to a pitch class");
		set.add(a.major(0)); set.add(b.major(0)); set.add(a.minor(0));
		this.assertEquals(set.size, 2, "usable as keys");
		this.assertEquals(a.major(0).name, "C major", "named in 12");
		this.assertEquals(RLChordSpace.triads(RLJust([3, 5])).minor([-1, 1]).name, "5/3 minor", "and on the lattice");
	}
}

TestRLChordContext : UnitTest {
	var ctx, space;

	setUp {
		space = RLChordSpace.triads(RLEDO(12));
		ctx = RLChordContext(space);
	}

	test_p_l_r_in_twelve {
		var ok = true;
		12.do { |n|
			ok = ok and: { ctx.p.(space.major(n)) == space.minor(n) } and: { ctx.p.(space.minor(n)) == space.major(n) }
				and: { ctx.l.(space.major(n)) == space.minor(n + 4) } and: { ctx.l.(space.minor(n)) == space.major(n + 8) }
				and: { ctx.r.(space.major(n)) == space.minor(n + 9) } and: { ctx.r.(space.minor(n)) == space.major(n + 3) };
		};
		this.assert(ok, "P, L, R on the 24 triads");
		this.assertEquals(ctx.l.(space.major(2)), space.minor(6), "L of D major is F sharp minor (opycleid)");
	}

	test_t_and_i_move_the_pitch_classes {
		var ok = true;
		[RLEDO(12), RLEDO(19)].do { |edo|
			var sp = RLChordSpace.triads(edo), cx = RLChordContext(sp);
			sp.all.do { |chord|
				edo.steps.do { |k|
					ok = ok and: { cx.t(k).(chord).hasPcs(chord.pcs.collect { |x| edo.pcAdd(x, k) }) }
						and: { cx.i(k).(chord).hasPcs(chord.pcs.collect { |x| edo.pcSub(k, x) }) };
				};
			};
		};
		this.assert(ok, "T_k is x + k and I_k is k - x on every chord, in 12 and in 19");
	}

	test_opycleid_readme {
		var c = space.major(0);
		this.assertEquals(ctx.leftInterval(c, space.minor(11)), ctx.i(6), "T/I: C major to B minor is I6");
		this.assertEquals(ctx.leftInterval(c, space.minor(11)).label, "I6", "and says so");
		this.assertEquals(ctx.t(9) <> ctx.i(6), ctx.i(3), "I6 then T9 is I3");
		this.assertEquals(ctx.rightInterval(space.minor(11), space.major(7)), ctx.l, "PRL: B minor to G major is L");
		this.assertEquals(ctx.rightInterval(space.minor(11), space.major(7)).label, "L", "and says so");
	}

	test_two_common_tones {
		var ok = true;
		[RLEDO(12), RLEDO(19), RLEDO(31), RLEDO(53)].do { |edo|
			var cx = RLChordContext(RLChordSpace.triads(edo));
			cx.space.all.do { |chord|
				cx.generators.do { |g| ok = ok and: { chord.commonPcs(cx.transform(g).(chord)).size == 2 } };
			};
		};
		this.assert(ok, "P, L and R each keep two tones, in 12, 19, 31 and 53");
	}

	test_duality_and_simple_transitivity {
		var commute = true, simple = true, all = space.all;
		all.do { |chord|
			12.do { |k|
				[ctx.p, ctx.l, ctx.r].do { |right|
					commute = commute and: { ctx.t(k).(right.(chord)) == right.(ctx.t(k).(chord)) }
						and: { ctx.i(k).(right.(chord)) == right.(ctx.i(k).(chord)) };
				};
			};
			all.do { |other|
				simple = simple and: { ctx.leftInterval(chord, other).(chord) == other }
					and: { ctx.rightInterval(chord, other).(chord) == other };
			};
		};
		this.assert(commute, "every T and I commutes with P, L and R on the 24 triads");
		this.assert(simple, "one left and one right transformation for each of the 576 pairs");
		this.assert((ctx.t(5) <> ctx.r) == (ctx.r <> ctx.t(5)), "as transformations too");
	}

	test_composition_reads_right_to_left {
		var c = space.major(0);
		this.assertEquals((ctx.l <> ctx.p).(c), space.major(8), "LP: P first, then L, C major to A flat major");
		this.assertEquals((ctx.p <> ctx.l).(c), space.major(4), "PL: L first, C major to E major");
		this.assertEquals((ctx.l <> ctx.p).label, "LP", "labels compose");
		this.assert(((ctx.t(5) <> ctx.r).inverse <> (ctx.t(5) <> ctx.r)).isIdentity, "an inverse undoes");
		this.assertEquals(ctx.l.repeated(2).isIdentity, true, "L is an involution");
		this.assertEquals(ctx.act(\R, c), [space.minor(9)], "act answers a list");
		this.assertEquals(ctx.neighbours(c), [[\P, space.minor(0)], [\L, space.minor(4)], [\R, space.minor(9)]], "the neighbours of C major");
	}

	test_relations_between_p_l_r {
		var just = RLChordContext(RLChordSpace.triads(RLJust([3, 5])));
		var hexPole = ctx.l <> ctx.p <> ctx.l;
		this.assert((ctx.p <> ctx.l <> ctx.r).repeated(2).isIdentity, "(PLR)^2 = 1 in 12");
		this.assert((just.p <> just.l <> just.r).repeated(2).isIdentity, "and on the just lattice");
		this.assertEquals(hexPole, ctx.p <> ctx.l <> ctx.p, "LPL = PLP in 12: the hexatonic pole");
		this.assertEquals(hexPole.(space.major(0)), space.minor(8), "C major to A flat minor");
		this.assert((just.l <> just.p <> just.l) != (just.p <> just.l <> just.p), "not on the lattice: the hexatonic cycle does not close");
		this.assert((ctx.l <> ctx.p).repeated(3).isIdentity, "(LP)^3 = 1 in 12");
		this.assert((just.l <> just.p).repeated(3).isIdentity.not, "not on the lattice");
	}

	// T6 is central: the same transformation on the left and on the right.
	test_equality_is_about_what_is_done {
		var rp2 = (ctx.r <> ctx.p).repeated(2), set = Set.new;
		this.assert(rp2 == ctx.t(6), "(RP)^2 is T6");
		this.assertEquals(rp2.hash, ctx.t(6).hash, "with the same hash");
		set.add(rp2); set.add(ctx.t(6)); set.add(ctx.t(5));
		this.assertEquals(set.size, 2, "one key for both");
		this.assert(ctx.t(6).isRight and: { ctx.t(6).isLeft }, "T6 is on both sides");
		this.assert(ctx.t(5).isLeft and: { ctx.t(5).isRight.not }, "T5 is on the left only");
		this.assert(ctx.l.isRight and: { ctx.l.isLeft.not }, "L on the right only");
		this.assert((ctx.t(6) <> rp2).isIdentity, "T6 twice is nothing, however it is written");
		this.assert(RLChordContext(RLChordSpace.triads(RLEDO(19))).center.isEmpty, "19 steps have no such transposition");
	}

	test_named_transformations {
		var n = ctx.define(\N, [\R, \L, \P]);
		this.assertEquals(n.(space.major(0)), space.minor(5), "N, the word R then L then P: C major to F minor");
		this.assert(n.repeated(2).isIdentity, "an involution");
		this.assertEquals(ctx.transform(\N), n, "kept under its name");
		this.assertEquals(ctx.rightInterval(space.major(0), space.minor(5)).label, "N", "and recognised");
		this.assertException({ ctx.transform(\Q) }, Error, "an unknown name is refused");
	}

	test_refusals {
		var three = RLChordSpace(RLEDO(12), [[\major, [0, 4, 7]], [\minor, [0, 3, 7]], [\augmented, [0, 4, 8]]]);
		var notInverse = RLChordSpace(RLEDO(12), [[\major, [0, 4, 7]], [\sus, [0, 5, 7]]]);
		this.assertException({ RLChordContext(three) }, Error, "three types are refused");
		this.assertException({ RLChordContext(notInverse) }, Error, "a second type that is not the inversion of the first too");
	}
}
