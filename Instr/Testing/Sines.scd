Instr([\Testing, \Sines, \Sin_FM_Sin],{ arg freq=330, fmFreq=0.2, fmIndex=2.0, amp=0.1;
	SinOsc.ar(freq*SinOsc.kr(fmFreq, 0.0, fmIndex), 0.0, amp);
});