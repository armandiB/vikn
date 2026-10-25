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
		REVisualsTape.clearCache;
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

	// from: the entries before it skipped, the rest still at the start plus their own seconds
	// (the start is the tape's 0, in the past when played from later: the Routine begins at
	// start + from)
	test_play_from_a_later_second {
		var c = REOfflineClock(tempo: 1);
		var t = REVisualsTape.new, got = List.new;
		t.add(0, '/a', [1]);
		t.add(0.5, '/b', [2]);
		t.add(2, '/c', [3]);
		c.advanceTo(1);
		t.play(10, c, { |p, a| got.add([c.seconds, p]) }, from: 0.5);
		c.advanceTo(10.4);
		this.assertEquals(got.size, 0, "nothing at the tape's 0: the entry there is before `from`");
		c.advanceTo(13);
		this.assertEquals(got.collect(_[0]), [10.5, 12], "each kept entry at the start plus its own seconds");
		this.assertEquals(got.collect(_[1]), ["/b", "/c"]);
		got.clear;
		c.advanceTo(20);
		t.play(nil, c, { |p, a| got.add([c.seconds, p]) }, from: 2);   // atSecs nil: the tape's 0 is now
		c.advanceTo(23);
		this.assertEquals(got.collect(_[0]), [22], "now plus the entry's seconds");
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

	// a tape of two taps' frames at 100 a second for a second, and notes
	prFrames {
		var t = REVisualsTape.new;
		100.do { |i| [0, 32].do { |start| t.add(i / 100, '/lsys/state', [start, 0.5, 0.25]) } };
		10.do { |i| t.add(i / 10 + 0.005, '/lsys/note', [i, 0, 440.0, 0.1]) };
		^t.sort.write(path)
	}

	test_true_false_null_and_tiny_floats {
		var t = REVisualsTape.new, r;
		t.add(0, '/x', [true, false, nil, 1.5e-07, -2.5, 440.0, 7]);
		t.write(path);
		r = REVisualsTape.read(path);
		this.assertEquals(r.entries[0], [0.0, "/x", true, false, nil, 1.5e-07, -2.5, 440.0, 7], "null read back as nil, the rest as written");
		this.assert(r.entries[0][8].isInteger and: { r.entries[0][5].isFloat });
	}

	test_skip_and_rate {
		var r, starts;
		this.prFrames;
		r = REVisualsTape.read(path, skip: ['/lsys/note']);
		this.assertEquals(r.countBy["/lsys/note"], nil, "skipped");
		this.assertEquals(r.countBy["/lsys/state"], 200, "the rest kept");
		r = REVisualsTape.read(path, rate: ('/lsys/state': 10));
		starts = r.entries.select { |e| e[1] == "/lsys/state" }.collect(_[2]);
		this.assert(starts.occurrencesOf(0).inclusivelyBetween(9, 11) and: { starts.occurrencesOf(32).inclusivelyBetween(9, 11) },
			"10 frames a second per tap, both taps: % and %".format(starts.occurrencesOf(0), starts.occurrencesOf(32)));
		this.assertEquals(r.countBy["/lsys/note"], 10, "another address untouched");
		this.assertEquals(r.entries.collect(_[0]), r.entries.collect(_[0]).copy.sort, "still in time order");
	}

	// a tape on one line with JSON's \u escapes (as Python's json.dumps writes): read through parseJSON
	test_one_line_and_unicode_escapes {
		var r;
		File.use(path, "w", { |f| f.write("[[0.0,\"/a\",\"caf\\u00e9\",1],[0.5,\"/b\",2.5,-3]]") });
		r = REVisualsTape.read(path);
		this.assertEquals(r.size, 2);
		this.assertEquals(r.entries[0], [0.0, "/a", "café", 1], "the escape decoded, the numbers typed");
		this.assertEquals(r.entries[1], [0.5, "/b", 2.5, -3]);
	}

	test_not_a_tape {
		File.use(path, "w", { |f| f.write("hello") });
		this.assert(REVisualsTape.read(path).isNil, "no array: nil");
		this.assert(REVisualsTape.read(path ++ ".nothing").isNil, "no file: nil");
	}

	test_cache {
		var reads, a, b, c;
		this.prFrames;
		reads = REVisualsTape.reads;
		a = REVisualsTape.read(path);
		b = REVisualsTape.read(path);
		this.assertEquals(REVisualsTape.reads, reads + 1, "read once, then from the cache");
		this.assert(a !== b and: { a.entries == b.entries } and: { a.entries !== b.entries }, "a tape of its own each time, the same entries");
		b.entries.add([9.0, "/x"]);
		this.assertEquals(REVisualsTape.read(path).size, a.size, "what a reader adds stays its own");
		REVisualsTape.read(path, skip: ['/lsys/note']);
		this.assertEquals(REVisualsTape.reads, reads + 2, "other options: read again");
		REVisualsTape.new.add(0, '/y', [1]).write(path);
		c = REVisualsTape.read(path);
		this.assertEquals(c.size, 1, "the file changed: read again");
	}

	test_load_yields_and_answers {
		var got, done = false, ticks = 0, ticker, t0 = Main.elapsedTime;
		var saved = REVisualsTape.chunkBytes;
		this.prFrames;
		REVisualsTape.chunkBytes = 200;   // many pieces: a yield between each
		ticker = Routine { loop { ticks = ticks + 1; 0.001.wait } }.play(SystemClock);
		REVisualsTape.load(path, action: { |t| got = t; done = true });
		this.assert(done.not, "not read at once");
		while { done.not and: { (Main.elapsedTime - t0) < 5 } } { 0.02.wait };
		ticker.stop;
		REVisualsTape.chunkBytes = saved;
		this.assert(done, "the action called");
		this.assert(ticks > 2, "other tasks ran meanwhile: % ticks".format(ticks));
		this.assertEquals(got.size, 210, "the whole tape");
		this.assertEquals(got.entries, REVisualsTape.read(path).entries, "the same as read");
	}
}
