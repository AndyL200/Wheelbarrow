package Network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.prefs.Preferences;
import java.util.concurrent.atomic.AtomicReference;


import Components.ComponentMacros.DatagramType;



public class CallServer extends CallObj implements AutoCloseable {
    DatagramSocket docket;
    HashMap<SocketAddress, Pair<Integer, Long>> clients = new HashMap<>(); // Track client activity timestamp
    private InetAddress ADDRESS;
    private int PORT;
    private static final int INACTIVITY_TIMEOUT = 30000;
    private static final boolean DEBUG_PACKET_TYPE = Preferences.userRoot().node("wheelbarrow/debug").getBoolean("mode", false);
    private int NETWORK_BUFFER_SIZE = 2048; // Default, will be set to MTU if possible
    // Threads
    private volatile Thread receiveThread;
    private volatile Thread broadcastThread;

    //Locks
    private ReentrantLock audioLock = new ReentrantLock();
    private ReentrantLock videoLock = new ReentrantLock();

    private volatile boolean running = false;
    
    AtomicReference<BlockingQueue<byte[]>> outboundQueue = new AtomicReference<>(new LinkedBlockingQueue<>(100));
    private final int SERVER_ID;
    public CallServer() {
        //what if already hosting server? could just use the same socket
        SERVER_ID = 0;
        ADDRESS = getLocalNetworkAddress();
        this.PORT = 50000; // Default port, will be overridden if unavailable
        try {
            docket = new DatagramSocket(PORT, ADDRESS);
            docket.setSoTimeout(INACTIVITY_TIMEOUT); // 2 second timeout instead of 50
        }
        catch (SocketException s1) {
            System.out.println("Could not create socket on port 50000 with address " + ADDRESS + ": " + s1.getMessage());
            try {
                // Retry with wildcard address (null) to bind to all interfaces
                docket = new DatagramSocket(0, ADDRESS);
                PORT = docket.getLocalPort();
                docket.setSoTimeout(INACTIVITY_TIMEOUT);
            } catch (SocketException s2) {
                System.out.println("Could not create socket with address " + ADDRESS + ": " + s2.getMessage());
                try {
                    // Final attempt with wildcard address and any available port
                    docket = new DatagramSocket(0);
                    PORT = docket.getLocalPort();
                    docket.setSoTimeout(INACTIVITY_TIMEOUT);
                    ADDRESS = docket.getLocalAddress();
                } catch (SocketException ex) {
                    System.out.println("Failed to create socket: " + ex.getMessage());
                    throw new RuntimeException("Unable to start audio call server");
                }
                return;
            }
        }
        int[] minMTU = {65535};
        try {
        NetworkInterface.getNetworkInterfaces().asIterator().forEachRemaining((ni) -> {
            try {
                if (ni.isLoopback() || !ni.isUp()) {
                    return;
                }
                int mtu = ni.getMTU();
                if (mtu > 0 && mtu < minMTU[0]) {
                    minMTU[0] = mtu;
                }
            } catch (SocketException e) {
                System.out.println("Error checking network interface: " + e.getMessage());
                return;
            }
            
        });
            docket.setSendBufferSize(minMTU[0]);
        } catch (SocketException se) {
            System.out.println("Failed to set send buffer size: " + se.getMessage());
        }

        if (NETWORK_BUFFER_SIZE > minMTU[0]) {
            NETWORK_BUFFER_SIZE = minMTU[0] - 100; // Leave some overhead for headers
            System.out.println("Adjusted network buffer size to " + NETWORK_BUFFER_SIZE + " based on MTU");
        }

        if (ADDRESS == null) {
            ADDRESS = docket.getLocalAddress();
        }
    }
    @Override
    public void openAudioCall() {
        if (!running) {
            System.out.println("Server not running, cannot open audio call");
            return;
        }
        audioLock.lock();
        try {
            if (audioCall != null) {
                System.out.println("Audio call already open");
                return;
            }
            audioCall = new AudioCall();
            audioCall.start();
            audioCall.setOnAudioSupply(this::supplyAudio);
        } finally {
            audioLock.unlock();
        }
    }

    private void supplyAudio(byte[] audioData) {
        System.out.println("[CallClient.supplyAudio] audio of size " + audioData.length + " supplied.");
        //split data into Network buffer sized chunks
        if(audioData.length > NETWORK_BUFFER_SIZE) {
            for (int i = 0; i < audioData.length; i += NETWORK_BUFFER_SIZE) {
                int end = Math.min(audioData.length, i + NETWORK_BUFFER_SIZE);
                byte[] identifiers = {(byte) SERVER_ID, (byte) DatagramType.AUDIO.getValue()};
                byte[] chunk = new byte[end - i + identifiers.length];
                System.arraycopy(identifiers, 0, chunk, 0, identifiers.length);
                System.arraycopy(audioData, i, chunk, identifiers.length, end - i);
                System.out.println("[CallClient.supplyAudio] offering chunk of size " + chunk.length);
                outboundQueue.get().offer(chunk);
            }
        }
        else {
            outboundQueue.get().offer(audioData);
        }
    }

    @Override
    public void openVideoCall() {
        if (!running) {
            System.out.println("Server not running, cannot open video call");
            return;
        }
        videoLock.lock();
        try {
            if (videoCall != null) {
                System.out.println("Video call already open");
                return;
            }
            videoCall = new VideoCall();
            videoCall.start();
            videoCall.setOnVideoSupply(this::supplyVideo);
        } finally {
            videoLock.unlock();
        }
    }

    private void supplyVideo(byte[] videoData) {
        System.out.println("[CallClient.supplyVideo] video of size " + videoData.length + " supplied.");
        if (videoData.length > NETWORK_BUFFER_SIZE) {
            for(int i = 0; i < videoData.length; i += NETWORK_BUFFER_SIZE) {
                int end = Math.min(videoData.length, i + NETWORK_BUFFER_SIZE);
                byte[] identifiers = {(byte) SERVER_ID, (byte) DatagramType.VIDEO.getValue()};
                byte[] chunk = new byte[end - i + identifiers.length];
                System.arraycopy(identifiers, 0, chunk, 0, identifiers.length);
                System.arraycopy(videoData, i, chunk, identifiers.length, end - i);
                outboundQueue.get().offer(chunk);
            }
        }
        else {
            outboundQueue.get().offer(videoData);
        }
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
            byte[] networkData = new byte[65535]; // Max UDP packet size
            DatagramPacket request = new DatagramPacket(networkData, networkData.length);
            try {
                docket.receive(request);
            } catch (SocketTimeoutException ste) {
                // Handle socket timeout
                continue; // Just continue
            } catch (IOException io) {
                System.out.println("Error receiving packet: " + io.getMessage());
                continue;
            }

            // Handle new clients
            SocketAddress clientAddress = request.getSocketAddress();
            synchronized (clients) {
                long currentTime = System.currentTimeMillis();
                if (!clients.containsKey(clientAddress)) {
                    System.out.println("New client connected: " + clientAddress);
                    clients.put(clientAddress, new Pair<>(clients.size()-1, currentTime));
                }
                else {
                    clients.put(clientAddress, new Pair<>(clients.get(clientAddress).first(), currentTime)); // Update last activity time
                }
                
            }

            byte[] packetData = request.getData();

            //determine type by checking against format
            DatagramType type;
            if (packetData[1] == DatagramType.AUDIO.getValue()) {
                type = DatagramType.AUDIO;
            } else if (packetData[1] == DatagramType.VIDEO.getValue()) {
                type = DatagramType.VIDEO;
            } else {
                //Really we would want a format test here if the sizes fail
                type = DatagramType.UNKNOWN; // Treat as debug or unknown type
            }
            
            if (DEBUG_PACKET_TYPE) {
                System.out.println("[CallServer][RECV] from=" + clientAddress + " type=" + type + " (" + type.toString() + ") payload=" + request.getLength());
            }
            
            int client_id = clients.get(clientAddress).first(); // Get client ID based on address
            if (type == DatagramType.AUDIO) {
                byte[] audioData = new byte[request.getLength() + 2];
                audioData[0] = (byte) SERVER_ID;
                audioData[1] = (byte) DatagramType.AUDIO.getValue();
                System.arraycopy(packetData, 2, audioData, 2, request.getLength() - 2);
                receiveAudio(type, audioData, client_id);
            } else if (type == DatagramType.VIDEO) {
                byte[] videoData = new byte[request.getLength() + 2];
                videoData[0] = (byte) SERVER_ID;
                videoData[1] = (byte) DatagramType.VIDEO.getValue();
                System.arraycopy(packetData, 2, videoData, 2, request.getLength() - 2);
                receiveVideo(type, videoData, client_id);
            } else if (type == DatagramType.UNKNOWN) {
                System.out.println("[CallServer][RECV] Unknown datagram type=" + type);
            }
        }
    }

    private void handleBroadcast() {
        while (running) {
            byte[] first;
            try {
                first = outboundQueue.get().take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            List<byte[]> batch = new ArrayList<>();
            batch.add(first);
            outboundQueue.get().drainTo(batch);

            synchronized (clients) {
                long startTime = System.currentTimeMillis();
                for (byte[] packetData : batch) {
                    if (packetData == null) {
                        continue;
                    }
                    Iterator<SocketAddress> iterator = clients.keySet().iterator();
                    while (iterator.hasNext()) {
                        SocketAddress client = iterator.next();
                        Long lastActivityTime = clients.get(client).second();
                        long currentTime = System.currentTimeMillis();

                        if (currentTime - (currentTime - startTime) - lastActivityTime > INACTIVITY_TIMEOUT) {
                            clients.remove(client);
                            System.out.println("Removed inactive client: " + client);
                        } else {
                            try {
                                if (DEBUG_PACKET_TYPE) {
                                    System.out.println("[CallServer][SEND] to=" + client + " payload=" + packetData.length);
                                }
                                docket.send(new DatagramPacket(packetData, packetData.length, client));
                            } catch (IOException e) {
                                System.out.println("Error sending packet to " + client + ": " + e.getMessage());
                            }
                        }
                    }
                }
            }
        }
    }

    private void receiveAudio(DatagramType type, byte[] data, int client_id) {
        audioLock.lock();
        try {
            if (audioCall != null && type == DatagramType.AUDIO) {
                audioCall.offer(client_id, data);
            }
        } finally {
            audioLock.unlock();
            outboundQueue.get().offer(data); // Echo back to all clients, including sender, for testing
        }
    }

    private void receiveVideo(DatagramType type, byte[] data, int client_id) {
        videoLock.lock();
        try {
            if (videoCall != null && type == DatagramType.VIDEO) {
                videoCall.offer(client_id, data);
            }
        } finally {
            videoLock.unlock();
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
    }

    @Override
    public void stop() {
        this.running = false;
        audioLock.lock();
        try {
            if (audioCall != null) {
                audioCall.stop();
            }
        } finally {
            audioLock.unlock();
        }
        videoLock.lock();
        try {
            if (videoCall != null) {
                videoCall.stop();
            }
        } finally {
            videoLock.unlock();
        }

        
        
        if (this.receiveThread != null) {
            this.receiveThread.interrupt();
            try {
                this.receiveThread.join();
            } catch (InterruptedException e) {
                this.receiveThread.interrupt();
            }
        }
        if (this.broadcastThread != null) {
            this.broadcastThread.interrupt();
            try {
                this.broadcastThread.join();
            } catch (InterruptedException e) {
                this.broadcastThread.interrupt();
            }
        }

        try {
            docket.close();
        } catch (Exception e) {
            System.out.println("Error closing socket: " + e.getMessage());
        }
        clients.clear();
    }

    private InetAddress getLocalNetworkAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
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

    private String typeToLabel(int type) {
        if (type == DatagramType.AUDIO.getValue()) {
            return "AUDIO";
        }
        if (type == DatagramType.VIDEO.getValue()) {
            return "VIDEO";
        }
        return "UNKNOWN";
    }
}
