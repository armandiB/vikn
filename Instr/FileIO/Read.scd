// Sample player
Instr([\FileIO, \Read, \SampleRead],{ arg sample, pchRatio=1.0, start=0.0;
	PlayBuf.ar(sample.numChannels,
		sample.bufnumIr, // finds the buffer number when the synth starts
		sample.bufRateScaleKr * pchRatio,
		1.0,
		start * sample.bufFramesKr,
		1.0); // loop
});