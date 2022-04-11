FullChannelDeck {
	var <buffer;
	var <deckbus;

	var <deck;
	var <mixerChannelDeck;
	var <deckPositionFunction;

	var <server;
	var <deckNumber;
	var <numChannelsIn = 2;
	classvar numChannelsOut = 2;

	*new{|targetDeck, targetMixerChannel, mainbus, cuebus, deckNumber, server, buffer|
		^super.new().initFullChannelDeck(targetDeck, targetMixerChannel, mainbus, cuebus, deckNumber, server, buffer);
	}

	initFullChannelDeck{|targetDeck, targetMixerChannel, mainbus, cuebus, deckNumberarg, serverarg, bufferarg|
		server = serverarg ? Server.default;
		deckNumber = deckNumberarg;
		buffer = bufferarg ? Buffer(server, 1, numChannelsIn);
		deckbus = Bus.audio(server, numChannelsOut);
		deck = Deck(targetDeck, buffer, deckbus, deckNumber, numChannelsIn, numChannelsOut);
		mixerChannelDeck = MixerChannelDeck(targetMixerChannel, deckbus, mainbus, cuebus, deckNumber, numChannelsOut);
		this.registerDeckPositionFunction();
	}

	createBuffer{
		buffer !? _.free;
		buffer = Buffer(server, 1, numChannelsIn);
		deck.changeBuffer(buffer);
	}

	registerDeckPositionFunction{
		deckPositionFunction !? _.free;
		deckPositionFunction = OSCFunc({ |msg|
			if(msg[1]==(deck !? _.nodeID)){
				if(deck.isPlaying){
					var total_dur, played_perc;
					total_dur = deck.getTotalDuration();
					("Deck "++ deckNumber.asString ++": ").post;
					played_perc = msg[3]/deck.getNumFrames();
				(played_perc*total_dur).asTimeString(1).post; "  ".post; (played_perc*100).round(0.1).post;"%  Remain ".post; ((played_perc-1)*total_dur).asTimeString(1).postln;
				};
			};
		}, '/posCDJ', server.addr);
	}

	loadTrack{|pathName|
		var file = SoundFile.openRead(pathName);
		if(
			file.isNil,
			{("Opening "++pathName.asString++" failed").postln; ^nil},
			{
				if(file.numChannels != numChannelsIn,
					{
						this.changeNumChannelsIn(file.numChannels);
						//wait?
					}
				);
				buffer.allocRead(pathName.asString, completionMessage: {
					Routine({
						0.5.wait;
						buffer.updateInfo;
						0.5.wait;
						("Deck "++ deckNumber.asString ++" loaded: ").post; deck.getTotalDuration().asTimeString(1).postln;
					}).play;
				});
				deck.resetStart();
			}
		);
	}

	changeNumChannelsIn{|numChannels|
		numChannelsIn = numChannels;
		this.createBuffer();
		deck.changeNumChannelsIn(numChannels);
		("Deck "++ deckNumber.asString ++" changed in channel count to "++ numChannels.asString).postln;
	}

	play{
		^deck.play;
	}

	stop{
		^deck.stop;
	}

	pitchCoarse{|factor|
		deck.pitchMainCoarse = factor;
		this.changeMainPitch();
	}

	pitchFine{|factor|
		deck.pitchMainFine = factor;
		this.changeMainPitch();
	}

	changeMainPitch{
		deck.computePitchMainFactor();
		deck.setBufrate();
		("Pitch Deck "++ deckNumber.asString ++": ").post; ((deck.pitchMainFactor - 1)*100).round(0.001).post; "%".postln;
	}

	mixerAmp{|amp|
		mixerChannelDeck.synth.set(\amp, amp);
	}

	free{
		deck.free;
		mixerChannelDeck.free;
		buffer.free;
		deckbus.free;
		deckPositionFunction.free;
	}
}