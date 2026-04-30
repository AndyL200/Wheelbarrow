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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import Components.ComponentMacros.DatagramType;

public class CallClient extends CallObj implements AutoCloseable {
    DatagramSocket docket;
    InetAddress serverAddress;
    int serverPort;
    private volatile boolean running = false;

    int INCLUSIVE_BUFFER_SIZE = Call.GENERIC_BUFFER_SIZE + AudioCall.NETWORK_BUFFER_SIZE + VideoCall.NETWORK_BUFFER_SIZE; // Adjust as needed
    private static final int INACTIVITY_TIMEOUT = 2000; // 2 seconds in milliseconds


    // Threads
    Thread sendThread;
    Thread receiveThread;

    BlockingQueue<Packet> outboundQueue = new LinkedBlockingQueue<>(40);

    public CallClient(String serverHost, int serverPort) {
        try {
            this.serverAddress = InetAddress.getByName(serverHost);
            this.serverPort = serverPort;
            this.docket = new DatagramSocket(50000);
            this.docket.setSoTimeout(INACTIVITY_TIMEOUT); // 2 second timeout for receive
        } catch (SocketException s1) {
            
            try {
                this.docket = new DatagramSocket(0); // try any available port
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


       
        this.sendThread.interrupt();
        try {
            this.sendThread.join();
        } catch (InterruptedException e) {
            this.sendThread.interrupt();
        }
        this.receiveThread.interrupt();
        try {
            this.receiveThread.join();
        } catch (InterruptedException e) {
            this.receiveThread.interrupt();
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

    public void openAudioCall() {
        audioCall = new AudioCallClient();
        audioCall.start();
        audioCall.setOnAudioSupply(this::supplyAudio);
    }

    private void supplyAudio(byte[] audioData) {
        outboundQueue.offer(new Packet(DatagramType.AUDIO, audioData));
    }

    public void openVideoCall() {
        videoCall = new VideoCallClient();
        videoCall.start();
        videoCall.setOnVideoSupply(this::supplyVideo);
    }

    private void supplyVideo(byte[] videoData) {
        outboundQueue.offer(new Packet(DatagramType.VIDEO, videoData));
    }

    private void handleSend() {
        while (running) {
            try {
            Packet first = outboundQueue.take();
            List<Packet> batch = new ArrayList<>();
            batch.add(first);
            outboundQueue.drainTo(batch);

            for (Packet packet : batch) {
                if (packet.type() == DatagramType.AUDIO) {
                    sendAudio(packet.data());
                } else if (packet.type() == DatagramType.VIDEO) {
                    sendVideo(packet.data());
                }
            }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void handleReceive() {
        while (running) {
            byte[] networkData = new byte[INCLUSIVE_BUFFER_SIZE];
            DatagramPacket request = new DatagramPacket(networkData, networkData.length);
            try {
                docket.receive(request);
            } catch (IOException e) {
                System.out.println("Error receiving packet: " + e.getMessage());
                continue;
            }

            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(request.getData(), 0, request.getLength())); // always reconstructing?????

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
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(DatagramType.AUDIO.getValue());
            dos.write(audioData);
            dos.flush();

            byte[] packet = baos.toByteArray();
            DatagramPacket dgPacket = new DatagramPacket(packet, packet.length, serverAddress, serverPort);
            docket.send(dgPacket);
        } catch (IOException e) {
            System.out.println("Error sending audio packet: " + e.getMessage());
        }
    }

    private void sendVideo(byte[] videoData) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(DatagramType.VIDEO.getValue());
            dos.write(videoData);
            dos.flush();

            byte[] packet = baos.toByteArray();
            DatagramPacket dgPacket = new DatagramPacket(packet, packet.length, serverAddress, serverPort);
            docket.send(dgPacket);
        } catch (IOException e) {
            System.out.println("Error sending video packet: " + e.getMessage());
        }
    }

    public void queueAudio(byte[] audioData) {
        outboundQueue.offer(new Packet(DatagramType.AUDIO, audioData));
    }

    public void queueVideo(byte[] videoData) {
        outboundQueue.offer(new Packet(DatagramType.VIDEO, videoData));
    }

}
