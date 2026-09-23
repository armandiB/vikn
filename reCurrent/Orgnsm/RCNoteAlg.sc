// reCurrent — note algorithms for orgnsms (BlockOrgnsm's ~orgnsm_notealg_lib
// and the pieces' ~apply_scale_force_field).
//
// Each *firefly* method returns the walk function an orgnsm stores under
// staticAttrs.notealg (wrapped in an extra {} so the Event does not call it):
//   notealg: { RCNoteAlg.fireflyNote1d }
// The functions read their parameters from the orgnsm's staticAttrs
// (stattrs) at every call; function-valued parameters may be written
// { |args| } or, as in the proto-library, {{ |args| }}. All searches are
// bounded; any failure returns the previous value.

RCNoteAlg {
	classvar <>maxWalk = 4096;   // iteration cap of the 1-D scale walk

	// 1-D raga walk over scale degrees (aroh going up, avaroh going down).
	// stattrs: scale (size = degrees per octave), aroh, avaroh, octaves (or nil),
	// notealg_maxrandomjump, notealg_maxothersjump, notealg_startnotefunc.
	*fireflyNote1d {
		^{ |prev, othersNotes, origin, stattrs|
			var ppo = stattrs.scale.size;
			var aroh = stattrs.aroh, avaroh = stattrs.avaroh;
			var allowedOctaves = stattrs.octaves;
			var maxRandomJump = stattrs.notealg_maxrandomjump ? 0;
			var maxOthersJump = stattrs.notealg_maxothersjump;
			if(prev.isNil) {
				RCUtil.attrFunc(stattrs, \notealg_startnotefunc).value(ppo, aroh, avaroh)
			} {
				switch(origin,
					\same, { prev },
					\random, {
						var dir = 2.rand * 2 - 1;
						var scale = if(dir > 0) { aroh } { avaroh }.copy.sort;
						var prevOctave = prev.div(ppo);
						var prevScaleIndex, possible = List.new, posScale, octave, newNote;
						if(scale.size == 0 or: { ppo == 0 }) {
							prev
						} {
							prevScaleIndex = scale.detectIndex { |x| (prev % ppo) <= x } ?? {
								if(dir > 0) { prevOctave = prevOctave + 1; 0 } { scale.size - 1 }
							};
							posScale = prevScaleIndex;
							octave = prevOctave;
							newNote = (octave * ppo) + scale[posScale];
							RCGuard.boundedLoop(maxWalk, \fireflyNote1d, { (prev - newNote).abs <= maxRandomJump }, {
								if(allowedOctaves.isNil or: { allowedOctaves.includes(octave) }) { possible.add(newNote) };
								posScale = posScale + dir;
								if(posScale == -1) { posScale = scale.size - 1; octave = octave - 1 };
								if(posScale == scale.size) { posScale = 0; octave = octave + 1 };
								newNote = (octave * ppo) + scale[posScale];
							});
							if(possible.size == 0) { prev } { possible.choose }
						}
					},
					\others, {
						var possible = (othersNotes ? []).select { |note|
							if(note == prev) { false } {
								var scale = if(note > prev) { aroh } { avaroh };
								scale.includes(note % ppo)
								and: { allowedOctaves.isNil or: { allowedOctaves.includes(note.div(ppo)) } }
								and: { maxOthersJump.isNil or: { (prev - note).abs <= maxOthersJump } }
							}
						};
						possible.choose ? prev
					},
					{ prev }
				)
			}
		}
	}

	// Flocking walk on a discrete tonnetz. prev = [tpos, freqMult]; others =
	// [[tpos, freqMult, weight], ...]; forceField.(prevTpos, move, proba, mask) → proba.
	// stattrs: tonnetz_mult_factors, tonnetz_mult_factors_mask, visibility_freq_ratio,
	// max_velocity, repuls_radius, repuls_amount_func, repuls_proba_func, velocity_proba_func,
	// temperature, temperature_weight, notealg_start_tpos_func, center_of_mass (when
	// centerOfMassFromStaticAttrs).
	*fireflyTonnetz { |centerOfMassFromStaticAttrs = false|
		^{ |prev, others, forceField, stattrs|
			var tmf = stattrs.tonnetz_mult_factors;
			var resTpos;
			if(prev.isNil) {
				resTpos = RCUtil.attrFunc(stattrs, \notealg_start_tpos_func).value;
			} {
				var prevTpos = prev[0], prevFreqm = prev[1];
				var mask = stattrs.tonnetz_mult_factors_mask;
				var vizRatio = stattrs.visibility_freq_ratio ? 2;
				var maxVel = stattrs.max_velocity ? 1;
				var repulsRadius = stattrs.repuls_radius;
				var repulsAmountFunc = RCUtil.attrFunc(stattrs, \repuls_amount_func) ? { 0 };
				var repulsProbaFunc = RCUtil.attrFunc(stattrs, \repuls_proba_func) ? { |minOthers, amount, proba| proba };
				var velocityProbaFunc = RCUtil.attrFunc(stattrs, \velocity_proba_func) ? { 1 };
				var temp = stattrs.temperature ? 0;
				var dirMove, centerOfMass, ranges;
				var seen = List.new, results = List.new, probas = List.new, repulsAmounts = List.new;
				var minOthers, sum;

				if(centerOfMassFromStaticAttrs) {
					(others ? []).do { |o|
						var ratio = o[1] / prevFreqm;
						if((ratio <= vizRatio) and: { vizRatio.reciprocal <= ratio }) { seen.add(o[0]) };
					};
					centerOfMass = stattrs.center_of_mass;
					dirMove = if(centerOfMass.notNil) { ((centerOfMass - prevTpos) * mask).ceil.asInteger } { 0 ! tmf.size };
				} {
					var count = 0;
					centerOfMass = 0 ! tmf.size;
					(others ? []).do { |o|
						var ratio = o[1] / prevFreqm;
						if((ratio <= vizRatio) and: { vizRatio.reciprocal <= ratio }) {
							var weight = o[2] ? 1;
							centerOfMass = centerOfMass + (o[0] * weight);
							count = count + weight;
							seen.add(o[0]);
						};
					};
					dirMove = if(count > 0) { centerOfMass = centerOfMass / count; ((centerOfMass - prevTpos) * mask).ceil.asInteger } { 0 ! tmf.size };
				};

				// candidate moves: the L1 box between 0 and dirMove, bounded by maxVel
				ranges = dirMove.collect { |xi| if(xi >= 0) { (0..xi) } { (xi..0) } };
				RCUtil.cartesianProduct(ranges).do { |move|
					var dist = move.abs.sum;
					if(dist <= maxVel) {
						var proba = RCGuard.call(\fireflyTonnetz, 0) { forceField.value(prevTpos, move, velocityProbaFunc.value(dist, maxVel), mask) };
						if(proba.isNumber and: { proba > 0 }) {
							var newTpos = move + prevTpos;
							results.add(newTpos);
							probas.add(proba);
							repulsAmounts.add(seen.sum { |otherTpos| repulsAmountFunc.value(repulsRadius, otherTpos, newTpos) });
						};
					};
				};
				// temperature: random moves in the masked dimensions
				if(temp > 0) {
					var tempWeight = stattrs.temperature_weight ? 1;
					var tempMask = mask.collect { |v| if(v != 0) { 1 } { 0 } };
					RCUtil.l1Vectors(tempMask.sum, temp.ceil.asInteger + 1).do { |moveMasked|
						var idx = -1;
						var move = tempMask.collect { |b| if(b == 1) { idx = idx + 1; moveMasked[idx] } { 0 } };
						var dist = move.abs.sum;
						var proba = RCGuard.call(\fireflyTonnetz, 0) { forceField.value(prevTpos, move, velocityProbaFunc.value(dist, maxVel), mask) * tempWeight };
						if(proba.isNumber and: { proba > 0 }) {
							var newTpos = move + prevTpos;
							results.add(newTpos);
							probas.add(proba);
							repulsAmounts.add(seen.sum { |otherTpos| repulsAmountFunc.value(repulsRadius, otherTpos, newTpos) });
						};
					};
				};

				if(results.size == 0) {
					resTpos = prevTpos;
				} {
					minOthers = repulsAmounts.minItem;
					probas = probas.collect { |proba, i| repulsProbaFunc.value(minOthers, repulsAmounts[i], proba) };
					probas = RCGuard.finite(probas.asArray, 0, \fireflyTonnetz);
					sum = probas.sum;
					if(sum <= 0) {
						resTpos = prevTpos;
					} {
						resTpos = (results.asArray.wchoose(probas / sum) ? prevTpos).asInteger;
					};
				};
			};
			[resTpos, (tmf ** resTpos).product]
		}
	}

	//////// scale force fields

	// Tonnetz coordinates of ratios: exponents of the odd primes of the mult
	// factors, and the octave exponent in slot 0. Floats are turned into
	// fractions with asFraction(maxDenominator).
	*tonnetzPositions { |ratios, tonnetzMultFactors, maxDenominator = 65536|
		var fractions = tonnetzMultFactors.collect { |x| x.asFraction(maxDenominator) };
		var octave = fractions[0][0].div(fractions[0][1]);
		var primes = fractions.collect { |f|
			var num = f[0].factors.reject { |n| n == octave };
			var den = f[1].factors.reject { |n| n == octave };
			(if(num.size == 0) { 1 } { num.product }) div: (if(den.size == 0) { 1 } { den.product })
		};
		^ratios.collect { |ratio|
			var f = ratio.asFraction(maxDenominator);
			var numFactors = f[0].factors, denFactors = f[1].factors;
			var exps = primes.collect { |p| numFactors.count { |x| x == p } - denFactors.count { |x| x == p } };
			var remainder = ratio / (tonnetzMultFactors ** exps).product;
			exps[0] = (remainder.log / octave.log).round.asInteger;
			exps
		}
	}

	// The force field the pieces apply during a section: pulls the flock
	// towards targetPos, penalises moves along dimension 4 and moves away
	// from the scale's tonnetz positions (scaleForceFactor).
	*scaleForceField { |targetPos, scaleForceFactor, scale, tuningRatios, tonnetzMultFactors, maxDenominator = 65536|
		var tposScale = this.tonnetzPositions(tuningRatios[scale], tonnetzMultFactors, maxDenominator);
		^{ |prevTpos, move, proba, mask|
			var moveNorm = (move * mask).abs.sum;
			var dirTerm = if(moveNorm == 0) { 0 } { (move * (prevTpos - targetPos)).sign.sum / moveNorm };
			var scaleDist = tposScale.collect { |tp| ((prevTpos + move - tp) * mask)[1..].abs.sum }.minItem;
			proba / ((move[4] ? 0).abs + 1) / (dirTerm + 1.5).pow(2) / (scaleDist + 1).pow(scaleForceFactor)
		}
	}

	// batch.editAttr("force_field", { field }) with the field above.
	*applyScaleForceField { |batch, targetPos, scaleForceFactor, scale, tuningRatios, tonnetzMultFactors|
		var field = this.scaleForceField(targetPos, scaleForceFactor, scale, tuningRatios, tonnetzMultFactors);
		batch.editAttr("force_field", { field });
		^field
	}
}
