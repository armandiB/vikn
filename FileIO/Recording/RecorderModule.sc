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
	var <recordingSuffix;

	*new { |server, folderPath, fileName, numChannels=1|
		^super.new.initRecorderModule(server, folderPath, fileName, numChannels);
	}

	initRecorderModule {|serverarg, folderPatharg, fileNamearg, numChannelsarg|
		server = serverarg;
		numChannels = numChannelsarg;
		recordBus = Bus.audio(server, numChannels);
		recorder = Recorder(server);
		recHeaderFormat = server.recHeaderFormat;
		recorder.recHeaderFormat = recHeaderFormat;
		recorder.recSampleFormat = "int24";
		recordingSuffix = -1;
		folderPath = folderPatharg;
		fileName = fileNamearg;
		this.prepareForRecord();
	}

	makeRealFilePath {
		^folderPath +/+ Date.localtime.stamp ++ "_" ++ fileName ++ "_" ++ recordingSuffix ++ "." ++ recHeaderFormat;
	}

	prepareForRecord {
		recordingSuffix = recordingSuffix + 1;
		recorder.prepareForRecord(this.makeRealFilePath(), numChannels);
		^this;
	}

	recHeaderFormat_ {|recH|
		recHeaderFormat = recH;
		recorder.recHeaderFormat = recH;
		^this;
	}

	record { |node, clock, quant, duration, numChan|
		numChan = numChan ? numChannels;
		if (clock.isNil)
		{
			recorder.record(bus: recordBus, numChannels: numChan, node: node, duration: duration);
			^this;
		}
		{
			var routine = Routine({recorder.record(bus: recordBus, numChannels: numChan, node: node, duration: duration); nil;});
			routine.play(clock, quant: quant);
			^this;
		}
	}

	pauseRecording {
		recorder.pauseRecording;
		^this;
	}

	resumeRecording {
		recorder.resumeRecording;
		^this;
	}

	stopRecording {
		recorder.stopRecording;
		this.prepareForRecord();
		^this;
	}

	stopRecordingFinal {
		recorder.stopRecording;
		^this;
	}

	cancelPrepareForRecord {
		recorder.stopRecording;
		^File.delete(this.makeRealFilePath());
	}

	isRecording {
		^recorder.isRecording;
	}

	bus {
		^recordBus;
	}
}

//Extend pattern with function(recorderModule) that returns this with out overrided by [out, recordBus]
// or add outBus attribute to pattern and out overrided by [outBus, recordBus] and scaleBus added to out (volume control)
