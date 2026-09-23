// reCurrent — paths over a spherical design (the ~generate_path_tdesign /
// ~generate_disjoint_triplets_tdesign / ~thue_morse helpers of the pieces).
//
// `design` is any object answering size, triplets and calcTriplets (a
// SphericalDesign TDesign, or a test double); no quark class is named here.
// Both generators are seeded and bounded: they never loop forever.

RCSpherePath {

	*prTriplets { |design|
		var triplets = design.triplets;
		if(triplets.isNil) { triplets = design.calcTriplets.triplets };
		^triplets
	}

	// A path of `pathSize` adjacent, distinct points starting at startPoint.
	// Retries (seeded) up to maxTries times to reach the requested size and
	// returns the longest path found.
	*generatePath { |design, startPoint = 0, seed = 0, pathSize, maxTries = 1000|
		var triplets = this.prTriplets(design);
		var routine, best;
		pathSize = pathSize ? design.size;
		routine = Routine {
			var tries = 0;
			best = [startPoint];
			while { (best.size < pathSize) and: { tries < maxTries } } {
				var path = List[startPoint];
				var current = startPoint;
				(pathSize - 1).max(0).do {
					var adjacent = triplets.select { |t| t.includes(current) };
					var acceptable = adjacent.flat.reject { |p| path.includes(p) };
					if(acceptable.size > 0) {
						current = acceptable.choose;
						path.add(current);
					};
				};
				if(path.size > best.size) { best = path.asArray };
				tries = tries + 1;
			};
			if(best.size < pathSize) {
				RCLog.warn(\spherePath, "generatePath: only % of % points reachable from % after % tries".format(best.size, pathSize, startPoint, tries));
			};
			best.yield;
		};
		routine.randSeed = seed;
		^routine.next.asArray
	}

	// Triplets (faces) that share no point, covering as many points as found.
	*disjointTriplets { |design, seed = 0, maxTries = 1000|
		var triplets = this.prTriplets(design);
		var routine, best;
		routine = Routine {
			var tries = 0, bestUsed = -1;
			best = [];
			while { (bestUsed < design.size) and: { tries < maxTries } } {
				var candidates = triplets.scramble;
				var used = Set.new;
				var chosen = List.new;
				candidates.do { |triplet|
					if(triplet.every { |p| used.includes(p).not }) {
						chosen.add(triplet);
						triplet.do { |p| used.add(p) };
					};
				};
				if(used.size > bestUsed) { bestUsed = used.size; best = chosen.asArray };
				tries = tries + 1;
			};
			best.yield;
		};
		routine.randSeed = seed;
		^routine.next.asArray
	}

	// Thue–Morse digit sum of pos in the given base, modulo base.
	*thueMorse { |pos, base = 2|
		^pos.asInteger.asDigits(base).sum % base
	}
}
