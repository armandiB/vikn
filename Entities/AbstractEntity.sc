AbstractEntity {
	var <server;

	*new{|server|
		^super.new().initAbstractEntity(server);
	}
	initAbstractEntity{|serverarg|
		server = serverarg;
	}

	makeSynthDefName{}
	makeSynthDef{}

	mergeAdd {}

	spawn {}

}

AbstractEntitySynthDefSender : AbstractSynthDefSender {
	var entity;

	*new{|server, entity|
		^super.newNoInitSynthDef(server).initAbstractEntitySynthDefSender(entity);
	}
	initAbstractEntitySynthDefSender{|entityarg|
		entity = entityarg;
		this.initSynthDef();
	}

	initSynthDef{
		^this.sendDef(entity.makeSynthDef());
	}
}