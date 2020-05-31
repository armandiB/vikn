Instr([\Analysis, \Fourier, \FFT],{ arg in, numFrames, hop=0.5, wintype=1; //Not multichannel yet.
	FFT(LocalBuf(numFrames), in, hop: hop, wintype: wintype);
});

Instr([\Analysis, \Fourier, \IFFT],{ arg chainin, wintype=1, ifft_amp=1, ifft_copy;
	ifft_amp*IFFT(Instr([\Analysis, \Utils, \LocalCopy]).value(chainin, ifft_copy), wintype: 1)
}, [
	nil, nil, nil, StaticIntegerSpec(1)
]);