RecorderModule {

	//attibutes:
	// path, format etc.
	// busses to record
	// recordingTime (modulable), maxRecordingTime (fixed?)

	var <server;
	var <recordBus;
	var <recorder;
	var <path;
	var <numChannels; // default 1

	*new { |server, path, numChannels=1|
		^super.new.initRecorderModule(server, path, numChannels);
	}

	initRecorderModule {|serverarg, patharg, numChannelsarg|
		server = serverarg;
		numChannels = numChannelsarg;
		recordBus = Bus.audio(server, numChannels);
		recorder = Recorder(server);
		recorder.recSampleFormat = "int24";
		path = patharg;
		recorder.prepareForRecord(path, numChannels);
	}

	record { |node, clock, quant, duration, numChan|
		numChan = numChan ? numChannels;
		if (clock.isNil)
		{^recorder.record(bus: recordBus, numChannels: numChan, node: node, duration: duration);}
		{
			var routine = Routine({recorder.record(bus: recordBus, numChannels: numChan, node: node, duration: duration); nil;});
			routine.play(clock, quant: quant);
			^recorder;
		}
	}

	pauseRecording {
		^recorder.pauseRecording;
	}

	resumeRecording {
		^recorder.resumeRecording;
	}

	stopRecording {
		^recorder.stopRecording;
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

//thisProcess.platform.recordingsDir

//s.queryAllNodes
//s.recHeaderFormat
//s.recSampleFormat