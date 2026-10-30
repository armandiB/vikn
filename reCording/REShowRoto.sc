// reCording — the Roto-Control at HadronLake3's show console (REShowConsole): its knobs mixed into
// the takes' control tapes (REKnobMix, one per knob), its buttons choosing each mix's state.
//
//   r = REShowRoto((device: "Roto-Control", glide: 1.0, knobs: [
//       (cc: 0, ctl: "symbolic_wave", op: \add, range: [-0.5, 0.5]),   // a ctl knob: mixed with the tape's /fx/ctl symbolic_wave
//       (cc: 1, set: ["colour", "mix"], range: [0, 1])                 // a set knob: /fx/set colour mix <lo + k·(hi - lo)>, no tape, no states
//   ]));
//   r.start;                                   // MIDIClient.init, the device's source connected, the mappings (guarded: no device, a warning; the mixes still work)
//   r.tapeValue("symbolic_wave", 0.3, now);    // the console's chase filter: the mix's output for the tape's value, nil for a name no knob takes
//   r.tick(now);                               // the mixes gliding after a hold: their values sent
//   r.knobReceived(0, 0.5, now); r.buttonReceived(0, 100, now);   // what the MIDI handlers call (tests drive them)
//   r.stop;
//
// The device (the spec's `device`, "Roto-Control"): knobs send CCs 0-31 on MIDI channel 1 (sclang
// chan 0, `knobChan`) as fine pairs (cc, cc + 32: RCMidi's pairing, the MSB completed by its LSB), the
// position k = (msb + lsb/128) × 128 / 16383, 0..1 (the 14-bit value over 16383: 1 at the top; a device
// sending the MSB alone reaches 0.992); the buttons send the same CCs on MIDI channel 2 (sclang chan 1,
// `buttonChan`), 7-bit, a state by thirds: v < 43 → 1, v < 86 → 2, else 3 (REKnobMix: 1 both live, 2
// the tape held, 3 the knob held). A ctl knob's move sends /fx/ctl <name> <out> at once; a set knob's
// /fx/set <effect> <param> <value>; a button's press sends the mix's output when it has one. `op`
// \add (the default) or \scale, `range` [lo, hi] ([0, 1]); `glide` the mixes' convergence (1.0 s).
// Sends go through sendFunc (RCVisuals.sendArgs); `now` is timeFunc.value (Main.elapsedTime).
// `stop` frees the mappings, disconnects the source and, when `start` initialized the MIDI client
// itself, disposes it (the console's quit: the process then ends at 0.exit).

REShowRoto {
	var <spec, <deviceName, <glide, <knobChan = 0, <buttonChan = 1;
	var <knobs, <byCtl;   // cc → knob (an Event: cc, name, set, op, lo, hi, mix); ctl name (String) → knob
	var <>sendFunc, <>timeFunc;
	var <midi, <started = false, ownsMidi = false;   // ownsMidi: start initialized the MIDI client itself (stop disposes it)
	var <source, sourceIndex;   // the device's MIDIEndPoint connected by start (nil: not found), its index among the sources

	*new { |spec| ^super.new.initREShowRoto(spec) }

	initREShowRoto { |specarg|
		var list;
		spec = specarg ? ();
		deviceName = (spec[\device] ? "Roto-Control").asString;
		glide = (spec[\glide] ? 1.0).asFloat;
		knobChan = (spec[\knobChan] ? 0).asInteger;
		buttonChan = (spec[\buttonChan] ? 1).asInteger;
		knobs = IdentityDictionary.new;
		byCtl = Dictionary.new;
		sendFunc = { |path, args| RCVisuals.sendArgs(path, args) };
		timeFunc = { Main.elapsedTime };
		list = spec[\knobs] ? [];
		if(list.isKindOf(Dictionary)) {
			list = list.asKeyValuePairs.clump(2).collect { |pair| var d = pair[1]; d[\cc] = d[\cc] ?? { pair[0].asString.asInteger }; d };
		};
		list.do { |d| this.prAddKnob(d) };
	}

	prAddKnob { |d|
		var cc = d[\cc], range = d[\range] ? [0, 1], knob, op;
		if(cc.isNil) { RCLog.warn(\roto, "a knob without a cc: skipped (%)".format(d)); ^this };
		cc = cc.asInteger;
		op = (d[\op] ? \add).asSymbol;
		knob = (cc: cc, op: op, lo: (range[0] ? 0).asFloat, hi: (range[1] ? 1).asFloat);
		if(d[\ctl].notNil) {
			knob[\name] = d[\ctl].asString;
			knob[\mix] = REKnobMix(op, knob[\lo], knob[\hi], glide);
			byCtl[knob[\name]] = knob;
		} {
			if(d[\set].isNil or: { d[\set].size < 2 }) { RCLog.warn(\roto, "knob cc %: neither ctl nor set [effect, param]: skipped".format(cc)); ^this };
			knob[\set] = d[\set].collect(_.asString);
		};
		if(knobs[cc].notNil) { RCLog.warn(\roto, "knob cc % given twice: the last wins".format(cc)) };
		knobs[cc] = knob;
	}

	knob { |cc| ^knobs[cc.asInteger] }
	mix { |cc| ^knobs[cc.asInteger] !? (_[\mix]) }
	mixes { ^knobs.values.select { |k| k[\mix].notNil }.collect(_[\mix]) }
	ctlNames { ^byCtl.keys.asArray.sort }
	size { ^knobs.size }

	//////// the device

	// MIDI up, the device's source connected (that one alone: MIDIIn.connectAll with this Mac's twenty
	// sources, network sessions among them, made MIDIIn.disconnectAll and MIDIClient.disposeClient
	// deadlock one time in six, and a headless sclang then never ended at 0.exit, Oct 29 2026; with no
	// source connected the client disposes every time) and the mappings installed: a fine pair per knob
	// on knobChan, a 7-bit one per button on buttonChan, through RCMidi (its fine pairing). Without the
	// device nothing is connected (a warning: plug it in and start again); without MIDI at all the mixes
	// still work from tapeValue.
	start {
		if(started) { ^this };
		started = true;
		if(MIDIClient.initialized.not) {
			RCGuard.call(\roto, nil) { MIDIClient.init(verbose: false) };
			ownsMidi = MIDIClient.initialized;
		};
		if(MIDIClient.initialized) {
			sourceIndex = (MIDIClient.sources ? []).detectIndex { |e| e.name == deviceName or: { e.device == deviceName } };
			if(sourceIndex.isNil) {
				source = nil;
				RCLog.warn(\roto, "MIDI device \"%\" not found among % sources: no source connected (plug it in, start again)".format(deviceName, MIDIClient.sources.size));
			} {
				source = MIDIClient.sources[sourceIndex];
				RCGuard.call(\roto, nil) { MIDIIn.connect(sourceIndex, source) };
			};
		} {
			RCLog.warn(\roto, "MIDI not initialized: the knobs are not heard (the tape plays alone)");
		};
		this.installMappings;
		RCLog.post(\roto, "% knobs on % (chan % knobs, chan % buttons)%".format(knobs.size, deviceName, knobChan + 1, buttonChan + 1,
			if(source.notNil) { ", connected" } { "" }), true);
	}

	// The mappings alone (start does this after MIDI is up; a test without MIDI drives their MIDIdefs):
	// rc_roto_knob_<cc> and rc_roto_knob_<cc>_lsb (the fine pair), rc_roto_button_<cc>.
	installMappings {
		midi !? (_.freeAll);
		midi = RCMidi((name: \roto));
		knobs.keysValuesDo { |cc, knob|
			RCGuard.call(\roto, nil) {
				midi.control(("knob_" ++ cc).asSymbol, cc, knobChan, deviceName, { |x| x }, { |v|
					this.knobReceived(cc, this.class.positionOf(v), timeFunc.value)
				}, fine: true);
				if(knob[\mix].notNil) {
					midi.control(("button_" ++ cc).asSymbol, cc, buttonChan, deviceName, { |x| x }, { |v|
						this.buttonReceived(cc, v, timeFunc.value)
					}, fine: false);
				};
			};
		};
		^midi
	}

	// A fine value (msb + lsb/128, RCMidi's) as a position 0..1: the 14-bit value over 16383.
	*positionOf { |v| ^(v * 128 / 16383).clip(0, 1) }

	// The mappings freed, the source start connected disconnected, the MIDI client start brought up
	// disposed (one a session had up before stays as it was).
	stop {
		midi !? (_.freeAll);
		midi = nil;
		source !? { |s| RCGuard.call(\roto, nil) { MIDIIn.disconnect(sourceIndex, s) } };
		source = nil;
		sourceIndex = nil;
		if(ownsMidi) {
			ownsMidi = false;
			RCGuard.call(\roto, nil) { MIDIClient.disposeClient };
		};
		started = false;
	}

	//////// what the handlers call

	// A knob at position k (0..1): a ctl knob's mix updated and its output sent, a set knob's value sent.
	knobReceived { |cc, k, now|
		var knob = knobs[cc.asInteger], out;
		if(knob.isNil) { ^nil };
		now = now ?? { timeFunc.value };
		k = k.asFloat.clip(0, 1);
		knob[\k] = k;
		if(knob[\mix].notNil) {
			knob[\mix].knob_(k, now);
			out = knob[\mix].value(now);
			out !? { this.prSend('/fx/ctl', [knob[\name], out]) };
			^out
		};
		out = knob[\lo] + (k * (knob[\hi] - knob[\lo]));
		this.prSend('/fx/set', knob[\set] ++ [out]);
		^out
	}

	// A button's value v (0..127): the state by thirds on the knob's mix; the output sent when there is one.
	buttonReceived { |cc, v, now|
		var knob = knobs[cc.asInteger], state, out;
		if(knob.isNil or: { knob[\mix].isNil }) { ^nil };
		now = now ?? { timeFunc.value };
		state = this.class.stateOf(v);
		knob[\mix].state_(state, now);
		out = knob[\mix].value(now);
		out !? { this.prSend('/fx/ctl', [knob[\name], out]) };
		^state
	}

	// a button's value cut in three
	*stateOf { |v| ^case { v < 43 } { 1 } { v < 86 } { 2 } { 3 } }

	// The tape's value for a ctl name: the mix's output (the knob mixed in), nil for a name no knob takes
	// (the console sends the entry as it is).
	tapeValue { |name, v, now|
		var knob = byCtl[name.asString];
		if(knob.isNil or: { v.isNumber.not }) { ^nil };
		now = now ?? { timeFunc.value };
		knob[\mix].tape_(v, now);
		^knob[\mix].value(now)
	}

	// The mixes still moving (a hold left: the glide) send their values. Returns the number sent.
	tick { |now|
		var count = 0;
		now = now ?? { timeFunc.value };
		knobs.do { |knob|
			var m = knob[\mix];
			if(m.notNil and: { m.moving(now) }) {
				m.value(now) !? { |out| this.prSend('/fx/ctl', [knob[\name], out]); count = count + 1 };
			};
		};
		^count
	}

	prSend { |path, args| RCGuard.call(\roto, nil) { sendFunc.value(path, args) } }

	// one line per knob: cc, what it drives, its state and position
	status {
		^knobs.keys.asArray.sort.collect { |cc|
			var k = knobs[cc];
			"cc %: % % (%) k %".format(cc, k[\name] ?? { k[\set].join(" ") }, k[\op], k[\mix] !? { |m| "state " ++ m.state } ? "set", k[\k] ? "-")
		}
	}

	printOn { |stream| stream << "REShowRoto(" << deviceName << ", " << knobs.size << " knobs" << if(started) { ", started)" } { ")" } }
}
