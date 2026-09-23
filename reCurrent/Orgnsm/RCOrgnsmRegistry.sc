// reCurrent — registry of a song's orgnsms and fobjects
// (BlockOrgnsm's ~orgnsms_dict + ~max_orgnsm_nb_dict).
//
// orgnsms: species → tribe → number → RCOrgnsm. Numbers are allocated per
// (species, tribe) and never reused within a song; registering a fixed number
// that already exists picks the next free one instead of overwriting.

RCOrgnsmRegistry {
	var <song, <orgnsms, <maxNumbers, <fobjects;

	*new { |song| ^super.new.initRCOrgnsmRegistry(song) }

	initRCOrgnsmRegistry { |songarg|
		song = songarg;
		orgnsms = IdentityDictionary.new;
		maxNumbers = IdentityDictionary.new;
		fobjects = IdentityDictionary.new;
	}

	prTribeDict { |species, tribe|
		orgnsms[species] = orgnsms[species] ?? { IdentityDictionary.new };
		maxNumbers[species] = maxNumbers[species] ?? { IdentityDictionary.new };
		orgnsms[species][tribe] = orgnsms[species][tribe] ?? { IdentityDictionary.new };
		maxNumbers[species][tribe] = maxNumbers[species][tribe] ? -1;
		^orgnsms[species][tribe]
	}

	// Returns the number the orgnsm ends up with.
	register { |orgnsm, pickNewNumber = true|
		var species = orgnsm.species, tribe = orgnsm.tribe;
		var dict = this.prTribeDict(species, tribe);
		var number = orgnsm.number;
		if(pickNewNumber or: { number.isNil }) {
			number = maxNumbers[species][tribe] + 1;
		} {
			if(dict[number].notNil and: { dict[number] !== orgnsm }) {
				RCLog.warn(\registry, "% % % already exists, picking the next free number".format(species, tribe, number));
				number = maxNumbers[species][tribe] + 1;
			};
		};
		maxNumbers[species][tribe] = max(maxNumbers[species][tribe], number);
		orgnsm.number = number;
		dict[number] = orgnsm;
		^number
	}

	remove { |orgnsm|
		var dict = orgnsms[orgnsm.species] !? { |d| d[orgnsm.tribe] };
		if(dict.isNil or: { dict[orgnsm.number] !== orgnsm }) { ^false };
		dict.removeAt(orgnsm.number);
		^true
	}

	// IdentityDictionary number → orgnsm (nil when unknown).
	tribe { |species, tribe| ^orgnsms[species] !? { |d| d[tribe] } }

	all {
		var res = List.new;
		orgnsms.do { |tribes| tribes.do { |dict| dict.do { |o| res.add(o) } } };
		^res.asArray
	}

	size { ^this.all.size }

	//////// fobjects (name → RCFObject)

	// Registers under `name`, or name_1, name_2... when taken. Returns the final name.
	addFobject { |name, fobject|
		var real = name.asSymbol, suffix = 0;
		while { fobjects.includesKey(real) } {
			suffix = suffix + 1;
			real = (name.asString ++ "_" ++ suffix).asSymbol;
		};
		if(suffix > 0) { RCLog.warn(\registry, "fobject name % taken, renamed %".format(name, real)) };
		fobjects[real] = fobject;
		^real
	}

	removeFobject { |name, fobject|
		if(fobjects[name.asSymbol] === fobject) { fobjects.removeAt(name.asSymbol); ^true };
		^false
	}

	fobject { |name| ^fobjects[name.asSymbol] }

	clear {
		orgnsms.clear;
		maxNumbers.clear;
		fobjects.clear;
	}

	printOn { |stream| stream << "RCOrgnsmRegistry(" << song.name << ", " << this.size << " orgnsms, " << fobjects.size << " fobjects)" }
}
