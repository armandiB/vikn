TestRCLoopBuffer : UnitTest {
	var song, savedRateLimit;

	setUp {
		RCTestSupport.bootSession;
		song = RCSong(\lb, 1);
		savedRateLimit = RCLog.rateLimit;
		RCLog.rateLimit = 0;
		RCLog.reset;
	}

	tearDown {
		RCTestSupport.reset;
		RCLog.rateLimit = savedRateLimit;
	}

	logHas { |text| ^RCLog.history.any { |e| e[2].contains(text) } }

	test_framesFor {
		this.assertEquals(RCLoopBuffer.framesFor(48000, 0.5, 4, 4), 384000, "sampleRate × beatDur × beats × margin");
		this.assertEquals(RCLoopBuffer.framesFor(48000, 0, 4, 4), 1, "never zero frames");
	}

	test_guards_without_server {
		var lb;
		song.inChanArray = [10];
		lb = RCLoopBuffer(song, \a, 4, 0, 2);
		this.assert(lb.isFreed and: { lb.buffer.isNil }, "no server → not allocated, no crash");
		this.assert(this.logHas("server not running"), "reported");
		this.assertEquals(song.loopBuffer(\a), nil, "not registered");
		lb = RCLoopBuffer(song, \b, 4, 3, 2);
		this.assert(this.logHas("no input channel"), "bad input index reported");
		lb = RCLoopBuffer(song, \c, 0, 0, 2);
		this.assert(this.logHas("loopBeats must be positive"), "bad loop length reported");
		lb.free;
		this.assert(lb.isFreed, "free on a failed buffer is harmless");
	}

	test_fileTree {
		var dir = PathName.tmp +/+ "rc_tree_test";
		var tree;
		File.mkdir(dir);
		File.mkdir(dir +/+ "sub");
		File.use(dir +/+ "a.wav", "w", { |f| f.write("x") });
		File.use(dir +/+ "sub" +/+ "b.wav", "w", { |f| f.write("x") });
		tree = RCUtil.fileTree(dir);
		this.assert(tree.at(\root, 'a.wav').isKindOf(PathName), "top-level file under root");
		this.assert(tree.at(\sub, 'b.wav').isKindOf(PathName), "nested file under its folder");
		this.assertEquals(RCUtil.fileTree(dir +/+ "nope").size, 0, "missing folder → empty tree + error");
		File.delete(dir +/+ "sub" +/+ "b.wav");
		File.delete(dir +/+ "a.wav");
		File.delete(dir +/+ "sub");
		File.delete(dir);
	}
}
