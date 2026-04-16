package Network;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import Components.Message;

public class ServerInfo implements java.io.Serializable {
    public String SERVER_NAME;
    public InetAddress SERVER_ADDRESS;
    public int SERVER_PORT;
    public Optional<List<Message>> messageQueue;

    public ServerInfo(String name, InetAddress address, int port, List<Message> messageQueue) {
        this.SERVER_NAME = name;
        this.SERVER_ADDRESS = address;
        this.SERVER_PORT = port;
        this.messageQueue = Optional.ofNullable(messageQueue);
    }

    public void queueMessage(Message message) {
        messageQueue.ifPresent(queue -> queue.add(message));
    }

    public void queueAllMessages(List<Message> messages) {
        messageQueue.ifPresent(queue -> queue.addAll(messages));
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
            return new byte[0];
        }
    }

    //Do I even want this?
    public ServerInfo(String name, ServerSocket socket, List<Message> messageQueue) {
        this.SERVER_NAME = name;
        this.SERVER_ADDRESS = socket.getInetAddress();
        this.SERVER_PORT = socket.getLocalPort();
        this.messageQueue = Optional.ofNullable(messageQueue);
    }

    public ServerInfo(String name, String address, int port, List<Message> messageQueue) {
        try {
            this.SERVER_ADDRESS = InetAddress.getByName(address);
            this.SERVER_PORT = port;
        } catch (Exception e) {
            System.out.println("Error creating ServerInfo: " + e.getMessage());
            //e.printStackTrace();
        }
        this.SERVER_NAME = name;
        this.messageQueue = Optional.ofNullable(messageQueue);
    }

    public List<Message> getMessageQueue() {
        return messageQueue.orElse(null);
    }

    public void dump() {
        ServerCache cache = new ServerCache(SERVER_NAME, SERVER_ADDRESS, SERVER_PORT, null, messageQueue.orElse(null).toArray(new Message[0]), new byte[0]);
        cache.moveToCache();
    }

}
