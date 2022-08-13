/*
 * Copyright (C) 2022 Sebastian Krieter
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
package de.featjar.evaluation.properties;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class ListProperty<T> extends Property<List<T>> {

    public ListProperty(String name, Function<String, T> converter) {
        super(name, parseList(converter), Collections.emptyList());
    }

    public ListProperty(String name, Function<String, T> converter, T defaultValue) {
        super(name, parseList(converter), Arrays.asList(defaultValue));
    }
}
