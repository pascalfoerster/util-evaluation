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
import de.featjar.base.cli.AListOption;
import de.featjar.base.cli.ICommand;
import de.featjar.base.cli.ListOption;
import de.featjar.base.cli.Option;
import de.featjar.base.cli.OptionList;
import de.featjar.base.cli.RangeOption;
import de.featjar.base.io.csv.CSVFile;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * TODO documentation
 *
 * @author Sebastian Krieter
 */
public abstract class Evaluator implements ICommand {

    public static final Option<Path> modelsPathProperty = new Option<>("models", Option.PathParser)
            .setDefaultValue(Path.of("models"))
            .setValidator(Option.PathValidator);
    public static final Option<Path> resourcesPathProperty = new Option<>("resources", Option.PathParser)
            .setDefaultValue(Path.of("resources"))
            .setValidator(Option.PathValidator);

    public static final ListOption<String> phases = new ListOption<>("phases", Option.StringParser);
    public static final Option<Long> timeout = new Option<>("timeout", Option.LongParser, Long.MAX_VALUE);
    public static final Option<Long> randomSeed = new Option<>("seed", Option.LongParser);

    public static final ListOption<String> systemNamesOption = new ListOption<>("systemNames", Option.StringParser);
    public static final ListOption<String> systemsOption = new ListOption<>("systems", Option.StringParser);
    public static final RangeOption systemIterationsOption = new RangeOption("systemIterations");
    public static final RangeOption algorithmIterationsOption = new RangeOption("algorithmIterations");

    public OptionList optionParser;
    private AListOption<?>[] loptions;
    public ArrayList<int[]> optionIndicesList;
    public int[] optionIndices;

    @Override
    public List<Option<?>> getOptions() {
        return List.of(
                INPUT_OPTION,
                OUTPUT_OPTION,
                modelsPathProperty,
                resourcesPathProperty,
                phases,
                timeout,
                randomSeed,
                systemNamesOption,
                systemsOption,
                systemIterationsOption,
                algorithmIterationsOption);
    }

    public OptionList getOptionParser() {
        return optionParser;
    }

    public <T> T getOption(Option<T> option) {
        return optionParser.get(option).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    public <T extends Evaluator> void optionLoop(EvaluationPhase<T> phase, AListOption<?>... loptions) {
        this.loptions = loptions;
        optionIndicesList = new ArrayList<>();
        int[] values = new int[loptions.length + 1];
        Arrays.fill(values, -1);
        cross(0, values, 0);
        FeatJAR.log().info("Start");
        optionIndicesList.stream()
                .peek(o -> {
                    optionIndices = o;
                    FeatJAR.log().info(Arrays.toString(o));
                })
                .forEach(l -> phase.optionLoop((T) this, l[l.length - 1]));
    }

    @SuppressWarnings("unchecked")
    public <T> T cast(int index) {
        int optionIndex = optionIndices[index];
        return optionIndex < 0
                ? null
                : (T) optionParser.get(loptions[index]).orElseThrow().get(optionIndex);
    }

    public void run(Consumer<int[]> runner) {
        FeatJAR.log().info("Start");
        optionIndicesList.stream()
                .peek(o -> {
                    optionIndices = o;
                    FeatJAR.log().info(Arrays.toString(o));
                })
                .forEach(runner);
    }

    private void cross(int optionIndex, int[] values, int lastChange) {
        if (optionIndex > values.length - 2) {
            values[values.length - 1] = lastChange;
            optionIndicesList.add(Arrays.copyOf(values, values.length));
        } else {
            int size = optionParser.get(loptions[optionIndex]).orElseThrow().size();
            if (size > 0) {
                values[optionIndex] = 0;
                cross(optionIndex + 1, values, lastChange);
                for (int i = 1; i < size; i++) {
                    values[optionIndex] = i;
                    cross(optionIndex + 1, values, optionIndex);
                }
            }
        }
    }

    private static final String DATE_FORMAT_STRING = "yyyy-MM-dd_HH-mm-ss";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT_STRING);

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

    public int systemIndex, systemIteration, algorithmIteration;

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

    public void readSystemNames() throws IOException {
        List<String> lines = Files.readAllLines(configPath.resolve("models.txt"));

        systemNames = new ArrayList<>(lines.size());
        systemIDs = new ArrayList<>(lines.size());

        int lineID = 1;
        for (final String line : lines) {
            if (line.matches("[^#\t].*")) {
                systemNames.add(line);
                systemIDs.add(lineID);
            }
            lineID++;
        }
    }

    @Override
    public void run(OptionList optionParser) {
        this.optionParser = optionParser;
        try {
            init();
            phaseLoop:
            for (String phase : optionParser.get(phases).get()) {
                printConfigFile();
                for (EvaluationPhase<?> phaseExtension :
                        EvaluationPhaseExtensionPoint.getInstance().getExtensions()) {
                    if (phaseExtension.getIdentifier().equals(phase)) {
                        runPhase(phaseExtension);
                        continue phaseLoop;
                    }
                }
                FeatJAR.log().error("Phase \"" + phase + "\" not found.");
            }
        } catch (final Exception e) {
            FeatJAR.log().error(e);
        } finally {
            dispose();
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Evaluator> void runPhase(EvaluationPhase<T> phaseExtension) throws IOException, Exception {
        updateSubPaths();
        FeatJAR.log().info("Running " + phaseExtension.getName());
        phaseExtension.run((T) this);
    }

    public void init() throws Exception {
        outputRootPath = optionParser.get(OUTPUT_OPTION).get();
        resourcePath = optionParser.get(resourcesPathProperty).get();
        modelPath = optionParser.get(modelsPathProperty).get();
        systemNames = optionParser.get(systemNamesOption).get();
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
        csvPath = outputPath.resolve("data-" + getTimeStamp());
        tempPath = outputPath.resolve("temp");
        logPath = outputPath.resolve("log-" + getTimeStamp());
    }

    protected void setupDirectories() throws IOException {
        try {
            createDir(outputPath);
            createDir(csvPath);
            createDir(tempPath);
            createDir(logPath);
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

    private void printConfigFile() {
        for (final Option<?> opt : getOptions()) {
            FeatJAR.log().info(opt.toString());
        }
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
        FeatJAR.log().info(sb.toString());
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
        //        sb.append(systemIterationMax);
        FeatJAR.log().info(sb.toString());
    }

    public final void writeCSV(CSVFile writer, Consumer<CSVFile> writing) {
        writer.newLine();
        try {
            writing.accept(writer);
            writer.flush();
        } catch (Exception e) {
            FeatJAR.log().error(e);
            writer.removeLastLine();
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
