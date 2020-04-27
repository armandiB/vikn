TemplateModule{
	var funDict; //To hold Event of functions in order to make a combination SynthDef.
	var dfltParams; //To hold Event of default parameters for the SynthDef.

	*new{
		^super.new;
	}

	getFun {|funKey|
		^dfltParams.use({funDict.at(funKey)}.valueEnvir);
	}
}