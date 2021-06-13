CVDCDef : AbstractSynthDefSender{

	initSynthDef{
		var sd;
		CVDCDef.synthDefName = \CVDCChan;
		if(this.hasSentDef(CVDCDef.synthDefName_ar).not, {
			sd = SynthDef(CVDCDef.synthDefName_ar, {
				Out.ar(\out.kr, K2A.ar(Lag.ar(In.ar(\in.ar), \lagtime.kr(0.05)))); //TODO: try difference with OffsetOut
			});
			sd.send(server);
			this.addSentDef(CVDCDef.synthDefName_ar);
			this.addSynthDefDict(sd);
		});


		if(this.hasSentDef(CVDCDef.synthDefName_kr).not, {
			sd = SynthDef(CVDCDef.synthDefName_kr, {
				Out.ar(\out.kr, K2A.ar(Lag.kr(In.kr(\in.kr), \lagtime.kr(0.05)))); //try OffsetOut too out of curiosioty
		});
		sd.send(server);
			this.addSentDef(CVDCDef.synthDefName_kr);
		this.addSynthDefDict(sd);
		});
	}

	//ToDo: user-set lag time. Naming same way as _ar, _kr in AbstractSynthDefSender
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
		synth = Synth(CVDCDef.synthDefName_ar, [out: outbus, in: in, lagtime: lagtime], cvOutGlobal.cvOutGroup);
		rate = \ar;
		^synth;
	}

	makeSynth_kr{ arg in, lagtimearg;
		var inbus = in ? this.controlbus_();
		this.freeSynth();
		lagtimearg !? {lagtime = lagtimearg};
		synth = Synth(CVDCDef.synthDefName_kr, [out: outbus, in: inbus, lagtime: lagtime], cvOutGlobal.cvOutGroup);
		rate = \kr;
		^synth;
	}

	setDCBus{|val|
		//rate == \kr
		var busVal = val * cvOutGlobal.interface_factor;
		controlbus.setFlex(busVal);
		^busVal;
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
	var <tuningfreq;
	var <>tuningdc; //in V, useful because synths don't track perfectly
	var <>realTuning;

	var <tuneSynth;

	var <storeLogTuningfreq;


	*new { arg server, outbus, realTuning, tuningfreq, tuningdc=0;
		^super.new(server, outbus).initCVVoctChan(realTuning, tuningfreq, tuningdc);
	}

	initCVVoctChan{ arg realTuningarg, tuningfreqarg, tuningdcarg;
		cvOutGlobal.cvVoctChanList.add(this);
		tuningfreq = this.tuningfreq_(tuningfreqarg ? realTuningarg.reffreq ? cvOutGlobal.reffreq);
		realTuning = realTuningarg;
		tuningdc = tuningdcarg;
	}

	tuningfreq_{|freq|
		tuningfreq = freq;
		storeLogTuningfreq = freq.log2;
		^tuningfreq;
	}

	tune_kr{|freq, chan, volume=0.2, fadetime=3|
		var realFreq = freq !? {this.tuningfreq_(_)}.value ? tuningfreq;
		controlbus.setFlex(tuningdc);
		tuneSynth = {SinOsc.ar(\freq.kr(realFreq),0,\volume.kr(volume));}.play(server, chan ? cvOutGlobal.tuningchan, fadetime);
		^tuneSynth;
	}

	tune_kr_ratio{|ratio, chan, volume=0.2, fadetime=3|
		var freq = realTuning.reffreq ? cvOutGlobal.reffreq;
		freq = freq*ratio;
		^this.tune_kr(freq, chan, volume, fadetime);
	}

	tune_kr_note{|note, chan, volume=0.2, fadetime=3|
		^this.tune_kr(realTuning.noteToFreq(note), chan, volume, fadetime);
	}

	setFreqBus{|freq|
		//rate == \kr
		var realFreq = freq ? tuningfreq;
		var busVal = ((realFreq/tuningfreq).log2 + tuningdc) * cvOutGlobal.interface_factor;
		controlbus.setFlex(busVal);
		^busVal;
	}

	setNoteBus{|note|
		//rate == \kr
		var busVal = (((realTuning.atNote(note) - realTuning.storeAtNoteReffreqNote)/12) + realTuning.storeLogReffreq - storeLogTuningfreq + tuningdc) * cvOutGlobal.interface_factor;
		controlbus.setFlex(busVal);
		^busVal;
	}

	stopTune{
		^tuneSynth.release;
	}

	free{
		this.stopTune();
		this.freeSynth();
		controlbus !? controlbus.free;
		^this;
	}
}