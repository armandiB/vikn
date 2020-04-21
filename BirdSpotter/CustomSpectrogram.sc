CustomSpectrogram : Spectrogram{

	//dependencies of inbus and fftbuf
	//server in initSpectrogram

	sendSynthDef {
	}

	initSpectrogram { arg parent, boundsarg, bufSizearg, col, bg, lowfreqarg, highfreqarg, serverarg;
		server = serverarg ? Server.default;
		inbus = 0;
		rate = 25; // updates per second
		bufSize = bufSizearg ? 1024; // fft window
		binfreqs = bufSize.collect({|i| ((server.sampleRate/2)/bufSize)*(i+1)});
		index = 0;
		intensity = 1;
		background = bg ? Color.black;
		color = col ? Color(1, 1, 1); // white by default
		crosshaircolor = Color.white;
		tobin = min(binfreqs.indexOf((highfreqarg/2).nearestInList(binfreqs)), bufSize.div(2) - 1);
		frombin = max(binfreqs.indexOf((lowfreqarg/2).nearestInList(binfreqs)), 0);
		fftDataArray = Int32Array.fill((tobin - frombin + 1), 0);
		running = false;
		this.sendSynthDef;
		this.createWindow(parent, boundsarg);
	}

	createWindow {arg parent, boundsarg;
		var bounds;
		window = parent ? Window("Spectrogram",  Rect(200, 450, 600, 300));
		bounds = boundsarg ? window.view.bounds;
		this.setWindowImage( bounds.width );
		this.setUserView(window, bounds);
		window.onClose_({
			image.free;
			this.stopruntask;
		}).front;
	}

	startruntask {
		running = true;
		this.recalcGradient;
		{
			runtask = Task({
				{
					fftbuf.getn(0, bufSize,
					{ arg buf;
						var magarray, complexarray;
						magarray = buf.clump(2)[(frombin .. tobin)].flop;

						/*
// OLD METHOD:
						// magnitude spectrum
						complexarray = (Complex(
								Signal.newFrom( magarray[0] ),
								Signal.newFrom( magarray[1] )
						).magnitude.reverse*2).clip(0, 255); // times 2 in order to strenghten color
						*/

// NEW METHOD:
						/*
						// log intensity - thanks nick
						// this crashes server atm., on resize and new buffer size
						//20*log10(mag+1) * 4
						complexarray = ((((Complex(
								Signal.newFrom( magarray[0] ),
								Signal.newFrom( magarray[1] )
							).magnitude.reverse)+1).log10)*80).clip(0, 255);
						// That +1 above is the cause of the crash
						// thus temporary fix below
						// AB: fixed back
						*/

						complexarray = (((1+(Complex(
								Signal.newFrom( magarray[0] ),
								Signal.newFrom( magarray[1] )
						).magnitude.reverse)).log10)*80).clip(0, 255);

						complexarray.do({|val, i|
							val = val * intensity;
							fftDataArray[i] = colints.clipAt((val/16).round);
						});
						{
							image.setPixels(fftDataArray, Rect(index%imgWidth, 0, 1, (tobin - frombin + 1)));
							index = index + 1;
							if( userview.notClosed, { userview.refresh });
						}.defer;
					});
					rate.reciprocal.wait; // framerate
				}.loop;
			}).start;
		}.defer; //no need to defer to allow the creation of an fftbuf before starting
	}

	stopruntask {
		running = false;
		runtask.stop;
	}

	fftbuf_ {arg fftbufarg;
		fftbuf = fftbufarg;
	}
}