/*
 * Copyright (C) 2023 FeatJAR-Development-Team
 *
 * This file is part of FeatJAR-evaluation.
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
 * See <https://github.com/FeatJAR> for further information.
 */
package de.featjar.evaluation;

import de.featjar.base.FeatJAR;
import de.featjar.base.extension.AExtensionPoint;

/**
 * TODO documentation
 *
 * @author Sebastian Krieter
 */
public class EvaluationPhaseExtensionPoint extends AExtensionPoint<EvaluationPhase<?>> {
    public static EvaluationPhaseExtensionPoint getInstance() {
        return FeatJAR.extensionPoint(EvaluationPhaseExtensionPoint.class);
    }
}