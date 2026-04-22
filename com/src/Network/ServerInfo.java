package Network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import Components.Message;
import Components.ComponentMacros.MessageType;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class ServerInfo implements java.io.Serializable {
    public StringProperty SERVER_NAME; //needs to be an observable property
    public InetAddress SERVER_ADDRESS;
    public int SERVER_PORT;
    public Optional<List<Message>> messageQueue;

    public ServerInfo(String name, InetAddress address, int port, List<Message> messageQueue) {
        this.SERVER_NAME = new SimpleStringProperty(name);
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
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(SERVER_NAME.get().length());
            dos.writeInt(SERVER_ADDRESS.getHostAddress().length());
            dos.writeInt(messageQueue.orElse(new ArrayList<>()).size());
            dos.writeBytes(SERVER_NAME.get());
            dos.writeBytes(SERVER_ADDRESS.getHostAddress());
            dos.writeInt(SERVER_PORT);
            for (Message msg : messageQueue.orElse(new ArrayList<>())) {
                dos.write(Message.toBytes(msg));
            }
            return baos.toByteArray();
        } catch (IOException e) {
            System.out.println("Error serializing ServerInfo: " + e.getMessage());
            //TEMP(temporary)
            throw new RuntimeException("Error serializing ServerInfo", e);
        }
    }

    public static ServerInfo parseMessage(Message message) {
        if ((message.type & MessageType.SERVER_INFO.getValue()) == 0) {
            System.out.println("Message type must be SERVER_INFO");
            return null;
        }
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(message.messageData));
        try {
            int name_length = dis.readInt(); //length of server name
            int address_length = dis.readInt(); //
            int queue_length = dis.readInt(); //number of messages in queue

            byte[] name_bytes = new byte[name_length];
            dis.readFully(name_bytes, 0, name_length);
            String name = new String(name_bytes);

            byte[] address_bytes = new byte[address_length];
            dis.readFully(address_bytes, 0, address_length);
            InetAddress address = InetAddress.getByName(new String(address_bytes));

            int port = dis.readInt();

            List<Message> messageQueue = new ArrayList<>();
            
            for (int i = 0; i < queue_length; i++) {
                int message_length = dis.readInt();
                byte[] message_bytes = new byte[message_length];
                try {
                    dis.readFully(message_bytes, 0, message_length);
                    Message msg = Message.fromBytes(message_bytes);
                    if (msg != null) {
                        messageQueue.add(msg);
                    }
                } catch (IllegalArgumentException e) {
                    System.out.println("Error parsing message in ServerInfo: " + e.getMessage());
                } catch (IOException e) {
                    System.out.println("Error reading message in ServerInfo: " + e.getMessage());
                }
            }
            System.out.println("ServerInfo parse success: name=" + name + ", address=" + address.getHostAddress() + ", port=" + port + ", queue_length=" + messageQueue.size());
            return new ServerInfo(message.sender, address, port, messageQueue);
        } catch (UnknownHostException h) {
            System.out.println("Error grabbing address: " + h.getMessage());
            throw new RuntimeException("Error grabbing address", h);
        } catch (IOException e) {
            System.out.println("Error parsing ServerInfo message: " + e.getMessage());
            //TEMP(temporary)
            throw new RuntimeException("Error parsing ServerInfo message", e);
        }
        
        //return new ServerInfo("UNFOUND", "", 0, new ArrayList<>());
    }

    //Do I even want this?
    public ServerInfo(String name, ServerSocket socket, List<Message> messageQueue) {
        this.SERVER_NAME = new SimpleStringProperty(name);
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
        this.SERVER_NAME = new SimpleStringProperty(name);
        this.messageQueue = Optional.ofNullable(messageQueue);
    }

    public List<Message> getMessageQueue() {
        return messageQueue.orElse(null);
    }

    public void dump() {
        ServerCache cache = new ServerCache(SERVER_NAME.get(), SERVER_ADDRESS, SERVER_PORT, null, messageQueue.orElse(null).toArray(new Message[0]), new byte[0]);
        cache.moveToCache();
    }

}
