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
package de.featjar.evaluation.util;

import de.featjar.base.FeatJAR;
import de.featjar.base.cli.AListOption;
import de.featjar.base.cli.ListOption;
import de.featjar.base.cli.OptionList;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Iterates over a list of {@link ListOption list options}.
 *
 * @author Sebastian Krieter
 */
public class OptionCombiner {

    private OptionList optionParser;
    private AListOption<?>[] options;
    private ProgressTracker progress;

    public final <T extends OptionCombiner> void loopOverOptions(Consumer<Integer> forEachOption) {
        Objects.requireNonNull(progress, () -> "Call init method first!");
        FeatJAR.log().info("Start");
        String string = printOptionNames(options);
        FeatJAR.log().info(string);

        while (progress.hasNext()) {
            FeatJAR.log().info(progress::nextAndPrint);
            forEachOption.accept(progress.getLastChanged());
        }
    }

    private String printOptionNames(AListOption<?>... loptions) {
        StringBuilder optionMessage = new StringBuilder();
        int[] sizes = progress.getSizes();
        for (int i = 0; i < sizes.length; i++) {
            optionMessage.append(loptions[i].getName());
            optionMessage.append(String.format("(%d) ", sizes[i]));
        }
        return optionMessage.toString();
    }

    @SuppressWarnings("unchecked")
    public <T> T getValue(int index) {
        int optionIndex = progress.getIndices()[index];
        return optionIndex < 0
                ? null
                : (T) optionParser.get(options[index]).orElseThrow().get(optionIndex);
    }

    public OptionCombiner(OptionList parser) {
        this.optionParser = parser;
    }

    public void init(AListOption<?>... options) {
        this.options = options;

        int[] sizes = new int[options.length];
        for (int i = 0; i < options.length; i++) {
            int size = optionParser.get(options[i]).orElseThrow().size();
            if (size <= 0) {
                throw new IllegalArgumentException(
                        String.format("Option list must not be empty. Option: %s", options[i].getName()));
            }
            sizes[i] = size;
        }
        progress = new ProgressTracker(sizes);
    }
}
