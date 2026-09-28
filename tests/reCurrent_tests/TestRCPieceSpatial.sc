// The spatial contract of the HadronLake pieces: the order-3 t-design their orgnsms sit on
// (~tdesign_default = TDesign.newHoa(optimize: 'spreadE', order: 3): SphericalDesign + atk-sc3) and
// the HOA percussion SynthDefs built with RCSynthDefs. The quark classes are reached through asClass:
// without them the tests report a skip instead of breaking the class library.
TestRCPieceSpatial : UnitTest {
	var savedRateLimit;

	setUp {
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown { RCLog.rateLimit = savedRateLimit }

	// the pieces' design, or nil (with a message) when the quarks are missing
	design {
		var class = \TDesign.asClass;
		var design;
		if(class.isNil or: { class.respondsTo(\newHoa).not }) {
			"TestRCPieceSpatial: TDesign.newHoa unavailable (SphericalDesign / atk-sc3): design test skipped".warn;
			^nil
		};
		design = class.newHoa(optimize: 'spreadE', order: 3);
		design.calcTriplets;
		^design
	}

	test_hoa_design_of_the_pieces {
		var design = this.design;
		var triplets, path, again, disjoint;
		if(design.isNil) { ^this.assert(true, "skipped: no TDesign") };
		triplets = design.triplets;
		this.assertEquals(design.size, 24, "an order-3 spreadE t-design has 24 points");
		this.assertEquals(design.directions.size, 24, "one [theta, phi] direction per point");
		this.assert(design.directions.every { |d| d.size == 2 and: { d[0].isNumber and: { d[1].abs <= (pi / 2) } } }, "directions are [azimuth, elevation] numbers");
		this.assert(triplets.notEmpty and: { triplets.every { |t| t.size == 3 and: { t.every { |p| p >= 0 and: { p < 24 } } } } }, "faces are triplets of point indices");
		this.assertEquals(triplets.flatten.asSet.size, 24, "every point belongs to a face");
		path = RCSpherePath.generatePath(design, 0, seed: 2);
		again = RCSpherePath.generatePath(design, 0, seed: 2);
		this.assertEquals(path.size, 24, "a full path visits every point");
		this.assertEquals(path.asSet.size, 24, "once");
		this.assertEquals(path[0], 0, "from the start point");
		this.assert((0..22).every { |i| triplets.any { |t| t.includes(path[i]) and: { t.includes(path[i + 1]) } } }, "consecutive points share a face");
		this.assertEquals(path, again, "seeded: the same path every time");
		disjoint = RCSpherePath.disjointTriplets(design, seed: 5);
		this.assert(disjoint.every { |t| triplets.includesEqual(t) }, "disjoint triplets are faces of the design");
		this.assertEquals(disjoint.flatten.size, disjoint.flatten.asSet.size, "sharing no point");
		this.assert(disjoint.size >= 6, "covering most of the design (% faces)".format(disjoint.size));
	}

	test_hoa_orgnsm_synthdef_variants_write_every_channel {
		var order = 3, n = (order + 1).squared;
		var encoder = \HoaEncodeDirection.asClass;
		var names, desc1, desc2;
		if(encoder.isNil) { ^this.assert(true, "skipped: no HoaEncodeDirection (atk-sc3)") };
		names = RCSynthDefs.addForOrgnsms(\rc_test_hoa_perc, { |amp = 0.3, freq = 60, theta = 0, phi = 0, radius = 1.5|
			var env = Env.perc(0.01, 0.2, amp).kr(doneAction: 2);
			encoder.ar(SinOsc.ar(freq) * env, theta, phi, radius, order)
		});
		desc1 = SynthDescLib.global[RCSynthDefs.outputSuffix(\rc_test_hoa_perc, 1)];
		desc2 = SynthDescLib.global[RCSynthDefs.outputSuffix(\rc_test_hoa_perc, 2)];
		this.assertEquals(names.size, RCSynthDefs.maxNumOuts, "one variant per number of outs");
		this.assert(desc1.notNil and: { desc2.notNil }, "variants registered");
		this.assert(desc1.controlNames.includesAll([\theta, \phi, \radius, \amp, \outs, \outamps]), "direction and level controls plus the output stage");
		this.assertEquals(desc1.outputs.collect(_.numberOfChannels), [n], "the single out writes all % HOA channels".format(n));
		this.assertEquals(desc2.outputs.collect(_.numberOfChannels), [n, n], "each of two outs writes all channels");
		this.assert(desc1.hasGate.not, "a percussion def frees itself: no gate");
	}
}
