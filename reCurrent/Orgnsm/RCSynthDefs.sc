// reCurrent — SynthDef helpers for orgnsms (BlockOrgnsm's add_synthdef_for_orgnsms
// and add_loop_buffer_synthdefs).
//
// An orgnsm sound function returns its signal; addForOrgnsms wraps it in an
// output stage and registers one SynthDef per possible number of outputs
// (name__1_out ... name__<maxNumOuts>_out) so that a note can be spread over
// several fobject buses (\outs / \outamps) chosen per event. SynthDefs are
// added with .add so that patterns know their control names. The sound
// function is evaluated once per variant (maxNumOuts times): keep it pure
// (no Buffer allocation or registration inside it).

RCSynthDefs {
	classvar <>maxNumOuts = 8;   // superimposing fobjects, minus one; keep small (graph size)

	*outputSuffix { |name, numOuts|
		^(name.asString ++ "__" ++ numOuts.asString ++ "_out").asSymbol
	}

	// Returns the names added. A failing sound function is reported, not thrown.
	*addForOrgnsms { |name, soundFunc, offsetOut = false, oneOutputOnly = false, server|
		var max = if(oneOutputOnly) { 1 } { maxNumOuts };
		var names = List.new;
		(1..max).do { |numOuts|
			var defName = this.outputSuffix(name, numOuts);
			var outFunc = this.prOutFunction(numOuts, offsetOut);
			RCGuard.call(\synthdef, nil) {
				SynthDef(defName, {
					var signal = SynthDef.wrap(soundFunc);
					SynthDef.wrap(outFunc, nil, [signal]);
				}).add;
				names.add(defName);
			};
		};
		if(names.size < max) { RCLog.error(\synthdef, "% of % variants of % failed".format(max - names.size, max, name)) };
		^names.asArray
	}

	*prOutFunction { |numOuts, offsetOut|
		^{ |signal|
			var outs, outamps;
			if(numOuts == 1) {
				outs = \outs.kr([0]);
				outamps = \outamps.kr([1]);
				if(offsetOut) { OffsetOut.ar(outs, signal * outamps) } { Out.ar(outs, signal * outamps) };
			} {
				outs = \outs.kr(0 ! numOuts);
				outamps = \outamps.kr([1] ++ (0 ! (numOuts - 1)));
				outs.do { |bus, i|
					if(offsetOut) { OffsetOut.ar(bus, signal * outamps[i]) } { Out.ar(bus, signal * outamps[i]) };
				};
			};
		}
	}

	// write_buffer_<n>chan: RecordBuf from SoundIn, retriggered by \trigger.
	*addLoopBufferWriters { |maxChans = 2|
		^(1..maxChans).collect { |numChans|
			var defName = ("write_buffer_" ++ numChans ++ "chan").asSymbol;
			SynthDef(defName, {
				var in = SoundIn.ar(numChans.collect { |i| \chan_in.ir + i });
				RecordBuf.ar(in, bufnum: \bufnum.kr, offset: 0.0, recLevel: \recLevel.kr(1.0), preLevel: \preLevel.kr(0.0),
					run: 1.0, loop: 0.0, trigger: \trigger.kr(1.0), doneAction: 0);
			}).add;
			defName
		}
	}
}
