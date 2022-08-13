AbstractSynthDefSender {
	classvar <sentDefsDict; //server -> IdentitySet(synthDefNames) //IdentityDictionary and IdentitySet for faster inclusion test
	classvar <synthDefDict; //server, name -> SynthDef, not functional
	var <server;

	classvar <>synthDefName;

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
		^super.new.initSynthDefSender(server, true);
	}
	*newNoInitSynthDef { arg server;
		^super.new.initSynthDefSender(server, false);
	}

	initSynthDefSender{ arg serverarg, initSynthDef=true;
		server = serverarg;
		if(sentDefsDict.includesKey(server).not) {sentDefsDict.add(server -> IdentitySet.new)};
		if(initSynthDef) {this.initSynthDef()};
	}

	initSynthDef{}

	addSentDef{ |synthDefNamearg|
		sentDefsDict.at(server).add(synthDefNamearg)
		^sentDefsDict;
	}
	hasSentDef{ |synthDefNamearg|
		^sentDefsDict[server] !? {_.includes(synthDefNamearg)}.value ?? false
	}

	sendDef{|sd, name|
		if(name.isNil){name = sd.name};
		if(this.hasSentDef.not, {
			sd.send(server);
			this.addSentDef(name);
			this.addSynthDefDict(sd);
		});
	}

	sendDefList{|defList|
		^defList.do(this.sendDef(_));
	}

	addSynthDefDict{ arg synthDef;
		//store in Library instead
	}
}