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
        PATHING_FAILED
    }

    private final State state;
    private final long estimatedTotalBlocks;
    private final long knownRemainingBlocks;
    private final PauseReason pauseReason;

    public AreaMiningStatus(State state, long estimatedTotalBlocks, long knownRemainingBlocks) {
        this(state, estimatedTotalBlocks, knownRemainingBlocks, PauseReason.NONE);
    }

    public AreaMiningStatus(State state, long estimatedTotalBlocks, long knownRemainingBlocks, PauseReason pauseReason) {
        this.state = state;
        this.estimatedTotalBlocks = estimatedTotalBlocks;
        this.knownRemainingBlocks = knownRemainingBlocks;
        this.pauseReason = pauseReason;
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
}
