// reAmbi — an ambisonic output chain: signal → [transformer] → [inserted stages] → decoder,
// each stage a NodeProxy in its own Group, decoded to AmbiX or binaural and
// played on an output bus. Replaces the ~start_ambi_output / ~rc_make_ambi_output
// functions of the Biomusic pieces.
//
//   ~song.addOutput(\main, RAOutputChain(s, 3, decode: \ambix, outBus: 0));
//   ~song.ambi.numChannels;                       // valid before build: 16
//   ~song.buildOutputs;                           // after makeGroups: synchronous, race-free
//   ~song.ambi.signal.source = { ... };           // the instrument-file idiom
//   ~reverb = ~song.ambi.insert(\reverb, { |in| ... \in.ar(0 ! 16) ... });   // before the decoder
//   ~song.ambi.addMonitor(RAStereoMonitor(16, 18));
//   ~song.ambi.clear;                             // back to the unbuilt configuration
//
// The constructor allocates nothing (the server resets its allocators at
// every boot); build creates one Group per stage in order (message order is
// node order: no moves, no waits), places each proxy in its group, links the
// stages (nodeMaps only, no synth exists yet), then sets the sources and
// plays the decoder into its own group after the stages. With the server
// stopped, build works client-side (tests, NRT) and sends nothing.
// insert / remove / move re-map only the links they touch. clear frees the
// groups first (immediate /n_free) so that the proxies send nothing late.

RAOutputChain {
	classvar binauralState;      // server name → order → (status:, actions:)
	classvar bootHookInstalled = false;

	var <server, <order, <name;
	var <decode, <outBus, <play, <useTransformer, <headphoneModel;
	var <refRadius, <binauralRadius, <foaRefRadius, <fadeTime, <placeholder;
	var <stageNames, <proxies, <stageGroups, <stageSources, <playGroup, <parentGroup;
	var <taps, <monitor, <isBuilt = false, <isOffline = false;

	*new { |server, order = 3, name = \main, decode = \binaural, outBus = 0, play = true, useTransformer = true,
		headphoneModel, refRadius = 10.0, binauralRadius = 3.25, foaRefRadius = 10.0, fadeTime = 1, placeholder = true|
		^super.new.initRAOutputChain(server, order, name, decode, outBus, play, useTransformer, headphoneModel,
			refRadius, binauralRadius, foaRefRadius, fadeTime, placeholder)
	}

	initRAOutputChain { |serverarg, orderarg, namearg, decodearg, outBusarg, playarg, useTransformerarg, headphoneModelarg,
		refRadiusarg, binauralRadiusarg, foaRefRadiusarg, fadeTimearg, placeholderarg|
		server = serverarg ? Server.default;
		order = orderarg;
		name = namearg.asSymbol;
		decode = decodearg.asSymbol;
		outBus = outBusarg;
		play = playarg;
		useTransformer = useTransformerarg;
		headphoneModel = headphoneModelarg;
		refRadius = refRadiusarg;
		binauralRadius = binauralRadiusarg;
		foaRefRadius = foaRefRadiusarg;
		fadeTime = fadeTimearg;
		placeholder = placeholderarg;
		proxies = IdentityDictionary.new;
		stageGroups = IdentityDictionary.new;
		stageSources = IdentityDictionary.new;
		taps = IdentityDictionary.new;
		stageNames = this.prBaseStages;
	}

	prBaseStages { ^[\signal] ++ (if(useTransformer) { [\transformer] } { [] }) ++ [\decoder] }

	//////// configuration (takes effect at the next build)

	numChannels { ^(order + 1).squared }
	playChannels { ^if(decode == \binaural) { 2 } { this.numChannels } }

	prConfig { |what|
		if(isBuilt) { RCLog.info(\ambi, "% %: takes effect at the next build".format(name, what)) };
	}

	decode_ { |d| decode = d.asSymbol; this.prConfig(\decode) }
	outBus_ { |b| outBus = b; this.prConfig(\outBus) }
	play_ { |p| play = p; this.prConfig(\play) }
	headphoneModel_ { |m| headphoneModel = m; this.prConfig(\headphoneModel) }
	refRadius_ { |r| refRadius = r; this.prConfig(\refRadius) }
	binauralRadius_ { |r| binauralRadius = r; this.prConfig(\binauralRadius) }
	foaRefRadius_ { |r| foaRefRadius = r }
	fadeTime_ { |t| fadeTime = t; proxies.do { |p| p.fadeTime = t } }
	placeholder_ { |p| placeholder = p; this.prConfig(\placeholder) }
	useTransformer_ { |bool|
		if(isBuilt) { RCLog.error(\ambi, "% useTransformer: clear the chain first".format(name)); ^this };
		useTransformer = bool;
		stageNames = this.prBaseStages;   // stages recorded before build are dropped
		stageSources.clear;
		proxies.clear;
	}

	//////// stages

	signal { ^proxies[\signal] }
	transformer { ^proxies[\transformer] }
	decoder { ^proxies[\decoder] }
	stage { |stageName| ^proxies[stageName.asSymbol] }
	stages { ^stageNames.collect { |n| [n, proxies[n]] } }
	lastStage { ^stageNames[stageNames.size - 2] }   // the stage feeding the decoder

	// The bus of a stage; \monitor is the stereo monitor's proxy bus.
	bus { |stageName|
		stageName = stageName.asSymbol;
		if(stageName == \monitor) { ^monitor !? { |m| m.proxy !? (_.bus) } };
		^proxies[stageName] !? (_.bus)
	}

	//////// build

	build { |parentGrouparg|
		var online, prev;
		if(isBuilt) { this.clear };
		parentGroup = parentGrouparg ? parentGroup;
		online = server.serverRunning;
		isOffline = online.not;
		if(isOffline) { RCLog.warn(\ambi, "% built offline (server not running): nothing sent".format(name)) };
		// 1. one group per stage, in order, then the play group
		stageNames.do { |n|
			var g = if(online) {
				if(prev.isNil) { Group.tail(parentGroup ?? { server.defaultGroup }) } { Group.after(prev) }
			} { Group.basicNew(server) };
			stageGroups[n] = g;
			prev = g;
		};
		playGroup = if(online) { Group.after(prev) } { Group.basicNew(server) };
		// 2. proxies, each placed in its group before anything is sent
		stageNames.do { |n|
			var p = proxies[n] ?? { NodeProxy.new(server, \audio, this.numChannels) };
			p.fadeTime = fadeTime;
			if(online) { p.group_(stageGroups[n]) };
			proxies[n] = p;
		};
		// 3. links first: no synth exists yet, so nothing crossfades
		stageNames.doAdjacentPairs { |a, b| proxies[a] <>> proxies[b] };
		// 4. sources
		if(placeholder) { proxies[\signal].source = this.prPlaceholderSource };
		if(useTransformer) { proxies[\transformer].source = this.prIdentitySource };
		stageNames.do { |n| stageSources[n] !? { |src| proxies[n].source = src } };
		this.prSetDecoderSource(online);
		// 5. play, monitor, taps
		if(online and: { play }) { proxies[\decoder].play(outBus, this.playChannels, playGroup) };
		isBuilt = true;
		monitor !? { |m| m.build(this) };
		taps.keys.copy.do { |n| this.prRetap(n) };
		RCLog.post(\ambi, "% chain ready on bus % (%, order %)".format(name, outBus, decode, order));
	}

	prIdentitySource { var n = this.numChannels; ^{ \in.ar(0 ! n) } }

	// the original's placeholder: a faint pink noise circling the listener
	prPlaceholderSource {
		var ord = order;
		^{ HOAEncoder.ar(ord, PinkNoise.ar(0.001), SinOsc.ar(0.1, 0, pi * 0.999), SinOsc.ar(0.2, 0, pi * 0.999 * 0.4)) }
	}

	prBinauralSource {
		var n = this.numChannels, ord = order, radius = binauralRadius, model = headphoneModel;
		^{ var in = \in.ar(0 ! n); HOABinaural.ar(ord, HoaNFCtrl.ar(in, AtkHoa.refRadius, radius, ord), headphoneCorrection: model) }
	}

	prAmbixSource {
		var n = this.numChannels, ord = order, radius = refRadius;
		var matrix = HoaMatrixDecoder.newFormat(\ambix, ord);
		^{ var in = \in.ar(0 ! n); HoaDecodeMatrix.ar(HoaNFCtrl.ar(in, AtkHoa.refRadius, radius, ord), matrix) }
	}

	prSetDecoderSource { |online|
		var dec = proxies[\decoder];
		switch(decode,
			\ambix, { dec.source = this.prAmbixSource },
			\raw, { dec.source = this.prIdentitySource },
			\binaural, {
				case
				{ this.class.binauralReady(server, order) } { dec.source = this.prBinauralSource }
				{ online } {
					// the proxy, its group and links exist now; its synth arrives with the IRs
					this.class.loadBinauralIRs(server, order, {
						if(isBuilt and: { decode == \binaural } and: { proxies[\decoder] === dec }) {
							dec.source = this.prBinauralSource;
							RCLog.post(\ambi, "% binaural IRs loaded, decoder up".format(name));
						};
					});
				}
				{	// the SynthDef is built on put even offline, and HOABinaural.ar needs the IR buffers
					RCLog.warn(\ambi, "% binaural IRs need a running server: identity decoder offline".format(name));
					dec.source = this.prIdentitySource;
				};
			},
			{
				RCLog.error(\ambi, "% unknown decode % (ambix, binaural or raw): identity decoder".format(name, decode));
				dec.source = this.prIdentitySource;
			}
		);
	}

	//////// live changes

	prInsertIndex { |after, before|
		var i, decoderIdx = stageNames.indexOf(\decoder);
		if(after.notNil) {
			i = stageNames.indexOf(after.asSymbol);
			if(i.isNil) { RCLog.warn(\ambi, "% insert: no stage %, inserting before the decoder".format(name, after)); ^decoderIdx };
			^(i + 1).min(decoderIdx)
		};
		if(before.notNil) {
			i = stageNames.indexOf(before.asSymbol);
			if(i.isNil) { RCLog.warn(\ambi, "% insert: no stage %, inserting before the decoder".format(name, before)); ^decoderIdx };
			^i.max(1)
		};
		^decoderIdx
	}

	// A new stage: source is a Function (or any proxy source; the chain makes and
	// owns the proxy) or an existing NodeProxy (adopted: its group becomes the
	// stage group). Position: after: / before: a stage name, default right before
	// the decoder. Returns the stage proxy (nil when refused). Before build the
	// stage is recorded and realized by build.
	insert { |stageName, source, after, before|
		var i, prevName, nextName, proxy, adopted;
		stageName = stageName.asSymbol;
		if(#[\signal, \decoder].includes(stageName)) { RCLog.error(\ambi, "% insert: % is a fixed stage".format(name, stageName)); ^nil };
		if(stageNames.includes(stageName)) { this.remove(stageName, 0) };
		i = this.prInsertIndex(after, before);
		stageNames = stageNames.insert(i, stageName);
		adopted = source.isKindOf(NodeProxy);
		if(adopted) { proxies[stageName] = source } { stageSources[stageName] = source };
		if(isBuilt.not) { ^proxies[stageName] };
		prevName = stageNames[i - 1];
		nextName = stageNames[i + 1];
		stageGroups[stageName] = if(isOffline) { Group.basicNew(server) } { Group.after(stageGroups[prevName]) };
		proxy = proxies[stageName] ?? { NodeProxy.new(server, \audio, this.numChannels).fadeTime_(fadeTime) };
		if(isOffline.not) {
			var old = proxy.group;
			proxy.group_(stageGroups[stageName]);   // a playing adopted proxy is re-sent into its stage group (crossfade)
			if(old.notNil and: { old.isPlaying } and: { old !== server.defaultGroup }) {
				server.sendBundle(fadeTime + (server.latency ? 0) + 0.05, old.freeMsg);   // its old, now empty group
			};
		};
		proxies[stageName] = proxy;
		proxies[prevName] <>> proxy;                                             // incoming link before the source: one synth
		stageSources[stageName] !? { |src| if(adopted.not) { proxy.source = src } };
		proxy <>> proxies[nextName];                                             // the successor crossfades to its new input
		this.prRetapLast;
		RCLog.post(\ambi, "% stage % inserted: %".format(name, stageName, stageNames));
		^proxy
	}

	// Unlink a stage, connect its neighbours, clear its proxy (with a fade) and free its group.
	remove { |stageName, fadeTimearg|
		var i, proxy, group;
		stageName = stageName.asSymbol;
		if(#[\signal, \decoder].includes(stageName)) { RCLog.error(\ambi, "% remove: % is a fixed stage".format(name, stageName)); ^this };
		i = stageNames.indexOf(stageName);
		if(i.isNil) { RCLog.warn(\ambi, "% remove: no stage %".format(name, stageName)); ^this };
		^this.prRemoveAt(stageName, i, fadeTimearg ? fadeTime)
	}

	prRemoveAt { |stageName, i, dt|
		var prevName = stageNames[i - 1], nextName = stageNames[i + 1];
		var proxy = proxies[stageName], group = stageGroups[stageName];
		var send = server.serverRunning and: { isOffline.not };
		stageNames = stageNames.reject { |n| n == stageName };
		taps.keysValuesDo { |n, spec|
			if(spec[1] == stageName) {
				RCLog.warn(\ambi, "% tap % read the removed stage %, unmapped".format(name, n, stageName));
				RCGuard.call(\ambi, nil) { spec[0].unmap(\in) };
				spec[1] = \last;
			};
		};
		if(isBuilt) {
			proxies[prevName] <>> proxies[nextName];
			proxy !? { |p|
				RCGuard.call(\ambi, nil) { p.clear(dt) };
			};
			group !? { |g|
				if(send) {
					server.sendBundle(dt + (server.latency ? 0) + 0.05, g.freeMsg);
				} {
					g.free(false);
				};
			};
			this.prRetapLast;
		};
		proxies.removeAt(stageName);
		stageGroups.removeAt(stageName);
		stageSources.removeAt(stageName);
		RCLog.post(\ambi, "% stage % removed: %".format(name, stageName, stageNames));
	}

	// Reorder a live inserted stage: group move plus the three affected links.
	move { |stageName, after, before|
		var i, target, from;
		stageName = stageName.asSymbol;
		if(#[\signal, \decoder].includes(stageName)) { RCLog.error(\ambi, "% move: % is a fixed stage".format(name, stageName)); ^this };
		from = stageNames.indexOf(stageName);
		if(from.isNil) { RCLog.warn(\ambi, "% move: no stage %".format(name, stageName)); ^this };
		stageNames = stageNames.reject { |n| n == stageName };
		i = this.prInsertIndex(after, before);
		stageNames = stageNames.insert(i, stageName);
		if(isBuilt.not or: { i == from }) { ^this };
		// old neighbours join, the stage settles between its new ones
		this.prLinkAround(from.min(i), from.max(i));
		if(isOffline.not) {
			stageGroups[stageName].moveAfter(stageGroups[stageNames[i - 1]]);
		};
		this.prRetapLast;
		RCLog.post(\ambi, "% stage % moved: %".format(name, stageName, stageNames));
	}

	// re-map every link from index lo - 1 to hi + 1 (bounded to the chain)
	prLinkAround { |lo, hi|
		var a = (lo - 1).max(0), b = (hi + 1).min(stageNames.size - 1);
		(a..(b - 1)).do { |k| proxies[stageNames[k]] <>> proxies[stageNames[k + 1]] };
	}

	//////// taps and monitors

	// Map proxy's \in from a stage (\last = the stage feeding the decoder, followed across changes).
	tap { |tapName, proxy, from = \last|
		tapName = tapName.asSymbol;
		from = from.asSymbol;
		taps[tapName] = [proxy, from];
		if(isBuilt) { this.prRetap(tapName) };
		^proxy
	}

	untap { |tapName|
		taps.removeAt(tapName.asSymbol) !? { |spec| RCGuard.call(\ambi, nil) { spec[0].unmap(\in) } };
	}

	prRetap { |tapName|
		var spec = taps[tapName];
		var stageName = if(spec[1] == \last) { this.lastStage } { spec[1] };
		var source = proxies[stageName];
		if(source.isNil) { RCLog.warn(\ambi, "% tap %: no stage %".format(name, tapName, stageName)); ^this };
		source <>> spec[0];
	}

	prRetapLast {
		taps.keysValuesDo { |n, spec| if(spec[1] == \last) { this.prRetap(n) } };
	}

	// Register a stereo monitor (built now if the chain is, rebuilt with the chain).
	addMonitor { |monitorarg|
		monitor !? { |old| if(old !== monitorarg) { RCGuard.call(\ambi, nil) { old.clear } } };
		monitor = monitorarg;
		if(isBuilt) { monitor.build(this) };
		^monitor
	}

	removeMonitor {
		monitor !? { |m| RCGuard.call(\ambi, nil) { m.clear } };
		monitor = nil;
	}

	//////// teardown

	// Frees everything: monitor, taps, the play group, the stage groups (back to
	// front, immediate messages), then the proxies (nothing left to send) and
	// their buses. Inserted stages and taps are dropped, the monitor stays
	// registered, the configuration is kept: the chain is unbuilt again.
	clear {
		var send;
		if(isBuilt.not) { ^this };
		send = server.serverRunning and: { isOffline.not };
		monitor !? { |m| RCGuard.call(\ambi, nil) { m.clear } };
		taps.keysValuesDo { |n, spec| RCGuard.call(\ambi, nil) { spec[0].unmap(\in) } };
		taps.clear;
		proxies[\decoder] !? { |d| d.monitorGroup !? { |g| RCGuard.call(\ambi, nil) { g.free(send) } } };
		playGroup !? { |g| RCGuard.call(\ambi, nil) { g.free(send) } };
		playGroup = nil;
		stageNames.reverseDo { |n| stageGroups[n] !? { |g| RCGuard.call(\ambi, nil) { g.free(send) } } };
		stageNames.do { |n| proxies[n] !? { |p| RCGuard.call(\ambi, nil) { p.clear(0) } } };
		stageNames = this.prBaseStages;
		proxies.clear;
		stageGroups.clear;
		stageSources.clear;
		isBuilt = false;
		RCLog.post(\ambi, "% chain cleared".format(name));
	}

	//////// binaural IRs (per order, with a completion callback)

	*prBinauralEntry { |server, order|
		var byServer;
		binauralState = binauralState ?? { IdentityDictionary.new };
		byServer = binauralState[server.name] ?? { var d = IdentityDictionary.new; binauralState[server.name] = d; d };
		^byServer[order] ?? { var e = (status: \none, actions: List.new); byServer[order] = e; e }
	}

	*binauralReady { |server, order = 3| ^this.prBinauralEntry(server, order)[\status] == \ready }

	// Load the IRs of one order (the quark's loader loads all seven at once and
	// offers no callback). action runs once the last buffer is in; a reboot
	// forgets the cache (allocators are reset).
	*loadBinauralIRs { |server, order = 3, action|
		var entry = this.prBinauralEntry(server, order);
		var numChans = (order + 1).squared;
		var path, done = 0, buffers;
		if(entry[\status] == \ready) { action.value; ^this };
		action !? { entry[\actions].add(action) };
		if(entry[\status] == \loading) { ^this };
		if(server.serverRunning.not) { RCLog.error(\ambi, "cannot load binaural IRs: server not running"); ^this };
		path = RCGuard.call(\ambi, nil) { HOA.kernelDirsFor("", "binauralIRs")[0] ++ "irsOrd" ++ order ++ ".wav" };
		if(path.isNil or: { File.exists(path).not }) { RCLog.error(\ambi, "binaural IRs not found: %".format(path)); ^this };
		entry[\status] = \loading;
		this.prInstallBootHook;
		buffers = numChans.collect { |i|
			Buffer.readChannel(server, path, channels: [i], action: {
				done = done + 1;
				if(done == numChans) {
					entry[\status] = \ready;
					RCLog.post(\ambi, "binaural IRs of order % loaded (% buffers)".format(order, numChans));
					entry[\actions].copy.do { |f| RCGuard.call(\ambi, nil) { f.value } };
					entry[\actions].clear;
				};
			});
		};
		HOABinaural.binauralIRs = HOABinaural.binauralIRs ?? { nil ! 7 };
		HOABinaural.binauralIRs[order - 1] = buffers;
	}

	*prInstallBootHook {
		if(bootHookInstalled) { ^this };
		bootHookInstalled = true;
		ServerBoot.add({ |srv|
			binauralState !? { |s| s.removeAt(srv.name) };
			HOABinaural.binauralIRs = nil;
		});
	}

	printOn { |stream|
		stream << "RAOutputChain(" << name << ", order " << order << ", " << decode << " on " << outBus << ", " << stageNames << (if(isBuilt) { "" } { ", unbuilt" }) << ")"
	}
}
