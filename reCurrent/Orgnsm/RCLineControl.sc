// reCurrent — the line-control orgnsm: a silent orgnsm that, once per cycle, asks a
// generator for the sonic lines of the coming cycle (RCLines), allocates them to the
// voices of a batch and writes every voice's seq_list for the cycle, as RCPathControl
// does with a rhythm. The voices read the result through RCOrgnsmPatterns.seqParams:
// each hit carries the line's ends as <key>0 / <key>1 (pitch0, pitch1, ...), its length
// as sustain, and line_id.
//
//   ~ctl = RCLineControl(~song, { |control, cycle, beats| RCLines.cloud(12, beats, seed: cycle) }, ~batch);
//   ~ctlBatch = RCBatch(\line_control, ~ctl, layerKey: \meta);
//   ~ctlBatch.addCreate(~batch.name, ("dur_params": [8, 1], "quant": [8, -0.25]));
//   ~ctlBatch.startPrepared;
//
// Static attrs: dur_params ([cycle beats, 1]), generator (a Function (control, cycleIndex,
// cycleBeats) → Array of lines, or nil for a silent cycle; a Ref of one is dereferenced),
// controlled_batch, allocation (\free, \round_robin, \strict: RCLines.allocate), voice_keys
// (the batch keys receiving lines, in allocation order; the batch's keys when nil),
// line_defaults (key → value for a coordinate a line lacks, e.g. (az: 0)), min_gap (beats
// between two lines of one voice), priority. The control keeps cycle_index, last_lines and
// dropped (lines no voice could take) in its static attrs.

RCLineControl : RCOrgnsm {
	classvar <>maxLinesPerCycle = 4096;

	*new { |song, generator, controlledBatch, species = \line_control, tribe = 0|
		^super.new(species, tribe, 0, song).initRCLineControl(generator, controlledBatch)
	}

	// bare instance for clone(): state is copied by RCOrgnsm.clone
	*bare { |species, tribe, number, song, addSongInName|
		^super.new(species, tribe, number, song, addSongInName)
	}

	prNewLike { ^this.class.bare(species, tribe, number, song, addSongInName) }

	initRCLineControl { |generator, controlledBatch|
		this.addStaticAttrs((
			seed: 1994,
			dur_params: [8, 1],   // cycle beats, time mult
			loop_time: { |self| var dp = self.dur_params; dp[0] * dp[1] },
			quant: { |self| [self.loop_time, 0] },
			dur_params_orgnsms: { |self| [self.loop_time, 1] },

			generator: generator,
			controlled_batch: controlledBatch,
			allocation: \free,
			voice_keys: nil,
			line_defaults: (),
			min_gap: 0.001,
			priority: 1,

			cycle_index: 0,
			last_lines: [],
			dropped: 0
		));
		this.attrDictBase = [
			type: \rest,
			dur_flex: Pfunc { |ev| ev[\self].staticAttrs.loop_time },
			lines: Pfunc { |ev| ev[\self].generateLines(ev) },
			other_params_key_list: Pfunc { |ev| ev[\self].otherParamsKeyList(ev[\lines]) },
			seq_list_by_orgnsm_dict: Pfunc { |ev| ev[\self].seqListByOrgnsm(ev) },
			set_attrs_in_orgnsms: Pfunc { |ev| ev[\self].setAttrsInOrgnsms(ev); \dummy }
		];
	}

	//////// generation

	// The lines of the coming cycle: the generator's, validated (a line without a positive
	// dur, or starting outside [0, cycle), is dropped with a warning), sorted by onset.
	// cycle_index counts the cycles generated, last_lines keeps the result.
	generateLines { |ev|
		var st = staticAttrs;
		var cycle = st.loop_time;
		var index = st[\cycle_index] ? 0;
		var gen = st[\generator];
		var lines, kept, bad = 0;
		if(gen.isKindOf(Ref)) { gen = gen.dereference };
		lines = RCGuard.call(\lineControl, []) { gen.value(this, index, cycle) } ? [];
		if(lines.isKindOf(SequenceableCollection).not) {
			RCLog.error(\lineControl, "% generator returned % (an Array of lines expected)".format(this.name, lines.class));
			lines = [];
		};
		if(lines.size > maxLinesPerCycle) {
			RCLog.error(\lineControl, "% lines in one cycle, truncated to %".format(lines.size, maxLinesPerCycle), force: true);
			lines = lines.keep(maxLinesPerCycle);
		};
		kept = lines.select { |l|
			var ok = l.isKindOf(Event) and: { l[\onset].isNumber } and: { l[\dur].isNumber } and: { l[\dur] > 0 }
				and: { l[\onset] >= 0 } and: { l[\onset] < cycle };
			if(ok.not) { bad = bad + 1 };
			ok
		};
		if(bad > 0) { RCLog.warn(\lineControl, "%: % line(s) dropped (no positive dur, or an onset outside [0, %))".format(this.name, bad, cycle)) };
		kept = kept.sort { |a, b| a[\onset] <= b[\onset] };
		st[\cycle_index] = index + 1;
		st[\last_lines] = kept;
		^kept
	}

	// The keys of the coming cycle's lines (plus the defaults'), as per-hit param names; `path` too
	// when a line is curved (RCLines.subseq carries it then).
	otherParamsKeyList { |lines|
		var keys = IdentitySet.newFrom(RCLines.keysOf(lines));
		(staticAttrs[\line_defaults] ? ()).keysDo { |k| keys.add(k) };
		^RCLines.paramKeys(keys.asArray.sort { |a, b| a.asString <= b.asString }) ++ if((lines ? []).any { |l| l[\path].notNil }) { [\path] } { [] }
	}

	// The batch keys lines go to, in order: voice_keys, else every key of the batch
	// (started and prepared), numbers before symbols, sorted.
	voiceKeys {
		var st = staticAttrs;
		var batch = st[\controlled_batch];
		var keys;
		st[\voice_keys] !? { |vk| ^vk };
		if(batch.isNil) { ^[] };
		keys = (batch.keys ++ batch.prepared.keys.asArray).asSet.asArray;
		^keys.sort { |a, b|
			case
			{ a.isNumber and: { b.isNumber } } { a <= b }
			{ a.isNumber } { true }
			{ b.isNumber } { false }
			{ a.asString <= b.asString }
		}
	}

	// Allocate the cycle's lines to the voices and build one RCSubseq per voice that got
	// any: Dictionary voiceKey → [RCSubseq]. A coordinate a line lacks takes its
	// line_defaults value on both ends (nil when there is none: the voice's beat then falls
	// back to its own default for the key).
	seqListByOrgnsm { |ev|
		var st = staticAttrs;
		var lines = ev[\lines] ? [];
		var voices = this.voiceKeys;
		var defaults = st[\line_defaults] ? ();
		var keys = RCLines.keysOf(lines) ++ defaults.keys.asArray;
		var alloc, res = Dictionary.new;
		keys = keys.asSet.asArray.sort { |a, b| a.asString <= b.asString };
		if(voices.size == 0) {
			if(lines.size > 0) { RCLog.warn(\lineControl, "%: no voice to play % line(s)".format(this.name, lines.size)) };
			st[\dropped] = lines.size;
			^res
		};
		if(defaults.size > 0) {
			lines = lines.collect { |l|
				var c = l.copy;
				c[\from] = l[\from].copy;
				c[\to] = l[\to].copy;
				defaults.keysValuesDo { |k, v|
					if(c[\from][k].isNil) { c[\from][k] = v };
					if(c[\to][k].isNil) { c[\to][k] = v };
				};
				c
			};
		};
		alloc = RCLines.allocate(lines, voices.size, st[\allocation] ? \free);
		st[\dropped] = alloc[\dropped];
		if(alloc[\dropped] > 0) { RCLog.info(\lineControl, { "%: % line(s) found no free voice".format(this.name, alloc[\dropped]) }) };
		alloc[\voices].do { |voiceLines, i|
			var subseq;
			if(voiceLines.size > 0) {
				subseq = RCLines.subseq(voiceLines, keys, st[\priority] ? 1, st[\min_gap] ? 0.001, \lineControl);
				subseq !? { res[voices[i]] = [subseq] };
			};
		};
		^res
	}

	// Push the cycle's distribution into the controlled batch (RCPathControl's pass):
	// dur_params, the param keys of new beats, the key list, the seq_list (an empty one
	// for a voice without lines this cycle).
	setAttrsInOrgnsms { |ev|
		var st = staticAttrs;
		var batch = st[\controlled_batch];
		var keyList = ev[\other_params_key_list] ? [];
		var seqLists = ev[\seq_list_by_orgnsm_dict] ? Dictionary.new;
		var durParams;
		if(batch.isNil) { RCLog.error(\lineControl, "% has no controlled_batch".format(this.name)); ^this };
		durParams = st.dur_params_orgnsms;
		batch.apply({ |o, i, list, key|
			var beat = o.beat;
			var oldKeys = o.staticAttrs[\other_params_key_list] ? [];
			o.rPut("dur_params", durParams);
			if(beat.notNil) {
				keyList.difference(oldKeys).do { |k|
					if(beat.keyProxy(k).isNil) {   // a template declaring the key reads it already
						if(k == \path) {   // a straight hit among curved ones has no path: not a rest
							beat.set(k, Pfunc { |ev2| ev2[\compute_seq_params].dereference[k] ? 0 });
						} {
							beat.set(k, Pfunc { |ev2| ev2[\compute_seq_params].dereference[k] ?? { Rest() } });
						};
					};
				};
				o.rPut("other_params_key_list", keyList);
			} {
				o.rPut("other_params_key_list", []);
			};
			o.rPut("seq_list", seqLists[key] ? []);
			o
		});
	}

	// Declare the keys of `keys` (coordinates) on every beat of the batch so that the first
	// cycle sets them without a pattern restart; a template that reads them itself needs
	// nothing. Returns the param keys.
	reserveKeysIn { |keys, batch|
		var params = RCLines.paramKeys(keys.collect(_.asSymbol));
		var b = batch ?? { staticAttrs[\controlled_batch] };
		if(b.isNil) { RCLog.error(\lineControl, "% reserveKeysIn: no batch".format(this.name)); ^params };
		b.apply({ |o| o.beat !? (_.reserveKeys(params)); o });
		^params
	}

	printOn { |stream| stream << "RCLineControl(" << this.name << ")" }
}
