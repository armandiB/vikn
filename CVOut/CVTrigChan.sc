CVTrigDef : AbstractSynthDefSender{

	classvar <synthDefName_ar = \CVTrigChan_ar;
	classvar <synthDefName_kr = \CVTrigChan_kr;

	initSynthDef{
		var sd;
		if(this.hasSentDef(synthDefName_ar).not, {
			sd = SynthDef(synthDefName_ar, {
				Out.ar(\out.kr, Trig1.ar(In.ar(\in.ar), \dur.kr(0.05))*0.9); //TODO: try difference with OffsetOut
			});
			sd.send(server);
			this.addSentDef(synthDefName_ar);
			this.addSynthDefDict(sd);
		});


		if(this.hasSentDef(synthDefName_kr).not, {
			sd = SynthDef(synthDefName_kr, {
				Out.ar(\out.kr, K2A.ar(Trig1.kr(In.kr(\in.kr), \dur.kr(0.05))*0.9));
		});
		sd.send(server);
		this.addSentDef(synthDefName_kr);
		this.addSynthDefDict(sd);
		});
	}
}

CVTrigChan : CVTrigDef {

	var <rate;
	var <>outbus; //TODO: test if setter works, probably not, make one with synth.set
	var <synth;
	var <controlbus;
	var <dur; //TODO: idem setter

	*new { arg server, outbus;
		^super.new(server).initCVTrigChan(outbus);
	}

	initCVTrigChan{ arg outbusarg;
		outbus = outbusarg;
		this.initSynthDef();
		CVOutGlobal.cvTrigChanList.add(this); //maybe want to have an attribute which is the position in the list for quicker deleting?
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
		synth = Synth(synthDefName_ar, [out: outbus, in: in, dur: dur], CVOutGlobal.cvOutGroupDict[server]);
		rate = \ar
		^synth
	}

	makeSynth_kr{ arg in, argdur;
		var inbus = in ? this.controlbus_();
		synth !? this.freeSynth();
		argdur !? {dur = argdur};
		synth = Synth(synthDefName_kr, [out: outbus, in: inbus, dur: dur], CVOutGlobal.cvOutGroupDict[server]);
		rate = \kr
		^synth
	}

	freeSynth{
		synth.free;
	}
}