// reCurrent — the visuals link: sound data from a song to HomewareVisuals
// (SuperCollider → OSC/UDP 57300 → bridge.mjs → WebSocket → a page).
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
// send goes on SystemClock (on the offline clock in a take render) from the
// calling thread's logical time: a Pfunc
// evaluated at an event's logical time sends at that time plus the server
// latency, when the sound starts (HomewareVisuals' latency rule).

// The bridge's port is far from sclang's own: sclang takes 57120 and, when that is busy, the next free one up,
// so a second sclang (a headless check, a render) took 57121, the bridge's port until Oct 24 2026, and the
// bridge could not start. A piece frozen before then sends to 57121: RCVisuals.setAddr("127.0.0.1", 57300).
// The default port is RC_VISUALS_PORT when the environment sets it, else 57300 (the bridge of the user's
// stack, which the IDE reaches): HomewareSC's headless runs set it to their checkout's own bridge (a
// HomewareVisuals worktree's port) or to a port nothing binds, so a check never draws on the pages the
// user has open. setAddr with a port still wins (HomewareVisuals' demos name their checkout's bridge).
RCVisuals {
	classvar <host = "127.0.0.1", <port, <defaultPort = 57300;
	classvar addr;
	classvar <>enabled = true;
	classvar <>sink;
	classvar relays;
	classvar <sent = 0;

	*initClass {
		var env = "RC_VISUALS_PORT".getenv;
		relays = IdentityDictionary.new;
		// an empty variable reads "", not nil
		if(env.notNil and: { env.asInteger > 0 }) { defaultPort = env.asInteger };
		port = defaultPort;
	}

	//////// address

	*setAddr { |hostarg = "127.0.0.1", portarg|
		host = hostarg;
		port = portarg ? defaultPort;
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
	// A thread on an offline clock (REOfflineClock: a take rendered offline) has a logical time
	// far ahead of the real one: the send waits on that clock instead, at the offline time it
	// belongs to (on SystemClock it would wait in real time, and fill its queue).
	*sendIn { |delay, path, args|
		var clock = thisThread.clock;
		if(enabled.not) { ^false };
		if(delay.isNil or: { delay <= 0 }) { ^this.sendArgs(path, args) };
		if(clock.isKindOf(REOfflineClock)) {
			clock.sched(delay * clock.tempo, { this.sendArgs(path, args); nil });
		} {
			SystemClock.sched(delay, { this.sendArgs(path, args); nil });
		};
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

	// A value for the reprocessing of a take's video (HomewareVisuals' effects page, which maps it
	// onto an effect's parameter with /fx/map): '/fx/ctl' <name> <value>, sent in `delay` seconds
	// (the default server's latency), when the sound it goes with starts, as the rigs' messages
	// are. In a take rendered offline it lands in the tape; scripts/take.sh render then sets the
	// /fx/ entries apart as the take's control tape (<take>/<take>.ctl.json).
	*ctl { |name, value, delay|
		^this.sendIn(delay ?? { Server.default.latency }, '/fx/ctl', [name.asString, value])
	}

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
