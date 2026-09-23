// reCurrent — an orgnsm: a beat template with identity, state and resources
// (BlockOrgnsm's ~make_new_orgnsm prototype).
//
// An RCOrgnsm is a template; create() returns a registered clone that is
// prepared (an RCBeatSpec) and started with .start. Identity is
// species / tribe / number → name "orgnsm_<species>_t<tribe>_<number>",
// which is the beat name in its layer and the OSC instrument name.
//
//   ~tpl = RCOrgnsm(\sampler, 0, 0, ~song);
//   ~tpl.addStaticAttrs((seed: 1994, loop_time: 4, quant: { |self| [self.loop_time, 0] }, bufrate: 1));
//   ~tpl.attrDictBase = [type: \note, instrument_flex: \PlayBuf, bufrate: Pfunc { |ev| ev.self.staticAttrs.bufrate }];
//   ~o = ~tpl.create(layerKey: \core, replaceAttrs: ("bufrate": 0.5, "attr_dict_base.amp": 0.2)).start;
//   ~o.rPut("bufrate", 2);   // static attrs are read at every event
//
// Key paths: "x" → staticAttrs.x; "attr_dict_base.x" / "attrDictBase.x",
// "add_first_array_base.x" / "addFirstArrayBase.x", "static_attrs.x",
// "server_ressources.x" / "serverResources.x".

RCOrgnsm {
	classvar <slotAliases;

	var <species, <tribe, <number, <song, <addSongInName;
	var <staticAttrs, <attrDictBase, <addFirstArrayBase;
	var <serverResources, <freeFunctions, <freeFunctionsAlways;
	var <>layerKey, <>terminationKey, <>chan, <>parentObject, <>batch, <>batchKey;
	var <spec, <beat, <isRegistered = false, <isFreed = false;
	var <>isCrawlerPattern = false;

	*initClass {
		slotAliases = IdentityDictionary[
			\static_attrs -> \staticAttrs, \staticAttrs -> \staticAttrs,
			\attr_dict_base -> \attrDictBase, \attrDictBase -> \attrDictBase,
			\add_first_array_base -> \addFirstArrayBase, \addFirstArrayBase -> \addFirstArrayBase,
			\server_ressources -> \serverResources, \server_resources -> \serverResources, \serverResources -> \serverResources
		];
	}

	*new { |species, tribe = 0, number = 0, song, addSongInName = false|
		^super.new.initRCOrgnsm(species, tribe, number, song, addSongInName)
	}

	initRCOrgnsm { |speciesarg, tribearg, numberarg, songarg, addSongInNamearg|
		species = speciesarg.asSymbol;
		tribe = tribearg;
		number = numberarg;
		song = songarg;
		addSongInName = addSongInNamearg;
		staticAttrs = (
			o_species: species,
			tribe: tribe,
			orgnsm: number,
			song_name: song !? (_.name),
			song_seed: song !? (_.seed),
			tribe_name: { |self| "orgnsm_" ++ self.o_species.asString ++ "_t" ++ self.tribe.asString },
			orgnsm_name: if(addSongInName) {
				{ |self| self.song_name.asString ++ "_" ++ self.tribe_name ++ "_" ++ self.orgnsm.asString }
			} {
				{ |self| self.tribe_name ++ "_" ++ self.orgnsm.asString }
			}
		);
		serverResources = ();
		freeFunctions = ();
		freeFunctionsAlways = ();
	}

	//////// identity

	number_ { |n| number = n; staticAttrs[\orgnsm] = n }
	tribe_ { |t| tribe = t; staticAttrs[\tribe] = t }
	species_ { |s| species = s.asSymbol; staticAttrs[\o_species] = species }
	tribeName { ^staticAttrs.tribe_name }
	name { ^staticAttrs.orgnsm_name.asSymbol }
	layer { ^layerKey !? { song.layer(layerKey) } }
	registry { ^song.registry }
	dereference { ^this }
	isOrgnsm { ^true }
	isFobject { ^false }
	isCrawler { ^isCrawlerPattern }
	seed { ^staticAttrs[\seed] }

	// snake_case aliases for pattern code ported from the proto-library
	static_attrs { ^staticAttrs }
	server_ressources { ^serverResources }

	//////// attributes

	staticAttrs_ { |event| staticAttrs = event }
	attrDictBase_ { |kvOrEvent| attrDictBase = kvOrEvent }
	addFirstArrayBase_ { |kvOrEvent| addFirstArrayBase = kvOrEvent }

	addStaticAttrs { |event|
		event.keysValuesDo { |k, v| RCUtil.warnIfReservedKey(k, \orgnsm) };
		staticAttrs.putAll(event);
	}

	// "a.b.c" or [\a, \b, \c] → [slot, keys...]; a bare key defaults to staticAttrs.
	convertKey { |key|
		var keys, slot;
		if(key.isKindOf(String)) { keys = key.split($.).collect(_.asSymbol) } {
			if(key.isKindOf(SequenceableCollection)) { keys = key.collect(_.asSymbol) } { keys = [key.asSymbol] };
		};
		slot = slotAliases[keys[0]];
		if(slot.isNil) { ^[\staticAttrs] ++ keys };
		^[slot] ++ keys[1..]
	}

	rPut { |key, val|
		var keys = this.convertKey(key);
		var slot = keys[0];
		var rest = keys[1..];
		if(rest.size == 0) {
			if(slot == \staticAttrs) { RCLog.error(\orgnsm, "rPut: cannot replace staticAttrs itself"); ^this };
			this.perform(slot.asSetter, val);
			^this
		};
		if(slot == \attrDictBase or: { slot == \addFirstArrayBase }) {
			var base = this.perform(slot);
			if(rest.size > 1) { RCLog.error(\orgnsm, "rPut: nested paths are not supported inside %: %".format(slot, key)); ^this };
			if(base.isNil) { base = [] };
			if(base.isKindOf(Dictionary)) {
				base.put(rest[0], val);
			} {
				base = RCUtil.kvReplace(base, rest[0], val, addEndIfNotFound: true);
				this.perform(slot.asSetter, base);
			};
			^this
		};
		if(slot == \staticAttrs and: { rest.size == 1 }) {
			// identity keys live in ivars too
			switch(rest[0],
				\tribe, { ^this.tribe_(val) },
				\orgnsm, { ^this.number_(val) },
				\o_species, { ^this.species_(val) }
			);
			RCUtil.warnIfReservedKey(rest[0], \orgnsm);
		};
		RCUtil.rPut(this.perform(slot), rest, val);
	}

	rGet { |key|
		var keys = this.convertKey(key);
		var slot = keys[0];
		var rest = keys[1..];
		var base = this.perform(slot);
		if(rest.size == 0) { ^base };
		if(slot == \attrDictBase or: { slot == \addFirstArrayBase }) {
			if(base.isKindOf(Dictionary)) { ^base[rest[0]] };
			^RCUtil.kvAt(base ? [], rest[0])
		};
		^RCUtil.rGet(base, rest)
	}

	//////// server resources

	setBuffer { |buffer, freePrevious = false, alwaysFreeAtDelete = false|
		if(freePrevious) { this.freeBuffer };
		serverResources[\buffer] = buffer;
		freeFunctions[\buffer] = { buffer.free };
		if(alwaysFreeAtDelete) { freeFunctionsAlways[\buffer] = { buffer.free } };
	}

	freeBuffer {
		freeFunctions[\buffer] !? { |f| RCGuard.call(\orgnsm, nil) { f.value(this) } };
		freeFunctions[\buffer] = nil;
		freeFunctionsAlways[\buffer] = nil;
		serverResources[\buffer] = nil;
	}

	freeAllServerResources {
		freeFunctions.do { |f| RCGuard.call(\orgnsm, nil) { f.value(this) } };
	}

	freeAlwaysServerResources {
		freeFunctionsAlways.do { |f| RCGuard.call(\orgnsm, nil) { f.value(this) } };
	}

	//////// pattern material

	// [\self, this] ++ addFirstArrayBase
	addFirstArray {
		^[\self, this] ++ RCUtil.asKV(addFirstArrayBase)
	}

	// [\quant, ...] ++ attrDictBase ++ fobject routing (when instrument_flex is present)
	attrDict {
		var res = [\quant, staticAttrs.quant];
		var base = RCUtil.asKV(attrDictBase);
		res = res ++ base;
		if(RCUtil.kvIncludesKey(base, \instrument_flex) and: { isCrawlerPattern.not }) {
			res = res ++ [
				\outs_and_transparencies, this.prRoutingPattern,
				\outs, Pfunc { |ev| ev[\outs_and_transparencies][0] },
				\outamps, Pfunc { |ev| ev[\outs_and_transparencies][1] },
				\instrument, Pfunc { |ev| RCSynthDefs.outputSuffix(ev[\instrument_flex], ev[\outs_and_transparencies][0].size) }
			];
		};
		^res
	}

	// Per event: which fobject buses receive the sound, with which weights.
	// Fobjects are taken by increasing priority until the transparency budget
	// reaches 1; the remainder goes to the orgnsm's own out. A transparency
	// function that fails counts as 0 (fobject ignored).
	prRoutingPattern {
		^Pfunc { |ev|
			var fobjects = song.registry.fobjects;
			var out = ev[\out] ? 0;
			if(ev[\dur].isRest or: { fobjects.size == 0 }) {
				[[out], [1]]
			} {
				var ignore = staticAttrs[\ignore_fobject_names] ? [];
				var stats = List.new;
				var sorted, budget = 0, current = 0, next = 0, idx = 0;
				var keptBuses = List.new, keptTransparencies = List.new;
				fobjects.do { |fobject|
					var t = RCGuard.call(\transparency, 0) { fobject.transparency(ev[\zpos], ev[\pos]) };
					if(t.isNumber.not or: { t.isNaN }) { t = 0 };
					if((t > 0) and: { ignore.includes(fobject.name).not }) { stats.add([fobject.inBus, fobject.priority, t]) };
				};
				sorted = stats.sort { |a, b| a[1] < b[1] };
				while { ((budget < 1) or: { next == current }) and: { idx < sorted.size } } {
					budget = budget + sorted[idx][2];
					keptBuses.add(sorted[idx][0]);
					keptTransparencies.add(sorted[idx][2]);
					current = sorted[idx][1];
					idx = idx + 1;
					if(idx < sorted.size) { next = sorted[idx][1] };
				};
				case
				{ budget > 1 } { keptTransparencies = keptTransparencies.asArray.normalizeSum }
				{ budget < 1 } { keptBuses.add(out); keptTransparencies.add(1 - budget) };
				[keptBuses.asArray, keptTransparencies.asArray]
			}
		}
	}

	//////// lifecycle

	clone {
		var c = this.class.new(species, tribe, number, song, addSongInName);
		c.staticAttrs_(staticAttrs.copy);
		c.attrDictBase_(attrDictBase.deepCopy);
		c.addFirstArrayBase_(addFirstArrayBase.deepCopy);
		c.prCopyResources(serverResources.copy, freeFunctions.copy, freeFunctionsAlways.copy);
		c.layerKey = layerKey;
		c.terminationKey = terminationKey;
		c.chan = chan;
		c.parentObject = parentObject;
		c.isCrawlerPattern = isCrawlerPattern;
		^c
	}

	prCopyResources { |resources, frees, freesAlways|
		serverResources = resources;
		freeFunctions = frees;
		freeFunctionsAlways = freesAlways;
	}

	register { |pickNewNumber = true|
		song.registry.register(this, pickNewNumber);
		isRegistered = true;
	}

	unregister {
		if(isRegistered) { song.registry.remove(this) };
		isRegistered = false;
	}

	tribeDict { ^song.registry.tribe(species, tribe) }

	// A registered, prepared clone. Start it with .start (or .value).
	create { |layerKey = \core, terminationKey, replaceAttrs, deleteAttrs, osc = false, pickNewNumber = true, oscNames, chan, batch, batchKey|
		var c = this.clone;
		c.layerKey = layerKey;
		c.terminationKey = terminationKey;
		c.chan = chan;
		c.batch = batch;
		c.batchKey = batchKey;
		replaceAttrs !? { |r| r.keysValuesDo { |k, v| c.rPut(k, v) } };
		deleteAttrs !? { |d| d.do { |k| c.rPut(k, nil) } };
		c.register(pickNewNumber);
		c.prepare(osc, oscNames);
		^c
	}

	prepare { |osc = false, oscNames|
		var layer = song.layer(layerKey);
		if(layer.isNil) { RCLog.error(\orgnsm, "% prepare: no layer % in song %".format(this.name, layerKey, song.name)); ^nil };
		spec = RCBeatSpec(layer, this.name, this.attrDict, chan, this.seed, this.addFirstArray, this.seed, terminationKey, { this.unregister });
		if(osc) { song.osc.startDefs(spec, this.name, oscNames) };
		^spec
	}

	start { |quant|
		if(isFreed) { RCLog.warn(\orgnsm, "% is freed, cannot start".format(this.name)); ^nil };
		if(spec.isNil) { this.prepare };
		if(spec.isNil) { ^nil };
		beat = spec.start(quant);
		^beat
	}

	value { ^this.start }
	isStarted { ^beat.notNil and: { beat.isFreed.not } }
	isPlaying { ^beat.notNil and: { beat.isPlaying } }

	// Stops the beat, unregisters, frees resources marked "always" (or all).
	free { |freeAllServerResources = false|
		if(isFreed) { ^false };
		isFreed = true;
		if(freeAllServerResources) { this.freeAllServerResources } { this.freeAlwaysServerResources };
		this.unregister;
		beat !? { |b| b.free(post: false) };
		^true
	}

	delete { |freeAllServerResources = false| ^this.free(freeAllServerResources) }

	printOn { |stream| stream << "RCOrgnsm(" << this.name << ")" }
}
