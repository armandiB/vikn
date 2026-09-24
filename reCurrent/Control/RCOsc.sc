// reCurrent — OSC control surface of a song (BlockBeats' make_osc_def_* functors).
//
// Paths are /<song>/<instr>/<names...>, keys <song>_<instr>_<names...>, so a
// Chataigne project built for the proto-library keeps working. A Symbol with
// underscores becomes nested segments (\start_normal → /start/normal), an
// Array gives the segments directly, nil adds nothing.
// Every handler is guarded: an error in one OSC action is posted, never thrown
// into the OSC dispatcher.

RCOsc {
	var <song, <defs;

	*new { |song| ^super.new.initRCOsc(song) }

	initRCOsc { |songarg|
		song = songarg;
		defs = IdentityDictionary.new;
	}

	segments { |instr, names|
		var res = [song.name, instr.asSymbol];
		if(names.notNil) {
			if(names.isKindOf(Collection) and: { names.isKindOf(String).not }) {
				res = res ++ names.collect(_.asSymbol);
			} {
				res = res ++ names.asString.split($_).collect(_.asSymbol);
			};
		};
		^res
	}

	key { |instr, names| ^this.segments(instr, names).join("_").asSymbol }
	path { |instr, names| ^("/" ++ this.segments(instr, names).join("/")).asSymbol }

	// Define (or redefine) an OSCdef. Returns the guarded handler, callable directly.
	def { |instr, names, func, print = true, inEnvir = true|
		var key = this.key(instr, names);
		var path = this.path(instr, names);
		var guarded;
		if(inEnvir) { func = func.inEnvir };
		guarded = RCGuard.wrap(key, nil, func);
		defs[key] !? (_.free);
		// permanent: Cmd-Period must not take the control surface down (free/freeAll do)
		defs[key] = OSCdef(key, guarded, path, song.session.localAddr).permanent_(true);
		if(print) { RCLog.post(\osc, "% at %".format(key, path)) };
		^guarded
	}

	// kill / pause / resume / <names> (start) defs for a prepared beat.
	startDefs { |spec, instr, names, print = true|
		var label;
		instr = (instr ? spec.name).asSymbol;
		label = "/" ++ song.name ++ " " ++ spec.layer.key ++ " " ++ spec.name;
		this.def(instr, \kill, { spec.kill; RCLog.post(\osc, label ++ " killed") }, print);
		this.def(instr, \pause, { spec.beat !? (_.pause); RCLog.post(\osc, label ++ " paused") }, print);
		this.def(instr, \resume, { spec.beat !? (_.resume); RCLog.post(\osc, label ++ " resumed") }, print);
		this.def(instr, names, { spec.start; RCLog.post(\osc, label ++ " created") }, print);
		^spec
	}

	// One OSC path → one attribute change on one beat (make_osc_def_edit).
	// seed: nil → song seed; a Function returning nil → unseeded.
	editDef { |layerKey, beatName, names, key, val, seed, print = true, printModif = true, inEnvir = true|
		^this.def(beatName, names, {
			var layer = song.layer(layerKey);
			var b = layer !? { layer.beat(beatName) };
			if(b.isNil) {
				RCLog.warn(\osc, "edit %: no beat %/% in %".format(names, layerKey, beatName, song.name));
			} {
				b.set(key, val, seed: if(seed.notNil) { seed.value } { song.seed });
				if(printModif) { RCLog.post(\osc, "/% % % \\% % modified".format(song.name, layerKey, beatName, key, names)) };
			};
		}, print, inEnvir)
	}

	killAllDef { |print = true|
		^this.def(\kill, \all, { song.killAllBeats }, print)
	}

	free { |instr, names|
		var key = this.key(instr, names);
		defs[key] !? (_.free);
		defs.removeAt(key);
	}

	freeAll {
		defs.do(_.free);
		defs.clear;
	}

	printOn { |stream| stream << "RCOsc(" << song.name << ", " << defs.size << " defs)" }
}
