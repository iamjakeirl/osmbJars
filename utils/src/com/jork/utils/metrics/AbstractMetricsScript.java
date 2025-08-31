package com.jork.utils.metrics;

import com.osmb.api.script.Script;
import com.osmb.api.visual.drawing.Canvas;
import com.jork.utils.metrics.core.MetricsTracker;
import com.jork.utils.metrics.core.MetricType;
import com.jork.utils.metrics.display.MetricsPanelConfig;
import com.osmb.api.ui.component.tabs.skill.SkillType;

import java.util.function.Supplier;

/**
 * Abstract base class for scripts that use the metrics system.
 * Provides automatic runtime tracking and easy metric registration.
 * Scripts should extend this class to get metrics functionality.
 */
public abstract class AbstractMetricsScript extends Script {
    
    protected MetricsTracker metrics;
    protected MetricsPanelConfig metricsConfig;
    
    public AbstractMetricsScript(Object scriptCore) {
        super(scriptCore);
    }
    
    @Override
    public void onStart() {
        // Initialize metrics with default configuration
        metricsConfig = createMetricsConfig();
        metrics = new MetricsTracker(this, metricsConfig);
        
        // Automatically register runtime metrics
        metrics.registerRuntimeMetrics();
        
        // Call subclass initialization
        onMetricsStart();
    }
    
    /**
     * Creates the metrics configuration.
     * Override this to customize the display settings.
     */
    protected MetricsPanelConfig createMetricsConfig() {
        return new MetricsPanelConfig();
    }
    
    /**
     * Called after metrics system is initialized.
     * Subclasses should override this instead of onStart().
     */
    protected abstract void onMetricsStart();
    
    @Override
    public void onPaint(Canvas canvas) {
        // Render metrics first
        if (metrics != null) {
            metrics.render(canvas);
        }
        
        // Call subclass painting
        onMetricsPaint(canvas);
    }
    
    /**
     * Called after metrics are rendered.
     * Subclasses should override this for additional painting.
     */
    protected void onMetricsPaint(Canvas canvas) {
        // Default implementation does nothing
    }
    
    public void onStop() {
        // Clear metrics on stop
        if (metrics != null) {
            metrics.clear();
        }
        
        // Call subclass cleanup
        onMetricsStop();
    }
    
    /**
     * Called when script is stopping.
     * Subclasses can override for cleanup.
     */
    protected void onMetricsStop() {
        // Default implementation does nothing
    }
    
    // Convenience methods for metric registration
    
    /**
     * Registers a simple numeric metric
     */
    protected void registerMetric(String label, Supplier<Object> valueSupplier) {
        if (metrics != null) {
            metrics.register(label, valueSupplier, MetricType.NUMBER);
        }
    }
    
    /**
     * Registers a metric with specific type
     */
    protected void registerMetric(String label, Supplier<Object> valueSupplier, MetricType type) {
        if (metrics != null) {
            metrics.register(label, valueSupplier, type);
        }
    }
    
    /**
     * Registers a metric with custom format
     */
    protected void registerMetric(String label, Supplier<Object> valueSupplier, MetricType type, String format) {
        if (metrics != null) {
            metrics.register(label, valueSupplier, type, format);
        }
    }
    
    /**
     * Enables XP tracking for a specific skill with sprite ID
     * @param skill The skill to track
     * @param spriteId The sprite ID for the skill icon (e.g., 220 for Hunter)
     */
    protected void enableXPTracking(SkillType skill, int spriteId) {
        if (metrics != null) {
            metrics.registerXPTracking(skill, spriteId);
        }
    }
    
    /**
     * Unregisters a metric by label
     */
    protected void unregisterMetric(String label) {
        if (metrics != null) {
            metrics.unregister(label);
        }
    }
    
    /**
     * Resets all metrics (useful for new sessions)
     */
    protected void resetMetrics() {
        if (metrics != null) {
            metrics.resetAll();
        }
    }
    
    /**
     * Gets the metrics tracker for advanced usage
     */
    protected MetricsTracker getMetricsTracker() {
        return metrics;
    }
    
    /**
     * Gets the metrics configuration for runtime adjustments
     */
    protected MetricsPanelConfig getMetricsConfig() {
        return metricsConfig;
    }
    
    /**
     * Sets whether metrics display is enabled
     */
    protected void setMetricsEnabled(boolean enabled) {
        if (metricsConfig != null) {
            metricsConfig.setEnabled(enabled);
        }
    }
    
    /**
     * Sets the position of the metrics panel
     */
    protected void setMetricsPosition(MetricsPanelConfig.Position position) {
        if (metricsConfig != null) {
            metricsConfig.setPosition(position);
        }
    }
}