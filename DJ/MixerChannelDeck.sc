MixerChannelDeck {
	var <target;
	var <inbus;
	var <mainbus;
	var <cuebus;

	var <synth;

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

	free{
		synth.free;
	}
}