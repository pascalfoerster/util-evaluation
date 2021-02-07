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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ListProperty<T> extends AProperty<List<T>> {

	protected final Function<String, T> converter;

	public ListProperty(String name, Function<String, T> converter) {
		super(name, Collections.emptyList());
		this.converter = converter;
	}

	public ListProperty(String name, Function<String, T> converter, T defaultValue) {
		super(name, Arrays.asList(defaultValue));
		this.converter = converter;
	}

	@Override
	protected List<T> cast(String valueString) throws Exception {
		return Arrays.stream(valueString.split(",")) //
				.map(converter) //
				.collect(Collectors.toList());
	}

}
