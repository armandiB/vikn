FileInModule : TemplateModule {

	var <filebufs, <server, <>normalize;
	var <>folderPathName, <>extensionSet, <>fileList;

	*new{|server, folderPathName, extensionSet=#["wav"], normalize=0.8, readFiles=true|
		^super.new.initFileInModule(server, folderPathName, extensionSet, normalize, readFiles);
	}
	initFileInModule {|serverarg, folderPathNamearg, extensionSetarg, normalizearg, readFilesarg|
		server = serverarg ? Server.default;
		normalize = normalizearg;
		this.fillDeftParams();
		this.fillFunDict();
		folderPathNamearg !? this.getFileList(folderPathNamearg, extensionSetarg);
		if(readFilesarg, {fileList !? this.readFileList});
	}

	fillDeftParams {
		dfltParams = (
			numChannels_d: 1,
			bufidx_d: 0,
			rate_d: 1,
			trigger_d: 1,
			startPos_d: 0,
			loop_d: 1,
			mul_d: 1.0
		)
	}

	//As of now filebufs is fixed at the time SynthDef is made.
	fillFunDict{
		funDict = (
			play_file_1chan: {
				var bufnum;
				bufnum = Select.kr(\bufidx.kr(~bufidx_d), filebufs);
				PlayBufRate.ar(~numChannels_d, bufnum, \rate.kr(~rate_d), \trigger.kr(~trigger_d), \startPos.kr(~startPos_d), \loop.kr(~loop_d), \mul.kr(~mul_d));
			}
		)
	}

	getFileList{|folderPathNamearg, extensionSetarg|
		folderPathName = folderPathNamearg ? folderPathName;
		extensionSet = {extensionSetarg.as(Set)}.try ? extensionSet ? Set[];
		fileList = List(); //Alternative: SoundFile.collect
		folderPathName.files.do({|file|
			if(extensionSet.includes(file.extension), {
				fileList.add(file)
		})});

		//ToDo: may want to move that out
		"Found ".post; fileList.size.post; " files:".postln;
		fileList[0..10].do({|file| file.fullPath.postln});
		if(fileList.size > 10, {"...".postln});
	}

	//Preloading everything to be able to switch files during execution, so unsuitable to large libraries.
	readFileList{
		filebufs !? {filebufs.do({|buf| buf.free})};
		filebufs = Array.fill(fileList.size, {|i| Buffer.read(server, fileList[i].fullPath)});
		normalize !? {this.normalizeFileBufs};
	}

	normalizeFileBufs{|normalizearg|
		normalize = normalizearg ? normalize;
		filebufs.do({|file| file.normalize(normalize)});
	}
}

//Pseudo-Ugen
PlayBufRate {
	*ar { |numChannels=1, bufnum, rate=1, trigger=1, startPos=0, loop=1, mul = 1.0, add = 0.0|
		^PlayBuf.ar(numChannels, bufnum, BufRateScale.kr(bufnum)*rate, trigger, BufFrames.ir(bufnum)*startPos, loop)*mul + add;
	}

	*kr { |numChannels=1, bufnum, rate=1, trigger=1, startPos=0, loop=1, mul = 1.0, add = 0.0|
		^PlayBuf.kr(numChannels, bufnum, BufRateScale.kr(bufnum)*rate, trigger, BufFrames.ir(bufnum)*startPos, loop)*mul + add;
	}
}