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

	//INIT()
	// set server
	// new Bus.ar(server)
	// new Recorder(server)
	// set recorder.recSampleFormat (int24)
	// set path, numChannels
	// call recorder.prepareForRecord

	//record()
	// call recorder.record

	//pauseRecording()
	//resumeRecording()
	//stopRecording()


}

//Extend pattern with function(recorderModule) that returns this with out overrided by [out, recordBus]
// or add outBus attribute to pattern and out overrided by [outBus, recordBus] and scaleBus added to out (volume control)

thisProcess.platform.recordingsDir

s.queryAllNodes
s.recHeaderFormat
s.recSampleFormat