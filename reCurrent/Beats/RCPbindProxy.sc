// reCurrent — the ordered keys of a beat: an RCKeyProxy per key and an
// EventPatternProxy around their Pbind, so that adding or removing a key swaps
// the whole pattern on the proxy's quant, as JITLib's PbindProxy did (with a
// PatternProxy per key, see RCKeyProxy for what that cost). One rebuild per
// edit, finishKey kept last: RCBeat's rc_finish clamps must run after every
// user key.

RCPbindProxy : Pattern {
	var <pairs, <source, <finishKey, proxies;

	*new { |pairs, finishKey = \rc_finish, clock, quant|
		^super.new.initRCPbindProxy(pairs, finishKey, clock, quant)
	}

	initRCPbindProxy { |pairsarg, finishKeyarg, clock, quant|
		pairs = pairsarg.asArray;
		finishKey = finishKeyarg;
		proxies = IdentityDictionary.new;
		pairs.pairsDo { |key, proxy| proxies[key] = proxy };
		source = EventPatternProxy(Pbind(*pairs));
		clock !? { source.clock = clock };
		source.quant = quant;
	}

	at { |key| ^proxies[key] }
	includesKey { |key| ^proxies[key].notNil }
	keys { ^RCUtil.kvKeys(pairs) }

	find { |key|
		pairs.pairsDo { |k, proxy, i| if(k == key) { ^i } };
		^nil
	}

	quant { ^source.quant }
	quant_ { |q| source.quant = q }
	clock_ { |c| source.clock = c }

	embedInStream { |inval| ^source.embedInStream(inval) }

	// Add a key (or replace its proxy); the pattern is rebuilt once and swapped
	// on `quant` (nil: at the next event).
	add { |key, proxy, quant|
		this.prPutPair(key, proxy);
		this.prRebuild(quant);
	}

	addAll { |kvPairs, quant|
		kvPairs.pairsDo { |key, proxy| this.prPutPair(key, proxy) };
		this.prRebuild(quant);
	}

	remove { |key, quant|
		if(proxies[key].isNil) { ^this };
		this.prRemovePair(key);
		proxies.removeAt(key);
		this.prRebuild(quant);
	}

	// key → last value of every recorded key; key → thread of every key that has one.
	lastValues {
		var res = IdentityDictionary.new;
		pairs.pairsDo { |key, proxy| proxy.lastValue !? { |v| res[key] = v } };
		^res
	}

	threads {
		var res = IdentityDictionary.new;
		pairs.pairsDo { |key, proxy| proxy.thread !? { |t| res[key] = t } };
		^res
	}

	prPutPair { |key, proxy|
		this.prRemovePair(key);
		pairs = pairs ++ [key, proxy];
		proxies[key] = proxy;
	}

	prRemovePair { |key|
		var i = this.find(key);
		if(i.notNil) {
			pairs = pairs.copy;
			pairs.removeAt(i);
			pairs.removeAt(i);
		};
	}

	prRebuild { |quant|
		var i = this.find(finishKey), finish;
		if(i.notNil and: { i != (pairs.size - 2) }) {
			pairs = pairs.copy;
			finish = pairs[i + 1];
			pairs.removeAt(i);
			pairs.removeAt(i);
			pairs = pairs ++ [finishKey, finish];
		};
		source.quant = quant;
		source.source = Pbind(*pairs);
	}

	storeArgs { ^[pairs, finishKey] }
	printOn { |stream| stream << "RCPbindProxy(" << this.keys << ")" }
}
