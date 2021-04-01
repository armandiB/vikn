CVDCDef : AbstractSynthDefSender{

	classvar <synthDefName_ar = \CVDCChan_ar;
	classvar <synthDefName_kr = \CVDCChan_kr;

	initSynthDef{
		var sd;
		if(this.hasSentDef(synthDefName_ar).not, {
			sd = SynthDef(synthDefName_ar, {
				Out.ar(\out.kr, K2A.ar(Lag.ar(In.ar(\in.ar), \lagTime.kr(0.05)))); //TODO: try difference with OffsetOut
			});
			sd.send(server);
			this.addSentDef(synthDefName_ar);
			this.addSynthDefDict(sd);
		});


		if(this.hasSentDef(synthDefName_kr).not, {
			sd = SynthDef(synthDefName_kr, {
				Out.ar(\out.kr, K2A.ar(Lag.kr(In.kr(\in.kr), \lagTime.kr(0.05)))); //try OffsetOut too out of curiosioty
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
	var <dur; //TODO: idem setter

	var <>cvOutGlobal;

	*new { arg server, outbus;
		^super.new(server).initCVDCChan(outbus);
	}

	initCVDCChan{ arg outbusarg;
		cvOutGlobal = CVOutGlobal.serverDict[server];
		outbus = outbusarg;
		this.initSynthDef();
		CVOutGlobal.cvDCChanList.add(this); //maybe want to have an attribute which is the position in the list for quicker deleting?
	}

	controlbus_{ |bus|
		if(bus.isNil, {
			controlbus = controlbus ? FlexBus.control(server,1);
		}, {
			controlbus = bus;
			if(synth.notNil && (rate == \kr), {
				synth.set(\in, bus);
			});
		});
		^controlbus;
	}

	makeSynth_ar{ arg in, argdur;
		synth !? this.freeSynth();
		dur = argdur ? dur;
		synth = Synth(synthDefName_ar, [out: outbus, in: in, lagTime: dur], CVOutGlobal.cvOutGroupDict[server]);
		rate = \ar
		^synth
	}

	makeSynth_kr{ arg in, argdur;
		var inbus = in ? this.controlbus_();
		synth !? this.freeSynth();
		argdur !? {dur = argdur};
		synth = Synth(synthDefName_kr, [out: outbus, in: inbus, lagTime: dur], CVOutGlobal.cvOutGroupDict[server]);
		rate = \kr
		^synth
	}

	freeSynth{
		synth.free;
	}
}

CVVoctChan : CVDCChan {
	var <>tuningfreq;
	var <>tuningdc; //in V, useful because synths don't track perfectly
	var <>midiToFreqFunc;


	*new { arg server, outbus, tuningfreq, midiToFreqFunc, tuningdc=0;
		^super.new(server, outbus).initCVVoctChan(tuningfreq, tuningdc, midiToFreqFunc);
	}

	initCVVoctChan{ arg tuningfreqarg, tuningdcarg, midiToFreqFuncarg;
		cvOutGlobal.cvVoctChanIndexes.add(CVOutGlobal.cvDCChanList.size);
		tuningfreq = tuningfreqarg;
		midiToFreqFunc = midiToFreqFuncarg;
		tuningdc = tuningdcarg;
	}

	//TODO: setFreqBus + use in setNoteBus unless precomputed with interface_factor

	//TODO: set bus to tuningdc and output sine in a channel given as argument (maybe global tuning tone channel in CVOutGlobal?)

	setNoteBus{|note| //0 -> C4 midi
		//rate == \kr
		controlbus.setFlex(((midiToFreqFunc.value(note+60)/tuningfreq).log2 + tuningdc) * cvOutGlobal.interface_factor) //TODO: not optimal, could compute the whole thing at each change of midiToFreqFuncarg, tuningdc or tuningfreq (and interface_factor? in that case, make setter used in CVOutGlobal that calls recompute) //or maybe just avoid log2 with Tuning
	}
}