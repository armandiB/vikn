CustomSpectrogram : Spectrogram{

	sendSynthDef {
		SynthDef(\spectroscope, {|inbus=0|
			InFeedback.ar(inbus);
		}).send(server);
	}

}