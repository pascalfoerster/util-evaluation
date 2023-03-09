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
package de.featjar.evaluation.properties;

import de.featjar.evaluation.Evaluator;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Property<T> {

    public static final Function<String, Boolean> BooleanConverter = Boolean::parseBoolean;
    public static final Function<String, Integer> IntegerConverter = Integer::parseInt;
    public static final Function<String, Double> DoubleConverter = Double::parseDouble;
    public static final Function<String, Long> LongConverter = Long::parseLong;
    public static final Function<String, String> StringConverter = String::toString;

    private final Function<String, T> converter;
    private final String name;
    private final T defaultValue;
    private T value;

    protected static <E> Function<String, List<E>> parseList(Function<String, E> elementConverter) {
        return valueString -> Arrays.stream(valueString.split(",")) //
                .map(elementConverter) //
                .collect(Collectors.toList());
    }

    public Property(String name, Function<String, T> converter) {
        this(name, converter, null);
    }

    public Property(String name, Function<String, T> converter, T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.converter = converter;
        Evaluator.addProperty(this);
    }

    public T getValue() {
        return (value != null) ? value : defaultValue;
    }

    public String getKey() {
        return name;
    }

    protected T getDefaultValue() {
        return defaultValue;
    }

    public boolean setValue(String valueString) {
        if (valueString != null) {
            try {
                value = cast(valueString);
                return true;
            } catch (final Exception e) {
            }
        }
        return false;
    }

    protected T cast(String valueString) throws Exception {
        return converter.apply(valueString);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append(" = ");
        if (value != null) {
            sb.append(value.toString());
        } else if (defaultValue != null) {
            sb.append(defaultValue.toString());
            sb.append(" (default value)");
        } else {
            sb.append("null");
        }
        return sb.toString();
    }
}
