/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.api.process.area;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class AreaMiningOptions {

    public static final AreaMiningOptions DEFAULT = new AreaMiningOptions(
            AreaMiningLiquidPolicy.SEAL_BOUNDARY,
            List.of(Blocks.SLIME_BLOCK)
    );

    private final AreaMiningLiquidPolicy liquidPolicy;
    private final List<Block> sealingBlocks;

    public AreaMiningOptions(AreaMiningLiquidPolicy liquidPolicy) {
        this(liquidPolicy, List.of(Blocks.SLIME_BLOCK));
    }

    public AreaMiningOptions(AreaMiningLiquidPolicy liquidPolicy, Collection<Block> sealingBlocks) {
        this.liquidPolicy = Objects.requireNonNull(liquidPolicy, "liquidPolicy");
        Objects.requireNonNull(sealingBlocks, "sealingBlocks");
        if (sealingBlocks.isEmpty() || sealingBlocks.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("At least one non-null sealing block is required");
        }
        this.sealingBlocks = List.copyOf(sealingBlocks);
    }

    public AreaMiningLiquidPolicy liquidPolicy() {
        return liquidPolicy;
    }

    public List<Block> sealingBlocks() {
        return sealingBlocks;
    }
}
