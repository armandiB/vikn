+Pavaroh {

	embedInStream { arg inval;
		var me, melast = 0, scale;
		var mestream = pattern.asStream;
		var stepsStr = stepsPerOctave.asStream, stepVal;
		var arohStream = aroh.asStream;
		var avarohStream = avaroh.asStream;
		var arohNext, avarohNext;

		while {
			stepVal = stepsStr.next(inval);
			me = mestream.next(inval);
			arohNext = arohStream.next(inval);
			avarohNext = avarohStream.next(inval);
			me.notNil and: { stepVal.notNil }
		} {
			scale = if(me >= melast) { arohNext } { avarohNext };
			melast = me;
			inval = me.degreeToKey(scale, stepVal).yield
		};
		^inval
	}
}
