+Bus {
	setFlex { |val|
		if(server.hasShmInterface,
			{^this.setSynchronous(val)},
			{^this.set(val)});
	}

	getFlex {
		if(server.hasShmInterface,
			{^this.getSynchronous},
			{^this.get});
	}

	addFlex { |val|
		^this.setFlex(val+this.getFlex);
	}
}
