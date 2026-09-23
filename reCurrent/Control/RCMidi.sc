// reCurrent — MIDI CC control of a song (BlockBeats' control_synth / control_midi /
// control_attribute), with the 14-bit "fine" pairing (cc, cc + 32).
//
//   ~song.midi.controlSynth(~reverb, \mix, { |x| x / 127 }, 0, 0, "Roto-Control", fine: true);
//   ~song.midi.control(\stretch, 1, 0, "Roto-Control", { |x| 10 * x / 127 }, { |v| ~batch.editAttr("timeStretch", v) });
// Every MIDIdef made here is remembered by name and freed by free(name)/freeAll.

RCMidi {
	classvar fineValues;   // srcID → chan → ccNum → name → lsb value

	var <song, <defs;

	*initClass {
		fineValues = IdentityDictionary.new;
	}

	*new { |song| ^super.new.initRCMidi(song) }

	initRCMidi { |songarg|
		song = songarg;
		defs = IdentityDictionary.new;
	}

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

	// Generic mapping: action.(valFunc.(cc value), raw value). Returns the MIDIdef names.
	control { |name, ccNum, chan, deviceName, valFunc, action, fine = false|
		var srcID = this.class.findSrcId(deviceName);
		var names = [name.asSymbol];
		var lsbName;
		name = name.asSymbol;
		this.free(name);
		if(fine) {
			lsbName = ("rc_" ++ name ++ "_lsb").asSymbol;
			MIDIdef.cc(lsbName, { |val|
				RCMidi.setFineValue(srcID, chan, ccNum, name, val);
			}, ccNum + 32, chan, srcID);
			names = names ++ [lsbName];
		};
		MIDIdef.cc(name, { |val|
			var total = val;
			if(fine) { total = total + ((RCMidi.fineValue(srcID, chan, ccNum, name) ? 0) / 127) };
			RCGuard.call(name, nil) {
				var v = valFunc.value(total);
				RCLog.info(name, "= " ++ v.asString);
				action.value(v, total);
			};
		}, ccNum, chan, srcID);
		defs[name] = names;
		^names
	}

	// synth.set(attr, value) (or synth.set(value) for buses) on each CC message.
	controlSynth { |synth, attr, valFunc, ccNum, chan, deviceName, name, fine = false, setNothing = false|
		name = name ?? { ("ctl_" ++ (attr ? "value") ++ "_" ++ ccNum ++ "_" ++ chan ++ "_" ++ deviceName).asSymbol };
		^this.control(name, ccNum, chan, deviceName, valFunc, { |v|
			if(setNothing.not) {
				if(attr.notNil) { synth.set(attr.asSymbol, v) } { synth.set(v) };
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
		defs[name.asSymbol] !? { |names|
			names.do { |n| MIDIdef.all[n] !? (_.free) };   // MIDIdef(n) would re-create an empty def
			defs.removeAt(name.asSymbol);
		};
	}

	freeAll {
		defs.keys.copy.do { |name| this.free(name) };
	}

	printOn { |stream| stream << "RCMidi(" << song.name << ", " << defs.size << " mappings)" }
}
