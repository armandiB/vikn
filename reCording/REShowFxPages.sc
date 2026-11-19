// reCording — the effects page's chain played from the Roto-Control's pages at HadronLake3's show console
// (REShowConsole, Oct 31 2026). Every effect of the chain is on, in one fixed order, at mix 0 (bypassed by the page:
// it costs nothing); each page of the device has seven buttons choosing one of its seven effects and an eighth
// resetting the chosen one, and eight knobs driving it: seven parameters laid out per effect, the eighth its mix.
// Choosing an effect turns the knobs' motors to its places and lights its button (the Roto-Control in MIDI mode
// follows the CCs it receives and sends nothing back: Oct 31 2026); a panel page (HomewareVisuals' public/fxpanel/)
// draws both pages as the device lays them out.
// Nov 18 2026, for the effects engine's w1 (HomewareVisuals' public/lib/fx-viz-w1.js, a graph of nodes): an effect
// with more than seven parameters has them in banks of seven, the chosen effect's button going round them; and the
// spec can say the graph (nodes of its own, their wires, the node shown, source nodes, values set once), sent with
// the state every time, as lines that change nothing when they are there already.
//
//   p = REShowFxPages(show[\fx]);
//   p.start;                       // MIDI up (the device's source and destination; no device: a warning, the rest works),
//                                  // the page told (/fx/clear, the graph, every layout parameter at rest, every mix 0,
//                                  // /fx/chain), each page's first effect chosen
//   p.start(false);                // the same without MIDI (tests; a console without the device)
//   p.knobReceived(0, 7, 0.6);     // page 1's eighth knob at 0.6: its effect's mix (what the MIDI handlers call)
//   p.buttonReceived(1, 2, 127);   // page 2's third button: its third effect chosen; chosen already: its next bank
//   p.select(0, 3); p.reset(0); p.effectOf(0); p.knobValue("glow", 0); p.mix("glow"); p.bankOf("aberration"); p.setBank(1, 1)
//   p.sendState;                   // the whole state sent again (a page opened late: the console's reload)
//   p.tick(now);                   // the whole state every `refresh` seconds (2; a page opened late heals), the panel's
//                                  // state when something changed and every second (the console's heartbeat)
//   p.stop;
//
// The spec (the show file's "fx"):
//   { "device": "Roto-Control", "knobChan": 0, "buttonChan": 1, "mixDead": 0.5, "refresh": 2,
//     "chain": ["warp", "feedback", ...],
//     "pages": [ { "name": "shape", "cc": 0, "effects": ["warp", ...] }, { "name": "light", "cc": 8, "effects": [...] } ],
//     "layouts": { "warp": [ ["amount", 0, 0.2, 0.02], ["scale", 0.5, 20, 3, "exp"], ... ], ... } }
// A page: up to seven effects on its buttons cc .. cc + 6 (buttonChan; any message chooses: a trigger's press and
// release, a toggle's either state), the reset on cc + 7, its knobs on cc .. cc + 7 (knobChan, 14-bit: the LSB on
// cc + 32, RCMidi's fine pairing). A layout: knobs, each [param, lo, hi, rest] with an optional curve:
// "lin" (the default), "exp" (lo and hi above 0: equal ratios along the knob) or "int" (rounded: a mode, a count); or
// one knob over several parameters, { "name": "warmth", "lo": -1, "hi": 1, "rest": 0, "set": [["tint_r", 0.5, 1.5],
// ["tint_b", 1.5, 0.5]] } (each from its lo to its hi along the knob). The state is every knob's place k (0..1, the
// 14-bit value over 16383) per effect: a parameter is lo + k (hi - lo), exp lo (hi / lo)^k. The mix: 0 up to mixDead
// (CC units: 0.5 of 127, so a knob turned down is off for sure), then linear to 1 at 127.
//
// Banks: a layout of more than seven knobs is laid out seven at a time (knobs 1-7 the first bank, 8-14 the second;
// 28 at most); the eighth knob is the mix in every bank. The chosen effect's button goes round its banks, the motors
// following; every effect keeps the bank it was left on. How the button says it ("bankBy"):
//   "press" (the default)   a message above 0 on the chosen effect's button is the next bank (a trigger's press; its
//                           release, 0, is nothing; two within bankWait, 0.1 s, are one). Its light: "bankLights",
//                           a value per bank (127 for every bank unless given: [127, 64] dims the second)
//   "value"                 the button has a state per bank after its dark one, and its message says which: 0, then
//                           "bankValues" (64 and 127 for a button of three states: dark, bank 1, bank 2). A value
//                           chooses the effect and the bank nearest to it; 0 on the chosen effect's button (the
//                           device gone round to its dark state) is bank 1 again, and the button is sent bank 1's
//                           value, so that it shows it. The lights are the values themselves.
//
// The graph (the effects engine's w1), each sent before the chain with every state:
//   "nodes": { "blur2": "blur" }               /fx/node: nodes of one's own; a page or a chain names them as effects,
//                                              and a node without a layout of its own takes its effect's
//   "inputs": { "setting": "main", "clip": "video:/recordings/x.mp4" }      /fx/input: source nodes (a video held to
//                                              the page's own: "video:/recordings/x.mp4:follow:0", the offset in seconds)
//   "wires": { "comp.b": "cut", "cut.in": "colours" }                       /fx/wire <node> <port> <source>
//   "out": "comp"                              /fx/out
//   "look": { "layers": 1 }                    /fx/look
//   "set": { "kaleidoscope": { "seam": 1 }, "comp": { "mix": 1 } }          values no knob holds, set once and again
//                                              with the state; a node whose mix is set here keeps it (no knob's mix
//                                              is sent for it: a node that must always draw, in the chain or off it)
// Sends: /fx/set <effect> <param> <value> and the state through sendFunc (RCVisuals.sendArgs); the motors (14-bit) and
// the lights through midiFunc { |chan, cc, value| } (a MIDIOut to the device); /fxpanel/state <json> (the panel
// page's header has its shape) from tick.

REShowFxPages {
	var <spec, <deviceName, <knobChan = 0, <buttonChan = 1, <mixDead = 0.5, <>refresh = 2;
	var <chain, <pages, <layouts;   // chain: [String]; pages: [(name, cc, effects)]; layouts: effect → [knob Events]
	var <places, <mixPlaces, <banks, <chosen;   // effect → its knobs' places (0..1, seven a bank); effect → its mix knob's place; effect → the bank shown; per page, an index into its effects
	var <bankBy = \press, <bankValues, <bankLights, <>bankWait = 0.1;
	var <nodes, <inputs, <wires, <outNode, <looks, <fixed;   // the graph, as lists in the order sent
	var <>sendFunc, <>midiFunc, <>timeFunc;
	var <midi, <out, <started = false, ownsMidi = false;
	var <source, sourceIndex;
	var dirty = true, panelAt, stateAt, bankAt;

	*new { |spec| ^super.new.initREShowFxPages(spec) }

	initREShowFxPages { |specarg|
		spec = specarg ? ();
		deviceName = (spec[\device] ? "Roto-Control").asString;
		knobChan = (spec[\knobChan] ? 0).asInteger;
		buttonChan = (spec[\buttonChan] ? 1).asInteger;
		mixDead = (spec[\mixDead] ? 0.5).asFloat;
		refresh = (spec[\refresh] ? 2).asFloat;
		chain =(spec[\chain] ? []).asArray.collect(_.asString);
		bankBy = (spec[\bankBy] ? \press).asSymbol;
		if(#[\press, \value].includes(bankBy).not) { RCLog.warn(\fxpages, "bankBy %: press or value: press".format(bankBy)); bankBy = \press };
		bankValues = (spec[\bankValues] ? [64, 127]).asArray.collect(_.asInteger);
		bankLights = (spec[\bankLights] ? [127]).asArray.collect(_.asInteger);
		bankWait = (spec[\bankWait] ? 0.1).asFloat;
		this.prGraph;
		layouts = Dictionary.new;
		(spec[\layouts] ? ()).keysValuesDo { |effect, list|
			list = list.asArray;
			if(list.size > 28) { RCLog.warn(\fxpages, "layout %: % knobs, four banks of seven at most".format(effect, list.size)) };
			layouts[effect.asString] = list.keep(28).collect { |d| this.prKnob(effect, d) };
		};
		nodes.do { |pair| if(layouts[pair[0]].isNil) { layouts[pair[1]] !? { |l| layouts[pair[0]] = l } } };   // a made node: its effect's layout
		pages = (spec[\pages] ? []).asArray.collect { |d, i|
			var effects = (d[\effects] ? []).asArray.collect(_.asString);
			if(effects.size > 7) { RCLog.warn(\fxpages, "page %: % effects, the buttons take 7".format(i + 1, effects.size)); effects = effects.keep(7) };
			effects.do { |e|
				if(chain.includesEqual(e).not and: { nodes.any { |pair| pair[0] == e }.not }) { RCLog.warn(\fxpages, "page %: % is not in the chain: it stays off".format(i + 1, e)) };
				if(layouts[e].isNil) { RCLog.warn(\fxpages, "page %: % has no layout: its mix alone".format(i + 1, e)) };
			};
			(name: (d[\name] ? ("page" ++ (i + 1))).asString, cc: (d[\cc] ? (i * 8)).asInteger, effects: effects)
		};
		places = Dictionary.new;
		mixPlaces = Dictionary.new;
		banks = Dictionary.new;
		this.effects.do { |e| places[e] = this.prRestPlaces(e); mixPlaces[e] = 0.0; banks[e] = 0 };
		fixed.do { |t| if(t[1] != "mix" and: { this.layout(t[0]).any { |knob| knob[\targets].any { |target| target[0] == t[1] } } }) {
			RCLog.warn(\fxpages, "set % %: a knob holds it too: the knob wins".format(t[0], t[1])) } };
		chosen = pages.collect { 0 };
		bankAt = pages.collect { nil };
		sendFunc = { |path, args| RCVisuals.sendArgs(path, args) };
		midiFunc = { |chan, cc, value| out !? { |o| o.control(chan, cc, value) } };
		timeFunc = { Main.elapsedTime };
	}

	// the graph's lines from the spec, each list in a fixed order (a parsed JSON object has none): by name
	prGraph {
		var sorted = { |d| (d ? ()).keys.asArray.collect(_.asString).sort.collect { |k| [k, (d ? ())[k.asSymbol]] } };
		var split = { |k| var i = k.findBackwards("."); if(i.isNil) { [k, ""] } { [k.copyRange(0, i - 1), k.copyToEnd(i + 1)] } };
		nodes = sorted.(spec[\nodes]).collect { |p| [p[0], p[1].asString] };
		inputs = sorted.(spec[\inputs]).collect { |p|   // "kind", "kind:address", a video's "kind:address:follow:offset"
			var parts = p[1].asString.split($:);
			[p[0]] ++ parts.collect { |x, i| if(i == 3) { x.asFloat } { x } }
		};
		wires = sorted.(spec[\wires]).collect { |p| split.(p[0]) ++ [p[1].asString] };
		looks = sorted.(spec[\look]);
		outNode = spec[\out] !? (_.asString);
		fixed = sorted.(spec[\set]).collect { |p| sorted.(p[1]).collect { |q| [p[0], q[0], q[1]] } }.flatten(1);
	}

	// a knob of a layout as kept: label, lo, hi, curve, rest, restPlace, targets [[param, lo, hi, curve], ...]
	prKnob { |effect, d|
		var k;
		if(d.isKindOf(Dictionary)) {
			k = (label: (d[\name] ? "?").asString, lo: (d[\lo] ? 0).asFloat, hi: (d[\hi] ? 1).asFloat, curve: (d[\curve] ? \lin).asSymbol);
			k[\rest] = (d[\rest] ? k[\lo]).asFloat;
			k[\targets] = (d[\set] ? []).asArray.collect { |t| [t[0].asString, t[1].asFloat, t[2].asFloat, this.prCurve(effect, t[0], t[3], t[1], t[2])] };
		} {
			d = d.asArray;
			k = (label: d[0].asString, lo: d[1].asFloat, hi: d[2].asFloat);
			k[\rest] = (d[3] ? d[1]).asFloat;
			k[\curve] = d[4];
			k[\targets] = [[k[\label], k[\lo], k[\hi], this.prCurve(effect, k[\label], d[4], k[\lo], k[\hi])]];
		};
		k[\curve] = this.prCurve(effect, k[\label], k[\curve], k[\lo], k[\hi]);
		k[\restPlace] = this.class.placeOf(k[\rest], k[\lo], k[\hi], k[\curve]);
		^k
	}

	prCurve { |effect, param, curve, lo, hi|
		curve = (curve ? \lin).asSymbol;
		if(#[\lin, \exp, \int].includes(curve).not) { RCLog.warn(\fxpages, "% %: no curve %: lin".format(effect, param, curve)); ^\lin };
		if(curve == \exp and: { lo <= 0 or: { hi <= 0 } }) { RCLog.warn(\fxpages, "% %: exp needs lo and hi above 0: lin".format(effect, param)); ^\lin };
		^curve
	}

	prRestPlaces { |effect|
		var layout = this.layout(effect);
		^Array.fill(7 * this.bankCount(effect), { |i| layout[i] !? { |knob| knob[\restPlace] } ? 0.0 })
	}

	//////// the numbers

	// a place (0..1) as a value over lo..hi
	*valueOf { |place, lo, hi, curve = \lin|
		var v;
		place = place.clip(0.0, 1.0);
		v = if(curve == \exp) { lo * ((hi / lo) ** place) } { lo + (place * (hi - lo)) };
		if(curve == \int) { ^v.round.asInteger };
		^v
	}

	// a value as its place over lo..hi (clipped to 0..1)
	*placeOf { |value, lo, hi, curve = \lin|
		var p;
		if(hi == lo) { ^0.0 };
		if(curve == \exp) { p = (value.max(min(lo, hi)) / lo).log / (hi / lo).log } { p = (value - lo) / (hi - lo) };
		^p.clip(0.0, 1.0)
	}

	// a fine value (msb + lsb/128, RCMidi's) as a place: the 14-bit value over 16383
	*positionOf { |v| ^(v * 128 / 16383).clip(0.0, 1.0) }

	// a mix knob's place as the mix: 0 up to mixDead (CC units), then linear to 1 at 127
	mixOf { |place| ^(((place.clip(0.0, 1.0) * 16383 / 128) - mixDead) / (127 - mixDead)).clip(0.0, 1.0) }

	//////// reading

	layout { |effect| ^layouts[effect.asString] ? [] }

	// every effect that has a state here: the chain's, then the pages' that are off it (nodes of a branch)
	effects {
		var all = chain.copy;
		pages.do { |pg| pg[\effects].do { |e| if(all.includesEqual(e).not) { all = all.add(e) } } };
		^all
	}

	// how many banks of seven knobs an effect's layout makes (one at least), and the one shown
	bankCount { |effect| ^(this.layout(effect).size / 7).ceil.asInteger.max(1) }
	bankOf { |effect| ^banks[effect.asString] ? 0 }

	// the effect a page drives (nil: a page without effects)
	effectOf { |page| ^pages[page] !? { |pg| pg[\effects][chosen[page]] } }

	// knob k's value for an effect (k 7: its mix) in the bank shown, or the bank given; nil for a knob its layout leaves free
	knobValue { |effect, k, bank|
		var e = effect.asString, i, knob;
		if(places[e].isNil) { ^nil };
		if(k == 7) { ^this.mixOf(mixPlaces[e]) };
		i = ((bank ? this.bankOf(e)) * 7) + k;
		knob = this.layout(e)[i];
		if(knob.isNil) { ^nil };
		^this.class.valueOf(places[e][i], knob[\lo], knob[\hi], knob[\curve])
	}

	mix { |effect| ^mixPlaces[effect.asString] !? { |p| this.mixOf(p) } ? 0.0 }

	// whether the spec sets this node's mix itself (set: { node: { mix: 1 } }): no knob's mix is sent for it
	fixedMix { |effect| ^fixed.any { |t| t[0] == effect.asString and: { t[1] == "mix" } } }

	//////// the device

	// MIDI up (midi: false, none), the page told the whole state from a clean one (/fx/clear first), each page's
	// chosen effect sent to the motors and lights.
	start { |withMidi = true|
		if(started) { ^this };
		started = true;
		if(withMidi) { this.prStartMidi };
		this.sendState(true);
		pages.size.do { |p| this.select(p, chosen[p]) };
		RCLog.post(\fxpages, "% pages, % effects in the chain on %%".format(pages.size, chain.size, deviceName,
			if(source.notNil) { ", connected" } { "" }), true);
	}

	// as REShowRoto: the device's source alone connected (connectAll deadlocked CoreMIDI at exit one run in six),
	// its destination for the motors and lights; MIDIClient brought up when nobody had (and disposed by stop)
	prStartMidi {
		var dst;
		if(MIDIClient.initialized.not) {
			RCGuard.call(\fxpages, nil) { MIDIClient.init(verbose: false) };
			ownsMidi = MIDIClient.initialized;
		};
		if(MIDIClient.initialized.not) { RCLog.warn(\fxpages, "MIDI not initialized: no knobs, no motors"); ^this };
		sourceIndex = (MIDIClient.sources ? []).detectIndex { |e| e.name == deviceName or: { e.device == deviceName } };
		if(sourceIndex.isNil) {
			RCLog.warn(\fxpages, "MIDI device \"%\" not found among % sources: plug it in and start again".format(deviceName, MIDIClient.sources.size));
		} {
			source = MIDIClient.sources[sourceIndex];
			RCGuard.call(\fxpages, nil) { MIDIIn.connect(sourceIndex, source) };
		};
		dst = (MIDIClient.destinations ? []).detectIndex { |e| e.name == deviceName or: { e.device == deviceName } };
		if(dst.isNil) { RCLog.warn(\fxpages, "no MIDI destination \"%\": the motors and lights stay where they are".format(deviceName)) } {
			out = RCGuard.call(\fxpages, nil) { MIDIOut(dst).latency_(0) };
		};
		this.installMappings;
	}

	// rc_fxpages_knob_<page>_<k> (a fine pair) and rc_fxpages_button_<page>_<b>, through RCMidi
	installMappings {
		midi !? (_.freeAll);
		midi = RCMidi((name: \fxpages));
		pages.do { |pg, p|
			8.do { |k|
				RCGuard.call(\fxpages, nil) {
					midi.control(("knob_" ++ p ++ "_" ++ k).asSymbol, pg[\cc] + k, knobChan, deviceName, { |x| x }, { |v|
						this.knobReceived(p, k, this.class.positionOf(v), timeFunc.value)
					}, fine: true);
					midi.control(("button_" ++ p ++ "_" ++ k).asSymbol, pg[\cc] + k, buttonChan, deviceName, { |x| x }, { |v|
						this.buttonReceived(p, k, v, timeFunc.value)
					}, fine: false);
				};
			};
		};
		^midi
	}

	// keepMidi: the mappings alone, CoreMIDI left as it is, for a process about to exit (REShowRoto's, HL3-G6)
	stop { |keepMidi = false|
		midi !? (_.freeAll);
		midi = nil;
		if(keepMidi.not) {
			source !? { |s| RCGuard.call(\fxpages, nil) { MIDIIn.disconnect(sourceIndex, s) } };
			if(ownsMidi) { RCGuard.call(\fxpages, nil) { MIDIClient.disposeClient } };
		};
		ownsMidi = false;
		source = nil;
		sourceIndex = nil;
		out = nil;
		started = false;
	}

	//////// what the handlers call

	// Knob k of a page at a place: a parameter (or several) of the page's effect in the bank shown, or (k 7) its
	// mix. Returns the value, nil for a knob the layout leaves free.
	knobReceived { |page, k, place, now|
		var effect = this.effectOf(page), knob, i;
		if(effect.isNil) { ^nil };
		place = place.asFloat.clip(0.0, 1.0);
		if(k == 7) {
			mixPlaces[effect] = place;
			if(this.fixedMix(effect).not) { this.prSendMix(effect) };
			dirty = true;
			^this.mix(effect)
		};
		i = (this.bankOf(effect) * 7) + k;
		knob = this.layout(effect)[i];
		if(knob.isNil) { ^nil };
		places[effect][i] = place;
		this.prSendKnob(effect, i);
		dirty = true;
		^this.knobValue(effect, k)
	}

	// Button b of a page: an effect chosen (any value: both are the same choice), the eighth the reset; the chosen
	// effect's button again goes round its banks (bankBy; the header). Returns the effect's name.
	buttonReceived { |page, b, v, now|
		var pg = pages[page], effect, n;
		if(pg.isNil) { ^nil };
		if(b == 7) { ^this.reset(page) };
		if(b >= pg[\effects].size) { ^nil };
		effect = pg[\effects][b];
		n = this.bankCount(effect);
		v = v ? 0;
		if(bankBy == \value) {   // the button says its state: 0 its dark one (the first bank), else the bank whose value is nearest
			banks[effect] = if(v <= 0) { 0 } { bankValues.keep(n).collect { |x| (x - v).abs }.minIndex };
			^this.select(page, b)
		};
		if(b == chosen[page] and: { n > 1 } and: { v > 0 }) {
			now = now ?? { timeFunc.value };
			if(bankAt[page].isNil or: { (now - bankAt[page]) >= bankWait }) {
				banks[effect] = (this.bankOf(effect) + 1) % n;
				bankAt[page] = now;
			};
		};
		^this.select(page, b)
	}

	// A page's effect chosen (its index among the page's): the motors to its places in the bank it was left on, its
	// button lit. Returns its name.
	select { |page, i|
		var pg = pages[page];
		if(pg.isNil or: { i.isNil } or: { i >= pg[\effects].size }) { ^nil };
		chosen[page] = i;
		this.prMotors(page);
		this.prLights(page);
		dirty = true;
		^pg[\effects][i]
	}

	// The bank of a page's effect shown (0 the first), the motors and its light following. Returns the bank.
	setBank { |page, bank|
		var effect = this.effectOf(page);
		if(effect.isNil) { ^nil };
		banks[effect] = bank.asInteger.clip(0, this.bankCount(effect) - 1);
		this.select(page, chosen[page]);
		^banks[effect]
	}

	// The page's effect back to rest: every knob's parameters in every bank (its mix as it is), the motors with them.
	reset { |page|
		var effect = this.effectOf(page);
		if(effect.isNil) { ^nil };
		this.layout(effect).do { |knob, i|
			places[effect][i] = knob[\restPlace];
			this.prSendKnob(effect, i);
		};
		this.prMotors(page);
		dirty = true;
		^effect
	}

	// The whole state to the page: the graph (nodes, source nodes, looks, the values set once: lines that change
	// nothing when they are there already), every layout parameter and every mix as kept, the wires and the node
	// shown, then the chain (clear: /fx/clear first, a clean page: what the layouts leave out at the bank's defaults).
	sendState { |clear = false|
		if(clear) { this.prSend('/fx/clear', []) };
		nodes.do { |pair| this.prSend('/fx/node', pair) };
		inputs.do { |t| this.prSend('/fx/input', t) };
		looks.do { |pair| this.prSend('/fx/look', pair) };
		fixed.do { |t| this.prSend('/fx/set', t) };
		this.effects.do { |effect|
			this.layout(effect).size.do { |i| this.prSendKnob(effect, i) };
			if(this.fixedMix(effect).not) { this.prSendMix(effect) };
		};
		wires.do { |t| this.prSend('/fx/wire', t) };
		outNode !? { |o| this.prSend('/fx/out', [o]) };
		this.prSend('/fx/chain', chain.copy);
		stateAt = timeFunc.value;
		dirty = true;
	}

	// knob i of an effect's layout (any bank) sent
	prSendKnob { |effect, i|
		var knob = this.layout(effect)[i], place = places[effect][i];
		knob !? { knob[\targets].do { |t| this.prSend('/fx/set', [effect, t[0], this.class.valueOf(place, t[1], t[2], t[3])]) } };
	}

	prSendMix { |effect| this.prSend('/fx/set', [effect, "mix", this.mix(effect)]) }

	// the page's eight knobs to the places of its effect in the bank shown (a knob its layout leaves free: down)
	prMotors { |page|
		var pg = pages[page], effect = this.effectOf(page), layout, base;
		if(effect.isNil) { ^this };
		layout = this.layout(effect);
		base = this.bankOf(effect) * 7;
		8.do { |k|
			var place = case { k == 7 } { mixPlaces[effect] } { layout[base + k].notNil } { places[effect][base + k] } { 0.0 };
			var v = (place * 16383).round.asInteger;
			this.prMidi(knobChan, pg[\cc] + k, v >> 7);
			this.prMidi(knobChan, pg[\cc] + k + 32, v & 127);
		};
	}

	// the page's chosen button lit (by the bank shown: its state's value, or its light), the others (and the reset) dark
	prLights { |page|
		var pg = pages[page], effect = this.effectOf(page), bank = effect !? { this.bankOf(effect) } ? 0;
		var lit = if(bankBy == \value) { bankValues.clipAt(bank) } { bankLights.clipAt(bank) };
		8.do { |b| this.prMidi(buttonChan, pg[\cc] + b, if(b == chosen[page]) { lit } { 0 }) };
	}

	prMidi { |chan, cc, value| RCGuard.call(\fxpages, nil) { midiFunc.value(chan, cc, value) } }

	prSend { |path, args| RCGuard.call(\fxpages, nil) { sendFunc.value(path, args) } }

	//////// the panel

	// What the panel page draws (public/fxpanel/index.html's header).
	panelState {
		^(pages: pages.collect { |pg, p|
			var effect = this.effectOf(p), layout = this.layout(effect), bank = effect !? { this.bankOf(effect) } ? 0, base = bank * 7;
			(name: pg[\name], cc: pg[\cc], effect: effect, selected: chosen[p], bank: bank, banks: effect !? { this.bankCount(effect) } ? 1,
				knobs: Array.fill(8, { |k|
					case
					{ effect.isNil } { nil }
					{ k == 7 } { (label: "mix", value: this.mix(effect), pos: mixPlaces[effect]) }
					{ layout[base + k].isNil } { nil }
					{ (label: layout[base + k][\label], value: this.knobValue(effect, k), pos: places[effect][base + k]) }
				}),
				buttons: Array.fill(7, { |b| pg[\effects][b] !? { |e| (effect: e, mix: this.mix(e), banks: this.bankCount(e)) } }) ++ [(label: "reset")])
		}, chain: chain.collect { |e| (effect: e, mix: this.mix(e)) })
	}

	// The whole state again every `refresh` seconds once started (an effects page opened or reloaded late heals
	// within that: the same values, no glide, so a page already in step sees nothing); the panel's state when
	// something changed since the last, and every second (a panel opened late).
	tick { |now|
		now = now ?? { timeFunc.value };
		if(started and: { refresh > 0 } and: { stateAt.isNil or: { (now - stateAt) >= refresh } }) { this.sendState; stateAt = now };
		if(dirty or:{ panelAt.isNil } or: { (now - panelAt) >= 1 }) { this.sendPanel(now) };
	}

	sendPanel { |now|
		dirty = false;
		panelAt = now ?? { timeFunc.value };
		this.prSend('/fxpanel/state', [REJSON.stringify(this.panelState)]);
	}

	// one line per page: its effect (its bank when it has several) and its knobs' values
	status {
		^pages.collect { |pg, p|
			var effect = this.effectOf(p), n = effect !? { this.bankCount(effect) } ? 1;
			"page % % (cc %): %% · %".format(p + 1, pg[\name], pg[\cc], effect ? "-", if(n > 1) { " " ++ (this.bankOf(effect) + 1) ++ "/" ++ n } { "" },
				8.collect { |k| var v = effect !? { this.knobValue(effect, k) }; if(v.isNil) { "-" } { v.round(0.001) } }.join(" "))
		}
	}

	printOn { |stream| stream << "REShowFxPages(" << pages.size << " pages, " << chain.size << " effects" << if(started) { ", started)" } { ")" } }
}
