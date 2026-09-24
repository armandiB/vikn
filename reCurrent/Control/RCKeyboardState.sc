// reCurrent — live state of an MPE keyboard (the ~on_dict machine of the pieces).
//
// One Event per channel: (note:, velocity:, bend: -1..1, touch: 0..127,
// control: IdentityDictionary cc → value, time:). Rules that keep it from
// desyncing: a new noteOn on a busy channel replaces the old note (warned),
// a noteOff for a different note still clears the channel (warned), and a
// channel silent for longer than staleTimeout seconds is dropped when read.
//
//   ~kb = ~song.makeKeyboard("Seaboard RISE 2");
//   ~kb.heldChans   // channel states sorted by note
//   ~kb.notes       // sorted note numbers
//   ~kb.reset

RCKeyboardState {
	var <song, <deviceName, <state, <defs, <>staleTimeout, <>verbose = false;

	*new { |song, deviceName, staleTimeout|
		^super.new.initRCKeyboardState(song, deviceName, staleTimeout)
	}

	initRCKeyboardState { |songarg, deviceNamearg, staleTimeoutarg|
		song = songarg;
		deviceName = deviceNamearg;
		staleTimeout = staleTimeoutarg;
		state = IdentityDictionary.new;
		defs = [];
		if(deviceName.notNil) { this.listen };
	}

	// Install the MIDIdefs (noteOn, noteOff, bend, touch, control) for the
	// device: permanent (Cmd-Period keeps them) and guarded (a failing
	// handler is reported, the MIDI dispatcher never sees the error).
	listen {
		var srcID = RCMidi.findSrcId(deviceName);
		var prefix = "rc_kbd_" ++ (song !? (_.name) ? "x") ++ "_";
		var guard = { |func| RCGuard.wrap(\keyboard, nil, func) };
		this.stopListening;
		defs = [
			MIDIdef.noteOn((prefix ++ "noteOn").asSymbol, guard.({ |vel, note, chan| this.noteOn(vel, note, chan) }), nil, nil, srcID),
			MIDIdef.noteOff((prefix ++ "noteOff").asSymbol, guard.({ |vel, note, chan| this.noteOff(vel, note, chan) }), nil, nil, srcID),
			MIDIdef.bend((prefix ++ "bend").asSymbol, guard.({ |val, chan| this.bend(val, chan) }), nil, srcID),
			MIDIdef.touch((prefix ++ "touch").asSymbol, guard.({ |val, chan| this.touch(val, chan) }), nil, srcID),
			MIDIdef.cc((prefix ++ "control").asSymbol, guard.({ |val, num, chan| this.cc(val, num, chan) }), nil, nil, srcID)
		].collect(_.permanent_(true));
	}

	stopListening {
		defs.do(_.free);
		defs = [];
	}

	prChan { |chan|
		^state[chan] ?? {
			var e = (control: IdentityDictionary.new, time: this.now);
			state[chan] = e;
			e
		}
	}

	now { ^Main.elapsedTime }

	noteOn { |vel, note, chan|
		var e = this.prChan(chan);
		if(e[\note].notNil and: { e[\note] != note }) {
			RCLog.warn(\keyboard, "chan % still held note % when note % arrived: replaced".format(chan, e[\note], note));
		};
		e[\note] = note;
		e[\velocity] = vel;
		e[\time] = this.now;
		if(verbose) { RCLog.post(\keyboard, "noteOn chan % note % vel %".format(chan, note, vel)) };
	}

	noteOff { |vel, note, chan|
		var e = state[chan];
		if(e.isNil) {
			RCLog.warn(\keyboard, "noteOff on chan % with no state".format(chan));
			^this
		};
		if(e[\note] != note) {
			RCLog.warn(\keyboard, "noteOff note % does not match held note % on chan %: cleared anyway".format(note, e[\note], chan));
		};
		state.removeAt(chan);
		if(verbose) { RCLog.post(\keyboard, "noteOff chan % note %".format(chan, note)) };
	}

	bend { |val, chan|
		var e = this.prChan(chan);
		e[\bend] = (val / 8192) - 1;
		e[\time] = this.now;
	}

	touch { |val, chan|
		var e = this.prChan(chan);
		e[\touch] = val;
		e[\time] = this.now;
	}

	cc { |val, num, chan|
		var e = this.prChan(chan);
		e[\control][num] = val;
		e[\time] = this.now;
	}

	prDropStale {
		var now;
		if(staleTimeout.isNil) { ^this };
		now = this.now;
		state.keys.copy.do { |chan|
			var e = state[chan];
			if((now - (e[\time] ? now)) > staleTimeout) {
				RCLog.warn(\keyboard, "chan % silent for more than % s: dropped".format(chan, staleTimeout));
				state.removeAt(chan);
			};
		};
	}

	// Channel state while a note is held, else nil (bend or pressure arriving
	// before the noteOn creates the state, but does not make the channel held).
	held { |chan|
		var e;
		this.prDropStale;
		e = state[chan];
		^if(e.notNil and: { e[\note].notNil }) { e } { nil }
	}

	// Channel states that hold a note, sorted by note.
	heldChans {
		this.prDropStale;
		^state.values.select { |e| e[\note].notNil }.sort { |a, b| a[\note] < b[\note] }
	}

	notes { ^this.heldChans.collect { |e| e[\note] } }

	size { ^this.heldChans.size }

	reset { state.clear }

	free {
		this.stopListening;
		state.clear;
	}

	printOn { |stream| stream << "RCKeyboardState(" << deviceName << ", " << state.size << " chans)" }
}
