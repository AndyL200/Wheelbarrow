package Network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import Components.Message;
import Components.Config.Settings;
import javafx.scene.image.Image;

//cache the most necessary info for the server, (users, recent messages, and the chatlog)

//abstraction for the physical memory on disk that holds the server info
//TODO(use appdata rather than the project directory for this cache)
public class ServerCache implements Serializable{
    private String SERVER_NAME;
    //Better to keep as is, test for validity on server cache objects
    private Socket SERVER_SOCKET;
    private List<User> users;
    private Message[] chatlog;
    private Settings settings;

    public List<User> getUsers() {
        return users;
    }
    public Message[] getRecentMessages() {
        if (chatlog.length <= 20) {
            return chatlog;
        }
        return Arrays.copyOfRange(chatlog, chatlog.length-20, chatlog.length);
    }

    public Message[] getChatlog() {
        return chatlog; //Consider memory allocation here
    }

    public Socket getServer() {
        return SERVER_SOCKET;
    }

    public int getServerPort() {
        return SERVER_SOCKET.getPort();
    }

    public InetAddress getServerAddress() {
        return SERVER_SOCKET.getInetAddress();
    }

    public String getHostAlias() {
        return SERVER_NAME;
    }

    public Settings getSettings() {
        return settings;
    }

    public ServerCache(String name, Socket socket, List<User> users, Message[] toLog) {
        this.SERVER_NAME = name;
        this.SERVER_SOCKET = socket;
        this.users = users;
        this.chatlog = toLog;
        this.settings = new Settings();
    }

    public ServerCache(String name, InetAddress address, int port, List<User> users, Message[] toLog, byte[] icon) {
        this.SERVER_NAME = name;
        try {
            this.SERVER_SOCKET = new Socket(address, port);
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.users = users;
        this.chatlog = toLog;
        this.settings = new Settings();
    }

    public ServerCache(String name, Socket socket, List<User> users, Message[] toLog, Settings settings) {
        this.SERVER_NAME = name;
        this.SERVER_SOCKET = socket;
        this.users = users;
        this.chatlog = toLog;
        this.settings = settings;
    }

    public ServerCache(String name, InetAddress address, int port, List<User> users, Message[] toLog, Settings settings) {
        this.SERVER_NAME = name;
        try {
            this.SERVER_SOCKET = new Socket(address, port);
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.users = users;
        this.chatlog = toLog;
        this.settings = settings;
    }

    public byte[] toBytes() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(this);
            oos.close();
            return baos.toByteArray();
        } catch (Exception e) {
            System.out.println("Error serializing ServerCache: " + e.getMessage());
            e.printStackTrace();
            return new byte[0];
        }
    }

    public static ServerCache fromBytes(byte[] data) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            ObjectInputStream ois = new ObjectInputStream(bais);
            ServerCache cache = (ServerCache) ois.readObject();
            ois.close();
            return cache;
        } catch (Exception e) {
            System.out.println("Error deserializing ServerCache: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public void moveToCache() {
        try {
            byte[] data = toBytes();
            Path currentDir = Paths.get("").toAbsolutePath();
            Path filename = currentDir.resolve("com").resolve("server_cache").resolve(SERVER_NAME + ".cache");
            System.out.println("Saving ServerCache to " + filename.toString());
            FileOutputStream fos = new FileOutputStream(filename.toFile());
            fos.write(data);
            fos.close();
            System.out.println("ServerCache saved to " + filename);
        } catch (Exception e) {
            System.out.println("Error saving ServerCache: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void accept(ServerInfo info) {
        //append to the file in memory with the new info
        int currentLength = chatlog.length;
        int newLength = currentLength + info.getMessageQueue().size();
        Message[] newChatlog = new Message[newLength];
        System.arraycopy(chatlog, 0, newChatlog, 0, currentLength);
        System.arraycopy(info.getMessageQueue().toArray(), 0, newChatlog, currentLength, info.getMessageQueue().size());
        chatlog = newChatlog;

        //doing this for now but more efficient to append to the file on disk
        moveToCache();
    }
}
