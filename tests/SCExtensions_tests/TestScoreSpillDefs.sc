// Score:spillDefs: a def too large for one OSC packet goes to a file and is loaded by /d_load; a small
// one stays inline; a line whose defs together exceed the limit spills its largest; the file holds the
// def's bytes exactly; every line of the spilled score encodes whole (no 32-byte stub).
TestScoreSpillDefs : UnitTest {
	var dir;

	setUp { dir = PathName.tmp +/+ ("vikn_spill_" ++ UniqueID.next) }
	tearDown { if(File.exists(dir)) { (dir +/+ "*.scsyndef").pathMatch.do(File.delete(_)); File.delete(dir) } }

	readBytes { |path|
		^File.use(path, "rb", { |f| var a = Int8Array.newClear(f.length); f.read(a); a })
	}

	// a def of n sines summed: about 100 bytes a sine
	bigDef { |name, n| ^SynthDef(name, { Out.ar(0, Mix.fill(n, { |i| SinOsc.ar(100 + i, 0, 1e-4) })) }) }

	test_large_def_spilled {
		var big = this.bigDef(\vikn_spill_big, 2000), small = SynthDef(\vikn_spill_small, { Out.ar(0, SinOsc.ar(440, 0, 0.1)) });
		var bigBytes = big.asBytes, smallBytes = small.asBytes;
		var score = Score([
			[0.0, ['/d_recv', smallBytes]],
			[0.0, ['/d_recv', bigBytes, ['/s_new', \vikn_spill_big, 1000, 0, 0]]],
			[1.0, ['/s_new', \vikn_spill_small, 1001, 0, 0]]
		]);
		// (Score.new puts a /g_new line first: the lines are found by what they hold)
		var names = score.spillDefs(dir), line = score.score.detect { |l| l[1][0] == '/d_load' };
		this.assert(bigBytes.size > 65480, "the big def is past one packet (% bytes)".format(bigBytes.size));
		this.assertEquals(names, ["vikn_spill_big"], "only the big def is spilled");
		this.assert(score.score.any { |l| l[1][0] == '/d_recv' and: { l[1][1] === smallBytes } }, "the small def stays inline");
		this.assert(line.notNil, "the big def's message becomes /d_load");
		this.assertEquals(line[1][1], dir +/+ "vikn_spill_big.scsyndef", "of its file in the folder given");
		this.assertEquals(line[1][2], ['/s_new', \vikn_spill_big, 1000, 0, 0], "its completion message kept");
		this.assert(this.readBytes(line[1][1]) == bigBytes, "the file holds the def's bytes exactly");
		this.assert(score.score.every { |l| l.asRawOSC.size > (16 + l.drop(1).sum { |m| if(m[1].isKindOf(RawArray)) { m[1].size } { 0 } }) }, "every line encodes whole (no 32-byte stub)");
		this.assertEquals(score.score.last, [1.0, ['/s_new', \vikn_spill_small, 1001, 0, 0]], "the other lines as they were");
	}

	test_line_total {
		// three defs of 24 to 30 KB in one bundle: each fits, together they do not; the largest goes
		var defs = [250, 280, 310].collect { |n, i| this.bigDef(("vikn_spill_mid" ++ i).asSymbol, n) };
		var bytes = defs.collect(_.asBytes);
		var score = Score([[0.0] ++ bytes.collect { |b| ['/d_recv', b] }]);
		var names = score.spillDefs(dir, 60000), line, kept;
		this.assert(bytes.every { |b| b.size < 60000 } and: { bytes.sum(_.size) > 60000 }, "each def fits, the three do not (%)".format(bytes.collect(_.size)));
		line = score.score.detect { |l| l.size == 4 };
		kept = line.drop(1).select { |m| m[0] == '/d_recv' };
		this.assertEquals(names, ["vikn_spill_mid2"], "the largest alone is spilled: the other two fit together");
		this.assert(kept.sum { |m| m[1].size } <= 60000 and: { kept.size >= 1 }, "what stays inline fits together");
		this.assertEquals(line.drop(1).collect { |m| if(m[0] == '/d_load') { m[1].basename.splitext[0] } { "vikn_spill_mid" ++ bytes.indexOf(m[1]) } }, ["vikn_spill_mid0", "vikn_spill_mid1", "vikn_spill_mid2"], "the line keeps its three messages, in order");
		this.assert(line.asRawOSC.size > (kept.sum { |m| m[1].size }), "the line encodes whole");
	}

	test_names_and_nothing_to_do {
		var a = this.bigDef(\vikn_spill_twice, 2000).asBytes, b = this.bigDef(\vikn_spill_twice, 2100).asBytes;
		var score = Score([[0.0, ['/d_recv', a]], [2.0, ['/d_recv', b]]]);
		var plain = Score([[0.0, ['/s_new', \default, 1000, 0, 0]], [1.0, ["/d_recv", SynthDef(\vikn_spill_tiny, { Out.ar(0, DC.ar(0)) }).asBytes]]]);
		this.assertEquals(score.spillDefs(dir), ["vikn_spill_twice", "vikn_spill_twice-2"], "a def redefined later keeps both versions");
		this.assert(this.readBytes(score.score.last[1][1]) == b, "the second file holds the second version");
		this.assertEquals(plain.spillDefs(dir ++ "_none"), [], "a score of small defs: nothing spilled");
		this.assert(File.exists(dir ++ "_none").not, "and no folder made");
	}
}
