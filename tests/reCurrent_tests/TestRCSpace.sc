TestRCSpace : UnitTest {
	var clock, song, savedRateLimit;

	setUp {
		clock = RCTestSupport.clock;
		RCTestSupport.bootSession;
		song = RCSong(\sp, 1);
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown {
		RCTestSupport.reset;
		RCLog.rateLimit = savedRateLimit;
	}

	closeTo { |a, b, tol = 1e-6|
		^a.flat.collect { |x, i| (x - b.flat[i]).abs < tol }.every { |x| x }
	}

	test_matrix_basics {
		var m = [[1, 2], [3, 4]];
		this.assertEquals(RCMatrix.identity(2), [[1.0, 0.0], [0.0, 1.0]], "identity");
		this.assertEquals(RCMatrix.diagonal([2, 3]), [[2, 0.0], [0.0, 3]], "diagonal");
		this.assertEquals(RCMatrix.product(m, RCMatrix.identity(2)), [[1, 2], [3, 4]], "product with identity");
		this.assertEquals(RCMatrix.product(m, m), [[7, 10], [15, 22]], "product");
		this.assertFloatEquals(RCMatrix.frobeniusNorm([[3, 4]]), 5, "frobenius norm");
		this.assertFloatEquals(RCMatrix.safeReciprocal(4), 0.25, "reciprocal");
		this.assertEquals(RCMatrix.safeReciprocal(0), 0, "reciprocal of zero is zero, not inf");
	}

	test_inverses {
		var m = [[4, 7], [2, 6]];
		var inv = [[0.6, -0.7], [-0.2, 0.4]];
		var spd = [[4, 2], [2, 3]];
		var spdInv = [[0.375, -0.25], [-0.25, 0.5]];
		this.assert(this.closeTo(RCMatrix.invertGJ(m), inv), "Gauss-Jordan");
		this.assert(this.closeTo(RCMatrix.invertLU(m), inv), "LU");
		this.assert(this.closeTo(RCMatrix.invertCholesky(spd), spdInv), "Cholesky");
		this.assert(this.closeTo(RCMatrix.invertLDL(spd), spdInv), "LDL");
		this.assert(this.closeTo(RCMatrix.invertCholesky([[4]]), [[0.25]]), "1x1");
	}

	test_singular_gives_zeros_not_nan {
		var singular = [[1, 2], [2, 4]];
		[RCMatrix.invertGJ(singular), RCMatrix.invertLU(singular), RCMatrix.invertCholesky(singular), RCMatrix.invertLDL(singular)].do { |res, i|
			this.assert(RCGuard.isFinite(res), "inverse % of a singular matrix is finite".format(i));
		};
		this.assert(RCGuard.isFinite(RCMatrix.invertCholesky([[-1, 0], [0, 1]])), "not positive definite → finite");
	}

	test_rotations {
		var v = [1, 0, 0], o = [0, 1, 0];
		var g = RCMatrix.givens(v, o);
		var h = RCMatrix.householder(v, o);
		var rotated = RCMatrix.product(g, v.flop.flop).flat;
		var reflected = RCMatrix.product(h, v.flop.flop).flat;
		this.assert(this.closeTo(rotated, o), "givens rotates the vector onto the origin direction");
		this.assert(this.closeTo(reflected, o), "householder maps the vector onto the origin direction");
		this.assert(this.closeTo(RCMatrix.product(g, [0, 0, 1].flop.flop).flat, [0, 0, 1]), "givens leaves the orthogonal complement alone");
		this.assert(this.closeTo(RCMatrix.givens(v, v), RCMatrix.identity(3)), "same direction → identity");
	}

	test_width_and_power {
		var w = RCMatrix.widthMatrixFromFactors([1, 1], false);
		var w2 = RCMatrix.widthMatrixFromFactors([0, 0], false);
		this.assert(this.closeTo(w, RCMatrix.identity(2)), "width 1 → identity");
		this.assert(this.closeTo(w2, [[0.5, 0.5], [0.5, 0.5]]), "width 0 → full mix");
		this.assertEquals(RCMatrix.widthMatrixFromFactors([2, 3], true), [[2, 0.0], [0.0, 3]], "ambisonics → diagonal");
		this.assertFloatEquals(RCMatrix.powerAdjustmentAmbisonics([1, 1], [0.5, 0.5]), 1, "unit widths → no adjustment");
		this.assert(RCGuard.isFinite(RCMatrix.inversePowerAdjustmentAmbisonics([0, 0], [0.5, 0.5])), "zero widths → finite");
	}

	test_fobject_template_and_registry {
		var tpl = RCFObject(song, \rc_test_fob, 2, [\room, 0.9], { |zpos, pos| (zpos[0] ? 0).min(1) }, priority: 2);
		var c;
		this.assertEquals(tpl.transparency([0.5], [0, 0]), 0.5, "transparency function");
		this.assertEquals(tpl.transparency(nil, nil), 0, "failing transparency (nil[0]) → 0");
		this.assertEquals(RCFObject(song, \x, 2, nil, { 0 / 0 }).transparency([0], [0]), 0, "NaN transparency → 0");
		c = tpl.clone;
		c.register(\verb);
		this.assertEquals(c.name, \verb, "registered under its name");
		this.assert(song.registry.fobject(\verb) === c, "registry lookup");
		this.assertEquals(tpl.clone.register(\verb), \verb_1, "name clash → suffix");
		c.free;
		this.assertEquals(song.registry.fobject(\verb), nil, "free unregisters");
		this.assertEquals(tpl.create(\nope), nil, "create without a group/server → nil + error, no crash");
		this.assert(RCLog.history.any { |e| e[2].contains("no fobject group") }, "reported");
	}

	test_fobject_weights_and_synthdef {
		var tpl = RCFObject(song, \rc_test_fob_sd, 2, nil, { 1 });
		var weights = tpl.posToSignalWeights([1]);
		var desc;
		this.assertEquals(weights.size, 2, "one weight per channel");
		this.assertEquals(tpl.addSynthDef({ |in| in * 0.5 }), \rc_test_fob_sd, "synthdef added");
		desc = SynthDescLib.global[\rc_test_fob_sd];
		this.assert(desc.notNil, "desc registered");
		[\in, \out, \recompute_space, \origin, \center, \width_factors].do { |k|
			this.assert(desc.controlNames.includes(k), "control % present".format(k));
		};
		this.assertEquals(RCFObject(song, \rc_bad_fob, 2, nil, { 1 }).addSynthDef({ nil.explode }), nil, "failing sound function reported, nil returned");
	}

	test_fobject_routing_in_orgnsm {
		var tpl = RCOrgnsm(\r, 0, 0, song);
		var f1 = RCFObject(song, \fa, 2, nil, { 0.6 }, priority: 0).clone;
		var f2 = RCFObject(song, \fb, 2, nil, { 0.6 }, priority: 1).clone;
		var f3 = RCFObject(song, \fc, 2, nil, { 0 }, priority: 2).clone;
		var o, ev;
		f1.register(\fa); f1.prSetBuses(10, 0);
		f2.register(\fb); f2.prSetBuses(11, 0);
		f3.register(\fc); f3.prSetBuses(12, 0);
		song.outArray = [7];
		tpl.addStaticAttrs((seed: 1, quant: [1, 0]));
		tpl.attrDictBase = [type: \note, dur_flex: 1, instrument_flex: \Kalimba, orgnsm_out_idx: 0, zpos: [0], pos: [0, 0]];
		o = tpl.create(layerKey: \core);
		ev = RCBeat(song.layer(\core), o.name, o.attrDict, addFirst: o.addFirstArray).asStream.next(Event.default);
		this.assertEquals(ev.outs, [10, 11], "fobjects taken by priority until the budget is full");
		this.assert(this.closeTo(ev.outamps, [0.5, 0.5]), "transparencies normalised");
		this.assertEquals(ev.instrument, 'Kalimba__2_out', "two-output synthdef variant");
		f2.free;
		ev = RCBeat(song.layer(\core), o.name, o.attrDict, addFirst: o.addFirstArray).asStream.next(Event.default);
		this.assertEquals(ev.outs, [10, 7], "remainder goes to the orgnsm's own out");
		this.assert(this.closeTo(ev.outamps, [0.6, 0.4]), "own out gets the leftover budget");
		o.rPut("ignore_fobject_names", [\fa]);
		ev = RCBeat(song.layer(\core), o.name, o.attrDict, addFirst: o.addFirstArray).asStream.next(Event.default);
		this.assertEquals(ev.outs, [7], "ignored fobject skipped (fc is transparent 0)");
	}
}
