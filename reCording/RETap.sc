// reCording — the hook the reCurrent methods call when something is
// recordable:
//   if(RETap.active) { RETap.action(this, \set, [key, val, seed, quant]) }
// one Boolean check when nothing is armed. Armed recorders (REScoreRecorder)
// register here and every call is forwarded to each of them under RCGuard,
// from the main thread only: patterns, control loops and replays run in
// Routines and are the program's own work. Library code that reaches a
// recordable method from another one calls its private twin (RCBeat.prFree,
// RCOrgnsm.prRPut...) so that one action is one event.
//
// Inputs open a cause around their handler,
//   RETap.input(\midi, song, (name: name, raw: total, value: v), { action.value(v, total) })
// the input is recorded and the actions the handler triggers are not: the
// message replays through the handler. A code line (the interpreter hooks
// installed by enableCode) opens a cause too, and the actions it triggers are
// recorded as its effects. RETap.silently { } records nothing inside.

RETap {
	classvar <active = false;
	classvar <recorders;
	classvar <>mainThreadOnly = true;   // false lets tests drive actions from a Routine
	classvar <frames;                   // open causes, innermost last: (kind:, ids: recorder → event id)
	classvar silent = 0;
	classvar codeUsers, preHook, dumpHook, savedPreProcessor;

	*initClass {
		recorders = [];
		frames = [];
		codeUsers = IdentitySet.new;
	}

	*add { |recorder|
		if(recorders.includes(recorder).not) { recorders = recorders.add(recorder) };
		active = recorders.notEmpty;
	}

	*remove { |recorder|
		recorders = recorders.reject { |r| r === recorder };
		active = recorders.notEmpty;
		if(active.not) { frames = [] };
	}

	*isMainThread { ^mainThreadOnly.not or: { thisThread === thisProcess.mainThread } }
	*frame { ^frames.last }

	// An action on a reCurrent object: receiver, method, arguments as called.
	// Not from a Routine, not inside silently, not as the effect of a message.
	*action { |obj, method, args|
		var frame;
		if(silent > 0 or: { this.isMainThread.not }) { ^this };
		frame = frames.last;
		if(frame.notNil and: { frame[\kind] != \code }) { ^this };
		recorders.do { |r| RCGuard.call(\tap, nil) { r.tapAction(obj, method, args, frame) } };
	}

	// An input (\midi, \osc, \keyboard) with its data, recorded; then func, the
	// handler, runs inside the input's cause. Returns what func returns.
	*input { |kind, song, data, func|
		var frame;
		if(silent > 0 or: { this.isMainThread.not }) { ^func.value };
		frame = IdentityDictionary[\kind -> kind, \ids -> IdentityDictionary.new];
		recorders.do { |r| RCGuard.call(\tap, nil) { r.tapInput(kind, song, data, frames.last, frame) } };
		if(func.isNil) { ^nil };
		frames = frames.add(frame);
		^protect { func.value } { frames.pop }
	}

	*silently { |func|
		silent = silent + 1;
		^protect { func.value } { silent = silent - 1 }
	}

	//////// code lines: the interpreter's preProcessor (a line is about to run) and codeDump (it ran)

	*enableCode { |user|
		codeUsers.add(user);
		if(preHook.isNil) { this.prInstallCodeHooks };
	}

	*disableCode { |user|
		codeUsers.remove(user);
		if(codeUsers.isEmpty and: { preHook.notNil }) { this.prRemoveCodeHooks };
	}

	*codeHooksInstalled { ^preHook.notNil }

	*prInstallCodeHooks {
		var interp = thisProcess.interpreter;
		savedPreProcessor = interp.preProcessor;
		preHook = { |code, interpreter|
			RETap.beginCode(code);
			savedPreProcessor !? { |pre| code = pre.value(code, interpreter) };
			code
		};
		dumpHook = { |code, res, func, interpreter| RETap.endCode(func.notNil) };
		interp.preProcessor = preHook;
		interp.codeDump = interp.codeDump.addFunc(dumpHook);
	}

	*prRemoveCodeHooks {
		var interp = thisProcess.interpreter;
		if(interp.preProcessor === preHook) {
			interp.preProcessor = savedPreProcessor;
		} {
			RCLog.warn(\tap, "the interpreter's preProcessor was replaced while code was recorded: left as it is");
		};
		interp.codeDump = interp.codeDump.removeFunc(dumpHook);
		preHook = dumpHook = savedPreProcessor = nil;
		this.prCloseCodeFrames;
	}

	// A stale code frame (a line that raised never reached codeDump) is closed first.
	*beginCode { |text|
		var frame;
		this.prCloseCodeFrames;
		if(this.isMainThread.not) { ^this };
		frame = IdentityDictionary[\kind -> \code, \ids -> IdentityDictionary.new];
		recorders.do { |r| RCGuard.call(\tap, nil) { r.tapCode(text, frames.last, frame) } };
		frames = frames.add(frame);
	}

	// ran: false when the line did not compile; its event is dropped.
	*endCode { |ran = true|
		var frame = frames.last;
		if(frame.isNil or: { frame[\kind] != \code }) { ^this };
		frames.pop;
		if(ran.not) { recorders.do { |r| RCGuard.call(\tap, nil) { r.dropCode(frame) } } };
	}

	*prCloseCodeFrames {
		while { frames.notEmpty and: { frames.last[\kind] == \code } } { frames.pop };
	}
}
