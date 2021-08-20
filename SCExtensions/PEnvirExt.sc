PenvirExt : Penvir {
	var <>proto;
	var <independentEventsList;  // could have an emptying mecanism
	*new { arg envir, pattern, independent=true, proto;
		^super.new(envir, pattern, independent).initPenvirExt(proto);
	}

	initPenvirExt {|protoarg|
		proto = protoarg;
		independentEventsList = List();
	}

	storeArgs { ^[envir,pattern,independent,proto] }

	embedInStream { arg inval;
		if(independent)
			{
			var ev = Event.new(8, proto, envir);
			independentEventsList.add(ev);
			ev
		}
			{
			envir.proto = proto;
			envir
		}
		.use { pattern.embedInStream(inval) };
		^inval
	}
}
