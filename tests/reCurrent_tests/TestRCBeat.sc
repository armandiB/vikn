TestRCBeat : UnitTest {
	var clock, song, layer, savedRateLimit;

	setUp {
		clock = RCTestSupport.clock;
		RCTestSupport.bootSession;
		song = RCSong(\tb, 1994);
		layer = song.layer(\core);
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown {
		RCTestSupport.reset;
		RCLog.rateLimit = savedRateLimit;
	}

	pull { |beat, n|
		var s = beat.asStream;
		^n.collect { s.next(Event.default) }
	}

	logHas { |text|
		^RCLog.history.any { |entry| entry[2].contains(text) }
	}

	test_dur_pipeline_and_mirroring {
		var b = RCBeat(layer, \t, [type: \rest, dur_flex: [1, 0.5, Rest(0.5)], amp: Pseq([0.1, 0.2], inf)]);
		var evs = this.pull(b, 4);
		this.assertEquals(evs.collect { |e| e.dur.value }, [1, 0.5, 0.5, 1], "dur list loops");
		this.assert(evs[2].dur.isRest, "rests kept");
		this.assertEquals(evs.collect(_.amp), [0.1, 0.2, 0.1, 0.2], "attribute streamed in order");
		this.assertEquals(b.lastValue(\amp), 0.2, "last value mirrored");
		this.assertEquals(b.lastValue(\real_dur).value, 1, "dur list value mirrored under real_dur");
		this.assertEquals(b.lastValue(\nothing, 7), 7, "default for unknown key");
		this.assert(evs[0].rc_beat === b, "event carries the beat");
		this.assertEquals(b.keyOrder.asArray, [\type, \amp, \rc_finish].reject { |k| k == \rc_finish }, "user keys in declared order");
	}

	test_swing {
		var b, evs, expected, t = 0, prev = 0;
		layer.swing.amount = 0.2;
		layer.swing.mod = 2;
		b = RCBeat(layer, \s, [type: \rest, dur_flex: 0.5]);
		evs = this.pull(b, 4);
		expected = 4.collect {
			var sw;
			t = t + 0.5;
			sw = layer.swing.value(t);
			prev = sw - prev;
			sw = 0.5 + prev;
			prev = layer.swing.value(t);
			sw
		};
		4.do { |i| this.assertFloatEquals(evs[i].dur, expected[i], "swing offset applied to event %".format(i)) };
	}

	test_dur_clamp_and_watchdog {
		var b = RCBeat(layer, \z, [type: \rest, dur_flex: 0]);
		var evs = this.pull(b, RCBeat.maxClampedInARow + 2);
		this.assertEquals(evs[0].dur, RCBeat.minDur, "zero dur clamped");
		this.assertEquals(evs[RCBeat.maxClampedInARow - 1].dur, RCBeat.minDur, "still clamped before the cap");
		this.assertEquals(evs[RCBeat.maxClampedInARow], nil, "beat stops after too many clamped durations");
		this.assert(this.logHas("stopping the beat"), "stop reported");
	}

	test_empty_dur_list_does_not_spin {
		var b = RCBeat(layer, \e, [type: \rest, dur_flex: []]);
		var evs = this.pull(b, 3);
		this.assert(evs.every { |e| e.dur.isRest and: { e.dur.value == 1 } }, "empty list yields 1-beat rests");
		this.assert(this.logHas("empty dur list"), "warned");
	}

	test_function_dur_flex_yielding_nothing_does_not_spin {
		var b = RCBeat(layer, \f, [type: \rest, dur_flex: { Pseq([], 1) }]);
		var evs = this.pull(b, 2);
		this.assert(evs.every { |e| e.dur.isRest }, "rests instead of an infinite loop");
	}

	test_termination_key_ends_stream {
		var b = RCBeat(layer, \k, [type: \rest, dur_flex: 1, x: Pseq([1, 2])], terminationKey: \x);
		var evs = this.pull(b, 4);
		this.assertEquals(evs.collect { |e| e !? (_.x) }, [1, 2, nil, nil], "stream ends after the last value of the termination key");
		this.wait({ b.isFreed }, "beat freed itself", 2);
		this.assert(b.isFreed, "beat is freed");
	}

	test_error_policy_restart {
		var count = 0;
		var b = RCBeat(layer, \r, [type: \rest, dur_flex: 1, boom: Pfunc { count = count + 1; if(count == 2) { Error("boom").throw }; count }]);
		var evs;
		b.restartDelay = 0.25;
		evs = this.pull(b, 5);
		this.assertEquals(evs[0].boom, 1, "first event ok");
		this.assert(evs[1].isRest, "error replaced by a silent event");
		this.assertEquals(evs[1].delta, 0.25, "silent event lasts restartDelay");
		this.assertEquals(evs[2].boom, 3, "stream rebuilt and continues");
		this.assertEquals(evs[4].boom, 5, "keeps going");
		this.assert(this.logHas("pattern error"), "error reported");
	}

	test_error_policy_gives_up {
		var b = RCBeat(layer, \g, [type: \rest, dur_flex: 1, boom: Pfunc { Error("always").throw }]);
		var evs;
		b.maxRestarts = 2;
		evs = this.pull(b, 4);
		this.assert(evs[0].isRest and: { evs[1].isRest }, "two restart attempts");
		this.assertEquals(evs[2], nil, "then the beat stops");
		this.assert(this.logHas("stopped after 3 consecutive error(s)"), "give-up reported");
	}

	test_error_policy_stop {
		var b = RCBeat(layer, \st, [type: \rest, dur_flex: 1, boom: Pfunc { Error("x").throw }]);
		var evs;
		b.errorPolicy = \stop;
		evs = this.pull(b, 2);
		this.assertEquals(evs[0], nil, "stop policy ends the stream at the first error");
	}

	test_group_and_out_resolution {
		var b, evs;
		song.outArray = [10, 20];
		b = RCBeat(layer, \o, [type: \rest, dur_flex: 1, orgnsm_out_idx: 1]);
		evs = this.pull(b, 1);
		this.assertEquals(evs[0].out, 20, "out resolved from song.outArray");
		this.assertEquals(evs[0].orgnsm_out_idx, 1, "index key kept");
		b = RCBeat(layer, \o2, [type: \rest, dur_flex: 1, orgnsm_out_idx: 5, orgnsm_group_idx: 3]);
		evs = this.pull(b, 1);
		this.assertEquals(evs[0].out, 0, "invalid out index → 0");
		this.assert(evs[0].notNil and: { evs[0].keys.includes(\group).not }, "invalid group index → no group key, stream alive");
		this.assert(this.logHas("not in song.outArray"), "error reported");
	}

	test_midiOnCtl {
		var b = RCBeat(layer, \m, [type: \midi, dur_flex: 1, ctlNum: 1, midicmd: \noteOn]);
		var ev = this.pull(b, 1)[0];
		this.assertEquals(ev.type, \midiOnCtl, "type promoted");
		this.assertEquals(ev.midicmd, [\noteOn, \control], "midicmd extended");
	}

	test_seeds_reproducible {
		var a = RCBeat(layer, \sa, [type: \rest, dur_flex: 1, x: Pwhite(0, 100000)]);
		var b = RCBeat(layer, \sb, [type: \rest, dur_flex: 1, x: Pwhite(0, 100000)]);
		var c = RCBeat(layer, \sc, [type: \rest, dur_flex: 1, x: Pwhite(0, 100000)], seeds: (x: 7));
		var d = RCBeat(layer, \sd, [type: \rest, dur_flex: 1, x: Pwhite(0, 100000)], seeds: (x: 7));
		this.assertEquals(this.pull(a, 5).collect(_.x), this.pull(b, 5).collect(_.x), "song seed by default → identical streams");
		this.assertEquals(this.pull(c, 5).collect(_.x), this.pull(d, 5).collect(_.x), "explicit per-key seed");
		this.assert(this.pull(a, 5).collect(_.x) != this.pull(c, 5).collect(_.x), "different seeds differ");
		this.assert(c.thread(\x).notNil, "seeded key has its own thread");
	}

	test_set_next_event {
		var b = RCBeat(layer, \se, [type: \rest, dur_flex: 1, amp: 1, x: Pseq([1, 2, 3, 4], inf)]);
		var s = b.asStream;
		s.next(Event.default);
		b.set(\amp, 9, quant: nil);
		this.assertEquals(s.next(Event.default).amp, 9, "set with quant nil lands on the next event");
		this.assertEquals(s.next(Event.default).x, 3, "other keys not restarted");
		b.set(\dur, [0.5, 0.5]);
		this.assertEquals(s.next(Event.default).dur, 0.5, "dur edits land on the next event");
		this.assertEquals(b.durList.size, 2, "durList installed");
		b.set(\fresh, 42, quant: nil);
		this.assertEquals(s.next(Event.default).fresh, 42, "new key added (pattern restarts)");
		this.assert(this.logHas("added key"), "restart reported");
	}

	test_nil_attribute_is_skipped {
		var b = RCBeat(layer, \nl, [type: \rest, dur_flex: 1, sustain: nil, x: 3]);
		var evs = this.pull(b, 2);
		this.assertEquals(evs.collect(_.x), [3, 3], "a nil attribute does not end the pattern");
		this.assertEquals(b.keyProxy(\sustain), nil, "nil attribute not declared");
	}

	test_reserved_key_warning {
		RCBeat(layer, \rk, [type: \rest, dur_flex: 1, release: 1]);
		this.assert(this.logHas("shadows a method"), "reserved attribute name warned");
	}

	test_addBeat_plays_and_frees {
		var b = layer.addBeat(\p, [type: \rest, dur_flex: 0.25, amp: Pseq([1, 2, 3], inf)], post: false);
		this.assert(b.isPlaying, "playing");
		this.assert(layer.beat(\p) === b, "registered");
		this.wait({ b.lastValue(\amp).notNil }, "events flow on the clock", 3);
		this.assert(b.lastValue(\amp).notNil, "value mirrored while playing");
		b.free(post: false);
		this.assert(b.isPlaying.not, "stopped");
		this.assertEquals(layer.beat(\p), nil, "unregistered");
	}

	test_addBeat_same_name_replaces {
		var a = layer.addBeat(\dup, [type: \rest, dur_flex: 1], post: false);
		var b = layer.addBeat(\dup, [type: \rest, dur_flex: 1], post: false);
		this.assert(a.isFreed, "previous beat freed");
		this.assert(layer.beat(\dup) === b, "new beat registered");
		b.free(post: false);
	}

	test_beatSpec {
		var spec = RCBeatSpec(layer, \sp, [type: \rest, dur_flex: 1]);
		var b = spec.value;
		this.assert(b.isKindOf(RCBeat) and: { b.isPlaying }, "spec starts a beat");
		spec.kill;
		this.assert(b.isFreed, "spec.kill frees it");
	}

	test_auxPattern {
		var pat = RCBeat.auxPattern(layer, \aux, \x, { |ev| [type: \rest, dur_flex: 1, x: Pseq([1])] });
		var s = pat.asStream;
		var name = s.next(());
		this.assert(name.asString.beginsWith("aux_"), "aux beat named with a counter");
		this.assert(layer.beat(name).notNil, "aux beat created and playing");
		this.assertEquals(layer.beat(name).tag, 'beat tb/core/aux_aux', "aux beats log under one shared tag");
		this.assertEquals(layer.beat(name).playQuant, [0, 0], "aux beat starts at once");
		layer.killAll;
	}

	test_auxPattern_refuses_endless_and_excess_beats {
		var saved = RCBeat.maxAuxBeatsPerLayer;
		var endless = RCBeat.auxPattern(layer, \nope, \x, { |ev| [type: \rest, dur_flex: 1] }).asStream;
		var ok = RCBeat.auxPattern(layer, \cap, \x, { |ev| [type: \rest, dur_flex: 1, x: Pseq([1])] }).asStream;
		this.assertEquals(endless.next(()), \skipped, "a dict without the termination key is refused");
		this.assert(this.logHas("would never end"), "refusal reported");
		this.assertEquals(layer.beats.size, 0, "nothing created");
		RCBeat.maxAuxBeatsPerLayer = 1;
		this.assert(ok.next(()) != \skipped, "first aux beat created");
		this.assertEquals(ok.next(()), \skipped, "above the cap nothing is spawned");
		this.assert(this.logHas("live beats in the layer"), "cap reported");
		RCBeat.maxAuxBeatsPerLayer = saved;
		layer.killAll;
	}

	test_play_now {
		var b = RCBeat(layer, \now, [type: \rest, dur_flex: 0.25]);
		var q0 = RCBeat(layer, \q0, [type: \rest, quant: 0]);
		b.play(0);
		this.assert(b.isPlaying, "quant 0 plays");
		this.assert(this.logHas("invalid quant").not, "quant 0 is valid");
		this.assertEquals(q0.playQuant, [0, 0], "quant 0 kept");
		this.assertEquals(this.pull(q0, 1)[0].dur, 1, "a quant of 0 still gives a positive initial dur");
		b.set(\quant, -1);
		this.assert(this.logHas("invalid quant"), "negative quant rejected");
		b.free(post: false);
	}

	test_function_attribute_is_called_per_event {
		var count = 0;
		var b = RCBeat(layer, \fn, [type: \rest, dur_flex: 1, x: { count = count + 1 }]);
		this.assertEquals(this.pull(b, 3).collect(_.x), [1, 2, 3], "a Function value is evaluated at every event");
		this.assertEquals(b.lastValue(\x), 3, "and mirrored");
	}

	test_live_key_stays_before_the_finish_clamp {
		var b = RCBeat(layer, \lk, [type: \rest, dur_flex: 1]);
		var s = b.asStream;
		s.next(Event.default);
		b.set(\sustain, -1, quant: nil);
		this.assertEquals(RCUtil.kvKeys(b.pbindProxy.pairs).last, \rc_finish, "rc_finish moved back to the end");
		this.assertEquals(s.next(Event.default).sustain, 0, "the clamp still runs after a key added live");
	}

	test_reserved_keys_hold_plain_values {
		var b = RCBeat(layer, \rv, [type: \rest, dur_flex: 1]);
		b.reserveKeys([\db, \stretch, \amp, \my_param]);
		this.assertEquals(RCBeat.reservedValue(\db), -20.0, "numeric Event default");
		this.assertEquals(RCBeat.reservedValue(\stretch), 1.0, "stretch default");
		this.assertEquals(RCBeat.reservedValue(\amp), 0, "a Function default → 0");
		this.assertEquals(this.pull(b, 1)[0][\my_param], 0, "an unknown key is reserved with 0, not a Symbol");
		this.assert(b.keyProxy(\my_param).notNil, "key declared");
	}

	test_duplicate_key_keeps_the_later_definition {
		var b = RCBeat(layer, \dk, [type: \rest, dur_flex: 1, chan: 5], chan: 2);
		var s = b.asStream;
		this.assertEquals(s.next(Event.default).chan, 5, "the attribute chan wins over the beat's own");
		this.assert(this.logHas("given twice"), "duplicate reported");
		b.set(\chan, 7, quant: nil);
		this.assertEquals(s.next(Event.default).chan, 7, "set edits the live key");
	}

	test_restart_advances_the_swing_phase {
		var count = 0;
		var b = RCBeat(layer, \sw, [type: \rest, dur_flex: 1, boom: Pfunc { count = count + 1; if(count == 2) { Error("boom").throw }; count }]);
		b.restartDelay = 0.25;
		this.pull(b, 2);
		this.assertFloatEquals(b.timeTrack, 2.25, "the silent restart gap counts in the swing time track");
	}

	test_asStream_warns_while_playing {
		var b = layer.addBeat(\as, [type: \rest, dur_flex: 1], post: false);
		b.asStream;
		this.assert(this.logHas("asStream on a playing beat"), "warned");
		b.free(post: false);
	}
}
