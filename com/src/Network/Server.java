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
import java.util.Deque;
import java.util.Enumeration;
import java.util.List;
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




//auto closeable to ensure program exits properly
public class Server implements User, AutoCloseable {
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

    private class ClientHandler implements Callable<Void> {
        private Socket client;
        public int infractions = 0;
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

                onMessageReceived.accept(new Message(new String(senderData), response, type));
                
                if ((type & MessageType.SERVER_INFO.getValue()) > 0) {
                    //ignore, this is just a client requesting server info, not an actual message to broadcast
                    server_info();
                }

                else if ((type & MessageType.MESSAGE.getValue()) > 0) {
                    DataOutputStream dos = new DataOutputStream(buffer);
                    dos.writeInt(type);
                    dos.writeInt(slength);
                    dos.writeInt(length);
                    dos.write(senderData);
                    dos.write(response);
                    Message m = Message.fromBytes(buffer.toByteArray());
                    messages.add(m);
                    //periodically cache messages??
                    broadcast(m);
                }

                else if ((type & MessageType.AUDIO_HOST.getValue()) > 0) {
                    DataOutputStream dos = new DataOutputStream(buffer);
                    dos.writeInt(type);
                    dos.writeInt(slength);
                    dos.writeInt(length);
                    dos.write(senderData);
                    dos.write(response);
                    Message m = Message.fromBytes(buffer.toByteArray());
                    //don't add audio messages to the message history, but still broadcast them
                    broadcast(m);
                }

                else if ((type & MessageType.OTHER.getValue()) > 0) {
                    //Broadcast
                    DataOutputStream dos = new DataOutputStream(buffer);
                    dos.writeInt(type);
                    dos.writeInt(slength);
                    dos.writeInt(length);
                    dos.write(senderData);
                    dos.write(response);
                    broadcast(buffer.toByteArray());
                }

                Thread.sleep(100);
            }
            return null;
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


    public Server(ServerCache cache) {
        try {
            this.cacheRef = new SoftReference<ServerCache>(cache);                
        } catch (Exception e) {
            System.out.println("Failed to initialize server info cache");
        }

        this();
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
        // //probably a better way to do this
        // for(Socket client : clients) {
        //     //send message to client
        //     try {
        //         DataOutputStream writer = new DataOutputStream(client.getOutputStream());
        //         writer.writeInt(MessageType.BROADCAST.getValue()); //type of message
        //         writer.writeInt(getName().getBytes().length); //sender length
        //         writer.writeInt(message.length); //length of message
        //         writer.write(getName().getBytes(), 0, getName().getBytes().length);
        //         writer.write(message);
        //         writer.flush();
        //     } catch (IOException e) {
        //         System.out.println("Error sending message to client: " + e.getMessage());
        //     }
        // }
    }
    public void broadcast(Message message) {
        //probably a better way to do this
        message.type |= MessageType.BROADCAST.getValue();
        for(Socket client : clients) {
            //send message to client
            try {
                DataOutputStream writer = new DataOutputStream(client.getOutputStream());
                writer.writeInt(message.type); //type of message
                writer.writeInt(message.sender.getBytes().length); //sender length
                writer.writeInt(message.messageData.length); //length of message
                writer.write(message.sender.getBytes(), 0, message.sender.getBytes().length);
                writer.write(message.messageData, 0, message.messageData.length);
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
