// reCurrent — MIDI CC control of a song (BlockBeats' control_synth / control_midi /
// control_attribute), with the 14-bit "fine" pairing (cc, cc + 32).
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

	var <song, <defs, <ccMap;

	*initClass {
		fineValues = IdentityDictionary.new;
	}

	*new { |song| ^super.new.initRCMidi(song) }

	initRCMidi { |songarg|
		song = songarg;
		defs = IdentityDictionary.new;    // name → [MIDIdef keys]
		ccMap = Dictionary.new;           // [ccNum, chan] → [names]
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
	// fine: the value is msb + lsb/128 (cc + 32 carries the low 7 bits).
	control { |name, ccNum, chan, deviceName, valFunc, action, fine = false|
		var srcID = this.class.findSrcId(deviceName);
		var key, lsbKey, keys, slot;
		name = name.asSymbol;
		key = this.defKey(name);
		keys = [key];
		this.free(name);
		slot = ccMap[[ccNum, chan]] ? [];
		if(slot.size > 0) {
			RCLog.warn(\midi, "% already listens to cc % chan % (%): both mappings will fire".format(song.name, ccNum, chan, slot));
		};
		if(fine) {
			lsbKey = (key ++ "_lsb").asSymbol;
			MIDIdef.cc(lsbKey, { |val|
				RCMidi.setFineValue(srcID, chan, ccNum, key, val);
			}, ccNum + 32, chan, srcID).permanent_(true);
			keys = keys ++ [lsbKey];
		};
		MIDIdef.cc(key, { |val|
			var total = val;
			if(fine) { total = total + ((RCMidi.fineValue(srcID, chan, ccNum, key) ? 0) / 128) };
			RCGuard.call(key, nil) {
				var v = valFunc.value(total);
				RCLog.info(key, { "= " ++ v.asString });
				action.value(v, total);
			};
		}, ccNum, chan, srcID).permanent_(true);
		defs[name] = keys;
		ccMap[[ccNum, chan]] = slot ++ [name];
		^keys
	}

	// target.set(attr, value) (or target.set(value) for buses) on each CC
	// message. A Function target is evaluated per message ({ ~reverb }), so a
	// node re-created at scene/init keeps its mapping.
	controlSynth { |synth, attr, valFunc, ccNum, chan, deviceName, name, fine = false, setNothing = false|
		name = name ?? { ("ctl_" ++ (attr ? "value") ++ "_" ++ ccNum ++ "_" ++ chan ++ "_" ++ deviceName).asSymbol };
		^this.control(name, ccNum, chan, deviceName, valFunc, { |v|
			var target = synth.value;
			if(setNothing.not and: { target.notNil }) {
				if(attr.notNil) { target.set(attr.asSymbol, v) } { target.set(v) };
			};
		}, fine)
	}

	// object[key] = value (dictionaries) or object.key_(value) (setters) on each CC message.
	controlAttribute { |object, key, valFunc, ccNum, chan, deviceName, name, fine = false|
		name = name ?? { ("attr_" ++ key ++ "_" ++ ccNum ++ "_" ++ chan ++ "_" ++ deviceName).asSymbol };
		^this.control(name, ccNum, chan, deviceName, valFunc, { |v|
			if(object.isKindOf(Dictionary)) { object.put(key.asSymbol, v) } { object.perform(key.asSymbol.asSetter, v) };
		}, fine)
	}

	free { |name|
		name = name.asSymbol;
		defs[name] !? { |keys|
			keys.do { |k| MIDIdef.all[k] !? (_.free) };   // MIDIdef(k) would re-create an empty def
			defs.removeAt(name);
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
