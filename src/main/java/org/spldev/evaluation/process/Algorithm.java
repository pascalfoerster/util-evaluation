/* -----------------------------------------------------------------------------
 * evaluation - Utilities for reproducible evaluations
 * Copyright (C) 2021 Sebastian Krieter
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
 * -----------------------------------------------------------------------------
 */
package org.spldev.evaluation.process;

import java.io.*;
import java.util.*;

import org.spldev.evaluation.streams.*;

public abstract class Algorithm<R> implements IOutputReader {

	protected int iterations = -1;

	protected final ArrayList<String> commandElements = new ArrayList<>();

	public abstract void postProcess() throws Exception;

	public abstract R parseResults() throws IOException;

	@Override
	public void readOutput(String line) throws Exception {
	}

	public abstract String getName();

	public abstract String getParameterSettings();

	public void preProcess() throws Exception {
		commandElements.clear();
		addCommandElements();
	}

	protected abstract void addCommandElements() throws Exception;

	public void addCommandElement(String parameter) {
		commandElements.add(parameter);
	}

	public List<String> getCommandElements() {
		return commandElements;
	}

	public String getCommand() {
		final StringBuilder commandBuilder = new StringBuilder();
		for (final String commandElement : commandElements) {
			commandBuilder.append(commandElement);
			commandBuilder.append(' ');
		}
		return commandBuilder.toString();
	}

	public String getFullName() {
		return getName() + "_" + getParameterSettings();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if ((obj == null) || (getClass() != obj.getClass())) {
			return false;
		}
		final Algorithm<?> other = (Algorithm<?>) obj;
		return Objects.equals(getFullName(), other.getFullName());
	}

	@Override
	public int hashCode() {
		return getFullName().hashCode();
	}

	@Override
	public String toString() {
		return getFullName();
	}

	public int getIterations() {
		return iterations;
	}

	public void setIterations(int iterations) {
		this.iterations = iterations;
	}

}
