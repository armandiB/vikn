PatternH {
	classvar <>hasInitMIDIClient = false;

	// static dictionary of references to each PatternH by pattern key? With added suffix if exists. classvar here or in GlobalParams/Global...

	var <server;
	var <group;

	var <pattern;
	var <patternKey;
	var <patternMode;
	var <patternProxy;  // unused?
	var <patternEnvir;  // see PLbindef

	var <paramEnvir;  // for PL use (maybe same as patternEnvir?)

	var <seed;
	var <fadeTime;

	var <>sendToRecorder=false;
	var <recorder;
	var <>linkedRecorders;  // collection of RecorderModule or PatternH

	var <>sendMIDI=false;
	var <midiOut;
	var <midiChan;  // static for now (PL could be used)
	var <>additionalKeyValueArrayMIDI;

	var <>sendOSC=false;

	*new { |server, patternKey, patternMode=\Pdef, group|
		^super.new.initPatternH(server, patternKey, patternMode, group);
	}

	initPatternH {|serverarg, patternKeyarg, patternModearg, grouparg|
		server = serverarg;
		seed = GlobalParams.seed;
		patternKey = patternKeyarg;
		patternMode = patternModearg;
		linkedRecorders = OrderedIdentitySet();
		this.initGroup(grouparg);
		this.fadeTime_(1);
	}

	initGroup {|grouparg|
		grouparg.isNil.if {
			group = Group.new(server);  // could have other addAction than 'addToHead'
		} {
			group = grouparg;
		};
		^this;
	}

	initRecorder { |folderPath, fileName, numChannels=1, monitoringBus, recSampleFormat="int24"|
		monitoringBus ?? {monitoringBus = Bus(rate: 'audio', index: 0, numChannels: if(numChannels==1) {2} {numChannels}, server:server)};
		recorder = RecorderModule(server, folderPath, fileName, group, numChannels, monitoringBus, recSampleFormat);
		sendToRecorder = true;
		this.pattern_(pattern);
		^this;
	}

	initMIDI { |portName, chan, additionalKeyValueArray, deviceName="IAC Driver"|
		if(chan == 0) {Log(GlobalParams.pipingLogName).warning("Reserved bus for start/stop recording")};
		portName.isInteger.if {portName = "Bus " ++ portName.asString;};
		hasInitMIDIClient.not.if {MIDIClient.init};
		midiOut = MIDIOut.newByName(deviceName, portName);
		midiChan = chan ? midiChan;
		additionalKeyValueArrayMIDI = additionalKeyValueArray;
		sendMIDI = true;
		this.pattern_(pattern);
		^this;
	}

	initOSC {
		sendOSC = true;
		this.pattern_(pattern);
		^this;
	}

	pattern_ {|newPattern, fadeTimearg, newSeed|
		newSeed !? {seed = newSeed};
		newPattern !? {pattern = this.appendAll(newPattern)};
		fadeTimearg !? {this.fadeTime_(fadeTimearg)};
		patternMode.switch(
			\Pdef, {patternProxy = Pdef(patternKey, Pseed(seed, pattern));}
		);
		^this;
	}

	fadeTime_ {|time|
		fadeTime = time;
		patternMode.switch(
			\Pdef, {Pdef(patternKey).fadeTime = time;}
		);
	}

	appendAll {|pat|
		sendToRecorder.if {pat = this.appendRecord(pat)};
		sendMIDI.if {pat = this.appendMIDI(pat)};
		sendOSC.if {pat = this.appendOSC(pat)};
		^pat;
	}

	appendRecord {|pat|
		^Pbindf(pat, \out, recorder.bus);
	}

	appendMIDI {|pat|
		additionalKeyValueArrayMIDI.isNil.if {
			^Pbindf(pat, \type, \composite, \types, [\note, \midi], \midiout, midiOut, \chan, midiChan);
		}{
			^Pbindf(pat, \type, \composite, \types, [\note, \midi], \midiout, midiOut, \chan, midiChan, *additionalKeyValueArrayMIDI);
		}
	}

	appendOSC {|pat|  // TODO
		^pat;
	}

	play {|argClock, protoEvent, quant, doReset=false, startRecording=true|
		argClock ?? {argClock = GlobalParams.linkClock};
		patternMode.switch(
			\Pdef, {Pdef(patternKey).play(argClock, protoEvent, quant, doReset)}
		);
		startRecording.if {this.record(argClock, quant)};
		^this;
	}

	stop {|prepare=true, delayRecording=5, stopRecording=true|  // could clock it like RecorderModule.record()
		patternMode.switch(
			\Pdef, {Pdef(patternKey).stop}
		);
		stopRecording.if {this.stopRecording(prepare, delayRecording)};
	}

	clear {|fadeTime=5|
		this.fadeTime_(fadeTime);
		patternMode.switch(
			\Pdef, {Pdef(patternKey).clear}
		);
		^this;
	}

	addLinkedRecorder {|recOrList|
		recOrList.isKindOf(Collection).if {
			recOrList.do {|rec| linkedRecorders.add(rec)};
		} {
			linkedRecorders.add(recOrList);
		};
		^this;
	}

	prepareForRecord { |recSuffix|
		recorder !? {recorder.prepareForRecord(recSuffix)};
		^this;
	}

	record {|argClock, quant, duration, numChan, node, doLinked=true, doLinkedRecursive=false|  // for now not recursive by default, in case there's a cycle
		argClock ?? {argClock = GlobalParams.linkClock};
		recorder !? {sendToRecorder.and {recorder.isRecording.not}.if{recorder.record(argClock, quant, duration, numChan, node)}};
		doLinked.if {
			doLinkedRecursive.if {
				linkedRecorders.do {|rec| rec.record(argClock, quant, duration, numChan, node, doLinked: true, doLinkedRecursive: true)};
			}{
				linkedRecorders.do {|rec| rec.record(argClock, quant, duration, numChan, node, doLinked: false)};
			}
		};
		^this;
	}

	stopRecording {|prepare=true, delay, doLinked=true, doLinkedRecursive=false|
		recorder !? {recorder.stopRecording(prepare, delay)};
		doLinked.if {
			doLinkedRecursive.if {
				linkedRecorders.do {|rec| rec.stopRecording(prepare, delay, doLinked: true, doLinkedRecursive: true)};
			}{
				linkedRecorders.do {|rec| rec.stopRecording(prepare, delay, doLinked: false)};
			}
		};
		^this;
	}

	cancelPrepareForRecord { |doLinked=true, doLinkedRecursive=false|
		var successList = recorder.isNil.if {[]} {[recorder.cancelPrepareForRecord]};
		doLinked.if {
			doLinkedRecursive.if {
				linkedRecorders.do {|rec| successList.add(rec.cancelPrepareForRecord(doLinked: true, doLinkedRecursive: true))};
			}{
				linkedRecorders.do {|rec| successList.add(rec.cancelPrepareForRecord(doLinked: false))};
			}
		};
		^successList;
	}

	//need MIDI function that sends start and stop recording note on reserved bus (Bus 1)

	//blend

	//more custom blend ?
	// eg presets for certain keys

	free { |doLinked=false, doLinkedRecursive=false|
		this.stop(false, nil);
		recorder.free;
		doLinked.if {
			doLinkedRecursive.if {
				linkedRecorders.do {|rec| rec.free(doLinked: true, doLinkedRecursive: true)};
			}{
				linkedRecorders.do {|rec| rec.free(doLinked: false)};
			}
		};
		group.freeAll;
		midiOut.disconnect;
		^this;
	}
}

/*
~patternLoop_group = Group.new(s);

~stretchedPatternLoop_midiOut = MIDIOut.newByName("IAC Driver","Bus 1");

~stretchedPatternCutting_recorder = RecorderModule(s, "/Volumes/GLYPHAB/Musical_Code/SuperCollider/CurrentSC/Recording/TestPatternsRecord/20210719", "test_stretchedPatternCutting", ~patternLoop_group, 2, Bus(server:s));
~cutting_fade = 1.0;  // fully linear fading
(
var rand_stream = (Pbrown(0, b.numFrames, b.numFrames * 0.04 * 1) + Pseries(0, b.numFrames*(pi/3-1)/8)).wrap(0, b.numFrames).asInteger.asStream;
~next_pos_cutting = 0;
Pdef(\stretchedPatternCutting, Pseed(1994, Pbind(
    \instrument, \stretchedFragments_stereo_loop,
    \bufnum, b,
	\cutting_fade, Pn(Plazy {~cutting_fade}),
	\bufrate, (1-Pkey(\cutting_fade)) + (Pkey(\cutting_fade)*Prand([1, 1.5, 2], inf)), // for example this could be different
	\delta, Pseq([0.5, 1/3, 0.5, 2/3]*8, inf),
	\time, (Pkey(\delta)*(1-Pkey(\cutting_fade))) + (Pkey(\cutting_fade)*0.5/Pexprand(1, 4)*Pn(Plazy { GlobalParams.linkClock.beatDur })*2.2),
    \stretch, (1-Pkey(\cutting_fade)) + (Pkey(\cutting_fade)*Pexprand(0.9, 4.0, inf)),
	\amp, (0.35*(1-Pkey(\cutting_fade))) + (Pkey(\cutting_fade)*Pseq([0.35, 0.22, 0.35], inf)),
	\attack, (0.07*(1-Pkey(\cutting_fade))) + (Pkey(\cutting_fade)*Pkey(\time)/Pexprand(1, 3)*8),
	\decay, (0.07*(1-Pkey(\cutting_fade))) + (Pkey(\cutting_fade)* (Pkey(\delta) - Pkey(\time))*Pexprand(0.9, Pkey(\stretch))/4.0/2),
	\start, Pfunc({|ev| var pos_temp = ~next_pos_cutting; pos_temp.postln; ~next_pos_cutting = (((~next_pos_cutting + (ev.delta * GlobalParams.linkClock.beatDur * b.sampleRate * ev.stretch.reciprocal * ev.bufrate))*(1-~cutting_fade)) + (~cutting_fade*rand_stream.next)).round.asInteger; pos_temp}),
	\group, ~patternLoop_group,
	//\out, ~stretchedPatternCutting_recorder.bus,
	//\type, \composite,
	//\types, [\note, \midi],
	//\midiout, ~stretchedPatternLoop_midiOut,
	//\chan, 3,
	//\midinote, 40 + (12*Pkey(\bufrate).log2), //E2=40
	\destination, ~targetAddress,
	\id, 'cut_violin',
	\sendOSC, ~oscTransmitter.([\id, \bufrate, \time]),
))).play(GlobalParams.linkClock, quant: [4, 0.5]);

~stretchedPatternCutting_recorder.record(GlobalParams.linkClock, [4, 0.5]);

(
~fade_val_var = {|key, endVal, dur=10|
Routine({
	var curVal;
	var numSteps = 1000;
		curVal = currentEnvironment.at(key).copy;
	0.5.wait;
	for(1, numSteps, {|i|
			currentEnvironment.put(key, curVal + (i/numSteps*(endVal - curVal)));
		(dur/numSteps).wait;
});}).play;
}
)
~fade_val_var.value(\cutting_fade, 11/5, 10)
Pdef(\stretchedPatternCutting).stop

~stretchedPatternCutting_recorder.stopRecording
