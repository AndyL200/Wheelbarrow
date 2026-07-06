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

    private static final int INACTIVITY_TIMEOUT = 20000; // 20 seconds in milliseconds
    private int NETWORK_BUFFER_SIZE = 2048; // TODO(This will changed based on the MTU)


    // Threads
    private volatile Thread sendThread;
    private volatile Thread receiveThread;

    AtomicReference<BlockingQueue<byte[]>> outboundQueue = new AtomicReference<>(new LinkedBlockingQueue<>(100));

    public CallClient(String serverHost, int serverPort) {
        try {
            this.serverAddress = InetAddress.getByName(serverHost);
            this.serverPort = serverPort;
            this.docket = new DatagramSocket(50000);
            this.docket.setSoTimeout(INACTIVITY_TIMEOUT); // 20 second timeout for receive
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
        audioCall = new AudioCall();
        audioCall.start();
        audioCall.setOnAudioSupply(this::supplyAudio);
    }

    private void supplyAudio(byte[] audioData) {
        System.out.println("[CallClient.supplyAudio] audio of size " + audioData.length + " supplied.");
        //split data into Network buffer sized chunks
        if(audioData.length > NETWORK_BUFFER_SIZE) {
            for (int i = 0; i < audioData.length; i += NETWORK_BUFFER_SIZE) {
                int end = Math.min(audioData.length, i + NETWORK_BUFFER_SIZE);
                byte[] identifiers = {(byte, (byte) DatagramType.AUDIO.getValue()}; // TODO(PAD FIRST TWO BYTES PREEMPTIVELY)
                byte[] chunk = new byte[end - i + identifiers.length];
                System.arraycopy(identifiers, 0, chunk, 0, identifiers.length);
                System.arraycopy(audioData, i, chunk, identifiers.length, end - i);
                outboundQueue.get().offer(chunk);
            }
        }
        else {
            outboundQueue.get().offer(audioData);
        }
    }


    @Override
    public void openVideoCall() {
        videoCall = new VideoCall();
        videoCall.start();
        videoCall.setOnVideoSupply(this::supplyVideo);
    }

    private void supplyVideo(byte[] videoData) {
        System.out.println("[CallClient.supplyVideo] video of size " + videoData.length + " supplied.");
        if (videoData.length > NETWORK_BUFFER_SIZE) {
            for(int i = 0; i < videoData.length; i += NETWORK_BUFFER_SIZE) {
                int end = Math.min(videoData.length, i + NETWORK_BUFFER_SIZE);
                byte[] identifiers = {(byte) 0, (byte) DatagramType.VIDEO.getValue()}; // TODO(PAD FIRST TWO BYTES PREEMPTIVELY)
                byte[] chunk = Arrays.copyOfRange(videoData, i, end);
                outboundQueue.get().offer(chunk);
            }
        }
        else {
            outboundQueue.get().offer(videoData);
        }
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
                DatagramType type;
                if (packetData[1] == DatagramType.AUDIO.getValue()) {
                    type = DatagramType.AUDIO;
                } else if (packetData[1] == DatagramType.VIDEO.getValue()) {
                    type = DatagramType.VIDEO;
                } else {
                    //Cannot be routed, do nothing
                    type = DatagramType.UNKNOWN;
                }

                 if (DEBUG_MODE) {
                        System.out.println("[CallClient][SEND] Sending packet. len=" + packetData.length + " type =" + type.toString());
                }

                if (type == DatagramType.AUDIO) {
                    sendAudio(type, packetData);
                } else if (type == DatagramType.VIDEO) {
                    sendVideo(type, packetData);
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

            DatagramType type;
            if (packetData[1] == DatagramType.AUDIO.getValue()) {
                type = DatagramType.AUDIO;
            } else if (packetData[1] == DatagramType.VIDEO.getValue()) {
                type = DatagramType.VIDEO;
            } else {
                //Really we would want a format test here if the sizes fail
                type = DatagramType.UNKNOWN; // Treat as debug or unknown type
            }
            int client_id = packetData[0]; 

            if (DEBUG_MODE) {
                System.out.println("[CallClient][RECV] Receiving packet. len=" + request.getLength() + " type=" + type.toString());
            }

            if (type == DatagramType.AUDIO) {
                byte[] audioData = Arrays.copyOf(packetData, request.getLength());
                receiveAudio(type, audioData, client_id);
            }
            else if (type == DatagramType.VIDEO) {
                byte[] videoData = Arrays.copyOf(packetData, request.getLength());
                receiveVideo(type, videoData, client_id);
            } else if (type == DatagramType.UNKNOWN) {
                System.out.println("[CallClient][RECV] Unknown datagram type=" + type);
            }
        }
    }

    private void receiveAudio(DatagramType type, byte[] data, int client_id) {
        if (audioCall != null && type == DatagramType.AUDIO) {
            audioCall.offer(client_id, data);
        }
    }

    private void receiveVideo(DatagramType type, byte[] data, int client_id) {
        if (videoCall != null && type == DatagramType.VIDEO) {
            videoCall.offer(client_id, data);
        }
    }

    private void sendAudio(DatagramType type, byte[] audioData) {
        try {
            if (DEBUG_MODE) {
                System.out.println("[CallClient][SEND][AUDIO] payload=" + audioData.length);
            }
            byte typeByte = (byte) type.getValue();
            audioData[1] = typeByte; // Prepend type byte to the audio data TODO(ALWAYS PAD FIRST TWO BYTES PREEMPTIVELY)
            DatagramPacket dgPacket = new DatagramPacket(audioData, audioData.length, serverAddress, serverPort);
            docket.send(dgPacket);
            System.out.println("[CallClient][SEND][AUDIO] Sent packet to server: " + serverAddress + ":" + serverPort);
        } catch (IOException e) {
            System.out.println("[CallClient][SEND][AUDIO] Error sending packet: " + e.getMessage());
        }
    }

    private void sendVideo(DatagramType type, byte[] videoData) {
        try {
            if (DEBUG_MODE) {
                System.out.println("[CallClient][SEND][VIDEO] payload=" + videoData.length);
            }
            byte typeByte = (byte) type.getValue();
            videoData[1] = typeByte; // Prepend type byte to the video data TODO(ALWAYS PAD FIRST TWO BYTES PREEMPTIVELY)
            DatagramPacket dgPacket = new DatagramPacket(videoData, videoData.length, serverAddress, serverPort);
            docket.send(dgPacket);
            System.out.println("[CallClient][SEND][VIDEO] Sent packet to server: " + serverAddress + ":" + serverPort);
        } catch (IOException e) {
            System.out.println("[CallClient][SEND][VIDEO] Error sending packet: " + e.getMessage());
        }
    }

}
