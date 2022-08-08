HarmonicEntity : AbstractEntity{

	var <size;
	var <numChannels; // Supported: 1 or 2
	var <controlMode;
	var <baseUgenName;

	var <outbus;
	///<startWeightFrequencies;
	var <frequencyBus;
	///<startWeights;
	var <weightBus;
	var <panBus;

	var <controlGroup;
	var <mergeGroup;
	var <synthGroup; // Assumes this order in the UGen graph
	var <mergeSynthList;
	var <synth;

	///<addWeightsFunction;

	*new{|server, size=8, numChannels=1, outbus=0, frequencyBus, weightBus, panBus, controlGroup, mergeGroup, synthGroup, controlMode=\FullControl, baseUgenName=\SinOsc|
		^super.new(server).initHarmonicEntity(size, numChannels, controlMode, baseUgenName, controlGroup, mergeGroup, synthGroup, outbus=0, frequencyBus, weightBus, panBus);
	}
	initHarmonicEntity{|sizearg, numChannelsarg, controlModearg, baseUgenNamearg, controlGrouparg, mergeGrouparg, synthGrouparg, outbusarg, frequencyBusarg, weightBusarg, panBusarg|
		size = sizearg;
		numChannels = numChannelsarg;
		controlMode = controlModearg;
		baseUgenName = baseUgenNamearg;
		controlGroup = controlGrouparg;
		mergeGroup = mergeGrouparg;
		synthGroup = synthGrouparg;
		outbus = outbusarg;
		frequencyBus = frequencyBusarg;
		weightBus = weightBusarg;
		panBus = panBusarg;
		mergeSynthList = List();
		AbstractEntitySynthDefSender(server, this);
	}

	makeSynthDefName{
		^("harmonicEntity_" ++ controlMode.asString ++ "_base_" ++ baseUgenName.asString ++ "_size_" ++ size.asString ++ "_" ++ numChannels.asString ++ "chan").asSymbol
	}

	makeSynthDef{
		^SynthDef(this.makeSynthDefName(),
			switch(controlMode)
			{\FullControl} {
				{ |out, freqbus, weightbus, freqscale=1, freqoffset=0, amp=1, panbus|
					var inFreqs = In.ar(freqbus, size);
					var inWeights = In.ar(weightbus, size);
					var snd;

					switch(baseUgenName.asSymbol)
					{\SinOsc} {
						switch(numChannels)
						{1} {
							snd = DynKlang.ar(`[inFreqs, inWeights, nil], freqscale, freqoffset);
						}
						{2} {
							snd = Mix.ar(Pan2.ar(SinOsc.ar((inFreqs*freqscale) + freqoffset, 0, inWeights), In.ar(panbus, size)));
						};
					};

					Out.ar(out, snd*amp);
				}
			}
		);
	}

	createSynth {|amp=1|
		var argArray = [\out, outbus];
		amp !? {argArray = argArray ++  [\amp, amp]};
		frequencyBus !? {argArray = argArray ++  [\freqbus, frequencyBus]};
		weightBus !? {argArray = argArray ++  [\weightbus, weightBus]};
		if((numChannels>1) && panBus.isNil.not) {argArray = argArray ++  [\panbus, panBus]};
		synth !? synth.free;
		synth = Synth(this.makeSynthDefName(), argArray, synthGroup);
	}

	setBuses {
		frequencyBus !? synth.set(\freqbus, frequencyBus);
		weightBus !? synth.set(\weightbus, weightBus);
		if(numChannels>1) {panBus !? panBus.set(\panbus, panBus)};
	}

	addWeights {|harmonicEntity, fadeTime=0.02, startAmount=1|
		var mergeSynth = {\amount.kr(startAmount)*In.ar(harmonicEntity.weightBus, harmonicEntity.size)}.play(mergeGroup, weightBus, fadeTime);

		mergeSynthList.add(mergeSynth);
		^(mergeSynthList.size-1);
	}
	addWeightsAndPan2 {|harmonicEntity, fadeTime=0.02, startAmount=1|
		var mergeSynth = {
			var weightsToAdd = \amount.kr(startAmount)*In.ar(harmonicEntity.weightBus, harmonicEntity.size);
			var currentWeights = In.ar(weightBus, harmonicEntity.size);
			var panToAddAdj = (1 + In.ar(harmonicEntity.panBus, harmonicEntity.size))*pi/4;
			var currentPanAdj = (1 + In.ar(panBus, harmonicEntity.size))*pi/4;
			var newWeights = ( (currentWeights + weightsToAdd).squared + (2*currentWeights*weightsToAdd* ((currentPanAdj-panToAddAdj).cos - 1) ) ).sqrt;
			var newPan = (4/pi*( ((currentPanAdj.sin*currentWeights)+(panToAddAdj.sin*weightsToAdd)) / ((currentPanAdj.cos*currentWeights)+(panToAddAdj.cos*weightsToAdd)) ).atan) - 1;
			ReplaceOut.ar(weightBus, newWeights);
			ReplaceOut.ar(panBus, newPan);
		}.play(mergeGroup, nil, fadeTime);

		mergeSynthList.add(mergeSynth);
		^(mergeSynthList.size-1);
	}

	mergeAdd {|harmonicEntity, fadeTime=0.02, startAmount=1| // Still, phases will generally be different so it's not a perfect sound merge
		switch(numChannels)
		{1} {^this.addWeights(harmonicEntity, fadeTime, startAmount);}
		{2} {^this.addWeightsAndPan2(harmonicEntity, fadeTime, startAmount);}
	}

	freeMerge {|index|
		mergeSynthList[index].free;
	}

	free{
		synth !? synth.free;
		mergeSynthList.do(_.free);
		frequencyBus !? frequencyBus.free;
		weightBus !? weightBus.free;
		panBus !? panBus.free;
	}

}