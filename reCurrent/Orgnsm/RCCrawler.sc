// reCurrent — a crawler: an agent that owns one or several attributes of one
// orgnsm at a time and jumps to the next orgnsm under the control of its own
// (silent) pattern orgnsm. Port of BlockOrgnsm's ~make_new_crawler.
//
//   ~c = RCCrawler(["static_attrs.seq_list"]);
//   ~c.patternAttrs = (tribe: 0, static_attrs: (rhythm_dict_target: ~rd, tolerance_change_subseq: 1, ...),
//       attr_dict_base: [dur_flex: 4, next_val: RCCrawlerMoves.nextValRhythmChange],
//       add_first_array_base: []);
//   ~c.initFromBatch(~batch, 0);
//   ~c.nextOrgnsm = ~batch.orgnsms(1)[0];
//   ~c.createPattern(layerKey: \core);
//
// The pattern orgnsm's attr dict runs make_jump (addFirst), then next_val,
// then set_next_val: jump to nextOrgnsm, compute the new value, apply it.
// Keys with keyIsMethod are applied by calling orgnsm.<camelCase key>(value).

RCCrawler {
	var <attrKeys, <keyIsMethod;
	var <>nextOrgnsmParams, <>nextOrgnsm, <>leaveVal, <>setLeaveVal = true, <>patternAttrs;
	var <state, <patternOrgnsm, <patternInstance, <>batch;

	*new { |attrKeys, keyIsMethod = false|
		^super.new.initRCCrawler(attrKeys, keyIsMethod)
	}

	initRCCrawler { |attrKeysarg, keyIsMethodarg|
		attrKeys = if(attrKeysarg.isKindOf(String) or: { attrKeysarg.isKindOf(Collection).not }) { [attrKeysarg] } { attrKeysarg.asArray };
		keyIsMethod = if(keyIsMethodarg.isKindOf(Boolean)) { keyIsMethodarg ! attrKeys.size } { keyIsMethodarg.asArray };
		state = (orgnsm: nil, val: nil, prevVal: nil);
	}

	orgnsm { ^state[\orgnsm] }
	val { ^state[\val] }
	prevVal { ^state[\prevVal] }

	init { |orgnsm, val|
		this.setNewOrgnsm(orgnsm);
		if(val.notNil) { this.setVal(val); state[\val] = val };
		^this
	}

	// Start on the orgnsm at position idxStart under batchKey (first key by default).
	initFromBatch { |batch, batchKey, idxStart = 0, val|
		var list, o;
		this.batch = batch;
		batchKey = batchKey ?? { batch.keys.first };
		list = batch.orgnsms(batchKey) ? [];
		o = list[idxStart..].detect(_.notNil);
		if(o.isNil) {
			RCLog.error(\crawler, "initFromBatch: no live orgnsm at %/% in batch %".format(batchKey, idxStart, batch.name));
			^this
		};
		^this.init(o, val)
	}

	setNewOrgnsm { |orgnsm|
		state[\orgnsm] = orgnsm;
		state[\prevVal] = attrKeys.collect { |k, i| if(keyIsMethod[i]) { nil } { orgnsm.rGet(k) } };
	}

	// Apply val (defaults to the stored one) to the current orgnsm's attributes.
	setVal { |val|
		var o = state[\orgnsm];
		val = val ? state[\val];
		if(val.isNil or: { o.isNil }) { ^this };
		attrKeys.do { |k, i|
			if(keyIsMethod[i]) { this.prCallMethod(o, k, val[i]) } { o.rPut(k, val[i]) };
		};
	}

	prCallMethod { |orgnsm, key, value|
		var selector = RCUtil.camelCase(key);
		RCGuard.call(\crawler, nil) {
			if(orgnsm.respondsTo(selector)) {
				orgnsm.perform(selector, value)
			} {
				var f = orgnsm.rGet(key);
				if(f.isKindOf(Function)) { f.value(orgnsm, value) } {
					RCLog.error(\crawler, "% has neither a method % nor a function %".format(orgnsm, selector, key));
				};
			};
		};
	}

	// Last value the current (or given) orgnsm's beat produced for key.
	accessCurrentPatternValue { |key, orgnsm|
		orgnsm = orgnsm ? state[\orgnsm];
		^orgnsm !? { |o| o.beat !? { |b| b.lastValue(key) } }
	}

	// nextOrgnsm and leaveVal are values, or functions of the crawler evaluated
	// at each jump (BlockOrgnsm's next_orgnsm / leave_val). A failing function
	// is reported and counts as nil.
	prResolveNext {
		^if(nextOrgnsm.isKindOf(Function)) { RCGuard.call(\crawler, nil) { nextOrgnsm.value(this) } } { nextOrgnsm }
	}

	prResolveLeaveVal {
		^if(leaveVal.isKindOf(Function)) { RCGuard.call(\crawler, nil) { leaveVal.value(this) } } { leaveVal }
	}

	jumpNext {
		var next;
		if(setLeaveVal) { this.setVal(this.prResolveLeaveVal) };
		next = this.prResolveNext;
		if(next.isNil) { RCLog.warn(\crawler, "jumpNext: no nextOrgnsm, staying"); ^this };
		this.setNewOrgnsm(next);
	}

	setNextVal { |newVal|
		this.setVal(newVal);
		state[\val] = newVal;
	}

	makePatternOrgnsm {
		var o = state[\orgnsm];
		var species;
		if(o.isNil) { RCLog.error(\crawler, "makePatternOrgnsm: init the crawler on an orgnsm first"); ^nil };
		species = ("Crawler_" ++ if(o.isOrgnsm) { o.species } { o.name }).asSymbol;
		patternOrgnsm = RCOrgnsm(species, patternAttrs[\tribe] ? 0, 0, o.song);
		patternOrgnsm.isCrawlerPattern = true;
		patternOrgnsm.parentObject = this;
		this.preparePatternOrgnsm;
		^patternOrgnsm
	}

	preparePatternOrgnsm {
		var pa = patternAttrs ? ();
		patternOrgnsm.staticAttrs.proto = pa[\static_attrs] ?? { pa[\staticAttrs] };
		patternOrgnsm.attrDictBase = RCUtil.kvPutAll(
			RCUtil.kvPutAll([type: \rest], RCUtil.asKV(pa[\attr_dict_base] ?? { pa[\attrDictBase] })),
			[set_next_val: Pfunc { |ev| ev[\self].parentObject.setNextVal(ev[\next_val]); \dummy }]
		);
		patternOrgnsm.addFirstArrayBase = RCUtil.kvPutAll(
			[make_jump: Pfunc { |ev| ev[\self].parentObject.jumpNext; \dummy }],
			RCUtil.asKV(pa[\add_first_array_base] ?? { pa[\addFirstArrayBase] })
		);
	}

	createPattern { |layerKey = \core, terminationKey, replaceAttrs, osc = false, pickNewNumber = true, oscNames|
		patternInstance !? (_.free);
		patternInstance = nil;
		if(this.makePatternOrgnsm.isNil) { ^nil };
		patternInstance = patternOrgnsm.create(layerKey: layerKey, terminationKey: terminationKey, replaceAttrs: replaceAttrs,
			osc: osc, pickNewNumber: pickNewNumber, oscNames: oscNames);
		patternInstance.start;
		^patternInstance
	}

	clone {
		var c = this.class.new(attrKeys.deepCopy, keyIsMethod.copy);
		c.nextOrgnsmParams = nextOrgnsmParams;
		c.nextOrgnsm = nextOrgnsm;
		c.leaveVal = leaveVal;
		c.setLeaveVal = setLeaveVal;
		c.patternAttrs = patternAttrs.deepCopy;
		c.batch = batch;
		c.prSetState(state.copy);
		^c
	}

	prSetState { |s| state = s }

	free {
		this.setVal(this.prResolveLeaveVal);
		patternInstance !? (_.free);
		patternInstance = nil;
	}

	delete { ^this.free }

	printOn { |stream| stream << "RCCrawler(" << attrKeys << " on " << state[\orgnsm] << ")" }
}
