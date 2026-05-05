package Scenes;

import Components.Config.LocalProfile;
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

public class SettingsScene extends AppSceneTemplate {

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

        // Apply inline styles (no external CSS needed)
        applyStyles();

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
        darkMode.setSelected(true); // default
        lightMode.getStyleClass().add("settings-radio");
        darkMode.getStyleClass().add("settings-radio");

        themeGroup.selectedToggleProperty().addListener((obs, old, nw) -> {
            if (nw == lightMode) applyTheme("light");
            else                 applyTheme("dark");
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
        colorPicker.setOnAction(e -> applyBackgroundColor(colorPicker.getValue()));
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
                applyBackgroundImage(chosen.getAbsolutePath());
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

        String username = LocalProfile.getUsername();
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
        requirePassword.setSelected(LocalProfile.isPasswordProtected());
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

    // ── Action handlers (wire to your actual app logic) ───────────────────────

    private void applyTheme(String mode) {
        System.out.println("Theme: " + mode);
        // TODO: toggle your app-wide stylesheet
    }

    private void applyBackgroundColor(Color color) {
        System.out.println("BG color: " + color);
        // TODO: push to app state
    }

    private void applyBackgroundImage(String path) {
        System.out.println("BG image: " + path);
        // TODO: push to app state
    }

    private void handleLogout(VBox pane) {
        try {
            LocalProfile.delete();
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

    // ── Inline styles ─────────────────────────────────────────────────────────

    private void applyStyles() {
        root.setStyle("""
            -fx-background-color: #0f0f17;
            -fx-font-family: 'Segoe UI', sans-serif;
        """);

        // Sidebar
        sidebar.setStyle("""
            -fx-background-color: #16161f;
            -fx-border-color: #2a2a3d;
            -fx-border-width: 0 1 0 0;
        """);

        // Apply style classes via inline lookup — done via scene stylesheet
        // Add a stylesheet string programmatically
        String css = """
            .sidebar-header {
                -fx-text-fill: #e0e0ff;
                -fx-font-size: 16px;
                -fx-font-weight: bold;
                -fx-padding: 0 0 8 4;
            }
            .sidebar-btn {
                -fx-background-color: transparent;
                -fx-text-fill: #9090b0;
                -fx-font-size: 13px;
                -fx-padding: 9 14;
                -fx-alignment: CENTER-LEFT;
                -fx-cursor: hand;
                -fx-background-radius: 6;
                -fx-border-width: 0;
            }
            .sidebar-btn:hover {
                -fx-background-color: #22223a;
                -fx-text-fill: #d0d0f0;
            }
            .sidebar-btn-active {
                -fx-background-color: #2a2a50;
                -fx-text-fill: #a0a8ff;
                -fx-font-weight: bold;
            }
            .settings-content {
                -fx-background-color: #0f0f17;
            }
            .settings-section-title {
                -fx-text-fill: #a0a8ff;
                -fx-font-size: 12px;
                -fx-font-weight: bold;
                -fx-text-transform: uppercase;
                -fx-padding: 0 0 4 0;
            }
            .settings-label {
                -fx-text-fill: #c0c0e0;
                -fx-font-size: 13px;
                -fx-min-width: 180;
            }
            .settings-hint {
                -fx-text-fill: #6060a0;
                -fx-font-size: 12px;
            }
            .settings-check .box {
                -fx-background-color: #22223a;
                -fx-border-color: #4040a0;
                -fx-border-radius: 3;
            }
            .settings-check:selected .box {
                -fx-background-color: #5050c8;
            }
            .settings-check {
                -fx-text-fill: #c0c0e0;
                -fx-font-size: 13px;
            }
            .settings-radio {
                -fx-text-fill: #c0c0e0;
                -fx-font-size: 13px;
            }
            .settings-radio .radio {
                -fx-border-color: #4040a0;
                -fx-background-color: #22223a;
            }
            .settings-radio:selected .radio {
                -fx-background-color: #5050c8;
            }
            .settings-btn {
                -fx-background-color: #2a2a50;
                -fx-text-fill: #c0c0ff;
                -fx-font-size: 13px;
                -fx-padding: 8 18;
                -fx-background-radius: 6;
                -fx-cursor: hand;
                -fx-border-width: 0;
            }
            .settings-btn:hover {
                -fx-background-color: #3a3a70;
            }
            .settings-btn-danger {
                -fx-background-color: #3a1a2a;
                -fx-text-fill: #ff8080;
            }
            .settings-btn-danger:hover {
                -fx-background-color: #5a2a3a;
            }
            .settings-separator {
                -fx-background-color: #2a2a3d;
            }
            .settings-slider .track {
                -fx-background-color: #2a2a4a;
                -fx-pref-height: 4px;
            }
            .settings-slider .thumb {
                -fx-background-color: #6060d0;
                -fx-background-radius: 50%;
                -fx-pref-width: 16px;
                -fx-pref-height: 16px;
            }
            .settings-slider .thumb:hover {
                -fx-background-color: #8080ff;
            }
        """;

        // Write to temp file and load — or inject via data URI
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("wheelbarrow-settings", ".css");
            java.nio.file.Files.writeString(tmp, css);
            root.getStylesheets().add(tmp.toUri().toString());
        } catch (Exception e) {
            System.out.println("Could not load settings CSS: " + e.getMessage());
        }
    }
}