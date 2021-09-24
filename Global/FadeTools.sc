FadeTools {
	*linFadeValEnvir {|envir, key, endVal, dur=10, numSteps=1000|
		^Routine({
			var curVal;
			curVal = envir.at(key).copy;
			for(1, numSteps, {|i|
				envir.put(key, curVal + (i/numSteps*(endVal - curVal)));
				(dur/numSteps).wait;
			});
			("Faded " + key.asString + " to " + endVal.asString).postln;
		}).play(AppClock);
	}

	*linFadeValSynth {|synth, key, endVal, dur=10, numSteps=1000|
		^Routine({
			var curVal, fadeDur, waitDur=0.1;
			synth.get(key.asSymbol, {|val| curVal = val});
			waitDur.wait;
			if(dur < (2*waitDur)){dur = 2*waitDur};
			fadeDur = dur - waitDur;
			for(1, numSteps, {|i|
				synth.set(key.asSymbol, curVal + (i/numSteps*(endVal - curVal)));
				(fadeDur/numSteps).wait;
			});
			("Faded " + key.asString + " to " + endVal.asString).postln;
		}).play(AppClock);
	}
}