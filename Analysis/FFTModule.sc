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
		this.fillDeftParams();
		this.fillFunDict();
	}

	fillDeftParams {
		dfltParams = (
			hop: 0.5,
			wintype: 1,
			active: 1,
			winsize: 0
		)
	}

	fillFunDict {
		funDict = (
			simple_fft: {|in, hop=(~hop), wintype=(~wintype), active=(~active), winsize=(~winsize), fftbufnum=(fftbuf)|
				FFT(fftbufnum, in, hop: hop, wintype: wintype, active: active, winsize:winsize);
			}
		)
	}

	/* Useless unless fftbuf is made dynamic (by using fftbufnum arg for example)
	fftSize_ {|fftSizearg=1024|
		fftSize = fftSizearg;
		{fftbuf.free}.try; //Security?
		fftbuf = Buffer.alloc(server, fftSize);
	}
	*/

}

IFFTModule : TemplateModule{

	var <fftSize, <server;
	var <inPlace, <inPlacebuf;

	*new{|fftSize=1024, server, inPlace=0|
		^super.new.initFFTModule(fftSize, server, inPlace);
	}
	initFFTModule {|fftSizearg, serverarg, inPlacearg|
		server = serverarg ? Server.default;
		fftSize = fftSizearg;
		inPlace = inPlacearg;
		if(inPlace.not,{
			inPlacebuf = Buffer.alloc(server, fftSize)});
		this.fillDeftParams();
		this.fillFunDict();
	}

	fillDeftParams {
		dfltParams = (
			wintype: 1,
			winsize: 0
		)
	}

	//ToDo: adapt funDict to dfltParams if it works
	fillFunDict{
		funDict = (
			simple_ifft: if(inPlace,{
				|inBuf, wintype=1, winsize=0|
				IFFT(inBuf, wintype: wintype, winsize:winsize);
			},{
				|inBuf, wintype=1, winsize=0|
				IFFT(PV_Copy(inBuf, inPlacebuf), wintype: wintype, winsize:winsize);
			}),
			copy_and_ifft:{if(inPlace,{
				|inBuf, copyBuf, wintype=1, winsize=0| var copyBuf_chain;
				copyBuf_chain = PV_Copy(inBuf, copyBuf);
				IFFT(copyBuf_chain, wintype: wintype, winsize:winsize);
			},{
				|inBuf, copyBuf, wintype=1, winsize=0| var copyBuf_chain;
				copyBuf_chain = PV_Copy(inBuf, copyBuf);
				IFFT(PV_Copy(copyBuf_chain, inPlacebuf), wintype: wintype, winsize:winsize);
			})}
		)
	}

	inPlace_{|inPlacearg=0|
		inPlace = inPlacearg;
		if(inPlace,{
			{inPlacebuf.free}.try;
			},{
			inPlacebuf = Buffer.alloc(server, fftSize);
		});
	}
}