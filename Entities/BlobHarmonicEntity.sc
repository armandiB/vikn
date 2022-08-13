BlobHarmonicEntity : HarmonicEntity{

	var <frequencyFunction;
	var <weightFunction; // arguments to be controls e.g. center, width, slope, pan
	var <panFunction;

	var <computeGroup; // has to be after controlGroup and before mergeGroup;

	var <computeSynth;

	var <instanceIndex;
	classvar nextInstanceIndex = 0; // to have a unique SynthDef per instance

	*new{|server, size=8, numChannels=1, outbus=0, controlGroup, computeGroup, mergeGroup, synthGroup, frequencyFunction, weightFunction, panFunction, frequencyBus, weightBus, panBus, controlMode=\FullControl, baseUgenName=\SinOsc, rateControls=\ar|
		^super.new(server, size, numChannels, controlMode, baseUgenName, controlGroup, mergeGroup, synthGroup, outbus=0, frequencyBus, weightBus, panBus, rateControls).initBlobHarmonicEntity(computeGroup, frequencyFunction, weightFunction, panFunction);
	}
	initBlobHarmonicEntity{|computeGrouparg, frequencyFunctionarg, weightFunctionarg, panFunctionarg|
		weightFunction = weightFunctionarg;
		frequencyFunction = frequencyFunctionarg;
		panFunction = panFunctionarg;
		computeGroup = computeGrouparg;
		instanceIndex = nextIndexNew;
		nextIndexNew = nextIndexNew + 1;
	}

	makeSynthDefs{
		^super.makeSynthDefs ++ [this.makeComputeSynthDef()];
	}

	makeComputeSynthDefName{ //has to be unique per instance
		^("harmonicBlobEntity_" ++ instanceIndex.asString ++ "_" ++ controlMode.asString ++ "_compute_size_" ++ size.asString ++ "_" ++ numChannels.asString ++ "chan_"  ++ rateControls.asString).asSymbol;
	}
	makeComputeSynthDef{
		^SynthDef(this.makeComputeSynthDefName(),{
			var freqs, weights, pan;
			switch(controlMode)
			{\FullControl} {
				freqs = SynthDef.wrap(frequencyFunction);
				weights = SynthDef.wrap(weightFunction);
				if(numChannelsarg>1) {pan = SynthDef.wrap(panFunction)};
				switch(rateControls)
				{\ar} {
					Out.ar(\freq.kr, freqs);
					Out.ar(\weightbus.kr, weights);
					if(numChannelsarg>1) {Out.ar(\panbus.kr, pan)};
				};
			}
		}
		);
	}

	startCompute{
		var argArray = [];
		switch(controlMode)
		{\FullControl} {
			argArray = argArray ++  [\freqbus, frequencyBus];
			argArray = argArray ++  [\weightbus, weightBus];
			if(numChannelsarg>1) {argArray = argArray ++  [\panbus, panBus]};
		};
		computeSynth !? computeSynth.free;
		computeSynth = Synth(this.makeComputeSynthDef(), argArray, computeGroup);
	}

	free{
		super.free;
		computeSynth.free;
	}
}