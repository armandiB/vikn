// reCurrent — the path-control orgnsm: a silent orgnsm that, once per loop,
// reads one rhythm from a rhythm dict, walks a path over a spherical design
// and hands each hit to the orgnsm of the batch sitting at that point, writing
// every orgnsm's seq_list (and other_params_key_list, dur_params) for the
// coming loop. Port of ~orgnsm_path_control (HadronLake2 Assemblage).
//
//   ~path = RCPathControl(~song, ~tdesign, ~rhythmDict, \sampler, \test_sampler_w0, ~batch);
//   ~pathBatch = RCBatch(\path_sampler, ~path, layerKey: \core);
//   ~pathBatch.addCreate(~batch.name, ("dur_params": [4, 1], "quant": [4, 0]));
//   ~pathBatch.startPrepared;
//
// Configurable static attrs (as before): dur_params, path_priority,
// start_orgnsm_series, seed_orgnsm_series, orgnsm_series_pattern (default
// Pseq(path.mirror1, inf); Ref an Array in the series to hit several orgnsms
// at once), add_previous_orgnsms_to_other_params, other_params_default_values,
// path_generation_func (Ref'd function (design, start, seed) → path).
// The controlled orgnsms read the result through
// RCOrgnsmPatterns.seqParams and Pfunc { |ev| ev.compute_seq_params.dereference[key] }.

RCPathControl : RCOrgnsm {
	classvar <subseqIndexKey = \zZZZ_subseq_index;
	classvar <previousOrgnsmsKey = \zZZZ_previous_orgnsms;
	classvar <noPreviousOrgnsmKey = \zZZZ_no_previous_orgnsm;
	classvar <>maxHitsPerLoop = 10000;

	*new { |song, design, rhythmDict, pathName, pathKey, controlledBatch, species = \path_control, tribe = 0|
		^super.new(species, tribe, 0, song).initRCPathControl(design, rhythmDict, pathName, pathKey, controlledBatch)
	}

	// bare instance for clone(): state is copied by RCOrgnsm.clone
	*bare { |species, tribe, number, song, addSongInName|
		^super.new(species, tribe, number, song, addSongInName)
	}

	prNewLike { ^this.class.bare(species, tribe, number, song, addSongInName) }

	initRCPathControl { |design, rhythmDict, pathName, pathKey, controlledBatch|
		this.addStaticAttrs((
			seed: 1994,
			dur_params: [4, 1],   // loop dur, time mult
			loop_time: { |self| var dp = self.dur_params; dp[0] * dp[1] },
			quant: { |self| [self.loop_time, 0] },
			dur_params_orgnsms: { |self| [self.loop_time, 1] },   // time mult already applied in the distributed seqs
			other_params_default_values: IdentityDictionary.new,
			add_previous_orgnsms_to_other_params: false,
			reserved_key_add_previous_orgnsms_to_other_params: previousOrgnsmsKey,

			start_orgnsm_series: 0,
			seed_orgnsm_series: 2,
			tdesign: design,
			path_generation_func: Ref({ |design, start, seed| RCSpherePath.generatePath(design, start, seed) }),
			orgnsm_series: { |self| RCPathControl.cachedSeries(self) },
			skip_orgnsm_if_rest: false,
			orgnsm_series_pattern: { |self| Pseq(self.orgnsm_series.mirror1, inf) },

			path_priority: 1,
			keys_ignore_order_other_params: [],
			rhythm_dict: rhythmDict,
			path_name: pathName,
			path_key: pathKey,
			controlled_batch: controlledBatch
		));
		this.attrDictBase = [
			type: \rest,
			dur_flex: Pfunc { |ev| ev[\self].staticAttrs.loop_time },

			start_orgnsm_series: 0,
			start_orgnsm_series_seed: \dummy,
			seed_orgnsm_series: 0,
			change_orgnsm_series_params: \dummy,

			seqs_info: Pfunc { |ev| ev[\self].seqsInfo },
			other_params_key_list: Pfunc { |ev| ev[\self].otherParamsKeyList(ev[\seqs_info]) },
			seq_list_by_orgnsm_dict: Pfunc { |ev| ev[\self].seqListByOrgnsm(ev) },
			set_attrs_in_orgnsms: Pfunc { |ev| ev[\self].setAttrsInOrgnsms(ev); \dummy }
		];
	}

	// The default orgnsm_series: the seeded path is generated once per
	// (design, start, seed, generation function) and cached in the static
	// attrs; the series stream itself is rebuilt every loop, as in the
	// original, so each loop walks the path from its start.
	*cachedSeries { |st|
		var key = [st[\tdesign].identityHash, st[\start_orgnsm_series], st[\seed_orgnsm_series], st[\path_generation_func]];
		if(st[\zZZZ_series_cache_key] != key) {
			st[\zZZZ_series_cache] = st[\path_generation_func].dereference.value(st[\tdesign], st[\start_orgnsm_series], st[\seed_orgnsm_series]);
			st[\zZZZ_series_cache_key] = key;
		};
		^st[\zZZZ_series_cache]
	}

	// The subseqs of this loop, fresh from the rhythm dict.
	seqsInfo {
		var st = staticAttrs;
		^RCGuard.call(\pathControl, []) { st[\rhythm_dict].subseqs(st[\path_name], st[\path_key]) }
	}

	// Union of the param keys of the subseqs, in a stable (sorted) order.
	otherParamsKeyList { |seqsInfo|
		var res = List.new;
		(seqsInfo ? []).do { |subseq|
			(subseq[3] ? ()).keys.asArray.sort { |a, b| a.asString < b.asString }.do { |key|
				if(res.includes(key).not) { res.add(key) };
			};
		};
		^res.asArray
	}

	// Declare the current rhythm's param keys on every beat of the batch (the
	// controlled one by default) so that the first loop sets them without a
	// pattern restart. Call it after the batch started. Returns the keys.
	reserveKeysIn { |batch|
		var keys = this.otherParamsKeyList(this.seqsInfo);
		var b = batch ?? { staticAttrs[\controlled_batch] };
		if(b.isNil) { RCLog.error(\pathControl, "% reserveKeysIn: no batch".format(this.name)); ^keys };
		b.apply({ |o| o.beat !? (_.reserveKeys(keys)); o });
		^keys
	}

	// Compile the loop, then distribute its hits over the orgnsm series.
	// Returns Dictionary batchKey → [RCSubseq].
	seqListByOrgnsm { |ev|
		var st = staticAttrs;
		var addPrev = st[\add_previous_orgnsms_to_other_params] ? false;
		var keyList = ev[\other_params_key_list] ? [];
		var seqsInfo = ev[\seqs_info] ? [];
		var keyListAdj = keyList ++ [subseqIndexKey];
		var seriesStream, computeStream, results = List.new, count = 0, res;
		var seqsByOrgnsm = Dictionary.new, cumdurPosByOrgnsm = Dictionary.new, cumdurAcc = 0;
		var previousOrgnsms;
		var seqListByOrgnsm;

		seqsInfo.do { |subseq, i|
			if(subseq[3].isNil) { subseq[3] = () };
			subseq[3][subseqIndexKey] = Pn(i);
		};
		seriesStream = st.orgnsm_series_pattern.asStream;   // Event-style access evaluates the function
		computeStream = RCOrgnsmPatterns.seqParamsForLoop(false, (
			dur_params: st[\dur_params],
			loop_time: { |self| var dp = self.dur_params; dp[0] * dp[1] },
			seq_list: seqsInfo,
			other_params_key_list: keyListAdj
		), 0, false).asStream;
		while { (res = computeStream.next).notNil and: { count < maxHitsPerLoop } } {
			results.add(res);
			count = count + 1;
		};
		if(count >= maxHitsPerLoop) { RCLog.error(\pathControl, "% hits in one loop, truncated".format(count), force: true) };

		results.do { |array|
			var dur = array[0];
			var otherParams = array[1..];
			if(dur.isRest.not) {
				var subseqIndex = otherParams.pop;
				var orgnsmKeys = seriesStream.next;
				if(orgnsmKeys.isKindOf(Ref)) {   // a Ref'd array hits several orgnsms at once
					orgnsmKeys = orgnsmKeys.dereference;
					if(orgnsmKeys.isKindOf(Collection).not) { orgnsmKeys = [orgnsmKeys] };
				} {
					orgnsmKeys = [orgnsmKeys];
				};
				orgnsmKeys.do { |orgnsmKey|
					if(orgnsmKey.notNil) {   // exhausted series → skip the hit
						var addedRest, isFirstHit = false, entry;
						if(seqsByOrgnsm.includesKey(orgnsmKey).not) {
							seqsByOrgnsm[orgnsmKey] = [0, List.new, List.new, List.new];
							cumdurPosByOrgnsm[orgnsmKey] = 0;
							isFirstHit = true;
						};
						entry = seqsByOrgnsm[orgnsmKey];
						addedRest = cumdurAcc - cumdurPosByOrgnsm[orgnsmKey];
						if(addedRest > 0) {
							if(isFirstHit) {
								entry[0] = addedRest;   // initial shift
							} {
								entry[1].add(Rest(addedRest));
								entry[2].add(this.prRestParams(keyList, addPrev, previousOrgnsms));
								entry[3].add(subseqIndex);
							};
						};
						if(addedRest > -1e-5) {
							var params = if(addPrev) { otherParams ++ [previousOrgnsms ? noPreviousOrgnsmKey] } { otherParams };
							entry[1].add(if(addedRest >= 0) { dur } { dur + addedRest });
							entry[2].add(params);
							entry[3].add(subseqIndex);
							cumdurPosByOrgnsm[orgnsmKey] = cumdurPosByOrgnsm[orgnsmKey] + addedRest + dur;
						} {   // the same orgnsm twice in one hit group: the second hit has no room
							RCLog.warn(\pathControl, { "%: orgnsm % hit twice at once, second hit dropped".format(this.name, orgnsmKey) });
						};
					};
				};
				previousOrgnsms = orgnsmKeys;
			};
			cumdurAcc = cumdurAcc + dur.value;
		};

		if(addPrev) { keyList = keyList ++ [st[\reserved_key_add_previous_orgnsms_to_other_params]] };
		seqListByOrgnsm = Dictionary.new;
		seqsByOrgnsm.keysValuesDo { |orgnsmKey, infos|
			var durSeq = infos[1].asArray;
			var paramsSeqs = infos[2].asArray.flop;
			var params = ();
			keyList.do { |key, i| params[key] = paramsSeqs[i].asArray };
			seqListByOrgnsm[orgnsmKey] = [RCSubseq(st[\path_priority], infos[0], durSeq, params, true ! durSeq.size, nil, [], 1)];
		};
		^seqListByOrgnsm
	}

	prRestParams { |keyList, addPrev, previousOrgnsms|
		var res = Rest() ! keyList.size;
		^if(addPrev) { res ++ [previousOrgnsms ? noPreviousOrgnsmKey] } { res }
	}

	// Push this loop's distribution into the controlled batch.
	setAttrsInOrgnsms { |ev|
		var st = staticAttrs;
		var batch = st[\controlled_batch];
		var keyList = ev[\other_params_key_list] ? [];
		var defaults = st[\other_params_default_values] ? IdentityDictionary.new;
		var seqLists = ev[\seq_list_by_orgnsm_dict] ? Dictionary.new;
		if(batch.isNil) { RCLog.error(\pathControl, "% has no controlled_batch".format(this.name)); ^this };
		if(st[\add_previous_orgnsms_to_other_params] ? false) { keyList = keyList ++ [st[\reserved_key_add_previous_orgnsms_to_other_params]] };
		batch.editAttr("dur_params", st.dur_params_orgnsms);
		// an orgnsm records only the keys installed on its beat: one without a
		// beat yet gets them at the first loop after it starts
		batch.apply({ |o|
			var beat = o.beat;
			var oldKeys = o.staticAttrs[\other_params_key_list] ? [];
			if(beat.notNil) {
				keyList.difference(oldKeys).do { |key|
					beat.set(key, Pfunc { |ev2|
						ev2[\compute_seq_params].dereference[key] ?? {
							defaults[key] ?? {
								RCLog.warn(\pathControl, { "%: key % not found in compute_seq_params, returning Rest()".format(o.name, key) });
								Rest()
							}
						}
					});
				};
				o.rPut("other_params_key_list", keyList);
			} {
				o.rPut("other_params_key_list", []);
			};
			o
		});
		batch.editAttr("seq_list", { |o, i, list, key| seqLists[key] ? [] }, nil, true);
	}
}
