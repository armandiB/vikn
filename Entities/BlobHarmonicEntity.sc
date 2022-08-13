BlobHarmonicEntity : HarmonicEntity{

	<frequencyFunction;
	<weightFunction; // arguments to be controls e.g. center, width, slope, pan
	<panFunction;

	<computeGroup; // has to be after controlGroup and before mergeGroup;

	//rateParameters ?

	<computeSynth;

	*new{|server, size=8, numChannels=1, outbus=0, controlGroup, computeGroup, mergeGroup, synthGroup, frequencyFunction, weightFunction, panFunction, frequencyBus, weightBus, panBus, controlMode=\FullControl, baseUgenName=\SinOsc, rateControls=\ar|
		^super.new(server, size, numChannels, controlMode, baseUgenName, controlGroup, mergeGroup, synthGroup, outbus=0, frequencyBus, weightBus, panBus, rateControls).initBlobHarmonicEntity(computeGroup, frequencyFunction, weightFunction, panFunction);
	}
	initBlobHarmonicEntity{|computeGrouparg, frequencyFunctionarg, weightFunctionarg, panFunctionarg|
		weightFunction = weightFunctionarg;
		frequencyFunction = frequencyFunctionarg;
		panFunction = panFunctionarg;
		computeGroup = computeGrouparg;
	}

	makeSynthDefs{
		^[this.makeMainSynthDef(), this.makeMergeAddSynthDef(), this.makeComputeSynthDef()];
	}

	makeComputeSynthDefName{ //has to be unique per instance
		^("harmonicBlobEntity_" ++ controlMode.asString ++ "_compute_size_" ++ size.asString ++ "_" ++ numChannels.asString ++ "chan_"  ++ rateControls.asString).asSymbol;
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
					Out.ar(\frequencybus.kr, freqs);
					Out.ar(\weightsbus.kr, weights);
					if(numChannelsarg>1) {Out.ar(\panbus.kr, pan)};
				};
			}
		}
		);
	}

	createComputeSynth{
	}
}