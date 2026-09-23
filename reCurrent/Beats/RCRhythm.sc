// reCurrent — rhythm arithmetic (pure class methods).
//
// Subsequence tuples are addressed by index here so that both plain arrays
// and RCSubseq instances work: [priority, shift, durs, params, mask, name, keysIgnoreOrder, timeMult].

RCRhythm {

	// Euclidean pattern of o onsets over p pulses, as a 0/1 array.
	*euclid { |o = 1, p = 4, shift = 0|
		var arr;
		if(p.isNil or: { p <= 0 }) { ^[] };
		arr = (o / p * (0..(p - 1))).floor.differentiate.asInteger.min(1);
		arr[0] = if(o <= 0) { 0 } { 1 };
		^arr.rotate(shift)
	}

	// [1, 0, 0, 1, 0] → [3, 2]; leading zeros become a leading Rest.
	*durListFromBinary { |binary|
		var durList = [];
		binary.do { |bit|
			if(bit == 1) {
				durList = durList.add(1);
			} {
				if(durList.size == 0) { durList = durList.add(Rest(0)) };
				durList[durList.size - 1] = durList[durList.size - 1] + 1;
			};
		};
		^durList
	}

	// Same, also collecting aux[i] for every hit → [durList, auxValues].
	*durListFromBinaryWithAux { |binary, aux|
		var durList = [], auxRes = [];
		binary.do { |bit, i|
			if(bit == 1) {
				durList = durList.add(1);
				auxRes = auxRes.add(aux[i]);
			} {
				if(durList.size == 0) {
					durList = durList.add(Rest(0));
					auxRes = auxRes.add(aux[i]);
				};
				durList[durList.size - 1] = durList[durList.size - 1] + 1;
			};
		};
		^[durList, auxRes]
	}

	*euclidDurList { |o = 1, p = 4, shift = 0|
		^this.durListFromBinary(this.euclid(o, p, shift))
	}

	// Cumulative onset times of a subseq: [onsets, indices, lastHitDur].
	// Onsets carry Rest-ness; with loopTime they are wrapped and sorted.
	*cumdurFromSubseq { |subseq, shift, mask, loopTime, timeMult|
		var cumdur = List.new;
		var durSeq = subseq[2];
		var last;
		shift = shift ? subseq[1];
		mask = mask ? subseq[4];
		if(timeMult.notNil) {
			durSeq = durSeq * timeMult;
			loopTime = loopTime !? (_ * timeMult);
			shift = shift * timeMult;
		};
		last = shift;
		if(durSeq.isKindOf(SequenceableCollection).not or: { mask.isKindOf(SequenceableCollection).not }) {
			RCLog.error(\cumdur, "cumdurFromSubseq needs array durations and mask (got % / %)".format(durSeq.class, mask.class));
			^[[], [], nil]
		};
		durSeq.do { |d, i|
			var keep = mask[i];
			if(keep.isNil) {
				RCLog.error(\cumdur, "mask % does not fit durations %".format(mask, durSeq));
				keep = false;
			};
			if(keep) { cumdur.add([if(d.isRest) { Rest(last) } { last }, i]) };
			last = d.value + last;
		};
		if(loopTime.notNil) {
			cumdur = cumdur.collect { |pair| [pair[0] % loopTime, pair[1]] };
			cumdur = cumdur.sort { |a, b| a[0].value < b[0].value };
		};
		if(cumdur.size == 0) { ^[[], [], nil] };
		cumdur = cumdur.flop;
		^[cumdur[0], cumdur[1], durSeq[cumdur[1].last]]
	}

	// Onset times back to durations: [durs, shift].
	*durFromCumdur { |cumdur, lastDur|
		var durs;
		if(cumdur.isKindOf(SequenceableCollection).not) {
			RCLog.error(\cumdur, "durFromCumdur needs an array");
			^[[], 0]
		};
		if(cumdur.size == 0) { ^[[], 0] };
		durs = this.differentiateRestSafe(cumdur).asArray[1..];
		if(lastDur.notNil) { durs = durs ++ [lastDur] };
		^[durs, cumdur[0].value]
	}

	// differentiate keeping the Rest-ness of the previous item
	*differentiateRestSafe { |cumdur|
		var prev = Rest(0);
		^cumdur.collect { |item|
			var res = if(prev.isRest) { Rest(item - prev) } { item.value - prev };
			prev = item;
			res
		}
	}
}
