// Normalizer: get all files with certain extensions in folder, mkdir normalized_float, normalize in there. Return list of all new files.

SampleNormalizer {

	//ToDo-low: make recursive, ignore normalize_float dirs

	var <>maxAmp, <folderPath, <>extensions;
	var <>filePathNames;

	*new { arg folderString, maxAmp=0.8, extensions = ["wav"], absolutePath=false;
		^super.new.initSampleNormalizer(folderString, maxAmp, extensions, absolutePath);
	}

	initSampleNormalizer{ arg folderStringarg, maxAmparg, extensionsarg, absolutePatharg;
		folderPath = PathName(folderStringarg);
		if(absolutePatharg.not,{
			folderPath = Sample.dir +/+ folderPath;
		});
		extensions = extensionsarg;
		maxAmp = maxAmparg;
	}

	folderPath_ { arg folder, absolutePath=false;
		folderPath = FileIOUtils.makePathNameIfNot(folder);
		if(absolutePath.not,{
			folderPath = Sample.dir +/+ folderPath;
		});
	}

	getFilePathNames{
		filePathNames = FileIOUtils.getFilesInDir(folderPath, extensions);
	}

	supr {arg fileString, absolutePath=false;
		var filePath = fileString;

	var folderPath, normalizedFolderPath, newFilePath;

	folderPath = filePath.pathOnly;
	}

	makeNewFolder {arg folderPath;
		var normalizedFolderPath = folderPath +/+ PathName.new("normalized_" ++ maxAmp.asCompileString.replace(".", "-"));
		if(normalizedFolderPath.isFolder.not, {File.mkdir(normalizedFolderPath.fullPath)});
		^normalizedFolderPath;
	}

	normalizeFile {arg filePath, normalizedFolderPath;
		var newFilePath = normalizedFolderPath +/+ filePath.fileName;
		SoundFile.normalize(filePath.fullPath, newFilePath.fullPath, maxAmp: maxAmp, threaded: true);
		^newFilePath;
	}

}
