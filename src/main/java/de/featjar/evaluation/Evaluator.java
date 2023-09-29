/*
 * Copyright (C) 2023 Sebastian Krieter
 *
 * This file is part of FeatJAR-util-evaluation.
 *
 * util-evaluation is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * util-evaluation is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with util-evaluation. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatureIDE/FeatJAR-evaluation> for further information.
 */
package de.featjar.evaluation;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import de.featjar.base.FeatJAR;
import de.featjar.base.cli.ICommand;
import de.featjar.base.cli.IOptionInput;
import de.featjar.base.cli.ListOption;
import de.featjar.base.cli.Option;
import de.featjar.base.io.csv.CSVFile;

/**
 * TODO documentation
 *
 * @author Sebastian Krieter
 */
public abstract class Evaluator implements ICommand {
    
	public final Option<Path> modelsPathProperty =
            new Option<>("models", Option.PathParser)
            .setDefaultValue(Path.of("models"))
            .setValidator(Option.PathValidator);
    public final Option<Path> resourcesPathProperty =
            new Option<>("resources", Option.PathParser)
            .setDefaultValue(Path.of("resources"))
            .setValidator(Option.PathValidator);

    public final ListOption<String> phases = new ListOption<>("phases", Option.StringParser);
    public final Option<Long> timeout = new Option<>("timeout", Option.LongParser, Long.MAX_VALUE);
    public final Option<Long> randomSeed = new Option<>("seed", Option.LongParser);

    public final Option<Integer> systemIterations = new Option<>("systemIterations", Option.IntegerParser, 1);
    public final Option<Integer> algorithmIterations = new Option<>("algorithmIterations", Option.IntegerParser, 1);

    @Override
    public List<Option<?>> getOptions() {
        return List.of(INPUT_OPTION, OUTPUT_OPTION, modelsPathProperty,
        		resourcesPathProperty,
        		phases,
        		timeout,
        		randomSeed,
        		systemIterations,
        		algorithmIterations);
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

    public int systemIndex, systemIteration, systemIndexMax, systemIterationMax;

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
	public void run(IOptionInput optionParser) {
        try {
            init(optionParser);
            phaseLoop:
            for (String phase : optionParser.get(phases).get()){
                for (EvaluationPhase phaseExtension :
                        EvaluationPhaseExtensionPoint.getInstance().getExtensions()) {
                    if (phaseExtension.getName().equals(phase)) {
                        updateSubPaths();
                        FeatJAR.log().info("Running " + phaseExtension.getName());
                        printConfigFile();
                        phaseExtension.run(this);
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

    public void init(IOptionInput optionInput) throws Exception {
        outputRootPath = optionInput.get(OUTPUT_OPTION).get();
        resourcePath = optionInput.get(resourcesPathProperty).get();
        modelPath = optionInput.get(modelsPathProperty).get();
        readSystemNames();
        systemIterationMax = optionInput.get(systemIterations).get();
        systemIndexMax = systemNames.size();
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

    protected void initRootPaths() {
    }

    protected void initSubPaths() {
        outputPath = outputRootPath.resolve(readCurrentOutputMarker());
        csvPath = outputPath.resolve("data");
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
        sb.append(systemIterationMax);
        FeatJAR.log().info(sb.toString());
    }

    protected final void writeCSV(CSVFile writer, Consumer<CSVFile> writing) {
        writer.newLine();
        try {
        	writing.accept(writer);
        	writer.flush();
        } catch (Exception e) {
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
