// reCurrent — the pattern library for orgnsms (BlockOrgnsm's ~orgnsm_pattern_lib).
//
// All patterns read the orgnsm through the event (ev.self) so they can live
// in a template and serve every clone. Plazy bodies receive the event under
// construction, so `Plazy { |ev| ev.self.staticAttrs }` replaces the old
// `~pat.self.dereference.static_attrs`.
//
//   compute_seq_params: RCOrgnsmPatterns.seqParams(returnDict: true)     // in addFirstArrayBase
//   dur_flex: Pfunc { |ev| ev.compute_seq_params.dereference[\dur] }      // in attrDictBase

RCOrgnsmPatterns {
	classvar <>maxEventsPerLoop = 4096;   // prMerge stops (error) past this many events in one loop

	// Pn(Plazy(func)) that cannot spin: a body that yields nothing rests 1 beat.
	// Like Pn, `key` (when given) is set to true in the event at every repeat,
	// so a Pgate on that key advances once per loop.
	*loop { |func, tag = \orgnsmPattern, key|
		^Prout { |inval|
			loop {
				var pat, stream, v, n = 0;
				if(key.notNil) { inval[key] = true };
				pat = RCGuard.call(tag, nil) { func.value(inval) };
				if(pat.isNil) {
					inval = Rest(1).yield;
				} {
					stream = pat.asStream;
					while { (v = stream.next(inval)).notNil } {
						n = n + 1;
						inval = v.yield;
					};
					if(n == 0) {
						RCLog.warn(tag, "pattern produced no value, resting 1 beat");
						inval = Rest(1).yield;
					};
				};
			};
		}
	}

	//////// buffers

	// samples per loop of the orgnsm's buffer (staticAttrs.loop_time beats)
	*numFramesPerLoop {
		^Pfunc { |ev|
			var o = ev[\self];
			var buffer = o.serverResources[\buffer];
			var clock = o.layer !? (_.clock) ?? { ev[\rc_beat].clock };
			if(buffer.isNil or: { buffer.sampleRate.isNil }) {
				RCLog.warn(\numFramesPerLoop, "% has no loaded buffer".format(o.name));
				1
			} {
				(clock.beatDur * buffer.sampleRate * (o.staticAttrs[\loop_time] ? 1)).max(1)
			}
		}
	}

	// Buffer start position: real-time tracking of the loop at cutting_fade 0,
	// Brownian / serial cutting at cutting_fade 1. The read cursor lives in the
	// orgnsm's staticAttrs under cursorKey (per orgnsm), or in `holder` to share it.
	*cuttingFadeStart { |cursorKey = \cutting_cursor, holder|
		var tracking = Pfunc { |ev|
			var o = ev[\self];
			var store = holder ? o.staticAttrs;
			var pos = store[cursorKey] ? 0;
			var fade = o.staticAttrs[\cutting_fade] ? 0;
			var clock = o.layer !? (_.clock) ?? { ev[\rc_beat].clock };
			var buffer = o.serverResources[\buffer];
			var frames = ev[\num_frames_per_loop] ? 1;
			var advance;
			if(buffer.isNil or: { buffer.sampleRate.isNil }) {
				pos
			} {
				advance = ev.delta * clock.beatDur * buffer.sampleRate * (ev[\stretch] ? 1).reciprocal * (ev[\bufrate] ? 1) * (1 - fade);
				// stretch 0 or a NaN rate would poison the cursor for good
				store[cursorKey] = RCGuard.finite((pos + advance).wrap(0, (frames - 1).max(0)), 0, \cuttingFadeStart).asInteger;
				pos
			}
		};
		var cutting = Pfunc { |ev| ev[\self].staticAttrs[\cutting_fade] ? 0 }
			* (Pbrown(0, Pkey(\num_frames_per_loop), Pkey(\num_frames_per_loop) * 0.04) + Pseries(0, Pkey(\num_frames_per_loop) * (pi / 3 - 1) / 8));
		^(tracking + cutting).wrap(0, Pkey(\num_frames_per_loop) - 1)
	}

	//////// euclidean layering

	// staticAttrs.amp_params: [[onsets, shift, amp], ...] over dur_params = [loopDur, pulses].
	// Yields [dur, amp] pairs (or the array of them with returnArray).
	*euclidDurAmp { |returnArray = false|
		^Plazy { |ev|
			var initial = ev[\self].staticAttrs;
			var arrays = this.prEuclidArrays(initial, 0);
			var initialDur = RCRhythm.durListFromBinary(arrays[0])[0];
			var initialShift = if(initialDur.isRest) { initialDur.value / initial[\dur_params][1] * initial[\dur_params][0] } { 0 };
			var actual = this.loop({ |ev2|
				var st = ev2[\self].staticAttrs;
				var eucl = this.prEuclidArrays(st, (initialShift * st[\dur_params][1] / st[\dur_params][0]).asInteger);
				var ampHits = eucl[1].reduce { |a, b| (a.squared + b.squared).sqrt };
				var durAmp = RCRhythm.durListFromBinaryWithAux(eucl[0], ampHits).flop.collect { |pair|
					[pair[0] / st[\dur_params][1] * st[\dur_params][0], pair[1]]
				};
				if(returnArray) { durAmp } { Pseq(durAmp) }
			}, \euclidDurAmp);
			if(initialShift != 0) {
				if(returnArray) { [[Rest(initialShift), 0], actual] } { Pseq([[Rest(initialShift), 0], actual]) }
			} { actual }
		}
	}

	// → [combined hits array, per-layer amp arrays]
	*prEuclidArrays { |st, shiftSub|
		var layers = st[\amp_params].collect { |rp|
			var hits = RCRhythm.euclid(rp[0], st[\dur_params][1], rp[1] - shiftSub);
			[hits, hits * rp[2]]
		}.flop;
		^[layers[0].reduce { |a, b| a.max(b) }, layers[1]]
	}

	//////// rhythm compilation

	// One loop of staticAttrs.seq_list (RCSubseqs) merged into one stream.
	// Each value is [dur, param values...] (returnDict: false) or a Ref'd
	// Dictionary (\dur → dur, key → value) so that Pbind does not stream it.
	*seqParamsForLoop { |useOrgnsmStaticAttrs = true, staticAttrs, initialBegShift = 0, returnDict = true|
		^Plazy { |ev|
			var st = if(useOrgnsmStaticAttrs) { ev[\self].staticAttrs } { staticAttrs };
			var durParams = st[\dur_params];
			var keyList = st[\other_params_key_list] ? [];
			var timeMult, loopTimeNoMult, loopTime, patArray, seqList;
			if(durParams.isNil or: { durParams[0].isNumber.not } or: { durParams[0] <= 0 }
				or: { (durParams[1] ? 1).isNumber.not } or: { (durParams[1] ? 1) <= 0 }) {
				RCLog.error(\seqParams, "dur_params % invalid (loop dur and time mult must be positive), resting 1 beat".format(durParams));
				Pseq([this.prRestValue(1, keyList, returnDict)])
			} {
				timeMult = durParams[1] ? 1;
				loopTimeNoMult = durParams[0];
				loopTime = loopTimeNoMult * timeMult;
				seqList = st[\seq_list] ? [];
				patArray = seqList.collect { |subseq|
					RCGuard.call(\seqParams, nil) { this.prSubseqPattern(subseq, loopTime, loopTimeNoMult, timeMult, initialBegShift) }
				}.reject(_.isNil);
				if(patArray.size == 0) {
					Pseq([this.prRestValue(loopTime, keyList, returnDict)])
				} {
					this.prMerge(patArray, loopTime).collect { |e|
						var dur = if(e[\dur].isRest) { Rest(e[\delta]) } { e[\delta] };
						// the silent events prMerge adds (leading gap, loop tail) carry no params:
						// rest them like prRestValue does, a missing key only matters on a hit
						var missing = if(dur.isRest) { Rest() };
						var valueFor = { |key| var v = e[key]; if(v.isNil or: { v == \nil }) { missing } { v } };
						if(returnDict) {
							Ref(([\dur -> dur] ++ keyList.collect { |key| key -> valueFor.(key) }).asDict)
						} {
							[dur] ++ keyList.collect { |key| valueFor.(key) }
						}
					}
				}
			}
		}
	}

	*prRestValue { |dur, keyList, returnDict|
		^if(returnDict) {
			Ref(([\dur -> Rest(dur)] ++ keyList.collect { |key| key -> Rest() }).asDict)
		} {
			[Rest(dur)] ++ (Rest() ! keyList.size)
		}
	}

	// Merge [offset, pattern] pairs into one event stream (delta = time to the
	// next event of any pattern). Unlike Ppar with leading rests, a hit at the
	// loop start keeps its length and no zero-length rest trails the loop.
	// The silent gap and tail events are built from an empty Event, so that a
	// param key which also exists upstream (\amp) never leaks its value into
	// a rest. A subseq yielding zero durations cannot spin: maxEventsPerLoop.
	*prMerge { |offsetsAndPatterns, loopTime|
		^Prout { |inval|
			var q = PriorityQueue.new;
			var now = 0, stream, ev, nexttime, count = 0;
			offsetsAndPatterns.do { |pair| q.put(pair[0], pair[1].asStream) };
			if(q.notEmpty and: { (nexttime = q.topPriority) > 0 }) {
				inval = Event.silentNoDefault(nexttime).yield;
				now = nexttime;
			};
			while { q.notEmpty and: { count < maxEventsPerLoop } } {
				stream = q.pop;
				ev = stream.next(inval);
				if(ev.isNil) {
					nexttime = q.topPriority;
					if(nexttime.notNil and: { nexttime > now }) {
						inval = Event.silentNoDefault(nexttime - now).yield;
						now = nexttime;
					};
				} {
					ev = ev.asEvent;
					q.put(now + ev.delta, stream);
					nexttime = q.topPriority;
					ev.put(\delta, nexttime - now);
					inval = ev.yield;
					now = nexttime;
					count = count + 1;
				};
			};
			if(count >= maxEventsPerLoop) {
				RCLog.error(\seqParams, "% events in one loop (zero durations?), the rest of the loop is dropped".format(count), force: true);
			};
			if(loopTime.notNil and: { now < loopTime }) {
				inval = Event.silentNoDefault(loopTime - now).yield;
			};
			inval
		}
	}

	// One subseq → [offset, Pevent of its hits within the loop] (durations, params, rests).
	// subseq: an RCSubseq or a raw [priority, shift, durs, params, mask, ...] array
	// (a Boolean mask means every hit, like RCRhythmDict.resolve).
	*prSubseqPattern { |subseq, loopTime, loopTimeNoMult, timeMult, initialBegShift|
		var s = if(subseq.isKindOf(RCSubseq)) { subseq.copy } { RCSubseq.fromArray(subseq) };
		var params = (s.params ? ()).copy;
		var durInfo = s.durs;
		var mask = s.mask ? true;
		var keysIgnoreOrder = s.keysIgnoreOrder ? [];
		var patternDur, begShift, eventPat, allArrays;
		if(mask.isKindOf(Boolean)) { mask = if(durInfo.isKindOf(SequenceableCollection)) { mask ! durInfo.size } { Pn(mask) } };
		s.mask = mask;
		// every param must be an array (or be listed in keysIgnoreOrder) for the
		// array branch; keys and values are visited together (their separate
		// enumerations are in different orders)
		allArrays = durInfo.isKindOf(SequenceableCollection) and: { mask.isKindOf(SequenceableCollection) } and: {
			var ok = true;
			params.keysValuesDo { |key, val|
				if(keysIgnoreOrder.includes(key).not and: { val.isKindOf(SequenceableCollection).not }) { ok = false };
			};
			ok
		};
		if(allArrays) {
			var cumdur = RCRhythm.cumdurFromSubseq(s, shift: s.shift - (initialBegShift / timeMult), loopTime: loopTimeNoMult, timeMult: timeMult);
			var durShift = RCRhythm.durFromCumdur(cumdur[0], cumdur[2]);
			patternDur = if(durShift[0].size == 0) { [Rest(loopTime)] } { durShift[0] };
			begShift = durShift[1] ? 0;
			params = params.collect { |array, key|
				if(keysIgnoreOrder.includes(key)) { array } { cumdur[1].collect { |idx| array[idx] } }
			};
		} {
			var combined;
			begShift = ((timeMult * s.shift) - initialBegShift) % loopTime;
			if(mask.isKindOf(SequenceableCollection)) { mask = Pseq(mask) };
			if(durInfo.isKindOf(SequenceableCollection)) { durInfo = Pseq(durInfo) };
			combined = Ptuple([timeMult * durInfo, mask]);
			patternDur = combined.collect { |pair| if(pair[1] ? true) { pair[0] } { Rest(pair[0]) } };
		};
		eventPat = params.asEvent.copy;
		eventPat[\dur] = patternDur;
		eventPat = eventPat.collect { |seq, key|
			if(seq.isKindOf(SequenceableCollection)) { if(seq.size > 0) { Pseq(seq) } { Pseq([\nil]) } } { seq }
		};
		// the hits fill the loop after the offset; params keep a Rest() for the remainder event
		eventPat = eventPat.collect { |pat, key| if(key == \dur) { PconstRestSafe(loopTime - begShift, pat) } { Pseq([pat, Rest()]) } };
		^[begShift, Pevent(Pbind(*eventPat.asKeyValuePairs), ())]
	}

	// Loop after loop of seqParamsForLoop, aligned on the earliest subseq shift.
	*seqParams { |returnDict = true|
		^Plazy { |ev|
			var st = ev[\self].staticAttrs;
			var durParams = st[\dur_params] ? [1, 1];
			var timeMult = durParams[1] ? 1;
			var loopTime = (durParams[0] ? 1) * timeMult;
			var seqList = st[\seq_list] ? [];
			var keyList = st[\other_params_key_list] ? [];
			var begShift = st[\initial_beg_shift] ?? {
				if(seqList.size == 0) { 0 } { (seqList.collect { |subseq| timeMult * subseq[1] }.minItem % loopTime) }
			};
			var actual = Pn(this.seqParamsForLoop(true, nil, begShift, returnDict));
			if(begShift != 0) {
				Pseq([this.prRestValue(begShift, keyList, returnDict), actual])
			} { actual }
		}
	}
}
