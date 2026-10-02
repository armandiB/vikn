// reCording — the address of a song's server while its recorder records level 3 (the server's
// messages): every message is forwarded to the real address and handed to the recorder with
// the time it is due (REScoreRecorder.tapServer). Installed by the recorder at the take's
// start when level3 is on, removed at stop; the server's responders match a reply by host and
// port, so the server stays whole meanwhile. Nothing of it exists when level 3 is off.

RETapAddr : NetAddr {
	classvar <commandNames;   // scsynth's numbered commands → their names
	var <real, <recorder;

	*initClass {
		commandNames = IdentityDictionary.new;
		#[none, notify, status, quit, cmd, d_recv, d_load, d_loadDir, d_freeAll, s_new, n_trace, n_free, n_run, n_cmd, n_map, n_set,
			n_setn, n_fill, n_before, n_after, u_cmd, g_new, g_head, g_tail, g_freeAll, c_set, c_setn, c_fill, b_alloc, b_allocRead,
			b_read, b_write, b_free, b_close, b_zero, b_set, b_setn, b_fill, b_gen, dumpOSC, c_get, c_getn, b_get, b_getn, s_get,
			s_getn, n_query, b_query, n_mapn, s_noid, g_deepFree, clearSched, sync, d_free, b_allocReadChannel, b_readChannel,
			g_dumpTree, g_queryTree, error, s_newargs, n_mapa, n_mapan, n_order, p_new, version
		].do { |name, i| commandNames[i] = ("/" ++ name).asSymbol };
	}

	*new { |real, recorder| ^super.new(real.hostname, real.port).initRETapAddr(real, recorder) }
	initRETapAddr { |r, rec| real = r; recorder = rec }

	// The command's name as a Symbol: '/s_new' for 9, "/s_new" and \s_new alike.
	*commandName { |cmd|
		var s;
		if(cmd.isNumber) { ^commandNames[cmd.asInteger] ?? { ("/" ++ cmd).asSymbol } };
		s = cmd.asString;
		^if(s.beginsWith("/")) { s.asSymbol } { ("/" ++ s).asSymbol }
	}

	sendMsg { |... msg| this.prTap(nil, [msg]); real.sendMsg(*msg) }
	sendBundle { |time ... msgs| this.prTap(time, msgs); real.sendBundle(time, *msgs) }
	listSendMsg { |msg| this.prTap(nil, [msg]); real.listSendMsg(msg) }
	listSendBundle { |time, msgs| this.prTap(time, msgs); real.listSendBundle(time, msgs) }
	sendClumpedBundles { |time ... msgs| this.prTap(time, msgs); real.sendClumpedBundles(time, *msgs) }
	sendRaw { |rawArray| real.sendRaw(rawArray) }
	sendStatusMsg { real.sendStatusMsg }
	sync { |condition, bundles, latency| bundles !? { |msgs| this.prTap(latency, msgs) }; ^real.sync(condition, bundles, latency) }
	makeSyncResponder { |condition| ^real.makeSyncResponder(condition) }
	connect { |disconnectHandler| ^real.connect(disconnectHandler) }
	disconnect { ^real.disconnect }
	isConnected { ^real.isConnected }
	hasBundle { ^real.hasBundle }

	// the same address as the real one, for whoever compares (the fields match a responder's)
	== { |that| ^that.isKindOf(NetAddr) and: { that.addr == addr } and: { that.port == port } }
	hash { ^real.hash }

	prTap { |time, msgs| RCGuard.call(\tap, nil) { recorder.tapServer(time, msgs) } }

	printOn { |stream| stream << "RETapAddr(" << real << ")" }
}
