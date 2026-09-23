// reCurrent — a layer of beats inside a song (BlockBeats' beat_env).
//
// Holds the clock, server, swing and MIDI outputs shared by its beats, the
// live beats by name, and the history of every beat name ever created.
// Beats register themselves (see RCBeat); addBeat is defined with RCBeat.

RCLayer {
	var <song, <key, <swing, <>midiOut, <>addMidiOuts, <beats, <history;
	var clock, server;

	*new { |song, key, clock, swing, midiOut, addMidiOuts|
		^super.new.initRCLayer(song, key, clock, swing, midiOut, addMidiOuts)
	}

	initRCLayer { |songarg, keyarg, clockarg, swingarg, midiOutarg, addMidiOutsarg|
		song = songarg;
		key = keyarg.asSymbol;
		clock = clockarg;
		swing = swingarg ?? { RCSwing.new };
		midiOut = midiOutarg;
		addMidiOuts = addMidiOutsarg ? [];
		beats = IdentityDictionary.new;
		history = List.new;
	}

	name { ^key }
	clock { ^clock ?? { song !? (_.clock) } }
	clock_ { |c| clock = c }
	server { ^server ?? { song !? (_.server) } }
	server_ { |s| server = s }
	seed { ^song !? (_.seed) }
	swing_ { |s| swing = s }
	songName { ^song !? (_.name) }

	beat { |name| ^beats[name.asSymbol] }

	// A beat with the same name replaces (frees) the previous one.
	registerBeat { |beat|
		var name = beat.name;
		beats[name] !? { |old| if(old !== beat) { old.free } };
		beats[name] = beat;
		history.add(name);
	}

	unregisterBeat { |beat|
		if(beats[beat.name] === beat) { beats.removeAt(beat.name) };
	}

	deleteBeat { |name, post = true|
		var b = beats[name.asSymbol];
		if(b.isNil) {
			if(post) { RCLog.warn(\layer, "no beat % in %/%".format(name, this.songName, key)) };
			^false
		};
		b.free;
		this.unregisterBeat(b);
		if(post) { RCLog.post(\layer, "deleted beat %/%/%".format(this.songName, key, name)) };
		^true
	}

	killAll { beats.copy.do { |b| b.free; this.unregisterBeat(b) } }
	pauseAll { beats.do(_.pause) }
	resumeAll { beats.do(_.resume) }

	// Last value a beat produced for a key (see RCBeat.lastValue).
	beatValue { |beatName, valueKey, default|
		var b = beats[beatName.asSymbol];
		if(b.isNil) {
			RCLog.warn(\layer, "beat % not found in %/%".format(beatName, this.songName, key));
			^default.value
		};
		^b.lastValue(valueKey, default)
	}

	free { this.killAll }

	printOn { |stream|
		stream << "RCLayer(" << this.songName << "/" << key << ", " << beats.size << " beats)"
	}
}
