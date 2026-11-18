// reLation — a canon shown on HomewareVisuals' canon page (/canon/): one row of bars for each voice, and under
// the rows which voice starts a note on each pulse (a tiling canon fills that line, one voice per pulse).
//
// The page only draws; the spans are computed here. Messages go through RCVisuals, as every page's do.
//
//   ~time = RLTimeSpans.new;
//   ~subject = ~time.spans([1, 0.5, 0.5, 1, 1, 0.5, 0.5, 1, 2]);
//   RLCanonPage.canon(~subject, [[0, 1], [8, 1], [0, 2]], title: "a canon in augmentation");
//
//   ~pair = RLMetre.perfect([0, 1, 2], 5).first;            // [stretches, entries]
//   RLCanonPage.canon([[0, 1], [1, 1], [2, 1]], ~pair[1].collect { |u, i| [u, ~pair[0][i]] }, pulses: 15);
//   RLCanonPage.head(4);                                     // a line at beat 4; nil takes it away

RLCanonPage {

	// voices: for each voice its spans [onset, duration], in beats; names: a name for each row; heights: for
	// each voice one number for each note (pitches, for one: scaled together to the height of a row); pulses:
	// how many pulses the line of pulses has, or [count, beats in a pulse].
	*show { |voices, names, heights, pulses, title|
		var all = heights !? { heights.flat }, lo = all !? { all.minItem }, hi = all !? { all.maxItem };
		RCVisuals.send('/canon/clear');
		title !? { RCVisuals.send('/canon/title', title.asString) };
		pulses !? { RCVisuals.send('/canon/pulses', pulses.asArray[0], pulses.asArray[1] ? 1) };
		voices.do { |spans, v|
			RCVisuals.send('/canon/voice', v, (names !? { names[v] } ? v).asString);
			spans.do { |s, k|
				if(heights.isNil) { RCVisuals.send('/canon/span', v, s[0], s[1]) } {
					RCVisuals.send('/canon/span', v, s[0], s[1], if(hi == lo) { 0.5 } { ((heights[v][k] - lo) / (hi - lo)).round(0.001) });
				};
			};
		};
	}

	// One rhythm under several left steps [u, d] of the time-spans group: the voices of RLTimeSpans:canon, each
	// row named by its step.
	*canon { |spans, lefts, heights, pulses, title|
		^this.show(RLTimeSpans.new.canon(spans, lefts), lefts.collect { |g| "[%, %]".format(g[0], g[1]) }, heights, pulses, title)
	}

	// Pulse layers as rows (metre: RLTimePoints): each layer, [period, phase] or a period, by its points from
	// `from` up to `to`, a bar half a period long from each point. Under the rows, the finest pulse all the
	// points fall on, and which layers sound on each of its points: where two layers meet, and where they
	// never do.
	*layers { |layers, from = 0, to = 1, names, title|
		var time = RLTimePoints.new, list = layers.collect { |l| if(l.isNumber) { [l, 0] } { [l[0], l[1] ? 0] } }, unit = 0;
		list.do { |l| unit = RLPulse.gcd(RLPulse.gcd(unit, l[0]), (l[1] - from).abs) };
		^this.show(list.collect { |l| time.layer(l, from, to).collect { |t| [(t - from).round(1e-10), l[0] / 2] } },
			names ?? { list.collect { |l| RLPulse(l[0]).name } }, nil, [((to - from) / unit).round.asInteger, unit], title)
	}

	// A line at a beat (where the playing is); nil takes it away.
	*head { |beat| ^RCVisuals.send('/canon/head', beat ? -1) }
}
