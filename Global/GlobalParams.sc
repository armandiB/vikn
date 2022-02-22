GlobalParams {
	classvar <refSampleRate = 48000;
	classvar <>seed = 1994;
	classvar <>hasSetSeed = false;
	classvar >linkClock;

	classvar <>mainServer;
	classvar <safeAudioBusNumber;

	classvar <>pipingLogName = \piping;

	classvar <>oscBaseMessage = "/fromSuperCollider/";

	classvar <>midiNote0 = 60;
	classvar <>defaultRefFreq = 440;

	*new{
		mainServer = Server.default;
		safeAudioBusNumber = mainServer.options.numOutputBusChannels + mainServer.options.numInputBusChannels + 12;
		this.setSeedSafe();
	}

	*linkClock{
		^linkClock ?? this.makeLinkClock();
	}

	*makeLinkClock{ |tempo|
		^linkClock ?? {linkClock = LinkClock(tempo).latency_(Server.default.latency); linkClock.permanent = true;};
	}

	*setSeed{|seedarg|
		seedarg !? {seed = seedarg};
		hasSetSeed = true;
		^thisThread.randSeed = seed;
	}

	*setSeedSafe{|seedarg|
		hasSetSeed.if {^nil} {^this.setSeed(seedarg)};
	}
}