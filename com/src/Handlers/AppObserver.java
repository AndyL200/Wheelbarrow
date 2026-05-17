package Handlers;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.prefs.Preferences;

import Components.Config.LocalProfile;
import Network.CallClient;
import Network.CallObj;
import Network.CallServer;
import Network.ChatObj;
import Network.Client;
import Network.Server;

public class AppObserver {
    public static volatile AppObserver INSTANCE;
    private CallObj currentCall;
    private ChatObj currentChat;
    private HashMap<String, Server> chatHostings;
    private LocalProfile localProfile;

    public static final boolean DEBUG_MODE = Preferences.userRoot().node("wheelbarrow/debug").getBoolean("mode", false);
    public static final boolean VERBOSE = Preferences.userRoot().node("wheelbarrow/debug").getBoolean("verbose", false);
    public static final boolean CONCURRENCY = Preferences.userRoot().node("wheelbarrow/debug").getBoolean("concurrency", false);

    AppObserver() {
        if (INSTANCE != null) {
            throw new IllegalStateException("AppObserver already initialized");
        }
        currentCall = null;
        currentChat = null;
        chatHostings = new LinkedHashMap<>();
        localProfile = new LocalProfile();
    }

    public static AppObserver getInstance() {
        if (INSTANCE == null) {
            synchronized (AppObserver.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AppObserver();
                }
            }
        }
        return INSTANCE;
    }

    public ChatObj getCurrentChat() {
        return currentChat;
    }
    public CallObj getCurrentCall() {
        return currentCall;
    }
    public List<Server> getChatHostings() {
        ArrayList<Server> copy = new ArrayList<>(chatHostings.values());
        return copy;
    }

    public void openIncomingChat() {
        if (this.currentChat == null) {
            this.currentChat = new Server();
        }
        else {
            if (DEBUG_MODE) {
                System.out.println("Chat already open");
            }
        }
    }
    public void openOutgoingChat(InetAddress address, int port) {
        if (this.currentChat == null) {
            this.currentChat = new Client(address, port);
        }
        else {
            if (DEBUG_MODE) {
                System.out.println("Chat already open");
            }
        }
    }
    public void openIncomingCall() {
        if (this.currentCall == null) {
            this.currentCall = new CallServer();
        }
        else {
            if (DEBUG_MODE) {
                System.out.println("Call already open");
            }
        }
    }

    public void openOutgoingCall(String address, int port) {
        if (this.currentCall == null) {
            this.currentCall = new CallClient(address, port);
        }
        else {
            if (DEBUG_MODE) {
                System.out.println("Call already open");
            }
        }
    }

    public LocalProfile getLocalProfile() {
        return localProfile;
    }


    public int loadProfile(String username) {
        return localProfile.login(username);
    }
    public int loadProfile(String username, String password) {
        return localProfile.login(username, password);
    }

}
