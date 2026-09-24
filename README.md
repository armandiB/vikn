# vikn
 My SuperCollider quark with a live and modular philosophy, and objects that make life faster
 
### Dependencies
Quarks:
- miscellanous_Lib: PL and PLnaryop in PatternH.sc
- ddwPatterns: PnNilSafe in PatternH.sc (Paccum is used by pieces, not by the library)

### reCurrent (current)
`reCurrent/` is the object-based live pattern library that replaces the
`Building_Blocks/Beats_Block` proto-library of HomewareSC: sessions, songs and
layers, beats with a swing pipeline and live edits, orgnsms cloned into
batches, path control over a spherical design, crawlers, rhythm dictionaries,
fobjects, loop buffers, OSC/MIDI control surfaces. Every class is prefixed
`RC`. Start with `HelpSource/Guides/reCurrent.schelp`.

- `reCurrent/Core`: RCLog (rate-limited logging), RCGuard (guarded calls), RCUtil
- `reCurrent/Session`: RCSession, RCSong, RCLayer, RCSwing
- `reCurrent/Beats`: RCBeat, RCBeatSpec, RCDurList, RCRhythm
- `reCurrent/Control`: RCOsc, RCMidi, RCKeyboardState
- `reCurrent/Orgnsm`: RCOrgnsm, RCOrgnsmRegistry, RCBatch, RCPathControl, RCSpherePath,
  RCCrawler, RCCrawlerMoves, RCNoteAlg, RCOrgnsmPatterns, RCSynthDefs
- `reCurrent/Rhythm`: RCSubseq, RCSubseqLibrary, RCRhythmDict
- `reCurrent/Space`: RCFObject, RCMatrix
- `reCurrent/Resources`: RCLoopBuffer

Tests live in `tests/reCurrent_tests/` (UnitTest, no server needed) and run with
`~/Music/Supercollider/HomewareSC/scripts/test.sh`.

### reAmbi and reCording
Two small modules the songs hold by name (`~song.ambi`, `~song.recorder(\k)`):

- `reAmbi/`: RAOutputChain (an ambisonic output chain built synchronously
  in ordered Groups: signal → transformer → inserted stages → AmbiX / binaural
  decoder), RAStereoMonitor (FOA cardioid stereo feed of the chain). Guide:
  `HelpSource/Guides/reAmbi.schelp`; tests in `tests/reAmbi_tests/`.
- `reCording/`: RERecorder (tracks recorded together under a piece folder),
  REReplay (a take cued into a proxy or a bus), RETake (paths, and the
  record-a-replay session). Guide: `HelpSource/Guides/reCording.schelp`;
  tests in `tests/reCording_tests/`.

### Main classes (older modules)
- FileIO/Recording/RecorderModule: creates a Recorder and a recording bus, with monitoring to another bus, organizes multiple takes

- Patterns/PatternH: holds a Pdef and creates a RecorderModule, can send corresponding MIDI and OSC info, handles parallel recording with other PatternH, sets seed

- Tuning/RealTuning, JIRealTuning: an extension of Tuning that gives an actual note (Z) -> frequency (R+) mapping thanks to a reference note+freq (e.g. 9 = A4 -> 440Hz). This is like tuning a piano in 10ms. The reference note+freq can be changed live. JIRealTuning has a just intonation structure as coordinates in a space of (prime) numbers and finds automatically the good octave for each note

- CVOut/CVTrigChan, CVDCChan, CVVoctChan: for outputting values continuously from an interface (e.g. to control modular synths). CVVoctChan handles value conversion, tuning of oscillators, and works with RealTuning for a note mapping

- Plotting/CustomSpectrogram: extension of Spectrogram with many nice options for displaying phase with color, increased resolution, etc. It can receive FFT bins from an arbitrary synth instead of computing the FFT internally, which is very useful for signal processing research
