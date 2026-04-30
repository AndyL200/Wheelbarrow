package Network;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Timer;
import java.util.function.Consumer;
import Components.ComponentMacros.MessageType;

import Components.Message;

import javax.crypto.SecretKey;

public class Client implements User {
    private static final String SESSION_SALT = "WheelbarrowSessionSalt2024";

    private volatile boolean running = true;
    private Socket SERVER;
    public String SERVER_HOSTNAME = ""; 
    private ServerInfo info;
    private String HOSTNAME;
    private String displayName = null;
    private InetAddress ADDRESS;
    private DataInputStream reader;
    private DataOutputStream writer;
    private Consumer<Message> onMessageReceived;
    private SecretKey sessionKey;
    

    public Client(InetAddress address) {
        this.SERVER_HOSTNAME = address.getHostName();
        getClientAddress();
        HOSTNAME = ADDRESS != null ? ADDRESS.getHostName() : "Unknown Client";
        if (SERVER_HOSTNAME.equals(HOSTNAME)) {
            HOSTNAME += "_1";
        }

        try {
            SERVER = new Socket(address, 50000);
            reader = new DataInputStream(SERVER.getInputStream());
            writer = new DataOutputStream(SERVER.getOutputStream());
            info = new ServerInfo("loading...", SERVER.getInetAddress(), SERVER.getPort(), null);
            Thread receiveThread = new Thread(this::receiveLoop);
            receiveThread.setDaemon(true);  // Exit with app
            receiveThread.start();
        } catch (IOException e) {
            System.out.println("Error connecting to server: " + e.getMessage());
        }
    }

    public Client(InetAddress address, int port) {
        this.SERVER_HOSTNAME = address.getHostName();
        getClientAddress();
        HOSTNAME = ADDRESS != null ? ADDRESS.getHostName() : "Unknown Client";
        if (SERVER_HOSTNAME.equals(HOSTNAME)) {
            HOSTNAME += "_1";
        }
        try {
            SERVER = new Socket(address, port);
            reader = new DataInputStream(SERVER.getInputStream());
            writer = new DataOutputStream(SERVER.getOutputStream());
            info = new ServerInfo("loading...", SERVER.getInetAddress(), SERVER.getPort(), null);
            Thread receiveThread = new Thread(this::receiveLoop);
            receiveThread.setDaemon(true);  // Exit with app
            receiveThread.start();
        } catch (IOException e) {
            System.out.println("Error connecting to server: " + e.getMessage());
        }
    }

    public Client(InetAddress address, int port, Consumer<Message> listener) {
        this.SERVER_HOSTNAME = address.getHostName();
        getClientAddress();
        HOSTNAME = ADDRESS != null ? ADDRESS.getHostName() : "Unknown Client";
        if (SERVER_HOSTNAME.equals(HOSTNAME)) {
            HOSTNAME += "_1";
        }
        this.onMessageReceived = listener;
        try {
            SERVER = new Socket(address, port);
            reader = new DataInputStream(SERVER.getInputStream());
            writer = new DataOutputStream(SERVER.getOutputStream());
            info = new ServerInfo("loading...", SERVER.getInetAddress(), SERVER.getPort(), null);
            Thread receiveThread = new Thread(this::receiveLoop);
            receiveThread.setDaemon(true);  // Exit with app
            receiveThread.start();
        } catch (IOException e) {
            System.out.println("Error connecting to server: " + e.getMessage());
        }
    }


    private void receiveLoop() {
        while(running) {
            byte[] data = receive();
            if (data.length > 0) {
                Message msg = Message.fromBytes(data);
                if (msg == null) {
                    System.out.println("Received invalid message");
                    continue;
                }
                if (msg.sender.equals(getName())) {
                    System.out.println("Received message from self, ignoring");
                    continue;
                }
                if ((msg.type & MessageType.MESSAGE.getValue()) > 0) {
                    //could be an image here
                    byte[] plaintext = decryptPayload(msg.messageData);
                    if (plaintext == null) continue;
                    System.out.println("Received message: " + new String(plaintext));
                    onMessageReceived.accept(new Message(msg.sender, plaintext, msg.type));
                }
                else if ((msg.type & MessageType.TYPING.getValue()) > 0) {
                    byte[] plaintext = decryptPayload(msg.messageData);
                    if (plaintext == null) continue;
                    System.out.println("Received typing message: " + new String(plaintext));
                    onMessageReceived.accept(new Message(msg.sender, plaintext, msg.type));
                }
                else if ((msg.type & MessageType.WELCOME.getValue()) > 0) {
                    System.out.println("Received welcome message: " + new String(msg.messageData));
                    SERVER_HOSTNAME = msg.sender;
                    info.SERVER_NAME.set(SERVER_HOSTNAME);
                    //send a request for server info
                    send(new Message(getName(), "Request", MessageType.SERVER_INFO.getValue()));
                    requestInfo();
                }
                else if ((msg.type & MessageType.SERVER_INFO.getValue()) > 0) {
                    // Probably an area where something specific happens because the message is directed towards this client
                    System.out.println("Received server info: " + new String(msg.messageData));
                    info = ServerInfo.parseMessage(msg);
                }
                else if ((msg.type & MessageType.BROADCAST.getValue()) > 0) {
                    //handle broadcast message, this is a message that should be sent to all users but not necessarily displayed in the chat
                    byte[] plaintext = decryptPayload(msg.messageData);
                    if (plaintext == null) continue;
                    System.out.println("Received broadcast message: " + new String(plaintext));
                    onMessageReceived.accept(new Message(msg.sender, plaintext, msg.type));
                }
            }
        }
    }

    /**
     * Encrypts {@code payload} when a session key is configured; returns the
     * payload unchanged otherwise. Returns {@code null} on encryption failure
     * so the caller can abort the send rather than transmit plaintext.
     */
    private byte[] encryptPayload(byte[] payload) {
        if (sessionKey == null || payload == null || payload.length == 0) {
            return payload;
        }
        try {
            return Security.encryptBytes(payload, sessionKey);
        } catch (Exception e) {
            System.out.println("Client: encryption failed - message will not be sent: " + e.getMessage());
            return null;
        }
    }

    /**
     * Decrypts {@code data} when a session key is configured; returns {@code data}
     * unchanged otherwise. Returns {@code null} on decryption failure so the
     * caller can drop the message rather than process potentially tampered data.
     */
    private byte[] decryptPayload(byte[] data) {
        if (sessionKey == null || data == null || data.length == 0) {
            return data;
        }
        try {
            return Security.decryptBytes(data, sessionKey);
        } catch (Exception e) {
            System.out.println("Client: decryption failed - message dropped: " + e.getMessage());
            return null;
        }
    }

    @Override
    //User Interface contracts
    public void send(byte[] data) {
        send(Message.fromBytes(data));
    }
    @Override
    public void send(Message message) {
        if (writer == null || SERVER == null || SERVER.isClosed()) {
            System.out.println("Cannot send message: Socket not connected");
            return;
        }

        // Encrypt the payload; abort if encryption fails
        byte[] data = encryptPayload(message.messageData);
        if (data == null) {
            System.out.println("Client: message not sent due to encryption failure");
            return;
        }
        try {
            writer.writeInt(message.type); //type of message
            writer.writeInt(message.sender.getBytes().length); //sender length
            writer.writeInt(data.length);
            writer.write(message.sender.getBytes(), 0, message.sender.getBytes().length);
            writer.write(data, 0, data.length);
            writer.flush();
        } catch (IOException e) {
            System.out.println("Error sending message: " + e.getMessage());

        }
    }
    @Override
    public byte[] receive() {
        try {
            return receiveClient().toByteArray();
        } catch (IOException e) {
            //System.out.println("Error receiving message: " + e.getMessage());
            return new byte[0];
        }
    }

    //expecting to "joker" the stream
    public ByteArrayOutputStream receiveClient() throws IOException {
        if (SERVER == null || SERVER.isClosed()) {
            System.out.println("Socket is not connected.");
            return new ByteArrayOutputStream();
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(buffer);
        int type = reader.readInt();
        int slength = reader.readInt();
        int length = reader.readInt();
        System.out.println("Message Received - Type: " + type + ", Sender Length: " + slength + ", Message Length: " + length);
        byte[] senderData = new byte[slength];
        reader.read(senderData, 0, slength);
        byte[] data = new byte[length];
        reader.read(data, 0, length);
        dos.writeInt(type); //messageType
        dos.writeInt(slength); //sender length
        dos.writeInt(length); //message length
        dos.write(senderData, 0, slength); //sender
        dos.write(data, 0, data.length); //msg data
        return buffer;
    }
    public void setOnMessageReceived(Consumer<Message> listener) {
        this.onMessageReceived = listener;
    }

    /**
     * Returns the display name used in outgoing messages. If a display name
     * has been set via {@link #setDisplayName(String)} that name is used;
     * otherwise the network hostname is returned.
     */
    public String getName() {
        return displayName != null ? displayName : HOSTNAME;
    }

    /**
     * Sets the display name shown to other users (e.g. the username chosen
     * at login). Overrides the default network hostname.
     */
    public void setDisplayName(String name) {
        this.displayName = name;
    }

    
    public InetAddress getClientAddress() {
        if (ADDRESS != null) {
            return ADDRESS;
        }
        try {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || iface.isUp() == false || iface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress()) {
                        ADDRESS = addr;
                        return addr;
                    }
                }
            }
        }
        catch (SocketException s) {
            System.out.println("Failed to get network interfaces");
        }
        return null;
    }

    @Override
    public InetAddress getAddress() {
        if (SERVER != null && SERVER.isConnected()) {
            return SERVER.getInetAddress();
        }
        return null;
    }
    private void requestInfo() {
        send(new Message(getName(), "Request", MessageType.SERVER_INFO.getValue()));
    }
    @Override
    public ServerInfo getInfo() {
        return info;
    }

}
