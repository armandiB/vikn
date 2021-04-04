CVOutGlobal{
	classvar <serverList; //list as a check to only one CVOutGlobal per server
	classvar <serverDict; //holder of all instances, could replace serverList if unicity guaranteed

	var <server;
	var <cvOutGroup;
	var <cvTrigChanList;
	var <cvDCChanList;
	var <cvVoctChanList; //indexes in cvDCChanList relative to this server, assuming cvDCChanList only adds elements

	var <>interface_factor;
	var <>basefreq;
	var <>tuningchan;

	*initClass {
		serverList = List.new;
		serverDict = IdentityDictionary.new;
    }

	*new { arg server, tuningchan, basefreq=440, interface_factor=0.1;
		^super.new.initCVOutGlobal(server, tuningchan, basefreq, interface_factor);
	}

	initCVOutGlobal{ arg serverarg, tuningchanarg, basefreqarg, interface_factorarg;
		server = serverarg;
		tuningchan = tuningchanarg;
		basefreq = basefreqarg;
		interface_factor = interface_factorarg;
		serverList.add(server);
		serverDict.add(server -> this);
		cvTrigChanList = List.new;
		cvDCChanList = List.new;
		cvVoctChanList = List.new;
		this.initServerObjects(server);
	}

	//TODO: recreate Group in case of Cmd+. (see permanent clock). Thinks Group exists but actually not. Also see serverList. To fix, doesn't work if server killed and rebooted
	initServerObjects{
		cvOutGroup ?? {cvOutGroup = Group.after(server.volume.ampSynth)};
	}

}
