// reCurrent — the visuals link: sound data from a song to HomewareVisuals
// (SuperCollider → OSC/UDP 57121 → bridge.mjs → WebSocket → a page).
//
// Three things: one address, sends that land when the sound does, and the two
// hooks a rig needs to describe its sounds — a per-event key for orgnsm
// templates and a relay of server replies (SendReply) to the page.
//
//   RCVisuals.send('/orgnsm/gone', 12);                                   // now
//   RCVisuals.sendForEvent(ev, '/orgnsm/note', [12, 440, 0.1]);           // when the event's sound starts
//   tpl.attrDictBase = tpl.attrDictBase ++ [viz: RCVisuals.notePfunc('/orgnsm/note', { |ev| [ev.freq, ev.amp] })];
//   RCVisuals.relay(\plankton, '/plankton/viz', '/orgnsm/state', ~server);   // [replyID, values...] forwarded
//
// `enabled = false` sends nothing (a gig without visuals); `sink` replaces the
// network by a Function { |path, args| } (tests, offline checks). A delayed
// send goes on SystemClock from the calling thread's logical time: a Pfunc
// evaluated at an event's logical time sends at that time plus the server
// latency, when the sound starts (HomewareVisuals' latency rule).

RCVisuals {
	classvar <host = "127.0.0.1", <port = 57121;
	classvar addr;
	classvar <>enabled = true;
	classvar <>sink;
	classvar relays;
	classvar <sent = 0;

	*initClass {
		relays = IdentityDictionary.new;
	}

	//////// address

	*setAddr { |hostarg = "127.0.0.1", portarg = 57121|
		host = hostarg;
		port = portarg;
		addr = NetAddr(host, port);
		^addr
	}

	*addr { ^addr ?? { this.setAddr(host, port) } }

	//////// sending

	// Now. Returns true when a message went out (or into the sink).
	*send { |path ... args| ^this.sendArgs(path, args) }

	*sendArgs { |path, args|
		if(enabled.not) { ^false };
		args = args ? [];
		sent = sent + 1;
		if(sink.notNil) { sink.value(path, args) } { this.addr.sendMsg(path, *args) };
		^true
	}

	// In `delay` seconds of the calling thread's logical time (SystemClock); at once when delay <= 0.
	*sendIn { |delay, path, args|
		if(enabled.not) { ^false };
		if(delay.isNil or: { delay <= 0 }) { ^this.sendArgs(path, args) };
		SystemClock.sched(delay, { this.sendArgs(path, args); nil });
		^true
	}

	// Seconds between an event's logical time and its sound: the server latency
	// (the event's own \latency when set), its \lag, its \timingOffset (beats of
	// the beat's clock). The server is the beat's layer's, else the event's,
	// else the default.
	*eventDelay { |ev|
		var beat = ev[\rc_beat];
		var server = (beat !? { |b| b.layer.server }) ?? { ev[\server] } ?? { Server.default };
		var latency = ev[\latency] ?? { server.latency } ? 0;
		var clock = (beat !? (_.clock)) ?? { thisThread.clock };
		var offset = ev[\timingOffset] ? 0;
		if(offset != 0 and: { clock.respondsTo(\beatDur) }) { offset = offset * clock.beatDur };
		^latency + (ev[\lag] ? 0) + offset
	}

	*sendForEvent { |ev, path, args| ^this.sendIn(this.eventDelay(ev), path, args) }

	//////// hooks

	// An attrDict value: at every non-rest event, func.(ev) → the args sent to
	// `path` when the sound starts (nil: nothing). Put it after the keys it
	// reads (freq, amp...): keys are computed in order. The key's own value is
	// 0 and never reaches a synth (only a def's control names are sent).
	*notePfunc { |path, func|
		^Pfunc { |ev|
			if(enabled and: { ev.isRest.not }) {
				RCGuard.call(\visuals, nil) { func.value(ev) } !? { |args| this.sendForEvent(ev, path, args) };
			};
			0
		}
	}

	// Server replies (SendReply, SendTrig: [cmd, nodeID, replyID, values...])
	// on `replyPath` forwarded to `outPath` as argFunc.(msg) (default
	// [replyID, values...]; nil sends nothing). One relay per key, permanent
	// (Cmd-. keeps it), freed by unrelay / freeRelays.
	*relay { |key, replyPath, outPath, server, argFunc|
		var func;
		key = key.asSymbol;
		this.unrelay(key);
		server = server ?? { Server.default };
		func = { |msg|
			var args = if(argFunc.isNil) { msg[2..] } { RCGuard.call(\visuals, nil) { argFunc.value(msg) } };
			args !? { this.sendArgs(outPath, args) };
		};
		relays[key] = OSCFunc(func, replyPath, server.addr).permanent_(true);
		^relays[key]
	}

	// true when a relay was freed
	*unrelay { |key|
		^relays.removeAt(key.asSymbol) !? { |f| f.free; true } ? false
	}

	*freeRelays {
		relays.do(_.free);
		relays.clear;
	}

	*relayKeys { ^relays.keys.asArray }
	*resetCount { sent = 0 }
}
