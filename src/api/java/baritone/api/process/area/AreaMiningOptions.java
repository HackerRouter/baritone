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
            List.of(Blocks.NETHERRACK),
            Long.MAX_VALUE
    );

    private final AreaMiningLiquidPolicy liquidPolicy;
    private final List<Block> sealingBlocks;
    private final long blockLimit;

    public AreaMiningOptions(AreaMiningLiquidPolicy liquidPolicy) {
        this(liquidPolicy, List.of(Blocks.NETHERRACK), Long.MAX_VALUE);
    }

    public AreaMiningOptions(AreaMiningLiquidPolicy liquidPolicy, Collection<Block> sealingBlocks) {
        this(liquidPolicy, sealingBlocks, Long.MAX_VALUE);
    }

    public AreaMiningOptions(AreaMiningLiquidPolicy liquidPolicy, Collection<Block> sealingBlocks, long blockLimit) {
        this.liquidPolicy = Objects.requireNonNull(liquidPolicy, "liquidPolicy");
        Objects.requireNonNull(sealingBlocks, "sealingBlocks");
        if (sealingBlocks.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Sealing blocks cannot contain null");
        }
        if (blockLimit < 0L) {
            throw new IllegalArgumentException("Block limit cannot be negative");
        }
        this.sealingBlocks = List.copyOf(sealingBlocks);
        this.blockLimit = blockLimit;
    }

    public AreaMiningLiquidPolicy liquidPolicy() {
        return liquidPolicy;
    }

    public List<Block> sealingBlocks() {
        return sealingBlocks;
    }

    public long blockLimit() {
        return blockLimit;
    }
}
