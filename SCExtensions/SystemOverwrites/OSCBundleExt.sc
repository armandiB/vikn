// reCording: a bundle for an offline server (its address an RECollectAddr: nothing answers, nothing
// waits) is prepared at once, in the calling thread, so that a NodeProxy source set while an
// REOfflineClock replays a take (a code line of the take) is collected at that moment of the take.
// The core method forks the preparation onto SystemClock at the thread's logical time, which during
// a replay is the offline clock's: the replay runs on without yielding and the score is made before
// that Routine ever runs, so the source change was lost. Any other server goes the core way (the
// body below is SC 3.14's OSCBundle:doPrepare).
+ OSCBundle {
	doPrepare { arg server, onComplete;
		if(preparationMessages.isNil) { ^onComplete.value };
		if(server.addr.isKindOf(RECollectAddr)) {
			server.sync(nil, preparationMessages);
			^onComplete.value
		};
		Routine.run {
			server.sync(Condition.new, preparationMessages);
			onComplete.value;
		};
	}
}
