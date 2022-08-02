MixerChannelDeck {
	var <target;
	var <inbus;
	var <mainbus;
	var <cuebus;

	var <synth;
	var <cued = false;
	var <cueAmp=0;

	var <deckNumber;
	var <numChannels;

	*new{|target, inbus, mainbus, cuebus, deckNumber, numChannels|
		^super.new().initMixerChannelDeck(target, inbus, mainbus, cuebus, deckNumber, numChannels);
	}

	initMixerChannelDeck{|targetarg, inbusarg, mainbusarg, cuebusarg, deckNumberarg, numChannelsarg|
		target = targetarg;
		inbus = inbusarg;
		mainbus = mainbusarg;
		cuebus = cuebusarg;
		deckNumber = deckNumberarg;
		numChannels = numChannelsarg;
		this.createSynth();
	}

	createSynth{
		synth !? _.free;
		synth = Synth(("MixerChannel_XonishEQ_"++numChannels.asString++"chan").asSymbol, [
			\in, inbus,
			\out, mainbus,
			\amp, 0,
			\cueout, cuebus,
			\cueamp, 0,
		], target);
	}

	cueAmp_{|amp|
		this.cueAmp = amp;
		synth.set(\cueamp, amp);
	}
	cueOn{|amp=nil|
		if(amp.isNotNil){
			this.cueAmp = amp;
		};
		synth.set(\cueamp, this.cueAmp);
		"Deck "++ deckNumber.asString ++" cued ".post; this.cueAmp.round(0.001).postln;
		cued = true;
	}
	cueOff{
		synth.set(\cueamp, 0);
		"Deck "++ deckNumber.asString ++" uncued".postln;
		cued = false;
	}
	toggleCue{|amp=nil|
		if(cued){
			this.cueOff();
		}{
			this.cueOn(amp);
		};
	}

	free{
		synth.free;
	}
}