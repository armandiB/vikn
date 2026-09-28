// reCurrent — a beat: one pattern voice with a live-editable attribute set.
//
// Replaces BlockBeats' make_beat_pattern_new / make_beat_from_dict /
// set_attr_to_beat_env / delete_beat and the beat env. The pattern is an
// RCPbindProxy (an RCKeyProxy per key, see there) whose pairs are, in order:
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
// key produced, thread(key) / randData(key) its Routine and random state (one
// Routine per key, seeded per key). A Function value is called per event; a
// key given twice keeps its later definition (warned).

RCBeat {
	classvar <>defaultEditQuant = 1;
	classvar <>defaultErrorPolicy = \restart;   // \restart or \stop
	classvar <>defaultMaxRestarts = 8;
	classvar <>defaultRestartDelay = 1;
	classvar <>minDur = 0.001;                 // durations <= 0 are clamped to this
	classvar <>maxClampedInARow = 64;          // then the beat stops itself
	classvar <>maxAuxBeatsPerLayer = 256;      // auxPattern stops spawning above this
	classvar <hiddenKeys;
	classvar auxCounter = 0;

	// Lag monitor (opt-in): how far behind logical time the interpreter runs
	// when an event is about to play, Main.elapsedTime - thisThread.seconds,
	// measured in the last key (rc_finish). scsynth prints "late" once this
	// exceeds server.latency; above lagWarnRatio * latency a rate-limited
	// warning names the beat. lagReset before a section, lagReport after.
	classvar <>lagMonitor = false;
	classvar <>lagWarnRatio = 0.8;
	classvar <lagMax = 0, <lagMaxTag, <lagCount = 0, <lagLateCount = 0, <lagSum = 0, <lagByTag;

	var <layer, <name, <chan, <midiOut, <terminationKey;
	var <pbindProxy, <pattern, <player, <playQuant, <editQuant;
	var <durList, <>seqOffset = 0, <realDur, realDurProxy;
	var <keyOrder;
	var <timeTrack = 0, <lastSwingAdd = 0;
	var <errorPolicy, <>maxRestarts, <>restartDelay, <restartCount = 0;
	var <isFreed = false, <tag;
	var clampedInARow = 0;

	*initClass {
		Class.initClassTree(Event);
		hiddenKeys = IdentitySet[\rc_beat, \dur_unadj, \dur, \rc_finish, \midiout];
		lagByTag = IdentityDictionary.new;
		Event.addEventType(\midiOnCtl, { |server|
			var original = currentEnvironment.copy.put(\type, \midi);
			~midicmd.do { |cmd| original.copy.put(\midicmd, cmd).play };
		});
	}

	// logTag: the RCLog tag (default "beat <song>/<layer>/<name>"); aux beats
	// share their parent's so the limiter does not grow with every spawn.
	*new { |layer, name, attrDict, chan, midiOut, seeds, addFirst, addFirstSeeds, terminationKey, logTag|
		^super.new.initRCBeat(layer, name, attrDict, chan, midiOut, seeds, addFirst, addFirstSeeds, terminationKey, logTag)
	}

	initRCBeat { |layerarg, namearg, attrDictarg, chanarg, midiOutarg, seedsarg, addFirstarg, addFirstSeedsarg, terminationKeyarg, logTagarg|
		var attrKV, addFirstKV, attrEvent, addFirstEvent;
		var overrides = ();          // type / types / midicmd overrides (midiOnCtl)
		var extraFirst = [];         // pairs prepended to addFirst (type, group, out)
		var extraFirstSeeds = [];
		var pairs, initialDur;
		var type, midiOnCtl;

		layer = layerarg;
		name = namearg.asSymbol;
		chan = chanarg;
		midiOut = midiOutarg ?? { layer.midiOut };
		terminationKey = terminationKeyarg;
		tag = logTagarg ?? { ("beat " ++ layer.songName ++ "/" ++ layer.key ++ "/" ++ name).asSymbol };
		editQuant = defaultEditQuant;
		errorPolicy = defaultErrorPolicy;
		maxRestarts = defaultMaxRestarts;
		restartDelay = defaultRestartDelay;
		keyOrder = List.new;

		attrKV = RCUtil.asKV(attrDictarg, tag);
		addFirstKV = RCUtil.asKV(addFirstarg, tag);
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
			extraFirst = extraFirst ++ [\type, this.prProxy(\type, if(midiOnCtl) { \midiOnCtl } { \midi })];
		};

		// addFirst: overrides, group/out resolution, termination; every value becomes an RCKeyProxy
		addFirstKV = addFirstKV.collect { |el, i|
			var key;
			if(i.odd) {
				key = addFirstKV[i - 1];
				if(overrides.includesKey(key)) { el = overrides[key] };
				if(key == \orgnsm_group_idx) {
					extraFirst = extraFirst ++ [\group, this.prProxy(\group, this.prResolveGroup(el))];
				};
				if(key == \orgnsm_out_idx) {
					extraFirst = extraFirst ++ [\out, this.prProxy(\out, this.prResolveOut(el))];
				};
				this.prProxy(key, this.prPrepareValue(key, el), this.prSeedAt(addFirstSeedsarg, (i - 1) div: 2, addFirstKV.size div: 2))
			} { el }
		};
		addFirstKV = extraFirst ++ addFirstKV;

		// the pattern pairs, in order; the beat's own keys are neither mirrored nor seeded
		pairs = [\rc_beat, this.prProxy(\rc_beat, this, nil, false)] ++ addFirstKV;
		initialDur = playQuant[0];                                  // BlockBeats: env[real_dur] = quant[0]
		realDurProxy = RCKeyProxy(\real_dur, if(initialDur <= 0) { 1 } { initialDur }, nil, layer.clock);   // quant 0 ("now") still needs a positive dur
		pairs = pairs ++ [\dur_unadj, realDurProxy, \dur, this.prProxy(\dur, this.prDurPipeline, nil, false)];
		if(midiOut.notNil) { pairs = pairs ++ [\midiout, this.prProxy(\midiout, midiOut, nil, false)] };
		if(chan.notNil) { pairs = pairs ++ [\chan, this.prProxy(\chan, chan, nil, false)] };

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
		pairs = this.prDedupePairs(pairs ++ [\rc_finish, this.prProxy(\rc_finish, this.prFinishPfunc, nil, false)]);

		// key additions and removals are deferred on the layer's clock, never TempoClock.default
		pbindProxy = RCPbindProxy(pairs, \rc_finish, layer.clock, editQuant);
		pbindProxy.keys.do { |key| if(hiddenKeys.includes(key).not and: { key != \chan }) { keyOrder.add(key) } };
		pattern = this.prGuardedPattern;
	}

	prProxy { |key, val, seed, mirror = true|
		^RCKeyProxy(key, val, seed, layer.clock, mirror)
	}

	//////// identity

	songName { ^layer.songName }
	song { ^layer.song }
	clock { ^layer.clock }
	seed { ^layer.seed }
	dereference { ^this }

	//////// value preparation

	// A key given twice (the beat's own \chan and a user \chan, \group from
	// addFirst and from the attributes) keeps its later definition, where
	// it stands: PbindProxy would otherwise edit the inert first one.
	prDedupePairs { |pairs|
		var seen = IdentitySet.new, kept = List.new;
		if(RCUtil.kvKeys(pairs).asSet.size * 2 == pairs.size) { ^pairs };
		forBy(pairs.size - 2, 0, -2) { |i|
			var key = pairs[i];
			if(seen.includes(key)) {
				RCLog.warn(tag, "key % given twice: the later definition is used".format(key));
			} {
				seen.add(key);
				kept.add([key, pairs[i + 1]]);
			};
		};
		^kept.reverse.flatten(1)
	}

	// Prepare a user value: nil stays nil (the key is skipped), a termination
	// key gets its ending. Streaming, mirroring and seeding are RCKeyProxy's
	// (a Function is called per event; a bare Function would reach the synth).
	prPrepareValue { |key, val|
		if(val.isNil) { ^nil };
		if(terminationKey.notNil and: { key == terminationKey }) {
			if(val.isKindOf(Function)) { val = Pfunc(val) };
			val = Pseq([val, Pfunc { this.prScheduleFree; nil }]);
		};
		^val
	}

	// Pairs to declare at creation for one attribute (dur keys go to realDur).
	// A nil value means "not set" (a template key deleted with deleteAttrs).
	prInitialPairs { |key, val, seed|
		key = key.asSymbol;
		if(val.isNil) { ^[] };
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
			^this.prResolveGroup(val) !? { |g| [\group, this.prProxy(\group, g), key, this.prProxy(key, val)] } ?? { [key, this.prProxy(key, val)] }
		};
		if(key == \orgnsm_out_idx) { ^[\out, this.prProxy(\out, this.prResolveOut(val)), key, this.prProxy(key, val)] };
		^[key, this.prProxy(key, this.prPrepareValue(key, val), seed)]
	}

	prSetDur { |key, val, seed|
		if(key == \dur_flex) {
			case
			{ val.isKindOf(Function) } { this.realDur_(this.prLoopPattern(val)) }
			{ val.isKindOf(SequenceableCollection) and: { val.isKindOf(RawArray).not } } { this.durList_(val, seed) }
			{ this.realDur_(this.prPrepareValue(\real_dur, val), seed) };
			^this
		};
		if(key == \dur_list) { ^this.durList_(val, seed) };
		^this.realDur_(this.prPrepareValue(\real_dur, val), seed)
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

	// 0 is valid ("now": nextTimeOnGrid returns the current beat).
	prCheckQuant { |quant|
		if(quant.isNumber) { quant = [quant, 0] };
		if(quant.isKindOf(SequenceableCollection).not or: { quant.size < 1 } or: { quant[0].isNumber.not } or: { quant[0] < 0 }) {
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
							RCLog.warn(tag, { "dur % (unadj %, swing %) clamped to %".format(durVal, d, swingAdd, minDur) });
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
					RCLog.warn(tag, { "% % clamped to 0".format(k, v) });
					ev[k] = 0;
				};
			};
			if(lagMonitor) { this.prRecordLag };
			\rc
		}
	}

	//////// lag monitor

	*lagReset {
		lagMax = 0;
		lagMaxTag = nil;
		lagCount = 0;
		lagLateCount = 0;
		lagSum = 0;
		lagByTag = IdentityDictionary.new;
	}

	// One line: the worst event and its beat, the mean, how many events crossed
	// lagWarnRatio of the server latency.
	*lagReport {
		var mean = if(lagCount > 0) { lagSum / lagCount } { 0 };
		^"lag: max % ms (%), mean % ms, % of % events over % of the latency".format(
			(lagMax * 1000).round(0.1), lagMaxTag, (mean * 1000).round(0.01), lagLateCount, lagCount, lagWarnRatio)
	}

	// Seconds the interpreter runs behind this event's logical time.
	prRecordLag {
		var lag = Main.elapsedTime - thisThread.seconds;
		var latency = layer.server.latency;
		lagCount = lagCount + 1;
		lagSum = lagSum + lag;
		if(lag > lagMax) { lagMax = lag; lagMaxTag = tag };
		if(lag > (lagByTag[tag] ? 0)) { lagByTag[tag] = lag };
		if(latency.notNil and: { lag > (lagWarnRatio * latency) }) {
			lagLateCount = lagLateCount + 1;
			RCLog.warn(\lag, { "% runs % ms behind its logical time (latency % ms)".format(tag, (lag * 1000).round(0.1), (latency * 1000).round(0.1)) });
		};
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
						timeTrack = timeTrack + restartDelay;   // the swing phase follows the silent gap
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
		^this.prSetKey(key, this.prPrepareValue(key, val), quant, seed)
	}

	setAll { |pairs, seeds, quant = \default|
		RCUtil.asKV(pairs).pairsDo { |key, val, i| this.set(key, val, this.prSeedFor(seeds, key, i div: 2), quant) };
	}

	// An existing key edits itself (RCKeyProxy.setSource, on `quant`); adding or
	// removing one rebuilds the pattern once (RCPbindProxy, rc_finish kept last).
	prSetKey { |key, val, quant, seed|
		var proxy = pbindProxy.at(key);
		if(proxy.notNil) {
			if(val.isNil) {
				pbindProxy.remove(key, quant);
				keyOrder.remove(key);
				RCLog.info(tag, { "removed key %: the pattern restarts".format(key) });
			} {
				proxy.clock = layer.clock;
				proxy.setSource(val, quant, seed);
			};
			^this
		};
		if(val.isNil) { ^this };
		pbindProxy.add(key, this.prProxy(key, val, seed), quant);
		keyOrder.add(key);
		if(this.isPlaying) {
			RCLog.info(tag, { "added key % to a running beat: the pattern restarts (use reserveKeys to avoid this)".format(key) });
		} {
			RCLog.info(tag, { "added key %".format(key) });
		};
	}

	// Declare keys up front so that later sets never rebuild the pattern. The
	// placeholder is the key's numeric Event default (\db → -20, \legato → 0.8),
	// else 0: a plain synth argument, never a Symbol reaching the server. One
	// rebuild for all the keys.
	reserveKeys { |keys|
		var missing = keys.collect(_.asSymbol).reject { |key| pbindProxy.includesKey(key) };
		if(missing.isEmpty) { ^this };
		pbindProxy.addAll(missing.collect { |key| [key, this.prProxy(key, this.class.reservedValue(key))] }.flatten(1), nil);
		missing.do { |key| keyOrder.add(key) };
		RCLog.info(tag, { "reserved keys %".format(missing) });
	}

	*reservedValue { |key|
		var default = Event.default[key.asSymbol];
		^if(default.isNumber) { default } { 0 }
	}

	editQuant_ { |q| editQuant = q; pbindProxy.quant = q }
	errorPolicy_ { |p| errorPolicy = p }

	durList_ { |array, seed|
		durList = RCDurList(array);
		seqOffset = 0;
		^this.realDur_(this.prLoopPattern {
			if(durList.size == 0) {
				RCLog.warn(tag, "empty dur list, resting 1 beat");
				Pseq([Rest(1)], 1)
			} {
				Pseq(durList.array, 1, seqOffset)
			}
		}, seed)
	}

	// The dur source, landing at the next event (mirrored under \real_dur).
	realDur_ { |pat, seed|
		realDur = pat;
		realDurProxy.setSource(pat, nil, seed);
	}

	//////// transport

	// quant: nil → the beat's playQuant; a given quant becomes the playQuant.
	play { |quant|
		if(isFreed) { RCLog.warn(tag, "cannot play a freed beat"); ^this };
		if(this.isPlaying) { RCLog.warn(tag, "already playing"); ^this };
		if(quant.notNil) { playQuant = this.prCheckQuant(quant) };
		player = pattern.play(layer.clock, quant: playQuant);
		^this
	}

	pause { player !? (_.pause) }
	resume { player !? { |p| p.resume(layer.clock) } }
	stop { player !? (_.stop) }
	isPlaying { ^player.notNil and: { player.isPlaying } }

	// A second stream shares the dur pipeline state (timeTrack, swing) with the player.
	asStream {
		if(this.isPlaying) { RCLog.warn(tag, "asStream on a playing beat: both streams advance the same swing phase") };
		^pattern.asStream
	}

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

	lastValue { |key, default| ^this.prProxyFor(key) !? (_.lastValue) ?? { default.value } }
	thread { |key| ^this.prProxyFor(key) !? (_.thread) }
	randData { |key| ^this.prProxyFor(key) !? (_.randData) }
	randData_ { |key, data| this.prProxyFor(key) !? (_.randData_(data)) }
	keyProxy { |key| ^pbindProxy.at(key.asSymbol) }

	// key → last value of every mirrored key; key → thread of every Pattern key.
	lastValues {
		var res = pbindProxy.lastValues;
		realDurProxy.lastValue !? { |v| res[\real_dur] = v };
		^res
	}

	threads {
		var res = pbindProxy.threads;
		realDurProxy.thread !? { |t| res[\real_dur] = t };
		^res
	}

	prProxyFor { |key|
		key = key.asSymbol;
		^if(key == \real_dur) { realDurProxy } { pbindProxy.at(key) }
	}

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
	// The aux beat starts at once (quant 0) and must carry terminationKey,
	// else it would never end: such a dict is refused with an error. Above
	// maxAuxBeatsPerLayer live beats nothing is spawned (error, \skipped).
	*auxPattern { |layer, namePrefix, terminationKey, attrDictFunc, chan, seeds, addFirst, addFirstSeeds|
		var logTag = ("beat " ++ layer.songName ++ "/" ++ layer.key ++ "/" ++ namePrefix ++ "_aux").asSymbol;
		^Pn(Pfunc { |ev|
			var auxName, attrDict, seedsNew;
			attrDict = RCGuard.call(logTag, nil) { attrDictFunc.value(ev) };
			case
			{ attrDict.isNil } { \skipped }
			{ terminationKey.notNil and: { RCUtil.kvIncludesKey(RCUtil.asKV(attrDict), terminationKey).not } } {
				RCLog.error(logTag, "aux beat has no % key, it would never end: not created".format(terminationKey));
				\skipped
			}
			{ layer.beats.size >= maxAuxBeatsPerLayer } {
				RCLog.error(logTag, "% live beats in the layer: aux beat not created".format(layer.beats.size));
				\skipped
			}
			{
				auxCounter = auxCounter + 1;
				auxName = (namePrefix.asString ++ "_" ++ auxCounter).asSymbol;
				seedsNew = if(seeds.isKindOf(Function)) { seeds.value(ev) } { seeds };
				layer.addBeat(auxName, attrDict, chan: chan, seeds: seedsNew ? \none, addFirst: addFirst,
					addFirstSeeds: addFirstSeeds ? \none, terminationKey: terminationKey, quant: 0, post: false, logTag: logTag);
				auxName
			}
		})
	}

	printOn { |stream|
		stream << "RCBeat(" << this.songName << "/" << layer.key << "/" << name << ")"
	}
}
