Instr([\SecuIO, \Channels, \SumToMono],{ arg in;
	if(in.numChannels > 1,
		{in.sum},
		{in}
	);
});

Instr([\SecuIO, \Channels, \MakeStereoIfMono],{ arg in;
	if(in.numChannels == 1,
		{Pan2.ar(in, 0.0)},
		{in}
	);
});