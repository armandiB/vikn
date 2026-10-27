// reAmbi — an ambisonic fobject: a spatial effect object in a third-order field (ATK Hoa, ACN N3D,
// 16 channels). It picks the field bus up in a steerable beam at a centre (az, el) as wide as asked,
// runs a chain of registered effect layers (RAFObjectLayers: wash, freeze, shimmer, ring, grains, haze,
// fog, drone; one SynthDef per chain, built on demand and cached) and puts the result back at the
// centre, spread over the object (the last layer's taps at their own places, per-degree spread gains)
// at a distance (a point source's gain and air law, HoaNFCtrl's near field). Every message is built as
// a flat Array so that a rig sends it in its own bundles, live or into a collecting server (an NRT
// take: RECollectAddr); the online methods send them at the server's latency and record (RETap).
//
//   o = RAFObject(s, \wash, inBus: pool, outBus: wet, params: (decay: 40));
//   ~send.(o.newMsgs(group, \addToHead), latency);           // /b_alloc… /s_new, /n_setn azs els times
//   ~send.(o.centerMsgs(0.4, 0.2) ++ o.tapsMsgs(azs, els) ++ [o.distMsg(3.2)], latency);
//   ~send.(o.setMsgs(\tune, \otonal, \decay, 30), latency);  // a language param recomputes its array
//   ~send.(o.releaseMsgs(4), latency); ... ~send.(o.freeMsgs, latency); o.didFree;
//   o.start(0.2, group); o.center(0.4, 0.2); o.release(4); o.free;   // the same, sent and recorded
//   RAFObject.defNames;                                       // for a rig's def list (an NRT score)
//   RAFObject.def([\wash, \shimmer]);                         // a chain: layer 2's controls l2_…
//
// The pickup: the field's degrees weighed by (1 - wide)^l, an energy beam (HoaDecodeDirection) with its
// on-axis gain put back to 1 whatever the width (wide 0: an order-3 beam, -3 dB at 28 degrees; 0.5: at
// 39; 1: W itself, an omni pickup). The output: each tap at its place by the real spherical harmonics
// (control rate: a moving tap costs 16 multiplies), the degrees weighed by (1 - spread)^l (0: a point;
// 1: W only), then the near field of its distance (HoaNFCtrl: degree 0 untouched, no level law of its
// own) after a point source's law (gain ref/dist capped at maxGain, a low-pass at lpfBase/dist): the
// same level and air as the piece's sources. The centre and the taps are glided as unit vectors (0.3 s),
// not as angles: a tap crossing the back passes smoothly. Layer k + 1 hears the mean of layer k's taps
// (/sqrt(n)); `through` adds what the earlier layers made to the output. One LocalIn/LocalOut pair per
// SynthDef: the builder shares its channels among the layers that feed back. Needs atk-sc3 and
// sc3-plugins (the layers). The biggest chain (wash) is 40 KB: under a Score's inline limit (60 KB,
// Score:spillDefs); SynthDef:add sends a def over 16 KB as a file.
RAFObject {
	classvar registry, <defNames, <>defPrefix = "RAFObject", <contractControls;
	var <server, <name, <layers, <order, <nTaps, <defName, <inBus, <outBus, <params;
	var <synth, <group, <buffers;
	var <az = 0, <el = 0, <azs, <els, <wide = 0.3, <spread = 0.3, <dist = 2;
	var <isStarted = false, <isReleased = false, <isFreed = false;

	*initClass {
		registry = registry ?? { IdentityDictionary.new };
		defNames = List.new;
		contractControls = [\out, 0, \inBus, 0, \gate, 1, \amp, 1, \mix, 1, \az, 0, \el, 0, \wide, 0.3, \spread, 0.3,
			\dist, 2, \ref, 1.5, \maxGain, 2, \lpfBase, 12000, \att, 0.5, \release, 2, \seed, 0, \through, 0];
	}

	// layers: a layer's name or an Array of names (a chain); inBus/outBus: a Bus or an index; params: the
	// layers' params (a kv Array or an Event; layer 2's keys prefixed l2_). Allocates nothing, sends nothing.
	*new { |server, layers, inBus = 0, outBus = 0, order = 3, params, name|
		^super.new.initRAFObject(server, layers, inBus, outBus, order, params, name)
	}

	initRAFObject { |serverarg, layersarg, inBusarg, outBusarg, orderarg, paramsarg, namearg|
		server = serverarg ? Server.default;
		layers = this.class.chain(layersarg);
		order = orderarg.asInteger;
		inBus = inBusarg; outBus = outBusarg;
		params = IdentityDictionary.new;
		RCUtil.asKV(paramsarg).pairsDo { |k, v| params[k.asSymbol] = v };
		name = (namearg ?? { layers !? { |l| l.collect(_.asString).join("+") } ? "none" }).asSymbol;
		nTaps = layers !? { |l| registry[l.last][\nTaps] } ? 0;
		azs = 0.0 ! nTaps; els = 0.0 ! nTaps;
		buffers = IdentityDictionary.new;
	}

	//////// the layer registry

	// A layer: an Event (read by brackets) with nTaps (the taps it outputs), controls (a kv Array of the
	// def's controls and defaults, in order), lang (kv: language-side params, never sent), arrays (kv:
	// name -> (n:, from: [the lang and control keys it reads], law: { |params, n| } or default: values):
	// /n_setn controls, recomputed when a key they read changes), buffers ([(control:, frames:,
	// channels:)]: server buffers allocated per start), needs ([controls the caller must give, a bus
	// index]), tapOrder (\radius, \elevation, \none: how a caller should order the taps' points), trigger
	// (true: it reads ctx[\trig], the def's \t_trig), fbChannels (channels of the shared LocalIn it needs;
	// it sets ctx[\fbOut], an Array of that many signals), process ({ |beam, nTaps, ctx| ... [taps, lim] }:
	// nTaps mono signals and the limiter's gain), doc (a line). ctx carries every control, array, buffer
	// and need under its bare name, dist/ref/maxGain/lpfBase, trig, fb, sr (SampleRate.ir), index.
	// Returns the name, nil when refused.
	*addLayer { |name, spec|
		var s, keys = [\nTaps, \controls, \lang, \arrays, \buffers, \needs, \tapOrder, \trigger, \fbChannels, \process, \doc], names;
		registry = registry ?? { IdentityDictionary.new };
		name = name.asSymbol;
		if(this.prValidName(name).not) { RCLog.error(\ambi, "RAFObject layer %: a name of letters, digits and underscores".format(name)); ^nil };
		if(spec.isKindOf(Event).not) { RCLog.error(\ambi, "RAFObject layer %: the spec must be an Event".format(name)); ^nil };
		s = spec.copy;
		s.keys.do { |k| if(keys.includes(k).not) { RCLog.error(\ambi, "RAFObject layer %: unknown spec key %".format(name, k)); ^nil } };
		if(s[\nTaps].isInteger.not or: { s[\nTaps] < 1 }) { RCLog.error(\ambi, "RAFObject layer %: nTaps must be an Integer of 1 or more".format(name)); ^nil };
		if(s[\process].isKindOf(Function).not) { RCLog.error(\ambi, "RAFObject layer %: process must be a Function".format(name)); ^nil };
		s[\controls] = s[\controls] ? []; s[\lang] = s[\lang] ? []; s[\arrays] = s[\arrays] ? [];
		s[\buffers] = s[\buffers] ? []; s[\needs] = s[\needs] ? []; s[\tapOrder] = s[\tapOrder] ? \none;
		s[\trigger] = s[\trigger] ? false; s[\fbChannels] = s[\fbChannels] ? 0; s[\doc] = s[\doc] ? "";
		names = RCUtil.kvKeys(s[\controls]) ++ RCUtil.kvKeys(s[\lang]) ++ RCUtil.kvKeys(s[\arrays])
			++ s[\buffers].collect { |b| b[\control] } ++ s[\needs];
		names.do { |n|
			if(n.isKindOf(Symbol).not or: { RCUtil.isReservedKey(n) } or: { this.prValidName(n).not }) {
				RCLog.error(\ambi, "RAFObject layer %: % is not a usable name (a method of Event, or not a word)".format(name, n)); ^nil };
			if(RCUtil.kvIncludesKey(contractControls, n) or: { [\azs, \els, \t_trig].includes(n) }) {
				RCLog.error(\ambi, "RAFObject layer %: % is the builder's own control".format(name, n)); ^nil };
		};
		if(names.size != names.asSet.size) { RCLog.error(\ambi, "RAFObject layer %: a name used twice".format(name)); ^nil };
		registry[name] = s;
		^name
	}

	*layer { |name| ^registry[name.asSymbol] }
	*layerNames { ^registry.keys.asArray.sort }
	*removeLayer { |name| ^registry.removeAt(name.asSymbol) }
	*prValidName { |name| var s = name.asString; ^s.notEmpty and: { s.every { |c| c.isAlphaNum or: { c == $_ } } } }

	//////// chains and SynthDefs

	// a name or an Array of names → the Array of Symbols; nil (logged) on an unknown name or an empty chain
	*chain { |layers|
		var l;
		if(layers.isNil) { ^nil };
		if(layers.isString) { layers = layers.asSymbol };
		l = layers.asArray.collect(_.asSymbol);
		if(l.isEmpty) { RCLog.error(\ambi, "RAFObject: an empty chain"); ^nil };
		l.do { |n| if(registry[n].isNil) { RCLog.error(\ambi, "RAFObject: no layer % (%)".format(n, this.layerNames)); ^nil } };
		^l
	}

	// a control's name in the def: layer 1's plain, layer k's prefixed l<k>_
	*prName { |k, control| ^if(k == 0) { control.asSymbol } { ("l" ++ (k + 1) ++ "_" ++ control).asSymbol } }

	*defName { |layers, order = 3|
		var c = this.chain(layers);
		^c !? { (defPrefix ++ "_o" ++ order ++ "_" ++ c.collect(_.asString).join("_")).asSymbol }
	}

	*sumDefName { |order = 3| ^(defPrefix ++ "_o" ++ order ++ "_sum").asSymbol }

	// the def's controls and defaults (kv): the builder's, the taps' places, the trigger, then every layer's
	*controlsOf { |layers, order = 3|
		var c = this.chain(layers), n, kv;
		if(c.isNil) { ^nil };
		n = registry[c.last][\nTaps];
		kv = contractControls.copy ++ [\azs, 0.0 ! n, \els, 0.0 ! n];
		if(c.any { |l| registry[l][\trigger] == true }) { kv = kv ++ [\t_trig, 0] };
		c.do { |l, k|
			var s = registry[l];
			s[\controls].pairsDo { |cn, v| kv = kv ++ [this.prName(k, cn), v] };
			s[\arrays].pairsDo { |an, a| kv = kv ++ [this.prName(k, an), this.prArrayDefault(s, an)] };
			s[\buffers].do { |b| kv = kv ++ [this.prName(k, b[\control]), 0] };
			s[\needs].do { |nn| kv = kv ++ [this.prName(k, nn), 0] };
		};
		^kv
	}

	// the language-side params and defaults (kv), prefixed as the controls are
	*langOf { |layers|
		var c = this.chain(layers), kv = [];
		if(c.isNil) { ^nil };
		c.do { |l, k| registry[l][\lang].pairsDo { |ln, v| kv = kv ++ [this.prName(k, ln), v] } };
		^kv
	}

	// a layer's params at their defaults (an Event: lang then controls)
	*prDefaultParams { |spec|
		var p = ();
		spec[\lang].pairsDo { |k, v| p[k] = v };
		spec[\controls].pairsDo { |k, v| p[k] = v };
		^p
	}

	// an array control's values from the layer's params: its default values, else its law
	*prArrayValues { |spec, an, p|
		var a = RCUtil.kvAt(spec[\arrays], an), vals;
		if(a.isNil) { ^nil };
		vals = a[\default] ?? { a[\law] !? { |f| f.value(p, a[\n]) } } ?? { 0.0 ! a[\n] };
		vals = vals.asArray.collect(_.asFloat);
		if(vals.size != a[\n]) { vals = vals.keep(a[\n]) ++ (0.0 ! (a[\n] - vals.size).max(0)) };
		^vals
	}

	*prArrayDefault { |spec, an| ^this.prArrayValues(spec, an, this.prDefaultParams(spec)) }

	// The SynthDef of a chain: built once, added (SynthDescLib), its name kept in defNames; the name, nil
	// when it could not be built (logged). force: built again.
	*def { |layers, order = 3, force = false|
		var c = this.chain(layers), name, d, ok;
		if(c.isNil) { ^nil };
		if(order < 1 or: { order > 3 }) { RCLog.error(\ambi, "RAFObject: order % (1 to 3)".format(order)); ^nil };
		name = this.defName(c, order);
		if(force.not and: { defNames.includes(name) } and: { SynthDescLib.global[name].notNil }) { ^name };
		d = RCGuard.call(\ambi, nil) { this.prBuild(c, order, name) };
		if(d.isNil) { RCLog.error(\ambi, "RAFObject: % could not be built".format(name), force: true); ^nil };
		ok = RCGuard.call(\ambi, nil) { d.add; true };
		if(ok.isNil) { ^nil };
		if(defNames.includes(name).not) { defNames.add(name) };
		RCLog.post(\ambi, "RAFObject: % added (% bytes, % UGens)".format(name, d.asBytes.size, d.children.size));
		^name
	}

	// The sum: several objects' wet on one private bus added to the field bus under one ceiling (0.95),
	// Sanitize'd (a NaN from one object silences nothing else), gated (release 0.3 s).
	*sumDef { |order = 3, force = false|
		var name = this.sumDefName(order), n = (order + 1).squared, d, ok;
		if(force.not and: { defNames.includes(name) } and: { SynthDescLib.global[name].notNil }) { ^name };
		d = RCGuard.call(\ambi, nil) {
			SynthDef(name, { |out = 0, inBus = 0, gain = 1, gate = 1, release = 0.3|
				var in = Sanitize.ar(In.ar(inBus, n)), pk = PeakFollower.ar(in.collect(_.abs).reduce(\max), 0.9999);
				var env = EnvGen.kr(Env.asr(0.01, 1, release), gate, doneAction: 2);
				Out.ar(out, in * (0.95 / pk.max(0.95)).lag(0.02) * Lag.kr(gain, 0.1) * env);
			})
		};
		if(d.isNil) { ^nil };
		ok = RCGuard.call(\ambi, nil) { d.add; true };
		if(ok.isNil) { ^nil };
		if(defNames.includes(name).not) { defNames.add(name) };
		^name
	}

	*sumSynth { |server, order = 3| ^this.sumDef(order) !? { |n| Synth.basicNew(n, server) } }

	//////// the builder

	// the taps' shared limiter (the loudest tap's peak held under 0.95 by one gain: the taps keep their
	// balance) → [the limited signals, the gain]
	*limit { |sigs|
		var pk = PeakFollower.ar(sigs.collect(_.abs).reduce(\max), 0.9999), lim = (0.95 / pk.max(0.95)).lag(0.02);
		^[sigs * lim, lim]
	}

	// the real spherical harmonics (ACN, N3D) to order 3 at a unit vector, times sig: HoaEncodeDirection at
	// AtkHoa.refRadius, as 16 multiplies (numbers or UGens)
	*sphKr { |x, y, z, order = 3, sig = 1|
		var x2 = x.squared, y2 = y.squared, z2 = z.squared;
		^[1, 3.sqrt * y, 3.sqrt * z, 3.sqrt * x,
			15.sqrt * x * y, 15.sqrt * y * z, 5.sqrt * 0.5 * ((3 * z2) - 1), 15.sqrt * x * z, 15.sqrt * 0.5 * (x2 - y2),
			(35 / 8).sqrt * y * ((3 * x2) - y2), 105.sqrt * x * y * z, (21 / 8).sqrt * y * ((5 * z2) - 1),
			7.sqrt * 0.5 * z * ((5 * z2) - 3), (21 / 8).sqrt * x * ((5 * z2) - 1), 105.sqrt * 0.5 * z * (x2 - y2),
			(35 / 8).sqrt * x * (x2 - (3 * y2))].keep((order + 1).squared) * sig
	}

	// a control lagged by `time` seconds, but not at the start: the lag's time grows from 0 over the first 50 ms, so a
	// value set by /n_setn in the synth's first bundle is taken at once, not glided to from the def's default
	*lagKr { |x, time = 0.3| ^Lag.kr(x, Line.kr(0, time, 0.05)) }

	*prBuild { |chain, order, name|
		var specs = chain.collect { |n| registry[n] }, nOut = specs.last[\nTaps], nCh = (order + 1).squared;
		var nFb = specs.sum { |s| s[\fbChannels] }, hasTrig = specs.any { |s| s[\trigger] == true };
		var contract = contractControls, cls = this;
		^SynthDef(name, {
			var c = (), azs, els, trig, fbIn, fbOuts = List.new, fbAt = 0;
			var hoa = HoaOrder.new(order), lDeg = hoa.l, bw = hoa.beamWeights(\energy);
			var w, wl, norm, cx, cy, cz, azk, elk, beam, sig, thru = 0, taps, lim, d, gain, lpf, s, sl, sum, env;
			contract.pairsDo { |k, v| c[k] = k.kr(v) };
			azs = \azs.kr(0.0 ! nOut).asArray; els = \els.kr(0.0 ! nOut).asArray;
			trig = if(hasTrig) { \t_trig.tr(0) } { 0 };
			RandSeed.kr(Impulse.kr(0), c[\seed]);
			if(nFb > 0) { fbIn = Sanitize.ar(LocalIn.ar(nFb)).asArray * Line.kr(0, 1, 0.05) };
			// 1. the pickup: the degrees weighed, an energy beam at the centre, its on-axis gain put back to 1
			w = Lag.kr(c[\wide], 0.5).clip(0, 1);
			wl = Array.newClear(order + 1); wl[0] = 1; Array.series(order, 1).do { |l| wl[l] = wl[l - 1] * (1 - w) };
			norm = Array.series(order + 1).collect { |l| (2 * l + 1) * bw[l] * wl[l] }.sum
				/ Array.series(order + 1).collect { |l| (2 * l + 1) * bw[l] }.sum;
			cx = Lag.kr(c[\el].cos * c[\az].cos, 0.3); cy = Lag.kr(c[\el].cos * c[\az].sin, 0.3); cz = Lag.kr(c[\el].sin, 0.3);
			azk = atan2(cy, cx); elk = atan2(cz, hypot(cx, cy));
			beam = HoaDecodeDirection.ar(In.ar(c[\inBus], nCh) * lDeg.collect { |l| wl[l] }, azk, elk, AtkHoa.refRadius, \energy, order) / norm.max(1e-3);
			// the distance law (the layers may read it)
			d = Lag.kr(c[\dist], 0.25).clip(0.8, 50);
			gain = (c[\ref] / d).min(c[\maxGain]); lpf = (c[\lpfBase] / d.max(1)).clip(200, 18000);
			// 2. the layers, each on the mean of the previous one's taps
			sig = beam;
			specs.do { |spec, k|
				var ctx = (dist: d, ref: c[\ref], maxGain: c[\maxGain], lpfBase: c[\lpfBase], trig: trig, sr: SampleRate.ir, index: k);
				spec[\controls].pairsDo { |cn, v| ctx[cn] = cls.prName(k, cn).kr(v) };
				spec[\arrays].pairsDo { |an, a| ctx[an] = cls.prName(k, an).kr(cls.prArrayDefault(spec, an)).asArray };
				spec[\buffers].do { |b| ctx[b[\control]] = cls.prName(k, b[\control]).kr(0) };
				spec[\needs].do { |nn| ctx[nn] = cls.prName(k, nn).kr(0) };
				if(spec[\fbChannels] > 0) { ctx[\fb] = fbIn.copyRange(fbAt, fbAt + spec[\fbChannels] - 1); fbAt = fbAt + spec[\fbChannels] };
				#taps, lim = spec[\process].value(sig, spec[\nTaps], ctx);
				taps = taps.asArray;
				if(taps.size != spec[\nTaps]) { Error("RAFObject layer %: % taps, nTaps %".format(chain[k], taps.size, spec[\nTaps])).throw };
				if(spec[\fbChannels] > 0) {
					var fo = ctx[\fbOut].asArray;
					if(fo.size != spec[\fbChannels]) { Error("RAFObject layer %: fbOut has % signals, fbChannels %".format(chain[k], fo.size, spec[\fbChannels])).throw };
					fbOuts.addAll(fo);
				};
				if(k < (specs.size - 1)) { sig = taps.sum / spec[\nTaps].sqrt; thru = thru + sig };
			};
			if(nFb > 0) { LocalOut.ar(fbOuts.asArray) };
			taps = taps.collect { |t| t + (thru * c[\through] / nOut.sqrt) };
			// 3. the output: the source's law, each tap at its place, the degrees weighed, the near field
			s = Lag.kr(c[\spread], 0.5).clip(0, 1);
			sl = Array.newClear(order + 1); sl[0] = 1; Array.series(order, 1).do { |l| sl[l] = sl[l - 1] * (1 - s) };
			sum = Array.fill(nOut, { |k|
				var tx = cls.lagKr(els[k].cos * azs[k].cos, 0.3), ty = cls.lagKr(els[k].cos * azs[k].sin, 0.3), tz = cls.lagKr(els[k].sin, 0.3);
				var len = (tx.squared + ty.squared + tz.squared).sqrt.max(1e-6);
				cls.sphKr(tx / len, ty / len, tz / len, order, LPF.ar(taps[k] * gain, lpf))
			}).sum * lDeg.collect { |l| sl[l] };
			sum = HoaNFCtrl.ar(sum, d, AtkHoa.refRadius, order);
			sum = LeakDC.ar(HPF.ar(sum, 24), 0.9995);
			env = EnvGen.kr(Env.asr(c[\att], 1, c[\release]), c[\gate], doneAction: 2);
			Out.ar(c[\out], sum * Lag.kr(c[\mix], 0.1) * c[\amp] * env);
		})
	}

	//////// geometry (ATK's axes: +x front, +y left, +z up; degrees in, radians out where said)

	*dir { |azDeg = 0, elDeg = 0| var a = azDeg.degrad, e = elDeg.degrad, c = e.cos; ^[c * a.cos, c * a.sin, e.sin] }
	*azel { |v| ^[atan2(v[1], v[0]), atan2(v[2], hypot(v[0], v[1]))] }
	*azelDeg { |v| ^this.azel(v).collect(_.raddeg) }
	*normalize { |v| var l = ((v[0] * v[0]) + (v[1] * v[1]) + (v[2] * v[2])).sqrt; ^if(l < 1e-12) { [1.0, 0.0, 0.0] } { v / l } }
	*cross { |a, b| ^[(a[1] * b[2]) - (a[2] * b[1]), (a[2] * b[0]) - (a[0] * b[2]), (a[0] * b[1]) - (a[1] * b[0])] }
	*dot { |a, b| ^(a[0] * b[0]) + (a[1] * b[1]) + (a[2] * b[2]) }

	// A seeded hash in [0, 1): Park and Miller's generator (x → 16807 x mod 2^31 − 1, exact in Floats)
	// over a mix of seed, a and b, iterated twice (the Bowls rig's: the same draws as its page's)
	*hash { |seed, a = 0, b = 0|
		var x = ((((seed.asFloat * 7919) + (a.asFloat * 104729) + (b.asFloat * 1299709)).abs % 2147483646) + 1);
		x = (x * 16807) % 2147483647;
		x = (x * 16807) % 2147483647;
		^x / 2147483647
	}

	// n Gaussian pairs (Box–Muller from the hash): draws in sigma units, the same for a seed
	*gaussianDraws { |seed, n = 6|
		^Array.fill(n, { |k|
			var u1 = this.hash(seed, k, 0).max(1e-12), u2 = this.hash(seed, k, 1), r = (-2 * u1.log).sqrt;
			[r * (2pi * u2).cos, r * (2pi * u2).sin]
		})
	}

	// The taps' points: a centre (a unit vector or [az, el] degrees), a Gaussian's sigmas across and up
	// (degrees), its lean (degrees) and draws in sigma units ([gx, gy] pairs) → [azs, els] in radians. The
	// frame is the page's: across = the way azimuth grows at the centre, up = the centre × across, turned by
	// the lean; each draw wrapped onto the sphere at its angular distance (capped at pi).
	*tapPoints { |center, acrossDeg = 10, upDeg = 10, angleDeg = 0, draws|
		var c = if(center.size == 2) { this.dir(center[0], center[1]) } { this.normalize(center) };
		var e1 = if(c[2].abs > 0.9999) { [0.0, 1.0, 0.0] } { this.normalize([c[1].neg, c[0], 0.0]) }, e2 = this.cross(c, e1), a = angleDeg.degrad;
		var f1 = (e1 * a.cos) + (e2 * a.sin), f2 = (e2 * a.cos) - (e1 * a.sin);
		var pts = (draws ? []).collect { |g|
			var dx = acrossDeg.degrad * g[0], dy = upDeg.degrad * g[1], rad = hypot(dx, dy), t;
			if(rad < 1e-9) { c } { t = atan2(dy, dx); rad = rad.min(pi); (c * rad.cos) + (((f1 * t.cos) + (f2 * t.sin)) * rad.sin) }
		};
		^[pts.collect { |p| this.azel(p)[0] }, pts.collect { |p| this.azel(p)[1] }]
	}

	// the taps' places ordered: \radius by angular distance from the centre, \elevation by elevation, \none as they are
	*orderTaps { |azs, els, center, kind = \none|
		var c, idx;
		if(kind == \none or: { azs.size < 2 }) { ^[azs, els] };
		c = if(center.size == 2) { this.dir(center[0], center[1]) } { this.normalize(center) };
		idx = Array.series(azs.size);
		idx = switch(kind,
			\radius, { idx.sort { |i, j| this.dot(this.dir(azs[i].raddeg, els[i].raddeg), c) > this.dot(this.dir(azs[j].raddeg, els[j].raddeg), c) } },
			\elevation, { idx.sort { |i, j| els[i] < els[j] } },
			{ idx });
		^[idx.collect { |i| azs[i] }, idx.collect { |i| els[i] }]
	}

	// a Gaussian's sigma (degrees) as a width 0..1 (1 at refDeg and beyond)
	*widthOf { |sigmaDeg, refDeg = 60| ^(sigmaDeg / refDeg.max(1e-6)).clip(0, 1) }

	//////// messages (flat Arrays; nothing sent, nothing recorded: a rig bundles them)

	prIndex { |bus| ^if(bus.isKindOf(Bus)) { bus.index } { bus } }
	prSpecs { ^layers.collect { |l| registry[l] } }
	prSounding { ^synth.notNil and: { isReleased.not } }

	// where a key lives: (layer:, bare:, kind: \control | \array | \lang | \buffer | \need | \contract | \unknown)
	prResolve { |key|
		var k = key.asSymbol, str = k.asString, idx = 0, bare = k, m, spec;
		if(layers.isNil) { ^(kind: \unknown) };
		if(RCUtil.kvIncludesKey(contractControls, k) or: { [\azs, \els, \t_trig].includes(k) }) { ^(layer: 0, bare: k, kind: \contract) };
		m = str.findRegexp("^l([0-9]+)_(.+)$");
		if(m.size == 3) { idx = m[1][1].asInteger - 1; bare = m[2][1].asSymbol };
		if(idx < 0 or: { idx >= layers.size } or: { idx == 0 and: { m.size == 3 } and: { RCUtil.kvIncludesKey(registry[layers[0]][\controls], k) } }) {
			idx = 0; bare = k
		};
		if(idx >= layers.size) { ^(kind: \unknown) };
		spec = registry[layers[idx]];
		case
		{ RCUtil.kvIncludesKey(spec[\controls], bare) } { ^(layer: idx, bare: bare, kind: \control) }
		{ RCUtil.kvIncludesKey(spec[\arrays], bare) } { ^(layer: idx, bare: bare, kind: \array) }
		{ RCUtil.kvIncludesKey(spec[\lang], bare) } { ^(layer: idx, bare: bare, kind: \lang) }
		{ spec[\buffers].any { |b| b[\control] == bare } } { ^(layer: idx, bare: bare, kind: \buffer) }
		{ spec[\needs].includes(bare) } { ^(layer: idx, bare: bare, kind: \need) };
		^(kind: \unknown)
	}

	// a layer's params now (an Event by bare names: the defaults under what was given)
	prLayerParams { |k|
		var spec = registry[layers[k]], p = this.class.prDefaultParams(spec);
		params.keysValuesDo { |key, v| var r = this.prResolve(key); if(r[\layer] == k and: { [\control, \lang].includes(r[\kind]) }) { p[r[\bare]] = v } };
		^p
	}

	prArrayValues { |k, an| ^this.class.prArrayValues(registry[layers[k]], an, this.prLayerParams(k)) }

	// The object started: its buffers allocated (/b_alloc, four arguments), its synth made (/s_new with the
	// scalar params; an Array value goes by /n_setn after), the taps' places and every array control set.
	// args: more params (kv or Event). Refused ([] and a log) without its layers, while sounding, when a
	// layer's need (a bus index) is missing, or when the def cannot be built.
	newMsgs { |target, addAction = \addToHead, args|
		var msgs = List.new, specs, missing, sd, scalars, arrays = IdentityDictionary.new, keys;
		if(layers.isNil) { RCLog.error(\ambi, "%: no layers, nothing to start".format(this)); ^[] };
		if(this.prSounding) { RCLog.warn(\ambi, "%: already started, release it first".format(this)); ^[] };
		defName = this.class.def(layers, order);
		if(defName.isNil) { ^[] };
		RCUtil.asKV(args).pairsDo { |k, v| params[k.asSymbol] = v };
		specs = this.prSpecs;
		missing = specs.collect { |s, k| s[\needs].collect { |nn| this.class.prName(k, nn) } }.flatten.detect { |f| params[f].isNumber.not };
		if(missing.notNil) { RCLog.error(\ambi, "%: needs % (a bus index): no synth".format(this, missing), force: true); ^[] };
		specs.do { |s, k|
			s[\buffers].do { |b|
				var f = this.class.prName(k, b[\control]), buf = Buffer.new(server, b[\frames], b[\channels]);
				buffers[f] = buf;
				msgs.add(['/b_alloc', buf.bufnum, b[\frames].asInteger, b[\channels].asInteger]);
				params[f] = buf.bufnum;
			};
		};
		sd = IdentityDictionary.new;
		[\out, this.prIndex(outBus), \inBus, this.prIndex(inBus), \az, az, \el, el, \wide, wide, \spread, spread, \dist, dist].pairsDo { |k, v| sd[k] = v };
		params.keys.asArray.sort.do { |k|
			var v = params[k], r = this.prResolve(k);
			case
			{ r[\kind] == \unknown } { RCLog.warn(\ambi, "%: unknown param % dropped".format(this, k)) }
			{ r[\kind] == \lang } { }
			{ v.isKindOf(SequenceableCollection) and: { v.isString.not } } {
				if(r[\kind] == \array) { arrays[k] = v } { RCLog.warn(\ambi, "%: % is not an array control".format(this, k)) } }
			{ v.isNumber } { sd[k] = v.asFloat; this.prNoteScalar(k, v) }
			{ RCLog.warn(\ambi, "%: % takes a number, not %".format(this, k, v)) };
		};
		scalars = [\out, \inBus, \az, \el, \wide, \spread, \dist].collect { |k| [k, sd.removeAt(k)] }.flatten
			++ sd.keys.asArray.sort.collect { |k| [k, sd[k]] }.flatten;
		synth = Synth.basicNew(defName, server);
		msgs.add(synth.newMsg(target, scalars, addAction));
		msgs.add(synth.setnMsg(\azs, azs.collect(_.asFloat)));
		msgs.add(synth.setnMsg(\els, els.collect(_.asFloat)));
		specs.do { |s, k|
			s[\arrays].pairsDo { |an, a|
				var f = this.class.prName(k, an), vals = arrays[f] ?? { this.prArrayValues(k, an) };
				vals = vals.asArray.collect(_.asFloat);
				if(vals.size != a[\n]) { RCLog.warn(\ambi, "%: % takes % values, got %".format(this, f, a[\n], vals.size)); vals = vals.keep(a[\n]) ++ (0.0 ! (a[\n] - vals.size).max(0)) };
				msgs.add(synth.setnMsg(f, vals));
			};
		};
		isStarted = true; isReleased = false; isFreed = false; group = target;
		^msgs.asArray
	}

	prNoteScalar { |k, v|
		switch(k, \az, { az = v.asFloat }, \el, { el = v.asFloat }, \wide, { wide = v.asFloat }, \spread, { spread = v.asFloat }, \dist, { dist = v.asFloat });
	}

	// Params set live (kv): the scalar controls in one /n_set, an array control by /n_setn (exactly its n
	// values), a language param stored then the arrays that read it recomputed; unknown keys and keys fixed
	// at the start (buffers, needs) warned and skipped. [] when not sounding.
	setMsgs { |... kv|
		var sets = List.new, msgs = List.new, redo = List.new;
		if(this.prSounding.not) { RCLog.warn(\ambi, "%: not sounding, nothing to set".format(this)); ^[] };
		kv.pairsDo { |k, v|
			var key = k.asSymbol, r = this.prResolve(key);
			case
			{ r[\kind] == \unknown } { RCLog.warn(\ambi, "%: unknown param %".format(this, key)) }
			{ r[\kind] == \lang } {
				params[key] = v;
				registry[layers[r[\layer]]][\arrays].pairsDo { |an, a| if((a[\from] ? []).includes(r[\bare])) { redo.add([r[\layer], an]) } } }
			{ r[\kind] == \array } { this.setnMsg(key, v) !? { |m| msgs.add(m) } }
			{ [\buffer, \need].includes(r[\kind]) } { RCLog.warn(\ambi, "%: % is fixed at the start".format(this, key)) }
			{ v.isNumber } {
				params[key] = v; sets.add(key); sets.add(v.asFloat); this.prNoteScalar(key, v);
				if(r[\kind] == \control) {
					registry[layers[r[\layer]]][\arrays].pairsDo { |an, a| if((a[\from] ? []).includes(r[\bare])) { redo.add([r[\layer], an]) } } } }
			{ RCLog.warn(\ambi, "%: % takes a number, not %".format(this, key, v)) };
		};
		redo.asSet.do { |pair|
			var f = this.class.prName(pair[0], pair[1]), vals = this.prArrayValues(pair[0], pair[1]);
			params.removeAt(f);
			msgs.add(synth.setnMsg(f, vals));
		};
		if(sets.notEmpty) { msgs.addFirst(synth.setMsg(*sets.asArray)) };
		^msgs.asArray
	}

	// one array control's values (exactly its n) → its /n_setn, nil when refused
	setnMsg { |key, values|
		var k = key.asSymbol, r = this.prResolve(k), a, vals;
		if(this.prSounding.not) { RCLog.warn(\ambi, "%: not sounding, nothing to set".format(this)); ^nil };
		if(r[\kind] != \array) { RCLog.warn(\ambi, "%: % is not an array control".format(this, k)); ^nil };
		a = RCUtil.kvAt(registry[layers[r[\layer]]][\arrays], r[\bare]);
		vals = values.asArray;
		if(vals.size != a[\n]) { RCLog.warn(\ambi, "%: % takes % values, got %".format(this, k, a[\n], vals.size)); ^nil };
		vals = vals.collect(_.asFloat);
		params[k] = vals;
		^synth.setnMsg(k, vals)
	}

	// the centre (radians) → [[/n_set az el]] ([] when not sounding; the centre is kept either way)
	centerMsgs { |azRad, elRad|
		az = azRad.asFloat; el = elRad.asFloat;
		^if(this.prSounding) { [synth.setMsg(\az, az, \el, el)] } { [] }
	}

	// the taps' places (radians, exactly nTaps each) → [/n_setn azs, /n_setn els]
	tapsMsgs { |azsArg, elsArg|
		var a = azsArg.asArray, e = elsArg.asArray;
		if(a.size != nTaps or: { e.size != nTaps }) { RCLog.error(\ambi, "%: % taps, got % azimuths and % elevations".format(this, nTaps, a.size, e.size)); ^[] };
		azs = a.collect(_.asFloat); els = e.collect(_.asFloat);
		^if(this.prSounding) { [synth.setnMsg(\azs, azs), synth.setnMsg(\els, els)] } { [] }
	}

	// the distance (metres) → one /n_set (nil when not sounding; the distance is kept)
	distMsg { |d|
		dist = d.asFloat;
		^if(this.prSounding) { synth.setMsg(\dist, dist) } { nil }
	}

	// a trigger for the layers that take one (freeze: the hold refreshed) → one /n_set; nil when none does
	trigMsg {
		^if(this.prSounding and: { this.prSpecs.any { |s| s[\trigger] == true } }) { synth.setMsg(\t_trig, 1) } { nil }
	}

	// the synth released over fade seconds (nil: its own release) → [[/n_set release gate]]; [] when not sounding
	releaseMsgs { |fade|
		if(this.prSounding.not) { ^[] };
		isReleased = true;
		^[if(fade.isNil) { synth.setMsg(\gate, 0) } { synth.setMsg(\release, fade.asFloat, \gate, 0) }]
	}

	// the buffers' /b_free (and /n_free of a synth not released when kill); then, once sent, didFree
	freeMsgs { |kill = false|
		var m = List.new;
		if(kill and: { synth.notNil } and: { isReleased.not }) { m.add(synth.freeMsg); isReleased = true };
		buffers.do { |b| m.add(['/b_free', b.bufnum]) };
		^m.asArray
	}

	// after freeMsgs went out: the buffer numbers given back, the synth forgotten
	didFree {
		buffers.do { |b| server.bufferAllocator.free(b.bufnum) };
		buffers.clear;
		synth = nil; isFreed = true; isStarted = false;
	}

	//////// online: the same, sent at the server's latency, recorded (RETap)

	start { |latency, target, addAction = \addToHead, args|
		if(RETap.active) { RETap.action(this, \start, [latency, target, addAction, args]) };
		^this.prStart(latency, target, addAction, args)
	}
	prStart { |latency, target, addAction, args|
		var msgs = this.newMsgs(target ?? { server.defaultGroup }, addAction, args);
		if(msgs.notEmpty) { this.prSend(msgs, latency) };
		^this
	}
	set { |key, value, latency| if(RETap.active) { RETap.action(this, \set, [key, value, latency]) }; ^this.prSet([key, value], latency) }
	setAll { |kv, latency| if(RETap.active) { RETap.action(this, \setAll, [kv, latency]) }; ^this.prSet(RCUtil.asKV(kv), latency) }
	prSet { |kv, latency| var m = this.setMsgs(*kv); if(m.notEmpty) { this.prSend(m, latency) }; ^this }
	center { |azRad, elRad, latency|
		var m;
		if(RETap.active) { RETap.action(this, \center, [azRad, elRad, latency]) };
		m = this.centerMsgs(azRad, elRad); if(m.notEmpty) { this.prSend(m, latency) }; ^this
	}
	taps { |azsArg, elsArg, latency|
		var m;
		if(RETap.active) { RETap.action(this, \taps, [azsArg, elsArg, latency]) };
		m = this.tapsMsgs(azsArg, elsArg); if(m.notEmpty) { this.prSend(m, latency) }; ^this
	}
	distance { |d, latency|
		var m;
		if(RETap.active) { RETap.action(this, \distance, [d, latency]) };
		m = this.distMsg(d); m !? { this.prSend([m], latency) }; ^this
	}
	trig { |latency|
		var m;
		if(RETap.active) { RETap.action(this, \trig, [latency]) };
		m = this.trigMsg; m !? { this.prSend([m], latency) }; ^this
	}
	release { |fade, latency| if(RETap.active) { RETap.action(this, \release, [fade, latency]) }; ^this.prRelease(fade, latency) }
	prRelease { |fade, latency|
		var m = this.releaseMsgs(fade), lat = latency ?? { server.latency ? 0 };
		if(m.isEmpty) { ^this };
		this.prSend(m, lat);
		this.prLater((fade ? 0) + lat + 0.5, { this.prFree(lat) });
		^this
	}
	free { |latency| if(RETap.active) { RETap.action(this, \free, [latency]) }; ^this.prFree(latency) }
	prFree { |latency|
		var m;
		if(isFreed) { ^this };
		m = this.freeMsgs(kill: true);
		if(m.notEmpty) { this.prSend(m, latency) };
		this.didFree;
		^this
	}
	prSend { |msgs, latency|
		RCGuard.call(\ambi, nil) { server.sendBundle(latency ?? { server.latency }, *msgs) }
	}
	// later, on the offline clock when that is the thread's (a render), else on the SystemClock
	prLater { |delay, func|
		var clock = thisThread.clock;
		if(clock.isKindOf(REOfflineClock)) { clock.sched(delay * clock.tempo, { func.value; nil }) } { SystemClock.sched(delay, { func.value; nil }) };
	}

	isOffline { ^REOfflineTap.isOffline(server) }

	printOn { |stream|
		stream << "RAFObject(" << name << ": " << (layers !? { |l| l.collect(_.asString).join(" > ") } ? "none") << ", " << nTaps << " taps, "
			<< if(isFreed) { "freed" } { if(isReleased) { "released" } { if(isStarted) { "started" } { "unstarted" } } } << ")"
	}
}
