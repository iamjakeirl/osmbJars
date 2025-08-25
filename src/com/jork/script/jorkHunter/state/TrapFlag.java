package com.jork.script.jorkHunter.state;

/**
 * Flags representing actions that need to be taken on a trap.
 * Priority is determined by ordinal position (lower ordinal = higher priority).
 */
public enum TrapFlag {
    URGENT_COLLAPSED,       // Priority 1 - Collapsed trap at risk of despawning (>90 seconds)
    NEEDS_REPOSITIONING,    // Priority 2 - Trap occluded by UI or needs to be moved
    NEEDS_INTERACTION,      // Priority 3 - Trap collapsed and needs to be reset
    READY_FOR_REMOVAL,      // Priority 4 - Trap has caught something and needs to be checked
    PENDING_VERIFICATION,   // Priority 5 - Trap state uncertain, needs verification
    LAYING_IN_PROGRESS;     // Priority 6 - Currently in the process of laying this trap
    
    /**
     * Get the priority of this flag (lower number = higher priority).
     */
    public int getPriority() {
        return ordinal();
    }
    
    /**
     * Check if this flag has higher priority than another flag.
     */
    public boolean hasHigherPriorityThan(TrapFlag other) {
        return this.ordinal() < other.ordinal();
    }
}