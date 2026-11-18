// reLation — time-spans and what moves them (Lewin): a context for rhythm.
//
// A time-span is [onset, duration], a plain Array, the duration above zero. The spans are also the elements of
// a group, [u, d] with
//
//   [u1, d1] [u2, d2] = [u1 + d1 u2, d1 d2]
//
// which moves them from both sides, as T, I and P, L, R move triads:
//
//   on the left    [u, d] [t, D] = [u + d t, d D]      everything stretched by d and shifted by u
//   on the right   [t, D] [u, d] = [t + D u, D d]      "move by u of my own durations, then scale my duration by d"
//
// The two commute. A rhythm is a walk: a first span and a word of right steps. The step from a note to the
// next one is [1, r], r the ratio of their durations; so the same word, from another span, is the same rhythm
// at another time and tempo, and several voices on one word are a canon in augmentation.
//
//   ~time = RLTimeSpans.new;
//   ~time.define(\same, [1, 1]);  ~time.define(\half, [1, 0.5]);  ~time.define(\twice, [1, 2]);
//   ~walk = RLWalk(~time, [0, 1], [\half, \same, \twice, \same]);
//   ~walk.chords;                                   // [0, 1], [1, 0.5], [1.5, 0.5], [2, 1], [3, 1]
//   ~time.stretch(1.5).(~walk.chords.last);         // [4.5, 1.5]: the last span, everything one and a half times slower
//   ~time.word([1, 0.5, 0.5, 1, 1]);                // the steps of a list of durations
//   ~time.interval([0, 1], [3, 2]);                 // [3, 2]: three of my durations later, twice as long
//   ~time.wait([2, 0.5], 3);                        // 2: the cue at 3 is two of this span's durations away
//
// Transformations are RLTransforms and walks are RLWalks (their chords are the spans).

RLTimeSpans {
	var named;

	*new { ^super.new.prInit }

	prInit { named = IdentityDictionary.new }

	// the group; results are rounded at the tenth decimal so that equal spans compare equal
	identity { ^[0, 1] }
	mul { |g, h| ^[(g[0] + (g[1] * h[0])).round(1e-10), (g[1] * h[1]).round(1e-10)] }
	inv { |g| ^[(g[0].neg / g[1]).round(1e-10), (1 / g[1]).round(1e-10)] }

	// what RLTransform asks of a context: a span is its own element, and nothing but the identity is central
	element { |span| ^span }
	chordOf { |g| ^g }
	isCentral { |g| ^(g[0].abs < 1e-9) and: { (g[1] - 1).abs < 1e-9 } }
	center { ^[] }
	prBefore { |g, h| ^(g[0] < h[0]) or: { (g[0] == h[0]) and: { g[1] < h[1] } } }
	identityTransform { ^RLTransform(this) }
	system { ^nil }

	// the transformations: on the left everything is moved alike, on the right each span by its own measure
	left { |u = 0, d = 1| ^RLTransform(this, [u, d], nil, "left(%, %)".format(u, d)) }
	right { |u = 0, d = 1| ^RLTransform(this, nil, [u, d], "(%, %)".format(u, d)) }
	shift { |u| ^this.left(u, 1) }
	stretch { |d| ^this.left(0, d) }

	// Name a right step [u, d], or the step a word does (its steps in the order of time).
	define { |name, step|
		var tr;
		if(step.isKindOf(SequenceableCollection) and: { step.every(_.isNumber) }) {
			tr = RLTransform(this, nil, step, name.asString);
		} {
			tr = RLTransform(this);
			step.do { |w| tr = this.transform(w) <> tr };
			tr.label = name.asString;
		};
		named[name.asSymbol] = tr;
		^tr
	}
	transform { |name|
		^if(name.isKindOf(RLTransform)) { name } {
			named[name.asSymbol] ?? { Error("RLTimeSpans: no step named %".format(name)).throw }
		}
	}
	names { ^named.keys.asArray.sort }
	act { |name, span| ^[this.transform(name).value(span)] }

	// The right step from one span to another (Lewin's interval), and the left one.
	interval { |x, y| ^this.mul(this.inv(x), y) }
	leftInterval { |x, y| ^this.mul(y, this.inv(x)) }

	// A list of durations as a rhythm: its spans from an onset, each starting where the one before ends; the
	// word of right steps between them; and back from spans to durations.
	spans { |durations, onset = 0|
		var t = onset;
		^durations.collect { |d| var span = [t, d]; t = t + d; span }
	}
	word { |durations|
		var out = Array.new;
		durations.doAdjacentPairs { |a, b| out = out.add(this.right(1, b / a)) };
		^out
	}
	// The spans between successive time points (the points of an RCSieve, for one).
	spansOf { |points|
		var out = Array.new;
		points.doAdjacentPairs { |a, b| out = out.add([a, b - a]) };
		^out
	}

	// How long until a cue, counted in the span's own durations (the truth value of "after the cue" for
	// time-spans): negative once the cue is past.
	wait { |span, cue| ^(cue - span[0]) / span[1] }

	// Spans as what a Pbind takes: [\delta, \sustain] pairs, the time to the next onset and the duration.
	events { |spans|
		^spans.collect { |s, i| [if(i < (spans.size - 1)) { spans[i + 1][0] - s[0] } { s[1] }, s[1]] }
	}

	== { |that| ^that.isKindOf(RLTimeSpans) }
	!= { |that| ^(this == that).not }
	hash { ^\RLTimeSpans.hash }

	printOn { |stream| stream << "RLTimeSpans" }
}
