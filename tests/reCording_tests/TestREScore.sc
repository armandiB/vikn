// REScore: events with ids, the value codec (what replays, what does not),
// reCurrent object references resolved through a song or the environment,
// and the file round trip.
TestREScore : UnitTest {
	var song, savedRateLimit, dir;

	setUp {
		RCTestSupport.bootSession;
		song = RCSong(\sc, 1);
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
		dir = PathName.tmp +/+ "re_score_" ++ UniqueID.next;
	}

	tearDown {
		RCTestSupport.reset;
		RCLog.rateLimit = savedRateLimit;
	}

	logHas { |text| ^RCLog.history.any { |e| e[2].contains(text) } }

	test_new_and_meta {
		var s = REScore(song, piece: "Piece", wersion: "w1");
		this.assertEquals(s.meta[\song], "sc", "song name from the RCSong");
		this.assertEquals(REScore(\demo).meta[\song], "demo", "or from a Symbol");
		this.assertEquals(s.meta[\piece], "Piece");
		this.assertEquals(s.meta[\wersion], "w1");
		this.assert("^[0-9]{6}_[0-9]{6}$".matchRegexp(s.meta[\created]), "created: a yymmdd_hhmmss stamp");
		this.assertEquals(s.meta[\sc], Main.version, "the sclang version");
		this.assertEquals(s.size, 0);
		this.assertEquals(s.duration, 0, "no events: no duration");
		this.assertEquals(REScore.pathFor("/r", song, "w1", "s"), "/r/s_sc w1.json", "the take path, json");
	}

	test_add_and_query {
		var s = REScore(\demo);
		var id1 = s.add((beat: 0.0, secs: 0.0, kind: \snapshot, name: \start));
		var id2 = s.add((beat: 1.0, secs: 0.5, kind: \action, voice: 'core/kick', method: \set, args: [\amp, 0.5]));
		var id3 = s.add((id: 10, beat: 2.0, secs: 1.0, kind: \midi, voice: \knob, value: 0.3));
		var id4 = s.add((id: 10, beat: 3.0, secs: 1.5, kind: \code, voice: \code, text: "~x = 1"));
		this.assertEquals([id1, id2, id3], [1, 2, 10], "ids assigned in sequence, a given id kept");
		this.assertEquals(id4, 11, "a used id is reassigned after the highest");
		this.assert(this.logHas("already used"), "and warned about");
		this.assertEquals(s.size, 4);
		this.assertEquals(s.at(10)[\voice], \knob, "at(id)");
		this.assert(s.at(2).isKindOf(IdentityDictionary) and: { s.at(2).isKindOf(Event).not }, "events are plain IdentityDictionaries");
		this.assertEquals(s.duration, 3.0, "duration: the last beat");
		this.assertEquals(s.eventsOf('core/kick').collect { |e| e[\id] }, [2], "eventsOf");
		this.assertEquals(s.ofKind(\code).size, 1, "ofKind");
		this.assertEquals(s.between(1.0, 3.0).collect { |e| e[\id] }, [2, 10], "between: from inclusive, to exclusive");
		this.assertEquals(s.voiceNames, ['code', 'core/kick', 'knob'], "voice names, sorted");
		this.assertEquals(s.kinds, [\action, \code, \midi, \snapshot], "kinds, sorted");
		s.add((beat: 3.0, kind: \action, cause: 11));
		this.assertEquals(s.causedBy(11).size, 1, "causedBy");
		s.remove(10);
		this.assertEquals(s.size, 4, "remove");
		this.assertEquals(s.at(10), nil);
		s.add((id: 5, beat: 0.5, kind: \tempo, tempo: 2.0));
		s.sort;
		this.assertEquals(s.events.collect { |e| e[\id] }, [1, 5, 2, 11, 12], "sort by beat, equal beats by id");
		s.meta[\duration] = 16.0;
		this.assertEquals(s.duration, 16.0, "a recorded duration wins");
	}

	test_codec_plain_values {
		[nil, true, 1, 0.5, "text", \sym, [1, "a", \b, [2]], "42"].do { |x|
			this.assertEquals(REScore.encodeValue(x), x, "plain value kept: " ++ x.asCompileString);
			this.assertEquals(REScore.decodeValue(REScore.encodeValue(x)), x, "and decoded");
		};
		this.assertEquals(REScore.encode(1)[1], true, "replayable");
		this.assertEquals(REScore.encodeValue(inf), IdentityDictionary[\sc -> "inf"], "a non-finite number as its compile string");
		this.assertEquals(REScore.decodeValue(REScore.encodeValue(inf)), inf);
	}

	test_codec_events_and_dicts {
		var ev = (a: 1, b: \x, c: [1, 2], d: (e: 2.5));
		var enc = REScore.encodeValue(ev);
		var dict = Dictionary["k" -> 1];
		var back;
		this.assert(enc.isKindOf(IdentityDictionary) and: { enc.isKindOf(Event).not }, "an Event encodes as a plain object");
		this.assertEquals(enc[\d][\e], 2.5, "nested");
		back = REScore.decodeValue(enc);
		this.assert(back.isKindOf(Event), "decodes as an Event");
		this.assertEquals(back, ev, "equal to the original");
		this.assertEquals(REScore.encodeValue(dict), IdentityDictionary[\dict -> IdentityDictionary[\k -> 1]], "another Dictionary is wrapped");
		back = REScore.decodeValue(REScore.encodeValue(dict));
		this.assert(back.isKindOf(IdentityDictionary) and: { back.isKindOf(Event).not }, "and comes back as an IdentityDictionary");
		this.assertEquals(back[\k], 1);
		this.assertEquals(REScore.decodeValue(IdentityDictionary.new).class, Event, "an empty object is an empty Event");
	}

	test_codec_patterns_functions_and_the_rest {
		var pat = Pseq([1, 2], inf);
		var enc = REScore.encode(pat);
		var fn = "{ |x| x + 1 }".interpret;
		var outer = 2;
		var open = { outer + 1 };   // refers to a variable outside: no source kept
		var env = Env([0, 1], [0.5]);
		var back;
		this.assertEquals(REScore.encodeValue({ 1 }), IdentityDictionary[\sc -> "{ 1 }"], "a closed Function keeps its source, even from a class file");
		this.assertEquals(enc[0], IdentityDictionary[\sc -> "Pseq([1, 2], inf)"], "a Pattern as its compile string");
		this.assertEquals(enc[1], true, "replayable");
		back = REScore.decodeValue(enc[0]);
		this.assertEquals(back.asCompileString, pat.asCompileString, "decoded to an equal Pattern");
		this.assertEquals(REScore.encodeValue(fn), IdentityDictionary[\sc -> "{ |x| x + 1 }"], "a Function with source");
		this.assertEquals(REScore.decodeValue(REScore.encodeValue(fn)).value(2), 3, "decoded and callable");
		enc = REScore.encode(open);
		this.assertEquals(enc[0], IdentityDictionary[\ref -> "a Function without source"], "a Function without source is a reference");
		this.assertEquals(enc[1], false, "not replayable");
		this.assertEquals(REScore.encodeValue(env), IdentityDictionary[\sc -> env.asCompileString], "an Env as its compile string");
		this.assertEquals(REScore.decodeValue(REScore.encodeValue(env)).asCompileString, env.asCompileString, "and back");
		back = REScore.decodeValue(REScore.encodeValue(Ref(3)));
		this.assert(back.isKindOf(Ref) and: { back.value == 3 }, "a Ref");
		back = REScore.decodeValue(REScore.encodeValue(Rest(1)));
		this.assert(back.isRest and: { back.dur == 1 }, "a Rest");
		this.assertEquals(REScore.encodeValue(RCCrawlerMoves), IdentityDictionary[\sc -> "RCCrawlerMoves"], "a Class");
		enc = REScore.encode(Object.new);
		this.assertEquals(enc[0][\ref].class, String, "anything else is its print string");
		this.assertEquals(enc[1], false, "and not replayable");
		enc = REScore.encode([1, (a: Object.new)]);
		this.assertEquals(enc[1], false, "a reference anywhere inside makes the value not replayable");
		this.assertEquals(REScore.decodeValue(IdentityDictionary[\ref -> "x"]), IdentityDictionary[\ref -> "x"], "a reference decodes to itself");
		this.assert(REScore.isRef(IdentityDictionary[\ref -> "x"]), "isRef");
		this.assertEquals(REScore.decodeValue(IdentityDictionary[\sc -> "1 +"]), nil, "a broken compile string decodes to nil");
		this.assert(this.logHas("cannot compile"), "and is reported");
	}

	test_rc_refs {
		var layer = song.layer(\core);
		var beat = layer.addBeat(\k, [type: \rest, dur_flex: 1, amp: 0.1]);
		var tpl = RCOrgnsm(\sp, 0, 0, song);
		var batch, crawler, ref, enc;
		tpl.addStaticAttrs((seed: 1));
		tpl.attrDictBase = [type: \rest, dur_flex: 1];
		tpl.register;
		batch = RCBatch(\flock, tpl, layerKey: \core);
		crawler = RCCrawler(["static_attrs.x"]);
		ref = REScore.rcRef(beat);
		this.assertEquals(ref, IdentityDictionary[\rc -> "beat", \song -> "sc", \layer -> "core", \name -> "k"], "a beat reference");
		this.assertEquals(REScore.resolve(ref, song), beat, "resolved through the song");
		this.assertEquals(REScore.resolve(ref), beat, "or through the default session");
		this.assertEquals(REScore.resolve(REScore.rcRef(layer), song), layer, "a layer");
		this.assertEquals(REScore.resolve(REScore.rcRef(song), song), song, "the song");
		this.assertEquals(REScore.rcRef(tpl)[\name], tpl.name.asString, "an orgnsm by name");
		this.assertEquals(REScore.resolve(REScore.rcRef(tpl), song), tpl, "resolved through the registry");
		currentEnvironment[\reTestBatch] = batch;
		currentEnvironment[\reTestCrawler] = crawler;
		this.assertEquals(REScore.rcRef(batch)[\rc], "batch");
		this.assertEquals(REScore.resolve(REScore.rcRef(batch), song), batch, "a batch by the variable holding it");
		this.assertEquals(REScore.resolve(REScore.rcRef(crawler), song), crawler, "a crawler by its keys");
		currentEnvironment[\reTestBatch] = nil;
		currentEnvironment[\reTestCrawler] = nil;
		this.assertEquals(REScore.resolve(REScore.rcRef(batch), song), nil, "gone from the environment: nil");
		enc = REScore.encode([beat, 1]);
		this.assertEquals(enc[0][0][\rc], "beat", "a beat inside a value is its reference");
		this.assertEquals(enc[1], true, "and replayable");
		this.assertEquals(REScore.decodeValue(enc[0], song), [beat, 1], "decoded back to the object");
		this.assertEquals(REScore.decodeValue(IdentityDictionary[\rc -> "beat", \song -> "sc", \layer -> "core", \name -> "nope"], song)[\name], "nope", "unresolvable: the reference itself");
		this.assertEquals(REScore.resolve(IdentityDictionary[\rc -> "thing"], song), nil, "unknown kind: nil");
		this.assert(this.logHas("unknown reference kind"), "and warned");
		beat.free;
	}

	test_file_round_trip {
		var s = REScore(\demo, "P", "w0");
		var path = REScore.pathFor(dir, \demo, "w0", "260101_000000");
		var rc = IdentityDictionary[\rc -> "beat", \layer -> "core", \name -> "kick"];
		var text, back;
		s.meta[\beat0] = 128.0;
		s.meta[\tempo] = 2.0;
		s.meta[\commits] = IdentityDictionary[\vikn -> "abc1234"];
		s.tempoMap.add([0, 2.0]);
		s.voices['core/kick'] = IdentityDictionary[\objects -> [rc]];
		s.controls[\knob] = IdentityDictionary[\min -> 0, \max -> 1, \warp -> "lin"];
		s.add((beat: 0.0, secs: 0.0, kind: \snapshot, name: \start, state: IdentityDictionary[\x -> 1]));
		s.add((beat: 1.0, secs: 0.5, kind: \action, voice: 'core/kick', rc: rc, method: \set,
			args: REScore.encodeValue([\amp, Pwhite(0.1, 0.5), nil, \default])));
		s.add((beat: 2.0, secs: 1.0, kind: \code, voice: \code, text: "~x = \"42\"", replay: false, cause: 1));
		text = s.asJSONString;
		this.assert(text.contains("\"kind\": \"action\""), "symbol fields are plain strings in the file");
		this.assert(text.contains("\n    {\"id\": 2, \"beat\": 1.0, \"secs\": 0.5, \"kind\": \"action\", \"voice\": \"core/kick\""), "one event per line, fields in order");
		this.assert(text.contains("\"args\": [\"\\\\amp\", {\"sc\": \"Pwhite(0.1, 0.5)\"}, null, \"\\\\default\"]"), "values in the codec's form");
		this.assertEquals(text.find("\"format\": \"re-score\""), 4, "format first");
		back = REScore.fromJSONString(text);
		this.assertEquals(back.meta, s.meta, "meta");
		this.assertEquals(back.size, 3);
		this.assertEquals(back.at(2)[\kind], \action, "symbol fields back as Symbols");
		this.assertEquals(back.at(2)[\voice], 'core/kick');
		this.assertEquals(back.at(2)[\method], \set);
		this.assertEquals(back.at(2)[\args], [\amp, IdentityDictionary[\sc -> "Pwhite(0.1, 0.5)"], nil, \default], "args exact");
		this.assertEquals(back.at(2)[\rc], rc, "the reference");
		this.assertEquals(back.at(3)[\text], "~x = \"42\"", "text exact");
		this.assertEquals(back.at(3)[\replay], false);
		this.assertEquals(back.at(3)[\cause], 1);
		this.assertEquals(back.at(1)[\state], IdentityDictionary[\x -> 1]);
		this.assertEquals(back.tempoMap.asArray, [[0, 2.0]]);
		this.assertEquals(back.voices['core/kick'][\objects][0][\name], "kick");
		this.assertEquals(back.controls[\knob][\warp], "lin");
		this.assertEquals(s.write(path), path, "write returns the path");
		this.assert(File.exists(path), "file written");
		this.assertEquals(REScore.read(path).asJSONString, text, "read: the same text again");
		this.assertEquals(s.copy.asJSONString, text, "copy: equal");
		this.assertEquals(REScore.fromDict(IdentityDictionary[\format -> "nope"]), nil, "another format is refused");
		this.assert(this.logHas("not a re-score file"), "and reported");
		File.delete(path);
		File.delete(dir);
	}

	test_gitHead {
		var vikn = REScore.filenameSymbol.asString.dirname.dirname;
		var head = REScore.gitHead(vikn);
		this.assert(head.notNil and: { "^[0-9a-f]{7,}$".matchRegexp(head) }, "the vikn checkout's short commit: " ++ head);
		this.assertEquals(REScore.gitHead("/"), nil, "outside a repository: nil");
		this.assertEquals(REScore.gitHead(nil), nil);
	}
}
