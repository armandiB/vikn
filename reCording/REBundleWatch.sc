// reCording — (Nov 6 2026) a server's address left inside a bundle, found and recovered.
//
// server.bind swaps the server's address for a BundleNetAddr until the bundle closes. A Routine that waits
// or stops itself inside it never closes it, and from then on every message sclang sends to that server is
// kept and never sent: silence, and the IDE's numbers frozen (ServerStatusWatcher stops updating while the
// address has a bundle). That is what a replayed `~rec.stop` did to FrissonFixed's passes on Nov 6
// (133286 messages held). SC's status watcher says so every 0.7 s while it lasts (server.changed(\bundling));
// sclang runs one thread at a time, so the same bundle still open at the next of those notices belongs to a
// Routine suspended inside it, which will not close it. Its messages are dropped (sent late they would all
// sound at once) and the address recovered, loudly.
//
//   REBundleWatch.watch(server);   // REScorePlayer.play watches its song's server
//   REBundleWatch.check(server);   // one look (what the notice runs): true when it recovered the address

REBundleWatch {
	classvar <watched, seen;
	classvar <>recovers = true;   // false: only posted

	*initClass {
		watched = IdentitySet.new;
		seen = IdentityDictionary.new;
	}

	*watch { |server|
		if(server.isNil or: { watched.includes(server) }) { ^this };
		watched.add(server);
		server.addDependant(this);
	}

	*unwatch { |server|
		server !? {
			watched.remove(server);
			seen.removeAt(server);
			server.removeDependant(this);
		};
	}

	*update { |server, what|
		if(what == \bundling) { this.check(server) };
	}

	*check { |server|
		var a = server.addr, last = seen[server];
		if(a.hasBundle.not) { seen.removeAt(server); ^false };
		if(last.isNil or: { last[\addr] !== a }) {
			seen[server] = IdentityDictionary[\addr -> a, \since -> Main.elapsedTime];
			^false
		};
		seen.removeAt(server);
		RCLog.error(\bundle, ("% has been inside one bundle for % s with % message(s) held: a Routine waited or stopped "
			++ "itself inside server.bind (a replayed line?) and will not close it; %").format(server.name,
			(Main.elapsedTime - last[\since]).round(0.1), a.bundle.size,
			if(recovers) { "they are dropped and the address recovered" } { "nothing sent to that server reaches it" }), true);
		if(recovers) { server.addr = a.recover };
		^recovers
	}
}
