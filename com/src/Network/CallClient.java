package Network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.prefs.Preferences;
import java.util.concurrent.atomic.AtomicReference;


import Components.ComponentMacros.DatagramType;

public class CallClient extends CallObj implements AutoCloseable {
    private volatile DatagramSocket docket;
    InetAddress serverAddress;
    int serverPort;
    private volatile boolean running = false;
    private static final boolean DEBUG_MODE = Preferences.userRoot().node("wheelbarrow/debug").getBoolean("mode", false);

    private static final int INACTIVITY_TIMEOUT = 2000; // 2 seconds in milliseconds


    // Threads
    private volatile Thread sendThread;
    private volatile Thread receiveThread;

    AtomicReference<BlockingQueue<byte[]>> outboundQueue = new AtomicReference<>(new LinkedBlockingQueue<>(100));

    public CallClient(String serverHost, int serverPort) {
        try {
            this.serverAddress = InetAddress.getByName(serverHost);
            this.serverPort = serverPort;
            this.docket = new DatagramSocket(50000);
            this.docket.setSoTimeout(INACTIVITY_TIMEOUT); // 2 second timeout for receive
        } catch (SocketException s1) {
            
            try {
                this.docket = new DatagramSocket(0); // try any available port
                this.docket.setSoTimeout(INACTIVITY_TIMEOUT);
            } catch (Exception e) {
                System.out.println("Could not create socket: " + s1.getMessage() + "Closing socket...");
                close();
            }
        } catch (Exception e) {
            System.out.println("Error initializing client: " + e.getMessage());
            throw new RuntimeException("Unable to start call client");
        }
    }

    @Override
    public void start() {
        running = true;

        // Start send thread (sends data to server)
        this.sendThread = new Thread(this::handleSend);
        this.sendThread.setDaemon(true);
        this.sendThread.start();

        // Start receive thread (receives from server and distributes to call clients)
        this.receiveThread = new Thread(this::handleReceive);
        this.receiveThread.setDaemon(true);
        this.receiveThread.start();
    }

    @Override
    public void stop() {
        running = false;

        if (audioCall != null) {
            audioCall.stop();
        }
        if (videoCall != null) {
            videoCall.stop();
        }


        if (this.sendThread != null) {
        this.sendThread.interrupt();
            try {
                this.sendThread.join();
            } catch (InterruptedException e) {
                this.sendThread.interrupt();
            }
        }
        if (this.receiveThread != null) {
            this.receiveThread.interrupt();
            try {
                this.receiveThread.join();
            } catch (InterruptedException e) {
                this.receiveThread.interrupt();
            }
        }   

        try {
            docket.close();
        } catch (Exception e) {
            System.out.println("Error closing socket: " + e.getMessage());
        }
        
    }

    @Override
    public void close() {
        stop();
    }
    @Override
    public void openAudioCall() {
        audioCall = new AudioCallClient();
        audioCall.start();
        audioCall.setOnAudioSupply(this::supplyAudio);
    }

    private void supplyAudio(byte[] audioData) {
        outboundQueue.get().offer(audioData);
    }
    @Override
    public void openVideoCall() {
        videoCall = new VideoCallClient();
        videoCall.start();
        videoCall.setOnVideoSupply(this::supplyVideo);
    }

    private void supplyVideo(byte[] videoData) {
        outboundQueue.get().offer(videoData);
    }

    private void handleSend() {
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

            for (byte[] packetData : batch) {
                int type;
                if (packetData.length <= AudioCall.NETWORK_BUFFER_SIZE) {
                    type = DatagramType.AUDIO.getValue();
                } else if (packetData.length <= VideoCall.NETWORK_BUFFER_SIZE) {
                    type = DatagramType.VIDEO.getValue();
                } else {
                    //Really we would want a format test here if the sizes fail
                    type = DatagramType.UNKNOWN.getValue(); // Treat as debug or unknown type
                }

                 if (DEBUG_MODE) {
                        System.out.println("[CallClient][SEND] Sending packet. len=" + packetData.length + " type =" + typeToLabel(type));
                }

                if (type == DatagramType.AUDIO.getValue()) {
                    sendAudio(packetData);
                } else if (type == DatagramType.VIDEO.getValue()) {
                    sendVideo(packetData);
                }
            }
        }
    }

    private void handleReceive() {
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

            byte[] packetData = request.getData();

            int type;
            if (request.getLength() <= AudioCall.NETWORK_BUFFER_SIZE) {
                type = DatagramType.AUDIO.getValue();
                //Video Call packets will be larger than audio
            } else if (request.getLength() <= VideoCall.NETWORK_BUFFER_SIZE) {
                type = DatagramType.VIDEO.getValue();
            } else {
                //Really we would want a format test here if the sizes fail
                type = DatagramType.UNKNOWN.getValue(); // Treat as debug or unknown type
            }

            if (DEBUG_MODE) {
                System.out.println("[CallClient][RECV] Receiving packet. len=" + request.getLength() + " type=" + typeToLabel(type));
            }

            if (type == DatagramType.AUDIO.getValue()) {
                byte[] audioData = Arrays.copyOf(packetData, request.getLength());
                receiveAudio(audioData);
            }
            else if (type == DatagramType.VIDEO.getValue()) {
                byte[] videoData = Arrays.copyOf(packetData, request.getLength());
                receiveVideo(videoData);
            } else if (type == DatagramType.UNKNOWN.getValue()) {
                System.out.println("[CallClient][RECV] Unknown datagram type=" + type);
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

    private void sendAudio(byte[] audioData) {
        try {
            if (DEBUG_MODE) {
                System.out.println("[CallClient][SEND][AUDIO] payload=" + audioData.length);
            }
            DatagramPacket dgPacket = new DatagramPacket(audioData, audioData.length, serverAddress, serverPort);
            docket.send(dgPacket);
            System.out.println("[CallClient][SEND][AUDIO] Sent packet to server: " + serverAddress + ":" + serverPort);
        } catch (IOException e) {
            System.out.println("[CallClient][SEND][AUDIO] Error sending packet: " + e.getMessage());
        }
    }

    private void sendVideo(byte[] videoData) {
        try {
            if (DEBUG_MODE) {
                System.out.println("[CallClient][SEND][VIDEO] payload=" + videoData.length);
            }
            DatagramPacket dgPacket = new DatagramPacket(videoData, videoData.length, serverAddress, serverPort);
            docket.send(dgPacket);
            System.out.println("[CallClient][SEND][VIDEO] Sent packet to server: " + serverAddress + ":" + serverPort);
        } catch (IOException e) {
            System.out.println("[CallClient][SEND][VIDEO] Error sending packet: " + e.getMessage());
        }
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
