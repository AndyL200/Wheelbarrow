package Network;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import Components.Message;

public class ServerInfo implements java.io.Serializable {
    private String SERVER_NAME;
    private Socket SERVER_SOCKET;
    private List<Message> messageQueue;

    ServerInfo(String name, Socket socket, List<Message> messageQueue) {
        this.SERVER_NAME = name;
        this.SERVER_SOCKET = socket;
        this.messageQueue = messageQueue;
    }

    public void queueMessage(Message message) {
        messageQueue.add(message);
    }

    public void queueAllMessages(List<Message> messages) {
        messageQueue.addAll(messages);
    }

    public byte[] toBytes() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(this);
            oos.close();
            return baos.toByteArray();
        } catch (Exception e) {
            System.out.println("Error serializing ServerInfo: " + e.getMessage());
            e.printStackTrace();
            return new byte[0];
        }
    }

    ServerInfo(String name, ServerSocket socket, List<Message> messageQueue) {
        this.SERVER_NAME = name;
        try {
            this.SERVER_SOCKET = new Socket(socket.getInetAddress(), socket.getLocalPort());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Message> getMessageQueue() {
        return messageQueue;
    }

    public void dump() {
        Path currentDir = Paths.get("").toAbsolutePath();
        Path filename = currentDir.resolve("com").resolve("server_cache").resolve(SERVER_NAME + ".cache");
        
        if (!Files.exists(filename)) {
            try {
                FileOutputStream fos = new FileOutputStream(filename.toFile());
                fos.write(new ServerCache(SERVER_NAME, SERVER_SOCKET, null, messageQueue.toArray(new Message[0])).toBytes());
                fos.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
