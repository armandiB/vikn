Instr([\Analysis, \Fourier, \FFT],{ arg in, numFrames, hop=0.5, wintype=1; //Not multichannel yet.
	FFT(LocalBuf(numFrames), in, hop: hop, wintype: wintype);
});