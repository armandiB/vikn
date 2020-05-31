// Normalizer: get all files with certain extensions in folder, mkdir normalized_float in folder_transformed, normalize in there. Return list of all new files.

SampleNormalizer {

	//ToDo-low: make recursive, ignore *_transformed dirs

	var <>maxAmp, <folderPath, <>extensions;
	var <>filePathNames;

	*new { arg folderString, maxAmp=0.8, extensions = [\wav], absolutePath=false;
		^super.new.initSampleNormalizer(folderString, maxAmp, extensions, absolutePath);
	}

	initSampleNormalizer{ arg folderStringarg, maxAmparg, extensionsarg, absolutePatharg;
		this.folderPath_(folderStringarg, absolutePatharg);
		extensions = extensionsarg;
		maxAmp = maxAmparg;
	}

	folderPath_ { arg folder, absolutePath=false;
		folderPath = FileIOUtils.makePathNameIfNot(folder) +/+ "/";
		if(absolutePath.not,{
			folderPath = Sample.dir +/+ folderPath;
		});
	}

	getFilePathNames{
		filePathNames = FileIOUtils.getFilesInDir(folderPath, extensions);
	}

	normalize {
		var normalizedFolderPath;
		this.getFilePathNames;
		normalizedFolderPath = this.makeNewFolder(folderPath);

		^filePathNames.collect({|file| this.normalizeFile(file, normalizedFolderPath)});
	}

	makeNewFolder {arg folderPath;
		var transformedPath, normalizedFolderPath;
		transformedPath = folderPath.parentPath +/+ PathName(folderPath.folderName ++ "_transformed");
		normalizedFolderPath = transformedPath +/+ PathName("normalized_" ++ maxAmp.asCompileString.replace(".", "-") ++ "_" ++ Date.getDate.format("%Y%m%d"));
		File.mkdir(normalizedFolderPath.fullPath);
		^normalizedFolderPath;
	}

	normalizeFile {arg filePath, normalizedFolderPath;
		var newFilePath = normalizedFolderPath +/+ filePath.fileName;
		SoundFile.normalize(filePath.fullPath, newFilePath.fullPath, maxAmp: maxAmp, threaded: true);
		^newFilePath;
	}

}