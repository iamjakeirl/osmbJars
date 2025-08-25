package com.jork.script.jorkHunter.javafx;

import com.jork.script.jorkHunter.JorkHunter;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal JavaFX options window for the JorkHunter script.
 * Provides configuration drop-downs:
 *   – Target (currently only "Birds")
 *   – Area   (currently only "Kourend Woodland")
 *   – Placement Strategy (trap placement patterns)
 * More values can be added later without changing consuming code.
 */
public class ScriptOptions extends VBox {

    private final ComboBox<String> targetDropdown;
    private final ComboBox<String> areaDropdown;
    private final ComboBox<String> strategyDropdown;
    private final Button            confirmBtn;
    private final CheckBox manualLevelCheck;
    private final TextField levelInput;
    
    // Dynamic strategy options
    private VBox strategyOptionsContainer;
    private final Map<String, Object> strategyOptions = new HashMap<>();
    
    // X-Pattern specific controls
    private Spinner<Integer> maxCenterDistanceSpinner;
    private CheckBox recenterOnEmptyCheck;

    public ScriptOptions(JorkHunter script) {
        setSpacing(15);
        setAlignment(Pos.CENTER);
        setPadding(new Insets(20));
        setMinWidth(320);
        setPrefWidth(350);

        // ── Target selection ────────────────────────────────
        Label targetLbl      = new Label("Select target:");
        targetLbl.setMinWidth(120);
        targetDropdown       = new ComboBox<>();
        targetDropdown.getItems().addAll("Birds");
        targetDropdown.getSelectionModel().selectFirst();
        targetDropdown.setPrefWidth(150);
        HBox targetRow = new HBox(targetLbl, targetDropdown);
        targetRow.setSpacing(10);
        targetRow.setAlignment(Pos.CENTER_LEFT);

        // ── Area selection ──────────────────────────────────
        Label areaLbl  = new Label("Select area:");
        areaLbl.setMinWidth(120);
        areaDropdown   = new ComboBox<>();
        areaDropdown.getItems().addAll("Kourend Woodland");
        areaDropdown.getSelectionModel().selectFirst();
        areaDropdown.setPrefWidth(150);
        HBox areaRow   = new HBox(areaLbl, areaDropdown);
        areaRow.setSpacing(10);
        areaRow.setAlignment(Pos.CENTER_LEFT);

        // ── Placement Strategy selection ─────────────────────
        Label strategyLbl = new Label("Placement strategy:");
        strategyLbl.setMinWidth(120);
        strategyDropdown  = new ComboBox<>();
        strategyDropdown.getItems().addAll("No Cardinal", "X-Pattern");
        strategyDropdown.getSelectionModel().select("X-Pattern");  // Set X-Pattern as default
        strategyDropdown.setPrefWidth(150);
        HBox strategyRow  = new HBox(strategyLbl, strategyDropdown);
        strategyRow.setSpacing(10);
        strategyRow.setAlignment(Pos.CENTER_LEFT);
        
        // ── Dynamic Strategy Options Container ────────────
        strategyOptionsContainer = new VBox(8);
        strategyOptionsContainer.setAlignment(Pos.CENTER_LEFT);
        strategyOptionsContainer.setPadding(new Insets(10, 0, 10, 0));
        strategyOptionsContainer.setMinHeight(80);
        
        // Listen for strategy changes to update options
        strategyDropdown.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateStrategyOptions(newVal);
        });
        
        // Initialize with default strategy options
        updateStrategyOptions(strategyDropdown.getValue());

        // ── Manual Hunter Level ───────────────────────────
        manualLevelCheck = new CheckBox("Enter hunter level manually");
        levelInput = new TextField();
        levelInput.setPromptText("Level 1-99");
        levelInput.setDisable(true);
        manualLevelCheck.selectedProperty().addListener((obs, o, n) -> levelInput.setDisable(!n));

        // ── Confirm button ─────────────────────────────────
        confirmBtn = new Button("Start");
        confirmBtn.setPrefWidth(100);
        confirmBtn.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        confirmBtn.setOnAction(e -> {
            saveStrategyOptions();
            ((Stage) getScene().getWindow()).close(); 
            
            // Notify the script that settings have been confirmed (FX thread)
            script.onSettingsSelected(
                getSelectedTarget(), 
                getSelectedArea(), 
                getSelectedStrategy(),
                isManualLevel(), 
                getManualLevel(),
                strategyOptions
            );
        });

        getChildren().addAll(targetRow, areaRow, strategyRow, strategyOptionsContainer, 
                            manualLevelCheck, levelInput, confirmBtn);
    }
    
    /**
     * Updates the strategy-specific options based on selected strategy.
     */
    private void updateStrategyOptions(String strategyName) {
        strategyOptionsContainer.getChildren().clear();
        strategyOptions.clear();
        
        if ("X-Pattern".equals(strategyName)) {
            // Add X-Pattern specific options
            Label optionsLabel = new Label("X-Pattern Options:");
            optionsLabel.setStyle("-fx-font-weight: bold");
            
            // Max center distance spinner
            Label distanceLabel = new Label("Max center distance:");
            maxCenterDistanceSpinner = new Spinner<>();
            SpinnerValueFactory<Integer> distanceFactory = 
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 10, 1);  // Default to 1
            maxCenterDistanceSpinner.setValueFactory(distanceFactory);
            maxCenterDistanceSpinner.setMaxWidth(80);
            HBox distanceRow = new HBox(5, distanceLabel, maxCenterDistanceSpinner);
            
            // Recenter on empty checkbox
            recenterOnEmptyCheck = new CheckBox("Recenter when no traps placed");
            recenterOnEmptyCheck.setSelected(true);  // Default to recentering
            
            strategyOptionsContainer.getChildren().addAll(
                optionsLabel, distanceRow, recenterOnEmptyCheck
            );
        }
        // Add more strategy-specific options here as needed
    }
    
    /**
     * Saves the current strategy options to the map.
     */
    private void saveStrategyOptions() {
        String strategy = getSelectedStrategy();
        
        if ("X-Pattern".equals(strategy)) {
            if (maxCenterDistanceSpinner != null) {
                strategyOptions.put("maxCenterDistance", maxCenterDistanceSpinner.getValue());
            }
            if (recenterOnEmptyCheck != null) {
                strategyOptions.put("recenterOnEmpty", recenterOnEmptyCheck.isSelected());
            }
        }
    }

    public String getSelectedTarget() {
        return targetDropdown.getValue();
    }

    public String getSelectedArea() {
        return areaDropdown.getValue();
    }

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

    /* ------------------------------------------------------------------ */
    /* Convenience helper to show the window and block until closed       */
    /* ------------------------------------------------------------------ */
    public static ScriptOptions showAndWait(JorkHunter script) {
        ScriptOptions pane = new ScriptOptions(script);
        Scene scene        = new Scene(pane);
        Stage stage        = new Stage();
        stage.setTitle("JorkHunter – Options");
        stage.setScene(scene);
        stage.setMinWidth(380);
        stage.setMinHeight(450);
        stage.setWidth(400);
        stage.setHeight(480);
        stage.setResizable(false);  // Prevent resizing for consistent appearance
        stage.showAndWait();
        return pane;
    }
} 