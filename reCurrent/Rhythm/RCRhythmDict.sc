// reCurrent — per-batch rhythm definitions resolved against a subseq library
// (BlockOrgnsm's ~make_rhythm_dict).
//
// entries[batchName][key] is an Array of rhythm-dict entries:
//   [priority, shiftAdd, "lib.path.name", mask?, params?, keysIgnoreOrder?, unused, timeMult?]
// subseqs(batchName, key) resolves them into RCSubseq objects with the library
// shift/durs scaled by timeMult, params expanded to one value per hit and the
// mask expanded to one Boolean per hit.
//
//   ~rd = RCRhythmDict(~lib);
//   ~rd.at(\sampler)[\bass] = [[1, 0, "textures.pad.continuous_dur64_step1", true, (freqmult: Pseq([1, 0.5], inf)), [\freqmult], nil, 1]];
//   ~rd.subseqs(\sampler, \bass)

RCRhythmDict {
	var <library, <entries;

	*new { |library| ^super.new.initRCRhythmDict(library) }

	initRCRhythmDict { |libraryarg|
		library = libraryarg;
		entries = Dictionary.new;
	}

	// The (mutable) Dictionary of a batch name, created on demand.
	at { |batchName|
		^entries[batchName.asSymbol] ?? { var d = Dictionary.new; entries[batchName.asSymbol] = d; d }
	}

	put { |batchName, key, entriesArray|
		this.at(batchName)[key] = entriesArray;
		^this
	}

	includes { |batchName, key| ^entries[batchName.asSymbol] !? { |d| d[key].notNil } ? false }

	// Array of RCSubseq for a batch key; entries whose library name is unknown are skipped.
	subseqs { |batchName, key|
		var infos = entries[batchName.asSymbol] !? { |d| d[key] };
		if(infos.isNil) {
			RCLog.error(\rhythmDict, "no rhythm entry %/%".format(batchName, key));
			^[]
		};
		^infos.collect { |info| this.resolve(info) }.reject(_.isNil)
	}

	resolve { |info|
		var libName = info[2];
		var libEntry = library.at(libName);
		var priority, shift, durs, params, originalKeys, keysIgnoreOrder, mask, timeMult, isArray, numHits;
		if(libEntry.isNil) { ^nil };
		priority = info[0];
		timeMult = info[7] ? 1;
		shift = (libEntry[1] + (info[1] ? 0)) * timeMult;
		durs = libEntry[2] * timeMult;
		isArray = library.isArraySeq(libEntry);
		numHits = library.subseqSize(libEntry, false);
		// params: library defaults under the entry's own (copies: the library is never mutated)
		params = ().putAll(libEntry[3] ? ());
		originalKeys = (info[4] ? ()).keys;
		params.putAll(info[4] ? ());
		params = params.collect { |val|
			if(val.isKindOf(SequenceableCollection).not and: { val.isKindOf(Pattern).not }) {
				if(isArray) { val ! numHits } { Pn(val) }
			} { val }
		};
		// keys whose order is not tied to the hits: library ones (unless overridden) + the entry's
		keysIgnoreOrder = (libEntry[4] ? []).select { |k| originalKeys.includes(k).not } ++ (info[5] ? []);
		mask = info[3] ? true;
		if(mask.isKindOf(Boolean)) { mask = if(isArray) { mask ! numHits } { Pn(mask) } };
		^RCSubseq(priority, shift, durs, params, mask, libName, keysIgnoreOrder, timeMult)
	}

	clone {
		var c = this.class.new(library);
		entries.keysValuesDo { |batchName, dict| c.entries[batchName] = dict.deepCopy };
		^c
	}

	printOn { |stream| stream << "RCRhythmDict(" << entries.keys.asArray << ")" }
}
