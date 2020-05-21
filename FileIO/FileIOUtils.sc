FileIOUtils{
	*makePathNameIfNot {arg stringOrPathName;
		if(stringOrPathName.isKindOf(PathName), {
			^stringOrPathName}, {
			^PathName(stringOrPathName)});
	}

	*getFilesInDir {arg dirPathName, extensions=nil; //Non-recursive.
		var fileList = List();
		if(extensions.isNil,{
			dirPathName.files.do({|file| fileList.add(file)});
		},{
			dirPathName.files.do({|file| if(extensions.includes(file.extension.asSymbol), {fileList.add(file)})});
		});
		^fileList;
	}

}