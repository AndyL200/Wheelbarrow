package Handlers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

import Components.Helper.SceneEvent;
import Scenes.*;
import javafx.stage.Stage;

public class SceneHandler implements AppObserver {

    private static SceneHandler instance;

    private AppSceneTemplate currentScene;
    private Stage stage;

    private static final String SERVER_LIST_PATH;
    static {
        Path p = Paths.get("").toAbsolutePath();
        Path filePath = p.resolve("com").resolve("temp_server_list").resolve("server_list.json");
        SERVER_LIST_PATH = filePath.toString();
    }

    // Stored handler references for clean unsubscribe
    private final Consumer<Object> gotoChatHandler     = _ -> switchScene(new ChatScene());
    private final Consumer<Object> gotoSettingsHandler = _ -> switchScene(new SettingsScene());
    private final Consumer<Object> gotoLoginHandler    = _ -> switchScene(new LoginScene());

    // ── Singleton ─────────────────────────────────────────────────────────────

    private SceneHandler(Stage stage, AppSceneTemplate scene) {
        this.stage = stage;
        this.currentScene = scene;
        this.stage.setScene(scene);
        register();
        if (scene instanceof ChatScene) handleChat();
    }

    /** Call once at app startup. Throws if called again. */
    public static SceneHandler init(Stage stage, AppSceneTemplate scene) {
        if (instance != null) {
            throw new IllegalStateException("SceneHandler already initialized");
        }
        instance = new SceneHandler(stage, scene);
        return instance;
    }

    /** Access the singleton after init. Throws if not yet initialized. */
    public static SceneHandler get() {
        if (instance == null) {
            throw new IllegalStateException("SceneHandler not initialized — call init() first");
        }
        return instance;
    }

    // ---Trying an event bus and singleton pattern for now

    @Override
    public void register() {
        EventBus.subscribe(SceneEvent.GOTO_CHAT,     gotoChatHandler);
        EventBus.subscribe(SceneEvent.GOTO_SETTINGS, gotoSettingsHandler);
        EventBus.subscribe(SceneEvent.GOTO_LOGIN,    gotoLoginHandler);
    }

    @Override
    public void unregister() {
        EventBus.unsubscribe(SceneEvent.GOTO_CHAT,     gotoChatHandler);
        EventBus.unsubscribe(SceneEvent.GOTO_SETTINGS, gotoSettingsHandler);
        EventBus.unsubscribe(SceneEvent.GOTO_LOGIN,    gotoLoginHandler);
    }

    // ── Scene switching ───────────────────────────────────────────────────────

    public void switchScene(AppSceneTemplate newScene) {
        this.currentScene = newScene;
        this.stage.setScene(newScene);
        if (newScene instanceof ChatScene) handleChat();
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

    public AppSceneTemplate getCurrentScene() {
        return currentScene;
    }
}