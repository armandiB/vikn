/* pvcalc2 not working
Instr([\Analysis, \ARmax, \AR1_v0],{ arg chainin, ar1_factor=0, ar1_copy, lower_bin=nil, higher_bin=nil, zeroothers=0; //Original implementation.
	var copyin = Instr([\Analysis, \Utils, \LocalCopy]).value(chainin, ar1_copy);
	var fftSize = copyin.fftSize; //Will fail for multichannel.
	var localBuf_delay = LocalBuf(fftSize);
	var chain_lowPass = copyin.pvcalc2(localBuf_delay, fftSize, {|mag, phase, mag_delay, phase_delay|
		[(ar1_factor*mag) + ((1-ar1_factor)*mag_delay), phase];
	}, frombin: lower_bin, tobin:higher_bin, zeroothers: zeroothers);
	PV_Copy(chain_lowPass, localBuf_delay);
}, [
	nil, ControlSpec(0, 1), StaticIntegerSpec(1), StaticIntegerSpec(0), StaticIntegerSpec(inf), StaticIntegerSpec(0)
]);
*/

//ToDo-vhigh: fix bug in crucial that fixes phases for PV
Instr([\Analysis, \ARmax, \AR1_v1],{ arg chainin, ar1_factor=0, ar1_copy;
	var copyin = Instr([\Analysis, \Utils, \LocalCopy]).value(chainin, ar1_copy);
	PV_MagSmooth(copyin, ar1_factor);

	//Instr([\Analysis, \Utils, \CopyPhase]).value(chain, chainin, ar1_copyPhase);
}, [
	nil, ControlSpec(0, 1), StaticIntegerSpec(1), StaticIntegerSpec(1)
]);
