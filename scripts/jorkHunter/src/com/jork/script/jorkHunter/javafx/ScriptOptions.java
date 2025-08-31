package com.jork.script.jorkHunter.javafx;

import com.jork.script.jorkHunter.JorkHunter;
import com.jork.script.jorkHunter.config.HuntingConfig;
import com.jork.script.jorkHunter.trap.TrapType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;

/**
 * Modern dark-themed JavaFX options window for the JorkHunter script.
 * Provides configuration options:
 *   – Target (Birds or Chinchompas)
 *   – Placement Strategy (trap placement patterns)
 * Uses TilePicker for hunting location selection in-game.
 */
public class ScriptOptions extends VBox {

    // Dark theme color constants
    private static final String DARK_BG = "#1e1e1e";
    private static final String DARKER_BG = "#161616";
    private static final String ACCENT_COLOR = "#ff8c42";  // Orange
    private static final String ACCENT_HOVER = "#ffa05c";  // Lighter orange on hover
    private static final String TEXT_PRIMARY = "#e4e4e4";
    private static final String TEXT_SECONDARY = "#999999";
    private static final String BORDER_COLOR = "#333333";
    private static final String INPUT_BG = "#2a2a2a";
    private static final String INPUT_FOCUS = "#363636";

    private final ComboBox<String> targetDropdown;
    private final ComboBox<String> strategyDropdown;
    private final Button            confirmBtn;
    private final CheckBox manualLevelCheck;
    private final TextField levelInput;
    private final CheckBox expediteCollectionCheck;
    private final TextField expediteChanceInput;
    private final CheckBox debugLoggingCheck;
    
    // Dynamic strategy options
    private VBox strategyOptionsContainer;
    private final Map<String, Object> strategyOptions = new HashMap<>();
    
    // Line pattern specific controls
    private ComboBox<String> lineOrientationDropdown;

    private final HuntingConfig config;
    
    public ScriptOptions(JorkHunter script, HuntingConfig config) {
        this.config = config;
        setSpacing(0);
        setAlignment(Pos.TOP_CENTER);
        setPadding(new Insets(0));
        setMinWidth(420);
        setPrefWidth(450);
        
        // Apply dark background to main container
        setStyle("-fx-background-color: " + DARK_BG + ";");

        // ── Header Section ──────────────────────────────────────────
        Label titleLabel = new Label("JorkHunter Configuration");
        titleLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + ";");
        titleLabel.setPadding(new Insets(25, 0, 25, 0));
        
        VBox headerBox = new VBox(titleLabel);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setStyle("-fx-background-color: " + DARKER_BG + ";");
        
        // ── Main Content Container ──────────────────────────────────
        VBox contentBox = new VBox(20);
        contentBox.setPadding(new Insets(25));
        contentBox.setAlignment(Pos.TOP_LEFT);
        
        // ── Target Selection Section ──────────────────────────────────
        Label targetSectionLabel = new Label("TARGET SELECTION");
        targetSectionLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_SECONDARY + ";");
        
        Label targetLbl = new Label("Creature:");
        targetLbl.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 13px;");
        targetLbl.setMinWidth(100);
        
        targetDropdown = new ComboBox<>();
        targetDropdown.setPrefWidth(250);
        targetDropdown.setStyle(getComboBoxStyle());
        styleComboBox(targetDropdown);
        
        // Add specific creatures based on enabled trap types in config
        if (config.isTypeEnabled(TrapType.BIRD_SNARE)) {
            targetDropdown.getItems().addAll(
                "Crimson Swift",
                "Copper Longtail", 
                "Tropical Wagtail",
                "Cerulean Twitch"
            );
        }
        if (config.isTypeEnabled(TrapType.CHINCHOMPA)) {
            targetDropdown.getItems().addAll(
                "Grey Chinchompa",
                "Red Chinchompa",
                "Black Chinchompa"
            );
        }
        
        // If it's the "all features" variant, show all creatures
        if (config.isAllFeaturesEnabled()) {
            targetDropdown.getItems().clear();
            targetDropdown.getItems().addAll(
                "Crimson Swift",
                "Copper Longtail",
                "Tropical Wagtail", 
                "Cerulean Twitch",
                "Grey Chinchompa",
                "Red Chinchompa",
                "Black Chinchompa"
            );
        }
        
        targetDropdown.getSelectionModel().selectFirst();
        
        HBox targetRow = new HBox(15, targetLbl, targetDropdown);
        targetRow.setAlignment(Pos.CENTER_LEFT);
        
        VBox targetSection = new VBox(8, targetSectionLabel, targetRow);
        targetSection.setPadding(new Insets(0, 0, 15, 0));
        
        // ── Info label about location selection ─────────
        Label infoLbl = new Label("ℹ Location will be selected in-game using tile picker");
        infoLbl.setStyle("-fx-font-style: italic; -fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 11px; " +
                         "-fx-background-color: " + INPUT_BG + "; -fx-padding: 8; -fx-background-radius: 4;");
        infoLbl.setWrapText(true);
        infoLbl.setMaxWidth(Double.MAX_VALUE);

        // ── Placement Strategy Section ─────────────────────
        Label strategySectionLabel = new Label("PLACEMENT STRATEGY");
        strategySectionLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_SECONDARY + ";");
        
        Label strategyLbl = new Label("Pattern:");
        strategyLbl.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 13px;");
        strategyLbl.setMinWidth(100);
        
        strategyDropdown = new ComboBox<>();
        strategyDropdown.getItems().addAll("Auto", "X-Pattern", "L-Pattern", "Line", "Cross", "Custom");
        strategyDropdown.getSelectionModel().selectFirst();
        strategyDropdown.setPrefWidth(250);
        strategyDropdown.setStyle(getComboBoxStyle());
        styleComboBox(strategyDropdown);
        
        HBox strategyRow = new HBox(15, strategyLbl, strategyDropdown);
        strategyRow.setAlignment(Pos.CENTER_LEFT);
        
        // ── Dynamic Strategy Options Container ────────────
        strategyOptionsContainer = new VBox(10);
        strategyOptionsContainer.setAlignment(Pos.CENTER_LEFT);
        strategyOptionsContainer.setPadding(new Insets(10, 0, 0, 0));
        strategyOptionsContainer.setMinHeight(60);
        strategyOptionsContainer.setStyle("-fx-background-color: " + INPUT_BG + "; " +
                                         "-fx-background-radius: 4; -fx-padding: 10;");
        
        // Listen for strategy changes to update options
        strategyDropdown.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateStrategyOptions(newVal);
        });
        
        // Initialize with default strategy options
        updateStrategyOptions(strategyDropdown.getValue());
        
        VBox strategySection = new VBox(8, strategySectionLabel, strategyRow, strategyOptionsContainer);
        strategySection.setPadding(new Insets(0, 0, 15, 0));

        // ── Advanced Options Section ───────────────────────────
        Label advancedSectionLabel = new Label("ADVANCED OPTIONS");
        advancedSectionLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_SECONDARY + ";");
        
        // Manual Hunter Level
        manualLevelCheck = new CheckBox("Enter hunter level manually");
        manualLevelCheck.setStyle(getCheckBoxStyle());
        
        levelInput = new TextField();
        levelInput.setPromptText("Level 1-99");
        levelInput.setPrefWidth(100);
        levelInput.setStyle(getTextFieldStyle());
        levelInput.setDisable(true);
        manualLevelCheck.selectedProperty().addListener((obs, o, n) -> {
            levelInput.setDisable(!n);
            if (n) levelInput.requestFocus();
        });
        
        HBox levelRow = new HBox(10, manualLevelCheck, levelInput);
        levelRow.setAlignment(Pos.CENTER_LEFT);
        
        // Expedite Collection Settings
        expediteCollectionCheck = new CheckBox("Expedite trap collection before breaks");
        expediteCollectionCheck.setStyle(getCheckBoxStyle());
        expediteCollectionCheck.setSelected(false);
        
        Label chanceLabel = new Label("Chance:");
        chanceLabel.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 12px;");
        
        expediteChanceInput = new TextField("50");
        expediteChanceInput.setPromptText("0-100");
        expediteChanceInput.setPrefWidth(60);
        expediteChanceInput.setStyle(getTextFieldStyle());
        expediteChanceInput.setDisable(true);
        
        Label percentLabel = new Label("%");
        percentLabel.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 12px;");
        
        // Enable/disable chance input based on checkbox
        expediteCollectionCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            expediteChanceInput.setDisable(!newVal);
            if (!newVal) {
                expediteChanceInput.setText("50");
            } else {
                expediteChanceInput.requestFocus();
            }
        });
        
        HBox chanceBox = new HBox(8, chanceLabel, expediteChanceInput, percentLabel);
        chanceBox.setAlignment(Pos.CENTER_LEFT);
        chanceBox.setPadding(new Insets(0, 0, 0, 25));
        
        VBox expediteBox = new VBox(8, expediteCollectionCheck, chanceBox);
        
        // Debug logging toggle
        debugLoggingCheck = new CheckBox("Enable debug logging");
        debugLoggingCheck.setStyle(getCheckBoxStyle());
        debugLoggingCheck.setSelected(false);

        VBox advancedSection = new VBox(12, advancedSectionLabel, levelRow, expediteBox, debugLoggingCheck);
        advancedSection.setPadding(new Insets(0, 0, 20, 0));

        // ── Action Button Section ─────────────────────────────────
        confirmBtn = new Button("Start Hunting");
        confirmBtn.setPrefWidth(150);
        confirmBtn.setPrefHeight(40);
        confirmBtn.setStyle(getButtonStyle());
        
        // Add hover effect
        confirmBtn.setOnMouseEntered(e -> confirmBtn.setStyle(getButtonHoverStyle()));
        confirmBtn.setOnMouseExited(e -> confirmBtn.setStyle(getButtonStyle()));
        
        confirmBtn.setOnAction(e -> {
            saveStrategyOptions();
            ((Stage) getScene().getWindow()).close(); 
            
            // Always set requiresCustomAnchor to true since we're always using TilePicker
            strategyOptions.put("requiresCustomAnchor", true);
            
            // Add expedite settings to strategy options
            strategyOptions.put("expediteCollection", isExpediteCollectionEnabled());
            strategyOptions.put("expediteChance", getExpediteCollectionChance());
            // Add debug logging setting
            strategyOptions.put("debugLogging", isDebugLoggingEnabled());
            
            // Notify the script that settings have been confirmed (FX thread)
            script.onSettingsSelected(
                getSelectedTarget(), 
                null, // No area selection anymore
                getSelectedStrategy(),
                isManualLevel(), 
                getManualLevel(),
                strategyOptions
            );
        });

        // Add all sections to content box
        contentBox.getChildren().addAll(
            targetSection,
            infoLbl,
            strategySection,
            advancedSection
        );
        
        // Button container with proper vertical centering
        VBox buttonContainer = new VBox(confirmBtn);
        buttonContainer.setAlignment(Pos.CENTER);
        buttonContainer.setPadding(new Insets(20, 25, 20, 25));  // Equal top/bottom padding for vertical centering
        buttonContainer.setStyle("-fx-background-color: " + DARKER_BG + ";");
        
        // Add all to main container
        getChildren().addAll(headerBox, contentBox, buttonContainer);
    }
    
    /**
     * Gets the CSS style for combo boxes
     */
    private String getComboBoxStyle() {
        return "-fx-background-color: " + INPUT_BG + "; " +
               "-fx-text-fill: " + TEXT_PRIMARY + "; " +
               "-fx-border-color: " + BORDER_COLOR + "; " +
               "-fx-border-radius: 4; " +
               "-fx-background-radius: 4; " +
               "-fx-font-size: 12px; " +
               "-fx-control-inner-background: " + INPUT_BG + "; " +
               "-fx-control-inner-background-alt: " + INPUT_FOCUS + "; " +
               "-fx-selection-bar: " + ACCENT_COLOR + "; " +
               "-fx-selection-bar-text: white;";
    }
    
    /**
     * Gets the CSS style for text fields
     */
    private String getTextFieldStyle() {
        return "-fx-background-color: " + INPUT_BG + "; " +
               "-fx-text-fill: " + TEXT_PRIMARY + "; " +
               "-fx-border-color: " + BORDER_COLOR + "; " +
               "-fx-border-radius: 4; " +
               "-fx-background-radius: 4; " +
               "-fx-font-size: 12px; " +
               "-fx-prompt-text-fill: " + TEXT_SECONDARY + ";";
    }
    
    /**
     * Gets the CSS style for checkboxes
     */
    private String getCheckBoxStyle() {
        return "-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 12px;";
    }
    
    /**
     * Gets the CSS style for the primary button
     */
    private String getButtonStyle() {
        return "-fx-background-color: " + ACCENT_COLOR + "; " +
               "-fx-text-fill: white; " +
               "-fx-font-size: 14px; " +
               "-fx-font-weight: bold; " +
               "-fx-background-radius: 6; " +
               "-fx-cursor: hand;";
    }
    
    /**
     * Gets the CSS style for the primary button on hover
     */
    private String getButtonHoverStyle() {
        return "-fx-background-color: " + ACCENT_HOVER + "; " +
               "-fx-text-fill: white; " +
               "-fx-font-size: 14px; " +
               "-fx-font-weight: bold; " +
               "-fx-background-radius: 6; " +
               "-fx-cursor: hand;";
    }
    
    /**
     * Applies custom styling to ComboBox cells for better dark theme support
     */
    private void styleComboBox(ComboBox<String> comboBox) {
        // Style the button cell (displayed item)
        comboBox.setButtonCell(new javafx.scene.control.ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";");
                }
            }
        });
        
        // Style the dropdown list cells
        comboBox.setCellFactory(listView -> new javafx.scene.control.ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; " +
                            "-fx-background-color: " + INPUT_BG + ";");
                    
                    // Hover effect
                    setOnMouseEntered(e -> setStyle("-fx-text-fill: white; " +
                            "-fx-background-color: " + ACCENT_COLOR + ";"));
                    setOnMouseExited(e -> setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; " +
                            "-fx-background-color: " + INPUT_BG + ";"));
                }
            }
        });
    }
    
    /**
     * Updates the strategy-specific options based on selected strategy.
     */
    private void updateStrategyOptions(String strategyName) {
        strategyOptionsContainer.getChildren().clear();
        strategyOptions.clear();
        
        if ("Line".equals(strategyName)) {
            // Add Line-specific options
            Label optionsLabel = new Label("Line Pattern Options");
            optionsLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 12px;");
            
            Label orientationLabel = new Label("Orientation:");
            orientationLabel.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 11px;");
            orientationLabel.setMinWidth(70);
            
            lineOrientationDropdown = new ComboBox<>();
            lineOrientationDropdown.getItems().addAll("Horizontal", "Vertical");
            lineOrientationDropdown.getSelectionModel().selectFirst();
            lineOrientationDropdown.setPrefWidth(140);
            lineOrientationDropdown.setStyle(getComboBoxStyle());
            styleComboBox(lineOrientationDropdown);
            
            HBox orientationRow = new HBox(10, orientationLabel, lineOrientationDropdown);
            orientationRow.setAlignment(Pos.CENTER_LEFT);
            
            strategyOptionsContainer.getChildren().addAll(
                optionsLabel, orientationRow
            );
        } else if ("Auto".equals(strategyName)) {
            // Show info about Auto mode
            Label infoLabel = new Label("Automatic Pattern Selection");
            infoLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 12px;");
            
            Label descLabel = new Label("Automatically selects optimal pattern:\n" +
                                      "• 1-2 traps: Line\n" +
                                      "• 3 traps: L-Pattern\n" + 
                                      "• 4 traps: Cross\n" +
                                      "• 5 traps: X-Pattern");
            descLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + TEXT_SECONDARY + ";");
            descLabel.setWrapText(true);
            
            strategyOptionsContainer.getChildren().addAll(infoLabel, descLabel);
        } else if ("Custom".equals(strategyName)) {
            // Show info about Custom mode
            Label infoLabel = new Label("Custom Tile Selection");
            infoLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 12px;");
            
            Label descLabel = new Label("Select multiple trap positions in-game.\n" +
                                      "You'll be able to choose exact tiles\n" +
                                      "where traps should be placed.");
            descLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + TEXT_SECONDARY + ";");
            descLabel.setWrapText(true);
            
            strategyOptionsContainer.getChildren().addAll(infoLabel, descLabel);
        } else {
            // For other patterns, just show a simple description
            Label infoLabel = new Label(strategyName + " Pattern");
            infoLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 12px;");
            
            Label noteLabel = new Label("Will use custom center selected in-game");
            noteLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + TEXT_SECONDARY + ";");
            
            strategyOptionsContainer.getChildren().addAll(infoLabel, noteLabel);
        }
    }
    
    /**
     * Saves the current strategy options to the map.
     */
    private void saveStrategyOptions() {
        String strategy = getSelectedStrategy();
        
        // Always use custom anchor now
        strategyOptions.put("requiresCustomAnchor", true);
        
        if ("Line".equals(strategy)) {
            if (lineOrientationDropdown != null) {
                strategyOptions.put("lineOrientation", lineOrientationDropdown.getValue());
            }
        }
    }

    public String getSelectedTarget() {
        return targetDropdown.getValue();
    }

    // Area selection removed - always using TilePicker

    public String getSelectedStrategy() {
        return strategyDropdown.getValue();
    }
    
    public Map<String, Object> getStrategyOptions() {
        return new HashMap<>(strategyOptions);
    }

    public boolean isManualLevel() {
        return manualLevelCheck.isSelected();
    }

    public int getManualLevel() {
        try {
            return Integer.parseInt(levelInput.getText().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Gets whether debug logging is enabled.
     */
    public boolean isDebugLoggingEnabled() {
        return debugLoggingCheck.isSelected();
    }
    
    // Area dropdown removed - using TilePicker for location selection
    
    /**
     * Gets whether expedite collection is enabled.
     * @return true if expedite collection is enabled
     */
    public boolean isExpediteCollectionEnabled() {
        return expediteCollectionCheck.isSelected();
    }
    
    /**
     * Gets the expedite collection chance.
     * @return The chance (0-100), defaults to 50 if invalid
     */
    public int getExpediteCollectionChance() {
        if (!expediteCollectionCheck.isSelected() || expediteChanceInput.getText().isEmpty()) {
            return 50;
        }
        
        try {
            int chance = Integer.parseInt(expediteChanceInput.getText());
            return Math.max(0, Math.min(100, chance)); // Clamp to 0-100
        } catch (NumberFormatException e) {
            return 50;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Convenience helper to show the window and block until closed       */
    /* ------------------------------------------------------------------ */
    public static ScriptOptions showAndWait(JorkHunter script, HuntingConfig config) {
        ScriptOptions pane = new ScriptOptions(script, config);
        Scene scene        = new Scene(pane);
        scene.setFill(javafx.scene.paint.Color.web(DARK_BG));
        Stage stage        = new Stage();
        stage.setTitle("JorkHunter – Configuration");
        stage.setScene(scene);
        stage.setMinWidth(450);
        stage.setMinHeight(520);
        stage.setWidth(470);
        stage.setHeight(540);
        stage.setResizable(false);  // Prevent resizing for consistent appearance
        stage.showAndWait();
        return pane;
    }
} 
