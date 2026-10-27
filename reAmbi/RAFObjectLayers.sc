// reAmbi — the effect layers of RAFObject: wash, freeze, shimmer, ring, grains, haze, fog, drone,
// registered at class initialisation (RAFObject.addLayer), and their language laws (the wash's delay
// times from a tuning and an irrational blend, the ring's frequencies, the grains' rates). A layer's
// process takes the beam (mono), reads its controls from ctx under their bare names and gives [nTaps
// mono taps, the limiter's gain] (RAFObject.limit: the taps under one gain). Every loop feeds back
// through its channel of the def's shared LocalIn (ctx[\fb], ctx[\fbOut]). Needs atk-sc3 and
// sc3-plugins (PV_Freeze from JoshUGens, Greyhole from DEINDUGens).
//
//   RAFObjectLayers.times(0.75, \otonal, 0.3)     // the wash's six delay times
//   RAFObjectLayers.ringFreqs(240, \just, 1)      // the ring's six frequencies
//   RAFObject.layer(\wash)[\controls]             // a layer's controls and defaults
//
// wash: six combs chained inside one long feedback loop (the wash of HadronLake3's Bowls, bound to a
//   place): a ping repeats at each comb's time, the sums of the times, their multiples; at irrational 0
//   every echo lies on the grid of the tuning's ratios over `time`, at 1 the times are offset by square
//   roots of distinct primes (no integer combination of them vanishes: no common period).
// freeze: a spectral hold of what last sounded in the object, refreshed by a trigger (a meeting under
//   the cloud) and fading over `hold`; its taps decorrelated by a random phase per bin.
// shimmer: a pitch-shifted feedback delay: each pass a ratio higher, the tail climbs until `dark` ends it.
// ring: six sympathetic resonators on a tuning over a base, a sine on a resonance at 0 dB whatever the
//   decay (Ringz's gain grows with the decay and the sample rate: normalised away); `strike` 1 drives
//   them only by the rises (a struck harp), 0 continuously (only the pool's own pitches sing).
// grains: a ring of the last four seconds, grains drawn from the last `window` seconds at the taps'
//   rates (an octave up and down, a fifth, a fourth, backwards), `scatter` the share away from the plain stream.
// haze: a frequency-shift feedback delay: a partial drifts `shift` Hz a pass, its mirror at the other tap.
// fog: a dense short diffusion (Greyhole) without a long tail, darker with the distance.
// drone: the beam strictly convolved with the last third of a second of another bus (the drone's W, a
//   kernel the synth itself records and reloads every `refresh` seconds, coherently normalised: a ping on
//   one of the drone's partials comes out at 0 dB for `norm` 3, off them 33 dB down), fed back so that
//   each pass convolves again: the tail converges on the partials the ping shares with the drone. Needs
//   the drone's bus (`droneBus`) and a kernel buffer the object allocates. One frame (0.34 s) of pre-delay.
RAFObjectLayers {
	*initClass {
		Class.initClassTree(RCLog);
		Class.initClassTree(RCUtil);
		Class.initClassTree(RAFObject);
		this.addWash; this.addFreeze; this.addShimmer; this.addRing; this.addGrains; this.addHaze; this.addFog; this.addDrone;
	}

	//////// the laws

	// the tunings: ratios a delay time or a base frequency is multiplied by, tap by tap
	*tunes {
		^(harmonic: [1, 2, 3, 4, 5, 6], otonal: [1, 5/4, 3/2, 7/4, 2, 9/4], just: [1, 9/8, 5/4, 3/2, 5/3, 15/8],
			fifths: [1, 3/2, 9/4, 27/8, 81/16, 243/32], sub: [1, 1/2, 1/3, 1/4, 1/5, 1/6],
			golden: Array.fill(6, { |k| ((1 + 5.sqrt) / 2) ** (k - 2) }))
	}
	*tuneNames { ^this.tunes.keys.asArray.sort }

	// fixed offsets of the delay times at irrational 1: square roots of six distinct primes (linearly
	// independent over the rationals with 1), scaled into about ±0.25, signs alternating
	*irrationals { ^[(2.sqrt - 1) / 2, (3.sqrt - 1) / -4, 5.sqrt - 2, (7.sqrt - 2) / -3, (11.sqrt - 3) / 1.5, (13.sqrt - 3) / -3] }

	*prTune { |tune, fallback|
		^this.tunes[tune.asSymbol] ?? { RCLog.warn(\ambi, "RAFObjectLayers: unknown tune % (%): %".format(tune, this.tuneNames, fallback)); this.tunes[fallback] }
	}

	// the wash's delay times: time × the tuning's ratio × (1 + irrational × the tap's offset), 0.01 to 7.9 s
	*times { |time = 0.75, tune = \harmonic, irrational = 0, n = 6|
		var r = this.prTune(tune, \harmonic), e = this.irrationals;
		^Array.fill(n, { |k| (time * r.wrapAt(k) * (1 + (irrational.clip(0, 1) * e.wrapAt(k)))).clip(0.01, 7.9).asFloat })
	}

	// the ring's frequencies: base × the tuning's ratio × 2^octave, 20 Hz to 18 kHz
	*ringFreqs { |base = 240, tune = \just, octave = 0, n = 6|
		var r = this.prTune(tune, \just);
		^Array.fill(n, { |k| (base * r.wrapAt(k) * (2 ** octave)).clip(20, 18000).asFloat })
	}

	// the grains' rates, tap by tap: the plain stream, an octave up, an octave down, a fifth, a fourth, backwards
	*grainRates { ^[1, 2, 0.5, 1.5, 4/3, -1] }

	//////// the layers

	*addWash {
		RAFObject.addLayer(\wash, (
			nTaps: 6, fbChannels: 1, tapOrder: \radius,
			lang: [\time, 0.75, \tune, \harmonic, \irrational, 0],
			arrays: [\times, (n: 6, from: [\time, \tune, \irrational], law: { |p, n| RAFObjectLayers.times(p[\time], p[\tune], p[\irrational], n) })],
			controls: [\decay, 20, \regen, 2, \taps, 6, \held, 0, \hp, 120, \dark, 4000, \drive, 0.15, \blur, 0.6, \wobble, 0.3, \wobbleRate, 0.1],
			doc: "six combs chained at high feedback inside one long loop: a long tail smeared, its rhythm from a tuning (time, tune, irrational)",
			process: { |beam, nTaps, ctx|
				var times = RAFObject.lagKr(ctx[\times], 2), decay = ctx[\decay], regen = ctx[\regen].clip(0.1, 6), nOn = ctx[\taps];   // six combs in series resonate at every multiple of 1/time together: 12 s regen swelled the bass of a live pool to the limiter; 2 s adds about 4 dB
				var fz = ctx[\held].clip(0, 1), hp = ctx[\hp], dark = ctx[\dark], drive = ctx[\drive].clip(0, 1), blur = ctx[\blur].clip(0, 1);
				var wobble = ctx[\wobble].clip(0, 1), wobbleRate = ctx[\wobbleRate], fb = ctx[\fb][0];
				var wob, t, on, g, loop, k, sat, x, outs, lim;
				wob = 1 + (wobble * 0.02 * (LFNoise2.kr(wobbleRate) + (0.5 * SinOsc.kr(wobbleRate * 0.37, 1.3))));
				t = (times * wob).clip(0.01, 7.9);
				on = Array.fill(nTaps, { |k| Lag.kr(nOn > k, 0.1) });                   // comb k runs while taps > k
				g = Select.kr(fz, [10 ** (-3 * (t * on).sum / decay.max(0.1)), 1]).min(1);   // a pass round the chain lasts the running times' sum
				loop = (beam * (1 - fz)) + (fb * g);
				loop = LPF.ar(HPF.ar(loop, hp.clip(10, 2000)), dark.clip(200, 16000));
				k = drive * 4; sat = (loop * (1 + k)).tanh / (1 + k); loop = loop + ((sat - loop) * drive);
				loop = AllpassC.ar(loop, 0.05, 0.0137, blur * 0.35);
				loop = AllpassC.ar(loop, 0.05, 0.0293, blur * 0.5);
				x = loop;
				outs = Array.fill(nTaps, { |k| var y = CombC.ar(x, 8, t[k], regen); x = (y * on[k]) + (x * (1 - on[k])); y * on[k] });
				#outs, lim = RAFObject.limit(outs ++ [x]);                                // the taps and the chain's end under one gain
				ctx[\fbOut] = [outs.last];
				[outs.drop(-1), lim]
			}
		))
	}

	*addFreeze {
		RAFObject.addLayer(\freeze, (
			nTaps: 3, trigger: true,
			controls: [\refresh, 0.2, \hold, 20, \smear, 0, \auto, 0, \hp, 120, \dark, 8000],
			doc: "a spectral hold of what last sounded, refreshed by the trigger (auto 1: by its own rises too), fading over hold; smear in bins",
			process: { |beam, nTaps, ctx|
				var win = 8192, hold = ctx[\hold].max(0.05), smear = ctx[\smear].clip(0, 128), refresh = ctx[\refresh].clip(0.05, 2);
				var hp = ctx[\hp], dark = ctx[\dark], auto = ctx[\auto], trig = ctx[\trig];
				var exc = HPF.ar(beam, hp.clip(10, 2000)), fast, slow, ownT, t, open, chain, chains, env, taps;
				fast = Amplitude.kr(exc, 0.001, 0.03); slow = Amplitude.kr(exc, 0.08, 0.4);
				ownT = Trig1.kr(Trig1.kr(fast > ((slow * 1.5) + 1e-4), 0.5), ControlDur.ir);   // one block at each rise, half a second apart at most
				t = trig + (auto * ownT);
				open = Trig1.kr(t, refresh);                                               // the hold open for refresh: the live sound passes, its last frame is kept
				chain = FFT(LocalBuf(win), exc, 0.5, 0, 1, win);
				chain = PV_Freeze(chain, 1 - open);
				chain = PV_MagSmear(chain, smear);
				chains = [chain] ++ Array.fill(nTaps - 1, { PV_Diffuser(PV_Copy(chain, LocalBuf(win)), Impulse.kr(0)) });
				env = Decay2.kr(t, 0.05, hold).min(1);                                     // the one-block trigger, never the gate
				taps = chains.collect { |c| LPF.ar(IFFT(c, 0, win), dark.clip(200, 16000)) * env };
				RAFObject.limit(taps)
			}
		))
	}

	*addShimmer {
		RAFObject.addLayer(\shimmer, (
			nTaps: 3, fbChannels: 1,
			controls: [\ratio, 2, \time, 0.4, \decay, 12, \held, 0, \hp, 120, \dark, 6000],
			doc: "a pitch-shifted feedback delay: the tail a ratio higher each pass (2 an octave, 3/2 a fifth...) until dark ends the climb",
			process: { |beam, nTaps, ctx|
				var ratio = Lag.kr(ctx[\ratio], 0.5).clip(0.25, 4), time = Lag.kr(ctx[\time], 1).clip(0.02, 1.9), decay = ctx[\decay];
				var fz = ctx[\held].clip(0, 1), hp = ctx[\hp], dark = ctx[\dark], fb = ctx[\fb][0];
				var g, loop, d, s, s2, taps, lim;
				g = Select.kr(fz, [10 ** (-3 * (time + 0.2) / decay.max(0.1)), 1]).min(1);   // a pass = the delay plus the shifter's window
				loop = (beam * (1 - fz)) + (fb * g);
				loop = LPF.ar(HPF.ar(loop, hp.clip(10, 2000)), dark.clip(200, 16000));
				d = DelayC.ar(loop, 2, time);
				s = PitchShift.ar(d, 0.2, ratio, 0, 0.01);
				s2 = PitchShift.ar(s, 0.2, ratio, 0, 0.01);
				#taps, lim = RAFObject.limit([d, s, s2]);
				ctx[\fbOut] = [taps[1]];
				[taps * [0.7, 1, 0.6], lim]
			}
		))
	}

	*addRing {
		RAFObject.addLayer(\ring, (
			nTaps: 6, tapOrder: \elevation,
			lang: [\base, 240, \tune, \just, \octave, 0],
			arrays: [\freqs, (n: 6, from: [\base, \tune, \octave], law: { |p, n| RAFObjectLayers.ringFreqs(p[\base], p[\tune], p[\octave], n) })],
			controls: [\decay, 8, \gain, 4, \bright, 0.5, \strike, 0, \hp, 80, \dark, 12000],
			doc: "six sympathetic resonators on a tuning over a base (base, tune, octave); strike 0 driven continuously, 1 by the rises only",
			process: { |beam, nTaps, ctx|
				var freqs = RAFObject.lagKr(ctx[\freqs], 0.5).clip(30, 8000), decay = Lag.kr(ctx[\decay], 1).clip(0.3, 60);
				var gain = ctx[\gain], bright = ctx[\bright].clip(0, 1), strike = ctx[\strike].clip(0, 1), hp = ctx[\hp], dark = ctx[\dark];
				var exc = HPF.ar(beam, hp.clip(10, 2000)), fast, slow, hit, norm, taps;
				fast = Amplitude.ar(exc, 0.001, 0.03); slow = Amplitude.ar(exc, 0.08, 0.4);
				hit = Trig1.ar(fast > ((slow * 1.5) + 1e-4), 0.08);
				exc = exc * ((1 - strike) + (strike * hit));
				norm = 13.8 / (decay * ctx[\sr]);                                          // a sine on the resonance at 0 dB whatever decay and sr
				taps = Array.fill(nTaps, { |k| LPF.ar(Ringz.ar(exc, freqs[k], decay) * norm * gain * ((freqs[k] / 240) ** ((bright - 0.5) * 2)), dark.clip(200, 16000)) });
				RAFObject.limit(taps)
			}
		))
	}

	*addGrains {
		RAFObject.addLayer(\grains, (
			nTaps: 6,
			arrays: [\rates, (n: 6, default: this.grainRates)],
			controls: [\density, 12, \dur, 0.15, \window, 2, \scatter, 0.5, \hp, 120, \dark, 12000],
			doc: "grains of the last window seconds at the taps' rates (tap 1 the plain stream; negative backwards), density a second in all, scatter the share away from the plain stream",
			process: { |beam, nTaps, ctx|
				var frames = 192000, buf = LocalBuf(frames).clear;                         // 4 s at 48 kHz; cleared: a local buffer is not
				var rates = RAFObject.lagKr(ctx[\rates], 0.5).clip(-4, 4);
				var density = ctx[\density].clip(0, 60), dur = ctx[\dur].clip(0.02, 1), window = ctx[\window].clip(0.1, 3), scatter = ctx[\scatter].clip(0, 1);
				var hp = ctx[\hp], dark = ctx[\dark], wr = Phasor.ar(0, 1, 0, frames), head, taps;
				BufWr.ar(HPF.ar(beam, hp.clip(10, 2000)), buf, wr, 1);
				head = A2K.kr(wr);
				taps = Array.fill(nTaps, { |k|
					var share = if(k == 0) { 1 - scatter } { scatter / (nTaps - 1) }, trig = Dust.kr(density * share);
					var back = TRand.kr(0.03, window, trig) + (dur * rates[k].abs.max(1)) + 0.02;   // never across the write head
					var pos = (head - (back * ctx[\sr])).wrap(0, frames) / frames;
					LPF.ar(GrainBuf.ar(1, trig, dur, buf, rates[k], pos, 2, 0, -1, 64), dark.clip(200, 16000))
				});
				RAFObject.limit(taps)
			}
		))
	}

	*addHaze {
		RAFObject.addLayer(\haze, (
			nTaps: 2, fbChannels: 1, tapOrder: \elevation,
			controls: [\shift, 0.7071, \time, 0.5, \decay, 15, \held, 0, \hp, 120, \dark, 6000],
			doc: "a frequency-shift feedback delay: a partial drifts shift Hz a pass (signed), its mirror at the other tap",
			process: { |beam, nTaps, ctx|
				var shift = Lag.kr(ctx[\shift], 0.5).clip(-20, 20), time = Lag.kr(ctx[\time], 1).clip(0.02, 1.9), decay = ctx[\decay];
				var fz = ctx[\held].clip(0, 1), hp = ctx[\hp], dark = ctx[\dark], fb = ctx[\fb][0];
				var g, loop, d, taps, lim;
				g = Select.kr(fz, [10 ** (-3 * time / decay.max(0.1)), 1]).min(1);
				loop = (beam * (1 - fz)) + (fb * g);
				loop = LPF.ar(HPF.ar(loop, hp.clip(10, 2000)), dark.clip(200, 16000));
				d = DelayC.ar(loop, 2, time);
				#taps, lim = RAFObject.limit([FreqShift.ar(d, shift), FreqShift.ar(d, shift.neg)]);
				ctx[\fbOut] = [taps[0]];                                                  // the signed sideband alone (both back: plain amplitude modulation)
				[taps, lim]
			}
		))
	}

	*addFog {
		RAFObject.addLayer(\fog, (
			nTaps: 2,
			controls: [\time, 0.12, \room, 1.5, \diff, 0.8, \feedback, 0.35, \damp, 0.6, \modDepth, 0.15, \modFreq, 0.7, \dark, 5000, \hp, 120, \gain, 1],
			doc: "a dense short diffusion (Greyhole: time, room, diff, feedback under 0.7, damp, a slow chorus) without a long tail, darker with the distance",
			process: { |beam, nTaps, ctx|
				var time = Lag.kr(ctx[\time], 1).clip(0.1, 0.5), room = Lag.kr(ctx[\room], 1).clip(0.5, 5), diff = ctx[\diff].clip(0, 1);
				var feedback = ctx[\feedback].clip(0, 0.7), damp = ctx[\damp].clip(0, 1), modDepth = ctx[\modDepth].clip(0, 1), modFreq = ctx[\modFreq].clip(0, 10);
				var dark = ctx[\dark], hp = ctx[\hp], gain = ctx[\gain], lpf, wet;
				lpf = (dark * (ctx[\ref] / ctx[\dist]).clip(0.15, 1)).clip(200, 16000);     // a deep fog darker than a deep source
				wet = Greyhole.ar(HPF.ar(beam, hp.clip(10, 2000)), time, damp, room, diff, feedback, modDepth, modFreq);
				RAFObject.limit(LPF.ar(wet, lpf) * gain)
			}
		))
	}

	*addDrone {
		RAFObject.addLayer(\drone, (
			nTaps: 2, fbChannels: 1,
			buffers: [(control: \kernelBuf, frames: 16384, channels: 1)],
			needs: [\droneBus],
			controls: [\refresh, 1, \time, 0.5, \decay, 20, \norm, 3, \held, 0, \hp, 120, \dark, 6000],
			doc: "the beam convolved with the last 0.34 s of the drone's W (droneBus), the kernel reloaded every refresh seconds, fed back: the tail converges on the drone's partials",
			process: { |beam, nTaps, ctx|
				var n = 16384, droneBus = ctx[\droneBus], kernelBuf = ctx[\kernelBuf], refresh = ctx[\refresh].clip(0.35, 30);
				var time = Lag.kr(ctx[\time], 1).clip(0.02, 1.9), decay = ctx[\decay], norm = ctx[\norm], fz = ctx[\held].clip(0, 1);
				var hp = ctx[\hp], dark = ctx[\dark], fb = ctx[\fb][0], sr = ctx[\sr];
				var droneW = LeakDC.ar(InFeedback.ar(droneBus, 1)), wr = Phasor.ar(0, 1, 0, n), wrap, every, trig, rmsH, scale, g, loop, conv, ret, taps, lim;
				BufWr.ar(droneW, kernelBuf, wr, 1);                                        // a ring of the drone: at a wrap the buffer holds the last n samples in order
				wrap = Impulse.ar(sr / n);
				every = (refresh * sr / n).round.max(1);
				trig = wrap * ((PulseCount.ar(wrap) % every) < 1);                        // the kernel re-read every `every` wraps
				rmsH = Latch.ar(RunningSum.rms(droneW, n), trig).lag(0.05);              // the RMS of the kernel in use
				scale = norm / (rmsH.max(1e-3) * n);                                      // coherent: a ping on a partial at norm / sqrt(2 × partials)
				g = Select.kr(fz, [10 ** (-3 * (time + (n / sr)) / decay.max(0.1)), 1]).min(1);
				loop = (beam * (1 - fz)) + (fb * g);
				loop = LPF.ar(HPF.ar(loop, hp.clip(10, 2000)), dark.clip(200, 16000));
				conv = Convolution2L.ar(loop, kernelBuf, trig, n, 1) * scale;             // a new kernel crossfaded over one frame
				ret = DelayC.ar(conv, 2, time);
				#taps, lim = RAFObject.limit([conv, ret]);
				ctx[\fbOut] = [taps[1]];
				[taps, lim]
			}
		))
	}
}
