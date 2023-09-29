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

/**
 * @author Sebastian Krieter
 */
public class ProgressTimer {

    private boolean running = false;
    private boolean verbose = true;

    private long startTime;

    private long curTime = 0;

    private long lastTime = -1;

    private static long getTime() {
        return System.nanoTime();
    }

    public void start() {
        if (!running) {
            startTime = getTime();
            curTime = startTime;
            running = true;
        }
    }

    public long stop() {
        if (running) {
            lastTime = getTime() - startTime;

            printTime();

            running = false;
        }
        return lastTime;
    }

    public long split() {
        final long startTime = curTime;
        curTime = getTime();

        lastTime = curTime - startTime;

        printTime();

        return lastTime;
    }

    private void printTime() {
        if (verbose) {
            final double timeDiff = (lastTime / 1_0000_00L) / 1_000.0;
            FeatJAR.log().info("Time: " + timeDiff + "s");
        }
    }

    public final boolean isRunning() {
        return running;
    }

    public long getLastTime() {
        return lastTime;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}
