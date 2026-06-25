package scenes;

import java.net.InetAddress;

import org.json.JSONObject;

import config.assets.SettingsIcon;
import components.ChatComp;
import components.ServerEntry;
import components.ServerOverlay;
import components.Sidebar;
import components.helper.SceneType;
import components.helper.ServerList;
import handlers.AppObserver;
import handlers.SceneHandler;
import network.ChatObj;
import network.Server;
import network.ServerInfo;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Pair;

class SidebarContainer extends VBox {
    public Sidebar sidebar;
    public Button settingsButton;
    public SidebarContainer(Sidebar sidebar, Button settingsButton) {
        this.sidebar = sidebar;
        this.sidebar.prefHeightProperty().bind(this.heightProperty());
        this.settingsButton = settingsButton;

        double buttonSize = 10;
        settingsButton.setMinSize(buttonSize, buttonSize);
        settingsButton.setPrefSize(buttonSize, buttonSize);
        settingsButton.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        // Bind height to width to maintain square aspect ratio
        settingsButton.prefHeightProperty().bind(settingsButton.widthProperty());
        settingsButton.setGraphic(new SettingsIcon());

        

        this.getChildren().add(sidebar);
        this.getChildren().add(settingsButton);
        VBox.setVgrow(this, Priority.ALWAYS);
        this.setMaxHeight(Double.MAX_VALUE);
    }
}

public class ChatScene extends AppScene {
    private SidebarContainer sideContain;

    private ChatComp chatComp;
    private ServerList serverList;
    private StackPane root;
    private HBox hbox;

    public ChatScene() {
        super();
        this.root = new StackPane();
        this.hbox = new HBox();

        this.sideContain = new SidebarContainer(new Sidebar(), new Button());
        this.sideContain.sidebar.setOnAddServer(this::openServerOverlay);
        this.sideContain.sidebar.setOnServerSelect(this::enterServer);
        // Bind the sidebar container height to the scene height so it matches ChatScene
        this.sideContain.prefHeightProperty().bind(this.heightProperty());

        this.sideContain.settingsButton.setOnMouseClicked((e) -> {
            SceneHandler.get().switchScene(new SettingsScene());
        });

        this.chatComp = null;
        
        this.root.setMaxHeight(Double.MAX_VALUE);
        this.root.setMaxWidth(Double.MAX_VALUE);
        this.hbox.setFillHeight(true);
        
        // Make children stretch to fill the HBox
        //HBox.setHgrow(this.sidebar, javafx.scene.layout.Priority.ALWAYS);
        
        
        this.hbox.getChildren().add(this.sideContain);
        this.root.getChildren().add(this.hbox);
        this.setRoot(this.root);


    }
    public ChatScene(int width, int height) {
        super(width, height);

        this.root = new StackPane();
        this.hbox = new HBox();

        this.sideContain = new SidebarContainer(new Sidebar(), new Button());
        this.sideContain.sidebar.setOnAddServer(this::openServerOverlay);
        this.sideContain.sidebar.setOnServerSelect(this::enterServer);

        // Bind the sidebar container height to the scene height so it matches ChatScene
        this.sideContain.prefHeightProperty().bind(this.heightProperty());


        this.sideContain.settingsButton.setOnMouseClicked((e) -> {
            SceneHandler.get().switchScene(new SettingsScene());
        });


        this.chatComp = null;
        
        this.root.setMaxHeight(Double.MAX_VALUE);
        this.root.setMaxWidth(Double.MAX_VALUE);
        this.hbox.setFillHeight(true);
        
        this.root.setPrefWidth(width);
        
        // Make children stretch to fill the HBox
        //HBox.setHgrow(this.sidebar, javafx.scene.layout.Priority.ALWAYS);
        
        
        this.hbox.getChildren().add(this.sideContain);
        this.root.getChildren().add(this.hbox);
        this.setRoot(this.root);
    }

    private void exchangeChatComp() {
        ChatObj chat = AppObserver.getInstance().getCurrentChat();
        if (chat == null) {
            return;
        }
        
        //Does anything need to be done to the old chatComp?
        if (this.chatComp != null) {
            int chatCompIndex = this.hbox.getChildren().indexOf(this.chatComp);
            this.hbox.getChildren().remove(this.chatComp);
            this.chatComp = new ChatComp();
            this.hbox.getChildren().add(chatCompIndex, this.chatComp);
        } else {
            this.chatComp = new ChatComp();
            this.hbox.getChildren().add(this.chatComp);
        }
    }

    public void openServerOverlay() {
        ServerOverlay overlay = new ServerOverlay();
        overlay.setOnClose(() -> {overlay.setVisible(false); this.root.getChildren().remove(overlay);});
        overlay.setOnServerFound(this::addServerToSidebar);
        overlay.setOnHostServer(this::createChatServer);
        this.root.getChildren().add(overlay);
    }

    public void createChatServer() {
        //Grab local IP and port directly from the running server instance
        AppObserver.getInstance().openIncomingChat();
        Server server = (Server) AppObserver.getInstance().getCurrentChat();
        if (server.socket == null) {
            System.err.println("Server failed to start, socket is null");
            return;
        }
        int port = server.socket.getLocalPort(); //default port
        addServerToSidebar(new Pair<>(server.getAddress(), port));
        exchangeChatComp();
    }
    public void enterServer(ServerInfo info) {
        if (info == null) {
            System.err.println("From enterServer(), ServerInfo is null, cannot enter server");
            return;
        }
        AppObserver.getInstance().openOutgoingChat(info.SERVER_ADDRESS, info.SERVER_PORT);
        exchangeChatComp();
        //add all messages in the message queue
        info.messageQueue.ifPresent((mqueue) -> mqueue.forEach((msg) -> this.chatComp.addMessage(msg)));
    }
    public ServerInfo addServerToSidebar(Pair<InetAddress, Integer> connectionInfo) {
        InetAddress address = connectionInfo.getKey();
        int port = connectionInfo.getValue();
        ServerInfo info = new ServerInfo(address.toString(), address, port, null);

        ServerEntry entry = Sidebar.createServerEntry(info);
        this.sideContain.sidebar.addServerEntry(entry);
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

                    this.sideContain.sidebar.addServerEntry(entry);
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

    @Override
    public SceneType getSceneType() {
        return SceneType.CHAT;
    }
}
