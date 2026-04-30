package Network;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

public class AudioCallClient implements AudioCall, AutoCloseable {
    AudioFormat micFmt;
    AudioFormat speakerFmt;

    AudioInputStream micStream;
    AudioInputStream spkrStream;

    PipedOutputStream micPipe;
    PipedOutputStream spkrPipe;

    TargetDataLine mic;
    SourceDataLine speaker;
    //UDP
    DatagramSocket socket;
    InetAddress serverAddress;
    int serverPort;
    volatile boolean running = false;


    //Threads
    Thread sendThread;
    Thread receiveThread;
    Thread consumeThread;

    BlockingQueue<byte[]> jitterQueue = new LinkedBlockingQueue<>(20); // ~1 second at 50ms packets

    public AudioCallClient(String serverHost, int serverPort) {
        
        try {
            this.serverAddress = InetAddress.getByName(serverHost);
            this.serverPort = serverPort;
            this.socket = new DatagramSocket(50000);
            this.socket.setSoTimeout(2000); // 2 second timeout for receive
        } catch (SocketException s1) {
            try {
                System.out.println("Could not create socket: " + s1.getMessage());
                this.socket = new DatagramSocket(0);
                this.socket.setSoTimeout(2000);
            } catch (SocketException s2) {
                System.out.println("Error initializing client: " + s2.getMessage());
            }
        } catch (Exception e) {
            System.out.println("Error initializing client: " + e.getMessage());
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
            mic.open(micFmt);
            //experiment with the latency
            //Write micFmt to COMMON_NETWORK_FORMAT
            micStream = new AudioInputStream(new PipedInputStream(micPipe), micFmt, AudioSystem.NOT_SPECIFIED);
            micStream = AudioSystem.getAudioInputStream(AudioCall.COMMON_NETWORK_FORMAT, micStream);
            mic.start();

            speaker.open(speakerFmt);
            //Write COMMON_NETWORK_FORMAT to speakerFmt
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
        
        // Start send thread (sends mic data to server)
        this.sendThread = new Thread(this::handleSend);
        this.sendThread.setDaemon(true);
        this.sendThread.start();

        // Start receive thread (receives audio from server and plays it)
        this.receiveThread = new Thread(this::handleReceive);
        this.receiveThread.setDaemon(true);
        this.receiveThread.start();

        this.consumeThread = new Thread(this::consumeAudio);
        this.consumeThread.setDaemon(true);
        this.consumeThread.start();
    }
    @Override
    public void stop() {
        running = false;
        mic.stop();
        mic.close();
        speaker.stop();
        speaker.close();
        socket.close();
        try {
            this.sendThread.join();
        } catch (InterruptedException e) {
            this.sendThread.interrupt();
        }
        try {
            this.receiveThread.join();
        } catch (InterruptedException e) {
            this.receiveThread.interrupt();
        }
        try {
            this.consumeThread.join();
        } catch (InterruptedException e) {
            this.consumeThread.interrupt();
        }
        
    }

    @Override
    public void close() {
        stop();
    }

    private void handleSend() {
        while (running) {
            sendAudio();
        }
    }

    private void handleReceive() {
        while (running) {
            //should this throw an error?
            receiveAudio();
        }   
    }

    private void sendAudio() {
        int bytesRead = 0;
        byte[] data;
        synchronized (mic) {
            if (mic == null || !mic.isOpen()) {
                return; //Microphone not ready
            }
            int MIC_BUFFER_SIZE = AudioCall.getBufferSize(micFmt, 50);
            data = new byte[MIC_BUFFER_SIZE];
            bytesRead = mic.read(data, 0, MIC_BUFFER_SIZE); // blocks until full
        }

        if (bytesRead <= 0 || data.length == 0) {
            return;
        }

        byte[] micData = convertMicStream(data, AudioCall.COMMON_NETWORK_FORMAT);
        DatagramPacket packet = new DatagramPacket(micData, micData.length, serverAddress, serverPort);
        try {
            socket.send(packet);
        } catch (IOException e) {
            System.out.println("Error sending packet to server: " + e.getMessage());
        }
    }

    private void receiveAudio() {
        byte[] data = new byte[AudioCall.NETWORK_BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(data, data.length);
        try {
            socket.receive(packet);
        } catch (IOException e) {
            return; // timeout, just retry
        }
        byte[] speakerData = convertSpkrStream(packet.getData(), AudioCall.COMMON_NETWORK_FORMAT);
        jitterQueue.offer(speakerData); // drops if full — intentional
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

}
