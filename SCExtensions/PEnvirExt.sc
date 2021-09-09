PenvirExt : Penvir {
	var <>proto;
	var <>insertParent;
	var <independentEventsList;  // could have an emptying mecanism
	*new { arg envir, pattern, independent=true, proto, insertParent;
		^super.new(envir, pattern, independent).initPenvirExt(proto, insertParent);
	}

	initPenvirExt {|protoarg, insertParentarg|
		proto = protoarg;
		insertParent = insertParentarg;
		independentEventsList = List();
	}

	storeArgs { ^[envir,pattern,independent,proto] }

	embedInStream { arg inval;
		if(independent)
			{
			var ev = Event.new(8, proto, envir);
			insertParent.notNil.if {ev.insertParent(insertParent, 0, 0)};
			independentEventsList.add(ev);
			ev
		}
			{
			envir.proto = proto;
			insertParent.notNil.if {envir.insertParent(insertParent, 0, 0)};
			envir
		}
		.use { pattern.embedInStream(inval) };
		^inval
	}
}
