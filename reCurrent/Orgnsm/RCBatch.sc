// reCurrent — a keyed collection of orgnsms cloned from one template
// (BlockOrgnsm's ~make_new_batch).
//
//   ~batch = RCBatch(\samplers, ~tpl, layerKey: \core, replaceAttrs: ("tribe": 1, "attr_dict_base.zpos": PbrownStart(0.5, 2, 0.2, 1)));
//   8.do { |i| ~batch.addCreate(i, ("seed": 1994 + i, "spos_center_idx": i)) };   // prepared
//   ~batch.startPrepared;
//   ~batch.editAttr("bufrate", 0.5);
//   ~batch.editAttr("bufrate", 0.75, { |o, i, list, key| key % 3 == 0 });
//   ~batch.deleteBeats(nil, true);
// Iteration functions receive (orgnsm, index, list, key).

RCBatch {
	var <name, <template, <layerKey, <replaceAttrs, <terminationKey, <osc, <oscNames, <chan, <pickNewNumber;
	var <lists, <prepared, <storage;

	*new { |name, template, layerKey = \core, replaceAttrs, terminationKey, osc = false, oscNames, chan, pickNewNumber = true|
		^super.new.initRCBatch(name, template, layerKey, replaceAttrs, terminationKey, osc, oscNames, chan, pickNewNumber)
	}

	initRCBatch { |namearg, templatearg, layerKeyarg, replaceAttrsarg, terminationKeyarg, oscarg, oscNamesarg, chanarg, pickNewNumberarg|
		name = namearg.asSymbol;
		template = templatearg;
		layerKey = layerKeyarg;
		replaceAttrs = Dictionary.new;   // equality on String keys
		replaceAttrsarg !? { |r| r.keysValuesDo { |k, v| replaceAttrs[k] = v } };
		terminationKey = terminationKeyarg;
		osc = oscarg;
		oscNames = oscNamesarg;
		chan = chanarg;
		pickNewNumber = pickNewNumberarg;
		lists = Dictionary.new;      // key → List of started orgnsms
		prepared = Dictionary.new;   // key → List of prepared (not started) orgnsms
	}

	song { ^template.song }

	// Create one orgnsm under `key`; started now or kept for startPrepared.
	addCreate { |key, replaceAttrs, deleteAttrs, start = false|
		var attrs = this.replaceAttrs.copy;
		var o;
		replaceAttrs !? { |r| r.keysValuesDo { |k, v| attrs[k] = v } };
		o = template.create(layerKey: layerKey, terminationKey: terminationKey, replaceAttrs: attrs,
			deleteAttrs: deleteAttrs, osc: osc, pickNewNumber: pickNewNumber, oscNames: oscNames, chan: chan,
			batch: this, batchKey: key);
		if(start) {
			o.start;
			this.prList(lists, key).add(o);
		} {
			this.prList(prepared, key).add(o);
		};
		^key
	}

	prList { |dict, key|
		^dict[key] ?? { var l = List.new; dict[key] = l; l }
	}

	// Start the prepared orgnsms of `keys` (all by default). Returns the keys.
	startPrepared { |keys|
		keys = keys ?? { prepared.keys.asArray };
		keys.do { |key|
			prepared[key] !? { |list|
				list.do { |o| o.start; this.prList(lists, key).add(o) };
				prepared.removeAt(key);
			};
		};
		^keys
	}

	// Live orgnsms under a key (freed ones filtered out).
	orgnsms { |key| ^lists[key] !? { |list| list.reject(_.isFreed).asArray } }

	// key → Array of live orgnsms
	allOrgnsms {
		var res = Dictionary.new;
		lists.keysValuesDo { |key, list| res[key] = list.reject(_.isFreed).asArray };
		^res
	}

	keys { ^lists.keys.asArray }
	size { ^lists.values.sum { |l| l.count(_.isFreed.not) } }

	store { storage = this.allOrgnsms }

	// The stored snapshot without the orgnsms freed since.
	recall {
		^storage !? { |dict| dict.collect { |list| list.reject(_.isFreed) } }
	}

	// key → list of func.(orgnsm, i, list, key)
	collectAll { |func|
		var res = Dictionary.new;
		lists.keysValuesDo { |key, list|
			res[key] = list.collect { |o, i| func.value(o, i, list, key) };
		};
		^res
	}

	// Apply func to the live orgnsms selected by cond (all by default). A
	// throwing cond or func is reported and skips that orgnsm.
	apply { |func, cond|
		^this.collectAll { |o, i, list, key|
			if(o.isFreed.not and: { cond.isNil or: { RCGuard.call(name, false) { cond.value(o, i, list, key) } } }) {
				RCGuard.call(name, o) { func.value(o, i, list, key) }
			} { o }
		}
	}

	editAttr { |attr, value, cond, valueIsFunc = false|
		^this.apply({ |o, i, list, key|
			var v = if(valueIsFunc) { value.value(o, i, list, key) } { value };
			o.rPut(attr, v);
			o
		}, cond)
	}

	// Free the selected live (and prepared) orgnsms; cleanup drops them from the lists.
	deleteBeats { |cond, cleanup = false|
		var res = this.apply({ |o| o.free; nil }, cond);
		prepared.keysValuesDo { |key, list|
			list.do { |o, i|
				if(cond.isNil or: { RCGuard.call(name, false) { cond.value(o, i, list, key) } }) { o.free };
			};
		};
		if(cleanup) {
			[lists, prepared].do { |dict|
				dict.keysValuesDo { |key, list| dict[key] = list.reject(_.isFreed) };
				dict.keys.copy.do { |key| if(dict[key].size == 0) { dict.removeAt(key) } };
			};
		};
		^res
	}

	free { this.deleteBeats(nil, true) }

	printOn { |stream| stream << "RCBatch(" << name << ", " << this.size << " orgnsms)" }
}
