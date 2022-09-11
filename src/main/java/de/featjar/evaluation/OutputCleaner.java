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
 * See <https://github.com/FeatureIDE/FeatJAR-evaluation> for further information.
 */
package de.featjar.evaluation;

import java.io.IOException;
import java.nio.file.Files;

public class OutputCleaner extends Evaluator {

    @Override
    public void evaluate() throws IOException {
        Files.deleteIfExists(config.outputRootPath.resolve(".current"));
        Feat.log().info("Reset current output path.");
    }

    @Override
    public String getName() {
        return "eval-clean";
    }

    @Override
    public String getDescription() {
        return "Cleans current evaluation results";
    }
}
