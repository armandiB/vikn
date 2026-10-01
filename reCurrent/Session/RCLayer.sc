// reCurrent — a layer of beats inside a song (BlockBeats' beat_env).
//
// Holds the clock, server, swing and MIDI outputs shared by its beats, the
// live beats by name, and the names of the last historySize beats created.
// Beats register themselves (see RCBeat); addBeat is defined with RCBeat.

RCLayer {
	classvar <>historySize = 64;
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
		swing.layer = this;   // its settings are recorded under this layer
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
	swing_ { |s| swing = s; s !? { s.layer = this } }
	songName { ^song !? (_.name) }

	beat { |name| ^beats[name.asSymbol] }

	// Create, register and play a beat (BlockBeats' make_beat_from_dict).
	// seeds / addFirstSeeds: nil → the song seed for every streamed key,
	// \none → unseeded, a number, an Array (by position) or a Dictionary (by key).
	// Recorded (RETap); library code creates through prAddBeat.
	addBeat { |name, attrDict, chan, midiOut, seeds, addFirst, addFirstSeeds, terminationKey, quant, post = true, logTag|
		if(RETap.active) { RETap.action(this, \addBeat, [name, attrDict, chan, midiOut, seeds, addFirst, addFirstSeeds, terminationKey, quant, post, logTag]) };
		^this.prAddBeat(name, attrDict, chan, midiOut, seeds, addFirst, addFirstSeeds, terminationKey, quant, post, logTag)
	}

	prAddBeat { |name, attrDict, chan, midiOut, seeds, addFirst, addFirstSeeds, terminationKey, quant, post = true, logTag|
		var b;
		if(seeds == \none) { seeds = () };
		if(addFirstSeeds == \none) { addFirstSeeds = [] };
		b = RCBeat(this, name, attrDict, chan, midiOut, seeds, addFirst, addFirstSeeds, terminationKey, logTag);
		this.registerBeat(b);
		b.prPlay(quant);
		if(post) { RCLog.post(\layer, { "started beat %/%/%".format(this.songName, key, name) }) };
		^b
	}

	// A beat with the same name replaces (frees) the previous one.
	registerBeat { |beat|
		var name = beat.name;
		beats[name] !? { |old| if(old !== beat) { old.prFree } };
		beats[name] = beat;
		history.add(name);
		while { history.size > historySize } { history.removeAt(0) };
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
		if(RETap.active) { RETap.action(this, \deleteBeat, [name, post]) };
		b.prFree;
		this.unregisterBeat(b);
		if(post) { RCLog.post(\layer, "deleted beat %/%/%".format(this.songName, key, name)) };
		^true
	}

	killAll { if(RETap.active) { RETap.action(this, \killAll, []) }; this.prKillAll }
	pauseAll { if(RETap.active) { RETap.action(this, \pauseAll, []) }; this.prPauseAll }
	resumeAll { if(RETap.active) { RETap.action(this, \resumeAll, []) }; this.prResumeAll }
	prKillAll { beats.copy.do { |b| b.prFree; this.unregisterBeat(b) } }
	prPauseAll { beats.do(_.prPause) }
	prResumeAll { beats.do(_.prResume) }

	// Last value a beat produced for a key (see RCBeat.lastValue).
	beatValue { |beatName, valueKey, default|
		var b = beats[beatName.asSymbol];
		if(b.isNil) {
			RCLog.warn(\layer, "beat % not found in %/%".format(beatName, this.songName, key));
			^default.value
		};
		^b.lastValue(valueKey, default)
	}

	free { this.prKillAll }

	printOn { |stream|
		stream << "RCLayer(" << this.songName << "/" << key << ", " << beats.size << " beats)"
	}
}
