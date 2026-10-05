// REVisualsTape: what RCVisuals sends recorded with the clock's time (the offline clock's in a
// render), shifted to a start, written and read back as the pages' recording format, played back
// at a given second.
TestREVisualsTape : UnitTest {
	var savedSink, savedEnabled, path;

	setUp {
		savedSink = RCVisuals.sink;
		savedEnabled = RCVisuals.enabled;
		RCVisuals.enabled = true;
		path = PathName.tmp +/+ "re_tape_test_" ++ UniqueID.next ++ ".viz.json";
	}

	tearDown {
		RCVisuals.sink = savedSink;
		RCVisuals.enabled = savedEnabled;
		if(File.exists(path)) { File.delete(path) };
	}

	test_record_on_offline_clock {
		var c = REOfflineClock(tempo: 2);
		var previous = { |p, a| };
		var t;
		RCVisuals.sink = previous;
		t = REVisualsTape.record({ c.seconds });
		this.assert(t.isRecording);
		c.schedAbs(2, { RCVisuals.send('/lsys/clear', 3); nil });                     // at 1 s
		c.schedAbs(2, { RCVisuals.sendIn(0.2, '/lsys/note', [7, 0, 440.0, 0.1]); nil });   // at 1.2 s: on the offline clock
		c.schedAbs(6, { RCVisuals.send('/lsys/hair', 0, \axiom, "F"); nil });          // at 3 s
		c.advanceTo(8);
		this.assertEquals(t.size, 3);
		this.assertEquals(t.entries[0], [1.0, "/lsys/clear", 3], "the clock's seconds, the address as a string");
		this.assertEquals(t.entries[1][0], 1.2, "a delayed send at the time its sound starts");
		this.assertEquals(t.entries[1][1..], ["/lsys/note", 7, 0, 440.0, 0.1]);
		this.assertEquals(t.entries[2], [3.0, "/lsys/hair", 0, "axiom", "F"], "Symbols as strings");
		this.assertEquals(t.duration, 3.0);
		this.assertEquals(t.countBy["/lsys/clear"], 1);
		t.finish;
		this.assert(t.isRecording.not);
		this.assert(RCVisuals.sink === previous, "the previous sink back");
		RCVisuals.send('/lsys/clear', 4);
		this.assertEquals(t.size, 3, "nothing recorded after finish");
		this.assertEquals(t.shifted(1.2).entries.collect(_[0]), [0, 0, 1.8], "shifted to a start, clamped at 0");
	}

	test_write_and_read {
		var t = REVisualsTape.new, r;
		t.add(0.5, '/texture/cycle', [1, 4, 0.5, "point", -1]);
		t.add(0.25, '/lsys/node', [1, 0, -1, 0, 0, 1.5, 0.0, 0.0]);
		t.add(2, '/lsys/hair', [0, 3, "F=F[+F]F\tX", "a \"quoted\" line", 1e-5]);
		t.sort;
		this.assertEquals(t.entries.collect(_[0]), [0.25, 0.5, 2.0], "sorted by time");
		t.write(path);
		this.assert(File.exists(path));
		this.assertEquals(File.readAllString(path).split($\n).reject(_.isEmpty).size, 5, "one entry per line, inside [ and ]");
		r = REVisualsTape.read(path);
		this.assertEquals(r.size, 3);
		this.assertEquals(r.entries[0], [0.25, "/lsys/node", 1, 0, -1, 0, 0, 1.5, 0.0, 0.0], "numbers read back as numbers");
		this.assertEquals(r.entries[1], [0.5, "/texture/cycle", 1, 4, 0.5, "point", -1], "a word stays a string");
		this.assertEquals(r.entries[2][2..], [0, 3, "F=F[+F]F\tX", "a \"quoted\" line", 1e-5], "escapes round trip");
		this.assert(r.entries[0][2].isInteger, "an integer stays an integer");
		this.assert(REVisualsTape.read(path ++ ".nothing").isNil, "a missing file reads as nil");
		this.assertEquals(REVisualsTape.prNumber("12"), 12);
		this.assertEquals(REVisualsTape.prNumber("-0.5e3"), -500.0);
		this.assert(REVisualsTape.prNumber("1.2.3").isNil);
		this.assert(REVisualsTape.prNumber("F").isNil);
	}

	test_play_at_a_second {
		var c = REOfflineClock(tempo: 1);
		var t = REVisualsTape.new, got = List.new, r;
		t.add(0, '/a', [1]);
		t.add(0.5, '/b', [2, "x"]);
		t.add(0.5, '/c', []);
		t.add(2, '/d', [3]);
		c.advanceTo(1);
		r = t.play(10, c, { |p, a| got.add([c.seconds, p, a]) });
		this.assert(t.isPlaying);
		c.advanceTo(9.9);
		this.assertEquals(got.size, 0, "nothing before the start");
		c.advanceTo(10.6);
		this.assertEquals(got.collect(_[0]), [10, 10.5, 10.5], "each entry at the start plus its seconds");
		this.assertEquals(got.collect(_[1]), ["/a", "/b", "/c"]);
		this.assertEquals(got[1][2], [2, "x"], "the arguments after the address");
		this.assertEquals(got[2][2], [], "none");
		c.advanceTo(13);
		this.assertEquals(got.size, 4);
		this.assert(t.isPlaying.not, "done");
		r = t.play(20, c, { |p, a| got.add([c.seconds, p, a]) });
		c.advanceTo(20.1);
		t.stop;
		c.advanceTo(30);
		this.assertEquals(got.size, 5, "stopped after the first entry");
		this.assert(t.isPlaying.not);
	}

	test_default_send_goes_through_rcvisuals {
		var c = REOfflineClock(tempo: 1);
		var t = REVisualsTape.new, got = List.new;
		RCVisuals.sink = { |p, a| got.add([p, a]) };
		t.add(1, '/lsys/gone', [5, 2.0]);
		t.play(0, c);
		c.advanceTo(2);
		this.assertEquals(got.asArray, [['/lsys/gone', [5, 2.0]]], "sent through RCVisuals.sendArgs, the address a Symbol");
	}
}
