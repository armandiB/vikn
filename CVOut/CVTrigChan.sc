AbstractSynthDefSender {
	classvar synthDefDict;
	var <server;
	var <hasSentSynthDef = false; //TODO: classvars hasSentSynthDef_ar hasSentSynthDef_kr ??

	*new { arg server;
		^super.new.initSynthDefSender(server);
	}

	initSynthDefSender{ arg serverarg;
		server = serverarg;
		this.initSynthDef();
	}

	initSynthDef{}

	addToSynthDefDict{ arg synthDef;
		//store in Library instead
	}
}

CVTrigDef : AbstractSynthDefSender{

	classvar synthDefName_ar = \CVTrigChan_ar;
	classvar synthDefName_kr = \CVTrigChan_kr;

	initSynthDef{
		var sd;
		if(hasSentSynthDef, {^nil});
		sd = SynthDef(synthDefName_ar, {|outbus, in = 0, dur = 0.1|
			Out.ar(outbus, Trig1.ar(In.ar(in), dur)*0.9);
		});
		sd.send(server);
		this.addToSynthDefDict(sd);

		if(hasSentSynthDef, {^nil});
		sd = SynthDef(synthDefName_kr, {|outbus, in = 0, dur = 0.1| //TODO: Out.kr will never work bc audio bus out
			Out.kr(outbus, Trig1.kr(In.kr(in), dur)*0.9);
		});
		sd.send(server);
		this.addToSynthDefDict(sd);
		hasSentSynthDef = true;
	}
}

CVTrigChan : CVTrigDef {

	var <>outbus;
	var <synth;

	*new { arg server, outbus;
		^super.new(server).initCVTrigChan(outbus);
	}

	initCVTrigChan{ arg outbusarg;
		outbus = outbusarg;
		this.initSynthDef();
	}

	makeSynth_ar{ arg in, dur;
		synth !? this.freeSynth();
		synth = Synth(synthDefName_ar, [outbus: outbus, in: in, dur: dur], in, \addAfter);
	}

	makeSynth_kr{ arg in, dur;
		synth !? this.freeSynth();
		synth = Synth(synthDefName_kr, [outbus: outbus, in: in, dur: dur], in, \addAfter);
	}

	freeSynth{
		synth.free;
	}
}