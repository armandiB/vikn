// reCurrent — a beat: one pattern voice with a live-editable attribute set.
//
// Replaces BlockBeats' make_beat_pattern_new / make_beat_from_dict /
// set_attr_to_beat_env / delete_beat and the beat env. The pattern is a
// PbindProxy whose pairs are, in order:
//   [\rc_beat, this] ++ addFirst ++ [\dur_unadj, realDur, \dur, pipeline]
//   ++ [\midiout, ...] ++ [\chan, ...] ++ attrDict ++ [\rc_finish, clamps]
// wrapped in a guarding Prout that applies the error policy.
//
// Live edits: beat.set(\amp, Pwhite(0.1, 0.5))
//   - land on the next beat by default (editQuant = 1, like Pdef), or at the
//     next event with quant: nil, or on a grid with quant: 4
//   - dur is special: set(\dur, x) is set(\dur_flex, x): an array becomes an
//     editable RCDurList, a Function becomes Pn(Plazy(func)), anything else
//     is streamed as is. Dur edits always land at the next event.
// Every non-static value is mirrored: lastValue(key) is the last value the
// key produced, thread(key)/randData(key) its stream thread (per key when
// seeded).

RCBeat {
	classvar <>defaultEditQuant = 1;
	classvar <>defaultErrorPolicy = \restart;   // \restart or \stop
	classvar <>defaultMaxRestarts = 8;
	classvar <>defaultRestartDelay = 1;
	classvar <>minDur = 0.001;                 // durations <= 0 are clamped to this
	classvar <>maxClampedInARow = 64;          // then the beat stops itself
	classvar <hiddenKeys;
	classvar auxCounter = 0;

	var <layer, <name, <chan, <midiOut, <terminationKey;
	var <pbindProxy, <pattern, <player, <playQuant, <editQuant;
	var <durList, <>seqOffset = 0, <realDur, realDurProxy;
	var <lastValues, <threads, <keyOrder;
	var <timeTrack = 0, <lastSwingAdd = 0;
	var <errorPolicy, <>maxRestarts, <>restartDelay, <restartCount = 0;
	var <isFreed = false, <tag;
	var clampedInARow = 0;

	*initClass {
		Class.initClassTree(Event);
		hiddenKeys = IdentitySet[\rc_beat, \dur_unadj, \dur, \rc_finish, \midiout];
		Event.addEventType(\midiOnCtl, { |server|
			var original = currentEnvironment.copy.put(\type, \midi);
			~midicmd.do { |cmd| original.copy.put(\midicmd, cmd).play };
		});
	}

	*new { |layer, name, attrDict, chan, midiOut, seeds, addFirst, addFirstSeeds, terminationKey|
		^super.new.initRCBeat(layer, name, attrDict, chan, midiOut, seeds, addFirst, addFirstSeeds, terminationKey)
	}

	initRCBeat { |layerarg, namearg, attrDictarg, chanarg, midiOutarg, seedsarg, addFirstarg, addFirstSeedsarg, terminationKeyarg|
		var attrKV, addFirstKV, attrEvent, addFirstEvent;
		var overrides = ();          // type / types / midicmd overrides (midiOnCtl)
		var extraFirst = [];         // pairs prepended to addFirst (type, group, out)
		var extraFirstSeeds = [];
		var pairs;
		var type, midiOnCtl;

		layer = layerarg;
		name = namearg.asSymbol;
		chan = chanarg;
		midiOut = midiOutarg ?? { layer.midiOut };
		terminationKey = terminationKeyarg;
		tag = ("beat " ++ layer.songName ++ "/" ++ layer.key ++ "/" ++ name).asSymbol;
		editQuant = defaultEditQuant;
		errorPolicy = defaultErrorPolicy;
		maxRestarts = defaultMaxRestarts;
		restartDelay = defaultRestartDelay;
		lastValues = IdentityDictionary.new;
		threads = IdentityDictionary.new;
		keyOrder = List.new;

		attrKV = RCUtil.asKV(attrDictarg);
		addFirstKV = RCUtil.asKV(addFirstarg);
		attrEvent = attrKV.asEvent;
		addFirstEvent = addFirstKV.asEvent;
		playQuant = this.prCheckQuant(attrEvent[\quant] ?? { addFirstEvent[\quant] } ? 1);

		// midiOnCtl: a \midi (or composite) beat that also carries ctlNum
		type = attrEvent[\type] ?? { addFirstEvent[\type] };
		midiOnCtl = attrEvent.includesKey(\ctlNum) or: { addFirstEvent.includesKey(\ctlNum) };
		if(midiOnCtl) {
			var midicmd = attrEvent[\midicmd] ?? { addFirstEvent[\midicmd] };
			var changeMidicmd = false;
			if(type == \midi) { overrides[\type] = \midiOnCtl; changeMidicmd = true };
			if(type == \composite) {
				var types = attrEvent[\types] ?? { addFirstEvent[\types] } ? [];
				if(types.includes(\midi)) { changeMidicmd = true };
				overrides[\types] = types.replace(\midi, \midiOnCtl);
			};
			if(changeMidicmd) {
				overrides[\midicmd] = midicmd !? { |x|
					if(x.isKindOf(ArrayedCollection)) {
						if(x.includes(\control).not) { x.replace(\noteOn, [\noteOn, \control]) } { x }
					} {
						if(x == \noteOn) { [\noteOn, \control] } { x }
					}
				} ?? { [\noteOn, \control] };
			};
		};
		// backwards compatibility: a MIDI out + channel without a type is a \midi beat
		if(midiOut.notNil and: { chan.notNil } and: { type.isNil }) {
			extraFirst = extraFirst ++ [\type, if(midiOnCtl) { \midiOnCtl } { \midi }];
			extraFirstSeeds = extraFirstSeeds ++ [nil];
		};

		// addFirst: overrides, group/out resolution, termination
		addFirstKV = addFirstKV.collect { |el, i|
			var key;
			if(i.odd) {
				key = addFirstKV[i - 1];
				if(overrides.includesKey(key)) { el = overrides[key] };
				if(key == \orgnsm_group_idx) {
					extraFirst = extraFirst ++ [\group, this.prResolveGroup(el)];
					extraFirstSeeds = extraFirstSeeds ++ [nil];
				};
				if(key == \orgnsm_out_idx) {
					extraFirst = extraFirst ++ [\out, this.prResolveOut(el)];
					extraFirstSeeds = extraFirstSeeds ++ [nil];
				};
				this.prPrepareValue(key, el, this.prSeedAt(addFirstSeedsarg, (i - 1) div: 2, addFirstKV.size div: 2))
			} { el }
		};
		addFirstKV = extraFirst ++ addFirstKV;

		// the pattern pairs, in order
		pairs = [\rc_beat, this] ++ addFirstKV;
		realDurProxy = PatternProxy.new;
		realDurProxy.quant = nil;
		realDurProxy.clock = layer.clock;
		realDurProxy.setSource(this.prCheckQuant(playQuant)[0]);   // BlockBeats: env[real_dur] = quant[0]
		pairs = pairs ++ [\dur_unadj, realDurProxy, \dur, this.prDurPipeline];
		if(midiOut.notNil) { pairs = pairs ++ [\midiout, midiOut] };
		if(chan.notNil) { pairs = pairs ++ [\chan, chan] };

		// attrDict, in order; midiOnCtl overrides replace or extend it unless addFirst already carries the key
		overrides.keysValuesDo { |key, val|
			if(RCUtil.kvIncludesKey(attrKV, key) or: { RCUtil.kvIncludesKey(addFirstKV, key).not }) {
				attrKV = RCUtil.kvReplace(attrKV, key, val, addEndIfNotFound: true);
			};
		};
		attrKV.pairsDo { |key, val, i|
			var seed = this.prSeedFor(seedsarg, key, i div: 2);
			if(key != \quant) {
				pairs = pairs ++ this.prInitialPairs(key, val, seed);
			};
		};
		pairs = pairs ++ [\rc_finish, this.prFinishPfunc];

		pbindProxy = PbindProxy(*pairs);
		pbindProxy.pairs.pairsDo { |key, proxy|
			proxy.clock = layer.clock;
			proxy.quant = nil;
			if(hiddenKeys.includes(key).not and: { key != \chan }) { keyOrder.add(key) };
		};
		pbindProxy.source.quant = editQuant;
		pbindProxy.source.clock = layer.clock;   // JITLib defers key changes on this clock, never TempoClock.default
		pattern = this.prGuardedPattern;
	}

	//////// identity

	songName { ^layer.songName }
	song { ^layer.song }
	clock { ^layer.clock }
	seed { ^layer.seed }
	dereference { ^this }

	//////// value preparation

	// Wrap a user value: termination, mirroring of streamed values, seeding.
	prPrepareValue { |key, val, seed|
		if(val.isNil) { ^nil };
		if(terminationKey.notNil and: { key == terminationKey }) {
			val = Pseq([val, Pfunc { this.prScheduleFree; nil }]);
		};
		if(RCUtil.isStatic(val).not) {
			val = this.prMirror(key, val);
			if(seed.notNil) { val = Pseed(Pn(seed, 1), val) };
		};
		^val
	}

	prMirror { |key, pat|
		^Pcollect({ |v|
			lastValues[key] = v;
			threads[key] = thisThread;
			v
		}, pat)
	}

	// Pairs to declare at creation for one attribute (dur keys go to realDur).
	prInitialPairs { |key, val, seed|
		key = key.asSymbol;
		if(key == \dur) { key = \dur_flex };
		if(key == \dur_flex or: { key == \dur_list } or: { key == \real_dur }) {
			this.prSetDur(key, val, seed);
			^[]
		};
		if(hiddenKeys.includes(key)) {
			RCLog.warn(tag, "key % is owned by the beat and was ignored".format(key));
			^[]
		};
		RCUtil.warnIfReservedKey(key, tag);
		if(key == \orgnsm_group_idx) {
			^this.prResolveGroup(val) !? { |g| [\group, g, key, val] } ?? { [key, val] }
		};
		if(key == \orgnsm_out_idx) { ^[\out, this.prResolveOut(val), key, val] };
		^[key, this.prPrepareValue(key, val, seed)]
	}

	prSetDur { |key, val, seed|
		if(key == \dur_flex) {
			case
			{ val.isKindOf(Function) } { this.realDur_(this.prLoopPattern(val)) }
			{ val.isKindOf(SequenceableCollection) and: { val.isKindOf(RawArray).not } } { this.durList_(val, seed) }
			{ this.realDur_(this.prPrepareValue(\real_dur, val, seed)) };
			^this
		};
		if(key == \dur_list) { ^this.durList_(val, seed) };
		^this.realDur_(this.prPrepareValue(\real_dur, val, seed))
	}

	prResolveGroup { |idx|
		var group = layer.song.groupArray[idx];
		if(group.isNil) {
			RCLog.error(tag, "orgnsm_group_idx % is not in song.groupArray (size %), using the default group".format(idx, layer.song.groupArray.size));
			^nil
		};
		^group
	}

	prResolveOut { |idx|
		var out = layer.song.outArray[idx];
		if(out.isNil) {
			RCLog.error(tag, "orgnsm_out_idx % is not in song.outArray (size %), using out 0".format(idx, layer.song.outArray.size));
			^0
		};
		^out
	}

	// seeds: nil → layer seed for every key; a number → that seed for every
	// key; an Array → by position; a Dictionary → by key, \default fallback.
	prSeedFor { |seeds, key, index|
		if(seeds.isNil) { ^this.seed };
		if(seeds.isKindOf(Dictionary)) { ^seeds[key] ?? { seeds[\default] } };
		if(seeds.isKindOf(SequenceableCollection)) { ^seeds[index] };
		^seeds
	}

	prSeedAt { |seeds, index, size|
		if(seeds.isNil) { ^this.seed };
		if(seeds.isKindOf(SequenceableCollection)) { ^seeds[index] };
		^seeds
	}

	prCheckQuant { |quant|
		if(quant.isNumber) { quant = [quant, 0] };
		if(quant.isKindOf(SequenceableCollection).not or: { quant.size < 1 } or: { quant[0].isNumber.not } or: { quant[0] <= 0 }) {
			RCLog.warn(tag, "invalid quant %, using [1, 0]".format(quant));
			^[1, 0]
		};
		^quant
	}

	//////// dur pipeline

	// dur = dur_unadj + swing(timeTrack) - previous swing offset, clamped.
	prDurPipeline {
		^Pfunc { |ev|
			var durUnadj = ev[\dur_unadj];
			var d, swingAdd, durVal;
			if(durUnadj.isNil) { nil } {
				d = durUnadj.value;
				timeTrack = timeTrack + d;
				swingAdd = layer.swing.value(timeTrack);
				durVal = d + swingAdd - lastSwingAdd;
				lastSwingAdd = swingAdd;
				if(durVal.isNumber.not or: { durVal.isNaN } or: { durVal <= 0 }) {
					clampedInARow = clampedInARow + 1;
					if(clampedInARow > maxClampedInARow) {
						RCLog.error(tag, "% non-positive durations in a row, stopping the beat".format(clampedInARow), force: true);
						this.prScheduleFree;
						nil
					} {
						if(durUnadj.isRest and: { durVal == 0 }) {
							Rest(0)   // a zero-length rest is harmless (counted, not clamped)
						} {
							RCLog.warn(tag, "dur % (unadj %, swing %) clamped to %".format(durVal, d, swingAdd, minDur));
							durVal = minDur;
							if(durUnadj.isRest) { Rest(durVal) } { durVal }
						}
					}
				} {
					clampedInARow = 0;
					if(durUnadj.isRest) { Rest(durVal) } { durVal }
				}
			}
		}
	}

	// Last key: keep note lengths sane whatever the attributes produced.
	prFinishPfunc {
		^Pfunc { |ev|
			[\legato, \sustain].do { |k|
				var v = ev[k];
				if(v.isNumber and: { v.isNaN or: { v < 0 } }) {
					RCLog.warn(tag, "% % clamped to 0".format(k, v));
					ev[k] = 0;
				};
			};
			\rc
		}
	}

	//////// error policy

	// No non-local returns in here: inside a method, ^ would target the method.
	prGuardedPattern {
		^Prout { |inval|
			var stream = pbindProxy.asStream;
			var ev, failed, running = true;
			restartCount = 0;
			while { running } {
				failed = false;
				ev = try {
					stream.next(inval)
				} { |err|
					if(RCGuard.strict) { err.throw };
					failed = true;
					RCLog.exception(tag, err, "pattern error");
					nil
				};
				if(failed) {
					restartCount = restartCount + 1;
					if((errorPolicy == \restart) and: { restartCount <= maxRestarts }) {
						RCLog.warn(tag, "restarting in % beat(s) (attempt %/%)".format(restartDelay, restartCount, maxRestarts), force: true);
						stream = pbindProxy.asStream;
						inval = Event.silent(restartDelay, inval).yield;
					} {
						RCLog.error(tag, "stopped after % consecutive error(s)".format(restartCount), force: true);
						this.prScheduleFree;
						running = false;
					};
				} {
					if(ev.isNil) {
						this.prScheduleFree;
						running = false;
					} {
						restartCount = 0;
						inval = ev.yield;
					};
				};
			};
			inval
		}
	}

	// Pn(Plazy(func)) that cannot spin: a loop body yielding nothing rests 1 beat.
	prLoopPattern { |func|
		^Prout { |inval|
			loop {
				var pat = RCGuard.call(tag, nil) { func.value(inval) };
				var stream, v, n = 0;
				if(pat.isNil) {
					inval = Rest(1).yield;
				} {
					stream = pat.asStream;
					while { (v = stream.next(inval)).notNil } {
						n = n + 1;
						inval = v.yield;
					};
					if(n == 0) {
						RCLog.warn(tag, "dur pattern produced no value, resting 1 beat");
						inval = Rest(1).yield;
					};
				};
			};
		}
	}

	// Free from outside the pattern evaluation. AppClock is never stopped and
	// runs on the main thread, so this cannot fail on a stopped TempoClock.
	prScheduleFree {
		if(isFreed) { ^this };
		AppClock.sched(0, { RCGuard.call(tag, nil) { this.free(post: false) }; nil });
	}

	//////// live editing

	// quant: \default → editQuant; nil → next event; number → grid in beats.
	set { |key, val, seed, quant = \default|
		key = key.asSymbol;
		if(quant == \default) { quant = editQuant };
		if(key == \quant) { playQuant = this.prCheckQuant(val); ^this };
		if(key == \dur) { key = \dur_flex };
		if(key == \dur_flex or: { key == \dur_list } or: { key == \real_dur }) { ^this.prSetDur(key, val, seed) };
		if(hiddenKeys.includes(key)) { RCLog.warn(tag, "key % is owned by the beat and cannot be set".format(key)); ^this };
		if(key == \orgnsm_group_idx) { this.prResolveGroup(val) !? { |g| this.prSetKey(\group, g, quant) } };
		if(key == \orgnsm_out_idx) { this.prSetKey(\out, this.prResolveOut(val), quant) };
		RCUtil.warnIfReservedKey(key, tag);
		^this.prSetKey(key, this.prPrepareValue(key, val, seed), quant)
	}

	setAll { |pairs, seeds, quant = \default|
		RCUtil.asKV(pairs).pairsDo { |key, val, i| this.set(key, val, this.prSeedFor(seeds, key, i div: 2), quant) };
	}

	prSetKey { |key, val, quant|
		var proxy = pbindProxy.at(key);
		if(proxy.notNil) {
			if(val.isNil) {
				pbindProxy.source.quant = quant;
				pbindProxy.set(key, nil);
				keyOrder.remove(key);
				RCLog.info(tag, "removed key %: the pattern restarts".format(key));
			} {
				proxy.clock = layer.clock;
				proxy.quant = quant;
				proxy.setSource(val);
			};
			^this
		};
		if(val.isNil) { ^this };
		pbindProxy.source.quant = quant;
		pbindProxy.set(key, val);
		pbindProxy.at(key).clock_(layer.clock).quant_(nil);
		keyOrder.add(key);
		RCLog.info(tag, "added key % to a running beat: the pattern restarts (use reserveKeys to avoid this)".format(key));
	}

	// Declare keys up front (value \rc_reserved) so that later sets never rebuild the pattern.
	reserveKeys { |keys|
		keys.do { |key| if(pbindProxy.at(key.asSymbol).isNil) { this.prSetKey(key.asSymbol, \rc_reserved, nil) } };
	}

	editQuant_ { |q| editQuant = q; pbindProxy.source.quant = q }
	errorPolicy_ { |p| errorPolicy = p }

	durList_ { |array, seed|
		var pat;
		durList = RCDurList(array);
		seqOffset = 0;
		pat = this.prLoopPattern {
			if(durList.size == 0) {
				RCLog.warn(tag, "empty dur list, resting 1 beat");
				Pseq([Rest(1)], 1)
			} {
				Pseq(durList.array, 1, seqOffset)
			}
		};
		pat = this.prMirror(\real_dur, pat);
		if(seed.notNil) { pat = Pseed(Pn(seed, 1), pat) };
		^this.realDur_(pat)
	}

	realDur_ { |pat|
		realDur = pat;
		realDurProxy.setSource(pat);
	}

	//////// transport

	play { |quant|
		quant = this.prCheckQuant(quant ? playQuant);
		if(isFreed) { RCLog.warn(tag, "cannot play a freed beat"); ^this };
		if(this.isPlaying) { RCLog.warn(tag, "already playing"); ^this };
		player = pattern.play(layer.clock, quant: quant);
		^this
	}

	pause { player !? (_.pause) }
	resume { player !? { |p| p.resume(layer.clock) } }
	stop { player !? (_.stop) }
	isPlaying { ^player.notNil and: { player.isPlaying } }
	asStream { ^pattern.asStream }

	// No pbindProxy.clear here: EventPatternProxy.clear schedules deferred work
	// on its clock, which could touch the dead stream later. Dropping the
	// player is enough; the proxy is garbage once unreferenced.
	free { |post = true|
		if(isFreed) { ^this };
		isFreed = true;
		RCGuard.call(tag, nil) { this.stop };
		player = nil;
		layer.unregisterBeat(this);
		if(post) { RCLog.post(tag, "freed") };
	}

	//////// introspection

	lastValue { |key, default| ^lastValues[key.asSymbol] ?? { default.value } }
	thread { |key| ^threads[key.asSymbol] }
	randData { |key| ^threads[key.asSymbol] !? (_.randData) }
	randData_ { |key, data| threads[key.asSymbol] !? { |t| t.randData = data } }
	keyProxy { |key| ^pbindProxy.at(key.asSymbol) }

	// Pfunc reading another beat's last value (BlockBeats' access_beat_value).
	*valuePattern { |layer, beatName, key, default, overrideNil = false|
		^Pfunc {
			var b = layer.beat(beatName);
			var v;
			if(b.isNil) {
				RCLog.warn(\valuePattern, "beat % not found in %".format(beatName, layer));
				default.value
			} {
				v = b.lastValue(key, default);
				if(overrideNil) { v ?? { default.value } } { v }
			}
		}
	}

	// A pattern that spawns a fresh, self-terminating beat at every event
	// (BlockBeats' make_aux_beat). attrDictFunc receives the parent event.
	*auxPattern { |layer, namePrefix, terminationKey, attrDictFunc, chan, seeds, addFirst, addFirstSeeds|
		^Pn(Pfunc { |ev|
			var auxName, attrDict, seedsNew;
			auxCounter = auxCounter + 1;
			auxName = (namePrefix.asString ++ "_" ++ auxCounter).asSymbol;
			attrDict = RCGuard.call(\auxBeat, nil) { attrDictFunc.value(ev) };
			if(attrDict.isNil) { \skipped } {
				seedsNew = if(seeds.isKindOf(Function)) { seeds.value(ev) } { seeds };
				layer.addBeat(auxName, attrDict, chan: chan, seeds: seedsNew ? \none, addFirst: addFirst,
					addFirstSeeds: addFirstSeeds ? \none, terminationKey: terminationKey, post: false);
				auxName
			}
		})
	}

	printOn { |stream|
		stream << "RCBeat(" << this.songName << "/" << layer.key << "/" << name << ")"
	}
}
