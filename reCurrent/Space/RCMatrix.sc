// reCurrent — small dense linear algebra on nested Arrays (BlockOrgnsm's
// ~make_identity ... ~invert_sym_matrix_LDL and the width/power helpers).
//
// Written without conditionals on values so that the same code runs on
// numbers (tests) and on control-rate UGens inside a SynthDef (the fobject
// space transform computes its matrices in-graph under Demand.kr). Divisions
// go through safeReciprocal: x / max(x², eps²), which is 1/x away from zero
// and 0 at zero — a singular pivot yields zeros, never NaN or inf.

RCMatrix {
	classvar <>eps = 1e-8;

	*identity { |size| ^size.collect { |i| size.collect { |j| if(i == j) { 1.0 } { 0.0 } } } }
	*allOnes { |size| ^size.collect { size.collect { 1.0 } } }
	*diagonal { |values| ^values.size.collect { |i| values.size.collect { |j| if(i == j) { values[i] } { 0.0 } } } }

	*safeReciprocal { |x| ^x / (x * x).max(eps * eps) }
	*safeDiv { |a, b| ^a * this.safeReciprocal(b) }

	*frobeniusNorm { |matrix|
		^matrix.sum { |row| if(row.isKindOf(Collection)) { row.sum { |x| x * x } } { row * row } }.sqrt
	}

	*product { |a, b|
		var bT = b.flop;
		^a.collect { |row| bT.collect { |col| (row * col).sum } }
	}

	*transpose { |a| ^a.flop }

	// Rotation R with R.vector ∝ origin (reflection form).
	*householder { |vector, origin|
		var v = vector.flop.flop, o = origin.flop.flop;
		var oNormed = o * this.safeReciprocal(this.frobeniusNorm(o));
		var w = v - (this.frobeniusNorm(v) * oNormed);
		var wNormed = w * this.safeReciprocal(this.frobeniusNorm(w));
		^this.identity(vector.size) - (2 * this.product(wNormed, wNormed.flop))
	}

	// Rotation R with R.vector ∝ origin, acting only in the plane they span.
	*givens { |vector, origin|
		var v = vector.flop.flop, o = origin.flop.flop;
		var oNormed = o * this.safeReciprocal(this.frobeniusNorm(o));
		var vNormed = v * this.safeReciprocal(this.frobeniusNorm(v));
		var cosTheta = (oNormed * vNormed).flat.sum;
		var sinTheta = (1 - (cosTheta * cosTheta)).max(0).sqrt;
		var rPlane = [[cosTheta, sinTheta.neg], [sinTheta, cosTheta]];
		var w = oNormed - (cosTheta * vNormed);
		var wNormed = w * this.safeReciprocal(this.frobeniusNorm(w));
		var plane = [vNormed.flat, wNormed.flat];
		^this.identity(vector.size) + this.product(plane.flop, this.product(rPlane - this.identity(2), plane))
	}

	// Gauss–Jordan inverse (no pivoting; singular pivots give zero rows).
	*invertGJ { |matrix|
		var n = matrix.size;
		var aug = matrix.collect { |row, i| row.copy ++ this.identity(n)[i] };
		n.do { |i|
			var inv = this.safeReciprocal(aug[i][i]);
			aug[i] = aug[i].collect { |x| x * inv };
			n.do { |j|
				if(j != i) {
					var factor = aug[j][i];
					aug[j] = aug[j] - (aug[i] * factor);
				};
			};
		};
		^aug.collect { |row| row.drop(n) }
	}

	// LU inverse with partial pivoting (numbers only: pivoting needs comparisons).
	*invertLU { |matrix|
		var n = matrix.size;
		var l = this.identity(n);
		var u = matrix.collect(_.copy);
		var p = (0..(n - 1));
		var inverse = n.collect { 0.0 ! n };
		n.do { |i|
			var maxVal = -1, maxRow = i;
			(i..(n - 1)).do { |k| var val = u[k][i].abs; if(val > maxVal) { maxVal = val; maxRow = k } };
			if(maxRow != i) { u.swap(i, maxRow); p.swap(i, maxRow); l.swap(i, maxRow) };
			if(i < (n - 1)) {
				((i + 1)..(n - 1)).do { |k|
					var factor = this.safeDiv(u[k][i], u[i][i]);
					l[k][i] = factor;
					((i + 1)..(n - 1)).do { |j| u[k][j] = u[k][j] - (factor * u[i][j]) };
					u[k][i] = 0;
				};
			};
		};
		n.do { |col|
			var e = 0.0 ! n, y = 0.0 ! n, x = 0.0 ! n;
			e[p[col]] = 1;
			n.do { |i| y[i] = e[i] - (if(i > 0) { (0..(i - 1)).sum { |j| l[i][j] * y[j] } } { 0 }) };
			((n - 1)..0).do { |i|
				var s = if(i < (n - 1)) { ((i + 1)..(n - 1)).sum { |j| u[i][j] * x[j] } } { 0 };
				x[i] = this.safeDiv(y[i] - s, u[i][i]);
			};
			n.do { |row| inverse[row][col] = x[row] };
		};
		^inverse
	}

	// Inverse of a symmetric positive-definite matrix by Cholesky (non-positive pivots → 0).
	*invertCholesky { |a|
		var n = a.size;
		var l = n.collect { 0.0 ! n };
		var linv = n.collect { 0.0 ! n };
		var inv = n.collect { 0.0 ! n };
		if(n == 1) { ^[[this.safeReciprocal(a[0][0])]] };
		n.do { |i|
			(0..i).do { |j|
				var sum = a[i][j];
				if(j > 0) { (0..(j - 1)).do { |k| sum = sum - (l[i][k] * l[j][k]) } };
				if(i == j) {
					l[i][j] = sum.max(0).sqrt;
				} {
					l[i][j] = this.safeDiv(sum, l[j][j]);
				};
			};
		};
		n.do { |i| linv[i][i] = this.safeReciprocal(l[i][i]) };
		(1..(n - 1)).do { |i|
			(0..(i - 1)).do { |j|
				var sum = 0;
				(j..(i - 1)).do { |k| sum = sum - (l[i][k] * linv[k][j]) };
				linv[i][j] = this.safeDiv(sum, l[i][i]);
			};
		};
		n.do { |i| n.do { |j| var sum = 0; n.do { |k| sum = sum + (linv[k][i] * linv[k][j]) }; inv[i][j] = sum } };
		^inv
	}

	// Inverse of a symmetric matrix by LDLᵀ (near-zero pivots → 0).
	*invertLDL { |a|
		var n = a.size;
		var l = n.collect { 0.0 ! n };
		var d = 0.0 ! n;
		var linv = n.collect { 0.0 ! n };
		var inv = n.collect { 0.0 ! n };
		var dinv;
		n.do { |i|
			var sum = a[i][i];
			if(i > 0) { (0..(i - 1)).do { |k| sum = sum - (l[i][k] * l[i][k] * d[k]) } };
			d[i] = sum;
			if(i < (n - 1)) {
				((i + 1)..(n - 1)).do { |j|
					var sum2 = a[j][i];
					if(i > 0) { (0..(i - 1)).do { |k| sum2 = sum2 - (l[j][k] * l[i][k] * d[k]) } };
					l[j][i] = this.safeDiv(sum2, d[i]);
				};
			};
		};
		n.do { |i| l[i][i] = 1 };
		n.do { |j| n.do { |i|
			var sum = if(i == j) { 1 } { 0 };
			if(i > 0) { (0..(i - 1)).do { |k| sum = sum - (l[i][k] * linv[k][j]) } };
			linv[i][j] = sum;
		} };
		dinv = d.collect { |v| this.safeReciprocal(v) };
		n.do { |i| n.do { |j| var sum = 0; n.do { |k| sum = sum + (linv[k][i] * dinv[k] * linv[k][j]) }; inv[i][j] = sum } };
		^inv
	}

	//////// fobject helpers

	// Ambisonics: diagonal of the factors. Multichannel: Wd + (I - Wd) * (1/n) * ones.
	*widthMatrixFromFactors { |widthFactors, isAmbisonics = false|
		var size = widthFactors.size, nonDiag;
		if(isAmbisonics) { ^this.diagonal(widthFactors) };
		nonDiag = widthFactors.collect { |w| (1 - w) / size };
		^size.collect { |r| size.collect { |c| if(r == c) { nonDiag[r] + widthFactors[r] } { nonDiag[r] } } }
	}

	*powerAdjustmentAmbisonics { |widthFactors, typicalPowerPerComponent|
		var total = typicalPowerPerComponent.sum;
		^(total * this.safeReciprocal(widthFactors.sum { |w, i| typicalPowerPerComponent[i] * w * w })).max(0).sqrt
	}

	*inversePowerAdjustmentAmbisonics { |widthFactors, typicalPowerPerComponent|
		var total = typicalPowerPerComponent.sum;
		^(total * this.safeReciprocal(widthFactors.sum { |w, i| typicalPowerPerComponent[i] * this.safeReciprocal(w) * this.safeReciprocal(w) })).max(0).sqrt
	}
}
