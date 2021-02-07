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

import org.spldev.evaluation.EvaluatorConfig;

public abstract class AProperty<T> implements IProperty {

	private final String key;
	private final T defaultValue;
	private T value;

	public AProperty(String key) {
		this(key, null);
	}

	public AProperty(String key, T defaultValue) {
		this.key = key;
		this.defaultValue = defaultValue;
		EvaluatorConfig.addProperty(this);
	}

	@Override
	public T getValue() {
		return (value != null) ? value : defaultValue;
	}

	@Override
	public String getKey() {
		return key;
	}

	protected T getDefaultValue() {
		return defaultValue;
	}

	protected abstract T cast(String valueString) throws Exception;

	@Override
	public boolean setValue(String valueString) {
		if (valueString != null) {
			try {
				value = cast(valueString);
				return true;
			} catch (Exception e) {
			}
		}
		return false;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append(key);
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
