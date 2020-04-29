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
			hop_d: 0.5,
			wintype_d: 1,
			active_d: 1,
			winsize_d: 0
		)
	}

	fillFunDict {
		funDict = (
			simple_fft: {
				FFT(fftbuf, \in.ar, \hop.kr(~hop_d), \wintype.kr(~wintype_d), \active.kr(~active_d), \winsize.kr(~winsize_d));
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

	synthDefTemplate {|synthdefname, function| //No out.
		^{
			this.makeFunDef(synthdefname, function);
		};
	}
}

IFFTModule : TemplateModule{

	var <fftSize, <server;
	var <inPlace, <inPlacebuf;

	*new{|fftSize=1024, server, inPlace=false|
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
			wintype_d: 1,
			winsize_d: 0
		)
	}

	fillFunDict{
		funDict = (
			inplace_simple_ifft: {
				IFFT(\inbufnum.kr, \wintype.kr(~wintype_d), \winsize.kr(~winsize_d));
			},
			notinplace_simple_ifft: {
				IFFT(PV_Copy(\inbufnum.kr, inPlacebuf),\wintype.kr(~wintype_d), \winsize.kr(~winsize_d));
			},
			copy_and_ifft:{ //Always in place.
				var copyBuf_chain;
				copyBuf_chain = PV_Copy(\inbufnum.kr, \copybufnum.kr);
				IFFT(PV_Copy(copyBuf_chain, inPlacebuf), \wintype.kr(~wintype_d), \winsize.kr(~winsize_d));
			}
		)
	}

	//Maybe not best system
	inPlace_{|inPlacearg=false|
		inPlace = inPlacearg;
		if(inPlace,{
			{inPlacebuf.free}.try;
		},{
			inPlacebuf = Buffer.alloc(server, fftSize);
		});
	}
}