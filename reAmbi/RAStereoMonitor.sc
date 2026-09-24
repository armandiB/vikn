// reAmbi — FOA cardioid stereo decode of an RAOutputChain, for the PA or a
// stereo monitor (the ~rc_make_stereo_monitor / ~stereoDecoderOutput of the pieces).
//
//   ~song.ambi.addMonitor(RAStereoMonitor(outBus: 16, ampDb: 18, hpFreq: 5.0, angleDeg: 131/2, pattern: 0.5));
//   ~song.ambi.monitor.ampDb = 12;   // live trim
//
// The monitor taps the stage feeding the decoder (tap \monitor, re-mapped
// when stages are inserted, removed or moved), in its own group after the
// chain's play group, and plays two channels on outBus. Built and cleared
// with the chain; the configuration survives a clear.

RAStereoMonitor {
	var <outBus, <ampDb, <hpFreq, <angleDeg, <pattern, <fadeTime;
	var <chain, <proxy, <group, <playGroup, <isBuilt = false;

	*new { |outBus = 16, ampDb = 0, hpFreq = 5.0, angleDeg = 65.5, pattern = 0.5, fadeTime = 1|
		^super.new.initRAStereoMonitor(outBus, ampDb, hpFreq, angleDeg, pattern, fadeTime)
	}

	initRAStereoMonitor { |outBusarg, ampDbarg, hpFreqarg, angleDegarg, patternarg, fadeTimearg|
		outBus = outBusarg;
		ampDb = ampDbarg;
		hpFreq = hpFreqarg;
		angleDeg = angleDegarg;
		pattern = patternarg;
		fadeTime = fadeTimearg;
	}

	// live: the proxy's amp control; the others take effect at the next build
	ampDb_ { |db| ampDb = db; proxy !? { |p| p.set(\amp, db.dbamp) } }
	outBus_ { |b| outBus = b; if(isBuilt) { RCLog.info(\ambi, "stereo monitor outBus: takes effect at the next build") } }
	hpFreq_ { |f| hpFreq = f; if(isBuilt) { RCLog.info(\ambi, "stereo monitor hpFreq: takes effect at the next build") } }
	angleDeg_ { |a| angleDeg = a; if(isBuilt) { RCLog.info(\ambi, "stereo monitor angleDeg: takes effect at the next build") } }
	pattern_ { |p| pattern = p; if(isBuilt) { RCLog.info(\ambi, "stereo monitor pattern: takes effect at the next build") } }
	fadeTime_ { |t| fadeTime = t; proxy !? { |p| p.fadeTime = t } }

	build { |chainarg|
		var server, online, n;
		chain = chainarg ? chain;
		if(chain.isNil or: { chain.isBuilt.not }) { RCLog.error(\ambi, "stereo monitor: the chain is not built"); ^this };
		if(isBuilt) { this.clear };
		server = chain.server;
		online = server.serverRunning and: { chain.isOffline.not };
		n = chain.numChannels;
		group = if(online) { Group.after(chain.playGroup) } { Group.basicNew(server) };
		playGroup = if(online) { Group.after(group) } { Group.basicNew(server) };
		proxy = NodeProxy.new(server, \audio, n);
		proxy.fadeTime = fadeTime;
		if(online) { proxy.group_(group) };
		chain.tap(\monitor, proxy);              // \in from the stage feeding the decoder
		proxy.source = this.prSource(n);
		proxy.set(\amp, ampDb.dbamp);
		if(online) { proxy.play(outBus, 2, playGroup) };
		isBuilt = true;
		RCLog.post(\ambi, "stereo monitor on bus % (% dB)".format(outBus, ampDb));
	}

	prSource { |n|
		var hp = hpFreq, angle = angleDeg.degrad, pat = pattern;
		^{ |amp = 1|
			var in = \in.ar(0 ! n);
			var foa = FoaEncode.ar(in.keep(AtkFoa.defaultOrder.asHoaOrder.size), FoaEncoderMatrix.newHoa1);
			FoaDecode.ar(FoaProximity.ar(HPF.ar(foa, hp), AtkHoa.refRadius), FoaDecoderMatrix.newStereo(angle, pat)) * amp
		}
	}

	// Same recipe as the chain's clear: groups first (immediate), then the proxy.
	clear {
		var send;
		if(isBuilt.not) { ^this };
		send = chain.server.serverRunning and: { chain.isOffline.not };
		chain.untap(\monitor);
		proxy.monitorGroup !? { |g| RCGuard.call(\ambi, nil) { g.free(send) } };
		playGroup !? { |g| RCGuard.call(\ambi, nil) { g.free(send) } };
		group !? { |g| RCGuard.call(\ambi, nil) { g.free(send) } };
		RCGuard.call(\ambi, nil) { proxy.clear(0) };
		proxy = nil;
		group = nil;
		playGroup = nil;
		isBuilt = false;
	}

	printOn { |stream| stream << "RAStereoMonitor(bus " << outBus << ", " << ampDb << " dB" << (if(isBuilt) { "" } { ", unbuilt" }) << ")" }
}
