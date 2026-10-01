// reCurrent — the stochastic laws of the texture rigs (Xenakis, Formalized Music): probability
// distributions as inverse distribution functions of one uniform draw u in [0, 1) ("the machine
// can only draw numbers y0 at random with equiprobability between 0 and 1; we modulate this
// probability", p. 142 and App. I-II), so that a seeded uniform stream gives a reproducible result
// under any law; random walks with elastic barriers (ch. IX method 4, GENDYN's mirrors ch. XIII);
// Poisson counts (ch. I); an entropy in bits (the ataxy, ch. II p. 63).
//
//   RCLaws.value(\linear, nil, 0, 3);        // Xenakis' second law on [0, 3]: small values likelier, mean 1
//   RCLaws.step(\cauchy, nil, 0.1);          // a signed step: mostly small, sometimes a leap
//   RCLaws.reflect(1.2, 0, 1);               // 0.8: the excursion comes back from the barrier
//   RCLaws.poisson(0.6);                     // how many events in a cell of mean density 0.6
//   RCLaws.poissonTable(0.6, 196);           // [107, 65, 19, 4, 1, 0, 0]: Achorripsis' table (p. 29)
//   RCUtil.seeded(1994, { 100.collect { RCLaws.value(\gauss) } });   // the same hundred every time
//
// Every draw uses the calling thread's random generator: wrap in RCUtil.seeded for reproducibility.

RCLaws {
	classvar <valueLaws, <stepLaws;

	*initClass {
		valueLaws = #[\uniform, \linear, \linear_hi, \triangular, \gauss, \arcsine, \cauchy, \logistic, \hcos, \exponential, \exponential_hi];
		stepLaws = #[\uniform, \linear, \gauss, \cauchy, \bilateral, \logistic];
	}

	// A gaussian of standard deviation sd (Box-Muller on two uniform draws).
	*gauss { |sd = 1|
		var u1 = 1.0.rand.max(1e-12), u2 = 1.0.rand;
		^sd * (-2 * u1.log).sqrt * (2pi * u2).cos
	}

	// A value in [lo, hi] by `law`, from the uniform draw u (nil: drawn now):
	//   \uniform
	//   \linear         the second law (App. II p. 327): x = a (1 - sqrt(1 - u)), density 2/a (1 - x/a): lo likelier
	//   \linear_hi      its mirror: hi likelier
	//   \triangular     the sum of two uniforms: the middle
	//   \gauss          standard deviation a quarter of the range, clipped
	//   \arcsine        the edges (ch. IX: arcsin)
	//   \cauchy         a narrow centre with far tails, clipped to the range
	//   \logistic, \hcos (hyperbolic cosine: ln tan(π u / 2)), clipped
	//   \exponential    from lo, mean a quarter of the range (the first law, App. I p. 323); \exponential_hi from hi
	*value { |law = \uniform, u, lo = 0, hi = 1|
		var a = hi - lo;
		var x;
		u = u ?? { 1.0.rand };
		x = switch(law,
			\uniform, { u },
			\linear, { 1 - (1 - u).sqrt },
			\linear_hi, { (1 - u).sqrt },
			\triangular, { (u + 1.0.rand) / 2 },
			\gauss, { (0.5 + this.gauss(0.25)).clip(0, 1) },
			\arcsine, { (1 - (pi * u).cos) / 2 },
			\cauchy, { (0.5 + ((pi * (u - 0.5)).tan / 16)).clip(0, 1) },
			\logistic, { (0.5 + ((u.max(1e-9) / (1 - u).max(1e-9)).log / 12)).clip(0, 1) },
			\hcos, { (0.5 + ((pi * u.clip(1e-6, 1 - 1e-6) / 2).tan.log / 8)).clip(0, 1) },
			\exponential, { (-1 * (1 - u).max(1e-12).log / 4).min(1) },
			\exponential_hi, { 1 - ((-1 * (1 - u).max(1e-12).log / 4).min(1)) },
			{ u }
		);
		^lo + (a * x)
	}

	// A signed step of scale `step` (the walks of ch. IX and GENDYN, p. 292): \uniform (±u step),
	// \linear (small steps likelier), \gauss (sd step / 2), \cauchy (mostly small, sometimes a leap,
	// clipped at four steps), \bilateral (Laplace: exponential either side), \logistic.
	*step { |law = \uniform, u, step = 1|
		var sign = if(0.5.coin) { 1 } { -1 };
		u = u ?? { 1.0.rand };
		^switch(law,
			\uniform, { sign * u * step },
			\linear, { sign * (1 - (1 - u).sqrt) * step },
			\gauss, { this.gauss(step / 2) },
			\cauchy, { ((pi * (u - 0.5)).tan * step / 4).clip(-4 * step, 4 * step) },
			\bilateral, { sign * -1 * (1 - u).max(1e-12).log * step / 2 },
			\logistic, { ((u.max(1e-9) / (1 - u).max(1e-9)).log * step / 6).clip(-4 * step, 4 * step) },
			{ sign * u * step }
		)
	}

	// x into [lo, hi] by reflection at the ends (the reflecting barriers of ch. IX method 4,
	// GENDYN's elastic mirrors): the excursion past a barrier comes back from it.
	*reflect { |x, lo, hi|
		var a = lo.min(hi), b = lo.max(hi), w = b - a, n = 0;
		if(w <= 0) { ^a };
		while { ((x < a) or: { x > b }) and: { n < 64 } } {
			if(x < a) { x = a + (a - x) };
			if(x > b) { x = b - (x - b) };
			n = n + 1;
		};
		^x.clip(a, b)
	}

	// A Poisson count of mean lambda (Knuth's method; the normal approximation past 50, as the
	// law becomes normal for large means, p. 66).
	*poisson { |lambda = 1|
		var l, k = 0, p = 1;
		if(lambda <= 0) { ^0 };
		if(lambda > 50) { ^(lambda + this.gauss(lambda.sqrt)).round.asInteger.max(0) };
		l = (-1 * lambda).exp;
		while { p > l } { k = k + 1; p = p * 1.0.rand };
		^k - 1
	}

	// An exponential duration of the given mean: x = -ln(1 - u) / c (the first law, App. I), clipped.
	*exponential { |mean = 1, lo = 0.001, hi = inf|
		^((-1 * (1 - 1.0.rand).max(1e-12).log) * mean).clip(lo, hi)
	}

	// Poisson's table for a mean density over a number of cells: [cells with 0, 1, 2 ... events], the
	// rounding remainder left to the empty cells (Achorripsis p. 29: λ 0.6 over 196 cells gives
	// 107, 65, 19, 4, 1).
	*poissonTable { |lambda = 0.6, cells = 196, max = 6|
		var counts = (max + 1).collect { |k| (cells * (-1 * lambda).exp * (lambda ** k) / k.asInteger.factorial).round.asInteger };
		counts[0] = counts[0] + (cells - counts.sum);
		^counts
	}

	// The entropy in bits of a set of values binned by `bin` (Xenakis' variety: 0 for one value,
	// log2 n for n equally frequent ones; p. 63).
	*entropyBits { |values, bin = 1|
		var counts = IdentityDictionary.new, n = values.size, h = 0;
		if(n == 0) { ^0 };
		values.do { |v| var k = (v / bin).floor.asInteger; counts[k] = (counts[k] ? 0) + 1 };
		counts.do { |c| var p = c / n; h = h - (p * p.log2) };
		^h
	}
}
