// Offline: Server.default is not running, so build takes the client-side path
// (proxies, buses and nodeMaps exist, nothing is sent).
TestRAOutputChain : UnitTest {
	var song, savedRateLimit;

	setUp {
		RCTestSupport.bootSession;
		song = RCSong(\ra, 1);
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown {
		RCTestSupport.reset;
		RCLog.rateLimit = savedRateLimit;
	}

	logHas { |text| ^RCLog.history.any { |e| e[2].contains(text) } }

	chain { |decode = \ambix, useTransformer = true, order = 1|
		^RAOutputChain(Server.default, order, \main, decode: decode, outBus: 0, useTransformer: useTransformer)
	}

	test_configuration_before_build {
		var c = this.chain;
		this.assertEquals(c.numChannels, 4, "order 1 → 4 channels");
		this.assertEquals(this.chain(order: 3).numChannels, 16, "order 3 → 16 channels");
		this.assertEquals(c.playChannels, 4, "ambix plays every channel");
		this.assertEquals(this.chain(\binaural).playChannels, 2, "binaural plays two");
		this.assertEquals(c.stageNames, [\signal, \transformer, \decoder], "base stages");
		this.assertEquals(this.chain(useTransformer: false).stageNames, [\signal, \decoder], "no transformer");
		this.assertEquals(c.stages.collect(_.last), [nil, nil, nil], "no proxies before build");
		this.assertEquals(c.signal, nil, "signal nil before build");
		this.assert(c.isBuilt.not, "unbuilt");
	}

	test_build_offline {
		var c = this.chain.build;
		var indices;
		this.assert(c.isBuilt and: { c.isOffline }, "built offline");
		this.assert(this.logHas("built offline"), "offline build warned");
		this.assert(c.signal.notNil and: { c.transformer.notNil } and: { c.decoder.notNil }, "three proxies");
		this.assert(c.stages.every { |pair| pair[1].numChannels == 4 }, "every proxy has numChannels channels");
		indices = c.stages.collect { |pair| pair[1].bus.index };
		this.assert(indices.every(_.notNil) and: { indices.asSet.size == 3 }, "distinct buses");
		this.assert(c.decoder.nodeMap.at(\in) === c.transformer, "decoder reads the transformer");
		this.assert(c.transformer.nodeMap.at(\in) === c.signal, "transformer reads the signal");
		this.assert(c.transformer.children.includes(c.decoder), "child link registered");
		this.assert(c.bus(\decoder) === c.decoder.bus, "bus lookup");
		this.assertEquals(c.lastStage, \transformer, "last stage before the decoder");
		this.assert(c.signal.source.isKindOf(Function) and: { c.decoder.source.isKindOf(Function) }, "sources set");
		c.build;
		this.assert(c.isBuilt and: { c.decoder.nodeMap.at(\in) === c.transformer }, "a second build rebuilds cleanly");
		this.assert(this.chain(\binaural).build.decoder.source.isKindOf(Function), "binaural offline: identity decoder stands in");
		this.assert(this.logHas("identity decoder offline"), "and says so");
		this.assert(this.chain(\raw).build.decoder.source.isKindOf(Function), "raw decoder");
		c.clear;
	}

	test_insert_remove_move_and_taps {
		var c = this.chain.build;
		var tapProxy = NodeProxy.new(Server.default, \audio, 4);
		var rev = c.insert(\reverb, { \in.ar(0 ! 4) });
		var early;
		this.assertEquals(c.stageNames, [\signal, \transformer, \reverb, \decoder], "inserted before the decoder by default");
		this.assert(rev.notNil and: { c.stage(\reverb) === rev }, "stage proxy returned");
		this.assert(c.decoder.nodeMap.at(\in) === rev, "decoder re-mapped to the new stage");
		this.assert(rev.nodeMap.at(\in) === c.transformer, "new stage reads its predecessor");
		this.assert(rev.source.isKindOf(Function), "source set");
		early = c.insert(\early, { \in.ar(0 ! 4) }, after: \signal);
		this.assertEquals(c.stageNames, [\signal, \early, \transformer, \reverb, \decoder], "after: a stage");
		this.assert(c.transformer.nodeMap.at(\in) === early and: { early.nodeMap.at(\in) === c.signal }, "links around the early stage");
		this.assertEquals(c.insert(\signal, { }), nil, "fixed stage names are refused");
		this.assert(this.logHas("fixed stage"), "refusal reported");
		c.tap(\rec, tapProxy);
		this.assert(tapProxy.nodeMap.at(\in) === rev, "a tap follows the last stage");
		c.move(\reverb, after: \signal);
		this.assertEquals(c.stageNames, [\signal, \reverb, \early, \transformer, \decoder], "moved");
		this.assert(rev.nodeMap.at(\in) === c.signal and: { early.nodeMap.at(\in) === rev } and: { c.decoder.nodeMap.at(\in) === c.transformer }, "links follow the move");
		this.assert(tapProxy.nodeMap.at(\in) === c.transformer, "the tap follows the new last stage");
		c.remove(\reverb);
		this.assertEquals(c.stageNames, [\signal, \early, \transformer, \decoder], "removed");
		this.assert(early.nodeMap.at(\in) === c.signal, "neighbours re-linked");
		this.assertEquals(c.stage(\reverb), nil, "stage dropped");
		c.untap(\rec);
		this.assertEquals(tapProxy.nodeMap.at(\in), nil, "untapped");
		c.clear;
		tapProxy.clear;
	}

	test_insert_before_build_is_realized {
		var c = this.chain;
		c.insert(\reverb, { \in.ar(0 ! 4) });
		this.assertEquals(c.stageNames, [\signal, \transformer, \reverb, \decoder], "recorded unbuilt");
		c.build;
		this.assert(c.stage(\reverb).notNil and: { c.decoder.nodeMap.at(\in) === c.stage(\reverb) }, "realized by build");
		c.clear;
		this.assertEquals(c.stageNames, [\signal, \transformer, \decoder], "clear drops inserted stages");
	}

	test_stereo_monitor {
		var c = this.chain.build;
		var m = RAStereoMonitor(16, 18);
		var rev;
		c.addMonitor(m);
		this.assert(m.isBuilt and: { c.monitor === m }, "built with the chain");
		this.assertEquals(m.proxy.numChannels, 4, "monitor proxy has the chain's channels");
		this.assert(m.proxy.nodeMap.at(\in) === c.transformer, "taps the last stage");
		this.assert(c.bus(\monitor) === m.proxy.bus, "bus(\\monitor)");
		this.assertFloatEquals(m.proxy.nodeMap.at(\amp), 18.dbamp, "gain set on the proxy");
		rev = c.insert(\reverb, { \in.ar(0 ! 4) });
		this.assert(m.proxy.nodeMap.at(\in) === rev, "re-tapped after an insert");
		m.ampDb = 6;
		this.assertFloatEquals(m.proxy.nodeMap.at(\amp), 6.dbamp, "live trim");
		c.clear;
		this.assert(m.isBuilt.not and: { c.monitor === m }, "cleared with the chain, still registered");
		c.build;
		this.assert(m.isBuilt and: { m.proxy.nodeMap.at(\in) === c.transformer }, "rebuilt with the chain");
		c.clear;
	}

	test_clear_and_song_slots {
		var c = this.chain;
		var other = this.chain(\raw);
		song.addOutput(\main, c);
		this.assert(song.ambi === c, "ambi is the main output");
		this.assertEquals(song.ambi.numChannels, 4, "configuration valid before build");
		song.buildOutputs;
		this.assert(c.isBuilt and: { c.signal.notNil }, "buildOutputs builds it");
		song.addOutput(\main, other);
		this.assert(c.isBuilt.not and: { song.ambi === other }, "replacing an output clears the old one");
		song.buildOutputs;
		song.clearAll;
		this.assert(other.isBuilt.not and: { song.ambi === other }, "clearAll clears outputs but keeps them registered");
		this.assertEquals(other.stageNames, [\signal, \transformer, \decoder], "back to the base stages");
		this.assertEquals(other.signal, nil, "proxies dropped");
		song.free;
		this.assertEquals(song.outputs.size, 0, "free empties the outputs");
	}
}
