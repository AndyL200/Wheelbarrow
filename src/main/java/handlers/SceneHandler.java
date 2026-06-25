package handlers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import scenes.*;
import javafx.stage.Stage;

public class SceneHandler {

    private static SceneHandler INSTANCE;

    private AppScene currentScene;
    private Stage stage;

    private static final String SERVER_LIST_PATH;
    static {
        //just use a resources?
        Path p = Paths.get("").toAbsolutePath();
        Path filePath = p.resolve("com").resolve("temp_server_list").resolve("server_list.json");
        SERVER_LIST_PATH = filePath.toString();
    }

    // ── Singleton ─────────────────────────────────────────────────────────────

    private SceneHandler(Stage stage, AppScene scene) {
        this.stage = stage;
        this.currentScene = scene;
        ThemeManager.getInstance().applySceneTheme(scene);
        this.stage.setScene(scene);
        if (scene instanceof ChatScene) {
            handleChat();
        }
    }

    /** Call once at app startup. Throws if called again. */
    public static SceneHandler init(Stage stage, AppScene scene) {
        if (INSTANCE != null) {
            throw new IllegalStateException("SceneHandler already initialized");
        }
        INSTANCE = new SceneHandler(stage, scene);
        return INSTANCE;
    }

    /** Access the singleton after init. Throws if not yet initialized. */
    public static SceneHandler get() {
        if (INSTANCE == null) {
            throw new IllegalStateException("SceneHandler not initialized — call init() first");
        }
        return INSTANCE;
    }
    // ── Scene switching ───────────────────────────────────────────────────────

    public void switchScene(AppScene newScene) {
        this.currentScene = newScene;
        ThemeManager.getInstance().applySceneTheme(newScene);
        this.stage.setScene(newScene);
        if (newScene instanceof ChatScene) {
            handleChat();
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private void handleChat() {
        if (!(currentScene instanceof ChatScene chat)) return;
        try {
            String json = Files.readString(Paths.get(SERVER_LIST_PATH));
            chat.initServerList(json);
        } catch (IOException e) {
            System.err.println("Error reading server list: " + e.getMessage());
        }
    }

    public AppScene getCurrentScene() {
        return currentScene;
    }
}