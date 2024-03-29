FullChannelDeck {
	var <buffer;
	var <deckbus;

	var <deck;
	var <mixerChannelDeck;
	var <deckPositionFunction;

	var <server;
	var <deckNumber;
	var <numChannelsIn = 2;
	classvar <>numChannelsOut = 2;

	var <midiOut;
	var <>scrubDownLatchStatus;
	var <>scrubUpLatchStatus;

	*new{|targetDeck, targetMixerChannel, mainbus, cuebus, deckNumber, server, midiOut, buffer|
		^super.new().initFullChannelDeck(targetDeck, targetMixerChannel, mainbus, cuebus, deckNumber, server, midiOut, buffer);
	}

	initFullChannelDeck{|targetDeck, targetMixerChannel, mainbus, cuebus, deckNumberarg, serverarg, midiOutarg, bufferarg|
		server = serverarg ? Server.default;
		midiOut = midiOutarg;
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
						2.0.wait;
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

	// Could move the below to Deck
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
		("Pitch Deck "++ deckNumber.asString ++": ").post; ((deck.pitchMainFactor - 1)*100).round(0.001).post; "%".postln;
		deck.setBufrate();
	}

	pitchTouchUp{|factor|
		deck.pitchTouchUp = factor;
		this.changeTouchPitch();
	}
	pitchTouchDown{|factor|
		deck.pitchTouchDown = factor;
		this.changeTouchPitch();
	}
	changeTouchPitch{
		deck.computePitchTouchFactor();
		("PitchTouch Deck "++ deckNumber.asString ++": ").post; (deck.pitchTouchFactor*100).round(0.001).post; "%".postln;
		deck.setBufrate();
	}

	makeMIDIFuncName{|prefix|
		^(prefix++"Deck "++deckNumber.asString).asSymbol
	}

	registerMIDIPitchCoarse{|ccnum, chan=0, precision=12.5|
		MIDIdef.cc(this.makeMIDIFuncName("pitchCoarse"), {arg ...args;
			this.pitchCoarse((args[0]-64)/64*precision/100);
		}, ccnum, chan);
	}
	registerMIDIPitchFine{|ccnum, chan=0, precision=1|
		MIDIdef.cc(this.makeMIDIFuncName("pitchFine"), {arg ...args;
			this.pitchFine((args[0]-64)/64*precision/100);
		}, ccnum, chan);
	}

	// Could have 2 bufrate controls in the deck SynthDef, one not lagged, one lagged for touch
	registerMIDIPitchTouchUp{|ccnum, chan=0|
		MIDIdef.polytouch(this.makeMIDIFuncName("pitchTouchUp"), {arg ...args;
			this.pitchTouchUp(1 + (1.1**(args[0]-127)));
		}, ccnum, chan);
		MIDIdef.noteOff(this.makeMIDIFuncName("pitchTouchUpOff"), {arg ...args;
			this.pitchTouchUp(1);
		}, ccnum, chan);
	}
	registerMIDIPitchTouchDown{|ccnum, chan=0|
		MIDIdef.polytouch(this.makeMIDIFuncName("pitchTouchDown"), {arg ...args;
			this.pitchTouchDown(1 - (1.1**(args[0]-127)));
		}, ccnum, chan);
		MIDIdef.noteOff(this.makeMIDIFuncName("pitchTouchDownOff"), {arg ...args;
			this.pitchTouchDown(1);
		}, ccnum, chan);
	}

	registerMIDIScrubUp{|ccnum, chan=0|
		MIDIdef.polytouch(this.makeMIDIFuncName("scrubUp"), {arg ...args;
			this.pitchTouchUp(1.03**args[0]);
		}, ccnum, chan);
		MIDIdef.noteOff(this.makeMIDIFuncName("scrubUpOff"), {arg ...args;
			this.pitchTouchUp(1);
		}, ccnum, chan);
	}
	registerMIDIScrubDown{|ccnum, chan=0|
		MIDIdef.polytouch(this.makeMIDIFuncName("scrubDown"), {arg ...args;
			this.pitchTouchDown(-1*(1.03**args[0]));
		}, ccnum, chan);
		MIDIdef.noteOff(this.makeMIDIFuncName("scrubDownOff"), {arg ...args;
			this.pitchTouchDown(1);
		}, ccnum, chan);
	}
	registerMIDIScrubUpDownLatch{|ccnumUp, chanUp, ccnumDown, chanDown, ccnumValue, chanValue=0|
		scrubDownLatchStatus = false;
		scrubUpLatchStatus = false;
		if(midiOut.isNil.not){
				midiOut.noteOff(chanUp, ccnumUp);
				midiOut.noteOff(chanDown, ccnumDown);
			};

		MIDIdef.noteOn(this.makeMIDIFuncName("scrubDownOn"), {arg ...args;
			scrubDownLatchStatus = true;
			scrubUpLatchStatus = false;
			this.pitchTouchUp(1);
			if(midiOut.isNil.not){
				midiOut.noteOff(chanUp, ccnumUp);
			};
		}, ccnumDown, chanDown);
		MIDIdef.noteOff(this.makeMIDIFuncName("scrubDownOff"), {arg ...args;
			scrubDownLatchStatus = false;
			this.pitchTouchDown(1);
		}, ccnumDown, chanDown);
		MIDIdef.cc(this.makeMIDIFuncName("scrubDownValue"), {arg ...args;
			if(scrubDownLatchStatus){
				this.pitchTouchDown(2 - (1.03**args[0]));
			};
		}, ccnumValue, chanValue);

		MIDIdef.noteOn(this.makeMIDIFuncName("scrubUpOn"), {arg ...args;
			scrubUpLatchStatus = true;
			scrubDownLatchStatus = false;
			this.pitchTouchDown(1);
			if(midiOut.isNil.not){
				midiOut.noteOff(chanDown, ccnumDown);
			};
		}, ccnumUp, chanUp);
		MIDIdef.noteOff(this.makeMIDIFuncName("scrubUpOff"), {arg ...args;
			scrubUpLatchStatus = false;
			this.pitchTouchUp(1);
		}, ccnumUp, chanUp);
		MIDIdef.cc(this.makeMIDIFuncName("scrubUpValue"), {arg ...args;
			if(scrubUpLatchStatus){
				this.pitchTouchUp(1.03**args[0]);
			};
		}, ccnumValue, chanValue);
	}

	preamp{|preamp|
		("Preamp Deck "++ deckNumber.asString ++": ").post; preamp.round(0.01).postln;
		deck.synth.set(\amp, preamp);
	}

	amp{|amp|
		("Amp Deck "++ deckNumber.asString ++": ").post; amp.round(0.01).postln;
		mixerChannelDeck.synth.set(\amp, amp);
	}

	registerMIDIAmp{|ccnum, chan=0|
		MIDIdef.cc(this.makeMIDIFuncName("amp"), {arg ...args;
			this.preamp(args[0]/100);
		}, ccnum, chan);
	}

	registerMIDIPlayStopLatch{|ccnum, chan=0|
		if(midiOut.isNil.not){
				midiOut.noteOff(ccnum, chan);
		};
		deck.isPlaying = false;
		MIDIdef.noteOn(this.makeMIDIFuncName("play"), {arg ...args;
			this.play;
		}, ccnum, chan);
		MIDIdef.noteOff(this.makeMIDIFuncName("stop"), {arg ...args;
			this.stop;
		}, ccnum, chan);
	}
	registerMIDIPlayStop{|ccnum, chan=0|
		MIDIdef.noteOn(this.makeMIDIFuncName("playStop"), {arg ...args;
			deck.togglePlayStop;
		}, ccnum, chan);
	}
	registerMIDIReset{|ccnum, chan=0|
		MIDIdef.noteOn(this.makeMIDIFuncName("reset"), {arg ...args;
			deck.resetStart;
		}, ccnum, chan);
	}

	registerMIDICue{|ccnum, chan=0|
		MIDIdef.noteOn(this.makeMIDIFuncName("cue"), {arg ...args;
			mixerChannelDeck.toggleCue(args[0]/100);
		}, ccnum, chan);
	}
	registerMIDICueLatch{|ccnum, chan, ccnumAmp, chanAmp=0|
		if(midiOut.isNil.not){
				midiOut.noteOff(ccnum, chan);
		};
		mixerChannelDeck.cued = false;
		MIDIdef.noteOn(this.makeMIDIFuncName("cueOn"), {arg ...args;
			mixerChannelDeck.cueOn();
		}, ccnum, chan);
		MIDIdef.noteOff(this.makeMIDIFuncName("cueOff"), {arg ...args;
			mixerChannelDeck.cueOff();
		}, ccnum, chan);
		MIDIdef.cc(this.makeMIDIFuncName("cueAmp"), {arg ...args;
			mixerChannelDeck.cueAmp = args[0]/100;
		}, ccnumAmp, chanAmp);
	}

	registerMIDIEQ{|ccnums, chan=0|
		MIDIdef.cc(this.makeMIDIFuncName("dbEQLow"), {arg ...args;
			var db = (args[0]-100)*40/101; ("Low Deck "++ deckNumber.asString ++": ").post; (db).round(0.1).postln; mixerChannelDeck.synth.set(\lowDb, db)},
		ccnums[0], chan:chan);
		MIDIdef.cc(this.makeMIDIFuncName("dbEQMid1"), {arg ...args;
			var db = args[0]-100; ("Mid1 Deck "++ deckNumber.asString ++": ").post; (db).round(0.1).postln; mixerChannelDeck.synth.set(\mid1Db, db)},
		ccnums[1], chan:chan);
		MIDIdef.cc(this.makeMIDIFuncName("dbEQMid2"), {arg ...args;
			var db = args[0]-100; ("Mid2 Deck "++ deckNumber.asString ++": ").post; (db).round(0.1).postln; mixerChannelDeck.synth.set(\mid2Db, db)},
		ccnums[2], chan:chan);
		MIDIdef.cc(this.makeMIDIFuncName("dbEQHigh"), {arg ...args;
			var db = args[0]-100; ("High Deck "++ deckNumber.asString ++": ").post; (db).round(0.01).postln; mixerChannelDeck.synth.set(\highDb, db)},
		ccnums[3], chan:chan);
	}

	free{
		//free MIDIDefs (add them to a bag)
		deck.free;
		mixerChannelDeck.free;
		buffer.free;
		deckbus.free;
		deckPositionFunction.free;
	}
}

FullChannelDeckSynthDefSender : AbstractSynthDefSender {
	initSynthDef{
		this.sendDef(
			SynthDef(\CDJplayer_2chanIn_1chanOut, { |out, bufnum, start, bufrate = 1, phaseshift=0, amp = 1, attack = 5, decay = 5, reset|
				var pos = (Phasor.ar(reset, bufrate*BufRateScale.kr(bufnum), 0, BufFrames.kr(bufnum)) + (phaseshift*(bufrate*BufRateScale.kr(bufnum))));
				var sig = Mix.ar(BufRd.ar(2, bufnum, phase: pos, interpolation: 4)), eg;
				var lag_amp = Lag.kr(amp, 0.1);
				eg = EnvGen.kr(Env.asr(attack, releaseTime: decay), sig.abs > 0, doneAction: Done.freeSelf);
				//(pos/BufFrames.kr(bufnum)*100).round(0.1).poll(0.2, \percentagePos);
				SendReply.kr(Impulse.kr(0.2), '/posCDJ', [pos]);
				Out.ar(out, sig * lag_amp * eg)
		}));

		this.sendDef(
			SynthDef(\CDJplayer_2chanIn_2chanOut, { |out, bufnum, start, bufrate = 1, phaseshift=0, amp = 1, attack = 5, decay = 5, reset|
				var pos = (Phasor.ar(reset, bufrate*BufRateScale.kr(bufnum), 0, BufFrames.kr(bufnum)) + (phaseshift*(bufrate*BufRateScale.kr(bufnum))));
				var sig = BufRd.ar(2, bufnum, phase: pos, interpolation: 4), eg;
				var lag_amp = Lag.kr(amp, 0.1);
				eg = EnvGen.kr(Env.asr(attack, releaseTime: decay), sig.abs > 0, doneAction: Done.freeSelf);
				//(pos/BufFrames.kr(bufnum)*100).round(0.1).poll(0.2, \percentagePos);
				SendReply.kr(Impulse.kr(0.2), '/posCDJ', [pos]);
				Out.ar(out, sig * lag_amp * eg)
		}));

		this.sendDef(
			SynthDef(\CDJplayer_1chanIn_1chanOut, { |out, bufnum, start, bufrate = 1, phaseshift=0, amp = 1, attack = 5, decay = 5, reset|
				var pos = (Phasor.ar(reset, bufrate*BufRateScale.kr(bufnum), 0, BufFrames.kr(bufnum)) + (phaseshift*(bufrate*BufRateScale.kr(bufnum))));
				var sig = BufRd.ar(1, bufnum, phase: pos, interpolation: 4), eg;
				var lag_amp = Lag.kr(amp, 0.1);
				eg = EnvGen.kr(Env.asr(attack, releaseTime: decay), sig.abs > 0, doneAction: Done.freeSelf);
				//(pos/BufFrames.kr(bufnum)*100).round(0.1).poll(0.2, \percentagePos);
				SendReply.kr(Impulse.kr(0.2), '/posCDJ', [pos]);
				Out.ar(out, sig * lag_amp * eg)
		}));

		this.sendDef(
			SynthDef(\CDJplayer_1chanIn_2chanOut, { |out, bufnum, start, bufrate = 1, phaseshift=0, amp = 1, attack = 5, decay = 5, reset|
				var pos = (Phasor.ar(reset, bufrate*BufRateScale.kr(bufnum), 0, BufFrames.kr(bufnum)) + (phaseshift*(bufrate*BufRateScale.kr(bufnum))));
				var sig = BufRd.ar(1, bufnum, phase: pos, interpolation: 4), eg;
				var lag_amp = Lag.kr(amp, 0.1);
				eg = EnvGen.kr(Env.asr(attack, releaseTime: decay), sig.abs > 0, doneAction: Done.freeSelf);
				//(pos/BufFrames.kr(bufnum)*100).round(0.1).poll(0.2, \percentagePos);
				SendReply.kr(Impulse.kr(0.2), '/posCDJ', [pos]);
				Out.ar(out, (sig * lag_amp * eg) ! 2)
		}));

		this.sendDef(
			SynthDef(\CDJplayer_stretch_2chan, { |out, bufnum, start, stretch = 1, bufrate = 1, phaseshift=0, amp = 1, attack = 5, decay = 5|
				var sig = BufRd.ar(2, bufnum, phase: (Phasor.ar(0, stretch.reciprocal*bufrate*BufRateScale.kr(bufnum), 0, BufFrames.kr(bufnum)) + (phaseshift*(stretch.reciprocal*bufrate*BufRateScale.kr(bufnum)))), interpolation: 4), eg;
				sig = PitchShift.ar(sig, pitchRatio: stretch, pitchDispersion: 0, timeDispersion: 0);
				eg = EnvGen.kr(Env.asr(attack, releaseTime: decay), sig.abs > 0, doneAction: Done.freeSelf);
				Out.ar(out, sig * amp * eg)
		}));

		this.sendDef(
			SynthDef(\MixerChannel_XonishEQ_1chan, { |in, out, amp=1, cueout, cueamp=1, lowDb=0, mid1Db=0, mid2Db=0, highDb=0|
				var sig = In.ar(in, 1);
				sig = BLowShelf.ar(sig, 250, 0.8, Lag2.kr(lowDb, 0.2));
				sig = BPeakEQ.ar(sig, 500, 0.6, Lag2.kr(mid1Db, 0.2));
				sig = BPeakEQ.ar(sig, 2000, 0.7, Lag2.kr(mid2Db, 0.2));
				sig = BHiShelf.ar(sig, 5000, 1, Lag2.kr(highDb, 0.2));
				Out.ar(out, sig * Lag2.kr(amp, 0.2));
				Out.ar(cueout, sig * cueamp);
		}));

		this.sendDef(
			SynthDef(\MixerChannel_XonishEQ_2chan, { |in, out, amp=1, cueout, cueamp=1, lowDb=0, mid1Db=0, mid2Db=0, highDb=0|
				var sig = In.ar(in, 2);
				sig = BLowShelf.ar(sig, 250, 0.8, Lag2.kr(lowDb, 0.2));
				sig = BPeakEQ.ar(sig, 500, 0.6, Lag2.kr(mid1Db, 0.2));
				sig = BPeakEQ.ar(sig, 2000, 0.7, Lag2.kr(mid2Db, 0.2));
				sig = BHiShelf.ar(sig, 5000, 1, Lag2.kr(highDb, 0.2));
				Out.ar(out, sig * Lag2.kr(amp, 0.2));
				Out.ar(cueout, sig * cueamp);
		}));
	}
}


