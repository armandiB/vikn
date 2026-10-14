// Score: SynthDefs of any size in a non-realtime score.
//
// A score carries its SynthDefs inline, as /d_recv messages holding the def's bytes. sclang writes
// every line of a score as one OSC packet (Score.write: Array:asRawOSC), encoded into a fixed buffer
// of 64 KB: a def larger than 65480 bytes (the rest is the bundle's and the message's own header)
// comes out as a 32-byte stub, with "makeSynthMsgWithTags: buffer overflow" posted. scsynth then
// reports "/d_recv failed", reads past the stub into the next packet ("ecv Command not found"), and
// sometimes dies (the render a 44-byte file). A live server never meets the limit: SynthDef:add sends
// a def over 16 KB as a file (/d_load from SynthDef.synthDefDir). spillDefs does the same in a score,
// into a folder of its own, so that the score is well formed and holds every def it needs.
//
//   score = Score(defs.collect { |d| [0.0, ['/d_recv', d.asBytes]] } ++ lines);
//   score.spillDefs(out ++ ".defs");   // before recordNRT: the large defs as files next to the render
//   score.recordNRT(...);
+ Score {
	// Every /d_recv whose def is over maxInline bytes, and as many of the others of its line as it takes
	// for the line's inline defs to stay under maxInline together (the largest first), is written into
	// dir as <name>.scsyndef (the bytes as they are: asBytes is the file format) and replaced by /d_load
	// of that file, its completion message kept. NRT scsynth performs a /d_load before the next command
	// of the score, so the def is there for whatever follows it. A def name met twice gets -2, -3...
	// dir is made when a def is spilled. Returns the names of the files written, in score order.
	spillDefs { |dir, maxInline = 60000|
		var spilled = List.new, written = Set.new;
		var isDef = { |m|
			m.isKindOf(SequenceableCollection) and: { m.isString.not } and: { m.size >= 2 }
			and: { m[0].isKindOf(String) or: { m[0].isKindOf(Symbol) } } and: { ['/d_recv', 'd_recv'].includes(m[0].asSymbol) }
			and: { m[1].isKindOf(RawArray) }
		};
		var fileName = { |bytes|
			// SCgf, version (int32), number of defs (int16), then the first def's name as a pstring
			var n = if(bytes.size > 10) { bytes[10].bitAnd(255) } { 0 };
			var name = if(n > 0 and: { bytes.size >= (11 + n) }) { bytes.copyRange(11, 10 + n).asArray.collect { |b| b.bitAnd(255).asAscii }.join } { "def" };
			var base = name.collect { |c| if(c.isAlphaNum or: { "_-.".includes(c) }) { c } { $_ } }, k = 1;
			name = base;
			while { written.includes(name) } { k = k + 1; name = base ++ "-" ++ k };
			written.add(name);
			name
		};
		dir = dir.standardizePath;
		score = score.collect { |line|
			var defs, inline, spill;
			if(line.isKindOf(SequenceableCollection).not or: { line.size < 2 }) { line } {
				defs = (1 .. line.size - 1).select { |i| isDef.(line[i]) };
				spill = defs.select { |i| line[i][1].size > maxInline };
				inline = defs.reject { |i| spill.includes(i) }.sort { |a, b| line[a][1].size >= line[b][1].size };
				while { inline.notEmpty and: { inline.sum { |i| line[i][1].size } > maxInline } } {
					spill = spill.add(inline.removeAt(0));
				};
				if(spill.isEmpty) { line } {
					line = line.copy;
					spill.sort.do { |i|
						var msg = line[i], name = fileName.(msg[1]), path = dir +/+ name ++ ".scsyndef";
						if(File.exists(dir).not) { File.mkdir(dir) };
						File.use(path, "wb", { |f| f.write(msg[1]) });
						line[i] = ['/d_load', path] ++ msg.copyToEnd(2);
						spilled.add(name);
					};
					line
				}
			}
		};
		^spilled.asArray
	}
}
