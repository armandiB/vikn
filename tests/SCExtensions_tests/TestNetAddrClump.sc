// The clumping of a bundle past the 64 KB packet buffer (SCExtensions/SystemOverwrites/NetAddrClumpExt.sc): an
// address that keeps what it would send, six messages of 20 KB and ten small ones sent clumped: several packets,
// each under 65535 bytes, every message once; a small bundle whole.
TestNetAddrClump : UnitTest {
	test_clumps_past_the_buffer {
		var sent = List.new, addr = NetAddrRecorder.new("127.0.0.1", 57999, sent), blob = Int8Array.newClear(20000);
		var big = 6.collect { ["/d_recv", blob] }, small = 10.collect { |i| ["/n_set", 1000 + i, "amp", 0.1] }, msgs = big ++ small;
		addr.sendClumpedBundles(0.2, *msgs);
		this.assert(sent.size >= 2, "a bundle of six 20 KB messages goes out in several packets (%)".format(sent.size));
		this.assert(sent.every { |b| b[1..].bundleSize < 65535 }, "every packet under 65535 bytes (%)".format(sent.collect { |b| b[1..].bundleSize }));
		this.assertEquals(sent.collect { |b| b.size - 1 }.sum, msgs.size, "every message sent once");   // a packet: its time, then its messages
		this.assert(sent.collect(_[0]).every { |t| t >= 0.2 }, "the packets at the bundle's time or a nanosecond after");
		sent.clear;
		addr.sendClumpedBundles(0.2, ["/n_set", 1000, "amp", 0.1], ["/n_set", 1001, "amp", 0.2]);
		this.assert(sent.size == 1 and: { sent[0].size == 3 }, "a small bundle goes out whole (its time and its two messages)");
	}
}

// An address that keeps the bundles it would send: [time, msg, msg ...] each (for the tests).
NetAddrRecorder : NetAddr {
	var <sent;
	*new { |hostname, port, list| ^super.new(hostname, port).prInitRecorder(list) }
	prInitRecorder { |list| sent = list ?? { List.new } }
	sendBundle { |time ... msgs| sent.add([time] ++ msgs) }
}
