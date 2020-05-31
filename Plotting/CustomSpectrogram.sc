CustomSpectrogram {

	//ToDo-med: tilt option
	//ToDo-med: hue smoothing
	//ToDo-medlow: generalized salience spectrogram (collapse harmonics)
	//ToDo-low: log-frequency mode
	//ToDo-low: change displayed tobin/frombin at runtime (SpectrogramWindow)
	//ToDo-low: better performance by pre-computing the log mapping into intervals for each pixel
	//ToDo-low: other sets of colors. Make magcolor more relevant in all modes. Hue offset parameter, hue spread parameter, hue distrubution parameter (could be an vectorized for combination of distributions), ...
	//ToDo-low: being able to resize and/or rescale pixels, or zoom and speed (video, flashes, etc.)
	//ToDo-vlow: change getn to be able to go beyond 1024*8 (size of packet, see help files)

	classvar <server;
	var window; //, bounds;
	var <fftbuf, isLocalFftbuf, fftDataArray, fftSynth;
	var inbus, <rate;
	var <bufSize, binfreqs;	// size of FFT
	var <frombin, <tobin;
	var image, imgWidth, imgHeight, index, <>intensity, runtask;
	var magcolor, background, colgrid, <colgridresolution; // colgrid is 2d // eventually 3d tensor of integers each representing a color pixel (in HSV)
	var userview, mouseX, mouseY, freq, drawCrossHair = false; // mYIndex, mXIndex, freq;
	var crosshaircolor, running;
	var <colormode;

	*new { arg parent, bounds, buffer, bufSize, magcolor, colormode=0, colgridresolution=[128, 256], background, lowfreq=0, highfreq=inf, server;
		^super.new.initCustomSpectrogram(parent, bounds, buffer, bufSize, magcolor, colormode, colgridresolution, background, lowfreq, highfreq, server);
	}

	initCustomSpectrogram { arg parent, boundsarg, bufferarg, bufSizearg, magcolorarg, colormodearg, colgridresolutionarg, bg, lowfreqarg, highfreqarg, serverarg;
		server = serverarg ? Server.default;
		inbus = 0;
		rate = 25; // updates per second
		bufSize = bufSizearg ? (bufferarg !? {_.numFrames} ? 1024); // fft window
		fftbuf = bufferarg ? Buffer.alloc(server, bufSize);
		isLocalFftbuf = if(bufferarg.isNil, {true}, {false});
		binfreqs = bufSize.collect({|i| ((server.sampleRate/2)/bufSize)*(i+1)});
		index = 0;
		intensity = 1;
		colormode = colormodearg ? 0;
		background = bg ? Color.black;
		magcolor = magcolorarg ? Color(1, 1, 1); // white by default
		colgridresolution = colgridresolutionarg ? [128, 256];
		crosshaircolor = Color.white;
		lowfreqarg = lowfreqarg ? 0;
		highfreqarg = highfreqarg ? inf;
		tobin = min(binfreqs.indexOf((highfreqarg/2).nearestInList(binfreqs)), bufSize.div(2) - 1);
		frombin = max(binfreqs.indexOf((lowfreqarg/2).nearestInList(binfreqs)), 0);
		fftDataArray = Int32Array.fill((tobin - frombin + 1), 0);
		running = false;
		this.createWindow(parent, boundsarg);
	}

	createWindow {arg parent, boundsarg;
		var bounds;
		window = parent ? Window("Spectrogram",  Rect(100, 450, 1200, 750));
		bounds = boundsarg ? window.view.bounds;
		this.setWindowImage( bounds.width );
		this.setUserView(window, bounds);
		window.onClose_({
			this.stopruntask;
			{image.free}.defer(0.1);
			if(isLocalFftbuf, try {fftbuf.free});
		}).front;
	}

	setUserView {arg window, bounds;
		userview = UserView(window, bounds)
			.focusColor_(Color.white.alpha_(0))
			.resize_(5)
			.drawFunc_({arg view;
				var b = view.bounds;
				Pen.use {
					Pen.scale( b.width / imgWidth, b.height / imgHeight );
					Pen.drawImage( 0@0, image );
				};
				if( drawCrossHair, {
					Pen.color = crosshaircolor;
					Pen.addRect( b.moveTo( 0, 0 ));
					Pen.clip;
					Pen.line( 0@mouseY, b.width@mouseY);
					Pen.line(mouseX @ 0, mouseX @ b.height);
					Pen.font = Font( "Helvetica", 10 );
					Pen.stringAtPoint( "freq: "+freq.asString, mouseX + 20 @ mouseY - 15);
					Pen.stroke;
				});
			})
			.mouseDownAction_({|view, mx, my|
				this.crosshairCalcFunc(view, mx, my);
				drawCrossHair = true;
				view.refresh;
			})
			.mouseMoveAction_({|view, mx, my|
				this.crosshairCalcFunc(view, mx, my);
				view.refresh;
			})
			.mouseUpAction_({|view, mx, my|�
				drawCrossHair = false;
				view.refresh;
			});
	}

	startruntask {
		running = true;
		this.recalcGradient;
		{
			runtask = Task({
				{
					fftbuf.getn(0, bufSize,
					{ arg buf;
						var inarray, polararray, rhoarray, phasearray;
						inarray = buf.clump(2)[(frombin .. tobin)].flop;

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

						polararray = Complex(
								Signal.newFrom( inarray[0] ),
								Signal.newFrom( inarray[1] )
						).asPolar;

						rhoarray = (((1+(polararray.magnitude.reverse)).log10)*0.3125).clip(0, 0.999999999);
						phasearray = polararray.phase.reverse;

						rhoarray.do({|rhoval, i|
								var magVal, phaseVal;
								magVal = rhoval * intensity;
								phaseVal = (phasearray[i]/2pi) + 0.5; // Between 0 and 1.
								fftDataArray[i] = colgrid.clipAt((magVal*colgridresolution[0]).round).clipAt((phaseVal*colgridresolution[1]).trunc);
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
		}.defer(0.1);
	}

	stopruntask {
		running = false;
		runtask.stop;
	}

	rate_ {arg ratearg;
		if(ratearg<=0,{
			rate = 25;
		},{
			rate = ratearg;
		});
	}

	fftbuf_ {arg fftbufarg;
		fftbuf = fftbufarg;
		isLocalFftbuf = false;
	}

	setBufAndSize_ {arg bufferarg, bufSizearg, restart=true;
		//if(bufSizearg.isPowerOfTwo, {
			this.stopruntask;
			bufSize = bufSizearg ? bufferarg.numFrames;
			if(isLocalFftbuf, {try {fftbuf.free}});
			fftbuf = bufferarg;
		    isLocalFftbuf = false;
			binfreqs = bufSize.collect({|i| ((server.sampleRate/2)/bufSize)*(i+1) });
			tobin = bufSize.div(2) - 1;
			frombin = 0;
			fftDataArray = Int32Array.fill((tobin - frombin + 1), 0);
			this.setWindowImage( userview.bounds.width );
		/*
		}, {
			"Buffer size has to be power of two (256, 1024, 2048, etc.)".warn;
		});
		*/
	}

	magcolor_ {arg colorarg;
		magcolor = colorarg;
		this.recalcGradient;
	}

	background_ {arg backgroundarg;
		background = backgroundarg;
		this.prCreateImage( userview.bounds.width );
		this.recalcGradient;
//		userview.backgroundImage_(image, 10);
		userview.refresh;
	}

	prCreateImage { arg width;
		if( image.notNil, { image.free });
		imgWidth = width;
		imgHeight = (tobin - frombin + 1); // bufSize.div(2);
		image = Image.color(imgWidth.asInteger, imgHeight.asInteger, background);
	}

	setBufSize_{arg buffersize, restart=true;
		//if(buffersize.isPowerOfTwo, {
			this.stopruntask;
			bufSize = buffersize;
		    if(isLocalFftbuf, {try {fftbuf.free}});
			fftbuf = Buffer.alloc(server, bufSize, 1, { if(restart, {this.startruntask}) }) ;
		    isLocalFftbuf = true;
			binfreqs = bufSize.collect({|i| ((server.sampleRate/2)/bufSize)*(i+1) });
			tobin = bufSize.div(2) - 1;
			frombin = 0;
			fftDataArray = Int32Array.fill((tobin - frombin + 1), 0);
			this.setWindowImage( userview.bounds.width );
		/*
		}, {
			"Buffersize has to be power of two (256, 1024, 2048, etc.)".warn;
		});
		*/
	}

	recalcGradient { arg colormodearg;
		var magColors;
		var magRes = colgridresolution[0];
		var phaseRes = colgridresolution[1];
		colormode = colormodearg ? colormode;

		switch(colormode,
			-1, {magColors = (0..magRes).collect({|val| blend(background, magcolor, val/magRes)});
				colgrid = magColors.collect({|col|
					var magHsv = col.asHSV;
					(0..phaseRes).collect({
						|val| Image.colorToPixel( col)
					});
				}); //colGrid is a 2d array of size colgridresolution
			},
			0.5, {magColors = (0..magRes).collect({|val| blend(background, magcolor, val/magRes)}); //using background and magColor (hue is overwritten)
				colgrid = magColors.collect({|col|
					var magHsv = col.asHSV;
					(0..phaseRes).collect({
						|val| Image.colorToPixel( Color.hsv(val/phaseRes, 1, magHsv[2], magHsv[3]))
					});
				});
			},
			0, {colgrid = (0..magRes).collect({|valmag| //slightly faster
					(0..phaseRes).collect({
						|phaseval| Image.colorToPixel( Color.hsv(phaseval/phaseRes, 1, valmag/magRes, 1))
					});
				});
			},
			1, {colgrid = (0..magRes).collect({|valmag|
				var valmagadj = valmag/magRes;
					(0..phaseRes).collect({
						|phaseval| Image.colorToPixel( Color.hsv(phaseval/phaseRes, 1/(1-(1-valmagadj).log2), valmagadj, 1))
					});
				});
			},
			1.5, {colgrid = (0..magRes).collect({|valmag|
				var valmagadj = valmag/magRes;
					(0..phaseRes).collect({
						|phaseval| Image.colorToPixel( Color.hsv(phaseval/phaseRes, 1-valmagadj, valmagadj, 1))
					});
				});
			}
		)
	}

	colormode_{arg colormodearg;
		this.recalcGradient(colormodearg);
	}

	colgridresolution_{arg colgridresolutionarg;
		colgridresolution = colgridresolutionarg;
		this.recalcGradient;
	}

	crosshairColor_{arg argcolor;
		crosshaircolor = argcolor;
	}

	crosshairCalcFunc {|view, mx, my|
		mouseX = (mx-1.5).clip(0, view.bounds.width);
		mouseY = (my-1.5).clip(0, view.bounds.height);
		freq = binfreqs[((view.bounds.height)-mouseY).round(1).linlin(0, view.bounds.height, frombin*2, tobin*2).floor(1)].round(0.01);
	}

	setWindowImage { arg width;
		this.prCreateImage( width );
		index = 0;
	}

	start { this.startruntask }

	stop { this.stopruntask }
}