/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.api.process;

import baritone.api.process.area.AreaMiningOptions;
import baritone.api.process.area.AreaMiningStatus;
import baritone.api.process.area.IColumnarArea;

/**
 * Mines every non-air block in a columnar area.
 */
public interface IAreaMineProcess extends IBaritoneProcess {

    void mineArea(IColumnarArea area, AreaMiningOptions options);

    default void mineArea(IColumnarArea area) {
        mineArea(area, AreaMiningOptions.DEFAULT);
    }

    AreaMiningStatus getAreaMiningStatus();

    void pause();

    boolean isPaused();

    void resume();

    default void cancel() {
        onLostControl();
    }
}
