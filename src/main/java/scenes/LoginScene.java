package scenes;

import java.util.List;
import java.util.function.Consumer;

import components.config.LocalProfile;
import components.config.User;
import components.helper.SceneType;
import handlers.AppObserver;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/**
 * JavaFX scene shown on application startup.
 *
 * Displays a horizontal list of all existing users from LocalProfile.listUsers().
 * Users can select a profile to sign in or create a new account.
 *
 * Layout proportions (4:5:3:2):
 * - Title (40%)
 * - User image (50%)
 * - User username (30%)
 * - SignIn/CreateView (20%)
 *
 * On successful sign-in or account creation the {@code onLogin} callback
 * is invoked with the chosen username.
 */
public class LoginScene extends AppScene {

    private Consumer<String> onLogin;
    private StackPane root;
    private Label usernameLabel;
    private ImageView userImageView;
    private VBox formContainer;
    private Label errorLabel;

    public LoginScene(int width, int height) {
        super(width, height);
        this.root = new StackPane();
        buildUI();
        this.setRoot(this.root);
    }

    //adapt to existing size
    public LoginScene() {
        super();
        this.root = new StackPane();
        buildUI();
        this.root.setMaxHeight(Double.MAX_VALUE);
        this.root.setMaxWidth(Double.MAX_VALUE);
        this.setRoot(this.root);
    }

    private void buildUI() {
        this.root.getStyleClass().add("login-root");
        this.root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // Main vertical layout with proportional spacing
        VBox mainLayout = new VBox(0);
        mainLayout.setStyle("-fx-spacing: 0;");
        mainLayout.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // 1. Title section (4/14 ≈ 28.6%)
        Label appTitle = new Label("Wheelbarrow");
        appTitle.getStyleClass().add("login-app-title");
        VBox titleContainer = new VBox(appTitle);
        titleContainer.setAlignment(Pos.CENTER);
        titleContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(titleContainer, Priority.NEVER);
        titleContainer.setStyle("-fx-min-height: 100; -fx-pref-height: 100;"); // 4 parts = ~100px example

        // 2. Horizontal user list
        HBox userListContainer = buildUserList();
        VBox.setVgrow(userListContainer, Priority.NEVER);
        userListContainer.setStyle("-fx-min-height: 150; -fx-pref-height: 150; -fx-padding: 10;");

        // 3. User image section (5/14 ≈ 35.7%)
        userImageView = new ImageView();
        userImageView.setFitHeight(150);
        userImageView.setFitWidth(150);
        userImageView.setPreserveRatio(true);
        Circle clip = new Circle(75);
        userImageView.setClip(clip);
        VBox imageContainer = new VBox(userImageView);
        imageContainer.setAlignment(Pos.CENTER);
        imageContainer.setStyle("-fx-pref-height: 150;");
        VBox.setVgrow(imageContainer, Priority.NEVER);

        // 4. Username section (3/14 ≈ 21.4%)
        usernameLabel = new Label("Select a user");
        usernameLabel.getStyleClass().add("login-welcome");
        usernameLabel.setWrapText(true);
        VBox usernameContainer = new VBox(usernameLabel);
        usernameContainer.setAlignment(Pos.CENTER);
        usernameContainer.setStyle("-fx-pref-height: 90;");
        VBox.setVgrow(usernameContainer, Priority.NEVER);

        // 5. Error label (global)
        errorLabel = new Label("");
        errorLabel.getStyleClass().add("login-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        // 6. Form container (SignIn/CreateView) (2/14 ≈ 14.3%)
        formContainer = new VBox(14);
        formContainer.setStyle("-fx-pref-height: 100;");
        formContainer.getChildren().add(errorLabel);
        VBox.setVgrow(formContainer, Priority.NEVER);

        // Assemble main layout
        mainLayout.getChildren().addAll(
            titleContainer,
            userListContainer,
            imageContainer,
            usernameContainer,
            formContainer
        );

        StackPane.setAlignment(mainLayout, Pos.CENTER);
        this.root.getChildren().add(mainLayout);
    }

    /** Build horizontal list of users from LocalProfile.listUsers() */
    private HBox buildUserList() {
        HBox userList = new HBox(15);
        userList.setAlignment(Pos.CENTER);
        userList.setStyle("-fx-padding: 10;");

        List<User> users = LocalProfile.listUsers();
        
        for (User user : users) {
            VBox userItem = createUserPortrait(user);
            userList.getChildren().add(userItem);
        }

        // "Create new account" button
        VBox createNewItem = createNewAccountButton();
        userList.getChildren().add(createNewItem);

        ScrollPane scroll = new ScrollPane(userList);
        scroll.setFitToHeight(true);
        scroll.setStyle("-fx-control-inner-background: transparent;");
        HBox.setHgrow(scroll, Priority.ALWAYS);

        HBox container = new HBox(scroll);
        HBox.setHgrow(container, Priority.ALWAYS);
        return container;
    }

    /** Create a clickable user portrait card */
    private VBox createUserPortrait(User user) {
        VBox portrait = new VBox(8);
        portrait.setPrefWidth(100);
        portrait.setAlignment(Pos.CENTER);
        portrait.getStyleClass().add("user-portrait-card");
        portrait.setStyle("-fx-border-color: #ccc; -fx-border-radius: 8; -fx-padding: 10; -fx-cursor: hand;");

        // User image
        ImageView img = new ImageView();
        img.setFitHeight(80);
        img.setFitWidth(80);
        img.setPreserveRatio(true);
        String imgUrl = user.getImgUrl();
        if (imgUrl != null && !imgUrl.isEmpty()) {
            try {
                Image userImage = new Image(imgUrl);
                img.setImage(userImage);
            } catch (Exception e) {
                img.setStyle("-fx-text-fill: #999;");
            }
        } else {
            img.setStyle("-fx-text-fill: #999;");
        }
        Circle clip = new Circle(40);
        img.setClip(clip);

        // Username label
        Label nameLabel = new Label(user.getUsername());
        nameLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-alignment: center;");
        nameLabel.setWrapText(true);

        portrait.getChildren().addAll(img, nameLabel);

        // Click to select this user
        portrait.setOnMouseClicked(e -> selectUser(user));

        return portrait;
    }

    /** Create "Create new account" button */
    private VBox createNewAccountButton() {
        VBox newAccountItem = new VBox();
        newAccountItem.setPrefWidth(100);
        newAccountItem.setAlignment(Pos.CENTER);
        newAccountItem.getStyleClass().add("user-portrait-card");
        newAccountItem.setStyle("-fx-border-color: #ccc; -fx-border-radius: 8; -fx-padding: 10; -fx-cursor: hand;");

        Label plusLabel = new Label("+");
        plusLabel.setStyle("-fx-font-size: 40; -fx-text-fill: #999;");

        Label createLabel = new Label("New Account");
        createLabel.setStyle("-fx-font-size: 12; -fx-text-alignment: center;");

        newAccountItem.getChildren().addAll(plusLabel, createLabel);

        newAccountItem.setOnMouseClicked(e -> selectUser(null));

        return newAccountItem;
    }

    /** Called when a user is selected or when creating a new account */
    private void selectUser(User user) {
        formContainer.getChildren().clear();
        errorLabel.setVisible(false);
        formContainer.getChildren().add(errorLabel);

        if (user != null) {
            // Update display
            usernameLabel.setText("Welcome back, " + user.getUsername() + "!");
            String imgUrl = user.getImgUrl();
            if (imgUrl != null && !imgUrl.isEmpty()) {
                try {
                    Image userImage = new Image(imgUrl);
                    userImageView.setImage(userImage);
                } catch (Exception e) {
                    userImageView.setImage(null);
                }
            }

            // Build sign-in form for this user
            buildSignInView(formContainer, user);
        } else {
            // Create new account
            usernameLabel.setText("Create Your Account");
            userImageView.setImage(null);
            buildCreateView(formContainer);
        }
    }

    /** Sign-in view: shown when a user is selected. */
    private void buildSignInView(VBox container, User user) {
        String storedUsername = user.getUsername();

        Label hint = new Label("Enter your password to continue.");
        hint.getStyleClass().add("login-hint");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.getStyleClass().add("login-field");

        Button signInBtn = new Button("Sign In");
        signInBtn.getStyleClass().add("login-btn");
        signInBtn.setMaxWidth(Double.MAX_VALUE);

        Runnable doSignIn = () -> {
            if (passwordField.getText().isEmpty() && LocalProfile.isPasswordProtected(storedUsername)) {
                showError(errorLabel, "Please enter your password.");
                return;
            }
            int login = AppObserver.getInstance().getLocalProfile().login(storedUsername, passwordField.getText());
            if (login == -1) {
                showError(errorLabel, "No users found.");
                return;
            }
            else if (login == 1) {
                showError(errorLabel, "Username not found.");
                return;
            }
            else if (login == 2) {
                showError(errorLabel, "Incorrect password.");
                return;
            }

            if (onLogin != null && login == 0) onLogin.accept(storedUsername);
        };

        signInBtn.setOnAction(e -> doSignIn.run());
        passwordField.setOnAction(e -> doSignIn.run());

        Hyperlink switchUser = new Hyperlink("Choose different user");
        switchUser.getStyleClass().add("login-link");
        switchUser.setOnAction(e -> {
            selectUser(null);
        });

        container.getChildren().addAll(hint, passwordField, signInBtn, switchUser);
    }

    /** Create-account view: shown when creating a new profile. */
    private void buildCreateView(VBox container) {
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
                if (onLogin != null) {
                    onLogin.accept(username);
                }  
            } catch (Exception ex) {
                showError(errorLabel, "Could not save profile: " + ex.getMessage());
            }
        };

        createBtn.setOnAction(e -> doCreate.run());
        usernameField.setOnAction(e -> {
            if (passwordField.getText().isEmpty()) doCreate.run();
            else passwordField.requestFocus();
        });
        passwordField.setOnAction(e -> doCreate.run());

        container.getChildren().addAll(subLabel, usernameField, pwLabel, passwordField, createBtn);
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

    public SceneType getSceneType() {
        return SceneType.LOGIN;
    }
}