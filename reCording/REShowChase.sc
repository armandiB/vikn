// reCording — a take's control tape chased to a clock it does not own (HadronLake3's show console,
// REShowConsole: Reaper plays the stems and sends its time; the page's effects follow the take's
// /fx/ entries as recorded, at their show time, whatever Reaper does: seek, stop, restart).
//
//   c = REShowChase(tape.entries, start);   // entries [secs, "/address", args...] on the take's timeline, sorted;
//                                           // start: the cue's start on the show clock (entry at secs → show time start + secs)
//   c.seek(t);                              // the state at t: the last entry before t per state key, in tape order
//   c.advance(from, to);                    // the entries with from < show time <= to, in order (the heartbeat)
//   c.filter = { |e| ... e or nil };        // every entry sent passes here first (the console replaces a mapped /fx/ctl's value)
//   c.sendFunc = { |path, args| };          // RCVisuals.sendArgs by default; a test captures
//
// State keys (what a seek keeps the last of): /fx/chain, /fx/clear, /fx/level: the address alone;
// /fx/ctl: (address, name); /fx/set, /fx/glide: (address, effect, param); /fx/map: (address, effect,
// param), its arguments 2 and 3 (argument 1 is the control's name); /fx/on, /fx/points: (address,
// effect); /fx/layer: (address, index); /fx/look: (address, key); anything else: the address and its
// first string argument. Transient, never chased, streamed by advance alone: /fx/rain, /fx/drop.
// Dropped always: /fx/start, /fx/stop, /fx/page (the page's video follows /fx/time in chase mode).

REShowChase {
	classvar <transient, <dropped;
	var <entries, <start, <>filter, <>sendFunc;
	var times, keys;   // per entry: its show time; its state key (a Symbol), \transient or \dropped
	var <sent = 0;

	*initClass {
		transient = #["/fx/rain", "/fx/drop"];
		dropped = #["/fx/start", "/fx/stop", "/fx/page"];
	}

	*new { |entries, start = 0| ^super.new.initREShowChase(entries, start) }

	initREShowChase { |list, startarg|
		entries = (list ? []).asArray;
		start = (startarg ? 0).asFloat;
		times = entries.collect { |e| start + (e[0] ? 0) };
		keys = entries.collect { |e| this.class.keyOf(e) };
		sendFunc = { |path, args| RCVisuals.sendArgs(path, args) };
	}

	size { ^entries.size }
	duration { ^entries.last !? (_[0]) ? 0 }
	end { ^start + this.duration }

	// The state key of an entry: a Symbol, \transient, or \dropped.
	*keyOf { |e|
		var addr = e[1].asString, args = e[2..];
		var parts, str;
		if(dropped.includesEqual(addr)) { ^\dropped };
		if(transient.includesEqual(addr)) { ^\transient };
		parts = switch(addr.asSymbol,
			'/fx/chain', { [] },
			'/fx/clear', { [] },
			'/fx/level', { [] },
			'/fx/ctl', { args[..0] },
			'/fx/set', { args[..1] },
			'/fx/glide', { args[..1] },
			'/fx/map', { args[1..2] },
			'/fx/on', { args[..0] },
			'/fx/points', { args[..0] },
			'/fx/layer', { args[..0] },
			'/fx/look', { args[..0] },
			{
				str = args.detect { |a| a.isString or: { a.isKindOf(Symbol) } };
				if(str.isNil) { [] } { [str] }
			}
		);
		^(([addr] ++ parts.collect(_.asString)).join("|")).asSymbol
	}

	// The state at show time t: for every state key, the last entry at or before t, sent in tape
	// order (the chain before what it holds, a clear before what follows it). Transients and dropped
	// entries are left out. Returns the number of entries sent.
	seek { |t|
		var n = this.prFirstAfter(t), last = IdentityDictionary.new, chosen, count = 0;
		n.do { |i|
			var k = keys[i];
			if(k !== \transient and: { k !== \dropped }) { last[k] = i };
		};
		chosen = last.values.asArray.sort;
		chosen.do { |i| if(this.prSend(entries[i])) { count = count + 1 } };
		^count
	}

	// The entries with from < show time <= to, in order, transients included, dropped ones not.
	// Returns the number sent.
	advance { |from, to|
		var i = this.prFirstAfter(from), count = 0;
		if(to <= from) { ^0 };
		while { i < entries.size and: { times[i] <= to } } {
			if(keys[i] !== \dropped) { if(this.prSend(entries[i])) { count = count + 1 } };
			i = i + 1;
		};
		^count
	}

	// the index of the first entry after show time t (entries.size when none)
	prFirstAfter { |t|
		var lo = 0, hi = times.size;
		while { lo < hi } {
			var mid = (lo + hi) div: 2;
			if(times[mid] <= t) { lo = mid + 1 } { hi = mid };
		};
		^lo
	}

	// one entry through the filter and out; true when something went
	prSend { |e|
		var out = e;
		if(filter.notNil) { out = RCGuard.call(\chase, nil) { filter.value(e) } };
		if(out.isNil) { ^false };
		RCGuard.call(\chase, nil) { sendFunc.value(out[1].asSymbol, out[2..]) };
		sent = sent + 1;
		^true
	}

	printOn { |stream| stream << "REShowChase(" << entries.size << " entries from " << start.round(0.01) << " s, " << this.duration.round(0.01) << " s long)" }
}
