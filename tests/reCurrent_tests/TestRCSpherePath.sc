// RCSpherePath: the Hamiltonian search and the walk's fallback to it, on the
// octahedron (RCTestFakeDesign, in TestRCPathControl.sc) and on the pieces'
// order-4 spreadE t-design (48 points, the one the walk misses a point of).
// The quark class is reached through asClass: without SphericalDesign / atk-sc3
// the design tests report a skip instead of breaking the class library.
TestRCSpherePath : UnitTest {
	var savedRateLimit;

	setUp {
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown { RCLog.rateLimit = savedRateLimit }

	// the pieces' design of the given order, or nil (with a message) when the quarks are missing
	design { |order = 4|
		var class = \TDesign.asClass;
		var design;
		if(class.isNil or: { class.respondsTo(\newHoa).not }) {
			"TestRCSpherePath: TDesign.newHoa unavailable (SphericalDesign / atk-sc3): design test skipped".warn;
			^nil
		};
		design = class.newHoa(optimize: 'spreadE', order: order);
		design.calcTriplets;
		^design
	}

	shareFace { |design, a, b|
		^design.triplets.any { |t| t.includes(a) and: { t.includes(b) } }
	}

	// every consecutive pair of the path shares a face of the design
	isPath { |design, path|
		^(path.size - 1).max(0).collect { |i| this.shareFace(design, path[i], path[i + 1]) }.every { |x| x }
	}

	warnings { ^RCLog.history.select { |e| e[1] == \warn }.collect { |e| e[2] } }

	test_hamiltonianPath_octahedron {
		var design = RCTestFakeDesign.new;
		var path = RCSpherePath.hamiltonianPath(design, 0, seed: 3);
		var again = RCSpherePath.hamiltonianPath(design, 0, seed: 3);
		var from2 = RCSpherePath.hamiltonianPath(design, 2, seed: 3);
		this.assertEquals(path.size, 6, "every point of the octahedron");
		this.assertEquals(path[0], 0, "from the start point");
		this.assertEquals(path.asSet.size, 6, "each once");
		this.assert(this.isPath(design, path), "consecutive points share a face");
		this.assertEquals(path, again, "seeded: the same path every time");
		this.assertEquals(from2[0], 2, "another start");
		this.assertEquals(from2.asSet.size, 6, "covers the octahedron from there too");
		this.assert(this.isPath(design, from2), "and is a path");
		this.assertEquals(this.warnings, [], "no warning for a full path");
	}

	test_hamiltonianPath_bounded {
		var design = RCTestFakeDesign.new;
		var path = RCSpherePath.hamiltonianPath(design, 0, seed: 0, maxSteps: 2);
		this.assertEquals(path.size, 3, "the start and one point per step");
		this.assert(this.isPath(design, path), "a path all the same");
		this.assert(this.warnings.any { |w| w.contains("only 3 of 6 points") }, "the short path is reported");
	}

	test_hamiltonianPath_start_on_no_face {
		var design = RCTestFakeDesign.new;
		this.assertEquals(RCSpherePath.hamiltonianPath(design, 9, seed: 0), [9], "the start alone");
		this.assert(this.warnings.any { |w| w.contains("on no face") }, "reported");
	}

	test_hamiltonianPath_covers_the_48_point_design_from_every_start {
		var design = this.design(4);
		var sizes, starts, distinct, paths;
		if(design.isNil) { ^this.assert(true, "skipped: no TDesign") };
		this.assertEquals(design.size, 48, "an order-4 spreadE t-design has 48 points");
		sizes = 48.collect { |start|
			var path = RCSpherePath.hamiltonianPath(design, start, seed: 0);
			var ok = (path[0] == start) and: { path.asSet.size == path.size } and: { this.isPath(design, path) };
			if(ok) { path.size } { -1 }
		};
		this.assertEquals(sizes, 48 ! 48, "a full path (distinct points, consecutive ones sharing a face) from every start");
		this.assertEquals(this.warnings, [], "no warning");
	}

	test_hamiltonianPath_seeded_on_the_48_point_design {
		var design = this.design(4);
		var path, again, other;
		if(design.isNil) { ^this.assert(true, "skipped: no TDesign") };
		path = RCSpherePath.hamiltonianPath(design, 0, seed: 1);
		again = RCSpherePath.hamiltonianPath(design, 0, seed: 1);
		other = RCSpherePath.hamiltonianPath(design, 0, seed: 2);
		this.assertEquals(path, again, "the same seed gives the same path");
		this.assert(path != other, "another seed breaks the ties differently");
		this.assertEquals(other.asSet.size, 48, "and covers the design too");
	}

	// the walk's results stay where it reaches the size (the 24-point design, the octahedron)
	test_generatePath_keeps_the_walk {
		var octa = RCTestFakeDesign.new;
		var design = this.design(3);
		this.assertEquals(RCSpherePath.generatePath(octa, 0, seed: 3), [0, 5, 2, 4, 3, 1], "the octahedron walk of seed 3, as before the fallback");
		this.assertEquals(RCSpherePath.generatePath(octa, 0, seed: 1, pathSize: 3), [0, 5, 1], "a requested size, as before");
		if(design.isNil) { ^this.assert(true, "skipped: no TDesign") };
		this.assertEquals(RCSpherePath.generatePath(design, 0, seed: 2),
			[0, 3, 18, 12, 14, 13, 15, 5, 6, 17, 16, 19, 10, 8, 11, 2, 1, 20, 22, 9, 21, 23, 7, 4],
			"the pieces' 24-point walk of seed 2, as before the fallback");
		this.assertEquals(this.warnings, [], "no warning");
	}

	// the walk stops at 47 of 48 points from 0: the search completes the path
	test_generatePath_falls_back_to_the_search {
		var design = this.design(4);
		var path, again, short;
		if(design.isNil) { ^this.assert(true, "skipped: no TDesign") };
		path = RCSpherePath.generatePath(design, 0, seed: 2);
		again = RCSpherePath.generatePath(design, 0, seed: 2);
		this.assertEquals(path.size, 48, "every point of the design");
		this.assertEquals(path[0], 0, "from the start point");
		this.assertEquals(path.asSet.size, 48, "each once");
		this.assert(this.isPath(design, path), "consecutive points share a face");
		this.assertEquals(path, again, "seeded: the same path every time");
		this.assertEquals(this.warnings, [], "a completed path is no warning");
		short = RCSpherePath.generatePath(design, 0, seed: 2, maxSteps: 3);
		this.assert(short.size < 48, "a search bound too low leaves the path short (% points)".format(short.size));
		this.assert(this.warnings.any { |w| w.contains("generatePath: only") }, "and that is reported");
	}
}
