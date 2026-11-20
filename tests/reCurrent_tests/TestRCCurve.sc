// RCCurve: curved courses as Envs per key, their fit, their server arrays, lines from samples.
TestRCCurve : UnitTest {
	var savedRateLimit;

	setUp {
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown { RCLog.rateLimit = savedRateLimit }

	near { |a, b, tol = 1e-6| ^(a - b).abs < tol }

	//////// fit

	test_fit_flat_straight_curved {
		var flat = RCCurve.fit({ |s| 0.3 });
		var straight = RCCurve.fit({ |s| 2 * s });
		var sq = RCCurve.fit({ |s| s.squared });
		var err = 33.collect { |i| var s = i / 32; (sq.at(s) - s.squared).abs }.maxItem;
		this.assert(flat.isNil, "a flat curve fits to nothing (a constant will do)");
		this.assertEquals(straight.levels.size, 2, "a straight line: its two ends");
		this.assert(this.near(straight.curves.asArray.first, 0), "and a linear segment");
		this.assert(RCCurve.isCurved(straight).not and: { RCCurve.isCurved(sq) }, "isCurved tells them apart");
		this.assert(sq.levels.size <= RCCurve.maxPoints, "at most maxPoints breakpoints");
		this.assert(this.near(sq.times.sum, 1), "times as fractions of the note");
		this.assert(err <= (RCCurve.tolerance * 1.0001), "within tolerance × the excursion at every sample (%)".format(err));
		this.assertEquals(RCCurve.fit([0, 0.25, 1]).levels, [0, 1], "an Array of samples fits as evenly spaced in s");
	}

	test_fit_wave_uses_the_breakpoints {
		var env = RCCurve.fit({ |s| (2pi * s).sin });
		var err = 33.collect { |i| var s = i / 32; (env.at(s) - (2pi * s).sin).abs }.maxItem;
		this.assert(env.levels.size > 3, "a full sine needs inner breakpoints (%)".format(env.levels.size));
		this.assert(err < 0.1, "and follows it (max error %)".format(err));
	}

	test_curve_through_and_at {
		var c = RCCurve.curveThrough(0, 1, 0.3);
		this.assertEquals(RCCurve.curveThrough(0, 1, 0.5), 0, "the midpoint halfway: linear");
		this.assert(this.near(RCCurve.curveAt(0, 1, c, 0.5), 0.3), "the curve found passes the midpoint");
		this.assert(RCCurve.curveThrough(0, 1, 1.2).isNil, "no monotone segment overshoots");
		this.assert(this.near(RCCurve.curveAt(2, 4, 0, 0.25), 2.5), "curve 0: linear");
	}

	//////// paths

	test_slice_cut_reflect_clip {
		var env = RCCurve.fit({ |s| s.squared });
		var part = RCCurve.slice(env, 0.25, 0.75);
		var cut = RCCurve.cut(env, 0.5);
		this.assert(this.near(part.levels.first, env.at(0.25)) and: { this.near(part.levels.last, env.at(0.75)) }, "a slice runs from the path at s0 to the path at s1");
		this.assert(this.near(part.times.sum, 1), "its times renormalised");
		this.assert(this.near(cut.levels.last, env.at(0.5)), "cut: the part before s");
		this.assertEquals(RCCurve.reflect(Env([0, 1.5], [1]), 0, 1).levels, [0, 0.5], "reflect mirrors a level past a wall");
		this.assertEquals(RCCurve.clip(Env([0, 1.5], [1]), 0, 1).levels, [0, 1], "clip holds it there");
	}

	test_slice_keeps_a_cut_segments_shape {
		var env = Env([0, 1, 0.2], [0.6, 0.4], [4, -3]);
		var part = RCCurve.slice(env, 0.2, 0.9);
		var errs = 21.collect { |i| var u = i / 20; (part.at(u) - env.at(0.2 + (0.7 * u))).abs };
		this.assert(errs.maxItem < 1e-6, "a slice is the path itself between s0 and s1, inside cut segments too (max error %)".format(errs.maxItem));
	}

	test_map_levels_keeps_the_shape {
		var env = RCCurve.fit({ |s| s.squared });
		var mapped = RCCurve.mapLevels(env, -3, 2);
		this.assert(11.collect { |i| var s = i / 10; this.near(mapped.at(s), 2 - (3 * env.at(s))) }.every { |b| b }, "an affine map of the levels is the map of the path, between breakpoints too");
	}

	test_peak_and_end_slope {
		var lin = Env([0, 2], [1], 0);
		var bent = Env([0, 1], [1], 3);
		var eps = 1e-5, num = (bent.at(1) - bent.at(1 - eps)) / eps;
		var padded = Env([0, 1, 1], [1, 0], [0, 0]);
		this.assertEquals(RCCurve.peak(Env([0, 3, 1], [0.5, 0.5])), 3, "the highest level");
		this.assert(this.near(RCCurve.endSlope(lin), 2), "a straight path's slope per line length");
		this.assert(this.near(RCCurve.endSlope(bent), num, 1e-3), "a curved one's, at its end (% vs %)".format(RCCurve.endSlope(bent), num));
		this.assert(this.near(RCCurve.endSlope(padded), 1), "a padded end of no length is skipped");
	}

	// A path array read at an index as RCCurve's synths read it (serverArray's layout, EnvGen's: [first
	// level, stages, -99, -99, (level, time, shape, curve) per stage], stage 0 a jump in no time)
	pathAt { |arr, index|
		var level = arr[0], t = 0, n = arr[1].asInteger, res;
		index = index.clip(0, 1);
		block { |break|
			(1..n - 1).do { |k|
				var next = arr[4 + (4 * k)], dt = arr[5 + (4 * k)], c = arr[7 + (4 * k)];
				if(index <= (t + dt)) { res = RCCurve.curveAt(level, next, c, ((index - t) / dt.max(1e-9)).clip(0, 1)); break.value };
				t = t + dt;
				level = next;
			};
			res = level;
		};
		^res
	}

	test_server_array {
		var env = Env([0, 1, 0.4], [0.5, 0.5], [2.5, -1.3]);
		var arr = RCCurve.serverArray(env, 2);
		var big = Env((0..11) / 11, 1 ! 11);
		var hand = RCCurve.serverArray(Env([0, 1, 0.5], [0.2, 0.3]));
		var fitted = RCCurve.fit({ |s| (2pi * s).sin });
		var errs = 41.collect { |i| var s = i / 40; (this.pathAt(RCCurve.serverArray(fitted), s) - fitted.at(s)).abs };
		this.assertEquals(arr.size, 4 + (4 * RCCurve.maxPoints), "maxPoints stages, a jump and at most maxPoints - 1 segments: 36 values");
		this.assertEquals(arr.keep(8), [0, 3, -99, -99, 0, 0, 1, 0], "EnvGen's layout: the first level, the stages counted (the jump, two segments), no release, no loop; stage 0 jumps to the first level in no time");
		this.assert(this.near(arr[9] + arr[13], 1), "the path's times as fractions of the note");
		this.assertEquals([arr[11], arr[15]], [2.5, -1.3], "the curves as they are, not cut to integers");
		this.assertEquals(arr.keep(-4), [0.4, 1, 5, 0], "padded past the end by flat stages at the last level");
		this.assert(this.near(hand[9] + hand[13], 1), "a hand-made Env's times normalised to fractions");
		this.assert(errs.maxItem < 1e-9, "read at an index, the array is the path (max error %)".format(errs.maxItem));
		this.assert(this.near(this.pathAt(arr, 1.5), 0.4), "an index past the end holds the last level");
		this.assertEquals(RCCurve.serverArray(big, 1).size, arr.size, "a path with more breakpoints is refitted into the array");
	}

	//////// lines

	test_line_from_samples {
		var samples = 17.collect { |i| var t = 2 + (i / 4); [t, (pitch: (t - 2).squared / 16, amp: -6 + (t - 2), az: 0.5)] };
		var line = RCCurve.lineFrom(samples, 7);
		var short = RCCurve.lineFrom([[1, (pitch: 0)], [1.001, (pitch: 1)]], 1, 0.05);
		this.assertEquals([line[\onset], line[\dur], line[\id]], [2, 4, 7], "from the first sample to the last, with its id");
		this.assert(this.near(line[\from][\pitch], 0) and: { this.near(line[\to][\pitch], 1) }, "its ends are the samples' ends");
		this.assert(line[\path][\pitch].notNil, "a bending key gets its path");
		this.assert(line[\path][\amp].isNil and: { line[\path][\az].isNil }, "a straight or constant key gets none");
		this.assert(this.near(line[\path][\pitch].at(0.5), 0.25, 0.02), "the path follows the course (halfway %)".format(line[\path][\pitch].at(0.5)));
		this.assert(short[\path].isNil and: { short[\dur] == 0.05 }, "a course shorter than minDur: the straight chirp");
		this.assert(RCCurve.lineFrom([[0, (pitch: 0)]]).isNil, "one sample is no line");
	}

	test_event_controls {
		var path = (pitch: RCCurve.fit({ |s| s.squared }));
		var ev = RCCurve.eventControls((), path, [\pitch, \amp], 2);
		var plain = RCCurve.eventControls((), 0, [\pitch], 2, false);
		this.assert(ev[\pitch_env].size == 1 and: { ev[\pitch_env][0].size == (4 + (4 * RCCurve.maxPoints)) }, "a curved key: its array, wrapped once");
		this.assertEquals([ev[\pitch_curved], ev[\amp_curved]], [1, 0], "the flags: on for the curved key, off for the other");
		this.assert(this.near(ev[\pitch_tail], RCCurve.endSlope(path[\pitch])) and: { ev[\pitch_peak] == 1 }, "its tail and its peak");
		this.assert(plain[\pitch_curved] == 0 and: { plain[\pitch_tail].isNil }, "no path: the flag off, no tail");
	}

	test_synth_helpers_declare_their_controls {
		var def = SynthDef(\test_rc_curve, {
			var ramp = Line.kr(0, 2, 1);
			Out.kr(0, [RCCurve.course(\pitch, 0, 1, ramp), RCCurve.glide(\amp, 0, -6, 1)]);
		});
		var names = def.allControlNames.collect(_.name);
		this.assert(names.includesAll([\pitch_env, \pitch_curved, \pitch_tail, \amp_env, \amp_curved]), "course and glide declare their controls (%)".format(names));
		this.assert(def.children.count { |u| u.isKindOf(IEnvGen) } == 0 and: { def.children.count { |u| u.isKindOf(EnvGen) } == 2 }, "no IEnvGen (it keeps its first path, VK-8): the glide's path and its straight line are EnvGens, the course a reader");
		this.assert(def.allControlNames.select { |c| c.name.asString.endsWith("_env") }.every { |c| c.rate == \control }, "the paths are kr controls: an /n_set of another path reaches a running synth");
	}

	// On the server (an NRT render, synchronous): a synth started on one path per key and sent another
	// with t_trig halfway (a continuous grain's next glide) plays the second, its curves as they are.
	// Until Nov 20 2026 IEnvGen played the first again, every curve cut to an integer (VK-8, HL3-P18).
	// glide: EnvGen along the glide's time; course: the reader at the glide's index. A third key glides
	// straight first, then curved: its path starts at its own first level, not where its EnvGen was.
	test_server_plays_each_glides_path {
		var stem = PathName.tmp +/+ "TestRCCurve_%_%".format(Date.getDate.stamp, 100000.rand);
		var osc = stem ++ ".osc", wav = stem ++ ".wav", sr = 48000, half = 0.5, tol = 0.05;
		var p1 = Env([0, 2, 1], [0.5, 0.5], [0.9, -2.5]), p2 = Env([1, -1, 0.5], [0.4, 0.6], [3.3, 0]);
		var q1 = Env([1, -1], [1], [1.7]), q2 = Env([-1, 1, 0], [0.5, 0.5], [-0.6, 2.2]);
		var def = SynthDef(\test_rc_curve_paths, { |line_dur = 0.5, t_trig = 1|
			var g = RCCurve.glide(\pitch, \pitch0.kr(0), \pitch1.kr(0), line_dur, t_trig);
			var c = RCCurve.course(\az, \az0.kr(0), \az1.kr(0), RCCurve.glideIndex(line_dur, t_trig));
			var e = RCCurve.glide(\el, \el0.kr(0), \el1.kr(0), line_dur, t_trig);
			Out.ar(0, K2A.ar([g, c, e]));
		});
		var straight = Env([0, 3], [1]), fracs = [0.2, 0.4, 0.7, 0.85], sf, data, at, errs;
		Score([
			[0.0, [\d_recv, def.asBytes]],
			[0.0, [\s_new, def.name, 1000, 0, 0, \line_dur, half, \pitch_curved, 1, \pitch_env, RCCurve.serverArray(p1), \az_curved, 1, \az_env, RCCurve.serverArray(q1), \el0, 0, \el1, 3].asOSCArgArray],
			[half, [\n_set, 1000, \t_trig, 1, \pitch_env, RCCurve.serverArray(p2), \az_env, RCCurve.serverArray(q2), \el_curved, 1, \el_env, RCCurve.serverArray(p2)].asOSCArgArray],
			[2 * half, [\c_set, 0, 0]]
		]).writeOSCFile(osc);
		[Platform.resourceDir +/+ "scsynth", "-N", osc, "_", wav, sr, "WAV", "float", "-o", 3, "-i", 0].collect { |a| a.asString.shellQuote }.join(" ").unixCmdGetStdOut;
		sf = SoundFile.openRead(wav);
		File.delete(osc);
		this.assert(sf.notNil, "the render was written (%)".format(wav));
		if(sf.isNil) { ^this };
		data = FloatArray.newClear(sf.numFrames * 3);
		sf.readData(data);
		sf.close;
		File.delete(wav);
		at = { |t, ch| data[((t * sr).round.asInteger + 63) * 3 + ch] };   // the block's value (K2A reaches it at the block's end)
		errs = { |ch, env, t0| fracs.collect { |s| (at.(t0 + (s * half), ch) - RCCurve.at(env, s)).abs }.maxItem };
		this.assert(errs.(0, p1, 0) < tol, "glide: the first path, its curves exact (max error %)".format(errs.(0, p1, 0)));
		this.assert(errs.(0, p2, half) < tol, "glide: the second path, sent to the running synth (max error %; the first again: %)".format(errs.(0, p2, half), errs.(0, p1, half)));
		this.assert(errs.(1, q1, 0) < tol, "course: the first path, its curve exact (max error %)".format(errs.(1, q1, 0)));
		this.assert(errs.(1, q2, half) < tol, "course: the second path (max error %; the first again: %)".format(errs.(1, q2, half), errs.(1, q1, half)));
		this.assert(errs.(2, straight, 0) < tol, "glide: a straight first glide (max error %)".format(errs.(2, straight, 0)));
		this.assert(errs.(2, p2, half) < tol and: { (at.(half, 2) - p2.levels.first).abs < tol }, "glide: then curved, the path from its own first level (max error %, at the restart %)".format(errs.(2, p2, half), at.(half, 2)));
	}
}
