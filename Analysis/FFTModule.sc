FFTModule : TemplateModule{

	var <fftSize, <server;
	var <fftbuf;

	*new{|fftSize=1024, server|
		^super.new.initFFTModule(fftSize, server);
	}
	initFFTModule {|fftSizearg, serverarg|
		server = serverarg ? Server.default;
		fftSize = fftSizearg;
		fftbuf = Buffer.alloc(server, fftSize);
		this.fillFunDict();
	}

	fillFunDict{
		funDict = (
			simple_fft: {|in, hop=0.5, wintype=1, active=1, winsize=0|
				FFT(fftbuf, in, hop: hop, wintype: wintype, active: active, winsize:winsize);
			}
		)
	}

}

IFFTModule : TemplateModule{

	var <fftSize, <server;
	var <fftbuf, <inPlace;

	*new{|fftSize=1024, server, inPlace=0|
		^super.new.initFFTModule(fftSize, server, inPlace);
	}
	initFFTModule {|fftSizearg, serverarg, inPlacearg|
		server = serverarg ? Server.default;
		fftSize = fftSizearg;
		inPlace = inPlacearg;
		if(inPlace.not,{
			fftbuf = Buffer.alloc(server, fftSize)});
		this.fillFunDict();
	}

	fillFunDict{
		funDict = (
			simple_ifft: {if(inPlace,{
				|inBuf, wintype=1, winsize=0|
				IFFT(inBuf, wintype: wintype, winsize:winsize);
			},{
				|inBuf, wintype=1, winsize=0|
				IFFT(PV_Copy(inBuf, fftbuf), wintype: wintype, winsize:winsize);
			})},
			copy_and_ifft:{if(inPlace,{
				|inBuf, copyBuf, wintype=1, winsize=0|
				PV_Copy(inBuf, copyBuf);
				IFFT(inBuf, wintype: wintype, winsize:winsize);
			},{
				|inBuf, copyBuf, wintype=1, winsize=0|
				PV_Copy(inBuf, copyBuf);
				IFFT(PV_Copy(inBuf, fftbuf), wintype: wintype, winsize:winsize);
			})}
		)
	}

	inPlace_{|inPlacearg|
		inPlace = inPlacearg;
		if(inPlace,{
			{fftbuf.free}.try;
			},{
			fftbuf = Buffer.alloc(server, fftSize)
		});
	}
}