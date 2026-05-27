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
    HashMap<SocketAddress, Long> clients = new HashMap<>(); // Track client activity timestamp
    private InetAddress ADDRESS;
    private int PORT;
    private static final int INACTIVITY_TIMEOUT = 30000;
    private static final boolean DEBUG_PACKET_TYPE = Preferences.userRoot().node("wheelbarrow/debug").getBoolean("mode", false);

    // Threads
    private volatile Thread receiveThread;
    private volatile Thread broadcastThread;

    //Locks
    private ReentrantLock audioLock = new ReentrantLock();
    private ReentrantLock videoLock = new ReentrantLock();

    private volatile boolean running = false;
    
    AtomicReference<BlockingQueue<byte[]>> outboundQueue = new AtomicReference<>(new LinkedBlockingQueue<>(100));

    public CallServer() {
        //what if already hosting server? could just use the same socket
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
            audioCall = new AudioCallServer();
            audioCall.start();
            audioCall.setOnAudioSupply(this::supplyAudio);
        } finally {
            audioLock.unlock();
        }
    }

    private void supplyAudio(byte[] audioData) {
        outboundQueue.get().offer(audioData);
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
            videoCall = new VideoCallServer();
            videoCall.start();
            videoCall.setOnVideoSupply(this::supplyVideo);
        } finally {
            videoLock.unlock();
        }
    }

    private void supplyVideo(byte[] videoData) {
        outboundQueue.get().offer(videoData);
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
                }
                clients.put(clientAddress, currentTime);
            }

            //determine type by checking against format
            int type;
            if (request.getLength() <= AudioCall.NETWORK_BUFFER_SIZE) {
                type = DatagramType.AUDIO.getValue();
            } else if (request.getLength() <= VideoCall.NETWORK_BUFFER_SIZE) {
                type = DatagramType.VIDEO.getValue();
            } else {
                //Really we would want a format test here if the sizes fail
                type = DatagramType.UNKNOWN.getValue(); // Treat as debug or unknown type
            }
            
            if (DEBUG_PACKET_TYPE) {
                System.out.println("[CallServer][RECV] from=" + clientAddress + " type=" + type + " (" + typeToLabel(type) + ") payload=" + request.getLength());
            }
            
            byte[] packetData = request.getData();
            if (type == DatagramType.AUDIO.getValue()) {
                byte[] audioData = Arrays.copyOf(packetData, request.getLength());
                receiveAudio(audioData);
            } else if (type == DatagramType.VIDEO.getValue()) {
                byte[] videoData = Arrays.copyOf(packetData, request.getLength());
                receiveVideo(videoData);
            } else if (type == DatagramType.UNKNOWN.getValue()) {
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
                List<Map.Entry<SocketAddress, Long>> toRemove = new ArrayList<>();
                clients.forEach((k, v) -> {
                        toRemove.add(Map.entry(k, v));
                });
                for (byte[] packetData : batch) {
                    if (packetData == null) {
                        continue;
                    }
                int iterator = 0;
                while (toRemove.size() > 0) {
                    Map.Entry<SocketAddress, Long> entry = toRemove.get(iterator);
                    long lastActivityTime = entry.getValue();
                    long currentTime = System.currentTimeMillis();

                    if (currentTime - (currentTime - startTime) - lastActivityTime > INACTIVITY_TIMEOUT) {
                        SocketAddress inactiveClient = entry.getKey();
                        clients.remove(entry.getKey());
                        System.out.println("Removed inactive client: " + inactiveClient);
                    } else {
                        // Determine packet type
                        int type;
                        if (packetData.length <= AudioCall.NETWORK_BUFFER_SIZE) {
                            type = DatagramType.AUDIO.getValue();
                        } else if (packetData.length <= VideoCall.NETWORK_BUFFER_SIZE) {
                            type = DatagramType.VIDEO.getValue();
                        } else {
                            type = DatagramType.UNKNOWN.getValue();
                        }
                        
                        try {
                            if (DEBUG_PACKET_TYPE) {
                                System.out.println("[CallServer][SEND] to=" + entry.getKey() + " type=" + type + " (" + typeToLabel(type) + ") payload=" + packetData.length);
                            }
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

    private void receiveAudio(byte[] data) {
        audioLock.lock();
        try {
            if (audioCall != null) {
                audioCall.offer(data);
            }
        } finally {
            audioLock.unlock();
        }
    }

    private void receiveVideo(byte[] data) {
        videoLock.lock();
        try {
            if (videoCall != null) {
                videoCall.offer(data);
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
