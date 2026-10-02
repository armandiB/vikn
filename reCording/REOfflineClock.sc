// reCording — a clock that runs without time: what a song schedules on it (its beats'
// players, the Routines, a replay) is run by stepping, so that a take renders offline: the
// server messages it sends go to an RECollectAddr, timed by this clock, into a Score.
//
//   c = REOfflineClock(tempo: 2);
//   a = RECollectAddr("127.0.0.1", 57999, c);
//   s = Server(\offline, a, options);                 // never booted: every message is collected
//   session = RCSession.boot(s, c, oscPort: 32397);   // the song's clock
//   ... build the song, play a take on it (REScorePlayer on c) ...
//   c.advanceTo(64);                                 // run the first 64 beats
//   Score(defs ++ a.bundles).recordNRT(...);
//
// The clock keeps the TempoClock interface the reCurrent classes and the streams use
// (beats, seconds, tempo, beatDur, sched, schedAbs, play, nextTimeOnGrid, timeToNextBeat,
// beats2secs, secs2beats, beatsPerBar, latency, permanent, isRunning, clear, stop). While an
// item runs, the main thread's clock, beats and seconds are this clock's, as AppClock does
// through its Scheduler: a Routine resumed then inherits them, and `n.wait` reschedules it here.
// Not a replacement for a live clock: nothing runs until advanceTo is called.

REOfflineClock {
	var <tempo, <beats = 0.0, baseBeats = 0.0, baseSeconds = 0.0, queue;
	var <>latency = 0.2, <>permanent = true, <beatsPerBar = 4.0, <baseBarBeat = 0.0, <baseBar = 0.0;
	var <running = true, <wakeups = 0;

	*new { |tempo = 1.0| ^super.new.initREOfflineClock(tempo) }

	initREOfflineClock { |t|
		tempo = t;
		queue = PriorityQueue.new;
	}

	//////// time
	seconds { ^this.beats2secs(beats) }
	beats2secs { |b| ^((b - baseBeats) / tempo) + baseSeconds }
	secs2beats { |s| ^((s - baseSeconds) * tempo) + baseBeats }
	beatDur { ^1 / tempo }
	elapsedBeats { ^beats }
	tempo_ { |t|
		baseSeconds = this.seconds;
		baseBeats = beats;
		tempo = t;
	}
	beatsPerBar_ { |n| baseBarBeat = beats; baseBar = this.beats2bars(beats); beatsPerBar = n }
	beats2bars { |b| ^((b - baseBarBeat) / beatsPerBar) + baseBar }
	bars2beats { |bars| ^((bars - baseBar) * beatsPerBar) + baseBarBeat }
	bar { ^this.beats2bars(beats).floor }
	nextBar { |beat| ^this.bars2beats(this.beats2bars(beat ? beats).ceil) }
	isRunning { ^running }
	queueSize { ^inf }

	nextTimeOnGrid { |quant = 1, phase = 0, referenceBeat|
		referenceBeat = referenceBeat ? beats;
		if(quant == 0) { ^referenceBeat + phase };
		if(quant < 0) { quant = beatsPerBar * quant.neg };
		if(phase < 0) { phase = phase % quant };
		^roundUp(referenceBeat - baseBarBeat - (phase % quant), quant) + baseBarBeat + phase
	}
	timeToNextBeat { |quant = 1| ^quant.asQuant.nextTimeOnGrid(this) - beats }

	//////// scheduling (what TempoClock offers; the items are awoken by advanceTo)
	sched { |delta, item| if(delta.notNil) { queue.put(beats + delta, item) } }
	schedAbs { |beat, item| queue.put(beat, item) }
	play { |task, quant = 1| this.schedAbs(quant.asQuant.nextTimeOnGrid(this), task) }
	playNextBar { |task| this.schedAbs(this.nextBar, task) }
	clear { queue.do { |x| x.removedFromScheduler }; queue.clear }
	stop { this.clear; running = false }
	isEmpty { ^queue.isEmpty }
	nextDue { ^queue.topPriority }

	//////// running
	// Runs everything due up to `target` (included), in order, then stands at target. Each item
	// is awoken as a TempoClock would: the main thread on this clock at the item's beat, the
	// item's return value (a delta) rescheduling it.
	advanceTo { |target|
		var thread = thisThread;
		var saveClock = thread.clock, saveSeconds = thread.seconds;
		var t, item, delta;
		thread.clock = this;
		while { queue.topPriority.notNil and: { queue.topPriority <= target } } {
			t = queue.topPriority;
			item = queue.pop;
			beats = t;
			thread.beats = t;
			delta = RCGuard.call(\offline, nil) { item.awake(t, this.beats2secs(t), this) };
			wakeups = wakeups + 1;
			if(delta.isNumber) { queue.put(t + delta, item) };
		};
		beats = target;
		thread.beats = target;
		thread.clock = saveClock;
		thread.seconds = saveSeconds;
	}

	advance { |deltaBeats| this.advanceTo(beats + deltaBeats) }

	printOn { |stream| stream << "REOfflineClock(tempo " << tempo << ", beat " << beats.round(0.001) << ", " << queue.size << " queued)" }
}

// A NetAddr that keeps what is sent to it with the offline clock's time, instead of sending:
// bundles as [seconds, msg, msg...] for a Score (a bundle's latency is added to the clock's
// seconds), ready for Score.recordNRT.
RECollectAddr : NetAddr {
	var <bundles, <>clock;

	*new { |hostname = "127.0.0.1", port = 57999, clock|
		^super.new(hostname, port).initRECollectAddr(clock)
	}

	initRECollectAddr { |c|
		clock = c;
		bundles = List.new;
	}

	now { ^clock !? (_.seconds) ? 0 }
	sendMsg { |... msg| bundles.add([this.now] ++ [msg]) }
	sendBundle { |time ... msgs| bundles.add([this.now + (time ? 0)] ++ msgs) }
	listSendMsg { |msg| bundles.add([this.now, msg]) }
	listSendBundle { |time, msgs| bundles.add([this.now + (time ? 0)] ++ msgs) }
	sendRaw { |rawArray| }
	sendClumpedBundles { |time ... msgs| this.sendBundle(time, *msgs) }
	sendStatusMsg { }

	// Score lines sorted by time (each bundle's messages kept together); `defs` go first.
	score { |defs|
		var lines = (defs ? []).collect { |d| [0.0, ['/d_recv', d.asBytes]] };
		^Score(lines ++ bundles.asArray.sort { |a, b| a[0] <= b[0] })
	}

	clear { bundles.clear }
	printOn { |stream| stream << "RECollectAddr(" << bundles.size << " bundles)" }
}
