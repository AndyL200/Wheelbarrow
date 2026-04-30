package Handlers;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.Scene;

/**
 * ThemeManager — singleton that manages light/dark theme switching.
 *
 * Usage:
 *   ThemeManager.getInstance().registerScene(myScene);     // apply current theme immediately
 *   ThemeManager.getInstance().switchTheme(Theme.LIGHT);   // switch all registered scenes
 *
 * Resilience: if the CSS files are missing from the classpath, embedded fallback
 * CSS strings are served as data URIs so the app is always styled.
 */
public class ThemeManager {

    public enum Theme { DARK, LIGHT }

    private static final ThemeManager INSTANCE = new ThemeManager();

    private Theme currentTheme = Theme.DARK;
    private final List<Scene> registeredScenes = new ArrayList<>();

    private ThemeManager() {}

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    /** Register a scene and apply the current theme to it immediately. */
    public void registerScene(Scene scene) {
        registeredScenes.add(scene);
        applyTheme(scene);
    }

    /** Switch the theme for all registered scenes. */
    public void switchTheme(Theme theme) {
        this.currentTheme = theme;
        for (Scene scene : registeredScenes) {
            applyTheme(scene);
        }
    }

    public Theme getCurrentTheme() {
        return currentTheme;
    }

    /** Apply the current theme to a specific scene. */
    public void applyTheme(Scene scene) {
        scene.getStylesheets().clear();
        scene.getStylesheets().add(loadCss("/Styles/baseStyles.css", BASE_CSS));
        if (currentTheme == Theme.DARK) {
            scene.getStylesheets().add(loadCss("/Styles/darkTheme.css", DARK_THEME_CSS));
        } else {
            scene.getStylesheets().add(loadCss("/Styles/lightTheme.css", LIGHT_THEME_CSS));
        }
    }

    /** Add an additional stylesheet to a specific scene using ThemeManager's loader. */
    public void addSceneCss(Scene scene, String classpathPath, String fallbackCss) {
        scene.getStylesheets().add(loadCss(classpathPath, fallbackCss));
    }

    // -------------------------------------------------------------------------
    // CSS loading with embedded fallback
    // -------------------------------------------------------------------------

    private String loadCss(String classpathPath, String fallbackCss) {
        try {
            URL url = getClass().getResource(classpathPath);
            if (url != null) {
                return url.toExternalForm();
            }
        } catch (Exception e) {
            System.err.println("ThemeManager: could not load CSS file '" + classpathPath + "': " + e.getMessage());
        }
        System.err.println("ThemeManager: falling back to embedded CSS for '" + classpathPath + "'");
        String encoded = URLEncoder.encode(fallbackCss, StandardCharsets.UTF_8).replace("+", "%20");
        return "data:text/css;charset=utf-8," + encoded;
    }

    // -------------------------------------------------------------------------
    // Embedded CSS fallback strings
    // Kept in sync with the .css files — updated whenever the files change.
    // -------------------------------------------------------------------------

    private static final String BASE_CSS =
        ".scroll-pane{-fx-background-color:transparent;-fx-background-insets:0;-fx-padding:0;}" +
        ".scroll-pane>.viewport{-fx-background-color:transparent;}" +
        ".scroll-pane>.corner{-fx-background-color:transparent;}" +
        ".sidebar-style{-fx-padding:8px 4px;}" +
        ".sidebar-core{-fx-padding:8px 4px;}" +
        ".sidebar-add-btn-label{-fx-font-size:26px;-fx-font-weight:bold;}" +
        ".server-entry-selected{-fx-border-width:2;-fx-border-radius:8;-fx-background-radius:8;}" +
        ".navbar{-fx-padding:8px 16px;-fx-alignment:center-left;-fx-min-height:44px;-fx-max-height:52px;-fx-border-width:0 0 1 0;}" +
        ".server-title-label{-fx-font-size:15px;-fx-font-weight:bold;}" +
        ".audio-call-btn,.video-call-btn,.disconnect-btn{-fx-border-color:transparent;-fx-padding:0;-fx-background-radius:6;-fx-border-radius:6;-fx-cursor:hand;}" +
        ".audio-call-btn-active,.video-call-btn-active{-fx-border-width:2;-fx-background-radius:6;-fx-border-radius:6;}" +
        ".send-box{-fx-padding:10px;-fx-spacing:8px;-fx-min-height:52px;-fx-max-height:62px;-fx-alignment:center;-fx-border-width:1 0 0 0;}" +
        ".msg-input{-fx-font-size:13px;-fx-padding:8px 12px;-fx-background-radius:6;-fx-border-radius:6;-fx-border-width:1;}" +
        ".send-btn{-fx-padding:8px 18px;-fx-font-size:13px;-fx-cursor:hand;-fx-background-radius:6;-fx-border-radius:6;-fx-font-weight:bold;-fx-border-color:transparent;}" +
        ".message-label{-fx-padding:6px 12px;-fx-background-radius:8;-fx-border-radius:8;-fx-font-size:13px;-fx-wrap-text:true;-fx-max-width:infinity;}" +
        ".audio-call-container,.video-call-container{-fx-border-width:2;-fx-border-radius:12;-fx-background-radius:12;}" +
        ".audio-user-placeholder,.video-user-placeholder{-fx-border-width:1;-fx-border-radius:8;-fx-background-radius:8;-fx-alignment:center;}" +
        ".audio-user-label,.video-user-label{-fx-font-size:12px;}" +
        ".audio-call-controls,.video-call-controls{-fx-padding:15px;-fx-border-radius:8;-fx-background-radius:8;-fx-spacing:12px;}" +
        ".audio-control-btn,.video-control-btn{-fx-padding:8px 16px;-fx-font-size:13px;-fx-background-radius:6;-fx-border-radius:6;-fx-cursor:hand;-fx-border-color:transparent;}" +
        ".audio-end-call-btn,.video-end-call-btn{-fx-padding:8px 16px;-fx-font-size:13px;-fx-background-radius:6;-fx-border-radius:6;-fx-cursor:hand;-fx-border-color:transparent;}" +
        ".audio-exit-btn,.video-exit-btn{-fx-padding:8px 16px;-fx-font-size:13px;-fx-background-radius:6;-fx-border-radius:6;-fx-cursor:hand;-fx-border-color:transparent;}" +
        ".add-server-content{-fx-border-radius:12;-fx-background-radius:12;}" +
        ".add-server-exit-label{-fx-font-size:13px;-fx-font-weight:bold;}" +
        ".add-server-title{-fx-font-size:20px;-fx-font-weight:bold;-fx-padding:5px 0 12px 0;}" +
        ".add-server-label{-fx-font-size:13px;}" +
        ".add-server-submit-btn,.add-server-create-btn{-fx-padding:10px 0;-fx-font-size:13px;-fx-cursor:hand;-fx-background-radius:6;-fx-border-radius:6;-fx-pref-width:220px;-fx-border-color:transparent;}";

    private static final String DARK_THEME_CSS =
        ".root{-fx-base:#253545;-fx-background:#1E2A38;-fx-control-inner-background:#253545;-fx-accent:#5C9EE6;}" +
        ".scroll-bar{-fx-background-color:#1A2530;}" +
        ".scroll-bar>.thumb{-fx-background-color:#3E6B8C;-fx-background-radius:4;}" +
        ".scroll-bar>.track{-fx-background-color:#1E2A38;}" +
        ".scroll-bar>.increment-button,.scroll-bar>.decrement-button{-fx-background-color:#1A2530;-fx-padding:2;}" +
        ".sidebar-style{-fx-background-color:#1A2530;}" +
        ".sidebar-core{-fx-background-color:#1A2530;}" +
        ".sidebar-add-btn-circle{-fx-fill:#2D4157;-fx-stroke:#3E6B8C;-fx-stroke-width:2;}" +
        ".sidebar-add-btn-circle-hover{-fx-fill:#3A5268;-fx-stroke:#5C9EE6;-fx-stroke-width:2;}" +
        ".sidebar-add-btn-label{-fx-text-fill:#E8EDF2;}" +
        ".server-entry-selected{-fx-border-color:#5C9EE6;}" +
        ".navbar{-fx-background-color:#253545;-fx-border-color:#1A2530;}" +
        ".server-title-label{-fx-text-fill:#E8EDF2;}" +
        ".audio-call-btn{-fx-background-color:transparent;}" +
        ".audio-call-btn-hover{-fx-background-color:rgba(255,255,255,0.08);}" +
        ".audio-call-btn-active{-fx-background-color:rgba(102,187,106,0.25);-fx-border-color:#66BB6A;}" +
        ".video-call-btn{-fx-background-color:transparent;}" +
        ".video-call-btn-hover{-fx-background-color:rgba(255,255,255,0.08);}" +
        ".video-call-btn-active{-fx-background-color:rgba(66,165,245,0.25);-fx-border-color:#42A5F5;}" +
        ".disconnect-btn{-fx-background-color:transparent;}" +
        ".disconnect-btn-hover{-fx-background-color:rgba(239,83,80,0.18);}" +
        ".chat-comp{-fx-background-color:#1E2A38;}" +
        ".chat-box{-fx-background-color:#1E2A38;}" +
        ".send-box{-fx-background-color:#253545;-fx-border-color:#1A2530;}" +
        ".msg-input{-fx-background-color:#2D4157;-fx-text-fill:#E8EDF2;-fx-prompt-text-fill:#8FA8BF;-fx-border-color:#3E6B8C;}" +
        ".msg-input:focused{-fx-border-color:#5C9EE6;}" +
        ".send-btn{-fx-background-color:#2563EB;-fx-text-fill:#ffffff;}" +
        ".send-btn:hover{-fx-background-color:#1D4EBB;}" +
        ".message-label{-fx-text-fill:#E8EDF2;-fx-background-color:#2D4157;}" +
        ".audio-call-comp{-fx-background-color:rgba(0,0,0,0.85);}" +
        ".audio-call-container{-fx-background-color:#253545;-fx-border-color:#66BB6A;}" +
        ".audio-call-users{-fx-background-color:transparent;}" +
        ".audio-user-placeholder{-fx-background-color:#1E2A38;-fx-border-color:#3E6B8C;}" +
        ".audio-user-label{-fx-text-fill:#E8EDF2;}" +
        ".audio-call-controls{-fx-background-color:#1E2A38;}" +
        ".audio-control-btn{-fx-background-color:#66BB6A;-fx-text-fill:#ffffff;}" +
        ".audio-control-btn:hover{-fx-background-color:#57A05B;}" +
        ".audio-end-call-btn{-fx-background-color:#EF5350;-fx-text-fill:#ffffff;}" +
        ".audio-end-call-btn:hover{-fx-background-color:#C62828;}" +
        ".audio-exit-btn{-fx-background-color:#546E7A;-fx-text-fill:#ffffff;}" +
        ".audio-exit-btn:hover{-fx-background-color:#455A64;}" +
        ".video-call-comp{-fx-background-color:rgba(0,0,0,0.85);}" +
        ".video-call-container{-fx-background-color:#253545;-fx-border-color:#42A5F5;}" +
        ".video-call-users{-fx-background-color:transparent;}" +
        ".video-user-placeholder{-fx-background-color:#1E2A38;-fx-border-color:#3E6B8C;}" +
        ".video-user-label{-fx-text-fill:#E8EDF2;}" +
        ".video-call-controls{-fx-background-color:#1E2A38;}" +
        ".video-control-btn{-fx-background-color:#42A5F5;-fx-text-fill:#ffffff;}" +
        ".video-control-btn:hover{-fx-background-color:#1976D2;}" +
        ".video-end-call-btn{-fx-background-color:#EF5350;-fx-text-fill:#ffffff;}" +
        ".video-end-call-btn:hover{-fx-background-color:#C62828;}" +
        ".video-exit-btn{-fx-background-color:#546E7A;-fx-text-fill:#ffffff;}" +
        ".video-exit-btn:hover{-fx-background-color:#455A64;}" +
        ".add-server-dark-bg{-fx-background-color:rgba(0,0,0,0.65);}" +
        ".add-server-content{-fx-background-color:#253545;}" +
        ".add-server-exit-circle{-fx-fill:#EF5350;-fx-stroke:transparent;}" +
        ".add-server-exit-label{-fx-text-fill:#ffffff;}" +
        ".add-server-title{-fx-text-fill:#E8EDF2;}" +
        ".add-server-label{-fx-text-fill:#8FA8BF;}" +
        ".add-server-submit-btn{-fx-background-color:#2563EB;-fx-text-fill:#ffffff;}" +
        ".add-server-submit-btn:hover{-fx-background-color:#1D4EBB;}" +
        ".add-server-create-btn{-fx-background-color:#2D6A31;-fx-text-fill:#ffffff;}" +
        ".add-server-create-btn:hover{-fx-background-color:#255829;}";

    private static final String LIGHT_THEME_CSS =
        ".root{-fx-base:#FFFFFF;-fx-background:#F0F4F8;-fx-control-inner-background:#FFFFFF;-fx-accent:#2563EB;}" +
        ".scroll-bar{-fx-background-color:#DDE6F0;}" +
        ".scroll-bar>.thumb{-fx-background-color:#7AA0C4;-fx-background-radius:4;}" +
        ".scroll-bar>.track{-fx-background-color:#ECF2F8;}" +
        ".scroll-bar>.increment-button,.scroll-bar>.decrement-button{-fx-background-color:#DDE6F0;-fx-padding:2;}" +
        ".sidebar-style{-fx-background-color:#DDE6F0;}" +
        ".sidebar-core{-fx-background-color:#DDE6F0;}" +
        ".sidebar-add-btn-circle{-fx-fill:#B0C8E0;-fx-stroke:#7AA0C4;-fx-stroke-width:2;}" +
        ".sidebar-add-btn-circle-hover{-fx-fill:#9AB8D4;-fx-stroke:#2563EB;-fx-stroke-width:2;}" +
        ".sidebar-add-btn-label{-fx-text-fill:#1A2433;}" +
        ".server-entry-selected{-fx-border-color:#2563EB;}" +
        ".navbar{-fx-background-color:#FFFFFF;-fx-border-color:#DDE6F0;}" +
        ".server-title-label{-fx-text-fill:#1A2433;}" +
        ".audio-call-btn{-fx-background-color:transparent;}" +
        ".audio-call-btn-hover{-fx-background-color:rgba(0,0,0,0.06);}" +
        ".audio-call-btn-active{-fx-background-color:rgba(67,160,71,0.18);-fx-border-color:#43A047;}" +
        ".video-call-btn{-fx-background-color:transparent;}" +
        ".video-call-btn-hover{-fx-background-color:rgba(0,0,0,0.06);}" +
        ".video-call-btn-active{-fx-background-color:rgba(30,136,229,0.18);-fx-border-color:#1E88E5;}" +
        ".disconnect-btn{-fx-background-color:transparent;}" +
        ".disconnect-btn-hover{-fx-background-color:rgba(229,57,53,0.12);}" +
        ".chat-comp{-fx-background-color:#F0F4F8;}" +
        ".chat-box{-fx-background-color:#F0F4F8;}" +
        ".send-box{-fx-background-color:#FFFFFF;-fx-border-color:#DDE6F0;}" +
        ".msg-input{-fx-background-color:#FFFFFF;-fx-text-fill:#1A2433;-fx-prompt-text-fill:#8FA8BF;-fx-border-color:#B0C8E0;}" +
        ".msg-input:focused{-fx-border-color:#2563EB;}" +
        ".send-btn{-fx-background-color:#2563EB;-fx-text-fill:#ffffff;}" +
        ".send-btn:hover{-fx-background-color:#1D4EBB;}" +
        ".message-label{-fx-text-fill:#1A2433;-fx-background-color:#FFFFFF;}" +
        ".audio-call-comp{-fx-background-color:rgba(0,0,0,0.45);}" +
        ".audio-call-container{-fx-background-color:#FFFFFF;-fx-border-color:#43A047;}" +
        ".audio-call-users{-fx-background-color:transparent;}" +
        ".audio-user-placeholder{-fx-background-color:#F0F4F8;-fx-border-color:#B0C8E0;}" +
        ".audio-user-label{-fx-text-fill:#1A2433;}" +
        ".audio-call-controls{-fx-background-color:#F0F4F8;}" +
        ".audio-control-btn{-fx-background-color:#43A047;-fx-text-fill:#ffffff;}" +
        ".audio-control-btn:hover{-fx-background-color:#388E3C;}" +
        ".audio-end-call-btn{-fx-background-color:#E53935;-fx-text-fill:#ffffff;}" +
        ".audio-end-call-btn:hover{-fx-background-color:#C62828;}" +
        ".audio-exit-btn{-fx-background-color:#78909C;-fx-text-fill:#ffffff;}" +
        ".audio-exit-btn:hover{-fx-background-color:#607D8B;}" +
        ".video-call-comp{-fx-background-color:rgba(0,0,0,0.45);}" +
        ".video-call-container{-fx-background-color:#FFFFFF;-fx-border-color:#1E88E5;}" +
        ".video-call-users{-fx-background-color:transparent;}" +
        ".video-user-placeholder{-fx-background-color:#F0F4F8;-fx-border-color:#B0C8E0;}" +
        ".video-user-label{-fx-text-fill:#1A2433;}" +
        ".video-call-controls{-fx-background-color:#F0F4F8;}" +
        ".video-control-btn{-fx-background-color:#1E88E5;-fx-text-fill:#ffffff;}" +
        ".video-control-btn:hover{-fx-background-color:#1565C0;}" +
        ".video-end-call-btn{-fx-background-color:#E53935;-fx-text-fill:#ffffff;}" +
        ".video-end-call-btn:hover{-fx-background-color:#C62828;}" +
        ".video-exit-btn{-fx-background-color:#78909C;-fx-text-fill:#ffffff;}" +
        ".video-exit-btn:hover{-fx-background-color:#607D8B;}" +
        ".add-server-dark-bg{-fx-background-color:rgba(0,0,0,0.4);}" +
        ".add-server-content{-fx-background-color:#FFFFFF;}" +
        ".add-server-exit-circle{-fx-fill:#E53935;-fx-stroke:transparent;}" +
        ".add-server-exit-label{-fx-text-fill:#ffffff;}" +
        ".add-server-title{-fx-text-fill:#1A2433;}" +
        ".add-server-label{-fx-text-fill:#5A7490;}" +
        ".add-server-submit-btn{-fx-background-color:#2563EB;-fx-text-fill:#ffffff;}" +
        ".add-server-submit-btn:hover{-fx-background-color:#1D4EBB;}" +
        ".add-server-create-btn{-fx-background-color:#388E3C;-fx-text-fill:#ffffff;}" +
        ".add-server-create-btn:hover{-fx-background-color:#2E7D32;}";

    public static final String LOGIN_CSS =
        ".login-root{-fx-alignment:center;}" +
        ".login-app-title{-fx-font-size:28px;-fx-font-weight:bold;-fx-text-fill:-fx-text-base-color;}" +
        ".login-card{-fx-background-color:rgba(255,255,255,0.02);-fx-padding:20px;-fx-border-radius:8px;-fx-background-radius:8px;}" +
        ".login-welcome{-fx-font-size:16px;-fx-font-weight:bold;}" +
        ".login-hint{-fx-font-size:12px;-fx-text-fill:-fx-prompt-text-fill;}" +
        ".login-field{-fx-pref-width:100%;-fx-padding:8px 10px;-fx-background-radius:6px;-fx-border-radius:6px;}" +
        ".login-btn{-fx-padding:8px 12px;-fx-background-radius:6px;-fx-border-radius:6px;-fx-cursor:hand;}" +
        ".login-link{-fx-text-fill:-fx-accent;-fx-underline:true;}" +
        ".login-error{-fx-text-fill:#ff6b6b;-fx-font-size:12px;}";

}
