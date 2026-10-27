// reAmbi — RAFObjectLayers: every layer registered and building, their controls, the laws.
TestRAFObjectLayers : UnitTest {
	var savedRateLimit;

	setUp { savedRateLimit = RCLog.rateLimit; RCLog.rateLimit = 0; RCLog.reset }
	tearDown { RCLog.rateLimit = savedRateLimit }

	logHas { |text| ^RCLog.history.any { |e| e[2].contains(text) } }

	test_registered {
		var names = RAFObject.layerNames;
		[\wash, \freeze, \shimmer, \ring, \grains, \haze, \fog, \drone].do { |n| this.assert(names.includes(n), "% registered".format(n)) };
		this.assertEquals(RAFObject.layer(\wash)[\nTaps], 6, "the wash has six taps");
		this.assertEquals(RAFObject.layer(\haze)[\nTaps], 2, "the haze two");
		this.assertEquals(RAFObject.layer(\wash)[\tapOrder], \radius, "the wash's taps by radius");
		this.assertEquals(RAFObject.layer(\ring)[\tapOrder], \elevation, "the ring's by elevation");
		this.assert(RAFObject.layer(\freeze)[\trigger] == true, "the freeze takes a trigger");
		this.assertEquals(RAFObject.layer(\drone)[\needs], [\droneBus], "the drone needs its bus");
		this.assertEquals(RAFObject.layer(\drone)[\buffers][0][\frames], 16384, "and a kernel buffer of one frame");
		[\wash, \shimmer, \haze, \drone].do { |n| this.assertEquals(RAFObject.layer(n)[\fbChannels], 1, "% feeds back".format(n)) };
		[\freeze, \ring, \grains, \fog].do { |n| this.assertEquals(RAFObject.layer(n)[\fbChannels], 0, "% does not".format(n)) };
		this.assert(RAFObject.layer(\fog)[\controls].includes(\room) and: { RAFObject.layer(\fog)[\controls].includes(\size).not }, "the fog's room (size is an Event method)");
	}

	test_every_layer_builds {
		RAFObject.layerNames.do { |n|
			var spec = RAFObject.layer(n), name = RAFObject.def(n), def, ctl, names, fbIn;
			this.assert(name.notNil, "% builds".format(n));
			if(name.notNil) {
				def = SynthDescLib.global[name].def;
				this.assert(def.notNil, "% is in SynthDescLib".format(n));
				this.assert(def.asBytes.size < 60000, "% under 60 000 bytes (%)".format(n, def.asBytes.size));
				names = def.allControlNames.collect(_.name);
				this.assert([\out, \inBus, \gate, \amp, \az, \el, \wide, \spread, \dist, \azs, \els, \release].every { |k| names.includes(k) }, "% has the builder's controls".format(n));
				this.assert(RCUtil.kvKeys(spec[\controls]).every { |k| names.includes(k) }, "% has its own controls".format(n));
				this.assert(RCUtil.kvKeys(spec[\arrays]).every { |k| names.includes(k) }, "% has its array controls".format(n));
				ctl = def.allControlNames.detect { |c| c.name == \azs };
				this.assertEquals(ctl.defaultValue.size, spec[\nTaps], "% has azs of its taps".format(n));
				this.assertEquals(names.includes(\t_trig), spec[\trigger] == true, "% has a trigger only when it takes one".format(n));
				fbIn = def.children.detect { |u| u.isKindOf(LocalIn) };
				this.assertEquals(fbIn.notNil, spec[\fbChannels] > 0, "% has a LocalIn only when it feeds back".format(n));
				this.assertEquals(def.children.detect { |u| u.isKindOf(Out) }.inputs.size, 17, "% writes 16 channels".format(n));
			};
		};
	}

	test_a_chain_of_two_feedback_layers {
		var name = RAFObject.def([\wash, \shimmer]), def, names, li;
		this.assert(name.notNil, "wash > shimmer builds");
		def = SynthDescLib.global[name].def;
		names = def.allControlNames.collect(_.name);
		this.assert(names.includes(\decay) and: { names.includes('l2_decay') } and: { names.includes('l2_ratio') } and: { names.includes(\through) }, "the prefixes and through");
		li = def.children.detect { |u| u.isKindOf(LocalIn) };
		this.assertEquals(li.numOutputs, 2, "one LocalIn of two channels");
		this.assertEquals(def.allControlNames.detect { |c| c.name == \azs }.defaultValue.size, 3, "the shimmer's three taps");
		this.assertEquals(RAFObject.langOf([\wash, \ring]), [\time, 0.75, \tune, \harmonic, \irrational, 0, 'l2_base', 240, 'l2_tune', \just, 'l2_octave', 0], "the language params of a chain");
	}

	test_times {
		var h = RAFObjectLayers.times(0.5, \harmonic, 0), i = RAFObjectLayers.times(0.5, \harmonic, 1), g = RAFObjectLayers.times(1, \golden, 0), fr = [[1, 1], [2, 1], [3, 1], [3, 2], [4, 3], [5, 4], [5, 3], [6, 5], [4, 1], [5, 1], [6, 1], [5, 2], [7, 4], [9, 4]];
		this.assertArrayFloatEquals(h, [0.5, 1, 1.5, 2, 2.5, 3], "harmonic at irrational 0: time × 1..6", 1e-9);
		this.assert(i.every { |t, k| ((t / h[k]) - 1).abs <= 0.25 and: { ((t / h[k]) - 1).abs > 0.05 } }, "at irrational 1 each time is offset, within 25 %");
		this.assert(i.collect { |t, k| t / h[k] }.asSet.size == 6, "every tap its own offset");
		this.assert(g.every { |a| g.every { |b| (a == b) or: { fr.every { |f| ((a / b) - (f[0] / f[1])).abs > 1e-6 and: { ((b / a) - (f[0] / f[1])).abs > 1e-6 } } } } }, "the golden set has no two times in a small ratio");
		this.assertEquals(RAFObjectLayers.times(4, \harmonic, 0).last, 7.9, "clipped at 7.9 s");
		this.assertArrayFloatEquals(RAFObjectLayers.times(0.5, \nope, 0), h, "an unknown tune: harmonic", 1e-9);
		this.assert(this.logHas("unknown tune nope"), "and a warning");
		this.assertEquals(RAFObjectLayers.times(0.5, "otonal", 0).size, 6, "a String names a tune");
		this.assertEquals(RAFObjectLayers.tuneNames, [\fifths, \golden, \harmonic, \just, \otonal, \sub], "the tunings");
	}

	test_ring_freqs_and_rates {
		this.assertArrayFloatEquals(RAFObjectLayers.ringFreqs(240, \just, 0), [240, 270, 300, 360, 400, 450], "the just hexatonic over 240", 1e-9);
		this.assertFloatEquals(RAFObjectLayers.ringFreqs(240, \just, 1)[0], 480, "an octave up", 1e-9);
		this.assertEquals(RAFObjectLayers.ringFreqs(10, \harmonic, 0)[0], 20.0, "clipped at 20 Hz");
		this.assertEquals(RAFObjectLayers.grainRates, [1, 2, 0.5, 1.5, 4/3, -1], "the grains' rates");
		this.assertEquals(RCUtil.kvAt(RAFObject.controlsOf(\grains), \rates), RAFObjectLayers.grainRates.collect(_.asFloat), "the rates as the array's default");
		this.assertArrayFloatEquals(RCUtil.kvAt(RAFObject.controlsOf(\ring), \freqs), RAFObjectLayers.ringFreqs(240, \just, 0), "the ring's default freqs from its law", 1e-9);
		this.assertArrayFloatEquals(RCUtil.kvAt(RAFObject.controlsOf(\wash), \times), RAFObjectLayers.times(0.75, \harmonic, 0), "the wash's default times from its law", 1e-9);
	}
}
