// reCurrent — MIDI CC control of a song (BlockBeats' control_synth / control_midi /
// control_attribute), with the 14-bit "fine" pairing (cc, cc + 32): the MSB on cc, then its
// LSB on cc + 32, as the MIDI spec sends them; the LSB completes the value.
//
//   ~song.midi.controlSynth(~reverb, \mix, { |x| x / 127 }, 0, 0, "Roto-Control", fine: true);
//   ~song.midi.controlSynth({ ~reverb }, \mix, ...);   // a Function target is resolved per message
//   ~song.midi.control(\stretch, 1, 0, "Roto-Control", { |x| 10 * x / 127 }, { |v| ~batch.editAttr("timeStretch", v) });
// Every MIDIdef made here is named rc_<song>_<name> (two songs never share
// one), is permanent (Cmd-Period leaves it), and is freed by free(name),
// freeMatching(prefix), freeCC(cc, chan) or freeAll. A second mapping on a
// (cc, chan) the song already listens to is warned about: both would fire.

RCMidi {
	classvar fineValues;   // srcID → chan → ccNum → defKey → lsb value
	// seconds an MSB waits for its LSB before it fires alone (a sender may leave out an LSB of 0)
	classvar <>pairWait = 0.02;

	var <song, <defs, <ccMap, throttles, pairs, <actions, <devices;

	*initClass {
		fineValues = IdentityDictionary.new;
	}

	*new { |song| ^super.new.initRCMidi(song) }

	initRCMidi { |songarg|
		song = songarg;
		defs = IdentityDictionary.new;    // name → [MIDIdef keys]
		ccMap = Dictionary.new;           // [ccNum, chan] → [names]
		throttles = IdentityDictionary.new;   // name → throttle state of a mapping (see control)
		pairs = IdentityDictionary.new;       // name → a fine mapping's MSB and the one waiting for its LSB
		actions = IdentityDictionary.new;     // name → action, for replay
		devices = IdentityDictionary.new;     // name → device name
	}

	// The mapping names of a device (every mapping when deviceName is nil), sorted:
	// what a score recorder groups under one voice.
	names { |deviceName|
		^defs.keys.select { |n| deviceName.isNil or: { devices[n] == deviceName.asString } }.asArray.sort { |a, b| a.asString <= b.asString }
	}

	// A mapping's action with a mapped value (a recorded one, REScorePlayer):
	// the valFunc is not applied again. False when there is no such mapping.
	replay { |name, value, raw|
		var action = actions[name.asSymbol];
		if(action.isNil) { ^false };
		RCGuard.call(this.defKey(name), nil) { action.value(value, raw) };
		^true
	}

	defKey { |name| ^("rc_" ++ song.name ++ "_" ++ name).asSymbol }

	// uid of the first MIDI source named deviceName; nil (+ warning) when absent.
	*findSrcId { |deviceName|
		(MIDIClient.sources ? []).do { |endpoint|
			if(endpoint.name == deviceName.asString) { ^endpoint.uid };
		};
		RCLog.warn(\midi, "MIDI device \"%\" not found: mapping listens to all sources".format(deviceName));
		^nil
	}

	*fineValue { |srcID, chan, ccNum, name|
		^fineValues[srcID ? \any] !? { |d| d[chan] } !? { |d| d[ccNum] } !? { |d| d[name] }
	}

	*setFineValue { |srcID, chan, ccNum, name, val|
		var d = fineValues;
		[srcID ? \any, chan, ccNum].do { |k|
			d[k] = d[k] ?? { IdentityDictionary.new };
			d = d[k];
		};
		d[name] = val;
	}

	// Generic mapping: action.(valFunc.(cc value), raw value). Returns the MIDIdef keys.
	// fine: the value is msb + lsb/128 (cc + 32 carries the low 7 bits). As the MIDI spec has
	// it, an MSB sets the LSB to 0 and the LSB that follows it completes the value: the MSB
	// fires only when no LSB comes within pairWait, the LSB fires (alone too: a fine move).
	// Fired on the MSB with the last LSB, a knob turned up across a step read 63.98, 64.98,
	// 64.02: a jump of a whole step and back.
	// throttle (seconds, nil = every message fires): a knob turn sends tens of
	// messages per second, twice as many with fine, and an action over a whole
	// batch costs milliseconds each, which starves the clock. The first message
	// fires at once; the ones arriving within the window are swallowed and the
	// last of them fires when it closes, re-arming while messages keep coming.
	control { |name, ccNum, chan, deviceName, valFunc, action, fine = false, throttle|
		var srcID = this.class.findSrcId(deviceName);
		var key, lsbKey, keys, slot, fire, deliver, state, pair;
		name = name.asSymbol;
		key = this.defKey(name);
		keys = [key];
		this.free(name);
		slot = ccMap[[ccNum, chan]] ? [];
		if(slot.size > 0) {
			RCLog.warn(\midi, "% already listens to cc % chan % (%): both mappings will fire".format(song.name, ccNum, chan, slot));
		};
		// recorded as an input (RETap) with the raw and the mapped value; the
		// action runs inside the message's cause
		fire = { |total|
			RCGuard.call(key, nil) {
				var v = valFunc.value(total);
				RCLog.info(key, { "= " ++ v.asString });
				if(RETap.active) {
					RETap.input(\midi, song, (name: name, raw: total, value: v), { action.value(v, total) });
				} {
					action.value(v, total);
				};
			};
		};
		if(throttle.notNil) {
			state = (armed: false, pending: nil);
			throttles[name] = state;
		};
		deliver = { |total|
			if(throttle.isNil) {
				fire.(total);
			} {
				if(state[\armed]) {
					state[\pending] = total;
				} {
					state[\armed] = true;
					fire.(total);
					SystemClock.sched(throttle, {
						var pending = state[\pending];
						state[\pending] = nil;
						if(pending.notNil and: { throttles[name] === state }) {
							fire.(pending);
							throttle   // another window: the knob is still turning
						} {
							state[\armed] = false;
							nil
						}
					});
				};
			};
		};
		if(fine) {
			pair = (msb: nil, waiting: nil);
			pairs[name] = pair;
			lsbKey = (key ++ "_lsb").asSymbol;
			MIDIdef.cc(lsbKey, { |val|
				RCMidi.setFineValue(srcID, chan, ccNum, key, val);
				pair[\waiting] = nil;
				pair[\msb] !? { |msb| deliver.(msb + (val / 128)) };   // before any MSB: nothing to complete
			}, ccNum + 32, chan, srcID).permanent_(true);
			keys = keys ++ [lsbKey];
		};
		MIDIdef.cc(key, { |val|
			if(fine) {
				var token = Object.new;
				pair[\msb] = val;
				pair[\waiting] = token;
				RCMidi.setFineValue(srcID, chan, ccNum, key, 0);
				SystemClock.sched(pairWait, {
					if(pair[\waiting] === token and: { pairs[name] === pair }) { pair[\waiting] = nil; deliver.(val) };
					nil
				});
			} {
				deliver.(val);
			};
		}, ccNum, chan, srcID).permanent_(true);
		defs[name] = keys;
		actions[name] = action;
		devices[name] = deviceName !? (_.asString);
		ccMap[[ccNum, chan]] = slot ++ [name];
		^keys
	}

	// target.set(attr, value) (or target.set(value) for buses) on each CC
	// message. A Function target is evaluated per message ({ ~reverb }), so a
	// node re-created at scene/init keeps its mapping.
	controlSynth { |synth, attr, valFunc, ccNum, chan, deviceName, name, fine = false, setNothing = false, throttle|
		name = name ?? { ("ctl_" ++ (attr ? "value") ++ "_" ++ ccNum ++ "_" ++ chan ++ "_" ++ deviceName).asSymbol };
		^this.control(name, ccNum, chan, deviceName, valFunc, { |v|
			var target = synth.value;
			if(setNothing.not and: { target.notNil }) {
				if(attr.notNil) { target.set(attr.asSymbol, v) } { target.set(v) };
			};
		}, fine, throttle)
	}

	// object[key] = value (dictionaries) or object.key_(value) (setters) on each CC message.
	controlAttribute { |object, key, valFunc, ccNum, chan, deviceName, name, fine = false, throttle|
		name = name ?? { ("attr_" ++ key ++ "_" ++ ccNum ++ "_" ++ chan ++ "_" ++ deviceName).asSymbol };
		^this.control(name, ccNum, chan, deviceName, valFunc, { |v|
			if(object.isKindOf(Dictionary)) { object.put(key.asSymbol, v) } { object.perform(key.asSymbol.asSetter, v) };
		}, fine, throttle)
	}

	free { |name|
		name = name.asSymbol;
		defs[name] !? { |keys|
			keys.do { |k| MIDIdef.all[k] !? (_.free) };   // MIDIdef(k) would re-create an empty def
			defs.removeAt(name);
			actions.removeAt(name);
			devices.removeAt(name);
			throttles.removeAt(name);   // a pending throttled value is dropped with its mapping
			pairs.removeAt(name);       // and an MSB waiting for its LSB
			this.prForgetFineValues(keys);
			ccMap.keysValuesDo { |cc, names| ccMap[cc] = names.reject { |n| n == name } };
			ccMap.keys.copy.do { |cc| if(ccMap[cc].isEmpty) { ccMap.removeAt(cc) } };
		};
	}

	// Free every mapping whose name starts with prefix ("bass_"). Returns the names.
	freeMatching { |prefix|
		var names = defs.keys.select { |n| n.asString.beginsWith(prefix.asString) }.asArray;
		names.do { |n| this.free(n) };
		^names
	}

	// Free every mapping listening on (ccNum, chan). Returns the names.
	freeCC { |ccNum, chan|
		var names = (ccMap[[ccNum, chan]] ? []).copy;
		names.do { |n| this.free(n) };
		^names
	}

	freeAll {
		defs.keys.copy.do { |name| this.free(name) };
	}

	prForgetFineValues { |keys|
		fineValues.do { |chans| chans.do { |ccs| ccs.do { |byKey| keys.do { |k| byKey.removeAt(k) } } } };
	}

	printOn { |stream| stream << "RCMidi(" << song.name << ", " << defs.size << " mappings)" }
}
