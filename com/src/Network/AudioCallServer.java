package Network;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

public class AudioCallServer implements AudioCall, AutoCloseable {
    AudioFormat micFmt;
    AudioFormat speakerFmt;

    AudioInputStream micStream;
    AudioInputStream spkrStream;

    PipedOutputStream micPipe;
    PipedOutputStream spkrPipe;

    TargetDataLine mic;
    SourceDataLine speaker;
    //UDP
    DatagramSocket docket;
    HashMap<SocketAddress, Long> clients = new HashMap<>(); // Track client activity timestamp
    private InetAddress ADDRESS;
    private int PORT;
    private volatile boolean running = false;
    private static final long INACTIVITY_TIMEOUT = 30000; // 30 seconds in milliseconds

    // Threads
    Thread receiveThread;
    Thread broadcastThread;
    Thread consumeThread;

    BlockingQueue<byte[]> jitterQueue = new LinkedBlockingQueue<>(20); // ~1 second at 50ms packets

    public AudioCallServer() {
       
        //what if already hosting server? could just use the same socket
        ADDRESS = getLocalNetworkAddress();
        this.PORT = 50000; // Default port, will be overridden if unavailable
        try {
            docket = new DatagramSocket(PORT, ADDRESS);
            docket.setSoTimeout(2000); // 2 second timeout instead of 50
        }
        catch (SocketException s) {
            System.out.println("Could not create socket on port 50000 with address " + ADDRESS + ": " + s.getMessage());
            try {
                // Retry with wildcard address (null) to bind to all interfaces
                docket = new DatagramSocket(0, ADDRESS);
                PORT = docket.getLocalPort();
                docket.setSoTimeout(2000); // 2 second timeout instead of 50
            } catch (SocketException e) {
                System.out.println("Could not create socket with address " + ADDRESS + ": " + e.getMessage());
                try {
                    // Final attempt with wildcard address and any available port
                    docket = new DatagramSocket(0);
                    PORT = docket.getLocalPort();
                    docket.setSoTimeout(2000); // 2 second timeout instead of 50
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
        
        micFmt = AudioCall.getBestFormat(null); // Use default mixer
        speakerFmt = AudioCall.getBestFormat(null); // Use default mixer
        micPipe = new PipedOutputStream();
        spkrPipe = new PipedOutputStream();

        try {
            mic = AudioSystem.getTargetDataLine(micFmt);
            speaker = AudioSystem.getSourceDataLine(speakerFmt);
        } catch (Exception e) {
            System.out.println("Error initializing audio devices: " + e.getMessage());
        }
    }
    @Override
    public void start() {
        try {
            // Setup mic pipeline
            mic.open(micFmt);
            micStream = new AudioInputStream(new PipedInputStream(micPipe), micFmt, AudioSystem.NOT_SPECIFIED);
            micStream = AudioSystem.getAudioInputStream(AudioCall.COMMON_NETWORK_FORMAT, micStream);
            mic.start();

            // Setup speaker pipeline
            speaker.open(speakerFmt);
            spkrStream = new AudioInputStream(new PipedInputStream(spkrPipe), AudioCall.COMMON_NETWORK_FORMAT, AudioSystem.NOT_SPECIFIED);
            spkrStream = AudioSystem.getAudioInputStream(speakerFmt, spkrStream);
            speaker.start();

            // Prime the queue with ~3 packets of silence before starting
            byte[] silence = new byte[AudioCall.getBufferSize(speakerFmt, 50)];
            for (int i = 0; i < 3; i++) {
                jitterQueue.offer(silence);
            }
        } catch (LineUnavailableException e) {
            System.out.println("Audio line unavailable: try setting a different microphone and speaker" + e.getMessage());
        } catch (IOException e) {
            System.out.println("Error initializing audio streams: " + e.getMessage());
        }

        running = true;
        
        // Start receive/broadcast handler thread
        this.receiveThread = new Thread(this::handleReceive);
        this.receiveThread.setDaemon(true);
        this.receiveThread.start();

        this.broadcastThread = new Thread(this::handleBroadcast);
        this.broadcastThread.setDaemon(true);
        this.broadcastThread.start();

        this.consumeThread = new Thread(this::consumeAudio);
        this.consumeThread.setDaemon(true);
        this.consumeThread.start();
    }
    @Override
    public void stop() {
        running = false;
        try {
            synchronized (mic) {
                mic.stop();
                mic.close();
            }
            synchronized (speaker) {
                speaker.stop();
                speaker.close();
            }
            docket.close();
            micPipe.close();
            spkrPipe.close();
        } catch (Exception e) {
            System.out.println("Error stopping: " + e.getMessage());
        }
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
            this.consumeThread.join();
        } catch (InterruptedException e) {
            this.consumeThread.interrupt();
        }
    }

    public void close() {
        stop();
    }
    
    private void handleReceive() {
        while (running) {
            receive();
        }
    }




    private void handleBroadcast() {
        while (running) {
            broadcast();
        }
    }

    private void receive() {
        byte[] data = new byte[AudioCall.NETWORK_BUFFER_SIZE];
        DatagramPacket request = new DatagramPacket(data, data.length);
        try {
            docket.receive(request);
        } catch (IOException e) {
            // Timeout is normal
            return;
        }

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

        byte[] speakerData = convertSpkrStream(request.getData(), AudioCall.COMMON_NETWORK_FORMAT);
        jitterQueue.offer(speakerData); // drops if full — intentional
    }

    private void broadcast() {
        // Read raw mic data - blocks until available
        byte[] data;
        int bytesRead = 0;
        synchronized (mic) {
            if (mic == null || !mic.isOpen()) {
                return; //Microphone not ready
            }
            int MIC_BUFFER_SIZE = AudioCall.getBufferSize(micFmt, 50);
            data = new byte[MIC_BUFFER_SIZE];
            bytesRead = mic.read(data, 0, MIC_BUFFER_SIZE); // blocks until full
        }

        if (bytesRead <= 0 || clients.isEmpty()) {
            return;
        }

        // Convert to network format
        byte[] networkData = convertMicStream(Arrays.copyOf(data, bytesRead), AudioCall.COMMON_NETWORK_FORMAT);

        if (networkData.length > 0) {
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
                        DatagramPacket packet = new DatagramPacket(networkData, networkData.length, entry.getKey());
                        try {
                            docket.send(packet);
                        } catch (IOException e) {
                            System.out.println("Error sending packet to " + entry.getKey() + ": " + e.getMessage());
                        }
                    }
                }
            }
        }
    }
    @Override
    public void setMic(Mixer.Info mixerInfo) {
        synchronized (mic) {
            micFmt = AudioCall.getBestFormat(mixerInfo);
            try {
                TargetDataLine newMic = AudioCall.findMic(mixerInfo);
                if (newMic != null) {
                    if (mic.isOpen()) {
                        mic.stop();
                        mic.close();
                    }
                    mic = newMic;
                    mic.open(micFmt);
                    try {
                        micPipe.close();
                        micPipe = new PipedOutputStream();
                        micStream = new AudioInputStream(new PipedInputStream(micPipe), micFmt, AudioSystem.NOT_SPECIFIED);
                        micStream = AudioSystem.getAudioInputStream(AudioCall.COMMON_NETWORK_FORMAT, micStream);
                    } catch (IOException e) {
                        System.out.println("Error setting up microphone pipeline: " + e.getMessage());
                    }
                    mic.start();
                    System.out.println("Microphone set to: " + mixerInfo.getName());
                } else {
                    System.out.println("Failed to set microphone: " + mixerInfo.getName());
                }
            } catch (LineUnavailableException e) {
                System.out.println("Error setting microphone: " + e.getMessage());
            }
        }
    }
    @Override
    public void setSpeaker(Mixer.Info mixerInfo) {
        synchronized (speaker) {
            speakerFmt = AudioCall.getBestFormat(mixerInfo);
            try {
                SourceDataLine newSpeaker = AudioCall.findSpeaker(mixerInfo);
                if (newSpeaker != null) {
                    if (speaker.isOpen()) {
                        speaker.stop();
                        speaker.close();
                    }
                    speaker = newSpeaker;
                    speaker.open(speakerFmt);
                    try {
                        spkrPipe.close();
                        spkrPipe = new PipedOutputStream();
                        spkrStream = new AudioInputStream(new PipedInputStream(spkrPipe), AudioCall.COMMON_NETWORK_FORMAT, AudioSystem.NOT_SPECIFIED);
                        spkrStream = AudioSystem.getAudioInputStream(speakerFmt, spkrStream);
                    } catch (IOException e) {
                        System.out.println("Error setting up speaker pipeline: " + e.getMessage());
                    }
                    speaker.start();
                    
                    System.out.println("Speaker set to: " + mixerInfo.getName());
                } else {
                    System.out.println("Failed to set speaker: " + mixerInfo.getName());
                }
            } catch (LineUnavailableException e) {
                System.out.println("Error setting speaker: " + e.getMessage());
            }  
        }
    }

    private void consumeAudio() {
        int silenceSize = AudioCall.getBufferSize(speakerFmt, 50);
        while (running) {
            try {
                byte[] speakerData = jitterQueue.poll(50, TimeUnit.MILLISECONDS);
                if (speakerData == null) {
                    // Queue ran dry — write silence to prevent underrun
                    speakerData = new byte[silenceSize];
                }
                synchronized (speaker) {
                    if (speaker != null && speaker.isOpen()) {
                        speaker.write(speakerData, 0, speakerData.length);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
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

     @Override
    public byte[] convertMicStream(byte[] input, AudioFormat tgtFmt) {
        if (micFmt.equals(tgtFmt)) return input;
        try {
            micPipe.write(input);          // feed raw mic bytes in
            micPipe.flush();
           // Expected output size after conversion
            float ratio = tgtFmt.getFrameRate() / micFmt.getFrameRate() 
                        * tgtFmt.getFrameSize() / micFmt.getFrameSize();
            int expectedBytes = (int)(input.length * ratio);
            expectedBytes -= expectedBytes % tgtFmt.getFrameSize(); // frame-align

            byte[] output = new byte[expectedBytes];
            int read = micStream.read(output, 0, expectedBytes);
            return read > 0 ? Arrays.copyOf(output, read) : new byte[expectedBytes];
        } catch (IOException e) {
            System.out.println("Mic conversion failed: " + e.getMessage());
            int tgtFrames = input.length / micFmt.getFrameSize();
            return new byte[tgtFrames * tgtFmt.getFrameSize()];
        }
    }
    
    @Override
    public byte[] convertSpkrStream(byte[] input, AudioFormat tgtFmt) {
        if (speakerFmt.equals(tgtFmt)) return input;

        try {
            spkrPipe.write(input);  // Write network format bytes into conversion pipeline
            spkrPipe.flush();
            
            // Calculate expected output size after conversion
            float ratio = speakerFmt.getFrameRate() / tgtFmt.getFrameRate() 
                        * speakerFmt.getFrameSize() / tgtFmt.getFrameSize();
            int expectedBytes = (int)(input.length * ratio);
            expectedBytes -= expectedBytes % speakerFmt.getFrameSize(); // frame-align

            byte[] output = new byte[expectedBytes];
            int read = spkrStream.read(output, 0, expectedBytes);
            return read > 0 ? Arrays.copyOf(output, read) : new byte[expectedBytes];

        } catch (IOException e) {
            System.out.println("Speaker conversion failed: " + e.getMessage());
            // Return silence on conversion failure
            int tgtFrames = input.length / tgtFmt.getFrameSize();
            return new byte[tgtFrames * speakerFmt.getFrameSize()];
        }
    }

    public InetAddress getAddress() {
        return ADDRESS;
    }

    public int getPort() {
        return PORT;
    }

}
