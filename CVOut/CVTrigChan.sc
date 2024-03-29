CVTrigDef : AbstractSynthDefSender{

	//TODO: another kr way with synth.set (test works in ar?), compare going into audio rate (in that case triggering is client-side)
	// could be more precise with OffsetOut
	// or just create a synth for each trig?

	initSynthDef{
		var sd;
		CVTrigDef.synthDefName = \CVTrigChan;
		if(this.hasSentDef(CVTrigDef.synthDefName_ar).not, {
			sd = SynthDef(CVTrigDef.synthDefName_ar, {
				Out.ar(\out.kr, Trig1.ar(In.ar(\in.ar), \dur.kr(0.05))*0.9); //TODO: try difference with OffsetOut
			});
			this.sendDef(sd,CVTrigDef.synthDefName_ar);
		});


		if(this.hasSentDef(CVTrigDef.synthDefName_kr).not, {
			sd = SynthDef(CVTrigDef.synthDefName_kr, {
				Out.ar(\out.kr, K2A.ar(Trig1.kr(In.kr(\in.kr), \dur.kr(0.05))*0.9));
			});
			this.sendDef(sd,CVTrigDef.synthDefName_kr);
		});
	}
}

CVTrigChan : CVTrigDef {

	var <rate;
	var <>outbus; //TODO: test if setter works, probably not, make one with synth.set
	var <synth;
	var <controlbus;
	var <dur; //TODO: idem setter + update playRoutine
	var <>playRoutine;

	var <>cvOutGlobal;

	*new { arg server, outbus, dur=0.05;
		^super.new(server).initCVTrigChan(outbus, dur);
	}

	initCVTrigChan{ arg outbusarg, durarg;
		this.initSynthDef();
		cvOutGlobal = CVOutGlobal.serverDict[server];
		outbus = outbusarg;
		dur = durarg;
		cvOutGlobal.cvTrigChanList.add(this); //maybe want to have an attribute which is the position in the list for quicker deleting?
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

	makeSynth_ar{ arg in, durarg;
		this.freeSynth();
		dur = durarg ? dur;
		synth = Synth(CVTrigDef.synthDefName_ar, [out: outbus, in: in, dur: dur], cvOutGlobal.cvOutGroup);
		rate = \ar;
		"Made CVTrigChan ar ".post; outbus.post; " ".post; synth.postln;
		^synth;
	}

	makeSynth_kr{ arg in, durarg;
		var inbus = in ? this.controlbus_();
		this.freeSynth();
		durarg !? {dur = durarg};
		synth = Synth(CVTrigDef.synthDefName_kr, [out: outbus, in: inbus, dur: dur], cvOutGlobal.cvOutGroup);
		rate = \kr;
		playRoutine = this.makePlayRoutine(dur);
		"Made CVTrigChan kr ".post; outbus.post; " ".post; synth.postln;
		^synth;
	}

	makePlayRoutine{|waitDur|
		^Routine({
			(waitDur/2).wait;
			controlbus.setFlex(0);
			nil.yieldAndReset;
		});
	}

	playTrig{|minWait|
		controlbus.setFlex(1);
		^SystemClock.sched(0.0, minWait !? {this.makePlayRoutine(minWait);} ?? {playRoutine;})
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