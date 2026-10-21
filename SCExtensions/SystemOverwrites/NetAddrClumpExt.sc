// reCording: NetAddr:sendClumpedBundles measures the whole bundle first (bundleSize), and the primitive behind
// it builds the packet in the same 64 KB buffer a send uses: past 65536 bytes it prints "makeSynthMsgWithTags:
// buffer overflow" and gives the size that fitted (under the 65535 threshold), so the bundle is sent whole,
// overflows again and goes out truncated: the server fails on the /d_recv it was cut through ("/d_recv
// failed", then "ecv Command not found" as it reads on). REScorePlayer fires a take's line inside server.bind,
// where everything the line sends is one bundle, so a line that adds eleven SynthDefs (Symbolic's ambient
// section, 66 KB, 2026-10-21) hit it. Here the size is the sum of the messages' own, each measured alone and
// well under the buffer; the clumps follow (clumpBundles measures per message too). The rest is SC 3.14's.
+ NetAddr {
	sendClumpedBundles { arg time ... args;
		var size = 0;
		args.do { |m| size = size + [m].bundleSize };
		if(size > 65535) {   // udp max size
			args.clumpBundles.do { |item|
				if(time.notNil) { time = time + 1e-9 };   // make it a nanosecond later
				this.sendBundle(time, *item)
			};
		} {
			this.sendBundle(time, *args)
		}
	}
}
