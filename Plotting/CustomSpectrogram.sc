CustomSpectrogram {

	classvar <server;
	var window; //, bounds;
	var <fftbuf, fftDataArray, fftSynth;
	var inbus, <>rate;
	var <bufSize, binfreqs;	// size of FFT
	var <frombin, <tobin;
	var image, imgWidth, imgHeight, index, <>intensity, runtask;
	var color, background, colints; // colints is an array of integers each representing a color
	var userview, mouseX, mouseY, freq, drawCrossHair = false; // mYIndex, mXIndex, freq;
	var crosshaircolor, running;


	*new { arg parent, bounds, buffer, bufSize, color, background, lowfreq=0, highfreq=inf, server;
		^super.new.initCustomSpectrogram(parent, bounds, buffer, bufSize, color, background, lowfreq, highfreq, server);
	}

	initCustomSpectrogram { arg parent, boundsarg, bufferarg, bufSizearg, col, bg, lowfreqarg, highfreqarg, serverarg;
		server = serverarg ? Server.default;
		inbus = 0;
		rate = 25; // updates per second
		bufSize = bufSizearg ? (bufferarg !? (_.numFrames) ? 1024); // fft window
		fftbuf = bufferarg ? Buffer.alloc(server, bufSize);
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
		this.createWindow(parent, boundsarg);
	}

	createWindow {arg parent, boundsarg;
		var bounds;
		window = parent ? Window("Spectrogram",  Rect(100, 450, 1200, 750));
		bounds = boundsarg ? window.view.bounds;
		this.setWindowImage( bounds.width );
		this.setUserView(window, bounds);
		window.onClose_({
			image.free;
			this.stopruntask;
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

	setBufAndSize_ {arg bufferarg, bufSizearg, restart=true;
		if(bufSizearg.isPowerOfTwo, {
			this.stopruntask;
			bufSize = bufSizearg ? bufferarg.numFrames;
			//No freeing by security
			fftbuf = bufferarg;
			binfreqs = bufSize.collect({|i| ((server.sampleRate/2)/bufSize)*(i+1) });
			tobin = bufSize.div(2) - 1;
			frombin = 0;
			fftDataArray = Int32Array.fill((tobin - frombin + 1), 0);
			this.setWindowImage( userview.bounds.width );
		}, {
			"Buffer size has to be power of two (256, 1024, 2048, etc.)".warn;
		});
	}

	color_ {arg colorarg;
		color = colorarg;
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
		if(buffersize.isPowerOfTwo, {
			this.stopruntask;
			bufSize = buffersize;
			//Don't free fftbuf
			fftbuf = Buffer.alloc(server, bufSize, 1, { if(restart, {this.startruntask}) }) ;
			binfreqs = bufSize.collect({|i| ((server.sampleRate/2)/bufSize)*(i+1) });
			tobin = bufSize.div(2) - 1;
			frombin = 0;
			fftDataArray = Int32Array.fill((tobin - frombin + 1), 0);
			this.setWindowImage( userview.bounds.width );
		}, {
			"Buffersize has to be power of two (256, 1024, 2048, etc.)".warn;
		});
	}

	recalcGradient {
		var colors;
		colors = (0..16).collect({|val| blend(background, color, val/16)});
		colints = colors.collect({|col| Image.colorToPixel( col )});
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