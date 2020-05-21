//Make CustomSpectrogram: .value(Dict) or .valueArray
Instr([\Plotting, \Spectrogram, \MakeSpectr],{ arg bufSize, lowfreq, highfreq, colormode, colgridresolution, server, parent=Window("Spectrogram",  Rect(30, 100, 1380, 800));
	CustomSpectrogram.new(bufSize: bufSize, lowfreq: lowfreq, highfreq: highfreq, colormode: colormode, colgridresolution: colgridresolution, server: server, parent: parent);
}, [
	StaticIntegerSpec(), StaticSpec(), StaticSpec(), StaticSpec(), ObjectSpec(), ObjectSpec(), ObjectSpec()
]);