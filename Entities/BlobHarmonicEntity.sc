BlobHarmonicEntity : HarmonicEntity{

	<weightFunction; // arguments to be controls e.g. center, width, slope, pan
	<panFunction;

	<computeGroup; // has to be after controlGroup and before mergeGroup;

	//rateParameters ?

	<computeSynth;

	*new{|server, size=8, numChannels=1, outbus=0, weightFunction, panFunction, frequencyBus, weightBus, panBus, controlGroup, computeGroup, mergeGroup, synthGroup, controlMode=\FullControl, baseUgenName=\SinOsc, rateControls=\ar|
		^super.new(server, size, numChannels, controlMode, baseUgenName, controlGroup, mergeGroup, synthGroup, outbus=0, frequencyBus, weightBus, panBus, rateControls).initBlobHarmonicEntity(weightFunction, panFunction, computeGroup);
	}
	initBlobHarmonicEntity{|weightFunctionarg, panFunctionarg, computeGrouparg|
		weightFunction = weightFunctionarg;
		panFunction = panFunctionarg;
		computeGroup = computeGrouparg;
		//send synthDef
	}

	makeComputeSynthDefName{ //has to be unique per instance
	}

	makeComputeSynthDef{
		//wrap
	}

	createComputeSynth{
	}
}