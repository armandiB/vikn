# vikn
 My SuperCollider quark with a live and modular philosophy and objects that make life faster
 
### Main classes
- RecorderModule: creates a Bus for recording with monitoring to another bus, organizes multiple takes
- PatternH: holds a Pdef and creates a RecorderModule, can send corresponding MIDI and OSC info, handles parallel recording with other PatternH

- RealTuning, JIRealTuning: an extension of Tuning that gives an actual note -> frequency mapping thanks to a reference note/freq. The reference note/freq can be changed live. JIRealTuning has a just intonation structure as coordinates in the space of prime numbers and finds automatically the good octave for each note

- CVTrigChan, CVDCChan, CVVoctChan: for outputting values continuously from an interface (e.g. to control modular synths). CVVoctChan handles conversion, tuning of oscillators, and works with RealTuning for a note mapping

- CustomSpectrogram: extension of Spectrogram with many nice options for displaying phase with color, increased resolution, etc. And it can receive FFT bins from an arbitrary synth instead of computing the FFT internally, which is very useful for signal processing research
