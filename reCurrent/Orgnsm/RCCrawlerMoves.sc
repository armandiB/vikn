// reCurrent — the crawler's rhythm-morphing move generator
// (BlockOrgnsm's ~crawler_pattern_lib[\next_val_rhythm_change]).
//
// At each step the crawler holds the seq_list of its current orgnsm and a
// target seq_list from staticAttrs.rhythm_dict_target (batch name / key of
// that orgnsm). One elementary move is tried per step, in order:
//   shift            a subseq of the same name moves one step towards the target shift
//   changeSubseq     a subseq whose name is not in the target is swapped for a
//                    close enough target (matching_distance_func on onsets)
//   convergeMask     one mask bit flips towards the target mask
//   decreaseMask     one hit of an unmatched subseq is masked out
//   switchOtherParams params of an otherwise matching subseq are replaced
// Each move can take a wrong turn with its error_probability_* attribute.
// Only array-based subseqs (durations and masks as arrays) can be morphed.
// The result is [newSeqList], the crawler's next value.

RCCrawlerMoves {

	*nextValRhythmChange {
		^Pfunc { |ev|
			var crawler = ev[\self].parentObject;
			var st = ev[\self].staticAttrs;
			var res = RCGuard.call(\crawlerMoves, nil) { this.computeNext(crawler, st) };
			[res ?? { crawler.prevVal[0] }]
		}
	}

	// The next seq_list, or the current one when no move applies.
	*computeNext { |crawler, st|
		var ctx = this.prContext(crawler, st);
		if(ctx.isNil) { ^crawler.prevVal[0] };
		^this.shift(ctx) ?? { this.changeSubseq(ctx) } ?? { this.convergeMask(ctx) }
			?? { this.decreaseMask(ctx) } ?? { this.switchOtherParams(ctx) } ?? { ctx[\current] }
	}

	*prContext { |crawler, st|
		var orgnsm = crawler.orgnsm;
		var batch, current, targets, ctx;
		if(orgnsm.isNil) { RCLog.error(\crawlerMoves, "crawler has no orgnsm"); ^nil };
		batch = orgnsm.batch;
		if(batch.isNil) { RCLog.error(\crawlerMoves, "% is not in a batch".format(orgnsm.name)); ^nil };
		current = crawler.prevVal[0] ? [];
		targets = RCGuard.call(\crawlerMoves, []) { st[\rhythm_dict_target].subseqs(batch.name, orgnsm.batchKey) };
		ctx = (
			current: current, targets: targets, orgnsm: orgnsm, st: st,
			priorityOk: RCUtil.attrFunc(st, \priority_matching_func) ? { true },
			matchingDistance: RCUtil.attrFunc(st, \matching_distance_func) ? { |a, b| (a.size - b.size).abs },
			tolerance: st[\tolerance_change_subseq] ? 0,
			otherKeysToChange: st[\other_keys_to_change],
			keepOtherParams: st[\keep_other_params_not_in_new_seq] ? false,
			errShift: st[\error_probability_shift_converge] ? 0,
			errMaskConverge: st[\error_probability_mask_converge] ? 0,
			errMaskDecrease: st[\error_probability_mask_decrease] ? 0
		);
		ctx[\remainingTargetNames] = RCUtil.bagDifference(targets.collect(_.name).asBag, current.collect(_.name).asBag);
		ctx[\remainingCurrentNames] = RCUtil.bagDifference(current.collect(_.name).asBag, targets.collect(_.name).asBag);
		ctx[\remainingTargets] = targets.select { |s| ctx[\remainingTargetNames].includesEqual(s.name) };
		ctx[\remainingCurrentIdx] = current.selectIndices { |s| ctx[\remainingCurrentNames].includesEqual(s.name) }.scramble;
		^ctx
	}

	*prReplace { |current, idx, subseq|
		^current.select { |s, i| i != idx } ++ [subseq]
	}

	// name → List of targets whose key (keyFunc) is not yet matched by a current subseq
	*prRemainingDict { |ctx, keyFunc, groupFunc|
		var targetBag = ctx[\targets].collect(keyFunc).asBag;
		var currentBag = ctx[\current].collect(keyFunc).asBag;
		var remainingTarget = RCUtil.bagDifference(targetBag, currentBag);
		var remainingCurrent = RCUtil.bagDifference(currentBag, targetBag);
		var dict = Dictionary.new;
		ctx[\targets].do { |s|
			if(remainingTarget.includesEqual(keyFunc.(s))) {
				var g = groupFunc.(s);
				dict[g] = dict[g] ?? { List.new };
				dict[g].add(s);
			};
		};
		^[dict, ctx[\current].selectIndices { |s| remainingCurrent.includesEqual(keyFunc.(s)) }.scramble]
	}

	//////// moves

	*shift { |ctx|
		var pair = this.prRemainingDict(ctx, { |s| [s.name, s.shift] }, { |s| s.name });
		var dict = pair[0], idxs = pair[1];
		idxs.do { |idx|
			var current = ctx[\current][idx];
			var acceptable = (dict[current.name] ? []).select { |t| (t.shift != current.shift) and: { ctx[\priorityOk].(t.priority, current.priority) } };
			if(acceptable.size > 0) {
				var chosen = acceptable.choose;
				var diff = chosen.shift - current.shift;
				var step = if(ctx[\errShift].coin) { diff.sign.neg } { diff.sign };
				var modified = current.deepCopy;
				modified.shift = modified.shift + step;
				^this.prReplace(ctx[\current], idx, modified)
			};
		};
		^nil
	}

	*changeSubseq { |ctx|
		var durParams = ctx[\orgnsm].staticAttrs[\dur_params] ? [1, 1];
		var loopNoMult = durParams[0], timeMult = durParams[1] ? 1;
		ctx[\remainingCurrentIdx].do { |idx|
			var current = ctx[\current][idx];
			var mask = current.mask;
			if(mask.isKindOf(SequenceableCollection)) {
				var currentCumdur = RCRhythm.cumdurFromSubseq(current, loopTime: loopNoMult, timeMult: timeMult)[0];
				var candidates = ctx[\remainingTargets]
					.select { |t| ctx[\priorityOk].(t.priority, current.priority) and: { t.name != current.name } and: { t.mask.isKindOf(SequenceableCollection) } }
					.collect { |t| var m = this.prFitMask(mask, t.mask.size); [RCRhythm.cumdurFromSubseq(t, mask: m, loopTime: loopNoMult, timeMult: timeMult)[0], t, m] };
				var distances = candidates.collect { |c| [ctx[\matchingDistance].(c[0], currentCumdur), c[1], c[2]] };
				var acceptable = distances.select { |d| d[0] <= ctx[\tolerance] };
				if(acceptable.size > 0) {
					var minDist = acceptable.collect(_.first).minItem;
					var chosenTriple = acceptable.select { |d| d[0] == minDist }.choose;
					var chosen = chosenTriple[1].deepCopy;
					chosen.mask = chosenTriple[2];
					this.prFilterParams(chosen, ctx);
					if(ctx[\keepOtherParams]) { this.prAddMissingParams(chosen, current) };
					^this.prReplace(ctx[\current], idx, chosen)
				};
			};
		};
		^nil
	}

	// Resize a mask to `size`: extra falses inserted at random, or falses removed first.
	*prFitMask { |mask, size|
		var diff = size - mask.size;
		var res = mask.copy;
		if(diff > 0) { diff.do { res = res.insert((res.size + 1).rand, false) } };
		if(diff < 0) {
			var falseIdx = res.selectIndices(_.not);
			if(falseIdx.size <= diff.neg) {
				res = true ! size;
			} {
				var remove = falseIdx.scramble[..(diff.neg - 1)];
				res = res.reject { |x, i| remove.includes(i) };
			};
		};
		^res
	}

	*convergeMask { |ctx|
		var pair = this.prRemainingDict(ctx, { |s| [s.name, s.mask, s.shift] }, { |s| [s.name, s.shift] });
		var dict = pair[0], idxs = pair[1];
		idxs.do { |idx|
			var current = ctx[\current][idx];
			if(current.mask.isKindOf(SequenceableCollection)) {
				var acceptable = (dict[[current.name, current.shift]] ? []).select { |t|
					(t.mask != current.mask) and: { t.mask.isKindOf(SequenceableCollection) } and: { t.mask.size == current.mask.size }
					and: { ctx[\priorityOk].(t.priority, current.priority) }
				};
				if(acceptable.size > 0) {
					var target = acceptable.choose;
					var diff = current.mask.collect { |b, i| b.xor(target.mask[i]) };
					var chosenIdx = if(ctx[\errMaskConverge].coin) { diff.selectIndices(_.not).choose } { diff.selectIndices { |d| d }.choose };
					if(chosenIdx.notNil) {
						var modified = current.deepCopy;
						modified.mask[chosenIdx] = modified.mask[chosenIdx].not;
						^this.prReplace(ctx[\current], idx, modified)
					};
				};
			};
		};
		^nil
	}

	*decreaseMask { |ctx|
		ctx[\remainingCurrentIdx].do { |idx|
			var current = ctx[\current][idx];
			var mask = current.mask;
			if(mask.isKindOf(SequenceableCollection) and: { mask.count { |b| b } > 0 }) {
				var chosenIdx = if(ctx[\errMaskDecrease].coin) { mask.selectIndices(_.not).choose } { mask.selectIndices { |b| b }.choose };
				if(chosenIdx.notNil) {
					var modified = current.deepCopy;
					modified.mask[chosenIdx] = modified.mask[chosenIdx].not;
					^this.prReplace(ctx[\current], idx, modified)
				};
			};
		};
		^nil
	}

	*switchOtherParams { |ctx|
		var keyOf = { |s| [s.name, s.mask, s.shift] };
		var currentKeys = ctx[\current].collect(keyOf);
		var dict = Dictionary.new;
		var idxs;
		ctx[\targets].do { |t|
			var k = keyOf.(t);
			if(currentKeys.includesEqual(k)) { dict[k] = dict[k] ?? { List.new }; dict[k].add(t) };
		};
		idxs = ctx[\current].selectIndices { |s| dict[keyOf.(s)].notNil }.scramble;
		idxs.do { |idx|
			var current = ctx[\current][idx];
			var acceptable = (dict[keyOf.(current)] ? []).select { |t| ctx[\priorityOk].(t.priority, current.priority) }.select { |t|
				var adjusted = t.deepCopy;
				if(ctx[\keepOtherParams]) { this.prAddMissingParams(adjusted, current) };
				current != adjusted
			};
			if(acceptable.size > 0) {
				var chosen = acceptable.choose.deepCopy;
				this.prFilterParams(chosen, ctx);
				if(ctx[\keepOtherParams]) { this.prAddMissingParams(chosen, current) };
				^this.prReplace(ctx[\current], idx, chosen)
			};
		};
		^nil
	}

	// keep only other_keys_to_change in the chosen params, when set
	*prFilterParams { |chosen, ctx|
		var keys = ctx[\otherKeysToChange];
		if(keys.notNil) {
			chosen.params = chosen.params.select { |v, k| keys.includes(k) };
		};
	}

	// copy current params the chosen subseq lacks (unless listed in its keysIgnoreOrder)
	*prAddMissingParams { |chosen, current|
		var notToKeep = chosen.keysIgnoreOrder ? [];
		var ignoreCurrent = current.keysIgnoreOrder ? [];
		chosen.params = chosen.params ? ();
		(current.params ? ()).keysValuesDo { |key, val|
			if(chosen.params.includesKey(key).not and: { notToKeep.includes(key).not }) {
				chosen.params[key] = val;
				if(ignoreCurrent.includes(key)) { chosen.keysIgnoreOrder = (chosen.keysIgnoreOrder ? []) ++ [key] };
			};
		};
		^chosen
	}
}
