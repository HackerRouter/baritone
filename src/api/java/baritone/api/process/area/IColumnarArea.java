/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.api.process.area;

import net.minecraft.core.BlockPos;

/**
 * A mining volume with an arbitrary XZ footprint and an inclusive, regular Y range.
 * Implementations must be immutable while a mining task is active.
 */
public interface IColumnarArea {

    int minX();

    int maxX();

    int minY();

    int maxY();

    int minZ();

    int maxZ();

    boolean containsXZ(int x, int z);

    default boolean contains(int x, int y, int z) {
        return y >= minY() && y <= maxY() && containsXZ(x, z);
    }

    default boolean contains(BlockPos pos) {
        return contains(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Returns the bounding-box volume. This is an upper bound for irregular footprints.
     */
    default long estimatedBlockCount() {
        return ((long) maxX() - minX() + 1L)
                * ((long) maxY() - minY() + 1L)
                * ((long) maxZ() - minZ() + 1L);
    }
}
