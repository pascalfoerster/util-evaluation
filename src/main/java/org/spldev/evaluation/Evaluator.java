/* -----------------------------------------------------------------------------
 * Evaluation-Lib - Miscellaneous functions for performing an evaluation.
 * Copyright (C) 2021  Sebastian Krieter
 * 
 * This file is part of Evaluation-Lib.
 * 
 * Evaluation-Lib is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * Evaluation-Lib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Evaluation-Lib.  If not, see <https://www.gnu.org/licenses/>.
 * 
 * See <https://github.com/skrieter/evaluation> for further information.
 * -----------------------------------------------------------------------------
 */
package org.spldev.evaluation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

import org.spldev.evaluation.properties.IProperty;
import org.spldev.util.io.csv.CSVWriter;
import org.spldev.util.logging.Logger;
import org.spldev.util.logging.Logger.LogType;
import org.spldev.util.logging.TabFormatter;
import org.spldev.util.logging.TimeStampFormatter;

/**
 * @author Sebastian Krieter
 */
public abstract class Evaluator {

	public final TabFormatter tabFormatter = new TabFormatter();
	protected final EvaluatorConfig config;

	private final LinkedHashMap<String, CSVWriter> csvWriterList = new LinkedHashMap<>();

	protected int systemID;
	protected int systemIteration;

	public Evaluator(String configPath, String configName) throws Exception {
		Logger.logInfo(System.getProperty("user.dir"));
		config = new EvaluatorConfig(configPath);
		config.readConfig(configName);
	}

	public void init() throws Exception {
		setupDirectories();
		installLogger();
		addCSVWriters();
		for (CSVWriter writer : csvWriterList.values()) {
			writer.flush();
		}
		Logger.logInfo("Running " + this.getClass().getSimpleName());
	}

	protected void setupDirectories() throws IOException {
		config.setup();
		try {
			createDir(config.outputPath);
			createDir(config.csvPath);
			createDir(config.tempPath);
			createDir(config.logPath);
		} catch (IOException e) {
			Logger.logError("Could not create output directory.");
			Logger.logError(e);
			throw e;
		}
	}

	private void installLogger() throws FileNotFoundException {
		Logger.addErrLog(LogType.ERROR);
		if (config.verbosity.getValue() > 0) {
			Logger.addOutLog(LogType.INFO, LogType.DEBUG, LogType.PROGRESS);
		} else {
			Logger.addOutLog(LogType.INFO, LogType.DEBUG);
		}
		if (config.logPath != null) {
			Path outLogFile = config.logPath.resolve("output.log");
			Logger.addFileLog(outLogFile, LogType.INFO, LogType.DEBUG);
			Path errLogFile = config.logPath.resolve("error.log");
			Logger.addFileLog(errLogFile, LogType.ERROR);
		}
		Logger.addFormatter(new TimeStampFormatter());
		Logger.addFormatter(tabFormatter);
		Logger.install();
	}

	private void createDir(final Path path) throws IOException {
		if (path != null) {
			Files.createDirectories(path);
		}
	}

	protected void addCSVWriters() {
	};

	public void dispose() {
		Logger.uninstall();
		if (config.debug.getValue() == 0) {
			deleteTempFolder();
		}
	}

	private void deleteTempFolder() {
		try {
			Files.walkFileTree(config.tempPath, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					Files.deleteIfExists(dir);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void run() {
		printConfigFile();
	}

	private void printConfigFile() {
		for (IProperty prop : EvaluatorConfig.propertyList) {
			Logger.logInfo(prop.toString());
		}
	}

	protected void logSystem() {
		StringBuilder sb = new StringBuilder();
		sb.append("Processing System: ");
		sb.append(config.systemNames.get(systemID));
		sb.append(" (");
		sb.append(systemID + 1);
		sb.append("/");
		sb.append(config.systemNames.size());
		sb.append(")");
		Logger.logInfo(sb.toString());
	}

	protected CSVWriter addCSVWriter(String fileName, List<String> csvHeader) {
		final CSVWriter existingCSVWriter = csvWriterList.get(fileName);
		if (existingCSVWriter == null) {
			CSVWriter csvWriter = new CSVWriter();
			try {
				csvWriter.setOutputDirectory(config.csvPath);
				csvWriter.setFileName(fileName);
			} catch (IOException e) {
				Logger.logError(e);
				return null;
			}
			csvWriter.setAppend(config.append.getValue());
			csvWriter.setHeader(csvHeader);
			csvWriterList.put(fileName, csvWriter);
			return csvWriter;
		} else {
			return existingCSVWriter;
		}
	}

	protected void extendCSVWriter(String fileName, List<String> csvHeader) {
		final CSVWriter existingCSVWriter = csvWriterList.get(fileName);
		if (existingCSVWriter != null) {
			extendCSVWriter(existingCSVWriter, csvHeader);
		}
	}

	protected void extendCSVWriter(CSVWriter writer, List<String> csvHeader) {
		for (String headerValue : csvHeader) {
			writer.addHeaderValue(headerValue);
		}
	}

	protected final void writeCSV(CSVWriter writer, Consumer<CSVWriter> writing) {
		writer.createNewLine();
		try {
			writing.accept(writer);
		} catch (Exception e) {
			writer.removeLastLine();
			throw e;
		}
		writer.flush();
	}

}
