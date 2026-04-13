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
import java.net.InetAddress;
import java.net.Socket;
import java.util.function.Consumer;
import Components.ComponentMacros.MessageType;

import Components.Message;

public class Client implements User {
    private volatile boolean running = true;
    private Socket server;
    private String HOSTNAME;
    private DataInputStream reader;
    private DataOutputStream writer;
    private Consumer<Message> onMessageReceived;
    public String connected_server = "";


    public Client(InetAddress address) {
        this.HOSTNAME = address.getHostAddress();
        try {
            server = new Socket(address, 50000);
            reader = new DataInputStream(server.getInputStream());
            writer = new DataOutputStream(server.getOutputStream());
            Thread receiveThread = new Thread(this::receiveLoop);
            receiveThread.setDaemon(true);  // Exit with app
            receiveThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Client(InetAddress address, int port) {
        this.HOSTNAME = address.getHostAddress();
        try {
            server = new Socket(address, port);
            reader = new DataInputStream(server.getInputStream());
            writer = new DataOutputStream(server.getOutputStream());
            Thread receiveThread = new Thread(this::receiveLoop);
            receiveThread.setDaemon(true);  // Exit with app
            receiveThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Client(Socket s) {
        this.HOSTNAME = s.getInetAddress().getHostAddress();
        try {
            server = s;
            reader = new DataInputStream(server.getInputStream());
            writer = new DataOutputStream(server.getOutputStream());
            Thread receiveThread = new Thread(this::receiveLoop);
            receiveThread.setDaemon(true);  // Exit with app
            receiveThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Client(InetAddress address, int port, Consumer<Message> listener) {
        this.HOSTNAME = address.getHostAddress();
        this.onMessageReceived = listener;
        try {
            server = new Socket(address, port);
            reader = new DataInputStream(server.getInputStream());
            writer = new DataOutputStream(server.getOutputStream());
            Thread receiveThread = new Thread(this::receiveLoop);
            receiveThread.setDaemon(true);  // Exit with app
            receiveThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void receiveLoop() {
        while(running) {
            try {
                byte[] data = receive();
                BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data)));
                if (data.length > 0) {
                    int type = br.read();
                    int length = br.read();
                    char[] message = new char[length];
                    br.read(message, 0, length);
                    String msgStr = new String(message);
                    if (type == MessageType.BROADCAST.getValue()) {
                    System.out.println("Received message: " + msgStr);
                    onMessageReceived.accept(new Message(getName(), msgStr));
                }
                    else if (type == MessageType.WELCOME.getValue()) {
                        System.out.println("Received welcome message: " + msgStr);
                        send(("ACK Hello, from " + getName()).getBytes());
                    }
                    else if(type == MessageType.MESSAGE.getValue()) {
                        // Probably an area where something specific happens because the message is directed towards this client
                        System.out.println("Received message: " + msgStr);
                        onMessageReceived.accept(new Message(getName(), msgStr));
                    }
            }
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    //User Interface contracts
    public void send(byte[] message) {
        try {
            sendServer(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void send(Message message) {
        try {
            sendServer(message.messageData);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public byte[] receive() {
        try {
            return receiveClient().toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    public void sendServer(byte[] data) throws IOException, UnsupportedEncodingException {
        writer.writeInt(MessageType.MESSAGE.getValue()); //type of message
        writer.writeInt(data.length);
        writer.write(data);
        writer.flush();
    }

    //expecting to "joker" the stream
    public ByteArrayOutputStream receiveClient() throws IOException {
        if (server == null || server.isClosed()) {
            System.out.println("Socket is not connected.");
            return new ByteArrayOutputStream();
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int type = reader.readInt();
        int length = reader.readInt();
        byte[] data = new byte[length];
        reader.readFully(data);
        buffer.write(type);
        buffer.write(length);
        buffer.write(data);
        return buffer;
    }
    public void setOnMessageReceived(Consumer<Message> listener) {
        this.onMessageReceived = listener;
    }

    public String getName() {
        return HOSTNAME;
    }
}

