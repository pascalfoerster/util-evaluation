/*
 * Copyright (C) 2022 Sebastian Krieter
 *
 * This file is part of evaluation.
 *
 * evaluation is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * evaluation is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with evaluation. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatJAR/evaluation> for further information.
 */
package de.featjar.evaluation;

import de.featjar.evaluation.properties.Property;
import de.featjar.util.cli.CLIFunction;
import de.featjar.util.io.csv.CSVWriter;
import de.featjar.util.logging.Logger;
import de.featjar.util.logging.TabFormatter;
import de.featjar.util.logging.TimeStampFormatter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Sebastian Krieter
 */
public abstract class Evaluator implements CLIFunction {

    @Override
    public void run(List<String> args) {
        if (args.size() < 1) {
            System.out.println("Configuration path not specified!");
            return;
        }
        Logger.logInfo(System.getProperty("user.dir"));
        try {
            init(args.get(0), args.size() > 1 ? args.get(1) : "config");
            printConfigFile();
            evaluate();
        } catch (final Exception e) {
            Logger.logError(e);
        } finally {
            dispose();
        }
    }

    public final TabFormatter tabFormatter = new TabFormatter();
    protected EvaluatorConfig config;

    private final LinkedHashMap<String, CSVWriter> csvWriterList = new LinkedHashMap<>();

    protected int systemIndex;
    protected int systemIteration;

    public void init(String configPath, String configName) throws Exception {
        config = new EvaluatorConfig(configPath);
        addProperties();
        config.readConfig(configName);
        initPaths();
        try {
            setupDirectories();
        } catch (final IOException e) {
            Logger.logError("Fail -> Could not create output directory.");
            Logger.logError(e);
            throw e;
        }
        try {
            installLogger();
        } catch (final Exception e) {
            Logger.logError("Fail -> Could not install logger.");
            Logger.logError(e);
            throw e;
        }

        config.readSystemNames();
        addCSVWriters();
        for (final CSVWriter writer : csvWriterList.values()) {
            writer.flush();
        }
        Logger.logInfo("Running " + this.getClass().getSimpleName());
    }

    protected void initPaths() {
        config.outputRootPath = Paths.get(config.outputPathProperty.getValue());
        config.resourcePath = Paths.get(config.resourcesPathProperty.getValue());
        config.modelPath = config.resourcePath.resolve(config.modelsPathProperty.getValue());
        config.outputPath = config.outputRootPath.resolve(config.readCurrentOutputMarker());
        config.csvPath = config.outputPath.resolve("data");
        config.tempPath = config.outputPath.resolve("temp");
        config.logPath = config.outputPath.resolve("log-" + config.getTimeStamp());
    }

    protected void addProperties() {}

    protected void setupDirectories() throws IOException {
        try {
            createDir(config.outputPath);
            createDir(config.csvPath);
            createDir(config.tempPath);
            createDir(config.logPath);
        } catch (final IOException e) {
            Logger.logError("Could not create output directory.");
            Logger.logError(e);
            throw e;
        }
    }

    private void installLogger() throws FileNotFoundException {
        Logger.setErrLog(Logger.LogType.ERROR);
        switch (config.verbosity.getValue()) {
            case 0:
                Logger.setOutLog(Logger.LogType.INFO);
                break;
            case 1:
                Logger.setOutLog(Logger.LogType.INFO, Logger.LogType.DEBUG);
                break;
            case 2:
                Logger.setOutLog(Logger.LogType.INFO, Logger.LogType.DEBUG, Logger.LogType.PROGRESS);
                break;
        }
        if (config.logPath != null) {
            final Path outLogFile = config.logPath.resolve("output.log");
            Logger.addFileLog(outLogFile, Logger.LogType.INFO, Logger.LogType.DEBUG);
            final Path errLogFile = config.logPath.resolve("error.log");
            Logger.addFileLog(errLogFile, Logger.LogType.ERROR);
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

    protected void addCSVWriters() {}

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
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    public abstract void evaluate() throws Exception;

    private void printConfigFile() {
        for (final Property<?> prop : EvaluatorConfig.propertyList) {
            Logger.logInfo(prop.toString());
        }
    }

    protected void logSystem() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Processing System: ");
        sb.append(config.systemNames.get(systemIndex));
        sb.append(" (");
        sb.append(systemIndex + 1);
        sb.append("/");
        sb.append(config.systemNames.size());
        sb.append(")");
        Logger.logInfo(sb.toString());
    }

    protected void logSystemRun() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Processing System: ");
        sb.append(config.systemNames.get(systemIndex));
        sb.append(" (");
        sb.append(systemIndex + 1);
        sb.append("/");
        sb.append(config.systemNames.size());
        sb.append(") - ");
        sb.append(systemIteration + 1);
        sb.append("/");
        sb.append(config.systemIterations.getValue());
        Logger.logInfo(sb.toString());
    }

    protected CSVWriter addCSVWriter(String fileName, List<String> csvHeader) {
        final CSVWriter existingCSVWriter = csvWriterList.get(fileName);
        if (existingCSVWriter == null) {
            final CSVWriter csvWriter = new CSVWriter();
            try {
                csvWriter.setOutputDirectory(config.csvPath);
                csvWriter.setFileName(fileName);
            } catch (final IOException e) {
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
        for (final String headerValue : csvHeader) {
            writer.addHeaderValue(headerValue);
        }
    }

    protected final void writeCSV(CSVWriter writer, Consumer<CSVWriter> writing) {
        writer.createNewLine();
        try {
            writing.accept(writer);
        } catch (final Exception e) {
            writer.removeLastLine();
            throw e;
        }
        writer.flush();
    }
}
