/* -----------------------------------------------------------------------------
 * Evaluation-Lib - Miscellaneous functions for performing an evaluation.
 * Copyright (C) 2021  Sebastian Krieter
 * 
 * This file is part of Evaluation-Lib.
 * 
 * Evaluation-Lib is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * Evaluation-Lib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Evaluation-Lib.  If not, see <https://www.gnu.org/licenses/>.
 * 
 * See <https://github.com/skrieter/evaluation> for further information.
 * -----------------------------------------------------------------------------
 */
package org.spldev.evaluation.properties;

public class DoubleProperty extends AProperty<Double> {

	public DoubleProperty(String name) {
		super(name, 0.0);
	}

	public DoubleProperty(String name, Double defaultValue) {
		super(name, defaultValue);
	}

	@Override
	protected Double cast(String valueString) throws Exception {
		return Double.parseDouble(valueString);
	}

}
