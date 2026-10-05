// reCording — a rig's SendReply tap, offline: in a take rendered offline (scripts/take.sh render,
// an REOfflineClock and an RECollectAddr) scsynth NRT answers no reply, so the values a tap sends
// to the language live (the positions and levels RCVisuals relays to HomewareVisuals as
// /lsys/state, /orgnsm/state) are written into a Buffer instead, at the tap's rate, and the buffer
// to a float sound file at the render's end; HomewareSC's scripts/analysis/hwa/viztape.py turns
// the frames into entries of the visuals tape (<take>.viz.json).
//
//   // the SynthDef's offline variant (the rig's def block)
//   SynthDef(\LSystem_VizTapBuf_w0_32, { |posBus = 0, levelBus = 0, rate = 20, bufnum = 0|
//       REOfflineTap.record(bufnum, rate, In.kr(posBus, 96) ++ In.kr(levelBus, 32));
//   }).add;
//   // where the rig creates a tap, when ~rc_offline is set (REOfflineTap.isOffline(server))
//   t = REOfflineTap(rig.server, \LSystem_VizTapBuf_w0_32, \lsys, '/lsys/state', [32 * b], rate, 128);
//   ~lsys_send.(t.msgs(rig.group_lsys, [\posBus, ..., \rate, rate]));   // /b_alloc, then the /s_new
//   rig.viz_taps[b] = t.synth;                                           // freed as the live tap is
//   // the render, after the clock ran: the Score lines writing the files, and the index
//   w = REOfflineTap.writeLines(server, addr.bundles, folder, atTime, startSecs);
//   w[\lines].do { |l| score.add(l) };   w[\index]   // → <folder>/index.json
//
// Frame i of a tap's buffer is [i + 1] ++ the values latched at trigger i (time i / rate from the
// synth's start): channel 0 is 0 on a frame never written (a tap freed before the end), and the
// frames of a tap that ran past its buffer all land on the last one, whose counter then exceeds
// its index (the reader reports an overflow). The buffer is sized from endSecs (the clock seconds
// the render ends at, set by the render) and the clock's seconds at creation.
// A tap's creation time is read back from the collected bundles (its /s_new, by node id): the
// rigs write no sidecar. Nothing here touches a live server: new refuses one.

REOfflineTap {
	classvar <all;                            // server name → List of taps (the render reads it at its end)
	classvar <>endSecs;                       // the clock seconds the render ends at; nil: defaultSecs, warned
	classvar <>defaultSecs = 600;
	classvar <>maxRate;                       // a cap on the taps' rate (frames per second); nil: none
	var <server, <defName, <key, <path, <head, <rate, <numValues, <synth, <buffer, <frames, <createdAt;

	*new { |server, defName, key, path, head, rate = 20, numValues|
		^super.new.initREOfflineTap(server, defName, key, path, head, rate, numValues)
	}

	//////// the UGen graph of a tap's offline variant

	// values: the kr UGens the live tap sends (an Array). Writes [count] ++ values latched at each
	// trigger into frame count - 1; the frames after the end of the buffer go to its last one.
	*record { |bufnum, rate = 20, values|
		var trig = Impulse.kr(rate);
		var count = PulseCount.kr(trig);
		^BufWr.kr([count] ++ Latch.kr(values.asArray, trig), bufnum, count - 1, loop: 0)
	}

	//////// the server

	// The RECollectAddr behind the server's address (a BundleNetAddr inside server.bind, the
	// RETapAddr of a recorder recording level 3: RECollectAddr.behind), nil live.
	*collector { |server| ^RECollectAddr.behind(server.addr) }

	*isOffline { |server| ^this.collector(server).notNil }

	initREOfflineTap { |s, d, k, p, h, r, n|
		var addr = this.class.collector(s), now, secs, list;
		if(addr.isNil) { Error("REOfflineTap: % is not an offline server (no RECollectAddr behind its address)".format(s)).throw };
		server = s;
		defName = d.asSymbol;
		key = k.asSymbol;
		path = p.asSymbol;
		head = h.asArray;
		numValues = n.asInteger;
		rate = maxRate !? { |m| r.min(m) } ? r;
		now = addr.now;
		createdAt = now;
		if(endSecs.isNil) { RCLog.warn(\offline, "REOfflineTap: endSecs not set, % s of frames assumed".format(defaultSecs)) };
		secs = ((endSecs ? defaultSecs) - now).max(1);
		frames = (secs * rate).ceil.asInteger + 2;
		buffer = Buffer.new(server, frames, numValues + 1);   // the number allocated client-side, nothing sent
		synth = Synth.basicNew(defName, server);
		all = all ?? { IdentityDictionary.new };
		list = all[server.name];
		if(list.isNil) { list = List.new; all[server.name] = list };   // (an index assignment answers the dictionary)
		list.add(this);
	}

	numChannels { ^numValues + 1 }
	bufnum { ^buffer.bufnum }

	//////// the messages (the rig's own transport sends them)

	// four arguments: a nil completion message has no place in a Score line
	allocMsg { ^['/b_alloc', buffer.bufnum, frames, numValues + 1] }

	// The /b_alloc, then the synth's /s_new with its bufnum: one bundle, in that order.
	msgs { |target, args, addAction = \addToTail|
		^[this.allocMsg, synth.newMsg(target, (args ? []) ++ [\bufnum, buffer.bufnum], addAction)]
	}

	// Seconds of this tap's /s_new in the collected bundles ([secs, msg, ...]), nil when never sent.
	startSecsIn { |bundles|
		var id = synth.nodeID;
		var line = bundles.detect { |b|
			b[1..].any { |m| m.isSequenceableCollection and: { (m[0] == 9 or: { m[0].asString.endsWith("s_new") }) and: { m[2] == id } } }
		};
		^line !? (_[0])
	}

	fileName { ^"%_%_%.wav".format(key, head.collect(_.asString).join("_"), synth.nodeID) }

	//////// the end of the render

	// The Score lines writing every tap of the server at atTime (float WAVs in folder) and the
	// index the merge reads: (format:, version:, taps: [(key:, path:, head:, file:, rate:,
	// values:, t0:, frames:, nodeID:, createdAt:)]), t0 the tap's start in tape time (its /s_new's
	// seconds minus startSecs). A tap never sent is left out, with a warning.
	*writeLines { |server, bundles, folder, atTime = 0, startSecs = 0|
		var taps = (all !? (_[server.name])) ? [];
		var lines = List.new, index = List.new;
		taps.do { |t|
			var t0 = t.startSecsIn(bundles);
			if(t0.isNil) {
				RCLog.warn(\offline, "REOfflineTap: % was never sent to the server: no state file".format(t.fileName));
			} {
				lines.add([atTime, ['/b_write', t.bufnum, folder.asString +/+ t.fileName, "wav", "float", -1, 0, 0]]);
				index.add((key: t.key, path: t.path.asString, head: t.head, file: t.fileName, rate: t.rate, values: t.numValues,
					t0: t0 - startSecs, frames: t.frames, nodeID: t.synth.nodeID, createdAt: t.createdAt - startSecs));
			};
		};
		^(format: "re-state", version: 1, lines: lines.asArray, index: index.asArray)
	}

	*taps { |server| ^((all !? (_[server.name])) ? []).asArray }

	*clear { |server|
		all !? { |d| if(server.isNil) { d.clear } { d.removeAt(server.name) } };
	}

	printOn { |stream| stream << "REOfflineTap(" << key << " " << path << " " << head << ", " << rate << " Hz, " << numValues << " values)" }
}
