package com.jork.script.jorkHunter.interaction;

import com.jork.script.jorkHunter.JorkHunter;
import com.jork.script.jorkHunter.state.TrapInfo;
import com.jork.script.jorkHunter.state.TrapState;
import com.jork.script.jorkHunter.trap.TrapType;
import com.jork.utils.ScriptLogger;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.visual.PixelAnalyzer;
import com.osmb.api.visual.PixelCluster;
import com.osmb.api.visual.SearchablePixel;
import com.osmb.api.shape.Polygon;
import com.osmb.api.shape.Rectangle;
import com.osmb.api.scene.RSTile;
import com.osmb.api.visual.PixelCluster.ClusterSearchResult;
import com.osmb.api.utils.Utils;
import com.osmb.api.input.MenuEntry;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Handles all trap interaction logic including pixel detection, menu interaction,
 * and state verification. This class is responsible for the actual UI interaction
 * with traps, separated from state management concerns.
 */
public class TrapInteractionHandler implements InteractionHandler {
    private final JorkHunter script;
    private final TrapVisibilityChecker visibilityChecker;
    
    public TrapInteractionHandler(JorkHunter script) {
        this.script = script;
        this.visibilityChecker = new TrapVisibilityChecker(script);
    }
    
    @Override
    public InteractionResult interact(TrapInfo trapInfo) {
        WorldPosition position = trapInfo.position();
        TrapState state = trapInfo.state();
        TrapType trapType = trapInfo.trapType();
        
        ScriptLogger.info(script, "Attempting interaction with " + state + " trap at " + position);
        
        // Check visibility first
        if (!canInteract(position)) {
            return InteractionResult.movementRequired(position);
        }
        
        // PRIMARY METHOD: Try TileCube approach first
        // Initial cube uses standing trap parameters (height=100, resize=0.4)
        // Collapsed traps will be recalculated in performTileCubeInteraction
        Polygon trapCube = script.getSceneProjector().getTileCube(position, 100);
        if (trapCube == null) {
            ScriptLogger.debug(script, "Trap cube not on screen at " + position);
            return InteractionResult.movementRequired(position);
        }
        
        // Resize the cube to better fit the trap model (40% for standing traps)
        Polygon resizedCube = trapCube.getResized(0.4);
        ScriptLogger.debug(script, "Using initial TileCube for trap interaction at " + position);
        
        // Attempt interaction with the resized cube
        InteractionResult cubeResult = performTileCubeInteraction(resizedCube, trapInfo);
        if (cubeResult.success()) {
            return cubeResult;
        }
        
        // FALLBACK 1: Try pixel cluster detection if TileCube fails
        ScriptLogger.debug(script, "TileCube interaction failed, trying pixel cluster detection");
        RSTile trapTile = script.getSceneManager().getTile(position);
        Polygon trapArea = (trapTile != null) ? trapTile.getTilePoly() : null;
        
        if (trapArea == null) {
            ScriptLogger.debug(script, "Trap tile not on screen at " + position);
            return InteractionResult.movementRequired(position);
        }
        
        Optional<PixelCluster> trapCluster = findTrapPixels(trapArea, trapType, state);
        
        if (trapCluster.isPresent()) {
            Rectangle tapArea = createTapArea(trapCluster.get(), trapType);
            return performTapInteraction(tapArea, trapInfo);
        }
        
        // FALLBACK 2: Return the cube result if all methods failed
        ScriptLogger.debug(script, "All interaction methods failed for trap at " + position);
        return cubeResult;
    }
    
    @Override
    public boolean canInteract(WorldPosition position) {
        // Check if trap is visible (not occluded by UI)
        if (!visibilityChecker.isTrapVisible(position)) {
            ScriptLogger.debug(script, "Trap at " + position + " is occluded by UI");
            return false;
        }
        
        // Check if trap is on screen
        RSTile trapTile = script.getSceneManager().getTile(position);
        if (trapTile == null || trapTile.getTilePoly() == null) {
            ScriptLogger.debug(script, "Trap at " + position + " is not on screen");
            return false;
        }
        
        return true;
    }
    
    @Override
    public InteractionResult verifyTrapState(WorldPosition position) {
        ScriptLogger.debug(script, "Performing blind tap verification at " + position);
        
        // Use TileCube approach for consistency
        Polygon trapCube = script.getSceneProjector().getTileCube(position, 100);
        if (trapCube == null) {
            ScriptLogger.debug(script, "Trap cube not on screen for verification at " + position);
            return InteractionResult.movementRequired(position);
        }
        
        // Resize the cube to better fit the trap model (40% of original size)
        final Polygon resizedCube = trapCube.getResized(0.4);
        
        // Attempt a blind tap to see what menu options are available
        boolean[] trapFound = {false};
        boolean tapped = script.submitHumanTask(() -> 
            script.getFinger().tapGameScreen(resizedCube, (menuEntries) -> {
                for (MenuEntry entry : menuEntries) {
                    String action = entry.getAction();
                    if (action != null) {
                        String actionLower = action.toLowerCase();
                        if (actionLower.contains("check") || 
                            actionLower.contains("dismantle") || 
                            actionLower.contains("lay")) {
                            trapFound[0] = true;
                            ScriptLogger.debug(script, "Blind tap found action: " + action);
                            return entry; // Select the trap action
                        }
                    }
                }
                return null; // Cancel if no trap actions found
            }), 2000);
        
        if (tapped && trapFound[0]) {
            return InteractionResult.success(
                InteractionResult.InteractionType.VERIFICATION_NEEDED,
                "Trap interaction detected at position via TileCube",
                position
            );
        }
        
        return InteractionResult.verificationNeeded(position);
    }
    
    /**
     * Find trap pixels within the given area.
     */
    private Optional<PixelCluster> findTrapPixels(Polygon area, TrapType trapType, TrapState state) {
        // Try appropriate pixel clusters based on state
        SearchablePixel[][] clustersToTry = (state == TrapState.COLLAPSED) 
            ? trapType.getCollapsedPixelClusters()
            : trapType.getStandingPixelClusters();
        
        for (int i = 0; i < clustersToTry.length; i++) {
            if (clustersToTry[i] == null || clustersToTry[i].length == 0) continue;
            
            ScriptLogger.debug(script, "Trying pixel cluster #" + (i + 1) + " for " + state + " trap");
            
            PixelCluster.ClusterQuery query = new PixelCluster.ClusterQuery(
                (int) trapType.getClusterDistance(),
                trapType.getMinClusterSize(),
                clustersToTry[i]
            );
            
            ClusterSearchResult result = script.getPixelAnalyzer().findClusters(area, query);
            
            if (result != null && result.getClusters() != null && !result.getClusters().isEmpty()) {
                ScriptLogger.debug(script, "Found trap pixels using cluster #" + (i + 1));
                return Optional.of(getLargestCluster(result.getClusters()));
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Get the largest cluster from a list of clusters.
     * Since we don't have access to pixel count, just return the first one.
     */
    private PixelCluster getLargestCluster(List<PixelCluster> clusters) {
        // In practice, there's usually only one cluster per trap anyway
        return clusters.isEmpty() ? null : clusters.get(0);
    }
    
    /**
     * Create a tap area from pixel cluster bounds.
     */
    private Rectangle createTapArea(PixelCluster cluster, TrapType trapType) {
        Rectangle bounds = cluster.getBounds();
        
        // Ensure minimum dimensions
        int width = Math.max(bounds.width, 2);
        int height = Math.max(bounds.height, 2);
        
        // Apply Y-scaling from trap type
        double yScale = trapType.getTapAreaYScale();
        int scaledHeight = Math.max((int)(height * yScale), 2);
        int yOffset = (scaledHeight - height) / 2;
        
        return new Rectangle(
            bounds.x,
            bounds.y - yOffset,
            width,
            scaledHeight
        );
    }
    
    /**
     * Perform tap interaction using TileCube with menu matching.
     */
    private InteractionResult performTileCubeInteraction(Polygon trapCube, TrapInfo trapInfo) {
        TrapState state = trapInfo.state();
        TrapType trapType = trapInfo.trapType();
        WorldPosition position = trapInfo.position();
        
        // Recalculate TileCube for collapsed traps with different parameters
        Polygon finalTapCube = trapCube;
        if (state == TrapState.COLLAPSED) {
            ScriptLogger.debug(script, "Recalculating TileCube for collapsed trap at " + position);
            Polygon collapsedCube = script.getSceneProjector().getTileCube(position, 40);
            if (collapsedCube != null) {
                // Use 60% resize for collapsed traps (larger area since they're flatter)
                finalTapCube = collapsedCube.getResized(0.6);
                ScriptLogger.debug(script, "Using collapsed trap TileCube (height=40, resize=0.6)");
            } else {
                ScriptLogger.debug(script, "Failed to get collapsed trap cube, using original");
            }
        }
        
        String[] acceptableActions = trapType.getActionsForTrapState(state);
        
        final Polygon tapCube = finalTapCube;
        boolean interacted = script.submitHumanTask(() ->
            script.getFinger().tapGameScreen(tapCube, (menuEntries) -> {
                for (MenuEntry entry : menuEntries) {
                    String action = entry.getAction();
                    if (action != null) {
                        for (String acceptableAction : acceptableActions) {
                            if (action.toLowerCase().contains(acceptableAction.toLowerCase())) {
                                ScriptLogger.debug(script, "Selected action via TileCube: " + action);
                                return entry;
                            }
                        }
                    }
                }
                ScriptLogger.debug(script, "No matching actions found in TileCube tap");
                return null;
            }), Utils.random(2900, 3400));
        
        if (interacted) {
            InteractionResult.InteractionType type = determineInteractionType(state);
            return InteractionResult.success(type, "Successfully interacted with trap via TileCube", position);
        }
        
        return InteractionResult.failure("TileCube tap failed", position);
    }
    
    /**
     * Perform tap interaction with menu matching.
     */
    private InteractionResult performTapInteraction(Rectangle tapArea, TrapInfo trapInfo) {
        TrapState state = trapInfo.state();
        TrapType trapType = trapInfo.trapType();
        WorldPosition position = trapInfo.position();
        
        String[] acceptableActions = trapType.getActionsForTrapState(state);
        
        boolean interacted = script.submitHumanTask(() ->
            script.getFinger().tapGameScreen(tapArea, (menuEntries) -> {
                for (MenuEntry entry : menuEntries) {
                    String action = entry.getAction();
                    if (action != null) {
                        for (String acceptableAction : acceptableActions) {
                            if (action.toLowerCase().contains(acceptableAction.toLowerCase())) {
                                ScriptLogger.debug(script, "Selected action: " + action);
                                return entry;
                            }
                        }
                    }
                }
                return null;
            }), Utils.random(2900, 3400));
        
        if (interacted) {
            InteractionResult.InteractionType type = determineInteractionType(state);
            return InteractionResult.success(type, "Successfully interacted with trap", position);
        }
        
        return InteractionResult.failure("Failed to interact with trap", position);
    }
    
    /**
     * Determine interaction type based on trap state.
     */
    private InteractionResult.InteractionType determineInteractionType(TrapState state) {
        return switch (state) {
            case FINISHED, FINISHED_SUCCESS, FINISHED_FAILED -> InteractionResult.InteractionType.TRAP_CHECKED;
            case COLLAPSED -> InteractionResult.InteractionType.TRAP_RESET;
            case LAYING -> InteractionResult.InteractionType.TRAP_LAID;
            default -> InteractionResult.InteractionType.FAILED;
        };
    }
    
    /**
     * Scan for specific trap pixels at a position.
     * Extracted from TrapStateManager for reuse.
     */
    public boolean scanForTrapPixels(WorldPosition position, SearchablePixel[] pixels, int minSize) {
        RSTile tile = script.getSceneManager().getTile(position);
        if (tile == null) return false;
        
        Polygon tilePoly = tile.getTilePoly();
        if (tilePoly == null) return false;
        
        PixelCluster.ClusterQuery query = new PixelCluster.ClusterQuery(
            10, // cluster distance
            minSize,
            pixels
        );
        
        ClusterSearchResult result = script.getPixelAnalyzer().findClusters(tilePoly, query);
        return result != null && result.getClusters() != null && !result.getClusters().isEmpty();
    }
    
    public TrapVisibilityChecker getVisibilityChecker() {
        return visibilityChecker;
    }
}