import os
import sys
from zipfile import ZipFile

arguments = sys.argv
if (len(arguments) == 2):
	rootDir = arguments[1]
	models = []
	for dirName, subdirList, fileList in os.walk(rootDir):
		if "model.xml" in fileList:
			modelName = os.path.basename(dirName)
			print(modelName)
			models.append((modelName, dirName))
	
	with ZipFile("models.zip", "w") as modelArchive:
		for name, dir in models:
			orgFile = os.path.join(dir, "model.xml")
			zipFile = os.path.join(name, "model.xml")
			modelArchive.write(orgFile, zipFile)
			
	with open("models.txt", "w") as modelList:
		for name, file in models:
			modelList.write(name + "\n")