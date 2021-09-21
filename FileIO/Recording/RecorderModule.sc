RecorderModule {

	//attibutes:
	// path, format etc.
	// busses to record
	// recordingTime (modulable), maxRecordingTime (fixed?)

	var <server;
	var <recordBus;
	var <recorder;
	var <folderPath;
	var <fileName;
	var <numChannels; // default 1
	var <recHeaderFormat;
	var <recSampleFormat;
	var <recordingSuffix;

	var <realFilePath;

	var <nodeRecording;
	var <monitoringBus;
	var <monitoringSynth;


	*new { |server, folderPath, fileName, nodeRecording, numChannels=1, recordBus, monitoringBus=0, recSampleFormat="int24"|
		^super.new.initRecorderModule(server, folderPath, fileName, nodeRecording, numChannels, recordBus, monitoringBus, recSampleFormat);
	}

	initRecorderModule {|serverarg, folderPatharg, fileNamearg, nodeRecordingarg, numChannelsarg, recordBusarg, monitoringBusarg, recSampleFormatarg|
		server = serverarg;
		numChannels = numChannelsarg;
		recordBus = recordBusarg ? Bus.audio(server, numChannels);
		recorder = Recorder(server);
		recHeaderFormat = server.recHeaderFormat;
		recorder.recHeaderFormat = recHeaderFormat;
		recSampleFormat = recSampleFormatarg;
		recorder.recSampleFormat = recSampleFormatarg;
		recordingSuffix = -1;
		folderPath = folderPatharg;
		fileName = fileNamearg;
		nodeRecording = nodeRecordingarg;
		monitoringBus = monitoringBusarg;
		this.prepareForRecord();
	}

	makeRealFilePath {
		realFilePath = folderPath +/+ Date.localtime.stamp ++ "_" ++ fileName ++ "_" ++ recordingSuffix ++ "." ++ recHeaderFormat;
		^realFilePath;
	}

	prepareForRecord { |recSuffix|
		monitoringSynth ?? this.monitor();
		recordingSuffix = recSuffix ? (recordingSuffix + 1);
		recorder.prepareForRecord(this.makeRealFilePath(), numChannels);
		^this;
	}

	monitor{|mBus|
		monitoringSynth !? {_.free};
		mBus !? {|m| monitoringBus = m};
		if(monitoringBus.isNil.not) {
			if (monitoringBus.numChannels > recordBus.numChannels) {
				monitoringSynth = {|amp=0.0| amp*(recordBus.ar ! (monitoringBus.numChannels.div(recordBus.numChannels)))}.play(nodeRecording ? server, monitoringBus, addAction: \addAfter);
			}
			{
				monitoringSynth = {|amp=0.0| amp*recordBus.ar}.play(nodeRecording ? server, monitoringBus, addAction: \addAfter);
			};
		}
		^this;
	}

	setAmpMonitoring {|amp|
		^monitoringSynth.set(\amp, amp);
	}

	stopMonitoring {
		monitoringSynth !? {_.free};
		^this;
	}

	recHeaderFormat_ {|recH|
		recHeaderFormat = recH;
		recorder.recHeaderFormat = recH;
		^this;
	}

	recSampleFormat_ {|recS|
		recSampleFormat = recS;
		recorder.recSampleFormat = recS;
		^this;
	}

	record { |argClock, quant, duration, numChan, node, doLinked, doLinkedRecursive|  // doLinked(Recursive) not used, for compatibility with PatternH
		if (node.isNil.not) {
			nodeRecording = node;
			this.monitor();
		}{
			monitoringSynth ?? this.monitor();
		};
		numChan = numChan ? numChannels;
		if (argClock.isNil)
		{
			recorder.record(bus: recordBus, numChannels: numChan, node: node, duration: duration);
		}
		{
			var routine = Routine({recorder.record(bus: recordBus, numChannels: numChan, node: node, duration: duration); nil});
			routine.play(argClock, quant: quant);
		}
		^this;
	}

	pauseRecording {
		recorder.pauseRecording;
		^this;
	}

	resumeRecording {
		recorder.resumeRecording;
		^this;
	}

	stopRecording {|prepare=true, delay, doLinked, doLinkedRecursive|
		var stopFunc = {
		recorder.stopRecording;
		if (prepare)
		{Routine({0.2.wait; this.prepareForRecord(); nil}).play(AppClock)}
		{this.stopMonitoring()};
		};
		delay.isNil.if {
			stopFunc.value;
		} {
			Routine({delay.wait; stopFunc.value; nil}).play(AppClock);
		}
		^this;
	}

	cancelPrepareForRecord {|doLinked, doLinkedRecursive|
		recorder.stopRecording;
		this.stopMonitoring();
		^File.delete(realFilePath);
	}

	isRecording {
		^recorder.isRecording;
	}

	bus {
		^recordBus;
	}

	free {|doLinked, doLinkedRecursive|
		this.stopMonitoring();
		recordBus.free;
	}
}
