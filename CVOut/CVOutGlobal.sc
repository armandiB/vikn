CVOutGlobal{
	classvar <serverList; //list as a check to only one CVOutGlobal per server
	classvar <serverDict; //holder of all instances, could replace serverList if unicity guaranteed

	classvar <cvOutGroupDict; //server -> group
	classvar <cvTrigChanList; //list for easier retrieval
	classvar <cvDCChanList;
	var <server;
	var <cvVOctChanIndexes; //indexes in cvDCChanList relative to this server, to change interface_factor there if it changes here

	var <interface_factor;
	var <>basefreq;

	*initClass {
		serverList = List.new;
		serverDict = IdentityDictionary.new;
        cvOutGroupDict = IdentityDictionary.new;
		cvTrigChanList = List.new;
		cvDCChanList = List.new;
    }

	*new { arg server, basefreq=440, interface_factor=0.1;
		^super.new.initCVOutGlobal(server, basefreq, interface_factor);
	}

	initCVOutGlobal{ arg serverarg, basefreqarg, interface_factorarg;
		server = serverarg;
		basefreq = basefreqarg;
		interface_factor = interface_factorarg;
		serverList.add(server);
		serverDict.add(server -> this);
		cvVOctChanIndexes = List.new;
		this.initServerObjects(server);
	}

	//TODO: recreate Group in case of Cmd+. (see permanent clock). Thinks Group exists but actually not. Also see serverList. To fix, doesn't work if server killed and rebooted
	initServerObjects{
		cvOutGroupDict.at(server) ?? {cvOutGroupDict.add(server -> Group.after(server.volume.ampSynth))};
	}

	interface_factor_{|fac|
		interface_factor = fac;
		//cvVOctChanIndexes.do({|idx| cvDCChanList[idx].interface_factor = fac}); //instead use that to recompute CVs for output
		^interface_factor;
	}

}
