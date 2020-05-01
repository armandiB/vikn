TemplateModule{
	var <funDict; //To hold Event of functions in order to make a combination SynthDef.
	var <dfltParams; //To hold Event of default parameters for the SynthDef.
	var <synthDefs, <funDefs; //Dictionaries of names -> SynthDefs and names -> funDefs.
	//ToDo: find a better system with synthdefs in a global per-class object

	*new{
		^super.new.resetDefDicts;
	}

	resetDefDicts {
		synthDefs = IdentityDictionary();
		funDefs = IdentityDictionary();
	}

	getFunDflt {|fun|
		^dfltParams.use({fun});
	}

	makeFunDef{|function| //No argument.
		^{SynthDef.wrap(
			function,
			prependArgs: []
		)};
	}

	synthDefTemplate {|funDef| //Writes to one out bus.
		^{|out=0|
			funDef.value;
			Out.ar(out, funDef);
		};
	}

	addSynthDef{|synthdefname, template|
		dfltParams.use({
			var newSynthDef;
			newSynthDef = SynthDef.new(synthdefname, template);
			synthDefs.put(synthdefname, newSynthDef);
			newSynthDef.add;
		});
	}

	getSynthDefName {|funKey|
		^this.class.asString ++ "_" ++ funKey.asString;
	}

	makeSynthDefs {
		funDict.keysValuesDo{|funKey, function|
			var synthdefname = this.getSynthDefName(funKey);
			var funDflt, funDef, template, synthDef;

			funDflt = this.getFunDflt(function);

			funDef = this.makeFunDef(funDflt);
			funDefs.put(synthdefname, funDef);

			template = this.synthDefTemplate(funDef);
			this.addSynthDef(synthdefname, template);

			"Added: ".post; synthdefname.postln;
		};
	}

	//Controls and default values of the enclosing function override the ones with same name.
	makeComposedDef {|thisKey, inModule, inKey|
		var synthdefname = this.getSynthDefName(thisKey) ++ "_o_" ++ inModule.getSynthDefName(inKey);
		var thisDflt, inDflt, funDef, template, synthDef;

		dfltParams.proto = inModule.dfltParams; //ToDo: bad, better with composed object
		thisDflt = this.getFunDflt(funDict.at(thisKey));
		inDflt = inModule.getFunDflt(inModule.funDict.at(inKey));

		funDef = this.makeFunDef(thisDflt, inModule.makeFunDef(inDflt));
		funDefs.put(synthdefname, funDef); //ToDo: composed module object

		template = this.synthDefTemplate(funDef);
		this.addSynthDef(synthdefname, template);

		dfltParams.proto = nil; //ToDo: remove

		"Added: ".post; synthdefname.postln;
	}

	postSynthDefs {
		synthDefs.keysValuesDo{|name, synthDef|
			name.post; ", ".post; synthDef.allControlNames.postln;
		};
	}

	//check that all synthDefs keys are distinct
}