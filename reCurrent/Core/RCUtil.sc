// reCurrent — small pure utilities shared by the module.
//
// Key-value arrays ("kv") are flat arrays [key, value, key, value, ...] used
// wherever the order of pattern keys matters (Pbind pairs, attr dicts).

RCUtil {
	classvar <>maxProductSize = 200000;   // cap for cartesianProduct / l1Vectors
	classvar <reservedKeyWhitelist;       // Event methods that deliberately read a key

	*initClass {
		reservedKeyWhitelist = IdentitySet[\delta, \isRest];
	}

	//////// nested dictionary access

	// keys: a Symbol or an array of Symbols (path into nested dictionaries).
	*rPut { |dict, keys, value|
		var cur = dict;
		if(keys.isKindOf(SequenceableCollection).not or: { keys.isKindOf(RawArray) }) {
			dict[keys] = value;
			^dict
		};
		if(keys.size == 0) { ^dict };
		keys[..(keys.size - 2)].do { |key|
			if(cur.isNil) { ^dict };
			cur = cur[key];
		};
		if(cur.isNil) {
			RCLog.error(\rPut, "path % does not exist".format(keys));
			^dict
		};
		cur[keys.last] = value;
		^dict
	}

	*rGet { |dict, keys|
		var cur = dict;
		if(keys.isKindOf(SequenceableCollection).not or: { keys.isKindOf(RawArray) }) { ^dict[keys] };
		keys.do { |key|
			if(cur.isNil) { ^nil };
			cur = cur[key];
		};
		^cur
	}

	//////// key-value arrays

	*kvAt { |kv, key|
		kv.pairsDo { |k, v| if(k == key) { ^v } };
		^nil
	}

	*kvIncludesKey { |kv, key|
		kv.pairsDo { |k| if(k == key) { ^true } };
		^false
	}

	*kvKeys { |kv|
		var res = Array.new(kv.size div: 2);
		kv.pairsDo { |k| res.add(k) };
		^res
	}

	// Replace the value of `key`; optionally append the pair when absent.
	*kvReplace { |kv, key, value, addEndIfNotFound = false|
		var found = false;
		var res = kv.collect { |el, i|
			if(i.odd and: { kv[i - 1] == key }) { found = true; value } { el }
		};
		if(found.not and: { addEndIfNotFound }) { res = res ++ [key, value] };
		^res
	}

	// Vector version: keys[i] gets values[i].
	*kvReplaceMany { |kv, keys, values, addEndIfNotFound = false|
		var foundIdxs = List.new;
		var res = kv.collect { |el, i|
			var idx;
			if(i.odd) {
				idx = keys.indexOf(kv[i - 1]);
				if(idx.notNil) { foundIdxs.add(idx); values[idx] } { el }
			} { el }
		};
		if(addEndIfNotFound and: { foundIdxs.size < keys.size }) {
			keys.do { |key, i| if(foundIdxs.includes(i).not) { res = res ++ [key, values[i]] } };
		};
		^res
	}

	// Keys present in `newKV` are removed from `parentKV`, then `newKV` is appended.
	*kvPutAll { |parentKV, newKV|
		var newKeys = this.kvKeys(newKV);
		var res = List.new;
		parentKV.pairsDo { |k, v| if(newKeys.includes(k).not) { res.add(k); res.add(v) } };
		^res.asArray ++ newKV
	}

	// Anything (Event, Dictionary, kv array, array of associations) → kv array.
	// A Dictionary yields hash order: when the order matters (pattern keys),
	// pass warnTag and the caller is told to use a kv Array instead.
	*asKV { |obj, warnTag|
		if(obj.isNil) { ^[] };
		if(obj.isKindOf(Dictionary)) {
			if(warnTag.notNil and: { obj.size > 1 }) {
				RCLog.warn(warnTag, "attributes given as a % (hash order): key order is not kept, use a kv Array [key, value, ...]".format(obj.class));
			};
			^obj.asPairs
		};
		if(obj.isKindOf(SequenceableCollection)) {
			if(obj.size > 0 and: { obj[0].isKindOf(Association) }) {
				^obj.collect { |assoc| [assoc.key, assoc.value] }.flatten(1)
			};
			^obj.asArray
		};
		^obj.asPairs
	}

	//////// static vs. streamable values

	// True when a value can be used as-is in a pattern without being streamed.
	*isStatic { |obj, depth = 0|
		if(obj.isNil or: { obj.isNumber } or: { obj.isKindOf(Symbol) } or: { obj.isKindOf(Boolean) }
			or: { obj.isKindOf(RawArray) } or: { obj.isKindOf(Ref) } or: { obj.isKindOf(Node) }
			or: { obj.isKindOf(Bus) } or: { obj.isKindOf(Buffer) }) { ^true };
		if(obj.isKindOf(Pattern) or: { obj.isKindOf(Stream) } or: { obj.isKindOf(AbstractFunction) }) { ^false };
		if(obj.isKindOf(Array) and: { obj.mutable.not }) { ^true };
		if(obj.isKindOf(Collection)) {
			if(depth >= 5) { ^false };
			obj.do { |el| if(this.isStatic(el, depth + 1).not) { ^false } };
			^true
		};
		^true
	}

	//////// combinatorics

	// All combinations, first array varying slowest. Empty on size overflow.
	*cartesianProduct { |arrays|
		var size;
		if(arrays.isEmpty) { ^[[]] };
		size = arrays.collect(_.size).product;
		if(size > maxProductSize) {
			RCLog.error(\cartesianProduct, "% combinations exceed the cap of %".format(size, maxProductSize));
			^[]
		};
		^arrays.inject([[]], { |acc, list|
			acc.collect { |partial| list.collect { |item| partial ++ [item] } }.flatten(1)
		})
	}

	// Products of every combination of one element per array.
	*cartesianProductProducts { |arrays|
		^this.cartesianProduct(arrays).collect(_.product)
	}

	// n-dimensional integer vectors with L1 norm strictly less than t
	// (same enumeration order as the original recursive version).
	*l1Vectors { |n, t|
		var results = List.new;
		var recurse;
		var estimate = ((2 * t) - 1).max(1) ** n;
		if(n <= 0 or: { t <= 0 }) { ^[] };
		if(estimate > maxProductSize) {
			RCLog.error(\l1Vectors, "n=% t=% would enumerate ~% vectors, cap is %".format(n, t, estimate.asInteger, maxProductSize));
			^[]
		};
		recurse = { |partial, remainingDim, remainingBudget|
			if(remainingDim == 0) {
				if(partial.collect(_.abs).sum < t) { results.add(partial) };
			} {
				(remainingBudget.neg .. remainingBudget).do { |val|
					recurse.(partial ++ [val], remainingDim - 1, remainingBudget - val.abs);
				};
			};
		};
		recurse.([], n, t - 1);
		^results.asArray
	}

	// Items of bagA whose count exceeds their count in bagB.
	*bagDifference { |bagA, bagB|
		var contentsB = bagB.contents;
		var res = List.new;
		bagA.contents.keysValuesDo { |item, count|
			if((count - (contentsB[item] ? 0)) > 0) { res.add(item) };
		};
		^res.asArray
	}

	// The first `digits` digits of the fractional part of x in the given base.
	*digitsInBase { |x, base, digits|
		if(digits.isNil or: { digits < 1 }) { ^[] };
		^(0..(digits - 1)).inject([x.frac, []], { |state|
			var newX = state[0] * base;
			[newX.frac, state[1] ++ [newX.floor]]
		})[1]
	}

	//////// files

	// Folder → MultiLevelIdentityDictionary of PathNames, keyed by folder and file names.
	*fileTree { |dirPath|
		var res = MultiLevelIdentityDictionary.new;
		var pathname = PathName(dirPath.asString.standardizePath);
		var recurse;
		if(pathname.isFolder.not) {
			RCLog.error(\fileTree, "not a folder: %".format(dirPath));
			^res
		};
		recurse = { |dir, parentKeys|
			dir.files.do { |file| res.put(*(parentKeys ++ [file.fileName.asSymbol, file])) };
			dir.folders.do { |folder| recurse.(folder, parentKeys ++ [folder.folderName.asSymbol]) };
		};
		pathname.files.do { |file| res.put(\root, file.fileName.asSymbol, file) };
		pathname.folders.do { |folder| recurse.(folder, [folder.folderName.asSymbol]) };
		^res
	}

	// "set_width" → \setWidth
	*camelCase { |name|
		var parts = name.asString.split($_);
		^(parts[0] ++ parts[1..].collect { |p| if(p.size > 0) { p[0].toUpper ++ p[1..] } { "" } }.join).asSymbol
	}

	//////// function-valued attributes

	// A function stored in an Event is auto-called by dict.key (with the Event
	// as first argument), which is why the proto-library wrapped such
	// functions in an extra {}. This returns the function either way: a
	// zero-argument function is taken as a wrapper and unwrapped once.
	*attrFunc { |dict, key|
		var raw = dict[key];
		if(raw.isKindOf(Function) and: { raw.def.argNames.isNil or: { raw.def.argNames.size == 0 } }) { ^raw.value };
		^raw
	}

	//////// attribute names

	// True when `ev.key` would call a method instead of reading the key
	// (e.g. \release, \size, \value, \copy, \next, \free, \name).
	*isReservedKey { |key|
		key = key.asSymbol;
		if(reservedKeyWhitelist.includes(key)) { ^false };
		^Event.findRespondingMethodFor(key).notNil
	}

	*warnIfReservedKey { |key, tag = \attr|
		if(this.isReservedKey(key)) {
			RCLog.warn(tag, "attribute '%' shadows a method: ev.% would call the method, not read the key. Use another name.".format(key, key));
			^true
		};
		^false
	}
}
