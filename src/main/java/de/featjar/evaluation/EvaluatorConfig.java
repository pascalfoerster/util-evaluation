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
package de.featjar.evaluation;

import de.featjar.evaluation.properties.Property;
import de.featjar.evaluation.properties.Seed;
import de.featjar.util.io.IO;
import de.featjar.util.io.list.StringListFormat;
import de.featjar.util.log.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * @author Sebastian Krieter
 */
public class EvaluatorConfig {

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

    public final Property<Boolean> append = new Property<>("append", Property.BooleanConverter); // todo remove
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

    public static void addProperty(Property<?> property) {
        propertyList.add(property);
    }

    public EvaluatorConfig() {
        configPath = Paths.get(DEFAULT_CONFIG_DIRECTORY);
    }

    public EvaluatorConfig(String configPath) {
        this.configPath = Paths.get(configPath);
    }

    public void readConfig(String name) throws Exception {
        readConfigFile("paths");
        if (name != null) {
            readConfigFile(name);
        }
    }

    public void setup() {
        initOutputPath();
        readSystemNames();
    }

    public String getTimeStamp() {
        return DATE_FORMAT.format(new Timestamp(System.currentTimeMillis()));
    }

    public void initOutputPath() {
        final String currentOutputMarker = readCurrentOutputMarker();
        outputPath = outputRootPath.resolve(currentOutputMarker);
        csvPath = outputPath.resolve("data");
        tempPath = outputPath.resolve("temp");
        logPath = outputPath.resolve("log-" + System.currentTimeMillis());
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
        systemNames = IO.load(configPath.resolve("models.txt"), new StringListFormat())
                .orElse(Collections.emptyList(), Logger::logProblems);
    }

    private Properties readConfigFile(String configName) throws Exception {
        final Path path = configPath.resolve(configName + ".properties");
        Logger.logInfo("Reading config file. (" + path.toString() + ") ... ");
        final Properties properties = new Properties();
        try {
            properties.load(Files.newInputStream(path));
            for (final Property<?> prop : propertyList) {
                final String value = properties.getProperty(prop.getKey());
                if (value != null) {
                    prop.setValue(value);
                }
            }
            Logger.logInfo("Success!");
            return properties;
        } catch (final IOException e) {
            Logger.logInfo("Fail! -> " + e.getMessage());
            Logger.logError(e);
            throw e;
        }
    }
}
