PatternH {
	classvar <>hasInitMIDIClient = false;  //ToDo: move to GrobalParams

	// static dictionary of references to each PatternH by pattern key? With added suffix if exists. classvar here or in GlobalParams/Global...

	var <server;
	var <group;

	var <pattern;
	var <patternKey;
	var <patternMode;
	var <patternPlayer;
	var <patternProxy;
	var <patternEnvir;  // TODO see PLbindef

	// Reserved key: 'patternH'
	var <>useEnvirs = false;
	var <>sharedEnvir;
	var <>independentEnvir;
	var <>keyArray;
	var <independentEventsList;

	var <seed;
	var <fadeTime;
	var <>maxNull=128;

	var <>sendToRecorder=false;
	var <recorder;
	var <>linkedRecorders;  // collection of RecorderModule or PatternH

	var <>sendMIDI=false;
	var <>midiOut;
	var <midiChan;  // static for now (PL could be used)
	var <>additionalKeyValueArrayMIDI;
	var <>patternTypesArray;

	var <>sendOSC=false;
	var <>oscDestination;
	var <>oscId;
	var <>oscKeysArray;

	var <fadeValSharedRoutine;

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

	initEnvir { |sharedEnvirarg, independentEnvirarg, keyArray|
		(sharedEnvirarg.isNil && independentEnvirarg.isNil).if {useEnvirs = false} {useEnvirs = true};
		useEnvirs.if {
			sharedEnvir = sharedEnvirarg ? ();
			independentEnvir = independentEnvirarg ? ();
			keyArray.isNil.if {
				this.keyArray = sharedEnvir.keys.asArray ++ independentEnvir.keys.asArray;
			}{
				this.keyArray = keyArray;
			};
		};
		this.pattern_(pattern);
		^this;
	}

	initRecorder { |folderPath, fileName, numChannels=1, monitoringBus, recSampleFormat="int24"|
		monitoringBus ?? {monitoringBus = Bus(rate: 'audio', index: 0, numChannels: if(numChannels==1) {2} {numChannels}, server:server)};
		recorder = RecorderModule(server: server, folderPath: folderPath, fileName: fileName ?? patternKey, nodeRecording: group, numChannels: numChannels, monitoringBus: monitoringBus, recSampleFormat: recSampleFormat);
		sendToRecorder = true;
		this.pattern_(pattern);
		^this;
	}

	initMIDI { |midiOut, chan, additionalKeyValueArray, patternTypesArray, deviceName, portName|
		if(chan == 0) {Log(GlobalParams.pipingLogName).warning("Reserved bus for start/stop recording")};
		hasInitMIDIClient.not.if {MIDIClient.init; hasInitMIDIClient=true;};
		this.midiOut = midiOut ?? {MIDIOut.newByName(deviceName, portName)};
		midiChan = chan ? midiChan;
		this.additionalKeyValueArrayMIDI = additionalKeyValueArray;
		this.patternTypesArray = patternTypesArray;
		sendMIDI = true;
		this.pattern_(pattern);
		^this;
	}

	initOSC { |destination, keysToTransmit, id|
		sendOSC = true;
		destination !? {oscDestination = destination};
		id !? {oscId = id};
		keysToTransmit !? {oscKeysArray = keysToTransmit};
		this.pattern_(pattern);
		^this;
	}

	pattern_ {|newPattern, fadeTimearg, newSeed, newMaxNull|
		newSeed !? {if (newSeed=='nil') {seed = nil} {seed = newSeed}};
		newMaxNull !? {if (newMaxNull=='nil') {maxNull = nil} {maxNull = newMaxNull}};
		newPattern !? {pattern = this.appendAll(newPattern)};
		fadeTimearg !? {this.fadeTime_(fadeTimearg)};
		patternMode.switch(
			\Pdef, {patternProxy = Pdef(patternKey, pattern);}
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
		useEnvirs.if {
			pat = this.appendParams(pat);
			pat = this.appendEnvir(pat);
		};
		pat = this.appendNilSafe(pat);
		pat = this.appendSeed(pat);
		^pat;
	}

	appendRecord {|pat|
		^Pbindf(pat, \out, recorder.bus);
	}

	appendMIDI {|pat|
		var compositeTypesArray;
		patternTypesArray.isNil.if {
			compositeTypesArray = [\note, \midi];
		}{
			compositeTypesArray = (patternTypesArray ++ [\midi]).asSet.asArray;
		};
		additionalKeyValueArrayMIDI.isNil.if {
			^Pbindf(pat, \type, \composite, \types, compositeTypesArray, \midiout, midiOut, \chan, midiChan);
		}{
			^Pbindf(pat, \type, \composite, \types, compositeTypesArray, \midiout, midiOut, \chan, midiChan, *additionalKeyValueArrayMIDI);
		}
	}

	appendOSC {|pat|
		^Pbindf(pat, \destination, oscDestination, \id, (oscId ?? patternKey), \sendOSC, this.makeOSCTransmitter(oscKeysArray));
	}

	appendParams {|pat|
		if (keyArray.size == 0) {^pat} {
			^pat <> Pbind(*keyArray.collect {|key| [key.asSymbol, PL(key.asSymbol)]}.flatten)
		}
	}

	appendEnvir {|pat|
		var res = PenvirExt(independentEnvir, pat, true, sharedEnvir, insertParent: (patternH: this));
		independentEventsList = res.independentEventsList;
		^res;
	}

	appendNilSafe {|pat|
		maxNull.isNil.if {^pat} {
			^PnNilSafe(pattern:pat, repeats:1, maxNull: maxNull);
		}
	}

	appendSeed {|pat|
		seed.isNil.if {^pat} {
			^Pseed(Pn(seed, 1), pat)
		}
	}

	play {|argClock, protoEvent, quant, doReset=false, startRecording=true|
		argClock ?? {argClock = GlobalParams.linkClock};
		patternMode.switch(
			\Pdef, {patternPlayer = Pdef(patternKey).play(argClock, protoEvent, quant, doReset)}
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

	makeOSCTransmitter {|keys_to_transmit, latency, baseMessage|
		Pfunc({|ev|
			var oscArray = [(baseMessage ?? GlobalParams.oscBaseMessage) ++ (ev.id ?? '') ];

			// Construct the osc array from the Pbind's keys
			ev.keysValuesDo{|k,v|
				// Filter out the 'destination' and 'id' keys
				(k != 'destination' and: {k != 'id'} and: {keys_to_transmit.includes(k)}).if{
					oscArray = oscArray ++ k ++ [v];
				}
			};

			// And send
			//ev.destination.sendMsg(oscArray)
			ev.destination.sendBundle(latency, oscArray)
		});
	}

	linFadeValShared {|key, endVal, dur=10, numSteps=1000|
		(fadeValSharedRoutine.notNil && fadeValSharedRoutine.isPlaying).if{
			fadeValSharedRoutine.stop;
		};
		fadeValSharedRoutine = FadeTools.linFadeValEnvir(sharedEnvir, key, endVal, dur, numSteps);
		^fadeValSharedRoutine;
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
