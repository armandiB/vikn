// reCurrent — an editable list of durations (numbers and Rests) driving a beat.
//
// The beat streams it as Pn(Plazy { Pseq(durList.array, 1, seqOffset) }), so
// element edits take effect immediately (Pseq reads lazily) and structural
// edits (size changes) at the next loop.

RCDurList {
	var <array;

	*new { |array|
		^super.new.initRCDurList(array)
	}

	initRCDurList { |arrayarg|
		array = arrayarg.asArray;
	}

	size { ^array.size }
	totalDur { ^array.sum(_.value) }
	at { |i| ^array[i] }
	put { |i, val| array[i] = val }
	asArray { ^array }

	// Turn the hit at `pos` (wrapped) into a rest of the same length.
	removeHit { |pos|
		var i;
		if(array.size == 0) { RCLog.warn(\durList, "removeHit on an empty list"); ^this };
		i = pos % array.size;
		if(array[i].isRest) {
			RCLog.warn(\durList, "hit % is already a rest".format(i));
		} {
			array[i] = Rest(array[i]);
		};
	}

	// Insert a hit at `durFromStart` beats from the loop start (wrapped),
	// splitting the hit or rest that contains that point.
	addHit { |durFromStart|
		var total = this.totalDur, wrapped, rolling = 0, hitPos = 0;
		var durToNext, durToPrev, newDurPrev, durNewHit, prev;
		if(array.size == 0 or: { total <= 0 }) {
			RCLog.warn(\durList, "addHit needs a non-empty list with positive total (size %, total %)".format(array.size, total));
			^this
		};
		wrapped = durFromStart % total;
		while { (rolling < wrapped) and: { hitPos < array.size } } {
			rolling = rolling + array[hitPos].value;
			hitPos = hitPos + 1;
		};
		if((rolling % total) == wrapped) {
			// exactly on an existing boundary: un-rest it, or complain
			var i = hitPos % array.size;
			if(array[i].isRest) {
				array[i] = array[i].value;
			} {
				RCLog.warn(\durList, "a hit already exists at % (index %)".format(wrapped, i));
			};
			^this
		};
		// strictly inside element hitPos-1: split it
		durToNext = rolling;
		prev = array[hitPos - 1];
		durToPrev = durToNext - prev.value;
		newDurPrev = wrapped - durToPrev;
		durNewHit = durToNext - wrapped;
		array[hitPos - 1] = if(prev.isRest) { Rest(newDurPrev) } { newDurPrev };
		array = array.insert(hitPos, durNewHit);
	}

	// Truncate or extend the loop to `newTotal` beats (last element stretched,
	// following elements dropped). Fixes the index-shifting removal of
	// BlockBeats' change_beat_dur.
	totalDur_ { |newTotal|
		var rolling = 0, hitPos = 0, last, newLast;
		if(newTotal.isNumber.not or: { newTotal <= 0 }) {
			RCLog.warn(\durList, "totalDur_ ignored: % is not a positive number".format(newTotal));
			^this
		};
		if(array.size == 0) { array = [newTotal]; ^this };
		while { (rolling < newTotal) and: { hitPos < array.size } } {
			rolling = rolling + array[hitPos].value;
			hitPos = hitPos + 1;
		};
		last = array[hitPos - 1];
		newLast = newTotal - (rolling - last.value);
		array[hitPos - 1] = if(last.isRest) { Rest(newLast) } { newLast };
		array = array.copyRange(0, hitPos - 1);
	}

	// Append `copies` copies of the current list to itself.
	duplicate { |copies = 1|
		var c = array.deepCopy;
		copies.do { array = array ++ c.deepCopy };
	}

	printOn { |stream|
		stream << "RCDurList(" << array << ")"
	}
}
