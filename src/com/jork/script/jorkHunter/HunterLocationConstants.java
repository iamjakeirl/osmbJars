package com.jork.script.jorkHunter;

import com.osmb.api.location.area.impl.RectangleArea;
import java.util.List;

/**
 * A central repository for all constants related to the JorkHunter script.
 * This includes definitions for hunting locations.
 */
public final class HunterLocationConstants {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private HunterLocationConstants() {}

    /**
     * Defines the Kourend Woodland bird snaring area.
     * Region ID: 6197
     */
    public static final HuntingLocation KOUREND_WOODLAND = new HuntingLocation(
        "Kourend Woodland",
        6197,
        List.of(new RectangleArea(1551, 3434, 4, 4, 0)) 
        //List.of(new RectangleArea(1533, 3435, 4, 3, 0)) - debug area no birds nearby to left
    );

    /**
     * Defines the Verdant Valley bird snaring area north of Piscatoris.
     * Region ID: 9526
     */
    public static final HuntingLocation VERDANT_VALLEY = new HuntingLocation(
            "Verdant Valley",
            9526,
            List.of(new RectangleArea(2375, 3495, 10, 10, 0))
    );

    // Add other hunting locations here in the future.
    // public static final HuntingLocation FELDIP_HILLS = new HuntingLocation(...);
} 