// reCurrent — an fobject: a spatial effect unit orgnsms can send to
// (BlockOrgnsm's ~make_new_fobject). Port of the template/clone design:
//   an in bus, a Synth in the song's fobject group, a priority, and a
//   transparency function (zpos, pos) → 0..1 that says how much of a sound
//   at that position reaches it (see RCOrgnsm's routing: fobjects are taken by
//   increasing priority until the transparency budget reaches 1).
//
//   ~tpl = RCFObject(~song, \Verb_test, 2, [\room, 0.9], { |zpos, pos| (zpos[0] ? 0).min(1) }, priority: 2);
//   ~tpl.addSynthDef({ |in| FreeVerb.ar(in, \mix.kr(0.5), \room.kr(0.9)) });   // in-graph space transform around it
//   ~verb = ~tpl.create(\verb1);
//   ~verb.setWidth([1, 0.5]);
// SynthDef args have `in` and `out` by convention; additionalSynthArgs are
// appended, then overrideSynthArgs at create().

RCFObject {
	var <song, <synthDefName, <numChansIn, <>additionalSynthArgs, <transparencyFunc, <>priority;
	var <isAmbisonics, <orderAmbisonics, <>isPointSource, <addSongInName;
	var <name, <group, <inBus, <outBus, <synthArgs, <synth, <soundFunction, <centerFromPos = true;
	var <posCenter, <posOrigin, <rotationMatrix, <widthFactors;
	var <isRegistered = false, <isFreed = false;

	*new { |song, synthDefName, numChansIn = 2, additionalSynthArgs, transparencyFunc, priority = 0, isAmbisonics = false, orderAmbisonics, isPointSource = false, addSongInName = false|
		^super.new.initRCFObject(song, synthDefName, numChansIn, additionalSynthArgs, transparencyFunc, priority, isAmbisonics, orderAmbisonics, isPointSource, addSongInName)
	}

	initRCFObject { |songarg, synthDefNamearg, numChansInarg, additionalSynthArgsarg, transparencyFuncarg, priorityarg, isAmbisonicsarg, orderAmbisonicsarg, isPointSourcearg, addSongInNamearg|
		song = songarg;
		synthDefName = synthDefNamearg.asSymbol;
		numChansIn = numChansInarg;
		additionalSynthArgs = additionalSynthArgsarg ? [];
		transparencyFunc = transparencyFuncarg ? { 0 };
		priority = priorityarg;
		isAmbisonics = isAmbisonicsarg;
		orderAmbisonics = orderAmbisonicsarg;
		isPointSource = isPointSourcearg;
		addSongInName = addSongInNamearg;
	}

	isOrgnsm { ^false }
	isFobject { ^true }
	isCrawler { ^false }
	transparencyFunc_ { |f| transparencyFunc = f }

	numChansOut { ^if(isAmbisonics) { (orderAmbisonics + 1).squared } { numChansIn } }

	typicalPowerPerComponent {
		if(isAmbisonics and: { isPointSource }) {
			^orderAmbisonics.collect { |order| var size = 2 * order + 1; size.collect { 1 / size / orderAmbisonics } }.flatten
		};
		^(1 / this.numChansOut) ! this.numChansOut
	}

	// Transparency at a position, guarded: a failing function counts as 0.
	transparency { |zpos, pos|
		var t = RCGuard.call(\transparency, 0) { transparencyFunc.value(zpos, pos) };
		if(t.isNumber.not or: { t.isNaN }) { ^0 };
		^t
	}

	// Speaker weights for a position in [-1, 1]^dims (multichannel only).
	posToSignalWeights { |pos|
		var dims = pos.size;
		var perDim = numChansIn div: dims;
		var overlapScale = 1;
		var overlapFactor = perDim / (2 * overlapScale);
		var factors;
		if(isAmbisonics) {
			RCLog.error(\fobject, "posToSignalWeights is not implemented for ambisonics");
			^1 ! this.numChansOut
		};
		if(numChansIn % dims != 0) { RCLog.error(\fobject, "% channels do not divide % dimensions evenly".format(numChansIn, dims)) };
		factors = pos.collect { |p| perDim.collect { |sp| (pi / 2 * (overlapFactor * (((1 - p) / 2) - (2 * sp / perDim))).max(-1).min(1)).cos } };
		^RCUtil.cartesianProductProducts(factors).reverse
	}

	//////// lifecycle

	clone {
		var c = this.class.new(song, synthDefName, numChansIn, additionalSynthArgs.copy, transparencyFunc, priority, isAmbisonics, orderAmbisonics, isPointSource, addSongInName);
		c.prCopyState(soundFunction, centerFromPos, posCenter, posOrigin, rotationMatrix, widthFactors);
		^c
	}

	prCopyState { |sound, center, pc, po, rm, wf|
		soundFunction = sound;
		centerFromPos = center;
		posCenter = pc;
		posOrigin = po;
		rotationMatrix = rm;
		widthFactors = wf;
	}

	register { |namearg|
		var real = (if(addSongInName) { song.name.asString ++ "_" } { "" } ++ namearg).asSymbol;
		name = song.registry.addFobject(real, this);
		isRegistered = true;
		^name
	}

	unregister {
		if(isRegistered) { song.registry.removeFobject(name, this) };
		isRegistered = false;
	}

	// A registered instance with its bus and synth. Replaces an existing fobject of that name.
	create { |namearg, overrideSynthArgs, groupIdx = 0, outIdx = 0|
		var c = this.clone;
		var server;
		song.registry.fobject(namearg.asSymbol) !? { |old| RCLog.warn(\fobject, "replacing fobject %".format(namearg)); old.free };
		c.prSetGroup(song.fobjectGroupArray[groupIdx]);
		if(c.group.isNil) {
			RCLog.error(\fobject, "no fobject group at index % (song.fobjectGroupArray has %)".format(groupIdx, song.fobjectGroupArray.size));
			^nil
		};
		server = c.group.server;
		if(server.serverRunning.not) { RCLog.error(\fobject, "server not running, cannot create fobject %".format(namearg)); ^nil };
		c.register(namearg);
		c.prSetBuses(Bus.audio(server, numChansIn), song.fobjectOutArray[outIdx] ? 0);
		c.prSetSynthArgs(RCUtil.kvPutAll(RCUtil.kvPutAll([\in, c.inBus, \out, c.outBus], additionalSynthArgs), overrideSynthArgs ? []));
		c.prSetSynth(Synth(synthDefName, c.synthArgs, c.group));
		^c
	}

	prSetGroup { |g| group = g }
	prSetBuses { |in, out| inBus = in; outBus = out }
	prSetSynthArgs { |args| synthArgs = args }
	prSetSynth { |s| synth = s }

	setArg { |key, value| synth !? { |s| s.set(key, value) } }
	setArgArray { |kv| synth !? { |s| s.set(kv[0], kv[1]) } }

	setCenter { |pos|
		this.setArg(\center, this.posToSignalWeights(pos));
		this.setArg(\recompute_space, 1);
		posCenter = pos;
	}

	setOrigin { |pos|
		this.setArg(\origin, this.posToSignalWeights(pos));
		this.setArg(\recompute_space, 1);
		posOrigin = pos;
	}

	setRotationMatrix { |matrix|
		this.setArg(\rotation_matrix, matrix);
		this.setArg(\recompute_space, 1);
		rotationMatrix = matrix;
	}

	setWidth { |factors|
		this.setArg(\width_factors, factors);
		this.setArg(\recompute_space, 1);
		widthFactors = factors;
	}

	free {
		if(isFreed) { ^this };
		isFreed = true;
		this.unregister;
		RCGuard.call(\fobject, nil) { synth !? (_.free) };
		RCGuard.call(\fobject, nil) { inBus !? (_.free) };
		synth = nil;
		inBus = nil;
	}

	delete { ^this.free }

	//////// SynthDef: space transform around the sound function

	// Adds SynthDef(synthDefName): In → pre-matrix → soundFunction(signal) →
	// post-matrix → Sanitize → Out. Matrices are recomputed in-graph on
	// \recompute_space from \origin/\center (or \rotation_matrix) and
	// \width_factors; a singular width matrix yields zeros, never NaN.
	addSynthDef { |soundFunc, centerFromPosarg = true|
		var dims = numChansIn;
		var ambi = isAmbisonics;
		var typicalPower = this.typicalPowerPerComponent;
		var transform, inFunc, outFunc;
		soundFunction = soundFunc;
		centerFromPos = centerFromPosarg;

		transform = {
			var recompute = \recompute_space.tr(1);
			var rotMat, rotMatT, widthFactorsIn, widths, widthMatrix, widthMatrixInverse, powerAdj, invPowerAdj, pre, post;
			if(centerFromPosarg) {
				var origin = Demand.kr(recompute, 0, \origin.kr(1 ! dims));
				var center = Demand.kr(recompute, 0, \center.kr(1 ! dims));
				rotMat = Demand.kr(recompute, 0, { RCMatrix.givens(origin, center) });
			} {
				rotMat = Demand.kr(recompute, 0, \rotation_matrix.kr(RCMatrix.identity(dims)));
			};
			widthFactorsIn = \width_factors.kr(1 ! dims);
			widths = Demand.kr(recompute, 0, widthFactorsIn);
			rotMatT = Demand.kr(recompute, 0, { rotMat.flop });
			widthMatrix = Demand.kr(recompute, 0, { RCMatrix.widthMatrixFromFactors(widths, ambi) });
			widthMatrixInverse = Demand.kr(recompute, 0, {
				if(ambi) { widthMatrix.collect { |row| row.collect { |x| RCMatrix.safeReciprocal(x) } } } { RCMatrix.invertGJ(widthMatrix) }
			});
			powerAdj = Demand.kr(recompute, 0, { if(ambi) { RCMatrix.powerAdjustmentAmbisonics(widths, typicalPower) } { 1 } });
			invPowerAdj = Demand.kr(recompute, 0, { if(ambi) { RCMatrix.inversePowerAdjustmentAmbisonics(widths, typicalPower) } { 1 } });
			pre = Demand.kr(recompute, 0, { invPowerAdj * RCMatrix.product(widthMatrixInverse, rotMatT).flop });
			post = Demand.kr(recompute, 0, { powerAdj * RCMatrix.product(rotMat, widthMatrix).flop });
			[pre, post]
		};
		inFunc = { |preT, in|
			var signal = In.ar(in, dims);
			RCMatrix.product([signal], preT)[0]
		};
		outFunc = { |postT, signal, out|
			signal = RCMatrix.product([signal], postT)[0];
			Out.ar(out, Sanitize.ar(signal));
		};
		^RCGuard.call(\fobject, nil) {
			SynthDef(synthDefName, {
				var matrices = SynthDef.wrap(transform);
				var signal = SynthDef.wrap(inFunc, nil, [matrices[0]]);
				signal = SynthDef.wrap(soundFunc, nil, [signal]);
				SynthDef.wrap(outFunc, nil, [matrices[1], signal]);
			}).add;
			synthDefName
		}
	}

	//////// key paths (like orgnsms)

	convertKey { |key|
		if(key.isKindOf(String)) { ^key.split($.).collect(_.asSymbol) };
		if(key.isKindOf(SequenceableCollection)) { ^key.collect(_.asSymbol) };
		^[key.asSymbol]
	}

	rPut { |key, val|
		var keys = this.convertKey(key);
		if(keys.size == 1) { ^this.perform(keys[0].asSetter, val) };
		RCUtil.rPut(this.perform(keys[0]), keys[1..], val);
	}

	rGet { |key|
		var keys = this.convertKey(key);
		if(keys.size == 1) { ^this.perform(keys[0]) };
		^RCUtil.rGet(this.perform(keys[0]), keys[1..])
	}

	printOn { |stream| stream << "RCFObject(" << (name ? synthDefName) << ")" }
}
