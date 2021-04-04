CVDCDef : AbstractSynthDefSender{

	classvar <synthDefName_ar = \CVDCChan_ar;
	classvar <synthDefName_kr = \CVDCChan_kr;

	initSynthDef{
		var sd;
		if(this.hasSentDef(synthDefName_ar).not, {
			sd = SynthDef(synthDefName_ar, {
				Out.ar(\out.kr, K2A.ar(Lag.ar(In.ar(\in.ar), \lagtime.kr(0.05)))); //TODO: try difference with OffsetOut
			});
			sd.send(server);
			this.addSentDef(synthDefName_ar);
			this.addSynthDefDict(sd);
		});


		if(this.hasSentDef(synthDefName_kr).not, {
			sd = SynthDef(synthDefName_kr, {
				Out.ar(\out.kr, K2A.ar(Lag.kr(In.kr(\in.kr), \lagtime.kr(0.05)))); //try OffsetOut too out of curiosioty
		});
		sd.send(server);
		this.addSentDef(synthDefName_kr);
		this.addSynthDefDict(sd);
		});
	}
}

//Only difference with CVTrigChan is CVOutGlobal.cvDCChanList, and name of Synth arguments- so could move that into Def and make a common instanciator object that has Def as attribute?
CVDCChan : CVDCDef {

	var <rate;
	var <>outbus; //TODO: test if setter works, probably not, make one with synth.set
	var <synth;
	var <controlbus;
	var <lagtime; //TODO: idem setter

	var <>cvOutGlobal;

	*new { arg server, outbus, lagtime=0.05;
		^super.new(server).initCVDCChan(outbus, lagtime);
	}

	initCVDCChan{ arg outbusarg, lagtimearg;
		cvOutGlobal = CVOutGlobal.serverDict[server];
		outbus = outbusarg;
		lagtime = lagtimearg;
		this.initSynthDef();
		cvOutGlobal.cvDCChanList.add(this); //maybe want to have an attribute which is the position in the list for quicker deleting?
	}

	controlbus_{ |bus|
		if(bus.isNil, {
			controlbus = controlbus ? Bus.control(server,1);
		}, {
			controlbus = bus;
			if(synth.notNil && (rate == \kr), {
				synth.set(\in, bus);
			});
		});
		^controlbus;
	}

	makeSynth_ar{ arg in, lagtimearg;
		this.freeSynth();
		lagtime = lagtimearg ? lagtime;
		synth = Synth(synthDefName_ar, [out: outbus, in: in, lagtime: lagtime], cvOutGlobal.cvOutGroup);
		rate = \ar;
		^synth;
	}

	makeSynth_kr{ arg in, lagtimearg;
		var inbus = in ? this.controlbus_();
		this.freeSynth();
		lagtimearg !? {lagtime = lagtimearg};
		synth = Synth(synthDefName_kr, [out: outbus, in: inbus, lagtime: lagtime], cvOutGlobal.cvOutGroup);
		rate = \kr;
		^synth;
	}

	freeSynth{
		synth !? synth.free;
		^synth;
	}

	free{
		this.freeSynth();
		controlbus !? controlbus.free;
		^this;
	}
}

CVVoctChan : CVDCChan {
	var <>tuningfreq;
	var <>tuningdc; //in V, useful because synths don't track perfectly
	var <>midiToFreqFunc;

	var <tuneSynth;


	*new { arg server, outbus, tuningfreq, midiToFreqFunc, tuningdc=0;
		^super.new(server, outbus).initCVVoctChan(tuningfreq, tuningdc, midiToFreqFunc);
	}

	initCVVoctChan{ arg tuningfreqarg, tuningdcarg, midiToFreqFuncarg;
		cvOutGlobal.cvVoctChanList.add(this);
		tuningfreq = tuningfreqarg;
		midiToFreqFunc = midiToFreqFuncarg;
		tuningdc = tuningdcarg;
	}

	tune_kr{|freq, chan, volume=0.2, fadetime=3|
		var realFreq = freq ? cvOutGlobal.basefreq;
		controlbus.setFlex(tuningdc);
		tuneSynth = {SinOsc.ar(\freq.kr(realFreq),0,\volume.kr(volume));}.play(server, chan ? cvOutGlobal.tuningchan, fadetime);
		^tuneSynth;
	}

	setFreqBus{|freq|
		var realFreq = freq ? tuningfreq;
		//rate == \kr
		controlbus.setFlex(((realFreq/tuningfreq).log2 + tuningdc) * cvOutGlobal.interface_factor);
		^freq;
	}

	setNoteBus{|note| //0 -> C4 midi
		//rate == \kr
		^this.setFreqBus(midiToFreqFunc.value(note+60)) //TODO: not optimal, could compute the whole thing at each change of midiToFreqFuncarg, tuningdc or tuningfreq (and interface_factor? in that case, make setter used in CVOutGlobal that calls recompute) //or maybe just avoid log2 with Tuning?
	} //returns freq

	stopTune{
		^tuneSynth.free;
	}

	free{
		this.stopTune();
		this.freeSynth();
		controlbus !? controlbus.free;
		^this;
	}
}