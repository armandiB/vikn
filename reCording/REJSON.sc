// reCording — JSON for the score files: a writer (sclang has none) and a
// reader that types the scalars String:parseJSON (yaml-cpp) hands back as
// Strings. One dialect, exact both ways:
//   nil ↔ null; Booleans; Integers; Floats (the shortest string that reads
//   back to the same Float, ".0" kept on integral ones; a non-finite number is
//   written as null, with a warning); Strings (escaped; bytes above 127 pass
//   through, UTF-8 in and out); a Symbol ↔ a string with a leading backslash
//   ("\\amp", the pick-file convention); a String that would read back as
//   something else (digits, "true", a leading backslash) ↔ {"str": "..."};
//   Arrays and Lists ↔ arrays; Dictionaries and Events ↔ objects (keys as
//   strings, read back as an IdentityDictionary with Symbol keys).
// Output is stable for diffs: object keys in `keyOrder` first, the rest
// sorted; pretty-printed down to `inlineDepth` (deeper nodes on one line).
//
//   REJSON.stringify((a: 1, b: [1.5, "x", nil]))          → {"a": 1, "b": [1.5, "x", null]}
//   REJSON.stringify(obj, indent: 2, inlineDepth: 2, keyOrder: #[\id, \beat])
//   REJSON.parse("{\"a\": 1, \"s\": \"\\\\amp\"}")        → IdentityDictionary 'a' → 1, 's' → \amp
//   REJSON.write(obj, path, ...) / REJSON.read(path)       (folders made on the way)

REJSON {
	classvar <numberLike = "^-?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?$";
	classvar <integerLike = "^-?[0-9]+$";

	//////// writing

	*stringify { |obj, indent, inlineDepth = inf, keyOrder|
		var stream = CollStream.on(String.new(1024));
		this.prWrite(obj, stream, indent, inlineDepth, (keyOrder ? []).collect(_.asString), 0);
		^stream.collection
	}

	// Returns the path written (standardized).
	*write { |obj, path, indent, inlineDepth = inf, keyOrder|
		var text = this.stringify(obj, indent, inlineDepth, keyOrder);
		path = path.asString.standardizePath;
		this.mkdirAll(path.dirname);
		File.use(path, "w", { |f| f.write(text) });
		^path
	}

	*mkdirAll { |dir|
		if(dir.size == 0 or: { File.exists(dir) }) { ^this };
		if(dir.contains("/")) { this.mkdirAll(dir.dirname) };
		File.mkdir(dir);
	}

	*prWrite { |obj, stream, indent, inlineDepth, keyOrder, depth|
		case
		{ obj.isNil } { stream << "null" }
		{ obj.isKindOf(Boolean) } { stream << obj.asString }
		{ obj.isKindOf(Integer) } { stream << obj.asString }
		{ obj.isKindOf(Float) } { stream << this.prFloat(obj) }
		{ obj.isString } { this.prString(obj, stream) }
		{ obj.isKindOf(Symbol) } { this.prString("\\" ++ obj.asString, stream, false) }
		{ obj.isKindOf(Dictionary) } { this.prObject(obj, stream, indent, inlineDepth, keyOrder, depth) }
		{ obj.isKindOf(SequenceableCollection) } { this.prArray(obj, stream, indent, inlineDepth, keyOrder, depth) }
		{
			RCLog.warn(\json, { "a % is not JSON: written as its print string".format(obj.class) });
			this.prString(obj.asString, stream);
		};
	}

	*prFloat { |x|
		var s;
		if(x.isNaN or: { x.abs == inf }) {
			RCLog.warn(\json, "non-finite number % written as null".format(x));
			^"null"
		};
		s = x.asStringPrec(15);
		if(s.asFloat != x) { s = x.asStringPrec(16) };
		if(s.asFloat != x) { s = x.asStringPrec(17) };
		if(s.contains(".").not and: { s.contains("e").not }) { s = s ++ ".0" };
		^s
	}

	// A string the reader would type as something else is wrapped as {"str": ...}
	// (not for object keys and symbol markers, protect = false).
	*prString { |str, stream, protect = true|
		var wrap = protect and: { this.prNeedsWrap(str) };
		if(wrap) { stream << "{\"str\": " };
		stream << "\"";
		str.do { |c|
			var code = c.ascii;
			case
			{ c == $" } { stream << "\\\"" }
			{ c == $\\ } { stream << "\\\\" }
			{ c == $\n } { stream << "\\n" }
			{ c == $\t } { stream << "\\t" }
			{ c == $\r } { stream << "\\r" }
			{ code >= 0 and: { code < 32 } } { stream << "\\u" << code.asHexString(4) }
			{ stream << c };
		};
		stream << "\"";
		if(wrap) { stream << "}" };
	}

	*prNeedsWrap { |str|
		if(str.size == 0) { ^false };
		if(str[0] == $\\) { ^true };
		if(str == "true" or: { str == "false" }) { ^true };
		^numberLike.matchRegexp(str)
	}

	*prArray { |arr, stream, indent, inlineDepth, keyOrder, depth|
		var pretty = indent.notNil and: { depth < inlineDepth };
		if(arr.size == 0) { stream << "[]"; ^this };
		stream << "[";
		arr.do { |el, i|
			if(i > 0) { stream << "," };
			if(pretty) { this.prNewline(stream, indent, depth + 1) } { if(i > 0) { stream << " " } };
			this.prWrite(el, stream, indent, inlineDepth, keyOrder, depth + 1);
		};
		if(pretty) { this.prNewline(stream, indent, depth) };
		stream << "]";
	}

	*prObject { |dict, stream, indent, inlineDepth, keyOrder, depth|
		var pretty = indent.notNil and: { depth < inlineDepth };
		var pairs = this.prPairs(dict, keyOrder);
		if(pairs.size == 0) { stream << "{}"; ^this };
		stream << "{";
		pairs.do { |pair, i|
			if(i > 0) { stream << "," };
			if(pretty) { this.prNewline(stream, indent, depth + 1) } { if(i > 0) { stream << " " } };
			this.prString(pair[0], stream, false);
			stream << ": ";
			this.prWrite(pair[1], stream, indent, inlineDepth, keyOrder, depth + 1);
		};
		if(pretty) { this.prNewline(stream, indent, depth) };
		stream << "}";
	}

	// [keyString, value] pairs: the keys of keyOrder first, in that order, then the rest sorted.
	*prPairs { |dict, keyOrder|
		var first = List.new, rest = List.new;
		dict.keysValuesDo { |k, v|
			var ks = k.asString;
			if(keyOrder.includesEqual(ks)) { first.add([ks, v]) } { rest.add([ks, v]) };
		};
		first = first.asArray.sort { |a, b| keyOrder.indexOfEqual(a[0]) <= keyOrder.indexOfEqual(b[0]) };
		rest = rest.asArray.sort { |a, b| a[0] <= b[0] };
		^first ++ rest
	}

	*prNewline { |stream, indent, depth|
		stream << "\n" << String.fill(indent * depth, $ );
	}

	//////// reading

	*read { |path|
		path = path.asString.standardizePath;
		if(File.exists(path).not) { RCLog.error(\json, "no file at %".format(path)); ^nil };
		^this.parse(File.readAllString(path))
	}

	*parse { |text|
		var raw;
		if(text.isNil) { ^nil };
		raw = try { text.parseJSON } { |err| RCLog.exception(\json, err, "parse"); nil };
		^this.prType(raw)
	}

	*prType { |obj|
		var res;
		case
		{ obj.isString } { ^this.prTypeString(obj) }
		{ obj.isKindOf(Dictionary) } {
			if(obj.size == 1 and: { obj["str"].isString }) { ^obj["str"] };
			res = IdentityDictionary.new;
			obj.keysValuesDo { |k, v| res[k.asSymbol] = this.prType(v) };
			^res
		}
		{ obj.isKindOf(SequenceableCollection) } { ^obj.collect { |el| this.prType(el) } }
		{ ^obj };
	}

	*prTypeString { |s|
		var f;
		if(s.size > 0 and: { s[0] == $\\ }) { ^s[1..].asSymbol };
		if(s == "true") { ^true };
		if(s == "false") { ^false };
		if(integerLike.matchRegexp(s)) {
			f = s.asFloat;
			^if(f.abs < 2147483648.0) { s.asInteger } { f }
		};
		if(numberLike.matchRegexp(s)) { ^s.asFloat };
		^s
	}
}
