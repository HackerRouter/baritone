/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.api.process.area;

public final class AreaMiningStatus {

    public enum State {
        IDLE,
        RUNNING,
        PAUSED,
        COMPLETE,
        CANCELLED
    }

    public enum PauseReason {
        NONE,
        MANUAL,
        NO_SEALING_BLOCKS,
        BLOCK_LIMIT_REACHED,
        PATHING_FAILED
    }

    private final State state;
    private final long estimatedTotalBlocks;
    private final long knownRemainingBlocks;
    private final PauseReason pauseReason;
    private final long minedBlocks;
    private final long blockLimit;

    public AreaMiningStatus(State state, long estimatedTotalBlocks, long knownRemainingBlocks) {
        this(state, estimatedTotalBlocks, knownRemainingBlocks, PauseReason.NONE, 0L, Long.MAX_VALUE);
    }

    public AreaMiningStatus(State state, long estimatedTotalBlocks, long knownRemainingBlocks, PauseReason pauseReason) {
        this(state, estimatedTotalBlocks, knownRemainingBlocks, pauseReason, 0L, Long.MAX_VALUE);
    }

    public AreaMiningStatus(State state, long estimatedTotalBlocks, long knownRemainingBlocks, PauseReason pauseReason, long minedBlocks, long blockLimit) {
        this.state = state;
        this.estimatedTotalBlocks = estimatedTotalBlocks;
        this.knownRemainingBlocks = knownRemainingBlocks;
        this.pauseReason = pauseReason;
        this.minedBlocks = minedBlocks;
        this.blockLimit = blockLimit;
    }

    public State state() {
        return state;
    }

    public long estimatedTotalBlocks() {
        return estimatedTotalBlocks;
    }

    /**
     * Returns the currently known remaining target count, or {@code -1} before the first scan.
     */
    public long knownRemainingBlocks() {
        return knownRemainingBlocks;
    }

    public PauseReason pauseReason() {
        return pauseReason;
    }

    public long minedBlocks() {
        return minedBlocks;
    }

    public long blockLimit() {
        return blockLimit;
    }
}
