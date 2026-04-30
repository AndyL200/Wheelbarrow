package Scenes;

import java.net.InetAddress;

import org.json.JSONObject;

import Components.ChatComp;
import Components.ServerEntry;
import Components.ServerOverlay;
import Components.Sidebar;
import Components.Helper.ServerList;
import Network.Client;
import Network.Server;
import Network.ServerInfo;
import Network.User;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Pair;

public class ChatScene extends AppSceneTemplate {
    private Sidebar sidebar;
    private ChatComp chatComp;
    private ServerList serverList;
    private StackPane root;
    private User u = null;
    private HBox hbox;
    private String username;

    private void initStyles() {
        try {
            String css = getClass().getResource("/Styles/chatStyles.css").toExternalForm();
            getStylesheets().add(css);
        } catch (Exception e) {
            System.err.println("Failed to load styles: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public ChatScene() {
        super();
        this.username = null;
        init();
    }

    public ChatScene(int width, int height) {
        super(width, height);
        this.username = null;
        init();
    }

    /** Preferred constructor – receives the local display name chosen at login. */
    public ChatScene(int width, int height, String username) {
        super(width, height);
        this.username = username;
        init();
    }

    private void init() {
        initStyles();

        this.root = new StackPane();
        this.hbox = new HBox();

        this.sidebar = new Sidebar();
        this.sidebar.setOnAddServer(this::openServerOverlay);
        this.sidebar.setOnServerSelect(this::enterServer);

        this.chatComp = null;

        this.root.setMaxHeight(Double.MAX_VALUE);
        this.root.setMaxWidth(Double.MAX_VALUE);
        this.hbox.setFillHeight(true);

        this.hbox.getChildren().add(this.sidebar);
        this.root.getChildren().add(this.hbox);
        this.setRoot(this.root);
    }

    private void exchangeChatComp() {
        if (u == null) {
            return;
        }
        if (this.chatComp != null && u == this.chatComp.getUser()) {
            //do nothing
            return;
        }
        //Does anything need to be done to the old chatComp?
        if (this.chatComp != null) {
            int chatCompIndex = this.hbox.getChildren().indexOf(this.chatComp);
            this.hbox.getChildren().remove(this.chatComp);
            this.chatComp = new ChatComp(u);
            this.hbox.getChildren().add(chatCompIndex, this.chatComp);
        } else {
            this.chatComp = new ChatComp(u);
            this.hbox.getChildren().add(this.chatComp);
        }
    }

    public void openServerOverlay() {
        ServerOverlay overlay = new ServerOverlay();
        overlay.setOnClose(() -> this.root.getChildren().remove(overlay));
        overlay.setOnServerFound(this::addServerToSidebar);
        overlay.setOnHostServer(this::createServer);
        this.root.getChildren().add(overlay);
    }

    public void createServer() {
        //Grab local IP and port directly from the running server instance
        Server server = new Server();
        if (server.socket == null) {
            System.err.println("Server failed to start, socket is null");
            return;
        }
        if (username != null) {
            server.setDisplayName(username);
        }
        int port = server.socket.getLocalPort(); //default port
        addServerToSidebar(new Pair<>(server.getAddress(), port));
        setUser(server);
    }

    public void enterServer(ServerInfo info) {
        if (info == null) {
            System.err.println("From enterServer(), ServerInfo is null, cannot enter server");
            return;
        }
        if (u != null && info.SERVER_ADDRESS.equals(u.getAddress())) {
            //entering a server that you are already in
            return;
        }
        Client c = new Client(info.SERVER_ADDRESS, info.SERVER_PORT);
        if (username != null) {
            c.setDisplayName(username);
        }
        setUser(c);
        //add all messages in the message queue
        info.messageQueue.ifPresent((mqueue) -> mqueue.forEach((msg) -> this.chatComp.addMessage(msg)));
    }

    public ServerInfo addServerToSidebar(Pair<InetAddress, Integer> connectionInfo) {
        InetAddress address = connectionInfo.getKey();
        int port = connectionInfo.getValue();
        ServerInfo info = new ServerInfo(address.toString(), address, port, null);

        ServerEntry entry = Sidebar.createServerEntry(info);
        this.sidebar.addServerEntry(entry);
        return info;
    }

    public void addServerToList(ServerEntry entry) {
        JSONObject serverObj = new JSONObject();
        serverObj.put("SERVER_NAME", entry.getServerInfo().SERVER_NAME);
        serverObj.put("Address", entry.getServerInfo().SERVER_ADDRESS.getHostAddress());
        serverObj.put("Port", entry.getServerInfo().SERVER_PORT);
        serverList.put(serverObj);
    }

    //this method is for the list of servers on the sidebar, these should exist in a space on disk
    public void loadServers() {
        serverList.forEach((obj) -> {
            if (obj instanceof JSONObject) {
                ServerEntry entry;
                JSONObject serverObj = (JSONObject) obj;
                try {
                    String name = serverObj.optString("SERVER_NAME", "Unnamed Server");
                    System.out.println("Loaded server: " + name);
                    String b64Icon = serverObj.optString("Icon", null);
                    String address = serverObj.optString("Address", null);
                    int port = serverObj.getInt("Port");
                    ServerInfo sinfo = new ServerInfo(name, address, port, null);
                    if (b64Icon != null) {
                        entry = Sidebar.createServerEntry(sinfo, new Image(b64Icon));
                    }
                    else {
                        entry = Sidebar.createServerEntry(sinfo);
                    }

                    this.sidebar.addServerEntry(entry);
                }
                catch (Exception e) {
                    System.err.println("Error loading server entry: " + e.getMessage());
                }
            }
        });
    }

    public void initServerList(String s) {
        //load from disk
        this.serverList = new ServerList(s);
        loadServers();
    }

    public void setUser(User user) {
        this.u = user;
        exchangeChatComp();
    }
}
