/* -----------------------------------------------------------------------------
 * Evaluation Lib - Miscellaneous functions for performing an evaluation.
 * Copyright (C) 2021  Sebastian Krieter
 * 
 * This file is part of Evaluation Lib.
 * 
 * Evaluation Lib is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * Evaluation Lib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Evaluation Lib.  If not, see <https://www.gnu.org/licenses/>.
 * 
 * See <https://github.com/skrieter/evaluation> for further information.
 * -----------------------------------------------------------------------------
 */
package org.spldev.evaluation.util;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.file.DirectoryStream.*;
import java.nio.file.FileSystem;
import java.util.*;

import org.spldev.util.*;
import org.spldev.util.io.*;
import org.spldev.util.io.format.*;
import org.spldev.util.logging.*;

/**
 * @author Sebastian Krieter
 */
public class ModelReader<T> {

	private String defaultFileName = "model.xml";
	private Path pathToFiles;
	private FormatSupplier<T> formatSupplier;

	public final Result<T> read(final String name) {
		Result<T> fm = null;

		fm = readFromFolder(pathToFiles, name);
		if (fm.isPresent()) {
			return fm;
		}

		fm = readFromFile(pathToFiles, name);
		if (fm.isPresent()) {
			return fm;
		}

		fm = readFromZip(pathToFiles, name);

		return fm;
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
		return FileHandler.load(path, formatSupplier);
	}

	public Result<T> readFromFolder(final Path rootPath, final String name) {
		final Path modelFolder = rootPath.resolve(name);
		Logger.logInfo("Trying to load from folder " + modelFolder);
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
		Logger.logInfo("Trying to load from file " + name);
		Result<T> loadedFm = loadFile(rootPath.resolve(name));
		if (loadedFm.isPresent()) {
			return loadedFm;
		}
		final Filter<Path> fileFilter = file -> Files.isReadable(file) && Files.isRegularFile(file)
			&& file.getFileName().toString().matches("^" + name + "\\.\\w+$");
		try (DirectoryStream<Path> files = Files.newDirectoryStream(rootPath, fileFilter)) {
			final Iterator<Path> iterator = files.iterator();
			while (iterator.hasNext()) {
				final Path next = iterator.next();
				Logger.logInfo("Trying to load from file " + next);
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

	protected Result<T> readFromZip(final Path rootPath, final String name) {
		final Filter<Path> fileFilter = file -> Files.isReadable(file) && Files.isRegularFile(file)
			&& file.getFileName().toString().matches(".*[.]zip\\Z");
		try (DirectoryStream<Path> files = Files.newDirectoryStream(rootPath, fileFilter)) {
			for (final Path path : files) {
				Logger.logInfo("Trying to load from zip file " + path);
				final URI uri = URI.create("jar:" + path.toUri().toString());
				try (final FileSystem zipFs = FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap())) {
					for (final Path root : zipFs.getRootDirectories()) {
						Result<T> fm = readFromFolder(root, name);
						if (fm.isPresent()) {
							return fm;
						}
						fm = readFromFile(root, name);
						if (fm.isPresent()) {
							return fm;
						}
					}
				} catch (final IOException e) {
					Logger.logError(e);
				}
			}
		} catch (final IOException e) {
			Logger.logError(e);
		}
		return Result.empty();
	}

	public void dispose() {
		Logger.uninstall();
	}

}
