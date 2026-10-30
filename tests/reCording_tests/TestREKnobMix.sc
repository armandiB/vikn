// REKnobMix: a tape value and a knob mixed into one parameter, the three states a button chooses,
// the holds and the linear convergence that leaves them, add and scale, without a clock.
TestREKnobMix : UnitTest {
	var savedRateLimit;

	setUp {
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown {
		RCLog.rateLimit = savedRateLimit;
	}

	logHas { |text| ^RCLog.history.any { |e| e[2].contains(text) } }

	test_add_both_live {
		var m = REKnobMix(\add, -1, 1);
		this.assertEquals(m.value(0), nil, "nothing arrived: nil");
		m.tape_(0.5, 0);
		this.assertFloatEquals(m.value(0), 0.5, "a knob never moved adds 0");
		m.knob_(0.75, 0);
		this.assertFloatEquals(m.value(0), 1.0, "T + lo + k·(hi - lo): 0.5 + (-1 + 1.5)");
		m.knob_(0.5, 0);
		this.assertFloatEquals(m.value(0), 0.5, "the middle of a symmetric range adds nothing");
		m.knob_(0, 0);
		this.assertFloatEquals(m.value(0), -0.5, "the bottom subtracts");
		this.assertEquals(m.moving(0), false, "nothing glides in state 1");
	}

	test_add_without_tape {
		var m = REKnobMix(\add, 0, 1);
		m.knob_(0.25, 0);
		this.assertFloatEquals(m.value(0), 0.25, "no tape value yet: T counts 0, the knob's offset alone");
	}

	test_scale {
		var m = REKnobMix(\scale, 0, 2);
		m.knob_(0.25, 0);
		this.assertEquals(m.value(0), nil, "scale without a tape value: nothing to send");
		m.tape_(0.4, 0);
		this.assertFloatEquals(m.value(0), 0.2, "T × (lo + k·(hi - lo)): 0.4 × 0.5");
		m = REKnobMix(\scale, 0, 2);
		m.tape_(0.4, 0);
		this.assertFloatEquals(m.value(0), 0.4, "a knob never moved scales by 1");
	}

	test_unknown_op_is_add {
		var m = REKnobMix(\pow, 0, 1);
		this.assertEquals(m.op, \add);
		this.assert(this.logHas("unknown op"), "said");
	}

	// state 2: the tape sampled and held when entered, the knob live; leaving it the tape converges
	// linearly from the held value to the live one over `glide`
	test_tape_hold_and_glide {
		var m = REKnobMix(\add, 0, 1, 1.0);
		m.tape_(0.2, 0);
		m.knob_(0.5, 0);
		this.assertFloatEquals(m.value(0), 0.7);
		m.state_(2, 1);
		this.assertEquals(m.state, 2);
		m.tape_(0.8, 1.5);
		this.assertFloatEquals(m.value(1.5), 0.7, "the tape held at 0.2: its new value changes nothing");
		m.knob_(0.0, 1.6);
		this.assertFloatEquals(m.value(1.6), 0.2, "the knob live");
		this.assertEquals(m.moving(1.6), false, "a hold is not a glide");
		m.state_(1, 2);
		this.assertEquals(m.moving(2), true, "leaving the hold: the tape glides");
		this.assertFloatEquals(m.value(2), 0.2, "at the start of the glide: the held value");
		this.assertFloatEquals(m.value(2.5), 0.5, "half way: half way between 0.2 and 0.8");
		this.assertFloatEquals(m.value(3), 0.8, "at the end: the live value");
		this.assertEquals(m.moving(3), true, "once more at the end, so that the live value goes out");
		this.assertEquals(m.moving(3.1), false, "then still");
		this.assertFloatEquals(m.value(3.1), 0.8);
		m.tape_(0.1, 4);
		this.assertFloatEquals(m.value(4), 0.1, "the tape followed live again");
	}

	// state 3: the knob held, the tape live; leaving it the knob's contribution converges
	test_knob_hold_and_glide {
		var m = REKnobMix(\add, 0, 1, 1.0);
		m.knob_(0.4, 0);
		m.tape_(0.1, 0);
		this.assertFloatEquals(m.value(0), 0.5);
		m.state_(3, 0);
		m.knob_(1.0, 0.5);
		this.assertFloatEquals(m.value(0.5), 0.5, "the knob held at 0.4");
		m.tape_(0.2, 0.6);
		this.assertFloatEquals(m.value(0.6), 0.6, "the tape live");
		m.state_(1, 1);
		this.assertFloatEquals(m.value(1), 0.6, "leaving: from the held contribution");
		this.assertFloatEquals(m.value(1.5), 0.9, "half way from 0.4 to 1.0: 0.7, plus the tape");
		this.assertFloatEquals(m.value(2), 1.2, "the live knob at the end");
		this.assert(m.moving(1.9));
		m.moving(2);
		this.assertEquals(m.moving(2.5), false);
	}

	test_hold_before_any_tape {
		var m = REKnobMix(\add, 0, 1, 1.0), s;
		m.state_(2, 0);
		m.knob_(0.5, 0);
		this.assertFloatEquals(m.value(0), 0.5, "nothing held: the knob alone");
		m.tape_(0.9, 1);
		this.assertFloatEquals(m.value(1), 0.5, "a tape value arriving under the hold stays out");
		m.state_(1, 2);
		this.assertEquals(m.moving(2), false, "nothing to converge from");
		this.assertFloatEquals(m.value(2), 1.4, "the live tape at once");
		s = REKnobMix(\scale, 0, 2, 1.0);
		s.state_(2, 0);
		s.knob_(0.5, 0);
		this.assertEquals(s.value(0), nil, "scale: no tape, nothing");
		s.tape_(0.5, 1);
		this.assertEquals(s.value(1), nil, "still held at nothing");
		s.state_(1, 2);
		this.assertFloatEquals(s.value(2), 0.5, "0.5 × 1.0 once left");
	}

	test_enter_hold_mid_glide {
		var m = REKnobMix(\add, 0, 1, 1.0);
		m.tape_(0.2, 0);
		m.state_(2, 0);
		m.tape_(0.8, 1);
		m.state_(1, 2);
		this.assertFloatEquals(m.value(2.5), 0.5);
		m.state_(2, 2.5);
		this.assertFloatEquals(m.value(3), 0.5, "held where the glide stood");
		this.assertEquals(m.moving(3), false, "the glide is over: the hold took it");
		m.tape_(0.0, 3.5);
		this.assertFloatEquals(m.value(4), 0.5, "held");
	}

	test_state_two_to_three {
		var m = REKnobMix(\add, 0, 1, 1.0);
		m.tape_(0.2, 0);
		m.knob_(0.4, 0);
		m.state_(2, 0);
		m.tape_(0.6, 0.5);
		m.state_(3, 1);
		this.assertEquals(m.state, 3);
		this.assertFloatEquals(m.value(1), 0.6, "the tape leaves its hold at 0.2 and the knob holds at 0.4");
		this.assertFloatEquals(m.value(1.5), 0.8, "the tape half way to 0.6, the knob held");
		m.knob_(1.0, 1.5);
		this.assertFloatEquals(m.value(2), 1.0, "the tape live (0.6), the knob still held (0.4)");
	}

	test_glide_zero_jumps {
		var m = REKnobMix(\add, 0, 1, 0);
		m.tape_(0.2, 0);
		m.state_(2, 0);
		m.tape_(0.9, 1);
		m.state_(1, 2);
		this.assertFloatEquals(m.value(2), 0.9, "no glide: the live value at once");
		this.assertEquals(m.moving(2), false);
	}

	test_same_state_and_unchanged_source_do_nothing {
		var m = REKnobMix(\add, 0, 1, 1.0);
		m.tape_(0.3, 0);
		m.state_(2, 0);
		m.state_(2, 1);
		this.assertEquals(m.state, 2, "set again: nothing");
		m.state_(1, 2);
		this.assertEquals(m.moving(2), false, "the live value is the held one: no glide to run");
		m.state_(7, 3);
		this.assertEquals(m.state, 1, "an unknown state is 1");
	}
}
