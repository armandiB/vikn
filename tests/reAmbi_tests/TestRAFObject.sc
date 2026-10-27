// reAmbi — RAFObject: the layer registry, the chains' defs, the geometry, the messages, the online methods.
// An offline server (RECollectAddr): nothing booted, every message collected. A test layer of its own (a
// comb on the beam, one feedback channel, an array control with a law, a buffer, a need, a trigger) so the
// class is tested apart from RAFObjectLayers; the real layers have their own tests (TestRAFObjectLayers).
TestRAFObject : UnitTest {
	var server, addr, clock, savedRateLimit;

	setUp {
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
		clock = REOfflineClock(tempo: 1);
		addr = RECollectAddr("127.0.0.1", 57994, clock);
		server = Server(\raf_test, addr);
		server.statusWatcher.serverRunning = true;
		server.statusWatcher.notified = true;
		this.prAddTestLayers;
	}

	tearDown {
		RAFObject.removeLayer(\raftest);
		RAFObject.removeLayer(\raftest2);
		server.remove;
		RCLog.rateLimit = savedRateLimit;
	}

	logHas { |text| ^RCLog.history.any { |e| e[2].contains(text) } }
	prMessages { ^addr.bundles.asArray.collect { |b| b.drop(1) }.flatten(1) }

	// raftest: 2 taps, a comb fed back (one LocalIn channel), `times` (2 values) from the lang param `time`
	// and the tune, a kernel buffer, a need, a trigger; raftest2: 3 plain taps
	prAddTestLayers {
		RAFObject.addLayer(\raftest, (
			nTaps: 2, fbChannels: 1, trigger: true, tapOrder: \radius,
			lang: [\time, 0.5, \tune, \harmonic],
			controls: [\decay, 10, \dark, 4000],
			arrays: [\times, (n: 2, from: [\time, \tune], law: { |p, n| n.collect { |k| p[\time] * (k + 1) * if(p[\tune] == \harmonic) { 1 } { 1.5 } } })],
			buffers: [(control: \kernelBuf, frames: 1024, channels: 1)],
			needs: [\sideBus],
			process: { |beam, nTaps, ctx|
				var loop = beam + (ctx[\fb][0] * 0.5), taps, lim;
				var side = InFeedback.ar(ctx[\sideBus], 1) * 0.001, kb = ctx[\kernelBuf], t = ctx[\trig];
				taps = nTaps.collect { |k| LPF.ar(CombC.ar(loop + side + (BufRd.ar(1, kb, Phasor.ar(0, 1, 0, 1024)) * 0) + (t * 0), 1, ctx[\times][k].clip(0.001, 1), ctx[\decay]), ctx[\dark]) };
				#taps, lim = RAFObject.limit(taps);
				ctx[\fbOut] = [taps[0]];
				[taps, lim]
			}
		));
		RAFObject.addLayer(\raftest2, (
			nTaps: 3, controls: [\gain, 1],
			process: { |beam, nTaps, ctx| RAFObject.limit(nTaps.collect { |k| beam * ctx[\gain] * (k + 1) }) }
		));
	}

	test_registry {
		var names = RAFObject.layerNames;
		this.assert(names.includes(\raftest) and: { names.includes(\raftest2) }, "the test layers are registered");
		this.assertEquals(RAFObject.layer(\raftest)[\nTaps], 2, "a layer's spec is read by brackets");
		this.assertEquals(RAFObject.layer(\raftest2)[\tapOrder], \none, "the defaults are filled in");
		this.assert(RAFObject.addLayer(\bad, (process: 1)).isNil, "a spec without nTaps is refused");
		this.assert(RAFObject.addLayer(\bad, (nTaps: 1, process: {}, controls: [\size, 1])).isNil, "a control named after an Event method is refused");
		this.assert(RAFObject.addLayer(\bad, (nTaps: 1, process: {}, controls: [\az, 1])).isNil, "a control named after the builder's is refused");
		this.assert(RAFObject.addLayer("bad name", (nTaps: 1, process: {})).isNil, "a name with a space is refused");
		this.assert(RAFObject.layer(\bad).isNil, "nothing registered under a refused name");
		this.assert(RAFObject.addLayer(\raftmp, (nTaps: 1, process: { |b, n, c| RAFObject.limit([b]) })) == \raftmp, "a plain layer is accepted");
		RAFObject.removeLayer(\raftmp);
		this.assert(RAFObject.layer(\raftmp).isNil, "and removed");
	}

	test_names_and_chains {
		this.assertEquals(RAFObject.defName(\raftest), \RAFObject_o3_raftest, "a single layer's def name");
		this.assertEquals(RAFObject.defName([\raftest, \raftest2]), \RAFObject_o3_raftest_raftest2, "a chain's def name");
		this.assertEquals(RAFObject.defName([\raftest2], 1), \RAFObject_o1_raftest2, "the order in the name");
		this.assertEquals(RAFObject.sumDefName, \RAFObject_o3_sum, "the sum's name");
		this.assert(RAFObject.chain(\nope).isNil and: { this.logHas("no layer nope") }, "an unknown layer: nil and a log");
		this.assert(RAFObject.chain([]).isNil, "an empty chain: nil");
		this.assertEquals(RAFObject.chain("raftest"), [\raftest], "a String names a layer");
		this.assert(RAFObject.defName([\raftest, \raftest2]).asString.every { |c| c.isAlphaNum or: { c == $_ } }, "a def name is a file name");
	}

	test_controls_of {
		var kv = RAFObject.controlsOf([\raftest, \raftest2]), keys = RCUtil.kvKeys(kv), lang = RAFObject.langOf([\raftest, \raftest2]);
		this.assert([\out, \inBus, \gate, \amp, \mix, \az, \el, \wide, \spread, \dist, \ref, \maxGain, \lpfBase, \att, \release, \seed, \through].every { |k| keys.includes(k) }, "the builder's controls");
		this.assert(keys.includes(\azs) and: { RCUtil.kvAt(kv, \azs).size == 3 }, "azs of the last layer's taps");
		this.assert(keys.includes(\t_trig), "the trigger when a layer takes one");
		this.assert(keys.includes(\decay) and: { keys.includes(\times) } and: { keys.includes(\kernelBuf) } and: { keys.includes(\sideBus) }, "layer 1's controls plain");
		this.assertEquals(RCUtil.kvAt(kv, \times), [0.5, 1.0], "an array control's default from its law on the layer's defaults");
		this.assert(keys.includes('l2_gain'), "layer 2's controls prefixed");
		this.assertEquals(lang, [\time, 0.5, \tune, \harmonic], "the language params, prefixed as the controls");
		this.assert(RAFObject.controlsOf([\raftest2]).includes(\t_trig).not, "no trigger without a layer that takes one");
	}

	test_def_builds {
		var name = RAFObject.def(\raftest), def, names, ctl;
		this.assertEquals(name, \RAFObject_o3_raftest, "the def is built and named");
		def = name !? { SynthDescLib.global[name].def };
		this.assert(def.notNil, "the def is in SynthDescLib");
		if(def.isNil) { ^this };
		this.assert(def.asBytes.size < 60000, "under a Score's inline limit (% bytes)".format(def.asBytes.size));
		names = def.allControlNames.collect(_.name);
		this.assert([\out, \inBus, \gate, \az, \el, \wide, \spread, \dist, \azs, \els, \t_trig, \decay, \dark, \times, \kernelBuf, \sideBus].every { |k| names.includes(k) }, "the controls are there: %".format(names));
		ctl = def.allControlNames.detect { |c| c.name == \azs };
		this.assertEquals(ctl.defaultValue.size, 2, "azs has nTaps channels");
		this.assertEquals(def.children.count { |u| u.isKindOf(LocalIn) }, 1, "one LocalIn");
		this.assertEquals(def.children.count { |u| u.isKindOf(LocalOut) }, 1, "one LocalOut");
		this.assertEquals(def.children.detect { |u| u.isKindOf(Out) }.inputs.size, 17, "a 16-channel Out");
		this.assertEquals(def.children.detect { |u| u.isKindOf(In) }.numOutputs, 16, "the pickup reads 16 channels");
		this.assert(def.children.count { |u| u.isKindOf(SOS) or: { u.isKindOf(FOS) } } > 0, "the near field's filters are there");
		this.assert(RAFObject.defNames.includes(name), "the name is kept in defNames");
		this.assertEquals(RAFObject.def(\raftest), name, "built once: the cached name");
		this.assert(RAFObject.def(\raftest, 4).isNil, "order 4 refused");
		this.assert(RAFObject.def(\nope).isNil, "an unknown layer: nil");
	}

	test_chain_builds_with_shared_feedback {
		var name = RAFObject.def([\raftest, \raftest, \raftest2]), def, names, li;
		this.assert(name.notNil, "a chain of three builds");
		def = SynthDescLib.global[name].def;
		names = def.allControlNames.collect(_.name);
		this.assert(names.includes(\decay) and: { names.includes('l2_decay') } and: { names.includes('l3_gain') } and: { names.includes('l2_times') }, "the prefixes");
		li = def.children.detect { |u| u.isKindOf(LocalIn) };
		this.assertEquals(def.children.count { |u| u.isKindOf(LocalIn) }, 1, "one LocalIn for two feedback layers");
		this.assertEquals(li.numOutputs, 2, "with a channel per feedback layer");
		this.assertEquals(def.children.detect { |u| u.isKindOf(LocalOut) }.inputs.size, 2, "both fed back");
		this.assertEquals(def.allControlNames.detect { |c| c.name == \azs }.defaultValue.size, 3, "the last layer's taps");
	}

	test_sum_def {
		var name = RAFObject.sumDef, def, s;
		this.assertEquals(name, \RAFObject_o3_sum, "the sum def");
		def = SynthDescLib.global[name].def;
		this.assert(def.notNil and: { def.asBytes.size < 60000 }, "built and small");
		this.assert([\out, \inBus, \gain, \gate, \release].every { |k| def.allControlNames.collect(_.name).includes(k) }, "its controls");
		s = RAFObject.sumSynth(server);
		this.assertEquals(s.defName.asSymbol, \RAFObject_o3_sum, "sumSynth: a Synth.basicNew of it");
		this.assert(RAFObject.defNames.includes(name), "in defNames");
	}

	test_sph_matches_atk {
		var hoa = HoaOrder.new(3);
		[[0, 0], [pi / 2, 0], [0.7, -0.4], [1.9, 1.2]].do { |d|
			var v = [d[1].cos * d[0].cos, d[1].cos * d[0].sin, d[1].sin], ours = RAFObject.sphKr(v[0], v[1], v[2]), atk = hoa.sph(d[0], d[1]);
			this.assertArrayFloatEquals(ours, atk, "sphKr = HoaOrder.sph at %".format(d), 1e-6);
		};
		this.assertEquals(RAFObject.sphKr(0.3, 0.4, 0.5, 1).size, 4, "kept to the order");
	}

	test_geometry {
		var pts, c, draws;
		this.assertArrayFloatEquals(RAFObject.dir(0, 0), [1, 0, 0], "the front", 1e-9);
		this.assertArrayFloatEquals(RAFObject.dir(90, 0), [0, 1, 0], "the left", 1e-9);
		this.assertArrayFloatEquals(RAFObject.azelDeg(RAFObject.dir(37, -12)), [37, -12], "az el round trip", 1e-9);
		pts = RAFObject.tapPoints([0, 0], 10, 10, 0, [[1, 0]]);
		this.assertFloatEquals(pts[0][0], 10.degrad, "a draw [1, 0] at across 10 lands 10 degrees to the left", 1e-9);
		this.assertFloatEquals(pts[1][0], 0, "and on the horizon", 1e-9);
		pts = RAFObject.tapPoints([0, 0], 10, 10, 90, [[1, 0]]);
		this.assertFloatEquals(pts[0][0], 0, "a lean of 90: the draw goes up", 1e-9);
		this.assertFloatEquals(pts[1][0], 10.degrad, "10 degrees up", 1e-9);
		pts = RAFObject.tapPoints([0, 0], 10, 5, 0, [[0, 1]]);
		this.assertFloatEquals(pts[1][0], 5.degrad, "up scales by the up sigma", 1e-9);
		draws = RAFObject.gaussianDraws(7, 6);
		pts = RAFObject.tapPoints([90, 40], 10, 5, 0, draws);
		this.assert(pts[0].every { |a, i| (RAFObject.dot(RAFObject.dir(a.raddeg, pts[1][i].raddeg), RAFObject.dir(90, 40)) > 60.degrad.cos) }, "the taps follow the centre (within 60 degrees of it)");
		this.assert(pts[0].every(_.isNumber) and: { pts[1].every(_.isNumber) }, "finite");
		pts = RAFObject.tapPoints([0, 90], 10, 10, 0, draws);
		this.assert(pts[0].every { |x| x.isNaN.not } and: { pts[1].every { |x| x.isNaN.not } }, "the pole is safe");
		this.assertEquals(draws, RAFObject.gaussianDraws(7, 6), "the draws are a function of the seed");
		this.assert(draws != RAFObject.gaussianDraws(8, 6), "another seed, other draws");
		this.assert(draws.flat.every { |x| x.isNaN.not }, "the draws are finite");
		this.assert(RAFObject.hash(1, 2, 3) >= 0 and: { RAFObject.hash(1, 2, 3) < 1 }, "the hash in [0, 1)");
		this.assertFloatEquals(RAFObject.hash(1, 2, 3), 0.9625802845519875, "the hash is the Bowls rig's", 1e-9);
		c = RAFObject.dir(0, 0);
		pts = RAFObject.orderTaps([0.5, 0.1, 0.3], [0, 0, 0], c, \radius);
		this.assertEquals(pts[0], [0.1, 0.3, 0.5], "by radius: the nearest first");
		pts = RAFObject.orderTaps([0, 0, 0], [0.5, -0.2, 0.1], c, \elevation);
		this.assertEquals(pts[1], [-0.2, 0.1, 0.5], "by elevation: the lowest first");
		pts = RAFObject.orderTaps([0.5, 0.1], [0, 0], c, \none);
		this.assertEquals(pts[0], [0.5, 0.1], "none: as they are");
		this.assertFloatEquals(RAFObject.widthOf(30), 0.5, "a sigma of 30 is a width of 0.5");
		this.assertEquals(RAFObject.widthOf(90), 1, "clipped at 1");
	}

	test_new_msgs {
		var o = RAFObject(server, \raftest2, 32, 48, params: (gain: 2)), g = Group.basicNew(server), msgs, args, o2;
		msgs = o.newMsgs(g, \addToHead);
		this.assertEquals(msgs.size, 3, "/s_new, setn azs, setn els");
		this.assertEquals(msgs[0][0], 9, "an /s_new");
		this.assertEquals(msgs[0][1], \RAFObject_o3_raftest2, "of the layer's def");
		this.assertEquals(msgs[0][2], o.synth.nodeID, "the synth's id");
		this.assertEquals(msgs[0][3], 0, "addToHead");
		this.assertEquals(msgs[0][4], g.nodeID, "in the target");
		args = msgs[0].drop(5);
		this.assertEquals(RCUtil.kvAt(args, \out), 48, "out = the out bus");
		this.assertEquals(RCUtil.kvAt(args, \inBus), 32, "inBus = the in bus");
		this.assertEquals(RCUtil.kvAt(args, \gain), 2.0, "the param");
		this.assert(msgs.every { |m| m.flat.size == m.size }, "no Array inside a message");
		this.assertEquals(msgs[1], [16, o.synth.nodeID, \azs, 3, 0.0, 0.0, 0.0], "setn azs of nTaps zeros");
		this.assertEquals(msgs[2][2], \els, "setn els");
		this.assert(o.isStarted and: { o.isReleased.not }, "started");
		this.assertEquals(o.newMsgs(g), [], "a second start while sounding is refused");
		this.assert(this.logHas("already started"), "and said");
		o2 = RAFObject(server, \raftest2, 0, 0);
		msgs = o2.newMsgs(g, \addToTail, [\amp, 0.5, \tune, \otonal, \nope, 1]);
		this.assertEquals(msgs[0][3], 1, "addToTail");
		this.assertEquals(RCUtil.kvAt(msgs[0].drop(5), \amp), 0.5, "an arg");
		this.assert(RCUtil.kvIncludesKey(msgs[0].drop(5), \nope).not and: { this.logHas("unknown param nope") }, "an unknown arg dropped and said");
		this.assert(o.isOffline, "the collecting server is offline");
	}

	test_new_msgs_with_buffer_need_and_arrays {
		var o = RAFObject(server, \raftest, 32, 48, params: (decay: 20, time: 0.25)), g = Group.basicNew(server), msgs, n, before;
		msgs = o.newMsgs(g);
		this.assertEquals(msgs, [], "without the need: nothing");
		this.assert(this.logHas("needs sideBus"), "and said which");
		this.assert(o.buffers.isEmpty and: { o.isStarted.not }, "no buffer, not started");
		before = Buffer.new(server, 1, 1).bufnum; server.bufferAllocator.free(before);
		msgs = o.newMsgs(g, \addToHead, [\sideBus, 20]);
		this.assertEquals(msgs.size, 5, "/b_alloc, /s_new, azs, els, times");
		n = o.buffers[\kernelBuf].bufnum;
		this.assertEquals(msgs[0], ['/b_alloc', n, 1024, 1], "the buffer allocated first, four arguments");
		this.assertEquals(RCUtil.kvAt(msgs[1].drop(5), \kernelBuf), n, "the buffer number on its control");
		this.assertEquals(RCUtil.kvAt(msgs[1].drop(5), \sideBus), 20.0, "the need on its control");
		this.assertEquals(RCUtil.kvAt(msgs[1].drop(5), \decay), 20.0, "a control param");
		this.assert(RCUtil.kvIncludesKey(msgs[1].drop(5), \time).not, "a language param is not sent");
		this.assertEquals(msgs[4], [16, o.synth.nodeID, \times, 2, 0.25, 0.5], "the array from the law on the given time");
		this.assertEquals(o.freeMsgs, [['/b_free', n]], "freeMsgs: the buffer freed");
		this.assertEquals(Buffer.new(server, 1, 1).bufnum == n, false, "the number is still taken before didFree");
		o.didFree;
		this.assert(o.isFreed and: { o.buffers.isEmpty } and: { o.synth.isNil }, "didFree: forgotten");
	}

	test_set_center_taps_dist_trig_release {
		var o = RAFObject(server, \raftest, 32, 48), g = Group.basicNew(server), id, m;
		o.newMsgs(g, \addToHead, [\sideBus, 20]);
		id = o.synth.nodeID;
		this.assertEquals(o.setMsgs(\decay, 30, \dark, 2000), [[15, id, \decay, 30.0, \dark, 2000.0]], "scalars in one /n_set");
		this.assertEquals(o.setMsgs(\time, 0.6), [[16, id, \times, 2, 0.6, 1.2]], "a language param recomputes its array");
		m = o.setMsgs(\tune, \other);
		this.assertEquals(m.size, 1, "a Symbol-valued language param too: one message");
		this.assertEquals(m[0].keep(4), [16, id, \times, 2], "the array");
		this.assertArrayFloatEquals(m[0].drop(4), [0.9, 1.8], "its values from the law", 1e-9);
		this.assertEquals(o.setMsgs(\nope, 1), [], "an unknown key: nothing");
		this.assertEquals(o.setMsgs(\times, [1, 2, 3]), [], "an array of the wrong size: nothing");
		this.assertEquals(o.setMsgs(\times, [0.1, 0.2]), [[16, id, \times, 2, 0.1, 0.2]], "an array control set whole");
		this.assertEquals(o.setMsgs(\kernelBuf, 3), [], "a buffer control is fixed");
		this.assertEquals(o.centerMsgs(0.4, 0.2), [[15, id, \az, 0.4, \el, 0.2]], "the centre");
		this.assertFloatEquals(o.az, 0.4, "kept", 1e-9);
		m = o.tapsMsgs([0.1, 0.2], [0.3, 0.4]);
		this.assertEquals(m, [[16, id, \azs, 2, 0.1, 0.2], [16, id, \els, 2, 0.3, 0.4]], "the taps");
		this.assertEquals(o.tapsMsgs([0.1], [0.3]), [], "the wrong count: nothing");
		this.assertEquals(o.distMsg(3.2), [15, id, \dist, 3.2], "the distance");
		this.assertEquals(o.trigMsg, [15, id, \t_trig, 1], "the trigger");
		this.assertEquals(o.releaseMsgs(4), [[15, id, \release, 4.0, \gate, 0]], "the release over 4 s");
		this.assert(o.isReleased, "released");
		this.assertEquals(o.setMsgs(\decay, 1), [], "nothing to set once released");
		this.assertEquals(o.releaseMsgs(2), [], "released once");
		this.assertEquals(o.centerMsgs(0.1, 0.1), [], "no centre message once released (the centre kept)");
		this.assertEquals(o.freeMsgs.size, 1, "freeMsgs: the buffer only (the synth frees itself)");
		o.didFree;
	}

	test_release_without_fade_and_kill {
		var o = RAFObject(server, \raftest2, 32, 48), g = Group.basicNew(server), id;
		o.newMsgs(g);
		id = o.synth.nodeID;
		this.assert(o.trigMsg.isNil, "no trigger on a layer without one");
		this.assertEquals(o.releaseMsgs, [[15, id, \gate, 0]], "a release at the synth's own time");
		o = RAFObject(server, \raftest2, 32, 48);
		o.newMsgs(g);
		id = o.synth.nodeID;
		this.assertEquals(o.freeMsgs(true), [[11, id]], "kill: /n_free of a synth not released");
		this.assert(o.isReleased, "and released");
	}

	test_chain_set_prefixed {
		var o = RAFObject(server, [\raftest2, \raftest], 32, 48), g = Group.basicNew(server), msgs, id;
		msgs = o.newMsgs(g, \addToHead, ['l2_sideBus', 21, 'l2_time', 0.1, \gain, 3]);
		this.assert(msgs.notEmpty, "the chain starts with its need prefixed");
		id = o.synth.nodeID;
		this.assertEquals(RCUtil.kvAt(msgs[1].drop(5), \gain), 3.0, "layer 1's control plain");
		this.assertEquals(RCUtil.kvAt(msgs[1].drop(5), 'l2_sideBus'), 21.0, "layer 2's need prefixed");
		this.assertEquals(msgs.last, [16, id, 'l2_times', 2, 0.1, 0.2], "layer 2's array from its prefixed language param");
		this.assertEquals(o.setMsgs('l2_decay', 3), [[15, id, 'l2_decay', 3.0]], "set with the prefix");
		this.assertEquals(o.setMsgs('l2_time', 0.3), [[16, id, 'l2_times', 2, 0.3, 0.6]], "a prefixed language param recomputes its array");
		this.assertEquals(o.nTaps, 2, "the chain's taps are the last layer's");
		o.releaseMsgs(1); o.freeMsgs; o.didFree;
	}

	test_online_collected {
		var o = RAFObject(server, \raftest2, 32, 48), g = Group.basicNew(server), b, o2, n, found;
		addr.clear;
		o.start(0.3, g);
		b = addr.bundles.last;
		this.assertFloatEquals(b[0], 0.3, "start: a bundle at the latency asked", 1e-9);
		this.assertEquals(b.size, 4, "with the three messages");
		this.assertEquals(b[1][0], 9, "the /s_new");
		o.center(0.4, 0.2);
		b = addr.bundles.last;
		this.assertFloatEquals(b[0], server.latency, "center: at the server's latency", 1e-9);
		this.assertEquals(b[1], [15, o.synth.nodeID, \az, 0.4, \el, 0.2], "the centre message");
		o.set(\gain, 2);
		this.assertEquals(addr.bundles.last[1], [15, o.synth.nodeID, \gain, 2.0], "set");
		o.distance(2.5);
		this.assertEquals(addr.bundles.last[1], [15, o.synth.nodeID, \dist, 2.5], "distance");
		o.release(1);
		this.assertEquals(addr.bundles.last[1], [15, o.synth.nodeID, \release, 1.0, \gate, 0], "release");
		this.assert(o.isReleased, "released");
		o.free;
		this.assert(o.isFreed, "freed");
		// a buffer freed only after the fade, on the offline clock when a thread of it schedules the release
		o2 = RAFObject(server, \raftest, 32, 48);
		clock.sched(0, { o2.start(0.2, g, \addToHead, [\sideBus, 20]); nil });
		clock.advanceTo(0.01);
		n = o2.buffers[\kernelBuf].bufnum;
		clock.sched(0, { o2.release(1); nil });
		clock.advanceTo(0.02);
		found = { addr.bundles.any { |bb| bb.drop(1).any { |m| m[0] == '/b_free' and: { m[1] == n } } } };
		this.assert(found.value.not, "the buffer is not freed at the release");
		clock.advanceTo(1.8);
		this.assert(found.value, "the buffer is freed after the fade, the latency and 0.5 s");
		this.assert(o2.isFreed, "and the object knows");
	}

	test_retap_records_the_online_methods {
		var o = RAFObject(server, \raftest2, 32, 48), g = Group.basicNew(server), log = List.new, saved = RETap.mainThreadOnly;
		protect {
			RETap.mainThreadOnly = false;
			RETap.observe({ |obj, method, args| log.add(method) }, {
				o.start(0.2, g); o.set(\gain, 2); o.center(0.4, 0.2); o.setMsgs(\gain, 3); o.centerMsgs(0.1, 0.1); o.release(1); o.free;
			});
		} { RETap.mainThreadOnly = saved };
		this.assertEquals(log.asArray, [\start, \set, \center, \release, \free], "the online methods recorded, the builders not");
	}
}
