package Network;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import Components.ComponentMacros.DatagramType;
import javafx.scene.chart.PieChart.Data;



public class CallServer extends CallObj implements AutoCloseable {
    DatagramSocket docket;
    HashMap<SocketAddress, Long> clients = new HashMap<>(); // Track client activity timestamp
    private InetAddress ADDRESS;
    private int PORT;
    private static final int INACTIVITY_TIMEOUT = 2000; // 2 seconds in milliseconds

    // Threads
    Thread receiveThread;
    Thread broadcastThread;

    private volatile boolean running = false;

    


    int INCLUSIVE_BUFFER_SIZE = Call.GENERIC_BUFFER_SIZE + AudioCall.NETWORK_BUFFER_SIZE + VideoCall.NETWORK_BUFFER_SIZE; // Adjust as needed

    
    BlockingQueue<Packet> outboundQueue = new LinkedBlockingQueue<>(40);

    public CallServer() {
        //what if already hosting server? could just use the same socket
        ADDRESS = getLocalNetworkAddress();
        this.PORT = 50000; // Default port, will be overridden if unavailable
        try {
            docket = new DatagramSocket(PORT, ADDRESS);
            docket.setSoTimeout(INACTIVITY_TIMEOUT); // 2 second timeout instead of 50
        }
        catch (SocketException s) {
            System.out.println("Could not create socket on port 50000 with address " + ADDRESS + ": " + s.getMessage());
            try {
                // Retry with wildcard address (null) to bind to all interfaces
                docket = new DatagramSocket(0, ADDRESS);
                PORT = docket.getLocalPort();
                docket.setSoTimeout(INACTIVITY_TIMEOUT); // 2 second timeout instead of 50
            } catch (SocketException e) {
                System.out.println("Could not create socket with address " + ADDRESS + ": " + e.getMessage());
                try {
                    // Final attempt with wildcard address and any available port
                    docket = new DatagramSocket(0);
                    PORT = docket.getLocalPort();
                    docket.setSoTimeout(INACTIVITY_TIMEOUT); // 2 second timeout instead of 50
                    ADDRESS = docket.getLocalAddress(); // Update ADDRESS to the one assigned by the socket
                } catch (SocketException ex) {
                    System.out.println("Failed to create socket: " + ex.getMessage());
                    throw new RuntimeException("Unable to start audio call server");
                }
                return;
            }
        }
        
        // Ensure ADDRESS is set from the socket if it's null
        if (ADDRESS == null) {
            ADDRESS = docket.getLocalAddress();
        }
        start();
    }


    public void openAudioCall() {
        audioCall = new AudioCallServer();
        audioCall.start();
        audioCall.setOnAudioSupply(this::supplyAudio);
    }

    private void supplyAudio(byte[] audioData) {
        outboundQueue.offer(new Packet(DatagramType.AUDIO, audioData));
    }

    public void openVideoCall() {
        videoCall = new VideoCallServer();
        videoCall.start();
        videoCall.setOnVideoSupply(this::supplyVideo);
        
    }

    private void supplyVideo(byte[] videoData) {
        outboundQueue.offer(new Packet(DatagramType.VIDEO, videoData));
    }

    @Override
    public AudioCall getAudio() {
        return audioCall;
    }

    @Override
    public VideoCall getVideo() {
        return videoCall;
    }


    private void handleReceive() {
        //receive the packet and feed it to correct call server
        //assumes generic buffer size is greater than network buffer size 
        while (running) {
            byte[] networkData = new byte[INCLUSIVE_BUFFER_SIZE];
            DatagramPacket request = new DatagramPacket(networkData, networkData.length);
            try {
                docket.receive(request);
            } catch (IOException e) {
                System.out.println("Error receiving packet: " + e.getMessage());
                continue;
            }

            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(request.getData(), 0, request.getLength())); // always reconstructing????

            // Handle new clients
            SocketAddress clientAddress = request.getSocketAddress();
            synchronized (clients) {
                long currentTime = System.currentTimeMillis();
                if (!clients.containsKey(clientAddress)) {
                    System.out.println("New client connected: " + clientAddress);
                    clients.put(clientAddress, currentTime);
                } else {
                    clients.put(clientAddress, currentTime);
                }
            }

            try {
            int type = dis.readInt(); //call type

            if (type == DatagramType.AUDIO.getValue()) {
                byte[] audioData = new byte[AudioCall.NETWORK_BUFFER_SIZE];
                dis.readFully(audioData, 0, AudioCall.NETWORK_BUFFER_SIZE);
                receiveAudio(audioData);
            }
            else if (type == DatagramType.VIDEO.getValue()) {
                byte[] videoData = new byte[VideoCall.NETWORK_BUFFER_SIZE];
                dis.readFully(videoData, 0, VideoCall.NETWORK_BUFFER_SIZE);
                receiveVideo(videoData);
            }
            } catch (IOException e) {
                System.out.println("Error parsing received packet: " + e.getMessage());
            }
        }
    }

     private void handleBroadcast() {
        
        while (running) {
            //Problem both of these will block
            //could use two separate sub threads or use a buffer
            // Block until at least one packet is ready
            Packet first = null;
            try {
                first = outboundQueue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Drain everything else that's already waiting
            List<Packet> batch = new ArrayList<>();
            batch.add(first);
            outboundQueue.drainTo(batch); // non-blocking, grabs whatever is there

            synchronized (clients) {
            Iterator<Map.Entry<SocketAddress, Long>> iterator = clients.entrySet().iterator();
            
            while (iterator.hasNext()) {
                Map.Entry<SocketAddress, Long> entry = iterator.next();
                long lastActivityTime = entry.getValue();
                long currentTime = System.currentTimeMillis();

                if (currentTime - lastActivityTime > INACTIVITY_TIMEOUT) {
                    SocketAddress inactiveClient = entry.getKey();
                    iterator.remove();
                    System.out.println("Removed inactive client: " + inactiveClient);
                }
                else {
                    for (Packet packet : batch) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        DataOutputStream dos = new DataOutputStream(baos);
                        if (packet != null) {
                            try {
                                    dos.writeInt(packet.type().getValue());
                                    dos.write(packet.data());
                                    byte[] packetData = baos.toByteArray();
                                    docket.send(new DatagramPacket(packetData, packetData.length, entry.getKey()));
                            } catch (IOException e) {
                                System.out.println("Error sending packet to " + entry.getKey() + ": " + e.getMessage());
                            }
                        }
                    }
                }
            }
        }
    }
}


    private void receiveAudio(byte[] data) {
        if (audioCall != null) {
            audioCall.offer(data);
        }
    }

    private void receiveVideo(byte[] data) {
        if (videoCall != null) {
            videoCall.offer(data);
        }
    }




   @Override
    public void close() {
        stop();
    }

    @Override
    public void start() {
        this.running = true;

        // Start receive/broadcast handler thread
        this.receiveThread = new Thread(this::handleReceive);
        this.receiveThread.setDaemon(true);
        this.receiveThread.start();

        this.broadcastThread = new Thread(this::handleBroadcast);
        this.broadcastThread.setDaemon(true);
        this.broadcastThread.start();

        openAudioCall();
    }


    @Override
    public void stop() {
        this.running = false;
        if (audioCall != null) {
            audioCall.stop();
        }
        if (videoCall != null) {
            videoCall.stop();
        }


        

        this.receiveThread.interrupt();
        this.broadcastThread.interrupt();
        try {
            this.receiveThread.join();
        } catch (InterruptedException e) {
            this.receiveThread.interrupt();
        }
        try {
            this.broadcastThread.join();
        } catch (InterruptedException e) {
            this.broadcastThread.interrupt();
        }

        try {
            docket.close();
        } catch (Exception e) {
            System.out.println("Error closing socket: " + e.getMessage());
        }
    }


    private InetAddress getLocalNetworkAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                // Skip loopback and inactive interfaces
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // Return first non-loopback, non-link-local IPv4 address
                    if (!addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                        return addr;
                    }
                }
            }
        } catch (SocketException e) {
            System.out.println("Error getting network interface: " + e.getMessage());
        }
        return null;
    }

    public InetAddress getAddress() {
        return ADDRESS;
    }

    public int getPort() {
        return PORT;
    }

}
