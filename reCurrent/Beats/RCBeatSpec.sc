// reCurrent — a prepared beat: everything needed to start it, without starting.
//
// BlockBeats returned "a function that starts the pattern when calling
// .value"; an RCBeatSpec is that function as an object. .start (or .value)
// creates and plays the RCBeat in its layer and returns it.

RCBeatSpec {
	var <layer, <name, <attrDict, <chan, <seeds, <addFirst, <addFirstSeeds, <terminationKey, <>killFunc;
	var <beat;

	*new { |layer, name, attrDict, chan, seeds, addFirst, addFirstSeeds, terminationKey, killFunc|
		^super.newCopyArgs(layer, name.asSymbol, attrDict, chan, seeds, addFirst, addFirstSeeds, terminationKey, killFunc)
	}

	start { |quant|
		beat = layer.addBeat(name, attrDict, chan: chan, seeds: seeds, addFirst: addFirst,
			addFirstSeeds: addFirstSeeds, terminationKey: terminationKey, quant: quant);
		^beat
	}

	value { ^this.start }

	kill {
		layer.deleteBeat(name);
		killFunc.value(this);
	}

	printOn { |stream|
		stream << "RCBeatSpec(" << layer.songName << "/" << layer.key << "/" << name << ")"
	}
}
