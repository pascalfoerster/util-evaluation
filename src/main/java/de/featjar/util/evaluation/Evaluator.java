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
 * See <https://github.com/FeatureIDE/FeatJAR-evaluation> for further information.
 */
package de.featjar.util.evaluation;

import de.featjar.base.data.Maps;
import de.featjar.util.evaluation.properties.Property;
import de.featjar.base.cli.ICommand;
import de.featjar.base.io.csv.CSVFile;
import de.featjar.base.log.Log;
import de.featjar.base.log.IndentFormatter;
import de.featjar.base.log.TimeStampFormatter;

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
public abstract class Evaluator implements ICommand {

    @Override
    public void run(List<String> args) {
        if (args.size() < 1) {
            FeatJAR.log().info("Configuration path not specified!");
            return;
        }
        FeatJAR.log().info(System.getProperty("user.dir"));
        try {
            init(args.get(0), args.size() > 1 ? args.get(1) : "config");
            printConfigFile();
            evaluate();
        } catch (final Exception e) {
            FeatJAR.log().error(e);
        } finally {
            dispose();
        }
    }

    public final IndentFormatter indentFormatter = new IndentFormatter();
    protected EvaluatorConfig config;

    private final LinkedHashMap<String, CSVFile> csvWriterList = Maps.empty();

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
            FeatJAR.log().error("Fail -> Could not create output directory.");
            FeatJAR.log().error(e);
            throw e;
        }
        try {
            installLogger();
        } catch (final Exception e) {
            FeatJAR.log().error("Fail -> Could not install logger.");
            FeatJAR.log().error(e);
            throw e;
        }

        config.readSystemNames();
        addCSVWriters();
        for (final CSVFile writer : csvWriterList.values()) {
            writer.flush();
        }
        FeatJAR.log().info("running " + this.getClass().getSimpleName());
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
            FeatJAR.log().error("Could not create output directory.");
            FeatJAR.log().error(e);
            throw e;
        }
    }

    private void installLogger() { // TODO: use CommandLine.configureVerbosity
        FeatJAR.install(cfg -> {
            cfg.log.logToSystemErr(Log.Verbosity.ERROR);
            switch (config.verbosity.getValue()) {
                case 0:
                    cfg.log.logToSystemOut(Log.Verbosity.INFO);
                    break;
                case 1:
                    cfg.log.logToSystemOut(Log.Verbosity.INFO, Log.Verbosity.DEBUG);
                    break;
                case 2:
                    cfg.log.logToSystemOut(Log.Verbosity.INFO, Log.Verbosity.DEBUG, Log.Verbosity.PROGRESS);
                    break;
            }
            if (config.logPath != null) {
                cfg.log.logToFile(config.logPath.resolve("output.log"), Log.Verbosity.INFO, Log.Verbosity.DEBUG);
                cfg.log.logToFile(config.logPath.resolve("error.log"), Log.Verbosity.ERROR);
            }
            cfg.log.addFormatter(new TimeStampFormatter());
            cfg.log.addFormatter(indentFormatter);
        });
    }

    private void createDir(final Path path) throws IOException {
        if (path != null) {
            Files.createDirectories(path);
        }
    }

    protected void addCSVWriters() {}

    public void dispose() {
        FeatJAR.log().uninstall();
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
            FeatJAR.log().info(prop.toString());
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
        FeatJAR.log().info(sb.toString());
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
        FeatJAR.log().info(sb.toString());
    }

    protected CSVFile addCSVWriter(String fileName, List<String> csvHeader) {
        final CSVFile existingCSVFile = csvWriterList.get(fileName);
        if (existingCSVFile == null) {
            final CSVFile csvFile;
            try {
                csvFile = new CSVFile(config.csvPath.resolve(fileName));
            } catch (final IOException e) {
                FeatJAR.log().error(e);
                return null;
            }
            csvFile.setHeaderFields(csvHeader);
            csvWriterList.put(fileName, csvFile);
            return csvFile;
        } else {
            return existingCSVFile;
        }
    }

    protected void extendCSVWriter(String fileName, List<String> csvHeader) {
        final CSVFile existingCSVFile = csvWriterList.get(fileName);
        if (existingCSVFile != null) {
            extendCSVWriter(existingCSVFile, csvHeader);
        }
    }

    protected void extendCSVWriter(CSVFile writer, List<String> csvHeader) {
        for (final String headerValue : csvHeader) {
            writer.addHeaderField(headerValue);
        }
    }

    protected final void writeCSV(CSVFile writer, Consumer<CSVFile> writing) {
        writer.newLine();
        try {
            writing.accept(writer);
        } catch (final Exception e) {
            writer.removeLastLine();
            throw e;
        }
        writer.flush();
    }
}
