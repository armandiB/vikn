___ global handler again with link clock (and attribute server set at instanciation)
~global_link_clock = LinkClock(2).latency_(Server.default.latency);
~interface_factor = 0.1;
//~global_link_clock.tempo = 1
(
~chan_DC_1 = 1+32;
~chan_gate_1 = 2+32;
~chan_tuning = 3+32;
~chan_DC_2 = 4+32;
~chan_gate_2 = 5+32;
~chan_dur_1 = 6+32;
)

~revelation_tuning = RealTuning([1,63/64,9/8,567/512,81/64,21/16,729/512,3/2,189/128,27/16,7/4,243/128].ratiomidi, name:"Revelation", noteFirstElement:5) //still tuned at 440 A4 //ratiomidi = .log2*12
~revelation_tuning_2 = JIRealTuning([3,7], [[0,0],[2,1],[2,0],[4,1],[4,0],[1,1],[6,0],[1,0],[3,1],[3,0],[0,1],[5,0]], name:"Revelation", noteFirstElement:5)
//TODO: FIX PROBLEM WITH F# < 0
//Specify octave ratios/intervals, or autoAdjust=True and minimize distance with closest elements?

~white_notes = [0,2,4,5,7,9,11] //Make an object that describes better this JI tuning

~cvOutGlobal = CVOutGlobal(s, ~chan_tuning, reffreq: ~revelation_tuning.reffreq)

//new channel for rolling bass
~cvVOctChan2 = CVVoctChan(s, ~chan_DC_2, ~revelation_tuning);
~cvVOctChan2.makeSynth_kr(); //make makeSynthAndTune
~cvVOctChan2.tune_kr_ratio(1/4); //make CVGateChan and link it to tune for gate on
~cvVOctChan2.stopTune;
~cvTrigChan2 = CVTrigChan(s, ~chan_gate_2);
~cvTrigChan2.makeSynth_kr();

~convergence_notes = [-7,-8,-8]-24
~wait_beats_convergence = 16
~pos_convergence_notes = 0
(
~convergence_notes_task = Task({
	var arrays = [[7,8,-8],[5,6,-8],[-7,-6,-9],[3,-8,-9]]-24;
	loop {
		arrays.do({|array, i|
			~convergence_notes = array;
			i.post; " ".post; ~convergence_notes.postln;
			~pos_convergence_notes = i;
			~wait_beats_convergence.wait;
		});
	}
});
)
~convergence_notes_task.play(~global_link_clock, true, [16,0])
~convergence_notes_task.stop

~wait_beats_2 = 6 /4 //7/4 //4/16
(
~rollingbass_task = Task({
	var init_seq = [-5, -5, -6]-24;
	var idx = 0;
	var rand_idx;
	loop {
		init_seq.do({|note, i|
			~cvVOctChan2.setNoteBus(note);
			~cvTrigChan2.playTrig();
			(~wait_beats_2/(i+1)).wait;
		});
		rand_idx = init_seq.size.rand;
		init_seq[rand_idx] = init_seq[rand_idx] + 4.rand2;
		if(~white_notes.includes(init_seq[rand_idx]).not, {init_seq[rand_idx] = ~convergence_notes[rand_idx]});
		init_seq.postln;
	}
});
)

~rollingbass_task.play(~global_link_clock, true, [4,0])
~rollingbass_task.stop

~cvVOctChan1 = CVVoctChan(s, ~chan_DC_1, ~revelation_tuning);
~cvVOctChan1.makeSynth_kr();
~cvVOctChan1.tune_kr();
~cvVOctChan1.stopTune;
~cvTrigChan1 = CVTrigChan(s, ~chan_gate_1);
~cvTrigChan1.makeSynth_kr();
~cvDurChan1 = CVDCChan(s, ~chan_dur_1);
~cvDurChan1.makeSynth_kr();

//~cvTrigChan1.controlbus.setFlex(0); //doesn't seem tight

~wait_beats_1 = 7/3 *4
~shift_notes_1 = 3; //kinda stupid, need better description of JI tuning
(
~lt_task = Task({
	var init_seq = [0, 5, 7, -1]-24;
	var idx = 0;
	var rand_idx, wait_time;
	loop {
		init_seq.do({|note, i|
			~cvVOctChan1.setNoteBus(note);
			note.postln;
			~cvTrigChan1.playTrig();
			wait_time = ~wait_beats_1/((i+0).div(1).rand+1); //in nb of beats, should convert to time from clock
			~cvDurChan1.setDCBus(wait_time**1.3/6);
			wait_time.wait;
		});
		rand_idx = init_seq.size.rand;
		init_seq[rand_idx] = init_seq[rand_idx] + 4.rand2;
		if(~white_notes.includes(init_seq[rand_idx]).not, {init_seq[rand_idx] = ~convergence_notes.choose + ~shift_notes_1});
	}
});
)

~lt_task.play(~global_link_clock, true, [4,0])
~lt_task.stop

//Other method without bus
y = SynthDef(\test_dc__2__kr, {Out.ar(\out.kr, MulAdd(1, \val.ar));}).add
~cv_synth = Synth(\test_dc__2__kr,[\out, ~chan_CV_test, \val, 1], CVOutGlobal.cvOutGroupDict[s])
~cv_synth.set(\val, -1)  //././1-Test setSynchronous: doesn't exist
//Compare the 2 methods: CPU, latency --- OffsetOut ? -> //././2- try square wave 1ms, block size, block size/1.001
~cv_synth.free
y.dumpUGens
~cv_synth.trace


//-----------
(
// this kick drum doesn't sound so good on cheap speakers
// but if your monitors have decent bass, it's electro-licious
SynthDef(\kik, { |basefreq = 50, ratio = 7, sweeptime = 0.05, preamp = 1, amp = 1,
        decay1 = 0.3, decay1L = 0.8, decay2 = 0.15, out|
    var    fcurve = EnvGen.kr(Env([basefreq * ratio, basefreq], [sweeptime], \exp)),
        env = EnvGen.kr(Env([1, decay1L, 0], [decay1, decay2], -4), doneAction: Done.freeSelf),
        sig = SinOsc.ar(fcurve, 0.5pi, preamp).distort * env * amp;
    Out.ar(out, sig ! 2)
}).add;

SynthDef(\kraftySnr, { |amp = 1, freq = 2000, rq = 3, decay = 0.3, pan, out|
    var    sig = PinkNoise.ar(amp),
        env = EnvGen.kr(Env.perc(0.01, decay), doneAction: Done.freeSelf);
    sig = BPF.ar(sig, freq, rq, env);
    Out.ar(out, Pan2.ar(sig, pan))
}).add;

~commonFuncs = (
        // save starting time, to recognize the last bar of a 4-bar cycle
    init: {
        if(~startTime.isNil) { ~startTime = thisThread.clock.beats };
    },
        // convert the rhythm arrays into patterns
    pbindPairs: { |keys|
        var    pairs = Array(keys.size * 2);
        keys.do({ |key|
            if(key.envirGet.notNil) { pairs.add(key).add(Pseq(key.envirGet, 1)) };
        });
        pairs
    },
        // identify rests in the rhythm array
        // (to know where to stick notes in)
    getRestIndices: { |array|
        var    result = Array(array.size);
        array.do({ |item, i|
            if(item == 0) { result.add(i) }
        });
        result
    }
);
)

(
~kikEnvir = (
    parent: ~commonFuncs,
	proto: topEnvironment,
        // rhythm pattern that is constant in each bar
    baseAmp: #[1, 0, 0, 0,  0, 0, 0.7, 0,  0, 1, 0, 0,  0, 0, 0, 0] * 1.0,
    baseDecay: #[0.15, 0, 0, 0,  0, 0, 0.15, 0,  0, 0.15, 0, 0,  0, 0, 0, 0],
    addNotes: {
        var    beat16pos = (thisThread.clock.beats - ~startTime) % 16,
            available = ~getRestIndices.(~baseAmp);
        ~amp = ~baseAmp.copy;
        ~decay2 = ~baseDecay.copy;
		~basefreq = ~revelation_tuning.noteToFreq(~convergence_notes).scramble;
            // if last bar of 4beat cycle, do busier fills
        if(beat16pos.inclusivelyBetween(12, 16)) {
            available.scramble[..rrand(5, 10)].do({ |index|
                    // crescendo
                ~amp[index] = index.linexp(0, 15, 0.2, 0.5);
                ~decay2[index] = 0.15;
            });
        } {
            available.scramble[..rrand(0, 2)].do({ |index|
                ~amp[index] = rrand(0.15, 0.3);
                ~decay2[index] = rrand(0.05, 0.1);
            });
        }
    }
);

~snrEnvir = (
    parent: ~commonFuncs,
    baseAmp: #[0, 0, 0, 0,  1, 0, 0, 0,  0, 0, 0, 0,  1, 0, 0, 0] * 1.5,
    baseDecay: #[0, 0, 0, 0,  0.7, 0, 0, 0,  0, 0, 0, 0,  0.4, 0, 0, 0],
    addNotes: {
        var    beat16pos = (thisThread.clock.beats - ~startTime) % 16,
            available = ~getRestIndices.(~baseAmp),
            choice;
        ~amp = ~baseAmp.copy;
        ~decay = ~baseDecay.copy;
        if(beat16pos.inclusivelyBetween(12, 16)) {
            available.scramble[..rrand(5, 9)].do({ |index|
                ~amp[index] = index.linexp(0, 15, 0.5, 1.8);
                ~decay[index] = rrand(0.2, 0.4);
            });
        } {
            available.scramble[..rrand(1, 3)].do({ |index|
                ~amp[index] = rrand(0.15, 0.3);
                ~decay[index] = rrand(0.2, 0.4);
            });
        }
    }
);

~hhEnvir = (
    parent: ~commonFuncs,
    baseAmp: 15 ! 16,
    baseDelta: 0.25 ! 16,
    addNotes: {
        var    beat16pos = (thisThread.clock.beats - ~startTime) % 16,
            available = (0..15),
            toAdd;
            // if last bar of 4beat cycle, do busier fills
        ~amp = ~baseAmp.copy;
        ~dur = ~baseDelta.copy;
        if(beat16pos.inclusivelyBetween(12, 16)) {
            toAdd = available.scramble[..rrand(2, 5)]
        } {
            toAdd = available.scramble[..rrand(0, 1)]
        };
        toAdd.do({ |index|
            ~amp[index] = ~doubleTimeAmps;
            ~dur[index] = ~doubleTimeDurs;
        });
    },
    doubleTimeAmps: Pseq(#[15, 10], 1),
    doubleTimeDurs: Pn(0.125, 2)
);


~kik = Penvir(~kikEnvir, Pn(Plazy({
    ~init.value;
    ~addNotes.value;
    Pbindf(
        Pbind(
            \instrument, \kik,
            \preamp, 0.4,
            \dur, 0.25,
            *(~pbindPairs.value(#[amp, decay2, basefreq]))
        ),
            // default Event checks \freq --
            // if a symbol like \rest or even just \,
            // the event is a rest and no synth will be played
        \freq, Pif(Pkey(\amp) > 0, 1, \rest)
    )
}), inf)).play(~global_link_clock, quant: 4);

~snr = Penvir(~snrEnvir, Pn(Plazy({
    ~init.value;
    ~addNotes.value;
    Pbindf(
        Pbind(
            \instrument, \kraftySnr,
            \dur, 0.25,
            *(~pbindPairs.value(#[amp, decay]))
        ),
        \freq, Pif(Pkey(\amp) > 0, 5000, \rest)
    )
}), inf)).play(~global_link_clock, quant: 4);

~hh = Penvir(~hhEnvir, Pn(Plazy({
    ~init.value;
    ~addNotes.value;
    Pbindf(
        Pbind(
            \instrument, \kraftySnr,
            \rq, 0.06,
            \amp, 15,
            \decay, 0.04,
            *(~pbindPairs.value(#[amp, dur]))
        ),
        \freq, Pif(Pkey(\amp) > 0, 12000, \rest)
    )
}), inf)).play(~global_link_clock, quant: 4);
)

// stop just before barline
(~global_link_clock.schedAbs(~global_link_clock.nextTimeOnGrid(4, -0.001), {
    [~kik, ~snr, ~hh].do(_.stop);
});)

~kik.mute
~kikEnvir.baseAmp = ~kikEnvir.baseAmp * 1.06
~kikEnvir.baseAmp[13] = 1
~snr.mute
~hh.mute


//------------ Help files
// Saw freq is controlled by mouse X position
// BBandPass filter is controlled by
// FormantTable data (frequencies, resonances, amplitudes)
(
    SynthDef(\formantVoice, { arg
        f = #[ 400, 750, 2400, 2600, 2900 ],
        a = #[ 1, 0.28183829312645, 0.089125093813375, 0.1, 0.01 ],
        q = #[ 0.1, 0.10666666666667, 0.041666666666667,
            0.046153846153846, 0.041379310344828 ];
	var scale = [0, 2, 4, 7, 9] ++ (12+[0, 2, 4, 6, 9]) ++ (24+[0, 2, 5, 7, 9]) ; // pentatonic major
    var scaleBuf = LocalBuf(scale.size, 1).set(scale);
    var degree = Index.kr(scaleBuf, MouseX.kr(0, BufFrames.kr(scaleBuf)));
	var vibrato = SinOsc.kr(MouseY.kr(0,9), mul:4);
    var base = 4 * 12;
    var in = Saw.ar(((degree + base).midicps + vibrato).lag(0.3));
    Out.ar(0, Mix.new(BBandPass.ar(in, f, q) * a).dup);
}).send(s);
)

// create a menu of the FormantTable options for preview
(
    var voices = FormantTable.keys.asArray;
    voices.sort;

    // start an instance of the synth
    #f, a, q = FormantTable.get( voices.first );
    x = Synth(\formantVoice, [\f, f, \a, a, \q, q]);
    w = Window("FormantTable Browser",
        Rect(Window.screenBounds.width/2 - 150,
        Window.screenBounds.height/2 - 30,
        300, 60));
    StaticText(w, Rect(0, 5, w.view.bounds.width, 20))
        .font_(Font("Helvetica", 11))
        .string_("Move mouse left/right to change pitch")
        .align_(\center);
    PopUpMenu(w, w.view.bounds.insetBy(15, 20).moveBy(0, 10))
        .items_(voices)
        .action_({ |v|
            // change the data based on user action
            #f, a, q = FormantTable.get( v.item );
            x.setn(\f, f);
            x.setn(\a, a);
            x.setn(\q, q);
        });
    w.onClose_({ x.free });
    w.front;
)

// TDEF !!!

	Tdef(\x).play; // create an empty Tdef and play it.

Tdef(\x, { loop({ "ggggggggggggggggg9999ggg999ggg999gg".scramble.postln; 0.5.wait; }) });


Tdef(\x, { loop({ "---------////----------------------".scramble.postln; 0.25.wait; }) });
Tdef(\x, { loop({ thisThread.seconds.postln; 1.wait; }) });
Tdef(\x, { loop({ thisThread.seconds.postln; 1.01.wait; }) });

TempoClock.default.tempo = 2;

Tdef(\x, { "the end".postln });
Tdef(\x, { "one more".postln });
Tdef(\x, { 10.do({ "ten more".scramble.postln; 0.25.wait; }) });
Tdef(\x, { loop({ "lots more".scramble.postln; 0.25.wait; }) });

TempoClock.default.tempo = 1;

Tdef(\x).stop;
Tdef(\x).play;

Tdef(\x).clear;

	// sound example


// load a synthdef
//s.boot;
SynthDef(\pdef_grainlet,{ arg out=0, freq=440, sustain=0.05;var env;env = EnvGen.kr(Env.perc(0.01, sustain, 0.3), doneAction: Done.freeSelf);Out.ar(out, SinOsc.ar(freq, 0, env))}).add;
Tdef(\x).play;

(
Tdef(\x, {loop({s.sendMsg("/s_new", "pdef_grainlet", -1,0,0, \freq, rrand(600, 640));0.1.wait;})})
)

(
Tdef(\x, {var x; x = Pseries(300, 20, 100).loop.asStream;loop({s.sendMsg("/s_new", "pdef_grainlet", -1,0,0, \freq, x.next);0.05.wait;})})
)
	TdefAllGui()
(
Tdef(\x, {var x;x = Plazy({ Pseries(300 + 300.rand, 10 + 30.rand, 10 + 30.rand) }).loop.asStream;loop({s.sendMsg("/s_new", "pdef_grainlet", -1,0,0, \freq, x.next);0.05.wait;})})
)

// metronome
Tdef(\y, { loop({ s.sendMsg("/s_new", "pdef_grainlet", -1,0,0, \freq, 1500); 1.wait; }) }).play;

// play ending stream once
(
Tdef(\x, {var x, dt;dt = [0.1, 0.125, 0.05].choose;x = Plazy({ Pseries(1300 + 300.rand, 110 + 130.rand, 16) }).asStream;x.do({ arg item;s.sendMsg("/s_new", "pdef_grainlet", -1,0,0, \freq, item);dt.wait;})})
)

// ... and so on ...

Tdef(\x).stop;
Tdef.removeAll;

//Conditions

	(
c = Condition.new(false);
​
Routine {
    1.wait;
    "waited for 1 second".postln;
    1.wait;
    "waited for another second, now waiting for you ... ".postln;
    c.wait;
    "the condition has stopped waiting.".postln;
    1.wait;
    "waited for another second".postln;
    "waiting for you ... ".postln;
        c.test = false;
        c.wait;
    "the condition has stopped waiting.".postln;
    1.wait;
    "the end".postln;
}.play;
)
​
// continue
(
c.test = true;
c.signal;
)
​
// a typical use is a routine that can pause under certain conditions:
(
c = Condition.new;
fork { loop { 1.wait; "going".postln; c.wait } };
)
c.test = true; c.signal;
c.test = false;
// the same, using hang
​
(
c = Condition.new;
​
Routine {
    1.wait;
    "waited for 1 second".postln;
    1.wait;
    "waited for another second, now waiting for you ... ".postln;
    c.hang;
    "the condition has stopped waiting.".postln;
    1.wait;
    "waited for another second".postln;
    "waiting for you ... ".postln;
    c.hang;
    "the condition has stopped waiting.".postln;
}.play;
)
​
// continue
c.unhang;
Waiting for Synths to end (waitForFree) uses a Condition implicitly:
(
SynthDef(\help, { |out|
    var mod = LFNoise2.kr(ExpRand(0.5, 2)) * 0.5;
    var snd =  mod * Blip.ar(Rand(200, 800) * (mod + 1));
    Out.ar(out, snd);
    FreeSelf.kr(mod < 0); // free the synth when amplitude goes below 0.
}).add;
)
​
(
fork {
    10.do {
        "started a synth".postln;
        Synth(\help).waitForFree;
        "This one ended. Wait a second,  I will start the next one.".postln;
        1.wait;
    };
    "This is it.".postln;
}
);

// Semaphore

	// allow only one thread
(
c = Semaphore(1);
fork {
    c.wait;
    "thread 1> now I am doing something for 10 seconds. Block the semaphore meanwhile.".postln;
    10.wait;
    c.signal;
    "thread 1> ok, done. Release the semaphore.".postln;
};
fork {
    3.0.rand.wait;
    "thread 2> I would like to go on, if I may.".postln;
    c.wait; // may I?
    "thread 2> this took until the other thread has released the semaphore. "
        "Blocking for 4 seconds.".postln;
    4.wait;
    "thread 2> ok, done. Releasing the semaphore".postln;
    c.signal;
};
fork {
    4.wait;
    "thread 3> I, too, would like to go on, if I may.".postln;
    c.wait; // may I?
    "thread 3> this took until both other threads had released the semaphore.".postln;
    c.signal;
};
)

	// allow two threads at a time.
(
c = Semaphore(2);
fork {
    c.wait;
    "thread 1> now I am doing something for 20 seconds. Block the semaphore.".postln;
    10.wait;
    "thread 1> ok, done. Releasing the semaphore".postln;
    c.signal;
};
fork {
    rrand(3.0, 5.0).wait;
    "thread 2> I would like to go on, if I may.".postln;
    if(c.count <= 0) { "thread 3> ok, then I wait ...".postln };
    c.wait; // may I?
    "thread 1> ok, going ahead.".postln;
    17.wait;
    "thread 2> ok, done. Releasing the semaphore".postln;
    c.signal;
};
fork {
    6.wait;
    "thread 3> I, too, would like to go on, if I may.".postln;
    if(c.count <= 0) { "thread 3> ok, then I wait ...".postln };
    c.wait; // may I?
    "thread 3> ok, this took until the first thread had released the semaphore. "
        "Ok, doing something for 4 seconds. Block the semaphore".postln;
        4.wait;
    "Releasing the semaphore.".postln;
    c.signal;
};
fork {
    7.wait;
    "thread 4> Me, the fourth one, would like to go on, if I may.".postln;
    if(c.count <= 0) { "thread 4> ok, then I wait ...".postln };
    c.wait; // may I?
    "thread 4> ok, this took until the third thread had released the semaphore. "
        "Ok, doing something for 3 seconds. Block the semaphore".postln;
        3.wait;
    "Releasing the semaphore.".postln;
    c.signal;
};
)

	// grant exclusive access to data to only one thread
// there should never be mixed values in the data array
(
var data, useAndModify;

data = [1, 2, 3];
c = Semaphore(1);
// c = Semaphore(2); use this to test how it would behave without exclusive access.
useAndModify = { |newData, who|
    postln(who + "trying to get blocking access.");
    if(c.count <= 0) { who + "ok, then I wait ...".postln };
    c.wait; // may I access? if not, I wait. if yes, disallow others.
    "\n".post;
    (who + "continuing...").postln;
    data.do({ |x|
            0.1.wait;
            postln(who + x);
        });
    "\n".post;
    newData.do { |x, i| data[i] = x };
    postln(who + "rewriting data to:" + newData);
    postln(who + "releasing");
    c.signal; // allow others access again
};

// e.g. set the values to integers
u = Routine {
    inf.do { |i|
        useAndModify.value([100, 200, 300], "thread 1>");
        rrand(1, 3).wait;
    }
};

// e.g. set the values to floats
k =  Routine {
    0.5.wait;
    inf.do { |i|
        useAndModify.value([pi, 0.5pi, 2pi], "thread 2>");
        rrand(1, 5).wait;
    }
};
u.play;
k.play;
);