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
package org.spldev.evaluation;

import java.nio.file.*;

public class OutputCleaner extends Evaluator {

	public OutputCleaner(String configPath) throws Exception {
		super(configPath, null);
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			System.out.println("Configuration path not specified!");
		}
		final OutputCleaner evaluator = new OutputCleaner(args[0]);
		Files.deleteIfExists(evaluator.config.outputRootPath.resolve(".current"));
		System.out.println("Reset current output path.");
	}

}
