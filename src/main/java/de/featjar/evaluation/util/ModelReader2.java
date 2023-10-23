/*
 * Copyright (C) 2023 Sebastian Krieter
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
package de.featjar.evaluation.util;

import de.featjar.util.data.Result;
import de.featjar.util.io.IO;
import de.featjar.util.io.format.FormatSupplier;
import de.featjar.util.logging.Logger;
import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;

/**
 * @author Sebastian Krieter
 */
public class ModelReader2<T> {

    private String defaultFileName = "model.xml";
    private Path pathToFiles;
    private FormatSupplier<T> formatSupplier;

    public final T read(final String name) {
        //		Path path = rootPath.resolve(name + ".zip");
        return readFromZip(pathToFiles, name);
    }

    public Path getPathToFiles() {
        return pathToFiles;
    }

    public void setPathToFiles(Path pathToFiles) {
        this.pathToFiles = pathToFiles;
    }

    public String getDefaultFileName() {
        return defaultFileName;
    }

    public void setDefaultFileName(String defaultFileName) {
        this.defaultFileName = defaultFileName;
    }

    public FormatSupplier<T> getFormatSupplier() {
        return formatSupplier;
    }

    public void setFormatSupplier(FormatSupplier<T> formatSupplier) {
        this.formatSupplier = formatSupplier;
    }

    public Result<T> loadFile(final Path path) {
        return IO.load(path, formatSupplier);
    }

    public Result<T> readFromFolder(final Path rootPath, final String name) {
        final Path modelFolder = rootPath.resolve(name);
        Logger.logDebug("Trying to load from folder " + modelFolder);
        if (Files.exists(modelFolder) && Files.isDirectory(modelFolder)) {
            final Path path = modelFolder.resolve(defaultFileName);
            if (Files.exists(path)) {
                return loadFile(path);
            } else {
                return readFromFile(modelFolder, "model");
            }
        } else {
            return Result.empty();
        }
    }

    public Result<T> readFromFile(final Path rootPath, final String name) {
        Logger.logDebug("Trying to load from file " + name);
        Result<T> loadedFm = loadFile(rootPath.resolve(name));
        if (loadedFm.isPresent()) {
            return loadedFm;
        } else {
            Logger.logDebug(loadedFm.getProblems().get(0));
        }
        final Filter<Path> fileFilter = file -> Files.isReadable(file)
                && Files.isRegularFile(file)
                && file.getFileName().toString().matches("^" + name + "\\.\\w+$");
        try (DirectoryStream<Path> files = Files.newDirectoryStream(rootPath, fileFilter)) {
            final Iterator<Path> iterator = files.iterator();
            while (iterator.hasNext()) {
                final Path next = iterator.next();
                Logger.logDebug("Trying to load from file " + next);
                loadedFm = loadFile(next);
                if (loadedFm.isPresent()) {
                    return loadedFm;
                }
            }
            return Result.empty();
        } catch (final IOException e) {
            Logger.logError(e);
        }
        return Result.empty();
    }

    protected T readFromZip(final Path rootPath, final String name) {
        Path path = rootPath.resolve(name + ".zip");
        Logger.logDebug("Trying to load from zip file " + path);
        final URI uri = URI.create("jar:" + path.toUri().toString());
        try (final FileSystem zipFs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
            for (final Path root : zipFs.getRootDirectories()) {
                Optional<Result<T>> loadedFm = Files.walk(root) //
                        .filter(Files::isRegularFile) //
                        .map(this::loadFile) //
                        .filter(Result::isPresent) //
                        .findFirst();
                if (loadedFm.isPresent()) {
                    return loadedFm.get().get();
                }
            }
        } catch (final IOException e) {
            Logger.logError(e);
        }
        return null;
    }
}