AbstractSynthDefSender {
	classvar <sentDefsDict; //server -> IdentitySet(synthDefNames) //IdentityDictionary and IdentitySet for faster inclusion test
	classvar <synthDefDict; //server, name -> SynthDef, not functional
	var <server;

	classvar <synthDefName;

	*initClass {
        sentDefsDict = IdentityDictionary.new;
    }

	*synthDefName_ar{
		^(synthDefName.asString++"_ar").asSymbol;
	}

	*synthDefName_kr{
		^(synthDefName.asString++"_kr").asSymbol;
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
		^sentDefsDict[server] !? {_.includes(synthDefName)}.value ?? false
	}

	sendDef{|sd, name|
			sd.send(server);
			this.addSentDef(name);
			this.addSynthDefDict(sd);
	}

	addSynthDefDict{ arg synthDef;
		//store in Library instead
	}
}