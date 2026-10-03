// reCording: a bundle for an offline server (its address an RECollectAddr: nothing answers, nothing
// waits) is prepared at once, in the calling thread, so that a NodeProxy source set while an
// REOfflineClock replays a take (a code line of the take) is collected at that moment of the take.
// The core method forks the preparation onto SystemClock at the thread's logical time, which during
// a replay is the offline clock's: the replay runs on without yielding and the score is made before
// that Routine ever runs, so the source change was lost. REScorePlayer fires each event inside
// `server.bind`, where the server's address is a BundleNetAddr around the collector: the preparation
// goes to the collector itself (now), what follows into the open bundle (a latency later). Any other
// server goes the core way (the last lines are SC 3.14's OSCBundle:doPrepare).
+ OSCBundle {
	doPrepare { arg server, onComplete;
		var collector;
		if(preparationMessages.isNil) { ^onComplete.value };
		collector = server.addr;
		while { collector.isKindOf(BundleNetAddr) } { collector = collector.saveAddr };
		if(collector.isKindOf(RECollectAddr)) {
			collector.sync(nil, preparationMessages);
			^onComplete.value
		};
		Routine.run {
			server.sync(Condition.new, preparationMessages);
			onComplete.value;
		};
	}
}
