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
			\gauss, { (0.5 + (0.25 * if(u.isNil) { this.gauss(1) } { this.normalQuantile(u) })).clip(0, 1) },   // from u when given (correlated draws)
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

	//////// covariation: correlated draws through a Gaussian copula
	// The laws above are inverse distribution functions of one uniform u each. Several dimensions
	// co-vary when their u's are correlated: draw standard normals z, mix them by the Cholesky
	// factor L of a correlation matrix (z' = L z), map each through the normal CDF to a uniform, and
	// hand those uniforms to the laws. Each dimension keeps its own marginal law; the correlation of
	// the resulting uniforms is (6 / π) asin(ρ / 2) (0.68 for ρ 0.7), ±1 ties them exactly.

	// The standard normal CDF Φ(z) (Abramowitz-Stegun 7.1.26, error under 1.5e-7).
	*normalCdf { |z|
		var x = z.abs / 2.sqrt;   // Φ(z) = (1 + erf(z / √2)) / 2
		var t = 1 / (1 + (0.3275911 * x));
		var erf = 1 - ((((((1.061405429 * t) - 1.453152027) * t + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * (-1 * x * x).exp);
		^if(z >= 0) { 0.5 * (1 + erf) } { 0.5 * (1 - erf) }
	}

	// Its inverse, the normal quantile Φ⁻¹(u) (Acklam's rational approximation, relative error 1.15e-9).
	*normalQuantile { |u|
		var a = #[-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02, 1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00];
		var b = #[-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02, 6.680131188771972e+01, -1.328068155288572e+01];
		var c = #[-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00, -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00];
		var d = #[7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00, 3.754408661907416e+00];
		var pLow = 0.02425, q, r;
		u = u.clip(1e-12, 1 - 1e-12);
		^case
		{ u < pLow } {
			q = (-2 * u.log).sqrt;
			(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1)
		}
		{ u > (1 - pLow) } {
			q = (-2 * (1 - u).log).sqrt;
			-1 * (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1)
		}
		{
			q = u - 0.5;
			r = q * q;
			(((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1)
		}
	}

	// The lower Cholesky factor of a symmetric positive-definite matrix (an Array of rows), nil when
	// the matrix is not positive-definite.
	*cholesky { |matrix|
		var n = matrix.size, l = Array.fill(n, { 0.0 ! n });
		n.do { |i|
			(i + 1).do { |j|
				var sum = matrix[i][j];
				j.do { |k| sum = sum - (l[i][k] * l[j][k]) };
				if(i == j) {
					if(sum <= 1e-12) { ^nil };
					l[i][j] = sum.sqrt;
				} {
					l[i][j] = sum / l[j][j];
				};
			};
		};
		^l
	}

	// A correlation matrix over n dimensions from pairs [i, j, rho] (unit diagonal, symmetric), and
	// its Cholesky factor: (matrix:, chol:). Pairs that do not fit together (no positive-definite
	// matrix has them) are shrunk towards 0 by tenths until one does, with a warning.
	*correlation { |n, pairs|
		var m = Array.fill(n, { |i| Array.fill(n, { |j| if(i == j) { 1.0 } { 0.0 } }) }), chol, shrink = 1.0, tries = 0;
		(pairs ? []).do { |p| var rho = p[2].clip(-1, 1) * 0.9999999; m[p[0]][p[1]] = rho; m[p[1]][p[0]] = rho };   // ±1 kept a hair inside: the factor stays definite
		chol = this.cholesky(m);
		while { chol.isNil and: { tries < 10 } } {
			shrink = shrink * 0.9;
			tries = tries + 1;
			n.do { |i| n.do { |j| if(i != j) { m[i][j] = m[i][j] * 0.9 } } };
			chol = this.cholesky(m);
		};
		if(tries > 0) { RCLog.warn(\laws, "correlation pairs % do not fit together: shrunk by %".format(pairs, shrink.round(0.01))) };
		^(matrix: m, chol: chol ?? { this.cholesky(Array.fill(n, { |i| Array.fill(n, { |j| if(i == j) { 1.0 } { 0.0 } }) })) })
	}

	// n correlated standard normals from a Cholesky factor (n independent gaussians mixed by it).
	*correlatedNormals { |chol|
		var z = chol.size.collect { this.gauss(1) };
		^chol.collect { |row| row.sum { |l, k| l * z[k] } }
	}

	// n correlated uniforms in (0, 1): the normals through Φ, ready for `value` and `step`.
	*correlatedUniforms { |chol|
		^this.correlatedNormals(chol).collect { |z| this.normalCdf(z).clip(1e-9, 1 - 1e-9) }
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
