package Scenes;

import java.io.IOException;
import java.util.function.Consumer;

import Components.Config.LocalProfile;
import Handlers.ThemeManager;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * JavaFX scene shown on application startup.
 *
 * If a local profile already exists the user is greeted by name and asked
 * only for a password (if the account is password-protected). If no profile
 * exists the user can choose a username and an optional password to create
 * their local account – like setting up a Linux user for the first time.
 *
 * On successful sign-in or account creation the {@code onLogin} callback
 * is invoked with the chosen username.
 */
public class LoginScene extends AppSceneTemplate {

    private Consumer<String> onLogin;
    private StackPane root;

    public LoginScene(int width, int height) {
        super(width, height);
        initStyles();
        this.root = new StackPane();
        buildUI();
        this.setRoot(this.root);
    }

    private void initStyles() {
        try {
            ThemeManager.getInstance().registerScene(this);
            ThemeManager.getInstance().addSceneCss(this, "/Styles/loginStyles.css", ThemeManager.LOGIN_CSS);
        } catch (Exception e) {
            System.err.println("Failed to load login styles: " + e.getMessage());
        }
    }

    private void buildUI() {
        this.root.getStyleClass().add("login-root");
        this.root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // App title shown above the card
        Label appTitle = new Label("OnlineCom");
        appTitle.getStyleClass().add("login-app-title");

        // Error label (hidden until needed)
        Label errorLabel = new Label("");
        errorLabel.getStyleClass().add("login-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        // Card that holds the form
        VBox card = new VBox(14);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(360);
        card.setAlignment(Pos.CENTER_LEFT);

        if (LocalProfile.hasProfile()) {
            buildSignInView(card, errorLabel);
        } else {
            buildCreateView(card, errorLabel);
        }

        card.getChildren().add(errorLabel);

        // Centre the title + card vertically and horizontally
        VBox wrapper = new VBox(20);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setMaxWidth(360);
        wrapper.getChildren().addAll(appTitle, card);

        StackPane.setAlignment(wrapper, Pos.CENTER);
        this.root.getChildren().add(wrapper);
    }

    /** Sign-in view: shown when a local profile already exists. */
    private void buildSignInView(VBox card, Label errorLabel) {
        String storedUsername = LocalProfile.getUsername();
        boolean needsPassword = LocalProfile.isPasswordProtected();

        Label welcomeLabel = new Label("Welcome back, " + storedUsername + "!");
        welcomeLabel.getStyleClass().add("login-welcome");
        welcomeLabel.setWrapText(true);

        Label hint = new Label(needsPassword
                ? "Enter your password to continue."
                : "Click Sign In to continue.");
        hint.getStyleClass().add("login-hint");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.getStyleClass().add("login-field");
        passwordField.setVisible(needsPassword);
        passwordField.setManaged(needsPassword);

        Button signInBtn = new Button("Sign In");
        signInBtn.getStyleClass().add("login-btn");
        signInBtn.setMaxWidth(Double.MAX_VALUE);

        Runnable doSignIn = () -> {
            if (needsPassword) {
                if (passwordField.getText().isEmpty()) {
                    showError(errorLabel, "Please enter your password.");
                    return;
                }
                if (!LocalProfile.checkPassword(passwordField.getText())) {
                    showError(errorLabel, "Incorrect password.");
                    return;
                }
            }
            if (onLogin != null) onLogin.accept(storedUsername);
        };

        signInBtn.setOnAction(e -> doSignIn.run());
        // Allow Enter key in the password field to trigger sign-in
        passwordField.setOnAction(e -> doSignIn.run());

        Hyperlink switchUser = new Hyperlink("Not you? Switch user");
        switchUser.getStyleClass().add("login-link");
        switchUser.setOnAction(e -> {
            try {
                LocalProfile.delete();
            } catch (IOException ex) {
                System.err.println("Could not delete profile: " + ex.getMessage());
            }
            root.getChildren().clear();
            buildUI();
        });

        card.getChildren().addAll(welcomeLabel, hint, passwordField, signInBtn, switchUser);
    }

    /** Create-account view: shown when no local profile exists yet. */
    private void buildCreateView(VBox card, Label errorLabel) {
        Label titleLabel = new Label("Create your account");
        titleLabel.getStyleClass().add("login-title");

        Label subLabel = new Label("Choose the name others will see when you chat.");
        subLabel.getStyleClass().add("login-hint");
        subLabel.setWrapText(true);

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.getStyleClass().add("login-field");

        Label pwLabel = new Label("Password (optional)");
        pwLabel.getStyleClass().add("login-hint");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Leave blank for no password");
        passwordField.getStyleClass().add("login-field");

        Button createBtn = new Button("Get Started");
        createBtn.getStyleClass().add("login-btn");
        createBtn.setMaxWidth(Double.MAX_VALUE);

        Runnable doCreate = () -> {
            String username = usernameField.getText().strip();
            if (username.isEmpty()) {
                showError(errorLabel, "Please enter a username.");
                return;
            }
            String password = passwordField.getText();
            try {
                if (password.isEmpty()) {
                    LocalProfile.create(username);
                } else {
                    LocalProfile.create(username, password);
                }
                if (onLogin != null) onLogin.accept(username);
            } catch (Exception ex) {
                showError(errorLabel, "Could not save profile: " + ex.getMessage());
            }
        };

        createBtn.setOnAction(e -> doCreate.run());
        // Allow Enter in the username field to submit (if no password intended)
        usernameField.setOnAction(e -> {
            if (passwordField.getText().isEmpty()) doCreate.run();
            else passwordField.requestFocus();
        });
        passwordField.setOnAction(e -> doCreate.run());

        card.getChildren().addAll(titleLabel, subLabel, usernameField, pwLabel, passwordField, createBtn);
    }

    private void showError(Label label, String msg) {
        label.setText(msg);
        label.setVisible(true);
        label.setManaged(true);
    }

    /** Called with the logged-in username when login or account creation succeeds. */
    public void setOnLogin(Consumer<String> onLogin) {
        this.onLogin = onLogin;
    }
}