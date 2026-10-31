// reCording — the effects page's chain played from the Roto-Control's pages at HadronLake3's show console
// (REShowConsole, Oct 31 2026). Every effect of the chain is on, in one fixed order, at mix 0 (bypassed by the page:
// it costs nothing); each page of the device has seven buttons choosing one of its seven effects and an eighth
// resetting the chosen one, and eight knobs driving it: seven parameters laid out per effect, the eighth its mix.
// Choosing an effect turns the knobs' motors to its places and lights its button (the Roto-Control in MIDI mode
// follows the CCs it receives and sends nothing back: Oct 31 2026); a panel page (HomewareVisuals' public/fxpanel/)
// draws both pages as the device lays them out.
//
//   p = REShowFxPages(show[\fx]);
//   p.start;                       // MIDI up (the device's source and destination; no device: a warning, the rest works),
//                                  // the page told (/fx/clear, every layout parameter at rest, every mix 0, /fx/chain),
//                                  // each page's first effect chosen
//   p.start(false);                // the same without MIDI (tests; a console without the device)
//   p.knobReceived(0, 7, 0.6);     // page 1's eighth knob at 0.6: its effect's mix (what the MIDI handlers call)
//   p.buttonReceived(1, 2, 127);   // page 2's third button: its third effect chosen
//   p.select(0, 3); p.reset(0); p.effectOf(0); p.knobValue("glow", 0); p.mix("glow")
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
// cc + 32, RCMidi's fine pairing). A layout: up to seven knobs, each [param, lo, hi, rest] with an optional curve:
// "lin" (the default), "exp" (lo and hi above 0: equal ratios along the knob) or "int" (rounded: a mode, a count); or
// one knob over several parameters, { "name": "warmth", "lo": -1, "hi": 1, "rest": 0, "set": [["tint_r", 0.5, 1.5],
// ["tint_b", 1.5, 0.5]] } (each from its lo to its hi along the knob). The state is every knob's place k (0..1, the
// 14-bit value over 16383) per effect: a parameter is lo + k (hi - lo), exp lo (hi / lo)^k. The mix: 0 up to mixDead
// (CC units: 0.5 of 127, so a knob turned down is off for sure), then linear to 1 at 127.
// Sends: /fx/set <effect> <param> <value> and the state through sendFunc (RCVisuals.sendArgs); the motors (14-bit) and
// the lights (127 the chosen button, 0 the others) through midiFunc { |chan, cc, value| } (a MIDIOut to the device);
// /fxpanel/state <json> (the panel page's header has its shape) from tick.

REShowFxPages {
	var <spec, <deviceName, <knobChan = 0, <buttonChan = 1, <mixDead = 0.5, <>refresh = 2;
	var <chain, <pages, <layouts;   // chain: [String]; pages: [(name, cc, effects)]; layouts: effect → [knob Events]
	var <places, <chosen;           // effect → 8 places (0..1; the eighth the mix's); per page, an index into its effects
	var <>sendFunc, <>midiFunc, <>timeFunc;
	var <midi, <out, <started = false, ownsMidi = false;
	var <source, sourceIndex;
	var dirty = true, panelAt, stateAt;

	*new { |spec| ^super.new.initREShowFxPages(spec) }

	initREShowFxPages { |specarg|
		spec = specarg ? ();
		deviceName = (spec[\device] ? "Roto-Control").asString;
		knobChan = (spec[\knobChan] ? 0).asInteger;
		buttonChan = (spec[\buttonChan] ? 1).asInteger;
		mixDead = (spec[\mixDead] ? 0.5).asFloat;
		refresh = (spec[\refresh] ? 2).asFloat;
		chain =(spec[\chain] ? []).asArray.collect(_.asString);
		layouts = Dictionary.new;
		(spec[\layouts] ? ()).keysValuesDo { |effect, list|
			list = list.asArray;
			if(list.size > 7) { RCLog.warn(\fxpages, "layout %: % knobs, the page has 7 (the eighth is the mix)".format(effect, list.size)) };
			layouts[effect.asString] = list.keep(7).collect { |d| this.prKnob(effect, d) };
		};
		pages = (spec[\pages] ? []).asArray.collect { |d, i|
			var effects = (d[\effects] ? []).asArray.collect(_.asString);
			if(effects.size > 7) { RCLog.warn(\fxpages, "page %: % effects, the buttons take 7".format(i + 1, effects.size)); effects = effects.keep(7) };
			effects.do { |e|
				if(chain.includesEqual(e).not) { RCLog.warn(\fxpages, "page %: % is not in the chain: it stays off".format(i + 1, e)) };
				if(layouts[e].isNil) { RCLog.warn(\fxpages, "page %: % has no layout: its mix alone".format(i + 1, e)) };
			};
			(name: (d[\name] ? ("page" ++ (i + 1))).asString, cc: (d[\cc] ? (i * 8)).asInteger, effects: effects)
		};
		places = Dictionary.new;
		(chain ++ pages.collect { |pg| pg[\effects] }.flatten).do { |e| if(places[e].isNil) { places[e] = this.prRestPlaces(e) } };
		chosen = pages.collect { 0 };
		sendFunc = { |path, args| RCVisuals.sendArgs(path, args) };
		midiFunc = { |chan, cc, value| out !? { |o| o.control(chan, cc, value) } };
		timeFunc = { Main.elapsedTime };
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
		^Array.fill(8, { |k| if(k < 7) { layout[k] !? { |knob| knob[\restPlace] } ? 0.0 } { 0.0 } })
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

	// the effect a page drives (nil: a page without effects)
	effectOf { |page| ^pages[page] !? { |pg| pg[\effects][chosen[page]] } }

	// knob k's value for an effect (k 7: its mix); nil for a knob its layout leaves free
	knobValue { |effect, k|
		var place = places[effect.asString] !? { |a| a[k] }, knob;
		if(place.isNil) { ^nil };
		if(k == 7) { ^this.mixOf(place) };
		knob = this.layout(effect)[k];
		if(knob.isNil) { ^nil };
		^this.class.valueOf(place, knob[\lo], knob[\hi], knob[\curve])
	}

	mix { |effect| ^places[effect.asString] !? { |a| this.mixOf(a[7]) } ? 0.0 }

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

	stop {
		midi !? (_.freeAll);
		midi = nil;
		source !? { |s| RCGuard.call(\fxpages, nil) { MIDIIn.disconnect(sourceIndex, s) } };
		source = nil;
		sourceIndex = nil;
		out = nil;
		if(ownsMidi) {
			ownsMidi = false;
			RCGuard.call(\fxpages, nil) { MIDIClient.disposeClient };
		};
		started = false;
	}

	//////// what the handlers call

	// Knob k of a page at a place: a parameter (or several) of the page's effect, or (k 7) its mix. Returns the
	// value, nil for a knob the layout leaves free.
	knobReceived { |page, k, place, now|
		var effect = this.effectOf(page), knob;
		if(effect.isNil) { ^nil };
		place = place.asFloat.clip(0.0, 1.0);
		if(k == 7) {
			places[effect][7] = place;
			this.prSendMix(effect);
			dirty = true;
			^this.mix(effect)
		};
		knob = this.layout(effect)[k];
		if(knob.isNil) { ^nil };
		places[effect][k] = place;
		this.prSendKnob(effect, k);
		dirty = true;
		^this.knobValue(effect, k)
	}

	// Button b of a page (any value: both are the same choice): an effect chosen, the eighth the reset.
	buttonReceived { |page, b, v, now|
		var pg = pages[page];
		if(pg.isNil) { ^nil };
		if(b == 7) { ^this.reset(page) };
		if(b < pg[\effects].size) { ^this.select(page, b) };
		^nil
	}

	// A page's effect chosen (its index among the page's): the motors to its places, its button lit. Returns its name.
	select { |page, i|
		var pg = pages[page];
		if(pg.isNil or: { i.isNil } or: { i >= pg[\effects].size }) { ^nil };
		chosen[page] = i;
		this.prMotors(page);
		this.prLights(page);
		dirty = true;
		^pg[\effects][i]
	}

	// The page's effect back to rest: its seven knobs' parameters (its mix as it is), the motors with them.
	reset { |page|
		var effect = this.effectOf(page);
		if(effect.isNil) { ^nil };
		this.layout(effect).do { |knob, k|
			places[effect][k] = knob[\restPlace];
			this.prSendKnob(effect, k);
		};
		this.prMotors(page);
		dirty = true;
		^effect
	}

	// The whole state to the page: every layout parameter and every mix as kept, then the chain (clear: /fx/clear
	// first, a clean page: what the layouts leave out at the bank's defaults).
	sendState { |clear = false|
		if(clear) { this.prSend('/fx/clear', []) };
		chain.do { |effect|
			this.layout(effect).size.do { |k| this.prSendKnob(effect, k) };
			this.prSendMix(effect);
		};
		this.prSend('/fx/chain', chain.copy);
		stateAt = timeFunc.value;
		dirty = true;
	}

	prSendKnob { |effect, k|
		var knob = this.layout(effect)[k], place = places[effect][k];
		knob !? { knob[\targets].do { |t| this.prSend('/fx/set', [effect, t[0], this.class.valueOf(place, t[1], t[2], t[3])]) } };
	}

	prSendMix { |effect| this.prSend('/fx/set', [effect, "mix", this.mix(effect)]) }

	// the page's eight knobs to the places of its effect (a knob its layout leaves free: down)
	prMotors { |page|
		var pg = pages[page], effect = this.effectOf(page), layout;
		if(effect.isNil) { ^this };
		layout = this.layout(effect);
		8.do { |k|
			var place = if(k == 7 or: { layout[k].notNil }) { places[effect][k] } { 0.0 };
			var v = (place * 16383).round.asInteger;
			this.prMidi(knobChan, pg[\cc] + k, v >> 7);
			this.prMidi(knobChan, pg[\cc] + k + 32, v & 127);
		};
	}

	// the page's chosen button lit, the others (and the reset) dark
	prLights { |page|
		var pg = pages[page];
		8.do { |b| this.prMidi(buttonChan, pg[\cc] + b, if(b == chosen[page]) { 127 } { 0 }) };
	}

	prMidi { |chan, cc, value| RCGuard.call(\fxpages, nil) { midiFunc.value(chan, cc, value) } }

	prSend { |path, args| RCGuard.call(\fxpages, nil) { sendFunc.value(path, args) } }

	//////// the panel

	// What the panel page draws (public/fxpanel/index.html's header).
	panelState {
		^(pages: pages.collect { |pg, p|
			var effect = this.effectOf(p), layout = this.layout(effect);
			(name: pg[\name], cc: pg[\cc], effect: effect, selected: chosen[p],
				knobs: Array.fill(8, { |k|
					case
					{ effect.isNil } { nil }
					{ k == 7 } { (label: "mix", value: this.mix(effect), pos: places[effect][7]) }
					{ layout[k].isNil } { nil }
					{ (label: layout[k][\label], value: this.knobValue(effect, k), pos: places[effect][k]) }
				}),
				buttons: Array.fill(7, { |b| pg[\effects][b] !? { |e| (effect: e, mix: this.mix(e)) } }) ++ [(label: "reset")])
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

	// one line per page: its effect and its knobs' values
	status {
		^pages.collect { |pg, p|
			var effect = this.effectOf(p);
			"page % % (cc %): % · %".format(p + 1, pg[\name], pg[\cc], effect ? "-",
				8.collect { |k| var v = effect !? { this.knobValue(effect, k) }; if(v.isNil) { "-" } { v.round(0.001) } }.join(" "))
		}
	}

	printOn { |stream| stream << "REShowFxPages(" << pages.size << " pages, " << chain.size << " effects" << if(started) { ", started)" } { ")" } }
}
