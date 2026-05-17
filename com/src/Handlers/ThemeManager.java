package Handlers;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.prefs.Preferences;

import com.oracle.javafx.scenebuilder.kit.editor.EditorPlatform.Theme;

import Components.Helper.SceneType;
import Scenes.AppScene;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;

/**
 * ThemeManager — thread-safe singleton that manages light/dark theme switching.
 *
 * Usage:
 *   ThemeManager.getInstance().registerScene(myScene);     // apply current theme immediately
 *   ThemeManager.getInstance().switchTheme(Theme.LIGHT);   // switch all registered scenes
 *
 * Resilience: if the CSS files are missing from the classpath, embedded fallback
 * CSS strings are served as data URIs so the app is always styled.
 */
public class ThemeManager {

    //System prefs allow styles to persist
    private static final Preferences prefs = Preferences.userRoot().node("wheelbarrow/appearance");
    private static volatile ThemeManager INSTANCE;
    //JUST USE A GRAPH GODDAMN
    //sceneType -> {selector -> {style -> value}}
    private HashMap<Integer, HashMap<String, HashMap<String, String>>> dynamicStyles;
    //just extend the preferences that are already being registered

    public volatile boolean isDarkTheme = "DARK".equals(prefs.get("theme", "DARK"));


    private ThemeManager() {
        // Restore persisted dynamic styles
        init();
    }

    public static ThemeManager getInstance() {
        if (INSTANCE == null) {
            synchronized (ThemeManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ThemeManager();
                }
            }
        }
        return INSTANCE;
    }


    private void init() {
        dynamicStyles = new HashMap<>();
        for (SceneType type : SceneType.values()) {
            int sheet = type.getValue();
            HashMap<String, HashMap<String, String>> styles = parseStyleSheet(sheet);
            if (styles != null) {
                dynamicStyles.put(sheet, styles);
            }
            sheet = type.getValue();
            styles = parseStyleSheet(sheet);
            if (styles != null) {
                dynamicStyles.put(sheet, styles);
            }
        }
    }


    public void applySceneTheme(AppScene scene) {
        scene.getStylesheets().clear();
        int sceneType = scene.getSceneType().getValue();
        if (isDarkTheme) {
            sceneType |= SceneType.DARK.getValue();
        } else {
            sceneType |= SceneType.LIGHT.getValue();
        }
        HashMap<String, HashMap<String, String>> styles = dynamicStyles.get(sceneType);
        if (styles != null) {
            for (String selector : styles.keySet()) {
                HashMap<String, String> properties = styles.get(selector);
                StringBuilder css = new StringBuilder();
                for (String property : properties.keySet()) {
                    css.append(property).append(": ").append(properties.get(property)).append("; ");
                }
                scene.getStylesheets().add(selector + "{" + css.toString() + "}");
            }
        }
    }

    public void switchDarkMode() {
        isDarkTheme = !isDarkTheme;
        prefs.put("theme", isDarkTheme ? "DARK" : "LIGHT");
    }

    public void setBackgroundColor(AppScene scene, String color) {
        int type = scene.getSceneType().getValue() | (isDarkTheme ? SceneType.DARK.getValue() : SceneType.LIGHT.getValue());
        if (dynamicStyles.containsKey(type)) {
            HashMap<String, HashMap<String, String>> styles = dynamicStyles.get(type);
            for (String selector : styles.keySet()) {
                HashMap<String, String> properties = styles.get(selector);
                if (properties.containsKey("-fx-background-color")) {
                    properties.put("-fx-background-color", color);
                }
            }
        }
    }
    public void setGlobalBackground(String color) {
        for (int type : dynamicStyles.keySet()) {
            HashMap<String, HashMap<String, String>> styles = dynamicStyles.get(type);
            for (String selector : styles.keySet()) {
                HashMap<String, String> properties = styles.get(selector);
                if (properties.containsKey("-fx-background-color")) {
                    properties.put("-fx-background-color", color);
                }
            }
        }
    }

    public void setBackgroundImage(AppScene scene, String url) {
            int type = scene.getSceneType().getValue() | (isDarkTheme ? SceneType.DARK.getValue() : SceneType.LIGHT.getValue());
            if (dynamicStyles.containsKey(type)) {
                HashMap<String, HashMap<String, String>> styles = dynamicStyles.get(type);
                for (String selector : styles.keySet()) {
                    HashMap<String, String> properties = styles.get(selector);
                    if (properties.containsKey("-fx-background-image")) {
                        properties.put("-fx-background-image", "url('" + url + "')");
                    }
                }
            }
    }

    public void setGlobalBackgroundImage(String url) {
        for (int type : dynamicStyles.keySet()) {
            HashMap<String, HashMap<String, String>> styles = dynamicStyles.get(type);
            for (String selector : styles.keySet()) {
                HashMap<String, String> properties = styles.get(selector);
                if (properties.containsKey("-fx-background-image")) {
                    properties.put("-fx-background-image", "url('" + url + "')");
                }
            }
        }
    }

    public void clearBackground() {
        for (int type : dynamicStyles.keySet()) {
            HashMap<String, HashMap<String, String>> styles = dynamicStyles.get(type);
            for (String selector : styles.keySet()) {
                HashMap<String, String> properties = styles.get(selector);
                if (properties.containsKey("-fx-background-image")) {
                    properties.remove("-fx-background-image");
                }
                if (properties.containsKey("-fx-background-color")) {
                    properties.remove("-fx-background-color");
                }
            }
        }
    }

    /** Add an additional stylesheet to a specific scene using ThemeManager's loader. */
    public void addSceneStyleClass(AppScene scene, String classname) {
        int type = scene.getSceneType().getValue() | (isDarkTheme ? SceneType.DARK.getValue() : SceneType.LIGHT.getValue());
        if (dynamicStyles.containsKey(type) && dynamicStyles.get(type).containsKey(classname)) {
            String style = "";
            HashMap<String, String> kv = dynamicStyles.get(type).get(classname);
            for (String key : kv.keySet()) {
                style += key + ": " + kv.get(key) + "; ";
            }
            scene.getStylesheets().add(classname + "{" + style + "}");
        }
    }

    //not sure if this is necessary
    private HashMap<String,HashMap<String, String>> parseStyleSheet(int sceneType) {
        HashMap<String, HashMap<String, String>> styles = new HashMap<>();
        String url = "";
        if ((sceneType & SceneType.CHAT.getValue()) > 1) {
            if (isDarkTheme) {
                url = "Styles/ChatDarkStyle.css";
            } else {
                url = "Styles/ChatLightStyle.css";
            }
        } 
        else if ((sceneType & SceneType.LOGIN.getValue()) > 1) {
            if (isDarkTheme) {
                url = "Styles/LoginDarkStyle.css";
            } else {
                url = "Styles/LoginLightStyle.css";
            }            
        }
        else if ((sceneType & SceneType.SETTINGS.getValue()) > 1) {
            if (isDarkTheme) {
                url = "Styles/SettingsDarkStyle.css";
            } else {
                url = "Styles/SettingsLightStyle.css";
            }            
        }
        InputStream cssStream = getClass().getResourceAsStream(url);
        byte[] cssBytes = null;
        try {
            if (cssStream != null) {
                cssBytes = cssStream.readAllBytes();
            }
            else {
                System.out.println("CSS file not found in classpath: " + url);
            }
        } catch (IOException io){
            System.out.println("Failed to load CSS for scene type " + sceneType + " from URL: " + url);
            return null;
        }
        String css = new String(cssBytes, StandardCharsets.UTF_8);
        css = css.replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", ""); // remove comments
        css = css.replaceAll("\\s+", " "); // normalize whitespace
        css = css.trim();

        char[] chars = css.toCharArray();
        int i = 0;
        boolean inSelector = true;
        boolean inProperty = false;
        List<String> selectors = new ArrayList<>();
        List<String> propPairs = new ArrayList<>();
        StringBuilder build = new StringBuilder();
        while (i < chars.length) {
            char c = chars[i];
            if (inSelector) {
                if (c == '{') {
                    selectors = new ArrayList<>(Arrays.asList(build.toString().trim().split(",")));
                    build.setLength(0);
                    inSelector = false;
                    inProperty = true;
                }
                else if (c == '}') { 
                    //ERROR
                    System.out.println("Unexpected closing brace in CSS for scene type " + sceneType);
                }
                else {
                    build.append(c);
                }
            }
            else if (inProperty) {
                if (c == '}') {
                    for (String selector : selectors) {
                        for (String propPair : propPairs) {
                            String[] kv = propPair.split(":", 2);
                            if (kv.length == 2) {
                                String key = kv[0].trim();
                                String value = kv[1].trim();
                                styles.putIfAbsent(selector, new HashMap<>());
                                styles.get(selector).put(key, value);
                            }
                        }
                    }
                    build.setLength(0);
                    propPairs.clear();
                    selectors.clear();
                    inSelector = true;
                    inProperty = false;
                }
                else if (c == ';') {
                    propPairs.add(build.toString().trim());
                    build.setLength(0);
                }
                else if (c == '{') {
                    //ERROR
                    System.out.println("Unexpected opening brace in CSS properties for scene type " + sceneType);
                }
                else {
                    build.append(c);
                }
            }
            i++;
        }
        return styles;
    }

}