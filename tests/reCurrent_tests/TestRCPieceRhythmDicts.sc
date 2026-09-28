// The rhythm-dict idioms of the Biomusic clones (HomewareSC/Personal_SC/Biomusic_clone), as
// contracts on RCSubseqLibrary and RCRhythmDict. The fixture is shaped like Common/Rc_SubseqLibrary
// and the HadronLake3 / WeMadeATrack dicts; nothing is read from HomewareSC. A change in the
// library that fails a test here breaks a piece.
TestRCPieceRhythmDicts : UnitTest {
	var savedRateLimit;

	setUp {
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown { RCLog.rateLimit = savedRateLimit }

	logHas { |text| ^RCLog.history.any { |e| e[2].contains(text) } }

	// Rc_SubseqLibrary in miniature: claves over 16 pulses, percs with library shifts (hats half a
	// beat late, snares on 2 and 4), textures computed at definition time
	library {
		var accel = { var durs = (1..9).collect { |n| 1 / n.squared }; [\accelerate_9_dur2, 2 - durs.sum, durs] }.value;
		^RCSubseqLibrary.newFrom((
			clave_44: (son: ('1': [\son, 0, [3, 3, 4, 2, 4]]), rumba: ('1': [\rumba, 0, [3, 4, 3, 2, 4]])),
			percs: (
				kick: (fourfour: [\fourfour, 0, [1, 1, 1, 1]], dnb_1: [\dnb_1, 0, [5/3, 7/3]]),
				hh: (fourfour: [\fourfour, 0.5, [1, 1, 1, 1]]),
				snare: (fourfour: [\fourfour, 1, [2, 2]])
			),
			textures: (
				accelerate: (one_on_n_squared_9_dur2: accel),
				rhythmic: (thuemorse_b2_dur6: [\thuemorse_b2_dur6, 0, 8.collect { |i| [1, 1/2].at(RCSpherePath.thueMorse(i, 2)) }])
			)
		))
	}

	// HadronLake3 Rhythms: dicts _0 → _1 → _end sharing the keys of the path name \rhythms
	dicts {
		var lib = this.library;
		var d0 = RCRhythmDict(lib), d1, dEnd, d;
		d = d0.at(\rhythms);
		d[\percs_t1] = 4.collect { |n| [1, 4 * n, "percs.kick.fourfour", true, (perc: \kick, amp_unadj: 1)] }
			++ 4.collect { |n| [1, 4 * n, "percs.hh.fourfour", true, (perc: \hh, amp_unadj: 0.25)] };
		d[\percs_t2] = 4.collect { |n| [1, 4 * n, "percs.snare.fourfour", true, (perc: \snare, amp_unadj: 0.6)] };
		d[\percs_t3] = [[1, 0, "clave_44.son.1", true, (perc: \kick, amp_unadj: 0.5, freqmult: [1, 3/2, 1, 5/4, 1])]];
		d1 = d0.clone;
		d = d1.at(\rhythms);
		d[\percs_t1] = 8.collect { |n| [1, 4 * n, "percs.kick.dnb_1", true, (perc: \kick, amp_unadj: 1, freqmult: [1, 3/4]), nil, nil, 0.5] };
		d[\percs_t2] = [[1, 0, "percs.snare.fourfour", true, (perc: \snare, amp_unadj: 0.6), nil, nil, 2]];
		d[\percs_t3] = [[1, 0, "clave_44.rumba.1", [true, false, true, true, false], (perc: \kick, amp_unadj: 0.5, freqmult: [1, 3/2, 1, 5/4, 1])]];
		dEnd = d0.clone;
		d = dEnd.at(\rhythms);
		[\percs_t1, \percs_t2, \percs_t3].do { |key| d[key] = d[key].collect { |entry| var e = entry.copy; e[3] = false; e } };
		^(d0: d0, d1: d1, dEnd: dEnd)
	}

	// onsets of every hit of a resolved rhythm, in beats from the loop start
	onsets { |subseqs| ^subseqs.collect { |s| s.shift + ([0] ++ s.durs.integrate.drop(-1)) }.flatten }

	test_computed_library_entries {
		var lib = this.library;
		var accel = lib.at("textures.accelerate.one_on_n_squared_9_dur2");
		var tm = lib.at("textures.rhythmic.thuemorse_b2_dur6");
		this.assertEquals(lib.names.size, 8, "every entry enumerated");
		this.assertEquals(accel[0], \accelerate_9_dur2, "an entry computed by a function keeps the [name, shift, durs] shape");
		this.assertEquals(accel[2].size, 9, "nine accelerating hits");
		this.assertFloatEquals(accel[1] + accel[2].sum, 2, "the shift fills the loop: the accelerando ends on its total");
		this.assertEquals(tm[2], [1, 0.5, 0.5, 1, 0.5, 1, 1, 0.5], "Thue–Morse durations");
		this.assertEquals(tm[1] + tm[2].sum, 6, "the _dur6 name is the total");
		this.assertEquals(lib.sizeOf("clave_44.son.1"), 5, "a clave has five hits");
		this.assertEquals(lib.at("clave_44.son.1")[2].sum, 16, "over sixteen pulses");
	}

	test_progression_shares_keys_and_clones_are_independent {
		var dicts = this.dicts;
		var keys = [\percs_t1, \percs_t2, \percs_t3];
		this.assert([dicts.d0, dicts.d1, dicts.dEnd].every { |d| d.at(\rhythms).keys.asArray.sort == keys }, "every dict of the progression answers the same keys");
		this.assert(dicts.d1.library === dicts.d0.library, "clones share the library");
		this.assertEquals(dicts.d0.subseqs(\rhythms, \percs_t1).size, 8, "editing the clone's t1 left the original's eight entries");
		this.assertEquals(dicts.d1.at(\rhythms)[\percs_t3][0][3], [true, false, true, true, false], "the clone holds its own t3");
		this.assertEquals(dicts.d0.at(\rhythms)[\percs_t3][0][3], true, "the original's t3 is untouched");
		this.assert(dicts.dEnd.at(\rhythms)[\percs_t2] != dicts.d0.at(\rhythms)[\percs_t2], "an entry edited in place in the clone (mask false) differs from the original's");
		this.assertEquals(dicts.d0.at(\rhythms)[\percs_t2][0][3], true, "editing a clone's entry array in place never reaches the original (deep copy)");
	}

	test_shifted_repeats_cover_the_loop {
		var dicts = this.dicts;
		var s = dicts.d0.subseqs(\rhythms, \percs_t1);
		var kicks = s.select { |x| x.name == "percs.kick.fourfour" };
		var hats = s.select { |x| x.name == "percs.hh.fourfour" };
		var onsets = this.onsets(s);
		this.assertEquals(s.size, 8, "eight subseqs: four kick bars, four hat bars");
		this.assertEquals(kicks.collect(_.shift), [0, 4, 8, 12], "the entry shifts place one bar after the other");
		this.assertEquals(hats.collect(_.shift), [0.5, 4.5, 8.5, 12.5], "the library shift of the hats is added to each");
		this.assertEquals(kicks.collect { |k| k.params[\perc] }, (\kick ! 4) ! 4, "a scalar param is expanded to one value per hit");
		this.assertEquals(hats[0].params[\amp_unadj], 0.25 ! 4, "each subseq carries its own params");
		this.assertEquals(onsets.size, 32, "thirty-two hits");
		this.assert(onsets.every { |o| o >= 0 and: { o < 16 } }, "all inside the 16-beat loop");
		this.assertEquals(onsets.asSet.size, 32, "kicks on the beats, hats off the beats: no two hits coincide");
	}

	test_time_mult_slot {
		var dicts = this.dicts;
		var kicks = dicts.d1.subseqs(\rhythms, \percs_t1);
		var snare = dicts.d1.subseqs(\rhythms, \percs_t2)[0];
		this.assertEquals(kicks.size, 8, "eight half-time bars");
		this.assertArrayFloatEquals(kicks[0].durs, [5/6, 7/6], "durations scaled by the time mult 0.5");
		this.assertEquals(kicks.collect(_.shift), (0..7) * 2, "shifts scaled too: eight bars of two beats fill the loop");
		this.assertEquals(kicks[0].timeMult, 0.5, "the time mult is recorded");
		this.assertEquals(kicks[0].params[\freqmult], [1, 3/4], "an array param with one value per hit is kept");
		this.assertEquals(kicks[0].params[\perc], [\kick, \kick], "a scalar one is expanded to the hits");
		this.assertEquals(snare.shift, 2, "(library shift 1 + entry shift 0) * time mult 2");
		this.assertEquals(snare.durs, [4, 4], "durations doubled");
		this.assert(this.logHas("values for").not, "no param-length mismatch reported");
	}

	test_array_mask_and_per_hit_params {
		var dicts = this.dicts;
		var clave = dicts.d1.subseqs(\rhythms, \percs_t3)[0];
		this.assertEquals(clave.name, "clave_44.rumba.1", "the dotted path names the subseq");
		this.assertEquals(clave.numHits, 5, "five hits");
		this.assertEquals(clave.mask, [true, false, true, true, false], "an array mask is kept as written");
		this.assertEquals(clave.params[\freqmult], [1, 3/2, 1, 5/4, 1], "per-hit tuning kept");
		this.assertEquals(clave.params[\amp_unadj], 0.5 ! 5, "scalar amp expanded");
		this.assertEquals(clave.params.keys.asArray.sort, [\amp_unadj, \freqmult, \perc], "the params are the entry's keys");
		this.assert(this.logHas("values for").not, "a five-value param on five hits is fine");
	}

	test_end_dict_masks_every_hit {
		var dicts = this.dicts;
		[\percs_t1, \percs_t2, \percs_t3].do { |key|
			var original = dicts.d0.subseqs(\rhythms, key);
			var ended = dicts.dEnd.subseqs(\rhythms, key);
			this.assertEquals(ended.size, original.size, "% keeps every subseq".format(key));
			this.assertEquals(ended.collect(_.name), original.collect(_.name), "% keeps the names (crawlers match by name)".format(key));
			this.assertEquals(ended.collect(_.shift), original.collect(_.shift), "% keeps the shifts".format(key));
			this.assert(ended.every { |s| s.mask.size == s.numHits and: { s.mask.every { |b| b == false } } }, "% has every hit masked out".format(key));
		};
	}

	test_array_batch_keys_as_in_wemadeatrack {
		var rd = RCRhythmDict(this.library);
		var d = rd.at(\batch_handshake_t0);
		7.do { |j| d[[0, j]] = [[1, j, "clave_44.son.1", true]]; d[[1, j]] = [[1, j, "clave_44.rumba.1", 5.collect { |n| n != j }]] };
		this.assert(rd.includes(\batch_handshake_t0, [0, 3]), "an Array key is found by equality");
		this.assertEquals(rd.subseqs(\batch_handshake_t0, [0, 3])[0].shift, 3, "and resolved");
		this.assertEquals(rd.subseqs(\batch_handshake_t0, [1, 2])[0].mask, [true, true, false, true, true], "a computed mask per key");
		this.assertEquals(rd.clone.subseqs(\batch_handshake_t0, [1, 6]).size, 1, "a clone keeps Array keys");
		this.assertEquals(rd.subseqs(\batch_handshake_t0, [0, 7]), [], "a missing key gives no subseqs");
		this.assert(this.logHas("no rhythm entry"), "and is reported");
	}
}
