var alpha_default = 0.99;
var epsilon_default = 0.5; //long-term noise characteristic
var delta_default = 0.5; //depens on roll-off and alpha
var r_exp = 0.98;
var delta_r = delta_default**r_exp;

/* pvcalc2 not working
Instr([\Analysis, \Denoising, \PCEN_v0], {arg lpchainin, fft_chain, pcen_copy;
	Instr([\Analysis, \Utils, \LocalCopy]).value(lpchainin, pcen_copy).pvcalc2(fft_chain, fft_chain.fftSize, {|mag_lp, phase_lp, mag, phase|
		[abs(mag - ((mag / (epsilon_default + mag_lp).pow(alpha_default)) + delta_default).pow(r_exp) + delta_r), phase];
		//Variation: replace inPlaceChain_pcen by localChain_lp inPlace:true, beware initialization. Crossfade both (PV_XCopy? Pseudo-Ugen or Ugen).
	})
}, [
	nil, nil, StaticIntegerSpec(1)
]);
*/