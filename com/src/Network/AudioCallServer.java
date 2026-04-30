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
import java.util.function.Consumer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import javafx.scene.chart.PieChart.Data;

public class AudioCallServer implements AudioCall, AutoCloseable {
    AudioFormat micFmt;
    AudioFormat speakerFmt;

    AudioInputStream micStream;
    AudioInputStream spkrStream;

    PipedOutputStream micPipe;
    PipedOutputStream spkrPipe;

    TargetDataLine mic;
    SourceDataLine speaker;
    
    private volatile boolean running = false;

    //Consumer
    Thread consumeThread;
    Thread supplyThread;

    Consumer<byte[]> onAudioSupply;

    BlockingQueue<byte[]> jitterQueue = new LinkedBlockingQueue<>(20); // ~1 second at 50ms packets

    public AudioCallServer() {
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


        this.consumeThread = new Thread(this::consumeAudio);
        this.consumeThread.setDaemon(true);
        this.consumeThread.start();
    }
    @Override
    public void stop() {
        running = false;
        this.consumeThread.interrupt();
        try {
            this.consumeThread.join();
        } catch (InterruptedException e) {
            this.consumeThread.interrupt();
        }
        if (supplyThread != null) {
            this.supplyThread.interrupt();
            try {
                this.supplyThread.join();
            } catch (InterruptedException e) {
                this.supplyThread.interrupt();
            }
        }

        try {
            synchronized (mic) {
                mic.stop();
                mic.close();
            }
            synchronized (speaker) {
                speaker.stop();
                speaker.close();
            }
            micPipe.close();
            spkrPipe.close();
        } catch (Exception e) {
            System.out.println("Error stopping: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        stop();
    }
    
    @Override
    public void offer(byte[] data) {
        byte[] speakerData = convertSpkrStream(data, AudioCall.COMMON_NETWORK_FORMAT);
        jitterQueue.offer(speakerData); // drops if full — intentional
    }

    private void audioSupplier() {
        while (running) {
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

            if (bytesRead <= 0) {
                continue;
            }

            // Convert to network format
            byte[] networkData = convertMicStream(Arrays.copyOf(data, bytesRead), AudioCall.COMMON_NETWORK_FORMAT);
            if (onAudioSupply != null) {
                onAudioSupply.accept(networkData);
            }
        }
    }

    public void setOnAudioSupply(Consumer<byte[]> onAudioSupply) {
        this.onAudioSupply = onAudioSupply;
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

        if (supplyThread.isAlive()) {
            supplyThread.interrupt();
            try {
                supplyThread.join();
            } catch (InterruptedException e) {
                supplyThread.interrupt();
            }
        }
        supplyThread = new Thread(this::audioSupplier);
        supplyThread.setDaemon(true);
        supplyThread.start();
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
        
        while (running) {
            try {
                int silenceSize = AudioCall.getBufferSize(speakerFmt, 50);
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
