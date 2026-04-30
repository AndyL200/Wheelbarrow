package Network;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import Components.Message;
import Components.ComponentMacros.MessageType;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;

import javax.crypto.SecretKey;



//auto closeable to ensure program exits properly
public class Server implements User, AutoCloseable {
    private static final String SESSION_SALT = "WheelbarrowSessionSalt2024";

    private volatile boolean running = true;

    public ServerSocket socket;
    public String HOSTNAME = "localhost";
    private InetAddress ADDRESS;
    private ExecutorService pool = Executors.newFixedThreadPool(50);
    protected List<Socket> clients = new ArrayList<Socket>(0);
    protected Deque<Message> messages = new java.util.LinkedList<Message>();
    private Consumer<Message> onMessageReceived;
    private ServerInfo info;
    private SoftReference<ServerCache> cacheRef;
    private Thread acceptor;
    private SecretKey sessionKey;
    private Set<Socket> authenticatedClients = Collections.synchronizedSet(new HashSet<>());

    private static final int MAX_LOGIN_ATTEMPTS = 5;

    private class ClientHandler implements Callable<Void> {
        private Socket client;
        public int infractions = 0;
        private int failedLoginAttempts = 0;
        ClientHandler(Socket client) {
            //handle client connection
            this.client = client;
        }
        public Void call() throws InterruptedException, IOException{
            if (client == null || client.isClosed()) {
                return null;
            }
            while (!client.isClosed()) {
                //handle client connection
                DataInputStream reception = new DataInputStream(client.getInputStream());
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                int type = reception.readInt();
                int slength = reception.readInt();
                int length = reception.readInt();
                byte[] senderData = new byte[slength];
                reception.read(senderData, 0, slength);
                byte[] response = new byte[length];
                reception.read(response, 0, length);

                // Decrypt payload when a session key is configured; drop message on failure
                byte[] plainPayload = decryptPayload(response);
                if (plainPayload == null) {
                    System.out.println("Server: dropping message with undecryptable payload from "
                            + client.getRemoteSocketAddress());
                    Thread.sleep(100);
                    continue;
                }

                // Require successful LOGIN before allowing any other message type
                boolean authRequired = LocalCredentials.hasCredentials();
                boolean isAuthenticated = !authRequired || authenticatedClients.contains(client);

                if ((type & MessageType.LOGIN.getValue()) > 0) {
                    handleLogin(client, senderData, plainPayload);
                }

                else if (!isAuthenticated) {
                    // Client has not logged in yet – ignore all other message types
                    System.out.println("Unauthenticated message from " + client.getRemoteSocketAddress() + ", ignoring");
                }

                else if ((type & MessageType.SERVER_INFO.getValue()) > 0) {
                    //ignore, this is just a client requesting server info, not an actual message to broadcast
                    onMessageReceived.accept(new Message(new String(senderData), plainPayload, type));
                    server_info();
                }

                else if ((type & MessageType.MESSAGE.getValue()) > 0) {
                    onMessageReceived.accept(new Message(new String(senderData), plainPayload, type));
                    DataOutputStream dos = new DataOutputStream(buffer);
                    dos.writeInt(type);
                    dos.writeInt(slength);
                    dos.writeInt(plainPayload.length);
                    dos.write(senderData);
                    dos.write(plainPayload);
                    Message m = Message.fromBytes(buffer.toByteArray());
                    messages.add(m);
                    //periodically cache messages??
                    broadcast(m);
                }

                else if ((type & MessageType.AUDIO.getValue()) > 0) {
                    onMessageReceived.accept(new Message(new String(senderData), plainPayload, type));
                    DataOutputStream dos = new DataOutputStream(buffer);
                    dos.writeInt(type);
                    dos.writeInt(slength);
                    dos.writeInt(plainPayload.length);
                    dos.write(senderData);
                    dos.write(plainPayload);
                    Message m = Message.fromBytes(buffer.toByteArray());
                    //don't add audio messages to the message history, but still broadcast them
                    broadcast(m);
                }

                else if ((type & MessageType.OTHER.getValue()) > 0) {
                    //Broadcast
                    onMessageReceived.accept(new Message(new String(senderData), plainPayload, type));
                    DataOutputStream dos = new DataOutputStream(buffer);
                    dos.writeInt(type);
                    dos.writeInt(slength);
                    dos.writeInt(plainPayload.length);
                    dos.write(senderData);
                    dos.write(plainPayload);
                    broadcast(buffer.toByteArray());
                }

                Thread.sleep(100);
            }
            authenticatedClients.remove(client);
            return null;
        }

        private void handleLogin(Socket client, byte[] senderData, byte[] payload) {
            // Enforce brute-force limit before processing credentials
            if (failedLoginAttempts >= MAX_LOGIN_ATTEMPTS) {
                System.out.println("Too many login attempts from " + client.getRemoteSocketAddress() + ", closing connection");
                sendAuthResult(client, false);
                try { client.close(); } catch (IOException ignored) {}
                return;
            }

            String credentials = new String(payload);
            int sep = credentials.indexOf(':');
            if (sep < 0) {
                failedLoginAttempts++;
                sendAuthResult(client, false);
                return;
            }
            String username = credentials.substring(0, sep);
            String password = credentials.substring(sep + 1);
            if (LocalCredentials.verify(username, password)) {
                failedLoginAttempts = 0;
                authenticatedClients.add(client);
                System.out.println("Login accepted from " + client.getRemoteSocketAddress());
                sendAuthResult(client, true);
            } else {
                failedLoginAttempts++;
                System.out.println("Login failed from " + client.getRemoteSocketAddress()
                        + " (attempt " + failedLoginAttempts + "/" + MAX_LOGIN_ATTEMPTS + ")");
                sendAuthResult(client, false);
            }
        }

        private void sendAuthResult(Socket client, boolean success) {
            int resultType = success
                    ? MessageType.AUTH_OK.getValue()
                    : MessageType.AUTH_FAIL.getValue();
            byte[] msg = success ? "OK".getBytes() : "FAIL".getBytes();
            try {
                DataOutputStream writer = new DataOutputStream(client.getOutputStream());
                writer.writeInt(resultType);
                writer.writeInt(getName().getBytes().length);
                writer.writeInt(msg.length);
                writer.write(getName().getBytes(), 0, getName().getBytes().length);
                writer.write(msg, 0, msg.length);
                writer.flush();
            } catch (IOException e) {
                System.out.println("Error sending auth result: " + e.getMessage());
            }
        }

        public void server_info() {
            DataOutputStream writer = null;
            byte[] infoBytes;
            
            try {
                writer = new DataOutputStream(client.getOutputStream());
            } catch (IOException e) {
                System.out.println("Error getting client output stream: " + e.getMessage());
                return;
            }
                infoBytes = info.toBytes();

                try {
                    writer.writeInt(MessageType.SERVER_INFO.getValue()); //type of message
                    writer.writeInt(getName().getBytes().length);
                    writer.writeInt(infoBytes.length); //length of client address + length of info
                    writer.write(getName().getBytes(), 0, getName().getBytes().length);
                    writer.write(infoBytes, 0, infoBytes.length);
                    writer.flush();
            } catch (IOException e) {
                System.out.println("Error sending server info: " + e.getMessage());
            }
        }
    }
    private class MessageHandler extends Thread {
        MessageHandler() {
            //handle message broadcasting
            
        }
        public void run() {
            //handle message broadcasting
        }
    }

    public Server() {
        initNetwork(null);
    }

    public Server(String password) {
        initNetwork(password);
    }

    public Server(ServerCache cache) {
        this.cacheRef = new SoftReference<ServerCache>(cache);
        initNetwork(null);
    }

    public Server(String password, ServerCache cache) {
        this.cacheRef = new SoftReference<ServerCache>(cache);
        initNetwork(password);
    }

    private void initNetwork(String password) {
        if (password != null && !password.isEmpty()) {
            try {
                this.sessionKey = Security.getKeyFromPassword(password, SESSION_SALT);
                System.out.println("Server: session key derived from password");
            } catch (Exception e) {
                System.out.println("Server: failed to derive session key – " + e.getMessage());
            }
        }

        try {
            this.ADDRESS = getAddress(); // Use localhost for testing
            this.socket = new ServerSocket(50000, 50, this.ADDRESS);
            this.HOSTNAME = ADDRESS.getHostName();
            System.out.println("Server HOSTNAME: "  + HOSTNAME + ", IP: " + ADDRESS.getHostAddress());
            
        }
        catch (IOException e) {
            System.out.println("Failed to initialize server socket on port 50000");
            try {
                this.socket = new ServerSocket(0, 50, this.ADDRESS);
                this.HOSTNAME = ADDRESS.getHostName();
            }
            catch (IOException ex) {
                System.out.println("Failed to initialize server socket on random port");
                this.socket = null;
            }
        }
        
        if (socket == null) {
                return;
        }

        acceptor = new Thread(this::acceptor_loop);
        acceptor.setDaemon(true);
        acceptor.start();
        
        try {
            initInfo();
        }
        catch (UnknownHostException h) {
            System.out.println("Failed to initialize server info" + h.getMessage());
        }
    }

    /**
     * Encrypts {@code payload} when a session key is configured; returns the
     * payload unchanged otherwise. Returns {@code null} on encryption failure
     * so callers can skip the send rather than transmit plaintext.
     */
    private byte[] encryptPayload(byte[] payload) {
        if (sessionKey == null || payload == null || payload.length == 0) {
            return payload;
        }
        try {
            return Security.encryptBytes(payload, sessionKey);
        } catch (Exception e) {
            System.out.println("Server: encryption failed – message will not be sent: " + e.getMessage());
            return null;
        }
    }

    /**
     * Decrypts {@code data} when a session key is configured; returns {@code data}
     * unchanged otherwise. Returns {@code null} on decryption failure so callers
     * can drop the message rather than process potentially tampered data.
     */
    private byte[] decryptPayload(byte[] data) {
        if (sessionKey == null || data == null || data.length == 0) {
            return data;
        }
        try {
            return Security.decryptBytes(data, sessionKey);
        } catch (Exception e) {
            System.out.println("Server: decryption failed – message dropped: " + e.getMessage());
            return null;
        }
    }

    
    private void acceptor_loop() {
        while(running) {
            try {
            Socket client = socket.accept();
            System.out.println("Client connected: " + client.getRemoteSocketAddress());
            
            sendWelcome(client, ("WELCOME to " + getName() + "'s server!").getBytes());
            clients.add(client);
            pool.submit(new ClientHandler(client));
            //sendBoard(client, null);
            }
            catch (IOException e) {
                System.out.println("Error accepting client connection: " + e.getMessage());
            }
             //send entire javafx scene tree on connect
            //can I leverage this against what the client already has stored?
        }
    }
    public void broadcast(byte[] message) {
        broadcast(Message.fromBytes(message));
    }
    public void broadcast(Message message) {
        //probably a better way to do this
        message.type |= MessageType.BROADCAST.getValue();
        for(Socket client : clients) {
            //send message to client
            try {
                byte[] encryptedData = encryptPayload(message.messageData);
                if (encryptedData == null) {
                    System.out.println("Server: skipping broadcast to " + client.getRemoteSocketAddress() + " due to encryption failure");
                    continue;
                }
                DataOutputStream writer = new DataOutputStream(client.getOutputStream());
                writer.writeInt(message.type); //type of message
                writer.writeInt(message.sender.getBytes().length); //sender length
                writer.writeInt(encryptedData.length); //length of message
                writer.write(message.sender.getBytes(), 0, message.sender.getBytes().length);
                writer.write(encryptedData, 0, encryptedData.length);
                writer.flush();
            } catch (IOException e) {
                System.out.println("Error sending message to client: " + e.getMessage());
            }
        }
    }
    public void sendWelcome(Socket client, byte[] message) {
        try {
            DataOutputStream writer = new DataOutputStream(client.getOutputStream());
            writer.writeInt(MessageType.WELCOME.getValue()); //type of message
            writer.writeInt(getName().getBytes().length); //sender length
            writer.writeInt(message.length); //length of message
            writer.write(getName().getBytes(), 0, getName().getBytes().length);
            writer.write(message, 0, message.length);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void sendBoard(Socket client, byte[] Board) {
        //Send the chat styles to the client??
    }

    public void send(byte[] message) {
        System.out.println("Sending message: " + new String(message));
        broadcast(message);
    }

    public void send(Message message) {
        if ((message.type & MessageType.TYPING.getValue()) == 0) {
            System.out.println("Broadcasting message from " + message.sender + ": " + new String(message.messageData));
        }
        broadcast(message);
    }

    public byte[] receive() {
        //handle incoming message
        return messages.peekFirst().messageData;
    }

    public String getName() {
        return HOSTNAME;
    }
    public void setOnMessageReceived(Consumer<Message> listener) {
        this.onMessageReceived = listener;
    }       
    
    public void initInfo() throws UnknownHostException{
            this.info = new ServerInfo(HOSTNAME, socket, new ArrayList<Message>(0));
    }

    @Override
    public void close() {
        closeServer();
    }

    @Override
    public InetAddress getAddress() {
        if (socket != null) {
            return socket.getInetAddress();
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
                        return addr;
                    }
                }
            }
        }
        catch (SocketException s) {
            System.out.println("Failed to get network interfaces");
        }
        System.out.println("Failed to bind a network interface");
        return null;
    }

    public void closeServer() {
        running = false;
        //Do I need to close all clients?
        try {
                acceptor.join();
            } catch (InterruptedException i) {
                acceptor.interrupt();
            }
            pool.shutdown();
            try {
                socket.close();
                if (cacheRef != null) {
                    cacheRef.get().accept(info);
                }
                else {
                    info.dump();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
    }
    public SoftReference<ServerCache> getServerCacheRef() {
        return this.cacheRef;
    }

    @Override
    public ServerInfo getInfo() {
        return info;
    }
}
