RealTuning : Tuning {
	var <reffreq;
	var <reffreqNote;
	var <noteFirstElement;  //defines the a reference for notes in this tuning, helps keep a convention with real notes (e.g. 0 is C4)

	//var <tuningFreqRatios;  //todo for speed, modify when tuning is set and conversely, could be useful for JIRealRuning
	var <storeStepsPerOctave;  //=octaveRatio.log2 * 12.0  //store for speed
	var <storeLogReffreq;  //for use in CVVoctChan
	var <storeAtNoteReffreqNote;  //for use i CVVoctChan

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

	atNote{|note|
		^this.wrapAt(note - noteFirstElement) + (storeStepsPerOctave*(note - noteFirstElement).div(this.size)); //in midi semitones from the reference point of the tuning
	}

	wrapAtNote{|note|
		^this.wrapAt(note - noteFirstElement);
	}

	noteToFreq{|note|
		^(this.atNote(note) - storeAtNoteReffreqNote).midiratio*reffreq;
	}

	// freqToNote with interpolation

	// I want to be able to work with any base note (F for Revelation)
	// and define a structure (e.g. JI) with origin this note
	// maybe A will be tuned 440
	// but I want to be able to quickly tune to 12-ET (or other) at another note
}

JIRealTuning : RealTuning {
	//basically a discrete vector space (base_primes, [index -> (coordinates_base)])
	//can fill up a tuning with base and coordinates (todo auto order by freq, or smarter order based on smallest primes?)
	var <basePrimes;
	var <tuningCoordinates;
	var <octaveFactors;

	var <octaveShiftsArray;

	*new { | basePrimes, tuningCoordinates, octaveRatio = 2.0, name = "Unknown Tuning", reffreq=440, reffreqNote=9, noteFirstElement=0, octaveShifts|  //default 9 is A4
		var octaveSemitones = octaveRatio.ratiomidi;
		var non_adjusted_semitones = JIRealTuning.semitonesFromCoordinates(basePrimes, tuningCoordinates);

		var octaveShifts_array = non_adjusted_semitones.collect({|prime, index|
			octaveShifts !? _.atFail(index.asSymbol) ? 0;
		});
		var tuning = non_adjusted_semitones.collect({ |semitones, index|
			semitones % octaveSemitones + (octaveShifts_array[index]*octaveSemitones);
		});
		var octaveFactors = non_adjusted_semitones.collect({ |semitones, index|
			semitones.div(octaveSemitones)*(-1) + octaveShifts_array[index];  //ratio_adjusted = ratio_non_adjusted * (octaveRatio**octave_factor)
		});
		^super.new(tuning, octaveRatio, name, reffreq, reffreqNote, noteFirstElement).initJIRealTuning(basePrimes, tuningCoordinates, octaveFactors, octaveShifts_array);
	}

	*semitonesFromCoordinates{|base_primes, coordinates|
		^coordinates.collect({|coordinate_array|
			var semitone = 1;
			coordinate_array.do({|coordinate, prime_index|
				semitone = semitone*(base_primes[prime_index]**coordinate)
			});
			semitone.ratiomidi;
		});
	}

	initJIRealTuning{|basePrimesarg, tuningCoordinatesarg, octaveFactorsarg, octaveShiftsarg|
		basePrimes = basePrimesarg;
		tuningCoordinates = tuningCoordinatesarg;
		octaveFactors = octaveFactorsarg;
		octaveShiftsArray = octaveShiftsarg;
	}

	//ToDo: shift along an axis

}
