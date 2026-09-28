// reCurrent — paths over a spherical design (the ~generate_path_tdesign /
// ~generate_disjoint_triplets_tdesign / ~thue_morse helpers of the pieces).
//
// `design` is any object answering size, triplets and calcTriplets (a
// SphericalDesign TDesign, or a test double); no quark class is named here.
// Every generator is seeded and bounded: none loops forever.

RCSpherePath {

	*prTriplets { |design|
		var triplets = design.triplets;
		if(triplets.isNil) { triplets = design.calcTriplets.triplets };
		^triplets
	}

	// The candidates around each point: the points of its faces, with the
	// repetitions of triplets.flat (a shared neighbour counts twice) so that a
	// seeded walk picks the same points as the original select/flat version.
	*prAdjacency { |triplets|
		var res = IdentityDictionary.new;
		triplets.do { |t| t.do { |p| res[p] = (res[p] ? []) ++ t } };
		^res
	}

	// The distinct neighbours of each point (the other points of its faces),
	// sorted so that the search's seeded tie-breaks start from a canonical order.
	*prNeighbours { |triplets|
		var sets = IdentityDictionary.new;
		var res = IdentityDictionary.new;
		triplets.do { |t|
			t.do { |p|
				var set = sets[p];
				if(set.isNil) { set = IdentitySet.new; sets[p] = set };
				t.do { |q| if(q != p) { set.add(q) } };
			};
		};
		sets.keysValuesDo { |p, set| res[p] = set.asArray.sort };
		^res
	}

	// A path of `pathSize` adjacent, distinct points starting at startPoint:
	// a seeded random self-avoiding walk over the triangulation, retried up to
	// maxTries times to reach the requested size. Where the walk reaches it
	// (the pieces' 24-point design) the result is the walk's, as before. Where
	// it does not (47 of 48 points on the order-4 design, 65 of 70 on the
	// order-5 one), the Hamiltonian search (*hamiltonianPath, same seed) takes
	// over and its first pathSize points are returned, so that the orgnsms at
	// the points the walk missed are hit too. Warns only when both fall short.
	*generatePath { |design, startPoint = 0, seed = 0, pathSize, maxTries = 1000, maxSteps = 100000|
		var triplets = this.prTriplets(design);
		var adjacency = this.prAdjacency(triplets);
		var routine, best, tries = 0;
		pathSize = pathSize ? design.size;
		routine = Routine {
			best = [startPoint];
			while { (best.size < pathSize) and: { tries < maxTries } } {
				var path = List[startPoint];
				var visited = IdentitySet[startPoint];
				var current = startPoint;
				(pathSize - 1).max(0).do {
					var acceptable = (adjacency[current] ? []).reject { |p| visited.includes(p) };
					if(acceptable.size > 0) {
						current = acceptable.choose;
						path.add(current);
						visited.add(current);
					};
				};
				if(path.size > best.size) { best = path.asArray };
				tries = tries + 1;
			};
			best.yield;
		};
		routine.randSeed = seed;
		best = routine.next.asArray;
		if(best.size < pathSize) {
			var walked = best.size;
			var search = this.prHamiltonianSearch(this.prNeighbours(triplets), design.size, startPoint, seed, maxSteps);
			if(search[0].size > best.size) { best = search[0].keep(pathSize) };
			if(best.size < pathSize) {
				RCLog.warn(\spherePath, "generatePath: only % of % points reachable from % (the walk % after % tries, the search % within % steps)".format(best.size, pathSize, startPoint, walked, tries, search[0].size, search[1]));
			} {
				RCLog.info(\spherePath, "generatePath: the walk reached % of % points from % after % tries, the Hamiltonian search covers it".format(walked, pathSize, startPoint, tries));
			};
		};
		^best
	}

	// A path through every point of the design, each once (Hamiltonian over
	// its triangulation), from startPoint: a depth-first search with
	// Warnsdorff's rule — the unvisited neighbour with the fewest onward
	// unvisited neighbours first, ties broken by the seed (a scramble before
	// a stable sort), backtracking when a branch dead-ends — bounded by
	// maxSteps. On the pieces' 24-, 48- and 70-point designs it finds a full
	// path from every start in about a millisecond, without backtracking.
	// Returns the longest path found, with a warning when it is not complete.
	*hamiltonianPath { |design, startPoint = 0, seed = 0, maxSteps = 100000|
		var neighbours = this.prNeighbours(this.prTriplets(design));
		var res;
		if(neighbours[startPoint].isNil) {
			RCLog.warn(\spherePath, "hamiltonianPath: point % is on no face of the design".format(startPoint));
			^[startPoint]
		};
		res = this.prHamiltonianSearch(neighbours, design.size, startPoint, seed, maxSteps);
		if(res[0].size < design.size) {
			RCLog.warn(\spherePath, "hamiltonianPath: only % of % points from % within % steps".format(res[0].size, design.size, startPoint, res[1]));
		};
		^res[0]
	}

	// The search itself: [longest path found, steps taken]. Seeded through
	// the Routine's random generator, as the walk is.
	*prHamiltonianSearch { |neighbours, size, startPoint, seed, maxSteps|
		var routine = Routine {
			var visited = IdentitySet[startPoint];
			var path = List[startPoint];
			var best = [startPoint];
			var stack = List.new;
			var steps = 0;
			var onward = { |p| (neighbours[p] ? []).count { |q| visited.includes(q).not } };
			var candidates = { |p|
				(neighbours[p] ? []).reject { |q| visited.includes(q) }.scramble.sort { |a, b| onward.(a) <= onward.(b) }.asList
			};
			stack.add(candidates.(startPoint));
			while { (path.size < size) and: { stack.notEmpty } and: { steps < maxSteps } } {
				var cands = stack.last;
				if(cands.isEmpty) {
					stack.pop;
					visited.remove(path.pop);
				} {
					var next = cands.removeAt(0);
					path.add(next);
					visited.add(next);
					stack.add(candidates.(next));
					if(path.size > best.size) { best = path.asArray };
				};
				steps = steps + 1;
			};
			[best, steps].yield;
		};
		routine.randSeed = seed;
		^routine.next
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
