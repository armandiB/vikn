// reCurrent — the vocabulary of subsequences of a piece (BlockOrgnsm's
// ~make_subseq_library). Entries are nested Events:
//   category → sub-category → name → [name, shift, durs, params?, keysIgnoreOrder?]
// and are addressed by dotted paths, "clave_44.son.1".
//
//   ~lib = RCSubseqLibrary.newFrom((
//       clave_44: (son: ('1': [\son, 0, [3, 3, 4, 2, 4]])),
//       percs: (kick: (fourfour: [\fourfour, 0, [1, 1, 1, 1]]))
//   ));
//   ~lib.put(\textures, (pad: (...)));      // add a category later
//   ~lib.at("percs.kick.fourfour")

RCSubseqLibrary {
	var <entries;

	*new { ^super.new.initRCSubseqLibrary }
	*newFrom { |nestedEvent| ^this.new.putAll(nestedEvent) }

	initRCSubseqLibrary {
		entries = ();
	}

	putAll { |event|
		event.keysValuesDo { |k, v| entries[k.asSymbol] = v };
		^this
	}

	put { |category, event|
		entries[category.asSymbol] = event;
		^this
	}

	// Entry array for a dotted name (padded to 4 elements), or nil with an error.
	at { |name|
		var cur = entries;
		name.asString.split($.).do { |k|
			if(cur.notNil) { cur = cur[k.asSymbol] };
		};
		if(cur.isNil or: { cur.isKindOf(SequenceableCollection).not }) {
			RCLog.error(\subseqLibrary, "no subseq named %".format(name));
			^nil
		};
		if(cur.size == 3) { cur = cur ++ [()] };
		^cur
	}

	includes { |name|
		var cur = entries;
		name.asString.split($.).do { |k| if(cur.notNil) { cur = cur[k.asSymbol] } };
		^cur.notNil and: { cur.isKindOf(SequenceableCollection) }
	}

	isArraySeq { |entry| ^entry[2].isKindOf(SequenceableCollection) }

	subseqSize { |entry, failNotArray = true|
		if(this.isArraySeq(entry)) { ^entry[2].size };
		if(failNotArray) { RCLog.error(\subseqLibrary, "size is not defined for pattern durations (%)".format(entry[0])) };
		^nil
	}

	size { |name|
		var entry = this.at(name);
		^entry !? { this.subseqSize(entry) }
	}

	// All dotted names, for browsing.
	names {
		var res = List.new;
		var recurse = { |dict, prefix|
			dict.keysValuesDo { |k, v|
				var path = if(prefix.isNil) { k.asString } { prefix ++ "." ++ k };
				if(v.isKindOf(Dictionary)) { recurse.(v, path) } { res.add(path) };
			};
		};
		recurse.(entries, nil);
		^res.asArray.sort
	}

	printOn { |stream| stream << "RCSubseqLibrary(" << this.names.size << " subseqs)" }
}
