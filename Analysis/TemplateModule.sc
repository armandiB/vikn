TemplateModule{
	var <funDict; //To hold Event of functions in order to make a combination SynthDef.
	var <dfltParams; //To hold Event of default parameters for the SynthDef.
	var <synthDefs, <funDefs; //Dictionaries of names -> SynthDefs and names -> funDefs.

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

	makeFunDef{|synthdefname, function|
		var funDef;
		funDef = SynthDef.wrap(
			this.getFunDflt(function),
			prependArgs: []
		);
		funDefs.put(synthdefname, funDef);
		^funDef;
	}

	synthDefTemplate {|synthdefname, function| //Writes to one out bus.
		^{|out=0|
			var funDef;
			funDef = this.makeFunDef(synthdefname, function);
			Out.ar(out, funDef);
		};
	}

	addSynthDef{|synthdefname, function|
		^dfltParams.use({
			SynthDef.new(synthdefname,
				this.synthDefTemplate(synthdefname, function)).add
		});
	}

	makeSynthDefs {
		funDict.keysValuesDo{|funKey, function|
			var synthdefname = this.class.asString ++ "_" ++ funKey.asString;
			var synthDef;

			synthDef = this.addSynthDef(synthdefname, function);
			synthDefs.put(synthdefname, synthDef);

			"Added: ".post; synthdefname.postln;
		};
	}

	//compose{|synthDef? using funDefs?|}
}