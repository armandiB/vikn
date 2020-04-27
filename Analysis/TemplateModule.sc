TemplateModule{
	var <funDict; //To hold Event of functions in order to make a combination SynthDef.
	var <dfltParams; //To hold Event of default parameters for the SynthDef.

	*new{
		^super.new;
	}

	getFunDflt {|fun|
		^dfltParams.use({fun});
	}
}