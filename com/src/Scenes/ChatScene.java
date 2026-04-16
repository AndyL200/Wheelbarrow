package Scenes;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

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
import javafx.util.Pair;

public class ChatScene extends AppSceneTemplate {
    private Sidebar sidebar;
    private ChatComp chatComp;
    private ServerList serverList;
    private HBox root;

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
        initStyles();

        this.root = new HBox();

        this.sidebar = new Sidebar();
        this.sidebar.setOnAddServer(this::openServerOverlay);
        this.sidebar.setOnServerSelect(this::enterServer);

        this.chatComp = new ChatComp();
        
        this.root.setMaxHeight(Double.MAX_VALUE);
        this.root.setMaxWidth(Double.MAX_VALUE);
        this.root.setFillHeight(true);
        
        // Make children stretch to fill the HBox
        HBox.setHgrow(this.sidebar, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(this.chatComp, javafx.scene.layout.Priority.ALWAYS);
        
        this.root.getChildren().addAll(this.sidebar, this.chatComp);
        this.setRoot(this.root);


    }
    public ChatScene(int width, int height) {
        super(width, height);
        initStyles();

        this.root = new HBox();

        this.sidebar = new Sidebar();
        this.sidebar.setOnAddServer(this::openServerOverlay);
        this.sidebar.setOnServerSelect(this::enterServer);

        this.chatComp = new ChatComp();
        
        this.root.setMaxHeight(Double.MAX_VALUE);
        this.root.setMaxWidth(Double.MAX_VALUE);
        this.root.setFillHeight(true);
        
        this.root.setPrefWidth(width);
        
        // Make children stretch to fill the HBox
        HBox.setHgrow(this.sidebar, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(this.chatComp, javafx.scene.layout.Priority.ALWAYS);
        
        this.root.getChildren().addAll(this.sidebar, this.chatComp);
        this.setRoot(this.root);
    }

    private void exchangeChatComp(User u) {
        if (u == this.chatComp.getUser()) {
            //do nothing
            return;
        }
        //Does anything need to be done to the old chatComp?
        this.root.getChildren().remove(this.chatComp);
        this.chatComp = new ChatComp(u);
        this.root.getChildren().add(this.chatComp);
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
        int port = server.socket.getLocalPort(); //default port
        ServerInfo info = addServerToSidebar(new Pair<>(server.getAddress(), port));
        if (info != null) {
            exchangeChatComp(server);
        }
    }
    public void enterServer(ServerInfo info) {
        if (info == null) {
            System.err.println("From enterServer(), ServerInfo is null, cannot enter server");
            return;
        }
        if (info.SERVER_ADDRESS.equals(this.chatComp.getUser().getAddress())) {
            //already in this server, do nothing
            return;
        }
        Client c = new Client(info.SERVER_ADDRESS, info.SERVER_PORT);
        exchangeChatComp(c);
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
                    ServerInfo info = new ServerInfo(name, address, port, null);
                    if (b64Icon != null) {
                        entry = Sidebar.createServerEntry(info, new Image(b64Icon));
                    }
                    else {
                        entry = Sidebar.createServerEntry(info);
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
}
