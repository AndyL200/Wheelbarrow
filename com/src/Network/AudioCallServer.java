package Network;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;


public class AudioCallServer implements AudioCall, AutoCloseable {
    /*
        @author - Andrew LeClair
        5/15/2026 ~volatile: reserved for variables accessed by multiple threads AND subject to change
            micFmt and mic are changed in setMic by the main thread and are accessed by the supplyThread
            speakerFmt and speaker are changed in setSpeaker by the main thread and are accessed by the consumeThread
            onAudioSupply is set by the main thread and read by the supplyThread
            supplyThread and consumeThread can be stopped by a different thread than the one that started them
            running is read by all threads
            jitterQueue is added to by the network thread and read by the consumeThread
    */

    //[STATIC]
    private static final boolean DEBUG_MODE = Preferences.userRoot().node("wheelbarrow/debug").getBoolean("mode", false);
    private static final boolean VERBOSE = Preferences.userRoot().node("wheelbarrow/debug").getBoolean("verbose", false);
    private static final boolean CONCURRENCY = Preferences.userRoot().node("wheelbarrow/debug").getBoolean("concurrency", false);

    //[DATALINES]
    private volatile AudioFormat micFmt;
    private volatile AudioFormat speakerFmt;
    private volatile TargetDataLine mic;
    private volatile SourceDataLine speaker;
    
    //[RUN]
    public volatile boolean running = false;

    //[THREAD]
    private volatile Thread supplyThread;
    private volatile Thread consumeThread;

    //[LOCK]
    ReentrantLock micLock = new ReentrantLock();
    ReentrantLock spkrLock = new ReentrantLock();
    
    //[CALLBACK]
    private volatile Consumer<byte[]> onAudioSupply;

    //[JITTER]
    private AtomicReference<BlockingQueue<byte[]>> jitterQueue = new AtomicReference<>(new LinkedBlockingQueue<>(20)); // ~1 second at 50ms packets

    public AudioCallServer() {
        // Routing handled by CallServer
    }

    @Override
    public void start() {
        if (running) return; // Prevent multiple starts  
        running = true;

        Thread oldSupplyThread = null;
        Thread oldConsumeThread = null;
        boolean acquired = false;
        boolean micReady = false;
        boolean spkrReady = false;
        try {
            micLock.lock();
            acquired = true;
            oldSupplyThread = supplyThread;
            micFmt = AudioCall.getBestFormat(null, TargetDataLine.class); // Use default mixer
            mic = AudioSystem.getTargetDataLine(micFmt);
            mic.open(micFmt);
            mic.start();
            micReady = true;
        } catch (LineUnavailableException lue) {
            //[LUE]
            System.out.println("Error initializing audio devices: " + lue.getMessage());
        } finally {
            if (acquired) {
                micLock.unlock();
            }
            // Stop thread outside the lock
            if (oldSupplyThread != null) {
                stopThread(oldSupplyThread);     
            } 
            if (micReady) {
                this.supplyThread = new Thread(this::audioSupplier);
                this.supplyThread.setDaemon(true);
                this.supplyThread.start();
            }
        }
        acquired = false;
        try {
            spkrLock.lock();
            if (DEBUG_MODE && CONCURRENCY) {
                System.out.println("[AudioCallServer.start] spkrLock acquired");
            }
            acquired = true;
            oldConsumeThread = consumeThread;
            speakerFmt = AudioCall.getBestFormat(null, SourceDataLine.class); // Use default mixer
            speaker = AudioSystem.getSourceDataLine(speakerFmt);
            speaker.open(speakerFmt);
            speaker.start();

            // Prime the queue with ~3 packets of silence before starting
            byte[] silence = new byte[AudioCall.getBufferSize(speakerFmt, getMillisForSpeakerBuffer())];
            for (int i = 0; i < 3; i++) {
                jitterQueue.get().offer(silence);
            }   
            spkrReady = true;
        } catch (LineUnavailableException lue) {
            System.out.println("Error initializing speaker: " + lue.getMessage());
        } finally {
            if (acquired) {
                spkrLock.unlock();
            }
            if (oldConsumeThread != null) {
                stopThread(oldConsumeThread);
            }
            if (spkrReady) {
                this.consumeThread = new Thread(this::consumeAudio);
                this.consumeThread.setDaemon(true);
                this.consumeThread.start();
            }
        }
    }
    @Override
    public void stop() {
        running = false;
        stopThread(this.consumeThread);
        stopThread(this.supplyThread);
        boolean acquired = false;

        //close mic
        micLock.lock();
        acquired = true;
        if (DEBUG_MODE && CONCURRENCY) {
            System.out.println("[AudioCallServer.stop] micLock acquired");
        }
        if (mic != null) {
            mic.stop();
            mic.close();
        }
        if (acquired) {
            micLock.unlock();
            if (DEBUG_MODE && CONCURRENCY) {
                System.out.println("[AudioCallServer.stop] releasing micLock");
            }
        }

        acquired = false;
        spkrLock.lock();
        acquired = true;
        if (DEBUG_MODE && CONCURRENCY) {
            System.out.println("[AudioCallServer.stop] spkrLock acquired");
        }
        if (speaker != null) {
            speaker.stop();
            speaker.close();
        }
        if (acquired) {
            spkrLock.unlock();
            if (DEBUG_MODE && CONCURRENCY) {
                System.out.println("[AudioCallServer.stop] releasing spkrLock");
            }
        }
    }

    @Override
    public void close() {
        stop();
    }

    //helper method
    private void stopThread(Thread t) {
        if (t == null || !t.isAlive()) return;
            t.interrupt();
        try {
            t.join();
        } catch (InterruptedException e) {
            System.out.println("Interrupted while waiting for thread to stop: " + e.getMessage());
        }
    }
    
    @Override
    public void offer(byte[] data) {
        if (data != null) {
            jitterQueue.get().offer(data); // drops if full — intentional
        }
    }

    private void audioSupplier() {
        while (running) {
            byte[] data;
            int bytesRead = 0;
            boolean acquired = false;
            byte[] networkData = null;
            try {
                micLock.lock();
                acquired = true;
                if (DEBUG_MODE && CONCURRENCY) {
                    System.out.println("[AudioCallServer.audioSupplier] micLock acquired");
                }
                if (mic == null || !mic.isOpen()) {
                    System.out.println("[AudioCallServer.audioSupplier] Microphone not ready, skipping audio supply");
                    return;
                }
                // Read enough mic data to yield NETWORK_BUFFER_SIZE after conversion
                int msToRead = getMillisForNetworkBuffer();
                int MIC_BUFFER_SIZE = AudioCall.getBufferSize(micFmt, msToRead);
                data = new byte[MIC_BUFFER_SIZE];
                bytesRead = mic.read(data, 0, MIC_BUFFER_SIZE);
                if (bytesRead > 0) {
                    networkData = convertMicStream(Arrays.copyOf(data, bytesRead), micFmt);
                }
            } finally {
                if (acquired) {
                    if (DEBUG_MODE && CONCURRENCY) {
                        System.out.println("[AudioCallServer.audioSupplier] Releasing micLock");
                    }
                    micLock.unlock();
                }
            }

            if (bytesRead <= 0) {
                continue;
            }

            
            if (onAudioSupply != null && networkData != null) {
                onAudioSupply.accept(networkData);
            }
        }
    }

    public void setOnAudioSupply(Consumer<byte[]> onAudioSupply) {
        this.onAudioSupply = onAudioSupply;
    }
    @Override
    public void setMic(Mixer.Info mixerInfo) {
        if (!running) {
            System.out.println("Cannot set microphone - audio call not running");
            return;
        }
        if (DEBUG_MODE) {
            System.out.println("[AudioCallServer] setMic() called with " + (mixerInfo == null ? "default" : mixerInfo.getName()));
        }
        Thread oldSupplyThread = null;
        boolean acquired = false;
        boolean micReady = false;
        try {
            if (DEBUG_MODE && CONCURRENCY) {
                System.out.println("[AudioCallServer] Acquiring micLock...");
            }
            micLock.lock();
            if (DEBUG_MODE && CONCURRENCY) {
                System.out.println("[AudioCallServer] Got micLock");
            }
            acquired = true;
            oldSupplyThread = supplyThread;
            micFmt = AudioCall.getBestFormat(mixerInfo, TargetDataLine.class);
            try {
                if (DEBUG_MODE) {
                    System.out.println("[AudioCallServer] Finding microphone device...");
                }
                TargetDataLine newMic = AudioCall.findMic(mixerInfo);
                if (newMic != null) {
                    if (DEBUG_MODE) {
                        System.out.println("[AudioCallServer] Found microphone, checking if old mic open...");
                    }
                    if (mic != null && mic.isOpen()) {
                        if (DEBUG_MODE) {
                            System.out.println("[AudioCallServer] Closing old microphone");
                        }
                        mic.stop();
                        mic.close();
                    }
                    mic = newMic;
                    if (DEBUG_MODE) {
                        System.out.println("[AudioCallServer] Opening new microphone...");
                    }
                    mic.open(micFmt);
                    if (DEBUG_MODE) {
                        System.out.println("[AudioCallServer] Starting microphone...");
                    }
                    mic.start();
                    micReady = true;
                    if (mixerInfo != null) {
                        System.out.println("Microphone set to: " + mixerInfo.getName());
                    } else {
                        System.out.println("Microphone set to: " + mic.getLineInfo().toString());
                    }
                } else {
                    if (DEBUG_MODE) {
                        System.out.println("[AudioCallServer] Failed to find microphone device");
                    }
                    if (mixerInfo != null) {
                        System.out.println("Failed to set microphone: " + mixerInfo.getName());
                    } else {
                        System.out.println("Failed to set microphone: " + mic.getLineInfo().toString());
                    }
                }
            } catch (LineUnavailableException lue) {
                System.out.println("Error setting microphone: " + lue.getMessage());
                if (DEBUG_MODE) {
                    lue.printStackTrace();
                }
            }
        } finally {
            if (acquired) {
                if (DEBUG_MODE && CONCURRENCY) {
                    System.out.println("[AudioCallServer.setMic] Releasing micLock");
                }
                micLock.unlock();
            }
             if (oldSupplyThread != null) {
                 stopThread(oldSupplyThread);     
             } 
             if (micReady) {
                 this.supplyThread = new Thread(this::audioSupplier);
                 this.supplyThread.setDaemon(true);
                 this.supplyThread.start();
            }
        }
    }
    @Override
    public void setSpeaker(Mixer.Info mixerInfo) {
        if (!running) {
            System.out.println("Cannot set speaker - audio call not running");
            return;
        }
        if (DEBUG_MODE) {
            System.out.println("[AudioCallServer] setSpeaker() called with " + (mixerInfo == null ? "default" : mixerInfo.getName()));
        }
        Thread oldConsumeThread = null;
        boolean acquired = false;
        boolean spkrReady = false;
        try {
            if (DEBUG_MODE) {
                System.out.println("[AudioCallServer] Acquiring spkrLock...");
            }
            spkrLock.lock();
            if (DEBUG_MODE && CONCURRENCY) {
                System.out.println("[AudioCallServer.setSpeaker] spkrLock acquired");
            }
            if (DEBUG_MODE) {
                System.out.println("[AudioCallServer] Got spkrLock");
            }
            acquired = true;
            oldConsumeThread = consumeThread;
            speakerFmt = AudioCall.getBestFormat(mixerInfo, SourceDataLine.class);
            try {
                if (DEBUG_MODE) {
                    System.out.println("[AudioCallServer] Finding speaker device...");
                }
                SourceDataLine newSpeaker = AudioCall.findSpeaker(mixerInfo);
                if (newSpeaker != null) {
                    if (DEBUG_MODE) {
                        System.out.println("[AudioCallServer] Found speaker, checking if old speaker open...");
                    }
                    if (speaker != null && speaker.isOpen()) {
                        if (DEBUG_MODE) {
                            System.out.println("[AudioCallServer] Closing old speaker");
                        }
                        speaker.stop();
                        speaker.close();
                    }
                    speaker = newSpeaker;
                    if (DEBUG_MODE) {
                        System.out.println("[AudioCallServer] Opening new speaker...");
                    }
                    speaker.open(speakerFmt);
                    if (DEBUG_MODE) {
                        System.out.println("[AudioCallServer] Starting speaker...");
                    }
                    speaker.start();
                    spkrReady = true;
                    if (mixerInfo != null) {
                        System.out.println("Speaker set to: " + mixerInfo.getName());
                    } else {
                        System.out.println("Speaker set to: " + speaker.getLineInfo().toString());
                    }
                } else {
                    if (DEBUG_MODE) {
                        System.out.println("[AudioCallServer] Failed to find speaker device");
                    }
                    if (mixerInfo != null) {
                        System.out.println("Failed to set speaker: " + mixerInfo.getName());
                    } else {
                        System.out.println("Failed to set speaker");
                    }
                }
            } catch (LineUnavailableException lue) {
                System.out.println("Error setting speaker: " + lue.getMessage());
                if (DEBUG_MODE) {
                    lue.printStackTrace();
                }
            }
        } finally {
            if (acquired) {
                if (DEBUG_MODE && CONCURRENCY) {
                    System.out.println("[AudioCallServer.setSpeaker] Releasing spkrLock");
                }
                spkrLock.unlock();
            }
            if (oldConsumeThread != null) {
                stopThread(oldConsumeThread);
            }
            if (spkrReady) {
                this.consumeThread = new Thread(this::consumeAudio);
                this.consumeThread.setDaemon(true);
                this.consumeThread.start();
            }
        }
        
    }

    private void consumeAudio() { 
        while (running) {
            byte[] data = null;
            try {
                data = jitterQueue.get().poll(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException i) {
                //[Interrupt]
                if (DEBUG_MODE && CONCURRENCY) {
                    System.out.println("[AudioCallServer.consumeAudio] Interrupted while polling jitterQueue");
                }
                return;
            }
            if (data == null) {
                continue;
            }
            
            byte[] speakerData = null;
            boolean acquired = false;
            try {
                spkrLock.lock();
                if (DEBUG_MODE && CONCURRENCY) {
                    System.out.println("[AudioCallServer.consumeAudio] spkrLock acquired");
                }
                acquired = true;

                if (speaker == null || !speaker.isOpen()) {
                    System.out.println("[AudioCallServer.consumeAudio] Speaker not ready, skipping audio consume");
                    return;
                }
                speakerData = convertSpkrStream(data, speakerFmt);
                if (speakerData == null) {
                    // Queue ran dry — write silence to prevent underrun
                    int silenceSize = AudioCall.getBufferSize(speakerFmt, getMillisForSpeakerBuffer());
                    speakerData = new byte[silenceSize];
                }
                
            } finally {
                if (acquired) {
                    if (DEBUG_MODE && CONCURRENCY) {
                        System.out.println("[AudioCallServer.consumeAudio] Releasing spkrLock");
                    }
                    spkrLock.unlock();
                }

                try {
                    if (speakerData != null) {
                        speaker.write(speakerData, 0, speakerData.length);
                    }
                } catch (IllegalArgumentException iae) {
                    System.out.println("Speaker write failed (likely device closed): " + iae.getMessage());
                }
            }
        }
    }

    

    //Thread safe method
   //Convert FROM mic
    @Override
    public byte[] convertMicStream(byte[] input, AudioFormat mFmt) {

        if (mFmt.equals(AudioCall.COMMON_NETWORK_FORMAT)) {
           return input;
        }
        try {
            
            AudioInputStream rawStream = new AudioInputStream(
            new ByteArrayInputStream(input),
            mFmt,
            input.length / mFmt.getFrameSize()
            );
            AudioInputStream convertedStream = AudioSystem.getAudioInputStream(
                AudioCall.COMMON_NETWORK_FORMAT, rawStream
            );

            int expectedBytes = AudioCall.NETWORK_BUFFER_SIZE;

            byte[] output = new byte[expectedBytes];
            int read = convertedStream.read(output, 0, expectedBytes);

            return read > 0 ? Arrays.copyOf(output, read) : null;
        } catch (IOException e) {
            System.out.println("Mic conversion failed: " + e.getMessage());
            return null;
        }
    }
    
    //Method needs to be thread safe
    //Convert TO speaker 
    @Override
    public byte[] convertSpkrStream(byte[] input, AudioFormat sFmt) {
        if (sFmt.equals(AudioCall.COMMON_NETWORK_FORMAT)) {
            return input;
        }

        try {
            AudioInputStream rawStream = new AudioInputStream(
            new ByteArrayInputStream(input),
            AudioCall.COMMON_NETWORK_FORMAT,
            input.length / AudioCall.COMMON_NETWORK_FORMAT.getFrameSize()
            );
            AudioInputStream convertedStream = AudioSystem.getAudioInputStream(
                sFmt, rawStream
            );
            int expectedBytes = AudioCall.getBufferSize(sFmt, getMillisForSpeakerBuffer());

            byte[] output = new byte[expectedBytes];
            int read = convertedStream.read(output, 0, expectedBytes);

            return read > 0 ? Arrays.copyOf(output, read) : null;
        } catch (IOException e) {
            System.out.println("Speaker conversion failed: " + e.getMessage());
            // Return silence on conversion failure
            return null;
        }
    }

    private int getMillisForNetworkBuffer() {
        int networkFrames = AudioCall.NETWORK_BUFFER_SIZE / AudioCall.COMMON_NETWORK_FORMAT.getFrameSize();
        float ms = networkFrames / AudioCall.COMMON_NETWORK_FORMAT.getFrameRate() * 1000f;
        return (int) ms;
    }

    private int getMillisForSpeakerBuffer() {
        int networkFrames = AudioCall.NETWORK_BUFFER_SIZE / AudioCall.COMMON_NETWORK_FORMAT.getFrameSize();
        float ms = networkFrames / AudioCall.COMMON_NETWORK_FORMAT.getFrameRate() * 1000f;
        return (int) ms;
    }
}
