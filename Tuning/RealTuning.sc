RealTuning : Tuning {
	var <>reffreq;
	var <>reffreqNote;
	var <>noteFirstElement; //0 is C4

	//var <tuningFreqRatios; //for speed, modify when tuning is set and conversely
	//var <stepsPerOctaveStore; //=octaveRatio.log2 * 12.0  //store for speed

	var <>baseNote;

	*new { | tuning, octaveRatio = 2.0, name = "Unknown Tuning", reffreq=440, reffreqNote=9, noteFirstElement=0|
		^super.new(tuning, octaveRatio, name).initRealTuning(reffreq, reffreqNote, noteFirstElement);
	}

	initRealTuning{ arg reffreqarg, reffreqNotearg, noteFirstElementarg;
		reffreq = reffreqarg;
		reffreqNote = reffreqNotearg;
		noteFirstElement = noteFirstElementarg;
	}

	atNote{|note|
		^this.wrapAt(note - noteFirstElement) + (this.stepsPerOctave*(note - noteFirstElement).div(this.size)); //in midi semitones
	}

	wrapAtNote{|note|
		^this.wrapAt(note - noteFirstElement);
	}

	noteToFreq{|note|
		^this.atNote(note - reffreqNote).midiratio*reffreq;
	}

	// I want to be able to work with any base note (F for Revelation)
	// and define a structure (e.g. JI) with origin this note
	// maybe A will be tuned 440
	// but I want to be able to quickly tune to 12-ET (or other) at another note
}
