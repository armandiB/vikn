// reCording: a bundle for an offline server (its address an RECollectAddr: nothing answers, nothing
// waits) is prepared at once, in the calling thread, so that a NodeProxy source set while an
// REOfflineClock replays a take (a code line of the take) is collected at that moment of the take.
// The core method forks the preparation onto SystemClock at the thread's logical time, which during
// a replay is the offline clock's: the replay runs on without yielding and the score is made before
// that Routine ever runs, so the source change was lost. REScorePlayer fires each event inside
// `server.bind`, where the server's address is a BundleNetAddr around the collector, and a recorder
// recording level 3 puts an RETapAddr in front of it (RECollectAddr.behind follows both): the
// preparation goes behind the bind, to the RETapAddr when there is one (its sync hands the def's
// bytes to the recorder, then reaches the collector at once), else to the collector itself; what
// follows goes into the open bundle (a latency later). Any other server goes the core way (the
// last lines are SC 3.14's OSCBundle:doPrepare).
+ OSCBundle {
	doPrepare { arg server, onComplete;
		var target = server.addr;
		if(preparationMessages.isNil) { ^onComplete.value };
		while { target.isKindOf(BundleNetAddr) } { target = target.saveAddr };
		if(RECollectAddr.behind(target).notNil) {
			target.sync(nil, preparationMessages);
			^onComplete.value
		};
		Routine.run {
			server.sync(Condition.new, preparationMessages);
			onComplete.value;
		};
	}
}
