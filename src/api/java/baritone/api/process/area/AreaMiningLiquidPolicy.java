/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.api.process.area;

public enum AreaMiningLiquidPolicy {
    /** Leave liquid cells alone and retain Baritone's conservative adjacent-liquid checks. */
    AVOID,
    /** Replace liquid cells inside the target with throwaway blocks before clearing them. */
    REPLACE,
    /** Seal liquid cells touching the side or top boundary, then clear liquid cells inside. */
    SEAL_BOUNDARY
}
