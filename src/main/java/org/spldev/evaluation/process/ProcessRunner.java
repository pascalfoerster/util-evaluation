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
package org.spldev.evaluation.process;

import java.util.*;
import java.util.concurrent.*;

import org.spldev.evaluation.streams.*;
import org.spldev.util.logging.*;

public class ProcessRunner {

	private long timeout = Long.MAX_VALUE;

	public <R> void run(Algorithm<R> algorithm, Result<R> result) {
		boolean terminatedInTime = false;
		boolean noError = false;
		long startTime = 0, endTime = 0;
		try {
			System.gc();
			algorithm.preProcess();

			Logger.logInfo(algorithm.getCommand());

			final List<String> command = algorithm.getCommandElements();
			if (!command.isEmpty()) {
				final ProcessBuilder processBuilder = new ProcessBuilder(command);
				Process process = null;

				final ErrStreamCollector errStreamCollector = new ErrStreamCollector();
				final StreamRedirector errRedirector = new StreamRedirector(
						Arrays.asList(new ErrStreamReader(), errStreamCollector));
				final StreamRedirector outRedirector = new StreamRedirector(
						Arrays.asList(new OutStreamReader(), algorithm));
				final Thread outThread = new Thread(outRedirector);
				final Thread errThread = new Thread(errRedirector);
				try {
					startTime = System.nanoTime();
					process = processBuilder.start();

					outRedirector.setInputStream(process.getInputStream());
					errRedirector.setInputStream(process.getErrorStream());
					outThread.start();
					errThread.start();

					terminatedInTime = process.waitFor(timeout, TimeUnit.MILLISECONDS);
					endTime = System.nanoTime();
					noError = errStreamCollector.getErrList().isEmpty();
					result.setTerminatedInTime(terminatedInTime);
					result.setNoError(noError);
					result.setTime((endTime - startTime) / 1_000_000L);
				} finally {
					if (process != null) {
						process.destroyForcibly();
					}
					Logger.logInfo("In time: " + terminatedInTime + ", no error: " + noError);
				}
			} else {
				result.setTerminatedInTime(false);
				result.setNoError(false);
				result.setTime(Result.INVALID_TIME);
				Logger.logInfo("Invalid command");
			}
		} catch (final Exception e) {
			Logger.logError(e);
			result.setTerminatedInTime(false);
			result.setNoError(false);
			result.setTime(Result.INVALID_TIME);
		}
		try {
			result.setResult(algorithm.parseResults());
		} catch (final Exception e) {
			Logger.logError(e);
			if (terminatedInTime) {
				result.setNoError(false);
			}
		}
		try {
			algorithm.postProcess();
		} catch (final Exception e) {
			Logger.logError(e);
		}
	}

	public long getTimeout() {
		return timeout;
	}

	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}
}
