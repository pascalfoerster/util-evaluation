/*
 * Copyright (C) 2023 FeatJAR-Development-Team
 *
 * This file is part of FeatJAR-evaluation.
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
 * See <https://github.com/FeatJAR> for further information.
 */
package de.featjar.evaluation;

import de.featjar.base.FeatJAR;
import de.featjar.base.cli.ICommand;
import de.featjar.base.cli.ListOption;
import de.featjar.base.cli.Option;
import de.featjar.base.cli.OptionList;
import de.featjar.base.cli.RangeOption;
import de.featjar.base.io.csv.CSVFile;
import de.featjar.evaluation.util.OptionCombiner;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * TODO documentation
 *
 * @author Sebastian Krieter
 */
public abstract class Evaluator implements ICommand {

    private static final String DATE_FORMAT_STRING = "yyyy-MM-dd_HH-mm-ss";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT_STRING);

    public String getTimeStamp() {
        return DATE_FORMAT.format(new Timestamp(System.currentTimeMillis()));
    }

    public static final Option<Path> modelsPathOption = new Option<>("models", Option.PathParser)
            .setDefaultValue(Path.of("models"))
            .setValidator(Option.PathValidator);
    public static final Option<Path> resourcesPathOption = new Option<>("resources", Option.PathParser)
            .setDefaultValue(Path.of("resources"))
            .setValidator(Option.PathValidator);

    public static final Option<Long> timeout = new Option<>("timeout", Option.LongParser, Long.MAX_VALUE);
    public static final Option<Long> randomSeed = new Option<>("seed", Option.LongParser);

    public static final ListOption<String> systemsOption = new ListOption<>("systems", Option.StringParser);
    public static final RangeOption systemIterationsOption = new RangeOption("systemIterations");
    public static final RangeOption algorithmIterationsOption = new RangeOption("algorithmIterations");

    public OptionList optionParser;
    public OptionCombiner optionCombiner;

    public Path outputPath;
    public Path outputRootPath;
    public Path modelPath;
    public Path resourcePath;
    public Path csvPath;
    public Path genPath;
    public Path tempPath;
    public List<String> systemNames;
    public List<Integer> systemIDs;

    @Override
    public List<Option<?>> getOptions() {
        return List.of(
                INPUT_OPTION,
                OUTPUT_OPTION,
                modelsPathOption,
                resourcesPathOption,
                timeout,
                randomSeed,
                systemsOption,
                systemIterationsOption,
                algorithmIterationsOption);
    }

    public OptionList getOptionParser() {
        return optionParser;
    }

    public <T> T getOption(Option<T> option) {
        return optionParser.getResult(option).orElseThrow();
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
                FeatJAR.log().error(e);
            }
        }

        try {
            Files.createDirectories(outputRootPath);
        } catch (final IOException e) {
            FeatJAR.log().error(e);
        }

        if (currentOutputMarker == null) {
            currentOutputMarker = getTimeStamp();
            try {
                Files.write(currentOutputMarkerFile, currentOutputMarker.getBytes());
            } catch (final IOException e) {
                FeatJAR.log().error(e);
            }
        }
        return currentOutputMarker;
    }

    protected abstract void runEvaluation() throws Exception;

    @Override
    public void run(OptionList optionParser) {
        this.optionParser = optionParser;
        this.optionCombiner = new OptionCombiner(optionParser);
        try {
            init();

            updateSubPaths();

            FeatJAR.log().info("Running " + getIdentifier());
            Properties properties = new Properties();
            for (final Option<?> opt : getOptions()) {
                String name = opt.getName();
                String value = String.valueOf(optionParser.getResult(opt).orElse(null));
                String isDefaultValue = optionParser.has(opt) ? "" : " (default)";
                properties.put(name, value);
                FeatJAR.log().info("%s: %s%s", name, value, isDefaultValue);
            }
            properties.store(Files.newOutputStream(csvPath.resolve("config.properties")), null);

            runEvaluation();
        } catch (final Exception e) {
            FeatJAR.log().error(e);
        } finally {
            dispose();
        }
    }

    public void init() throws Exception {
        outputRootPath = optionParser.getResult(OUTPUT_OPTION).get();
        resourcePath = optionParser.getResult(resourcesPathOption).get();
        modelPath = optionParser.getResult(modelsPathOption).get();
        systemNames = Files.list(modelPath)
                .map(p -> p.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
        FeatJAR.log().info("Running " + this.getClass().getSimpleName());
    }

    private void updateSubPaths() throws IOException {
        initSubPaths();
        try {
            setupDirectories();
        } catch (final IOException e) {
            FeatJAR.log().error("Fail -> Could not create output directory.");
            FeatJAR.log().error(e);
            throw e;
        }
    }

    protected void initRootPaths() {}

    protected void initSubPaths() {
        outputPath = outputRootPath.resolve(readCurrentOutputMarker());
        csvPath = outputPath.resolve("data").resolve("data-" + getTimeStamp());
        tempPath = outputPath.resolve("temp");
        genPath = outputPath.resolve("gen");
    }

    protected void setupDirectories() throws IOException {
        try {
            createDir(outputPath);
            createDir(csvPath);
            createDir(genPath);
            createDir(tempPath);
        } catch (final IOException e) {
            FeatJAR.log().error("Could not create output directory.");
            FeatJAR.log().error(e);
            throw e;
        }
    }

    private void createDir(final Path path) throws IOException {
        if (path != null) {
            Files.createDirectories(path);
        }
    }

    public void dispose() {
        deleteTempFolder();
    }

    private void deleteTempFolder() {
        if (tempPath != null) {
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
    }

    public CSVFile addCSVWriter(String fileName, String... csvHeader) throws IOException {
        long count = Files.walk(csvPath)
                .filter(p -> p.getFileName().toString().matches(Pattern.quote(fileName) + "(-\\d+)?[.]csv"))
                .count();
        final Path csvFilePath = csvPath.resolve(fileName + "-" + count + ".csv");
        final CSVFile csvWriter = new CSVFile(csvFilePath);
        csvWriter.setHeaderFields(csvHeader);
        csvWriter.flush();
        return csvWriter;
    }
}
