CVOutGlobal{
	classvar <serverList; //list as a check to only one CVOutGlobal per server
	classvar <cvOutGroupDict; //server -> group
	classvar <cvTrigChanList; //list for easier retrieval
	var <server;

	*initClass {
		serverList = List.new;
        cvOutGroupDict = IdentityDictionary.new;
		cvTrigChanList = List.new;
    }

	*new { arg server;
		^super.new.initCVOutGlobal(server);
	}

	initCVOutGlobal{ arg serverarg;
		server = serverarg;
		serverList.add(server);
		this.initServerObjects(server);
	}

	//TODO: recreate Group in case of Cmd+. (see permanent clock). Thinks Group exists but actually not. Also see serverList. To fix, doesn't work if server killed and rebooted
	initServerObjects{
		cvOutGroupDict.at(server) ?? {cvOutGroupDict.add(server -> Group.after(server.volume.ampSynth))};
	}

}
