Deck {
	var <target;
	var <buffer;
	var <outbus;
	var <synth;

	var <isPlaying = false;
	var <>pitchMainCoarse = 0;
	var <>pitchMainFine = 0;
	var <>pitchTouchDown = 1;
	var <>pitchTouchUp = 1;

	var <pitchMainFactor = 1;
	var <pitchTouchFactor = 1;

	var <deckNumber;
	var <numChannelsIn;
	var <numChannelsOut;

	*new{|target, buffer, outbus, deckNumber, numChannelsIn, numChannelsOut|
		^super.new().initDeck(target, buffer, outbus, deckNumber, numChannelsIn, numChannelsOut);
	}

	initDeck{|targetarg, bufferarg, outbusarg, deckNumberarg, numChannelsInarg, numChannelsOutarg|
		target = targetarg;
		buffer = bufferarg;
		outbus = outbusarg;
		deckNumber = deckNumberarg;
		numChannelsIn = numChannelsInarg;
		numChannelsOut = numChannelsOutarg;
		this.createSynth();
	}

	createSynth{
		synth !? _.free;
		synth = Synth(("CDJplayer_"++numChannelsIn.asString++"chanIn_"++numChannelsOut.asString++"chanOut").asSymbol, [\bufnum, buffer,
			\out, outbus,
			\bufrate, 0,
			\amp, 1,
			\attack, 5,
			\decay, 5,], target);
	}

	changeNumChannelsIn{|numChannels|
		numChannelsIn = numChannels;
		this.createSynth;
	}

	changeBuffer{|bufferarg|
		buffer = bufferarg;
		//set synth or new synth?
		//called before createSynth for changeNumChannelsIn so ok
	}

	computePitchMainFactor{
		pitchMainFactor = 1 + pitchMainCoarse + pitchMainFine;
		^pitchMainFactor;
	}

	computePitchTouchFactor{
		pitchTouchFactor = pitchTouchUp * pitchTouchDown;
		^pitchTouchFactor;
	}

	returnBufrate{
		^isPlaying.asInteger*pitchMainFactor*pitchTouchFactor;
	}

	setBufrate{
		synth.set(\bufrate, this.returnBufrate());
	}

	play{
		("Play Deck "++ deckNumber.asString ++": ").post; pitchMainFactor.round(0.001).postln;
		isPlaying = true;
		this.setBufrate();
	}
	stop{|print=true|
		if(print){("Stopped Deck "++ deckNumber.asString).postln;};
		isPlaying = false;
		this.setBufrate();
	}
	togglePlayStop{
		^if(isPlaying, {this.stop()}, {this.play()});
	}

	resetStart{
		("Reset Deck "++ deckNumber.asString).postln;
		Routine({
		synth.set(\reset, 1);
		0.05.wait;
		synth.set(\reset, 0);
		}).play;
	}

	getTotalDuration{
		^buffer !? {buffer.duration/pitchMainFactor} ?? 0;
	}

	getNumFrames{
		^buffer !? {buffer.numFrames} ?? 0;
	}

	nodeID{
		^synth.nodeID;
	}

	free{
		synth.free;
	}
}