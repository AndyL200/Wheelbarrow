package Scenes;

import Components.Config.LocalProfile;
import Components.Helper.SceneType;
import Handlers.AppObserver;
import Handlers.SceneHandler;
import Handlers.ThemeManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;

import java.io.File;

import com.oracle.javafx.scenebuilder.kit.editor.EditorPlatform.Theme;

public class SettingsScene extends AppScene {

    // Sidebar category buttons
    private static final String[] CATEGORIES = {
        "Appearance", "Account", "Performance"
    };

    private BorderPane root;
    private VBox sidebar;
    private StackPane contentArea;
    private String activeCategory = "Appearance";

    public SettingsScene(int width, int height) {
        super(width, height);
        build();
    }

    public SettingsScene() {
        super();
        build();
    }

    private void build() {
        root = new BorderPane();
        root.getStyleClass().add("settings-root");

        sidebar = buildSidebar();
        contentArea = new StackPane();
        contentArea.getStyleClass().add("settings-content");
        contentArea.setPadding(new Insets(32));

        root.setLeft(sidebar);
        root.setCenter(contentArea);

        showCategory("Appearance");

        // Assume AppSceneTemplate exposes a setRoot or getScene method
        // Adapt this line to match your actual template API:
        setRoot(root);
    }

    // ── Sidebar ──────────────────────────────────────────────────────────────

    private VBox buildSidebar() {
        VBox bar = new VBox(4);
        bar.getStyleClass().add("settings-sidebar");
        bar.setPadding(new Insets(24, 12, 24, 12));
        bar.setPrefWidth(180);

        Label header = new Label("Settings");
        header.getStyleClass().add("sidebar-header");
        bar.getChildren().add(header);

        Region spacer = new Region();
        spacer.setPrefHeight(16);
        bar.getChildren().add(spacer);

        for (String cat : CATEGORIES) {
            Button btn = new Button(cat);
            btn.getStyleClass().add("sidebar-btn");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setOnAction(e -> {
                showCategory(cat);
                // Update active styles
                bar.getChildren().stream()
                    .filter(n -> n instanceof Button)
                    .forEach(n -> n.getStyleClass().remove("sidebar-btn-active"));
                btn.getStyleClass().add("sidebar-btn-active");
            });
            if (cat.equals(activeCategory)) btn.getStyleClass().add("sidebar-btn-active");
            bar.getChildren().add(btn);
        }

        Button backBtn = new Button("Back to chat");
        backBtn.getStyleClass().addAll("sidebar-btn", "sidebar-back-btn");
        backBtn.setOnMouseClicked((e) -> {
            SceneHandler.get().switchScene(new ChatScene());
        });
        bar.getChildren().add(backBtn);
        return bar;
    }

    // ── Category routing ─────────────────────────────────────────────────────

    private void showCategory(String category) {
        activeCategory = category;
        contentArea.getChildren().clear();
        switch (category) {
            case "Appearance"   -> contentArea.getChildren().add(buildAppearancePane());
            case "Account"      -> contentArea.getChildren().add(buildAccountPane());
            case "Performance"  -> contentArea.getChildren().add(buildPerformancePane());
        }
    }

    // ── Appearance ───────────────────────────────────────────────────────────

    private Node buildAppearancePane() {
        VBox pane = new VBox(24);
        pane.setAlignment(Pos.TOP_LEFT);

        pane.getChildren().add(sectionTitle("Theme"));

        // Light / Dark toggle
        ToggleGroup themeGroup = new ToggleGroup();
        RadioButton lightMode = new RadioButton("Light");
        RadioButton darkMode  = new RadioButton("Dark");
        lightMode.setToggleGroup(themeGroup);
        darkMode.setToggleGroup(themeGroup);
        darkMode.setSelected(ThemeManager.getInstance().isDarkTheme); // default
        lightMode.setSelected(!ThemeManager.getInstance().isDarkTheme);
        lightMode.getStyleClass().add("settings-radio");
        darkMode.getStyleClass().add("settings-radio");

        themeGroup.selectedToggleProperty().addListener((obs, old, nw) -> {
            if (old == nw || nw == null) {return;}
            System.out.println("[Scenes.SettingsScene] Theme toggle");
            ThemeManager.getInstance().switchDarkMode();
        });

        HBox themeRow = new HBox(16, lightMode, darkMode);
        themeRow.setAlignment(Pos.CENTER_LEFT);
        pane.getChildren().add(themeRow);

        pane.getChildren().add(separator());
        pane.getChildren().add(sectionTitle("Background"));

        // Background color picker
        HBox colorRow = new HBox(12);
        colorRow.setAlignment(Pos.CENTER_LEFT);
        Label colorLabel = new Label("Background color");
        colorLabel.getStyleClass().add("settings-label");
        ColorPicker colorPicker = new ColorPicker(Color.web("#1a1a2e"));
        colorPicker.getStyleClass().add("settings-control");
        colorPicker.setOnAction(e -> ThemeManager.getInstance().setGlobalBackground(colorPicker.getValue().toString()));
        colorRow.getChildren().addAll(colorLabel, colorPicker);
        pane.getChildren().add(colorRow);

        // Background image
        HBox imageRow = new HBox(12);
        imageRow.setAlignment(Pos.CENTER_LEFT);
        Label imageLabel = new Label("Background image");
        imageLabel.getStyleClass().add("settings-label");
        Button browseBtn = new Button("Choose file…");
        browseBtn.getStyleClass().add("settings-btn");
        Label imagePathLabel = new Label("None");
        imagePathLabel.getStyleClass().add("settings-hint");
        browseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select background image");
            fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
            );
            File chosen = fc.showOpenDialog(root.getScene() != null ? root.getScene().getWindow() : null);
            if (chosen != null) {
                imagePathLabel.setText(chosen.getName());
                ThemeManager.getInstance().setGlobalBackgroundImage(chosen.getAbsolutePath());
            }
        });

        CheckBox useImageCheck = new CheckBox("Enable");
        useImageCheck.getStyleClass().add("settings-check");
        useImageCheck.selectedProperty().addListener((obs, old, nw) -> {
            browseBtn.setDisable(!nw);
            if (!nw) imagePathLabel.setText("None");
        });
        browseBtn.setDisable(true);
        imageRow.getChildren().addAll(imageLabel, useImageCheck, browseBtn, imagePathLabel);
        pane.getChildren().add(imageRow);

        return pane;
    }

    // ── Account ──────────────────────────────────────────────────────────────

    private Node buildAccountPane() {
        VBox pane = new VBox(24);
        pane.setAlignment(Pos.TOP_LEFT);

        pane.getChildren().add(sectionTitle("Session"));

        LocalProfile profile = AppObserver.getInstance().getLocalProfile();
        String username = (profile != null && profile.getUser() != null) ? profile.getUser().getUsername() : null;
        boolean loggedIn = username != null;

        Label whoLabel = new Label(loggedIn ? "Logged in as: " + username : "Not logged in");
        whoLabel.getStyleClass().add("settings-label");
        pane.getChildren().add(whoLabel);

        HBox actionRow = new HBox(12);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        if (loggedIn) {
            Button logoutBtn = new Button("Log out");
            logoutBtn.getStyleClass().addAll("settings-btn", "settings-btn-danger");
            logoutBtn.setOnAction(e -> handleLogout(pane));

            Button switchBtn = new Button("Switch account");
            switchBtn.getStyleClass().add("settings-btn");
            switchBtn.setOnAction(e -> handleSwitchAccount());

            actionRow.getChildren().addAll(logoutBtn, switchBtn);
        } else {
            Button loginBtn = new Button("Log in");
            loginBtn.getStyleClass().add("settings-btn");
            loginBtn.setOnAction(e -> handleLogin());
            actionRow.getChildren().add(loginBtn);
        }

        pane.getChildren().add(actionRow);

        pane.getChildren().add(separator());
        pane.getChildren().add(sectionTitle("Security"));

        CheckBox requirePassword = new CheckBox("Require password on launch");
        requirePassword.getStyleClass().add("settings-check");
        requirePassword.setSelected(profile != null && profile.isPasswordProtected());
        requirePassword.selectedProperty().addListener((obs, old, nw) -> handlePasswordToggle(nw));
        pane.getChildren().add(requirePassword);

        return pane;
    }

    // ── Performance ──────────────────────────────────────────────────────────

    private Node buildPerformancePane() {
        VBox pane = new VBox(24);
        pane.setAlignment(Pos.TOP_LEFT);

        pane.getChildren().add(sectionTitle("Storage"));

        CheckBox cacheCheck = new CheckBox("Cache server data to disk");
        cacheCheck.getStyleClass().add("settings-check");
        cacheCheck.setSelected(true);
        pane.getChildren().add(cacheCheck);

        pane.getChildren().add(separator());
        pane.getChildren().add(sectionTitle("Resource limits"));

        pane.getChildren().add(sliderRow(
            "CPU usage limit",
            0, 100, 80, "%",
            val -> System.out.println("CPU limit: " + val + "%")
        ));

        pane.getChildren().add(sliderRow(
            "Memory usage limit",
            256, 4096, 1024, " MB",
            val -> System.out.println("Memory limit: " + val + " MB")
        ));

        return pane;
    }

    // ── Reusable helpers ─────────────────────────────────────────────────────

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("settings-section-title");
        return label;
    }

    private Separator separator() {
        Separator sep = new Separator();
        sep.getStyleClass().add("settings-separator");
        return sep;
    }

    /** A labeled slider row with live value readout. */
    private Node sliderRow(String label, double min, double max, double initial,
                           String unit, java.util.function.DoubleConsumer onChange) {
        VBox container = new VBox(6);

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label nameLabel = new Label(label);
        nameLabel.getStyleClass().add("settings-label");
        Label valueLabel = new Label((int) initial + unit);
        valueLabel.getStyleClass().add("settings-hint");
        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);
        header.getChildren().addAll(nameLabel, spring, valueLabel);

        Slider slider = new Slider(min, max, initial);
        slider.getStyleClass().add("settings-slider");
        slider.setShowTickMarks(false);
        slider.setMajorTickUnit((max - min) / 4);
        slider.setBlockIncrement((max - min) / 20);
        slider.setPrefWidth(360);

        slider.valueProperty().addListener((obs, old, nw) -> {
            int v = nw.intValue();
            valueLabel.setText(v + unit);
            onChange.accept(v);
        });

        container.getChildren().addAll(header, slider);
        return container;
    }

   
    private void handleLogout(VBox pane) {
        try {
            //LocalProfile.delete();
            showCategory("Account"); // refresh panel
        } catch (Exception e) {
            System.out.println("Logout error: " + e.getMessage());
        }
    }

    private void handleSwitchAccount() {
        // TODO: navigate to login/account selection scene
        System.out.println("Switch account");
    }

    private void handleLogin() {
        // TODO: navigate to login scene
        System.out.println("Login");
    }

    private void handlePasswordToggle(boolean enabled) {
        // TODO: show password entry dialog if enabling
        System.out.println("Password protection: " + enabled);
    }
    public SceneType getSceneType() {
        return SceneType.SETTINGS;
    }
}