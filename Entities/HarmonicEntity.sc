HarmonicEntity : AbstractEntity{

	var <size;
	var <numChannels; // Supported: 1 or 2
	var <controlMode;
	var <baseUgenName;
	var <rateControls;

	var <outbus;
	var <>frequencyBus;
	var <>weightBus;
	var <>panBus;

	var <controlGroup;
	var <mergeGroup;
	var <synthGroup; // Assumes this order in the UGen graph
	var <mergeSynthList;
	var <synth;

	///<addWeightsFunction;

	*new{|server, size=8, numChannels=1, outbus=0, controlGroup, mergeGroup, synthGroup, frequencyBus, weightBus, panBus, controlMode=\FullControl, baseUgenName=\SinOsc, rateControls=\ar|
		^super.new(server).initHarmonicEntity(size, numChannels, controlMode, baseUgenName, controlGroup, mergeGroup, synthGroup, outbus=0, frequencyBus, weightBus, panBus, rateControls);
	}
	initHarmonicEntity{|sizearg, numChannelsarg, controlModearg, baseUgenNamearg, controlGrouparg, mergeGrouparg, synthGrouparg, outbusarg, frequencyBusarg, weightBusarg, panBusarg, rateControlsarg|
		size = sizearg;
		numChannels = numChannelsarg;
		controlMode = controlModearg;
		baseUgenName = baseUgenNamearg;
		controlGroup = controlGrouparg;
		mergeGroup = mergeGrouparg;
		synthGroup = synthGrouparg;
		outbus = outbusarg;
		frequencyBus = frequencyBusarg ? switch(rateControlsarg) {\ar} {Bus.audio(server, sizearg)};
		weightBus = weightBusarg ? switch(rateControlsarg) {\ar} {Bus.audio(server, sizearg)};
		panBus = panBusarg ? if(numChannelsarg>1) {switch(rateControlsarg) {\ar} {Bus.audio(server, sizearg)}};
		rateControls = rateControlsarg;
		mergeSynthList = List();
		this.initSynthDefHarmonicEntity;
	}
	initSynthDefHarmonicEntity{
		^AbstractEntitySynthDefSender(server, this);
	}

	makeMainSynthDefName{
		^("harmonicEntity_" ++ controlMode.asString ++ "_base_" ++ baseUgenName.asString ++ "_size_" ++ size.asString ++ "_" ++ numChannels.asString ++ "chan_"  ++ rateControls.asString).asSymbol
	}
	makeMainSynthDef{
		^SynthDef(this.makeMainSynthDefName,
			switch(rateControls)
			{\ar} {
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
			}
		);
	}

	makeSynthDefs{
		^[this.makeMainSynthDef, this.makeMergeAddSynthDef];
	}

	makeSound{|amp=1|
		var argArray = [\out, outbus];
		amp !? {argArray = argArray ++  [\amp, amp]};
		frequencyBus !? {argArray = argArray ++  [\freqbus, frequencyBus]};
		weightBus !? {argArray = argArray ++  [\weightbus, weightBus]};
		if((numChannels>1) && panBus.isNil.not) {argArray = argArray ++  [\panbus, panBus]};
		synth !? synth.free;
		synth = Synth(this.makeMainSynthDefName, argArray, synthGroup);
	}

	setBuses {
		frequencyBus !? synth.set(\freqbus, frequencyBus);
		weightBus !? synth.set(\weightbus, weightBus);
		if(numChannels>1) {panBus !? panBus.set(\panbus, panBus)};
	}

	makeMergeAddSynthDefName{
		^("harmonicEntity_" ++ controlMode.asString ++ "_mergeAdd_size_" ++ size.asString ++ "_" ++ numChannels.asString ++ "chan_"  ++ rateControls.asString).asSymbol;
	}
	makeMergeAddSynthDef{|fadeTime=0.02, startAmp=1|
		^SynthDef(this.makeMergeAddSynthDefName,
			switch(numChannels)
			{1} {this.addWeightsFunction(fadeTime, startAmp)}
			{2} {this.addWeightsAndPan2Function(fadeTime, startAmp)}
		);
	}
	// Assumes same size between entities
	addWeightsFunction {|fadeTime, startAmp|
		^switch(rateControls)
		{\ar} {{
			var dt = NamedControl.kr(\fadeTime, fadeTime);
			var gate = NamedControl.kr(\gate, 1.0);
			var startVal = (dt <= 0);
			var newWeights = \amp.kr(startAmp)*EnvGen.kr(Env.new([startVal, 1, 0], #[1, 1], \lin, 1), gate, 1.0, 0.0, dt, 2)*In.ar(\extWeightBus.kr, size);
			Out.ar(\weightbus.kr, newWeights);  // Could be ir to optimize
		}};
	}
	addWeightsAndPan2Function {|fadeTime, startAmp|
		^switch(rateControls)
		{\ar} {{
			var dt = NamedControl.kr(\fadeTime, fadeTime);
			var gate = NamedControl.kr(\gate, 1.0);
			var startVal = (dt <= 0);
			var weightsToAdd = \amp.kr(startAmp)*EnvGen.kr(Env.new([startVal, 1, 0], #[1, 1], \lin, 1), gate, 1.0, 0.0, dt, 2)*In.ar(\extWeightBus.kr, size);
			var currentWeights = In.ar(weightBus, size);
			var panToAddAdj = (1 + In.ar(\extPanBus.kr, size))*pi/4;
			var currentPanAdj = (1 + In.ar(panBus, size))*pi/4;
			var newWeights = ( (currentWeights + weightsToAdd).squared + (2*currentWeights*weightsToAdd* ((currentPanAdj-panToAddAdj).cos - 1) ) ).sqrt;
			var newPan = (4/pi*( ((currentPanAdj.sin*currentWeights)+(panToAddAdj.sin*weightsToAdd)) / ((currentPanAdj.cos*currentWeights)+(panToAddAdj.cos*weightsToAdd)) ).atan) - 1;
			ReplaceOut.ar(\weightbus.kr, newWeights);
			ReplaceOut.ar(\panbus.kr, newPan);
		}};
	}

	mergeAdd {|harmonicEntity, fadeTime=0.02, startAmp=1| // Still, phases will generally be different so it's not a perfect sound merge
		var mergeSynth;
		switch(numChannels)
		{1} {mergeSynth = Synth(this.makeMergeAddSynthDefName, [\weightbus, weightBus, \extWeightBus, harmonicEntity.weightBus, \fadeTime, fadeTime, \amp, startAmp], mergeGroup)}
		{2} {mergeSynth = Synth(this.makeMergeAddSynthDefName, [\weightbus, weightBus, \panbus, panBus, \extWeightBus, harmonicEntity.weightBus, \extPanBus, harmonicEntity.panBus, \fadeTime, fadeTime, \amp, startAmp], mergeGroup)};
		mergeSynthList.add(mergeSynth);
		^(mergeSynthList.size-1);
	}

	freeMerge {|index|
		mergeSynthList[index].free;
	}
	freeAllMerge{
		mergeSynthList.do(_.free);
	}

	free{
		synth !? synth.free;
		this.freeAllMerge;
		frequencyBus !? frequencyBus.free;
		weightBus !? weightBus.free;
		panBus !? panBus.free;
	}

}