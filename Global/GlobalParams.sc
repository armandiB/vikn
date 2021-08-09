GlobalParams {
	classvar <refSampleRate = 48000;
	classvar <>seed = 1994;
	classvar <>hasSetSeed = false;
	classvar <>linkClock;

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