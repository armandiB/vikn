RealTuning : Tuning {
	var <reffreq;  //to be changed, would have to call makeStoreLogRefFreq in every CVOctChan referencing this
	var <reffreqNote;
	var <noteFirstElement;  //defines the a reference for notes in this tuning, helps keep a convention with real notes (e.g. 0 is C4)

	//var <tuningFreqRatios;  //todo for speed, modify when tuning is set and conversely
	var <storeStepsPerOctave;  //=octaveRatio.log2 * 12.0  //store for speed
	var <storeLogReffreq;  //for use is CVVoctChan
	var <storeAtNoteReffreqNote;  //for use is CVVoctChan

	var <>baseNote;

	*new { | tuning, octaveRatio = 2.0, name = "Unknown Tuning", reffreq=440, reffreqNote=9, noteFirstElement=0|  //default 9 is A4
		^super.new(tuning, octaveRatio, name).initRealTuning(reffreq, reffreqNote, noteFirstElement);
	}

	initRealTuning{ arg reffreqarg, reffreqNotearg, noteFirstElementarg;
		this.reffreq_(reffreqarg);
		reffreqNote = reffreqNotearg;
		noteFirstElement = noteFirstElementarg;
		storeStepsPerOctave = this.stepsPerOctave();
		storeAtNoteReffreqNote = this.atNote(reffreqNotearg);
	}

	reffreq_{|freq|
		reffreq = freq;
		storeLogReffreq = freq.log2;
		^reffreq;
	}

	octaveRatio_{|oR|
		octaveRatio = oR;
		storeStepsPerOctave = this.stepsPerOctave();
		storeAtNoteReffreqNote = this.atNote(reffreqNote);
	}

	reffreqNote_{|note|
		reffreqNote = note;
		storeAtNoteReffreqNote = this.atNote(note);
	}

	noteFirstElement_{|note|
		noteFirstElement = note;
		storeAtNoteReffreqNote = this.atNote(reffreqNote);
	}

	retune{|freq, note|
		this.reffreq_(freq);
		this.reffreqNote_(note);
		^this;
	}

	distanceNotes{|note1, note2|
		^this.wrapAt(note1 - note2) + (storeStepsPerOctave*(note1 - note2).div(this.size));
	}

	atNote{|note|
		^this.wrapAt(note - noteFirstElement) + (storeStepsPerOctave*(note - noteFirstElement).div(this.size)); //in midi semitones from the reference point of the tuning
	}

	wrapAtNote{|note|
		^this.wrapAt(note - noteFirstElement);
	}

	noteToFreq{|note|
		^(this.atNote(note) - storeAtNoteReffreqNote).midiratio*reffreq;
	}


	// I want to be able to work with any base note (F for Revelation)
	// and define a structure (e.g. JI) with origin this note
	// maybe A will be tuned 440
	// but I want to be able to quickly tune to 12-ET (or other) at another note
}
