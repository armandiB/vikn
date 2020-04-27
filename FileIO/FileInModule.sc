FileInModule : TemplateModule {

	var <filebufs, <server, <>normalize;
	var <>folderPathName, <>extensionSet, <>fileList;

	*new{|server, folderPathName, extensionSet=#["wav"], normalize=0.8, readFiles=true|
		^super.new.initFileInModule(server, folderPathName, extensionSet, normalize, readFiles);
	}
	initFileInModule {|serverarg, folderPathNamearg, extensionSetarg, normalizearg, readFilesarg|
		server = serverarg ? Server.default;
		this.fillDeftParams();
		this.fillFunDict();
		folderPathNamearg !? this.getFileList(folderPathNamearg, extensionSetarg);
		if(readFilesarg, {fileList !? this.readFileList});
	}

	fillDeftParams {
		dfltParams = (
			numchan: 1
		)
	}

	//ToDo: adapt to dfltParams
	fillFunDict{
		funDict = (
			play_file_1chan: {|bufidx=0, rate=1, trigger=1, startPos=0, loop=1, mul = 1, filebufnums=(this.filebufs)|
				var bufnum;
				bufnum = Select.kr(bufidx, filebufnums);
				PlayBufRate.ar(1, bufnum, rate, trigger, startPos, loop, mul);
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

	//ToDo: this is temporary, need for dynamic change between file buffers, and can't work with big libraries
	//control rate bus to access bufnum with Select.kr for mapping?
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
				PlayBuf.ar(numChannels, bufnum, BufRateScale.kr(bufnum)*rate, trigger, BufFrames.ir(bufnum)*startPos, loop)*mul + add;
	}

	*kr { |numChannels=1, bufnum, rate=1, trigger=1, startPos=0, loop=1, mul = 1.0, add = 0.0|
				PlayBuf.kr(numChannels, bufnum, BufRateScale.kr(bufnum)*rate, trigger, BufFrames.ir(bufnum)*startPos, loop)*mul + add;
	}
}