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

import de.featjar.evaluation.properties.ListProperty;
import de.featjar.evaluation.properties.Property;
import de.featjar.evaluation.properties.Seed;
import de.featjar.util.cli.CLIFunction;
import de.featjar.util.io.IO;
import de.featjar.util.io.csv.CSVWriter;
import de.featjar.util.io.namelist.NameListFormat;
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
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * TODO documentation
 *
 * @author Sebastian Krieter
 */
public abstract class Evaluator implements CLIFunction {

    private static final String DATE_FORMAT_STRING = "yyyy-MM-dd_HH-mm-ss";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT_STRING);

    private static final String DEFAULT_RESOURCE_DIRECTORY = "";
    private static final String DEFAULT_MODELS_DIRECTORY = "models";
    private static final String DEFAULT_CONFIG_DIRECTORY = "config";
    private static final String DEFAULT_OUTPUT_DIRECTORY = "output";

    protected static final List<Property<?>> propertyList = new LinkedList<>();

    public final Property<String> outputPathProperty =
            new Property<>("output", Property.StringConverter, DEFAULT_OUTPUT_DIRECTORY);
    public final Property<String> modelsPathProperty =
            new Property<>("models", Property.StringConverter, DEFAULT_MODELS_DIRECTORY);
    public final Property<String> resourcesPathProperty =
            new Property<>("resources", Property.StringConverter, DEFAULT_RESOURCE_DIRECTORY);

    public final ListProperty<String> phases = new ListProperty<>("phases", Property.StringConverter);
    public final Property<Integer> debug = new Property<>("debug", Property.IntegerConverter);
    public final Property<Integer> verbosity = new Property<>("verbosity", Property.IntegerConverter);
    public final Property<Long> timeout = new Property<>("timeout", Property.LongConverter, Long.MAX_VALUE);
    public final Seed randomSeed = new Seed();

    public final Property<Integer> systemIterations = new Property<>("systemIterations", Property.IntegerConverter, 1);
    public final Property<Integer> algorithmIterations =
            new Property<>("algorithmIterations", Property.IntegerConverter, 1);

    public Path configPath;
    public Path outputPath;
    public Path outputRootPath;
    public Path modelPath;
    public Path resourcePath;
    public Path csvPath;
    public Path tempPath;
    public Path logPath;
    public List<String> systemNames;
    public List<Integer> systemIDs;

    public final TabFormatter tabFormatter = new TabFormatter();

    public int systemIndex, systemIteration, systemIndexMax, systemIterationMax;

    public static void addProperty(Property<?> property) {
        propertyList.add(property);
    }

    public void readConfig(String name) throws Exception {
        readConfigFile(name);
    }

    public String getTimeStamp() {
        return DATE_FORMAT.format(new Timestamp(System.currentTimeMillis()));
    }

    public int getSystemID() {
        return systemIDs.get(systemIndex);
    }

    public String getSystemName() {
        return systemNames.get(systemIndex);
    }

    public String readCurrentOutputMarker() {
        final Path currentOutputMarkerFile = outputRootPath.resolve(".current");
        String currentOutputMarker = null;
        if (Files.isReadable(currentOutputMarkerFile)) {
            List<String> lines;
            try {
                lines = Files.readAllLines(currentOutputMarkerFile);

                if (!lines.isEmpty()) {
                    final String firstLine = lines.get(0);
                    currentOutputMarker = firstLine.trim();
                }
            } catch (final Exception e) {
                Logger.logError(e);
            }
        }

        try {
            Files.createDirectories(outputRootPath);
        } catch (final IOException e) {
            Logger.logError(e);
        }

        if (currentOutputMarker == null) {
            currentOutputMarker = getTimeStamp();
            try {
                Files.write(currentOutputMarkerFile, currentOutputMarker.getBytes());
            } catch (final IOException e) {
                Logger.logError(e);
            }
        }
        return currentOutputMarker;
    }

    public void readSystemNames() {
        final List<NameListFormat.NameEntry> names = IO.load(configPath.resolve("models.txt"), new NameListFormat())
                .orElse(Collections.emptyList(), Logger::logProblems);
        systemNames = new ArrayList<>(names.size());
        systemIDs = new ArrayList<>(names.size());

        for (final NameListFormat.NameEntry nameEntry : names) {
            systemNames.add(nameEntry.getName());
            systemIDs.add(nameEntry.getID());
        }
    }

    private Properties readConfigFile(String configName) throws Exception {
        final Path path = configPath.resolve(configName + ".properties");
        System.out.print("Reading config file. (" + path.toString() + ") ... ");
        final Properties properties = new Properties();
        try {
            properties.load(Files.newInputStream(path));
            for (final Property<?> prop : propertyList) {
                final String value = properties.getProperty(prop.getKey());
                if (value != null) {
                    prop.setValue(value);
                }
            }
            System.out.println("Success!");
            return properties;
        } catch (final IOException e) {
            System.out.println("Fail! -> " + e.getMessage());
            System.err.println(e);
            throw e;
        }
    }

    @Override
    public void run(List<String> args) {
        if (args.size() < 1) {
            System.out.println("Configuration path not specified!");
            return;
        }
        Logger.logInfo(System.getProperty("user.dir"));
        try {
            init(args.get(0), args.size() > 1 ? args.get(1) : DEFAULT_CONFIG_DIRECTORY);
            phaseLoop:
            for (String phase : phases.getValue()) {
                for (EvaluationPhase phaseExtension :
                        EvaluationPhaseExtensionPoint.getInstance().getExtensions()) {
                    if (phaseExtension.getName().equals(phase)) {
                        updateSubPaths();
                        Logger.logInfo("Running " + phaseExtension.getName());
                        printConfigFile();
                        phaseExtension.run(this);
                        continue phaseLoop;
                    }
                }
                Logger.logError("Phase \"" + phase + "\" not found.");
            }
        } catch (final Exception e) {
            Logger.logError(e);
        } finally {
            dispose();
        }
    }

    public void init(String configPath, String configName) throws Exception {
        this.configPath = Paths.get(configPath);
        readConfig(configName);
        initRootPaths();
        readSystemNames();
        initConstants();
        Logger.logInfo("Running " + this.getClass().getSimpleName());
    }

    private void updateSubPaths() throws IOException {
        initSubPaths();
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
    }

    protected void initRootPaths() {
        outputRootPath = Paths.get(outputPathProperty.getValue());
        resourcePath = Paths.get(resourcesPathProperty.getValue());
        modelPath = resourcePath.resolve(modelsPathProperty.getValue());
    }

    protected void initSubPaths() {
        outputPath = outputRootPath.resolve(readCurrentOutputMarker());
        csvPath = outputPath.resolve("data");
        tempPath = outputPath.resolve("temp");
        logPath = outputPath.resolve("log-" + getTimeStamp());
    }

    protected void initConstants() {
        systemIterationMax = systemIterations.getValue();
        systemIndexMax = systemNames.size();
    }

    protected void setupDirectories() throws IOException {
        try {
            createDir(outputPath);
            createDir(csvPath);
            createDir(tempPath);
            createDir(logPath);
        } catch (final IOException e) {
            Logger.logError("Could not create output directory.");
            Logger.logError(e);
            throw e;
        }
    }

    private void installLogger() throws FileNotFoundException {
        Logger.uninstall();
        Logger.setErrLog(Logger.LogType.ERROR);
        switch (verbosity.getValue()) {
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
        if (logPath != null) {
            final Path outLogFile = logPath.resolve("output.log");
            Logger.addFileLog(outLogFile, Logger.LogType.INFO, Logger.LogType.DEBUG);
            final Path errLogFile = logPath.resolve("error.log");
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

    public void dispose() {
        Logger.uninstall();
        deleteTempFolder();
    }

    private void deleteTempFolder() {
        try {
            Files.walkFileTree(tempPath, new SimpleFileVisitor<Path>() {
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

    private void printConfigFile() {
        StringBuilder sb = new StringBuilder();
        sb.append("Current Configuration: ");
        for (final Property<?> prop : propertyList) {
            sb.append("\n\t");
            sb.append(prop.toString());
        }
        Logger.logInfo(sb.toString());
    }

    public void logSystem() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Processing System: ");
        sb.append(systemNames.get(systemIndex));
        sb.append(" (");
        sb.append(systemIndex + 1);
        sb.append("/");
        sb.append(systemNames.size());
        sb.append(")");
        Logger.logInfo(sb.toString());
    }

    public void logSystemRun() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Processing System: ");
        sb.append(systemNames.get(systemIndex));
        sb.append(" (");
        sb.append(systemIndex + 1);
        sb.append("/");
        sb.append(systemNames.size());
        sb.append(") - ");
        sb.append(systemIteration);
        sb.append("/");
        sb.append(systemIterations.getValue());
        Logger.logInfo(sb.toString());
    }

    public CSVWriter addCSVWriter(String fileName, String... csvHeader) {
        final CSVWriter csvWriter = new CSVWriter();
        try {
            csvWriter.setOutputDirectory(csvPath);
            csvWriter.setFileName(fileName);
        } catch (final IOException e) {
            Logger.logError(e);
            return null;
        }
        csvWriter.setAppend(true);
        csvWriter.setHeader(csvHeader);
        csvWriter.flush();
        return csvWriter;
    }
}
