// reCurrent — Markov chains (Xenakis, Formalized Music ch. II-III): matrices of transition
// probabilities, their stationary distribution and entropy, a matrix estimated from a protocol,
// and the coupled mechanism of Analogique A et B.
//
// A matrix is an Array of rows, row i the probabilities of the states entered from state i (rows
// sum to 1). Xenakis writes his matrices by columns: his (β), X→X 0.85 / X→Y 0.15, Y→X 0.4 / Y→Y
// 0.6 (p. 82), is [[0.85, 0.15], [0.4, 0.6]] here.
//
//   RCMarkov.next([[0.85, 0.15], [0.4, 0.6]], 0);                 // the next state from 0
//   RCMarkov.stationary([[0.85, 0.15], [0.4, 0.6]]);              // [0.727, 0.273] (p. 85)
//   RCMarkov.entropy([[0.85, 0.15], [0.4, 0.6]]);                 // 0.708 bits (p. 86: "0.707")
//   RCMarkov.fromProtocol("ABABBBABAAB...", [\A, \B]);            // the matrix of a protocol's transitions (p. 73-74)
//   RCMarkov.coupledStep(vars);                                   // one step of a mechanism (p. 83)
//
// A mechanism's variables are Events: (key: \f, state: 0, mtps: (alpha: m1, beta: m2),
// couplings: (g: [\beta, \alpha], d: [\alpha, \beta])): the state of variable g proposes, by its
// index in the table, the matrix f draws its next state from; with no couplings, `mtp` names the
// matrix, else the only one (the first by name). All variables step from the previous states at once.

RCMarkov {

	// The next state from `state` by the matrix (rows normalised as drawn).
	*next { |matrix, state = 0|
		var row = matrix[state.clip(0, matrix.size - 1)];
		^(0..(row.size - 1)).wchoose(row.normalizeSum)
	}

	// The stationary distribution, by iteration from the uniform one (p. 85-86: the fixed probability vector).
	*stationary { |matrix, iterations = 200|
		var n = matrix.size, v = (1 / n) ! n;
		iterations.do { v = n.collect { |j| v.collect { |x, i| x * matrix[i][j] }.sum } };
		^v
	}

	// The entropy in bits of one row of probabilities.
	*rowEntropy { |row|
		^row.sum { |p| if(p > 0) { -1 * p * p.log2 } { 0 } }
	}

	// The mean entropy of the chain in bits: each row's entropy weighted by the stationary
	// probability of its state (p. 86: (β) 0.707, (α) 0.722).
	*entropy { |matrix|
		var stationary = this.stationary(matrix);
		^matrix.sum { |row, i| stationary[i] * this.rowEntropy(row) }
	}

	// Every row sums to one (within tol), every entry non-negative.
	*isStochastic { |matrix, tol = 1e-6|
		^matrix.every { |row| row.every { |p| p >= 0 } and: { (row.sum - 1).abs < tol } }
	}

	*normalize { |matrix|
		^matrix.collect { |row| if(row.sum > 0) { row / row.sum } { (1 / row.size) ! row.size } }
	}

	*uniform { |n = 2| ^Array.fill(n, { (1 / n) ! n }) }

	// The matrix a protocol implies (p. 73-74: the 50 transitions of "ABABBBABAAB..." give A → B
	// 17 of 23, B → A 17 of 27): protocol a String (one character per state) or an Array of
	// Symbols, states their names in matrix order (the sorted distinct ones when nil). Returns
	// (states, mtp, counts): the names, the row-stochastic matrix (a state never left gets a uniform
	// row) and the transition counts.
	*fromProtocol { |protocol, states|
		var symbols = if(protocol.isKindOf(String)) { protocol.collectAs({ |c| c.asSymbol }, Array) } { protocol.asArray.collect(_.asSymbol) };
		var counts, n;
		states = (states ?? { symbols.asSet.asArray.sort { |a, b| a.asString <= b.asString } }).collect(_.asSymbol);
		n = states.size;
		counts = Array.fill(n, { 0 ! n });
		symbols.doAdjacentPairs { |a, b|
			var i = states.indexOf(a), j = states.indexOf(b);
			if(i.notNil and: { j.notNil }) { counts[i][j] = counts[i][j] + 1 };
		};
		^(states: states, mtp: this.normalize(counts), counts: counts)
	}

	// One step of a coupled mechanism (Analogique A et B, p. 83-84: "f0 standing before two urns
	// (α) and (β)... with a probability equal to 1/2"). For each variable the other variables'
	// states propose matrices through its couplings; one proposal is chosen at random, and the
	// next state is drawn from that matrix's row of the current state. Every variable steps from
	// the previous states. Writes state and mtp_used (the name of the matrix drawn from) into each
	// variable; returns the names used, in the variables' order. A variable whose matrix cannot be
	// found keeps its state (reported).
	*coupledStep { |vars|
		var states = vars.collect { |v| v[\state] ? 0 };
		^vars.collect { |v, vi|
			var mtps = v[\mtps] ? (), couplings = v[\couplings], proposals = List.new, chosen, matrix;
			couplings !? { couplings.keysValuesDo { |otherKey, table|
				var oi = vars.detectIndex { |w| w[\key] == otherKey };
				if(oi.notNil) { table.wrapAt(states[oi]) !? { |m| proposals.add(m) } };
			} };
			chosen = if(proposals.size > 0) { proposals.choose } { v[\mtp] ?? { mtps.keys.asArray.sort { |a, b| a.asString <= b.asString }.first } };
			matrix = if(chosen.isKindOf(Symbol)) { mtps[chosen] } { chosen };
			if(matrix.isNil) {
				RCLog.error(\markov, "coupledStep: variable % has no matrix %".format(v[\key], chosen));
			} {
				v[\state] = this.next(matrix, states[vi]);
				v[\mtp_used] = chosen;
			};
			chosen
		}
	}
}
