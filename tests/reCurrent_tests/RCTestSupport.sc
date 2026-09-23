// Shared support for the reCurrent tests.
//
// One permanent TempoClock for the whole suite. Stopping a busy TempoClock
// and dropping its last reference lets sclang garbage-collect the clock
// object while its C++ thread still has a due item, which corrupts memory
// (the item is awoken with a dangling clock). So tests never stop clocks:
// they clear the shared one between tests.

RCTestSupport {
	classvar clock;

	*clock {
		^clock ?? {
			clock = TempoClock.new(20).permanent_(true);
			clock
		}
	}

	// Stop every song (players go away), then drop anything still queued.
	*reset {
		RCSession.reset;
		this.clock.clear;
	}

	// Boot a session on the shared clock, with no hardware side effects.
	*bootSession {
		this.reset;
		^RCSession.boot(Server.default, this.clock, oscPort: nil, initMidi: false)
	}
}
