AbstractSynthDefSender {
	classvar <synthDefDict; //keys: server, name
	classvar <hasSentSynthDefs;
	var <server;
	*new { arg server;
		^super.new.initSynthDefSender(server);
	}

	initSynthDefSender{ arg serverarg;
		server = serverarg;
		hasSentSynthDefs = Set();
		this.initSynthDef();
	}

	initSynthDef{}

	addToSynthDefDict{ arg synthDef;
		//store in Library instead
	}
}

CVTrigDef : AbstractSynthDefSender{

	classvar <synthDefName_ar = \CVTrigChan_ar;
	classvar <synthDefName_kr = \CVTrigChan_kr;

	initSynthDef{
		var sd;
		if(hasSentSynthDefs.includes(server), {^nil});
		sd = SynthDef(synthDefName_ar, {|outbus, in = 0, dur = 0.1|
			Out.ar(outbus, Trig1.ar(In.ar(in), dur)*0.9); //TODO: try difference with OffsetOut
		});
		sd.send(server);
		this.addToSynthDefDict(sd);

		if(hasSentSynthDefs.includes(server), {^nil});
		sd = SynthDef(synthDefName_kr, {|outbus, in = 0, dur = 0.1|
			Out.ar(outbus, K2A.ar(Trig1.kr(In.kr(in), dur)*0.9));
		});
		sd.send(server);
		this.addToSynthDefDict(sd);
		hasSentSynthDefs.add(server);
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
		synth = Synth(synthDefName_ar, [outbus: outbus, in: in, dur: dur], CVOutGlobal.cvOutGroup);
		rate = \ar
		^synth
	}

	makeSynth_kr{ arg in, argdur;
		var inbus = in ? this.controlbus_();
		synth !? this.freeSynth();
		argdur !? {dur = argdur};
		synth = Synth(synthDefName_kr, [outbus: outbus, in: inbus, dur: dur], CVOutGlobal.cvOutGroup);
		rate = \kr
		^synth
	}

	freeSynth{
		synth.free;
	}
}