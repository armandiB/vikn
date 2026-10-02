// reCording — the transport and the editing of a folder of takes for a page (the remote rig's
// lanes view, HomewareSC's view.html, served on that folder): plain OSCdefs on <prefix>/rec/...
// (never recorded), a working copy next to a take being edited, and the feed back to the page's
// server (/rigfeed/state, /rigfeed/edit, /rigfeed/event).
//
//   ~ed = REScoreEditor(~song, ~rec, ~piece_dir +/+ "Scores");   // a piece: the prefix is /<song name>,
//                                                                // the page is scripts/take.sh view <folder>
//   ~ed.free;
// The remote rig (scripts/sc/rig.scd) makes one with the prefix /rig and its own feed function.
//
// Commands, one OSCdef each (any port the process listens on):
//   <prefix>/rec/play <take|last>        the take replays on the song (as edited while it is being
//                                        edited); onPlayer receives the player
//   <prefix>/rec/stopPlay
//   <prefix>/rec/edit <take> <op json>   an operation (REScore.edited) applied to a copy kept here
//                                        and written as <take>.edit.json (the page reads it)
//   <prefix>/rec/undo <take>             the last operation taken back (the file's score with the
//                                        others applied again)
//   <prefix>/rec/save <take> [new]       the copy written over the take, or as a new file
//                                        (<stamp>_<song> <version> edit.json); the copy dropped
//   <prefix>/rec/revert <take>           the copy dropped
// A take is named by its file name (under root) or its path; "last" is the recorder's last take.
// The feed: feed.(kind, dict) when a function is given, else /rigfeed/<kind> <json> to feedPort,
// with the state four times a second (recorder, beat, recBeat, position, playing, takes, lastTake,
// editing), edit after each operation, event after each recorded event (the recorder's onEvent,
// chained and put back by free).

REScoreEditor {
	var <song, <recorder, <root, <prefix, <version, feedFunc, <feedPort;
	var defs, <player, <playingPath, <editing, feedRoutine, feedAddr, savedOnEvent, chained = false;
	var <>onPlayer;   // { |player| } when /rec/play made one (the rig wires its own feed and the visuals)
	var <>tag = \editor;

	*new { |song, recorder, root, prefix, version, feed, feedPort = 32347|
		^super.new.initREScoreEditor(song, recorder, root, prefix, version, feed, feedPort)
	}

	initREScoreEditor { |songarg, rec, rootarg, prefixarg, versionarg, feedarg, portarg|
		song = songarg;
		recorder = rec;
		root = (rootarg ?? { rec !? (_.root) }).asString;   // the recorder's folder when none is given
		prefix = (prefixarg ?? { "/" ++ song.name }).asString;
		version = (versionarg ?? { rec !? (_.version) } ? "w0").asString;
		feedFunc = feedarg;
		feedPort = portarg;
		if(feedFunc.isNil) { feedAddr = NetAddr("127.0.0.1", feedPort) };
		this.prInstall;
		if(feedFunc.isNil) { this.prStartFeed };
	}

	//////// the takes

	takes { ^(root +/+ "*.json").pathMatch.reject { |p| p.endsWith(".l2.json") or: { p.endsWith(".l3.json") } or: { p.endsWith(".edit.json") } }.sort }

	resolveTake { |which|
		var path = which.asString;
		if(path == "last" or: { path.isEmpty }) { path = recorder !? (_.lastPath) ?? { this.takes.last } };
		if(path.notNil and: { path.beginsWith("/").not }) { path = root +/+ path };
		if(path.isNil or: { File.exists(path).not }) { RCLog.warn(tag, "no take %".format(which)); ^nil };
		^path
	}

	*workingPath { |path| ^path.asString.drop(-5) ++ ".edit.json" }

	//////// the transport

	play { |which = "last", quant|
		var path = this.resolveTake(which), s;
		if(path.isNil) { ^nil };
		s = if(editing.notNil and: { editing[\path] == path }) { editing[\score] } { REScore.read(path) };
		if(s.isNil) { ^nil };
		player !? (_.stop);
		player = REScorePlayer(s, song);
		playingPath = path;
		onPlayer.value(player);
		player.play(quant ? [1, 0]);
		^player
	}

	stopPlay { player !? (_.stop) }

	//////// editing

	edit { |which, op|
		var path = this.resolveTake(which);
		if(path.isNil or: { op.isNil }) { RCLog.warn(tag, "edit: no take or no operation"); ^nil };
		this.prLoad(path) !? { |ed|
			ed[\score] = ed[\score].edited(op);
			ed[\ops].add(op);
			this.prWrite;
		};
		^editing
	}

	undo { |which|
		var path = this.resolveTake(which);
		editing !? { |ed|
			if(ed[\path] == path and: { ed[\ops].notEmpty }) {
				ed[\ops].pop;
				ed[\score] = ed[\original];
				ed[\ops].do { |op| ed[\score] = ed[\score].edited(op) };
				this.prWrite;
			};
		};
		^editing
	}

	// Writes the copy over the take, or as a new file when asNew; the copy is dropped. Returns the path.
	save { |which, asNew = false|
		var path = this.resolveTake(which), target;
		editing !? { |ed|
			if(ed[\path] == path) {
				target = if(asNew) { REScore.pathFor(root, song.name, version ++ " edit") } { path };
				RCGuard.call(tag, nil) { ed[\score].write(target) };
				RCLog.post(tag, "edit: % operation(s) saved to %".format(ed[\ops].size, target.basename));
				this.prClear;
				this.prEditFeed(target);
			};
		};
		^target
	}

	revert { |which|
		var path = this.resolveTake(which);
		if(editing.notNil and: { editing[\path] == path }) { this.prClear; this.prEditFeed };
	}

	prLoad { |path|
		if(editing.isNil or: { editing[\path] != path }) {
			this.prClear;
			REScore.read(path) !? { |s| editing = (path: path, original: s, score: s, ops: List.new) };
		};
		^editing
	}

	prWrite {
		editing !? { |ed|
			RCGuard.call(tag, nil) { ed[\score].write(this.class.workingPath(ed[\path])) };
			this.prEditFeed;
		};
	}

	prClear {
		editing !? { |ed|
			var w = this.class.workingPath(ed[\path]);
			[w, REScore.companionPath(w, 2), REScore.companionPath(w, 3)].do { |p| if(File.exists(p)) { File.delete(p) } };
		};
		editing = nil;
	}

	//////// the feed

	feed { |kind, dict|
		if(feedFunc.notNil) { feedFunc.value(kind, dict) } { feedAddr.sendMsg("/rigfeed/" ++ kind, REJSON.stringify(dict)) };
	}

	prEditFeed { |saved|
		this.feed(\edit, (file: editing !? { |ed| ed[\path].basename }, working: editing !? { |ed| this.class.workingPath(ed[\path]).basename },
			ops: editing !? { |ed| ed[\ops].size } ? 0, saved: saved !? (_.basename)));
	}

	// What a page's state needs; the rig adds its own keys.
	state {
		var rec = recorder;
		^(recorder: rec !? { |r| r.state.asString } ? "off", beat: song.clock.beats.round(0.01), events: rec !? { |r| r.score !? (_.size) },
			recBeat: rec !? { |r| if(r.isRecording) { (song.clock.beats - r.beat0).round(0.01) } { nil } },
			position: player !? (_.position) !? (_.round(0.01)), playing: playingPath,
			overdubbing: rec !? { |r| r.player.notNil and: { r.player.isPlaying } } ? false,
			takes: this.takes, lastTake: rec !? (_.lastPath), editing: editing !? { |ed| (file: ed[\path].basename, ops: ed[\ops].size) })
	}

	prStartFeed {
		feedRoutine = Routine {
			loop { RCGuard.call(tag, nil) { this.feed(\state, this.state) }; 0.25.wait };
		}.play(AppClock);
		recorder !? { |r|
			savedOnEvent = r.onEvent;
			chained = true;
			r.onEvent = { |rec, ev|
				savedOnEvent.value(rec, ev);
				this.feed(\event, (id: ev[\id], beat: ev[\beat], kind: ev[\kind].asString, voice: ev[\voice] !? (_.asString), key: ev[\key] !? (_.asString),
					path: ev[\path], args: ev[\args], name: ev[\name] !? (_.asString), method: ev[\method] !? (_.asString)));
			};
		};
	}

	//////// the OSCdefs

	prInstall {
		var key = { |name| (prefix.asString.drop(1) ++ "_rec_" ++ name).asSymbol };
		var path = { |name| (prefix ++ "/rec/" ++ name).asSymbol };
		defs = [
			OSCdef(key.(\play), { |msg| this.play(msg[1] ? "last") }, path.(\play)).permanent_(true),
			OSCdef(key.(\stopPlay), { this.stopPlay }, path.(\stopPlay)).permanent_(true),
			OSCdef(key.(\edit), { |msg|
				var op = REJSON.parse(msg[2].asString);
				if(op.notNil) { this.edit(msg[1] ? "last", op) } { RCLog.warn(tag, "edit: no operation in %".format(msg[2])) };
			}, path.(\edit)).permanent_(true),
			OSCdef(key.(\undo), { |msg| this.undo(msg[1] ? "last") }, path.(\undo)).permanent_(true),
			OSCdef(key.(\save), { |msg| this.save(msg[1] ? "last", (msg[2] ? "").asString == "new") }, path.(\save)).permanent_(true),
			OSCdef(key.(\revert), { |msg| this.revert(msg[1] ? "last") }, path.(\revert)).permanent_(true)
		];
	}

	free {
		defs.do(_.free);
		defs = nil;
		feedRoutine !? (_.stop);
		feedRoutine = nil;
		if(chained) { recorder.onEvent = savedOnEvent; chained = false };
		player !? (_.stop);
		this.prClear;
	}

	printOn { |stream| stream << "REScoreEditor(" << song.name << ", " << prefix << ", " << root << ")" }
}
