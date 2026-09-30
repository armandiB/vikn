// reCurrent — the projection of R^m on a score space: a moving plane.
//
//   ~p = RCProjection(4, [\pitch, \az]);        // time = x0, pitch = x1, az = x2 (x3 unseen)
//   ~p.scales = [8, 2, pi];                      // beats per unit, octaves per unit, radians per unit
//   ~p.project([0.5, 0.25, 1, 0]);               // → [4, (pitch: 0.5, az: pi)]
//   ~p.rotate(0, 3, 0.25pi);                     // turn the time axis towards x3
//   ~p.spin = [[1, 3, 0.05]]; ~p.advance(8);     // 0.05 rad per beat in the (x1, x3) plane, 8 beats on
//
// The frame is an origin and one unit vector of R^m per row: the first row is the
// time axis, the others the parameter axes named by `keys`. A point projects to
// [time, params] with each coordinate ((x - origin) . axis) * scale. Rotating the
// frame in a coordinate plane of R^m (Givens rotations, the frame stays orthonormal)
// and translating it are what a piece moves between cycles, by hand or by advance
// (spin: rotations per beat, drift: a translation per beat). reset returns to the
// frame as constructed.

RCProjection {
	var <dim, <keys, <origin, <axes, <scales, <>spin, <>drift, <time = 0;
	var origin0, axes0;

	// axes: one Array of dim numbers per row (time first), the canonical vectors by default;
	// scales: one number per row (1 by default).
	*new { |dim = 3, keys = #[\pitch], origin, axes, scales|
		^super.new.initRCProjection(dim, keys, origin, axes, scales)
	}

	initRCProjection { |dimarg, keysarg, originarg, axesarg, scalesarg|
		dim = dimarg;
		keys = keysarg.collect(_.asSymbol);
		origin = (originarg ?? { 0.0 ! dim }).collect(_.asFloat);
		axes = (axesarg ?? { (keys.size + 1).collect { |i| dim.collect { |j| if(i == j) { 1.0 } { 0.0 } } } }).collect { |row| row.collect(_.asFloat) };
		scales = (scalesarg ?? { 1.0 ! (keys.size + 1) }).collect(_.asFloat);
		if(axes.size != (keys.size + 1)) { RCLog.error(\projection, "% axes for % keys: one row per key plus the time row".format(axes.size, keys.size)) };
		if(axes.any { |row| row.size != dim }) { RCLog.error(\projection, "an axis has not % coordinates".format(dim)) };
		this.orthonormalize;
		origin0 = origin.copy;
		axes0 = axes.collect(_.copy);
		spin = [];
		drift = nil;
	}

	//////// projection

	// [time, params] for a point of R^m.
	project { |x|
		var d = x - origin;
		var params = ();
		keys.do { |k, i| params[k] = (d * axes[i + 1]).sum * scales[i + 1] };
		^[(d * axes[0]).sum * scales[0], params]
	}

	timeAxis { ^axes[0] }
	axisFor { |key| ^keys.indexOf(key.asSymbol) !? { |i| axes[i + 1] } }
	scaleFor { |key| ^keys.indexOf(key.asSymbol) !? { |i| scales[i + 1] } }

	scales_ { |array| scales = array.collect(_.asFloat) }

	setScale { |key, value|
		var i = if(key == \time) { 0 } { keys.indexOf(key.asSymbol) !? (_ + 1) };
		if(i.isNil) { RCLog.warn(\projection, "no key %".format(key)); ^this };
		scales[i] = value.asFloat;
	}

	//////// motion

	// Rotate every axis in the (i, j) coordinate plane of R^m by angle (radians).
	rotate { |i, j, angle|
		var c = angle.cos, s = angle.sin;
		if(i == j or: { i >= dim } or: { j >= dim }) { RCLog.warn(\projection, "rotate: no plane (%, %) in R^%".format(i, j, dim)); ^this };
		axes = axes.collect { |row|
			var r = row.copy;
			r[i] = (c * row[i]) - (s * row[j]);
			r[j] = (s * row[i]) + (c * row[j]);
			r
		};
	}

	translate { |vector| origin = origin + vector.collect(_.asFloat) }

	// Move the frame by `beats` of its motion: spin [[i, j, radiansPerBeat], ...], drift (a
	// vector per beat). Returns the frame; time counts the beats advanced since the reset.
	advance { |beats = 1|
		spin.do { |sp| this.rotate(sp[0], sp[1], sp[2] * beats) };
		drift !? { |d| this.translate(d * beats) };
		time = time + beats;
	}

	reset {
		origin = origin0.copy;
		axes = axes0.collect(_.copy);
		time = 0;
	}

	// Gram-Schmidt over the rows (a hand-made frame, or drift of the rotations over many
	// cycles). A row that vanishes is reported and replaced by a canonical vector.
	orthonormalize {
		axes.size.do { |i|
			var r = axes[i];
			var len;
			i.do { |j| r = r - (axes[j] * (r * axes[j]).sum) };
			len = r.squared.sum.sqrt;
			if(len < 1e-9) {
				RCLog.error(\projection, "axis % is dependent on the others, replaced".format(i));
				r = dim.collect { |k| if(k == (i % dim)) { 1.0 } { 0.0 } };
				i.do { |j| r = r - (axes[j] * (r * axes[j]).sum) };
				len = r.squared.sum.sqrt.max(1e-9);
			};
			axes[i] = r / len;
		};
	}

	copy {
		var c = this.class.new(dim, keys, origin, axes, scales);
		c.spin = spin.collect(_.copy);
		c.drift = drift !? (_.copy);
		^c
	}

	printOn { |stream| stream << "RCProjection(R^" << dim << " → time, " << keys << ")" }
}
