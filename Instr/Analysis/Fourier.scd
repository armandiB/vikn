Instr([\Analysis, \Fourier, \FFT],{ arg in, numFrames, hop=0.5, wintype=1; //Not multichannel yet.
	FFT(LocalBuf(numFrames), in, hop: hop, wintype: wintype);
});

Instr([\Analysis, \Fourier, \IFFT],{ arg chainin, wintype=1, ifft_amp=1, ifft_copy;
	if(ifft_copy==1,
		{ifft_amp*IFFT(PV_Copy(chainin, LocalBuf(chainin.fftSize)), wintype: 1)}, //.fftSize fails in case of multichannel expansion. Can make a multichannel system with multiple spectrograms later if needed (or better visualization?).
		{ifft_amp*IFFT(chainin, wintype: 1)}
	);
}, [
	nil, nil, nil, StaticIntegerSpec(1)
]);