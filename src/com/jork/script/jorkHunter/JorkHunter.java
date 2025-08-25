package com.jork.script.jorkHunter;

import com.jork.script.jorkHunter.tasks.DropTask;
import com.jork.script.jorkHunter.tasks.BirdSnaringTask;
import com.jork.script.jorkHunter.state.TrapStateManager;
import com.jork.script.jorkHunter.state.TrapInfo;
import com.osmb.api.scene.RSTile;
import com.jork.script.jorkHunter.trap.TrapType;
import com.jork.script.jorkHunter.utils.tasks.TaskManager;
import com.jork.script.jorkHunter.utils.placement.TrapPlacementStrategy;
import com.jork.script.jorkHunter.utils.placement.NoCardinalStrategy;
import com.jork.script.jorkHunter.utils.placement.XPatternStrategy;
import com.osmb.api.ui.tabs.Skill;
import com.osmb.api.ui.tabs.Settings;
import com.osmb.api.ui.component.tabs.skill.SkillType;
import com.osmb.api.utils.UIResult;
import com.osmb.api.ui.component.tabs.skill.SkillsTabComponent.SkillLevel;
import com.jork.script.jorkHunter.javafx.ScriptOptions;
import com.osmb.api.script.Script;
import com.osmb.api.script.ScriptDefinition;
import com.osmb.api.script.SkillCategory;
import com.jork.utils.ScriptLogger;
import com.osmb.api.location.position.types.LocalPosition;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.visual.drawing.Canvas;
import com.osmb.api.shape.Polygon;
import com.osmb.api.shape.Rectangle;
import com.osmb.api.visual.PixelAnalyzer;
import com.osmb.api.utils.Utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ScriptDefinition(
    name = "jorkHunter",
    author = "jork",
    version = 1.0,
    description = "A versatile All-In-One (AIO) hunter script.",
    skillCategory = SkillCategory.HUNTER
)
// JorkHunter Script - Main Class
public class JorkHunter extends Script {

    private final TaskManager taskManager;
    private BirdSnaringTask huntTask; // Reference to access TrapStateManager
    private int maxTraps = 1; // Default to 1 trap, will be updated based on Hunter level
    // ───────────────────────────────────────────────────────────────────────────
    // UI synchronisation fields
    // These are written by the JavaFX thread and read by the script thread.
    // -------------------------------------------------------------------------
    private volatile boolean settingsConfirmed = false; // FX-thread sets → script thread reads
    private boolean initialised = false;                // script-thread internal guard

    private HuntingLocation selectedLocation; // To store the user's selection from the UI

    private volatile String selectedTarget = "Unknown";
    private volatile String selectedArea   = "Unknown";
    private volatile String selectedStrategy = "X-Pattern";
    private volatile int selectedManualLevel = -1;
    private volatile Map<String, Object> strategyOptions = null;

    // --- Trap Management (Centralized) ---------------------------------------
    // This flag is used to signal that the script should stop laying new traps
    // and just clear existing ones before a break or world hop.
    private boolean isDrainingForBreak = false;
    private boolean zoomSet = false;

    public JorkHunter(Object scriptCore) {
        super(scriptCore);
        this.taskManager = new TaskManager(this);
    }

    @Override
    public void onStart() {
        // Log script startup
        ScriptLogger.startup(this, "1.0", "jork", "Bird Snaring");

        // ── Hint the location manager with the expected starting region ──
        int kourendWoodlandRegion = 6197;
        setExpectedRegionId(kourendWoodlandRegion);
        ScriptLogger.info(this, "Expected region ID set to " + kourendWoodlandRegion + " (Kourend Woodland)");

        // ── Show settings window (NON-BLOCKING) ────────────────────────────
        ScriptOptions opts = new ScriptOptions(this);
        javafx.scene.Scene scene = new javafx.scene.Scene(opts);
        getStageController().show(scene, "JorkHunter – Options", false);

        ScriptLogger.info(this, "Settings window opened – waiting for user confirmation…");

        // If the user closes the window via the X button treat it as confirmation
        if (scene.getWindow() != null) {
            scene.getWindow().setOnHidden(e -> {
                Map<String, Object> defaultOptions = new HashMap<>();
                defaultOptions.put("maxCenterDistance", 2);
                defaultOptions.put("recenterOnEmpty", false);
                onSettingsSelected(this.selectedTarget, this.selectedArea, this.selectedStrategy, false, -1, defaultOptions);
            });
        }

        // No further game interaction here – wait for UI confirmation
    }

    /**
     * Called from the JavaFX thread when the user presses the Start button or
     * closes the settings window. This method MUST be lightweight and avoid
     * interacting with the game API. It only stores the selections and sets a
     * volatile flag that the script thread will act upon in its next poll.
     */
    public void onSettingsSelected(String target, String area, String strategy, boolean manual, int lvl, Map<String, Object> options) {
        this.selectedTarget = target;
        this.selectedArea   = area;
        this.selectedStrategy = strategy;
        this.strategyOptions = options;

        // For now, we only have one location, so we'll hardcode it.
        // TODO: This should look up the location from HunterLocationConstants based on the 'area' string.
        this.selectedLocation = HunterLocationConstants.KOUREND_WOODLAND;

        if (manual && lvl > 0) {
            this.selectedManualLevel = lvl;
        }
        this.settingsConfirmed = true;
    }

    // ───────────────────────────────────────────────────────────────────────
    // Initialise after UI confirmation – runs on script thread
    // -----------------------------------------------------------------------
    private void initialiseIfReady() {
        if (initialised || !settingsConfirmed) {
            return;
        }

        ScriptLogger.info(this, "Settings confirmed – Target: " + selectedTarget + " | Area: " + selectedArea + " | Strategy: " + selectedStrategy);

        // Set the expected region ID to speed up location initialization.
        if (selectedLocation != null) {
            setExpectedRegionId(selectedLocation.regionId());
            ScriptLogger.info(this, "Expected region ID set to " + selectedLocation.regionId() + " (" + selectedLocation.name() + ")");
        }

        // Ensure the inventory is open before beginning tasks
        openInventoryIfNeeded();

        // Determine hunter level either manual or automatic
        if (selectedManualLevel > 0) {
            int level = selectedManualLevel;
            maxTraps = Math.min(5, level / 20 + 1);
            ScriptLogger.info(this, "Manual Hunter Level: " + level + " | Max Traps: " + maxTraps);
        } else {
            calculateMaxTraps();
        }

        // Load tasks
        initializeTasks();

        ScriptLogger.info(this, "Initialisation complete. Starting tasks…");
        initialised = true;
    }

    /**
     * Sets the zoom level to a random value between MIN_ZOOM and MAX_ZOOM.
     * This method is called once at script start with highest priority.
     */
    private void setZoom() {
        log("Checking zoom level...");
        Settings settings = getWidgetManager().getSettings();

        // Define our desired zoom range (20-32% for hunting)
        final int MIN_ZOOM = 20;
        final int MAX_ZOOM = 32;

        // First check if zoom is already in acceptable range
        UIResult<Integer> currentZoomResult = settings.getZoomLevel();
        if (currentZoomResult.isFound()) {
            int currentZoom = currentZoomResult.get();
            log("Current zoom level: " + currentZoom + "%");
            if (currentZoom >= MIN_ZOOM && currentZoom <= MAX_ZOOM) {
                log("Zoom already in acceptable range: " + currentZoom + "%");
                zoomSet = true;
                return;
            }
        }
        // Generate random zoom level between MIN_ZOOM and MAX_ZOOM
        int targetZoom = Utils.random(MIN_ZOOM, MAX_ZOOM);
        log("Setting zoom to: " + targetZoom + "%");

        // Attempt to set zoom
        if (settings.setZoomLevel(targetZoom)) {
            log("Zoom set successfully to: " + targetZoom + "%");
            zoomSet = true;
        }
    }

    private void openInventoryIfNeeded() {
        // Early exit if inventory is already open - no need for human task
        if (getWidgetManager().getInventory().isOpen()) {
            return;
        }
        
        submitHumanTask(() -> {
            if (!getWidgetManager().getInventory().isOpen()) {
                return getWidgetManager().getInventory().open();
            }
            return true;
        }, 2000);
    }

    private void calculateMaxTraps() {
        ScriptLogger.info(this, "Starting Hunter level calculation...");
        
        try {
            // First, let's see what widget manager gives us
            if (getWidgetManager() == null) {
                ScriptLogger.error(this, "WidgetManager is null!");
                return;
            }
            
            Skill skillTab = getWidgetManager().getSkillTab();
            if (skillTab == null) {
                ScriptLogger.error(this, "SkillTab is null!");
                return;
            }
            
            ScriptLogger.info(this, "SkillTab obtained, attempting to get Hunter level...");
            
            SkillLevel hunterInfo = 
                skillTab.getSkillLevel(SkillType.HUNTER);
                
            if (hunterInfo == null) {
                ScriptLogger.warning(this, "HunterInfo is null - skills tab may not be accessible");
                retryCalculateMaxTraps();
                return;
            }
            
            int level = hunterInfo.getLevel();
            ScriptLogger.info(this, "Raw Hunter level from API: " + level);
            
            maxTraps = Math.min(5, level / 20 + 1);
            ScriptLogger.info(this, "Hunter Level: " + level + " | Calculated Max Traps: " + maxTraps);
            
        } catch (Exception e) {
            ScriptLogger.exception(this, "reading Hunter level", e);
            ScriptLogger.warning(this, "Defaulting to 1 trap due to error");
            retryCalculateMaxTraps();
        }
    }
    
    private void retryCalculateMaxTraps() {
        try {
            // Wait a moment then retry
            Thread.sleep(Utils.random(450, 650));
            
            Skill skillTab = getWidgetManager().getSkillTab();
            if (skillTab != null) {
                SkillLevel hunterInfo = 
                    skillTab.getSkillLevel(SkillType.HUNTER);
                if (hunterInfo != null) {
                    int level = hunterInfo.getLevel();
                    maxTraps = Math.min(5, level / 20 + 1);
                    ScriptLogger.info(this, "Hunter Level (retry): " + level + " | Max Traps: " + maxTraps);
                } else {
                    ScriptLogger.warning(this, "Still could not read Hunter level after retry, defaulting to 1 trap");
                }
            } else {
                ScriptLogger.warning(this, "Skills tab still not available after retry, defaulting to 1 trap");
            }
        } catch (Exception e) {
            ScriptLogger.exception(this, "retrying Hunter level read", e);
            ScriptLogger.warning(this, "Defaulting to 1 trap due to retry error");
        }
    }

    public int getMaxTraps() {
        // If maxTraps is still 1 and we're above level 20, try to recalculate
        if (maxTraps == 1) {
            try {
                Skill skillTab = getWidgetManager().getSkillTab();
                if (skillTab != null) {
                    SkillLevel hunterInfo = 
                        skillTab.getSkillLevel(SkillType.HUNTER);
                    if (hunterInfo != null) {
                        int level = hunterInfo.getLevel();
                        if (level >= 20) {
                            maxTraps = Math.min(5, level / 20 + 1);
                            ScriptLogger.info(this, "Recalculated - Hunter Level: " + level + " | Max Traps: " + maxTraps);
                        }
                    }
                }
            } catch (Exception e) {
                // Silently fail, keep using current maxTraps
            }
        }
        return maxTraps;
    }

    public void initializeTasks() {
        // Determine trap type based on selected target
        TrapType trapType = TrapType.BIRD_SNARE; // Currently only supporting bird snares
        
        // Create placement strategy based on user selection
        TrapPlacementStrategy strategy = createStrategyFromSelection(selectedStrategy);
        
        // Create and store reference to HuntTask
        huntTask = new BirdSnaringTask(this, trapType, maxTraps, selectedLocation.huntingZones(), strategy);
        
        // Add common tasks
        taskManager.addTasks(
            new DropTask(this, trapType.getDropItems(), selectedLocation),
            huntTask
        );

        // Add other hunter-specific tasks here in the future
    }

    @Override
    public int[] regionsToPrioritise() {
        return new int[] { 6197 };
    }

    @Override
    public int poll() {
        // --- Highest Priority: Set zoom level (only once) -------------------
        if (!zoomSet) {
            setZoom();
            return 0; // Immediate retry
        }
        
        // --- Pre-Task-Execution Checks (runs every poll) ---------------------
        
        // Proactively check if a break or hop is due, and if so, start draining traps.
        if (getProfileManager().isDueToBreak() || getProfileManager().isDueToHop() || getProfileManager().isDueToAFK()) {
            if (!this.isDrainingForBreak) {
                ScriptLogger.info(this, "Break or hop is due. Entering drain mode to clear active traps.");
                this.isDrainingForBreak = true;
            }
        }
        
        // If an AFK is scheduled and traps are already clear, trigger it immediately.
        TrapStateManager trapManager = getTrapStateManager();
        if (getProfileManager().isDueToAFK() && trapManager != null && trapManager.isEmpty() && !trapManager.isCurrentlyLayingTrap()) {
            ScriptLogger.info(this, "AFK is due and no traps are active. Triggering AFK now.");
            getProfileManager().forceAFK();
            return 1500; // Let the AFK manager take over
        }

        // --- Settings Confirmation Check -------------------------------------
        // Do nothing until the user has confirmed the settings in the UI.
        if (!settingsConfirmed) {
            return 1000; // Keep waiting until initialised
        }

        // Handle deferred initialisation once the UI has been confirmed
        initialiseIfReady();

        if (!initialised) {
            return 1000; // Keep waiting until initialised
        }

        // If settings are confirmed and the task manager is ready, execute tasks
        if (taskManager != null) {
            return taskManager.executeNextTask();
        }
        return 200; // Default poll rate before initialization
    }

    // ───────────────────────────────────────────────────────────────────────
    // │ BREAK & WORLD HOP MANAGEMENT                                          │
    // ───────────────────────────────────────────────────────────────────────

    @Override
    public boolean canBreak() {
        // Only prevent breaks if we have traps out (in any state)
        TrapStateManager trapManager = getTrapStateManager();
        if (trapManager == null) {
            return true;
        }
        
        boolean hasTraps = !trapManager.isEmpty();
        
        // Only log state transitions
        if (!hasTraps && isDrainingForBreak) {
            ScriptLogger.info(this, "All traps cleared - allowing break");
            isDrainingForBreak = false; // Reset flag after successful break
        }
        
        return !hasTraps;
    }

    @Override
    public boolean canHopWorlds() {
        // Only prevent hops if we have traps out (in any state)
        TrapStateManager trapManager = getTrapStateManager();
        if (trapManager == null) {
            return true;
        }
        
        boolean hasTraps = !trapManager.isEmpty();
        
        // Only log state transitions
        if (!hasTraps && isDrainingForBreak) {
            ScriptLogger.info(this, "All traps cleared - allowing world hop");
            isDrainingForBreak = false; // Reset flag after successful hop
        }
        
        return !hasTraps;
    }

    // --- Getter methods for tasks to access script state ---
    
    /**
     * Gets the TrapStateManager from the HuntTask for break/hop logic.
     * @return TrapStateManager instance, or null if tasks not initialized
     */
    private TrapStateManager getTrapStateManager() {
        return huntTask != null ? huntTask.getTrapStateManager() : null;
    }

    public boolean isDrainingForBreak() {
        return isDrainingForBreak;
    }

    @Override
    public void onPaint(Canvas canvas) {
        try {
            // Only draw debug visuals if we're initialized and have a hunting task
            if (!initialised || huntTask == null) {
                return;
            }

            TrapStateManager trapManager = huntTask.getTrapStateManager();
            if (trapManager == null) {
                return;
            }

            // Draw tracked trap positions in neon orange
            drawTrackedTrapPositions(canvas, trapManager);
            
            // Draw respawn circles with z-offset visualization
            // drawRespawnCirclesWithZOffset(canvas);  // Commented out - debugging tool

        } catch (Exception e) {
            // Silently catch any painting errors to avoid disrupting the script
            ScriptLogger.debug(this, "Error in onPaint: " + e.getMessage());
        }
    }

    /**
     * Draw neon orange highlights for all tracked trap positions
     */
    private void drawTrackedTrapPositions(Canvas canvas, TrapStateManager trapManager) {
        // Get all tracked trap positions from the TrapStateManager
        List<WorldPosition> trapPositions = trapManager.getLaidTrapPositions();
        
        if (trapPositions == null || trapPositions.isEmpty()) {
            return;
        }

        // Draw neon orange highlight for each tracked trap position
        for (WorldPosition position : trapPositions) {
            if (position == null) continue;

            // Get the tile polygon for this world position using the scene projector
            Polygon tilePoly = getSceneProjector().getTilePoly(position);
            
            if (tilePoly != null) {
                // Fill the polygon with neon orange (semi-transparent)
                canvas.fillPolygon(tilePoly, 0xFF6600, 0.4); // Neon orange with 40% opacity
                
                // Draw the polygon outline with bright neon orange for better visibility
                canvas.drawPolygon(tilePoly, 0xFF3300, 1.0); // Bright orange-red outline
                
                // Also draw a simple test rectangle to verify canvas is working
                Rectangle bounds = tilePoly.getBounds();
                canvas.fillRect(bounds.x, bounds.y, 10, 10, 0xFF0000); // Small red square as test
            }
        }
    }


    /**
     * DEBUG METHOD: Draws black squares at detected trap positions with z-offset values
     * Purpose: Visualizes where the system thinks traps are located based on respawn circle detection
     * and shows the z-offset being used for screen-to-world coordinate conversion.
     * Uncomment the call in onPaint() to enable this debugging visualization.
     */
    /*
    private void drawRespawnCirclesWithZOffset(Canvas canvas) {
        // Get all respawn circles detected by the pixel analyzer
        List<PixelAnalyzer.RespawnCircle> respawnCircles = getPixelAnalyzer().findRespawnCircleTypes();
        
        if (respawnCircles == null || respawnCircles.isEmpty()) {
            return;
        }

        for (PixelAnalyzer.RespawnCircle circle : respawnCircles) {
            Rectangle circleBounds = circle.getBounds();
            PixelAnalyzer.RespawnCircle.Type circleType = circle.getType();
            
            // Get the appropriate z-offset for this circle type
            int zOffset = getTrapStateManager().getZOffsetForCircleType(circleType);
            
            // Convert respawn circle to world positions using the utils method
            List<WorldPosition> positions = getUtils().getWorldPositionForRespawnCircles(
                List.of(circleBounds), zOffset);
            
            
            // For each detected world position, draw a 3x3 black square at the tile center + z-offset indicator
            for (WorldPosition position : positions) {
                if (position == null) continue;
                
                // Convert to local position to get coordinates for getTilePoint
                LocalPosition localPos = position.toLocalPosition(this);
                if (localPos == null) continue;
                
                // Get the tile center point with the same z-offset as the respawn circle
                java.awt.Point tileCenter = getSceneProjector().getTilePoint(
                    localPos.getPreciseX(), localPos.getPreciseY(), localPos.getPlane(), null, zOffset);
                if (tileCenter == null) continue;
                
                // Draw a 3x3 black square at the tile center with the correct z-offset
                canvas.fillRect(tileCenter.x - 1, tileCenter.y - 1, 3, 3, 0x000000); // Black square
                
                // Draw the z-offset text near the square
                String zOffsetText = String.valueOf(zOffset);
                canvas.drawText(zOffsetText, tileCenter.x + 5, tileCenter.y - 5, 0xFFFFFF, 
                    new java.awt.Font("Arial", java.awt.Font.BOLD, 12));
            }
        }
    }
    */


    
    /**
     * Creates a TrapPlacementStrategy instance based on the selected strategy name.
     * @param strategyName The name of the strategy selected by the user
     * @return The corresponding strategy instance
     */
    private TrapPlacementStrategy createStrategyFromSelection(String strategyName) {
        if (strategyName == null) {
            return new NoCardinalStrategy();
        }
        
        return switch (strategyName) {
            case "No Cardinal" -> new NoCardinalStrategy();
            case "X-Pattern" -> {
                // Extract options for X-Pattern
                int maxDistance = 2; // default
                boolean recenterOnEmpty = false; // default
                
                if (strategyOptions != null) {
                    Object distanceObj = strategyOptions.get("maxCenterDistance");
                    if (distanceObj instanceof Integer) {
                        maxDistance = (Integer) distanceObj;
                    }
                    
                    Object recenterObj = strategyOptions.get("recenterOnEmpty");
                    if (recenterObj instanceof Boolean) {
                        recenterOnEmpty = (Boolean) recenterObj;
                    }
                }
                
                ScriptLogger.info(this, "Creating X-Pattern strategy with maxDistance=" + maxDistance + 
                                       ", recenterOnEmpty=" + recenterOnEmpty);
                yield new XPatternStrategy(this, huntTask, maxDistance, recenterOnEmpty);
            }
            default -> {
                ScriptLogger.warning(this, "Unknown strategy: " + strategyName + ". Using No Cardinal as fallback.");
                yield new NoCardinalStrategy();
            }
        };
    }


} 