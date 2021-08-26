# vikn
 My SuperCollider quark with a live and modular philosophy and objects that make life faster
 
### Dependencies
Quarks:
- miscellanous_Lib: PL and PLnaryop in PatternH.sc
- ddwPatterns: PnNilSafe in PatternH.sc
 
### Main classes
- FileIO/Recording/RecorderModule: creates a Recorder and a recording Bus, with monitoring to another bus, organizes multiple takes

- FileIO/Recording/PatternH: holds a Pdef and creates a RecorderModule, can send corresponding MIDI and OSC info, handles parallel recording with other PatternH, sets seed

- Tuning/RealTuning, JIRealTuning: an extension of Tuning that gives an actual note (Z) -> frequency (R+) mapping thanks to a reference note+freq (e.g. 9 = A4 -> 440Hz). This is like tuning a piano in 10ms. The reference note+freq can be changed live. JIRealTuning has a just intonation structure as coordinates in a space of (prime) numbers and finds automatically the good octave for each note

- CVOut/CVTrigChan, CVDCChan, CVVoctChan: for outputting values continuously from an interface (e.g. to control modular synths). CVVoctChan handles value conversion, tuning of oscillators, and works with RealTuning for a note mapping

- Plotting/CustomSpectrogram: extension of Spectrogram with many nice options for displaying phase with color, increased resolution, etc. It can receive FFT bins from an arbitrary synth instead of computing the FFT internally, which is very useful for signal processing research
