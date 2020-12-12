//

ClockTree {

	var <>dummy;

	*new { arg dummy;
		^super.new.initClockTree(dummy);
	}

	initClockTree{ arg dummyarg;
		dummy = dummyarg;
	}

}