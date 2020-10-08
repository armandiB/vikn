Instr([\Testing, \Sines, \sin_FM_sin],{ arg freq=330, fmFreq=0.2, fmIndex=2.0, amp=0.1;
	SinOsc.ar(freq*SinOsc.ar(fmFreq, 0.0, fmIndex), 0.0, amp);
});

Instr([\Testing, \Sines, \sin_AM_sin],{ arg freq=330, amFreq=2, amIndex=0.2, amp=0.1;
	SinOsc.ar(freq, 0.0, amp)*(1+SinOsc.ar(amFreq, 0.0, amIndex));
});

Instr([\Testing, \Sines, \simple_sin],{ arg freq=330, amp=0.1;
	SinOsc.ar(freq, 0.0, amp);
});

Instr([\Testing, \Sines, \simple_sin_blip],{ arg freq=330, amp=0.1;
	SinOsc.ar(freq, 0.0, amp) * Env.perc(0.001,0.06).ar(Done.freeSelf, levelScale: amp);
});