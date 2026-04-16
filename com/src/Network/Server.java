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
    private ExecutorService pool = Executors.newFixedThreadPool(50);
    protected List<Socket> clients = new ArrayList<Socket>(0);
    protected Deque<Message> messages = new java.util.LinkedList<Message>();
    private final Semaphore messageSemaphore = new Semaphore(0);
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
                int length = reception.readInt();
                byte[] response = new byte[length];
                reception.readFully(response);

                if (type == MessageType.SERVER_INFO.getValue()) {
                    //ignore, this is just a client requesting server info, not an actual message to broadcast
                    server_info();
                }

                else if (type == MessageType.MESSAGE.getValue()) {
                    buffer.write(response);
                    Message m = Message.fromBytes(buffer.toByteArray());
                    messages.add(m);
                    //periodically cache messages??
                    broadcast(m);
                }

                else if (type == MessageType.OTHER.getValue()) {
                    //Broadcast
                    buffer.write(response);
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
                e.printStackTrace();
                return;
            }

            if (cacheRef.get() != null) {
                infoBytes = cacheRef.get().toBytes();

                try {
                writer.writeInt(MessageType.SERVER_CACHE.getValue()); //type of message
                writer.writeInt(infoBytes.length); //length of client address + length of info
                //writer.write(clientAddressBytes);
                writer.write(infoBytes);
                writer.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else {
                infoBytes = info.toBytes();

                try {
                writer.writeInt(MessageType.SERVER_INFO.getValue()); //type of message
                writer.writeInt(infoBytes.length); //length of client address + length of info
                //writer.write(clientAddressBytes);
                writer.write(infoBytes);
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
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
            this.socket = new ServerSocket(50000);
            this.HOSTNAME = InetAddress.getLocalHost().getHostName();
            System.out.println("Server HOSTNAME: "  + HOSTNAME + ", IP: " + InetAddress.getLocalHost().getHostAddress());
            
        } catch (UnknownHostException e1) {
            System.out.println("Failed to initialize server info cache");
            this.HOSTNAME = "localhost";
        } catch (IOException e0) {
            System.out.println("Failed to initialize server socket");
            try {
                this.socket = new ServerSocket(0);
                this.HOSTNAME = InetAddress.getLocalHost().getHostName();
            }
            catch (IOException e) {
                System.out.println("Failed to initialize server socket on random port");
                this.socket = null;
                this.HOSTNAME = "localhost";
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
            clients.add(client);
            pool.submit(new ClientHandler(client));
            sendClient(client, ("WELCOME to " + getName() + "'s server!").getBytes());
            sendBoard(client, null);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
             //send entire javafx scene tree on connect
            //can I leverage this against what the client already has stored?
        }
    }
    public void broadcast(byte[] message) {
        //probably a better way to do this
        for(Socket client : clients) {
            //send message to client
            try {
                DataOutputStream writer = new DataOutputStream(client.getOutputStream());
                writer.writeInt(MessageType.BROADCAST.getValue()); //type of message
                writer.writeInt(message.length); //length of message
                writer.write(message);
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public void broadcast(Message message) {
        //probably a better way to do this
        for(Socket client : clients) {
            //send message to client
            try {
                DataOutputStream writer = new DataOutputStream(client.getOutputStream());
                writer.writeInt(MessageType.BROADCAST.getValue()); //type of message
                writer.writeInt(message.messageData.length); //length of message
                writer.write(message.messageData);
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public void sendClient(Socket client, byte[] message) {
        try {
            DataOutputStream writer = new DataOutputStream(client.getOutputStream());
            writer.writeInt(MessageType.MESSAGE.getValue()); //type of message
            writer.writeInt(message.length); //length of message
            writer.write(message);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void sendBoard(Socket client, byte[] Board) {
        
    }

    public void send(byte[] message) {
        broadcast(message);
    }

    public void send(Message message) {
        broadcast(message);
    }

    public byte[] receive() {
        //handle incoming message
        //may not need this, as the server is only responsible for broadcasting messages to clients, not processing them
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
                if (!addr.isLoopbackAddress() && addr instanceof Inet6Address) {
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
}
