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
`~/Music/Supercollider/HomewareSC/scripts/test.sh` (`scripts/test.sh vikn` for
this suite alone). `TestRCPiece*` hold the idioms of HomewareSC's Biomusic clones
(rhythm-dict progressions, batches and crawlers, the spatial rig) as contracts on
the library, with fixtures shaped like the pieces.

### reAmbi and reCording
Two small modules the songs hold by name (`~song.ambi`, `~song.recorder(\k)`):

- `reAmbi/`: RAOutputChain (an ambisonic output chain built synchronously
  in ordered Groups: signal → transformer → inserted stages → AmbiX / binaural
  decoder), RAStereoMonitor (FOA cardioid stereo feed of the chain),
  RAFObject (an ambisonic fobject: a field bus picked up in a beam at a
  centre as wide as asked, a chain of registered effect layers in one
  SynthDef, the result put back at the centre's taps, spread, at a distance;
  flat messages for a rig's own bundles, live or in an NRT score, and online
  methods that send and record), RAFObjectLayers (its layers: wash, freeze,
  shimmer, ring, grains, haze, fog, drone). Guide:
  `HelpSource/Guides/reAmbi.schelp`; tests in `tests/reAmbi_tests/`.
- `reCording/`: RERecorder (tracks recorded together under a piece folder),
  REReplay (a take cued into a proxy or a bus), RETake (paths, and the
  record-a-replay session); the score recorder and its players; the show
  console (REShowConsole: Reaper's clock to the effects page, the takes'
  control tapes chased to it by REShowChase, the Roto-Control's knobs mixed
  in by REShowRoto and REKnobMix, or the chain played from the device's pages
  by REShowFxPages). Guide: `HelpSource/Guides/reCording.schelp`;
  tests in `tests/reCording_tests/`.

### reLation
`reLation/` is transformational music theory as objects (classes `RL...`): harmony described by what
moves one chord to another, in any tuning. The theory, its sources and its studies are in HomewareSC,
`Math/Category_Theory/`. Start with `HelpSource/Guides/reLation.schelp`.

- `reLation/Pitch`: RLPitchSystem, RLEDO (an equal division and its val), RLJust (just intonation as a
  lattice, its commas, its maps to equal divisions), RLTempered (the lattice with some commas tempered out:
  meantone and the other temperaments between the lattice and the divisions, with their tunings),
  RLVoiceLeading (the least motion between voicings)
- `reLation/Chords`: RLChordSpace (a system and its chord types), RLChord
- `reLation/Contexts`: RLChordContext (triads moved by T, I on the left and P, L, R on the right),
  RLTransform, RLMorphism (maps between contexts: temperaments, pushed and lifted walks)
- `reLation/Walks`: RLWalk (a start and a word; its chords, its drift, its voices, its frequencies), RLWalker
  (a chord and its voices taking one step at a time, for walks without an end: the `\freq` of a beat of reCurrent),
  RLLattice (a walk sent to HomewareVisuals' lattice page: the plane of fifths and thirds, the torus of a division),
  RLChordGraph (a graph of chords and a walk on it, with its voices, sent to the chord graph page)
- `reLation/Relations`: RLQuantale (the values of a relation: boolean, costs, strengths), RLRelation (a
  relation between two lists, composed, compared, closed into a distance), RLCategory (what named arrows
  generate, each with a shortest word: monoids of relations, groups of transformations), RLRelationContext
  (objects and named relations: Cube Dance and its analogues in 3k steps, the triads under P, L, R), RLGraph
  (distances, shortest walks, Hamiltonian cycles; edges costing steps or voice leading)
- `reLation/Networks`: RLNetwork (nodes holding objects, arrows labelled with transformations or relations:
  validity, the networks of one structure, local isographies; Klumpenhouwer networks on
  `RLRelationContext.pitchClasses`), RLAutomorphism (a map of a context's objects that its relations go
  through: a complete isography; applied again and again to a cell it makes a sequence)
- `reLation/Groups`: RLExtension (chords as elements of a group once a base chord is chosen: the eight
  extensions of Z12 by Z2, two in a prime division; left and right actions, a movable base chord; other
  (p, q) on request; also any pitch system, any number of types in a cycle, any finite type group),
  RLWreath (Hook's uniform transformations: 288 in 12 steps for two types)
- `reLation/Time`: RLTimeSpans (time-spans moved from both sides, after Lewin: a rhythm is a walk), RLMetre
  (metre as a relation on a cycle of pulses; rhythmic canons)

Chords of any size have a context (tetrads 4:5:6:7 by `RLChordSpace.tetrads`); `RLRelationContext.preserving`
gives Noll's monoid of a chord and the graded belonging of each pitch class to it.

Tests in `tests/reLation_tests/` (UnitTest, no server): the tables of the theory.

### Extensions of SuperCollider's classes
- `SCExtensions/ScoreExt.sc`: `Score:spillDefs(dir)`, SynthDefs of any size in a non-realtime score. A score
  carries its defs inline (`/d_recv`) and sclang encodes each line into a 64 KB buffer: a def over 65480 bytes
  becomes a stub that scsynth fails on. Called before `recordNRT`, it writes the large ones into `dir` and loads
  them with `/d_load`. Tests in `tests/SCExtensions_tests/`.

### Main classes (older modules)
- FileIO/Recording/RecorderModule: creates a Recorder and a recording bus, with monitoring to another bus, organizes multiple takes

- Patterns/PatternH: holds a Pdef and creates a RecorderModule, can send corresponding MIDI and OSC info, handles parallel recording with other PatternH, sets seed

- Tuning/RealTuning, JIRealTuning: an extension of Tuning that gives an actual note (Z) -> frequency (R+) mapping thanks to a reference note+freq (e.g. 9 = A4 -> 440Hz). This is like tuning a piano in 10ms. The reference note+freq can be changed live. JIRealTuning has a just intonation structure as coordinates in a space of (prime) numbers and finds automatically the good octave for each note

- CVOut/CVTrigChan, CVDCChan, CVVoctChan: for outputting values continuously from an interface (e.g. to control modular synths). CVVoctChan handles value conversion, tuning of oscillators, and works with RealTuning for a note mapping

- Plotting/CustomSpectrogram: extension of Spectrogram with many nice options for displaying phase with color, increased resolution, etc. It can receive FFT bins from an arbitrary synth instead of computing the FFT internally, which is very useful for signal processing research
