CVOutGlobal {
	var <server;
	var <cvOutGroup;

	*new { arg server;
		^super.new.initCVOutGlobal(server);
	}

	initCVOutGlobal{ arg serverarg;
		server = serverarg;
		this.initServerObjects();
	}

	//TODO: recreate Group in case of Cmd+.
	initServerObjects{
		cvOutGroup = Group.after(server.volume.ampSynth)
	}

}
