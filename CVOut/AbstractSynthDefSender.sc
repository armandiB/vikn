AbstractSynthDefSender {
	classvar <sentDefsDict; //server -> IdentitySet(synthDefNames) //IdentityDictionary and IdentitySet for faster inclusion test
	classvar <synthDefDict; //server, name -> SynthDef
	var <server;

	*initClass {
        sentDefsDict = IdentityDictionary.new;
    }

	*new { arg server;
		^super.new.initSynthDefSender(server);
	}

	initSynthDefSender{ arg serverarg;
		server = serverarg;
		sentDefsDict.add(server -> IdentitySet.new);
		this.initSynthDef();
	}

	initSynthDef{}

	addSentDef{ |synthDefName|
		sentDefsDict.at(server).add(synthDefName)
		^sentDefsDict;
	}
	hasSentDef{ |synthDefName|
		^sentDefsDict[server] !? (_.includes(synthDefName)) ?? false
	}

	addSynthDefDict{ arg synthDef;
		//store in Library instead
	}
}