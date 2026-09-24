// reCording — take paths and the "replay a take while recording the result"
// session used for AmbiX conversions (the ~rc_do_recording of the pieces).
//
//   RETake.path("~/piece/RecorderSC", "LeafHues/AmbiX", "AmbiX Out", "w1", "aiff")
//       → ~/piece/RecorderSC/LeafHues/AmbiX/<yymmdd_hhmmss>_AmbiX Out w1.aiff
//   RETake.session(~song.recorder(\ambix), ~song.replay(\take), minutes: 9, seconds: 24, quant: [4, 0])
//       starts both on the quantized beat, stops the recorder after the duration (+ margin) and
//       returns the total seconds.

RETake {

	// <root>/<subfolder>/<stamp>_<name>[ <version>].<format>; stamp defaults to now.
	*path { |root, subfolder = "", name, version = "", format = "aiff", stamp|
		var file = (stamp ?? { Date.localtime.stamp }).asString ++ "_" ++ name.asString;
		var dir = root.asString;
		if(version.asString.size > 0) { file = file ++ " " ++ version.asString };
		file = file ++ "." ++ format.asString;
		if(subfolder.asString.size > 0) { dir = dir +/+ subfolder.asString };
		^dir +/+ file
	}

	// Record the replay: recorder.start and replay.start at quant on clock (the
	// recorder's by default), then recorder.stopAfter(total) armed at the same
	// beat. Returns the total seconds; refused (error) when the server is down.
	*session { |recorder, replay, minutes = 0, seconds = 0, quant = #[4, 0], clock, margin = 10|
		var total = (60 * minutes) + seconds + margin;
		var c = clock ? recorder.clock ? TempoClock.default;
		if(recorder.server.serverRunning.not) { RCLog.error(\take, "session: server not running"); ^total };
		recorder.start(quant, c);
		replay !? { |r| r.start(quant, c) };
		Routine { recorder.stopAfter(total) }.play(c, quant);
		RCLog.post(\take, "session: % s of recording".format(total));
		^total
	}
}
