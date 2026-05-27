package Network;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
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

public class AudioCallClient implements AudioCall, AutoCloseable {

    /*
        @author - Andrew LeClair
        5/15/2026 ~volatile: reserved for variables accessed by multiple threads AND subject to change
            micFmt and mic are changed in setMic by the main thread and are accessed by the supplyThread
            speakerFmt and speaker are changed in setSpeaker by the main thread and are accessed by the consumeThread
            onAudioSupply is set by the main thread and read by the supplyThread
            supplyThread and consumeThread can be stopped by a different thread than the one that started them
            running is read by all threads
        ~Atomic Reference: used for jitterQueue    
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
    private volatile ReentrantLock micLock = new ReentrantLock();
    private volatile ReentrantLock spkrLock = new ReentrantLock();

    //[WAITING]
    private volatile Condition micWait = micLock.newCondition();
    private volatile Condition spkrWait = spkrLock.newCondition();
    
    //[CALLBACK]
    private volatile Consumer<byte[]> onAudioSupply;

    //[JITTER]
    private AtomicReference<BlockingQueue<byte[]>> jitterQueue = new AtomicReference<>(new LinkedBlockingQueue<>(100));

    public AudioCallClient() {
        // Routing handled by CallClient
    }
    @Override
    public void start() {
        if (running) return; // Prevent multiple starts
        running = true;


        micLock.lock();
        try {
            micFmt = AudioCall.getBestFormat(null, TargetDataLine.class); // Use default mixer
            mic = AudioSystem.getTargetDataLine(micFmt);
            mic.open(micFmt);
            mic.start();
        } catch(LineUnavailableException lue) {
            //[LUE]
            System.out.println("[AudioCallClient.start] Error initializing microphone: " + lue.getMessage());
        } finally {
            micLock.unlock();
        }

        spkrLock.lock();
        try {
            speakerFmt = AudioCall.getBestFormat(null, SourceDataLine.class); // Use default mixer
            speaker = AudioSystem.getSourceDataLine(speakerFmt);
            speaker.open(speakerFmt);
            speaker.start();
            // Prime the queue with ~3 packets of silence before starting
            byte[] silence = new byte[AudioCall.getBufferSize(speakerFmt, getMillisForSpeakerBuffer())];
            for (int i = 0; i < 3; i++) {
                jitterQueue.get().offer(silence);
            }   
        } catch (LineUnavailableException lue) {
            System.out.println("Error initializing speaker: " + lue.getMessage());
        } finally {
            spkrLock.unlock();
        }

        this.supplyThread = new Thread(this::supplyAudio);
        this.supplyThread.setDaemon(true);
        this.supplyThread.start();

        this.consumeThread = new Thread(this::consumeAudio);
        this.consumeThread.setDaemon(true);
        this.consumeThread.start();
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
    public void stop() {
        running = false;
        stopThread(this.supplyThread);
        stopThread(this.consumeThread);

        //close mic
        micLock.lock();
        try {
            if (mic != null) {
                mic.stop();
                mic.close();
            }
        } finally {
            micLock.unlock();
        }

        spkrLock.lock();
        try {
            if (speaker != null) {
                speaker.stop();
                speaker.close();
            }
        } finally {
            spkrLock.unlock();
        }
    }

    @Override
    public void close() {
        stop();
    }

    @Override
    public void offer(byte[] data) {
        if (data != null) {
            jitterQueue.get().offer(data); // drops if full — intentional
        }
    }

    private void supplyAudio() throws NullPointerException {
        while (running) {
            micLock.lock();
            try {
                byte[] data;
                int bytesRead = 0;
                byte[] networkData = null;

                
                int MIC_BUFFER_SIZE = AudioCall.NETWORK_BUFFER_SIZE;
                data = new byte[MIC_BUFFER_SIZE];
                if (mic == null || !mic.isOpen()) {
                    System.out.println("[AudioCallClient.supplyAudio] Microphone not ready, skipping audio supply");
                    try {
                        micWait.await(); // Wait for mic to be set or timeout after 100ms
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                        }
                }
                if (DEBUG_MODE) {
                    System.out.println("[AudioCallClient.supplyAudio] Reading from microphone with buffer size: " + MIC_BUFFER_SIZE);
                }
                bytesRead = mic.read(data, 0, MIC_BUFFER_SIZE); //should be the bottleneck
                if (bytesRead > 0) {
                    networkData = convertMicStream(Arrays.copyOf(data, bytesRead), micFmt);
                    if (onAudioSupply != null && networkData != null) {
                        onAudioSupply.accept(networkData);
                    }
                }
            } finally {
                micLock.unlock();
            }
        }
    }

    public void setOnAudioSupply(Consumer<byte[]> onAudioSupply) {
        this.onAudioSupply = onAudioSupply;
    }

    private void consumeAudio() { 
        while (running) {
            try {
                byte[] data = jitterQueue.get().take();
                processAudio(data); 
            } catch (InterruptedException i) {
                //[Interrupt]
                if (DEBUG_MODE && CONCURRENCY) {
                    System.out.println("[AudioCallClient.consumeAudio] Interrupted while polling jitterQueue");
                }
                return;
            } catch (NullPointerException npe) {
                //[NPE]
                if (DEBUG_MODE) {
                    System.out.println("[AudioCallClient.consumeAudio] Null pointer exception: " + npe.getMessage());
                }
            }
        }
    }
    private void processAudio(byte[] data) {
            spkrLock.lock();
            try {
                if (speaker == null || !speaker.isOpen()) {
                    System.out.println("[AudioCallClient.consumeAudio] Speaker not ready, skipping audio consume");
                    try {
                        spkrWait.await(); // Wait for speaker to be set or timeout after 100ms
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                byte[] speakerData = convertSpkrStream(data, speakerFmt);
                if (speakerData == null) {
                    // Queue ran dry — write silence to prevent underrun
                    int silenceSize = AudioCall.getBufferSize(speakerFmt, getMillisForSpeakerBuffer());
                    speakerData = new byte[silenceSize];
                }
                speaker.write(speakerData, 0, speakerData.length);
            } finally {
                spkrLock.unlock();
            }
    }
    @Override
    public void setMic(Mixer.Info mixerInfo) {
        //dispatch a thread to keep this off the UI thread
        Thread t = new Thread(() -> micChange(mixerInfo));
        t.start();
    }
    private void micChange(Mixer.Info mixerInfo) {
        if (!running) {
            System.out.println("Cannot set microphone - audio call not running");
            return;
        }
        if (DEBUG_MODE) {
            System.out.println("[AudioCallClient] setMic() called with " + (mixerInfo == null ? "default" : mixerInfo.getName()));
        }
        try {
            if (DEBUG_MODE && CONCURRENCY) {
                System.out.println("[AudioCallClient] Acquiring micLock...");
            }
            micLock.lock();
            if (DEBUG_MODE && CONCURRENCY) {
                System.out.println("[AudioCallClient] Got micLock");
            }
            micFmt = AudioCall.getBestFormat(mixerInfo, TargetDataLine.class);
            try {
                if (DEBUG_MODE) {
                    System.out.println("[AudioCallClient] Finding microphone device...");
                }
                TargetDataLine newMic = AudioCall.findMic(mixerInfo);
                if (newMic != null) {
                    if (DEBUG_MODE) {
                        System.out.println("[AudioCallClient] Found microphone, checking if old mic open...");
                    }
                    if (mic != null && mic.isOpen()) {
                        if (DEBUG_MODE) {
                            System.out.println("[AudioCallClient] Closing old microphone");
                        }
                        mic.stop();
                        mic.close();
                    }
                    mic = newMic;
                    if (DEBUG_MODE) {
                        System.out.println("[AudioCallClient] Opening new microphone...");
                    }
                    mic.open(micFmt);
                    if (DEBUG_MODE) {
                        System.out.println("[AudioCallClient] Starting microphone...");
                    }
                    mic.start();
                    if (mixerInfo != null) {
                        System.out.println("Microphone set to: " + mixerInfo.getName());
                    } else {
                        System.out.println("Microphone set to: " + (mic != null ? mic.getLineInfo().toString() : "null"));
                    }
                } else {
                    if (DEBUG_MODE) {
                        System.out.println("[AudioCallClient] Failed to find microphone device");
                    }
                    if (mixerInfo != null) {
                        System.out.println("Failed to set microphone: " + mixerInfo.getName());
                    } else {
                        System.out.println("Failed to set microphone: " + (mic != null ? mic.getLineInfo().toString() : "null"));
                    }
                }
            } catch (LineUnavailableException lue) {
                System.out.println("Error setting microphone: " + lue.getMessage());
                if (DEBUG_MODE) {
                    lue.printStackTrace();
                }
            }
        } finally {     
                if (DEBUG_MODE && CONCURRENCY) {
                    System.out.println("[AudioCallClient.setMic] Releasing micLock");
                }
                micLock.unlock();
                micWait.signalAll();
        }
    }

    @Override
    public void setSpeaker(Mixer.Info mixerInfo) {
        //dispatch a thread to keep this off the UI thread
        Thread t = new Thread(() -> speakerChange(mixerInfo));
        t.start();
    }
    private void speakerChange(Mixer.Info mixerInfo) {
        if (!running) {
            System.out.println("Cannot set speaker - audio call not running");
            return;
        }
        if (DEBUG_MODE) {
            System.out.println("[AudioCallClient] setSpeaker() called with " + (mixerInfo == null ? "default" : mixerInfo.getName()));
        }
        spkrLock.lock();
        try {
            speakerFmt = AudioCall.getBestFormat(mixerInfo, SourceDataLine.class);
                if (DEBUG_MODE) {
                    System.out.println("[AudioCallClient] Finding speaker device...");
                }
                SourceDataLine newSpeaker = AudioCall.findSpeaker(mixerInfo);
                if (newSpeaker != null) {
                    if (DEBUG_MODE) {
                        System.out.println("[AudioCallClient] Found speaker, checking if old speaker open...");
                    }
                    if (speaker != null && speaker.isOpen()) {
                        if (DEBUG_MODE) {
                            System.out.println("[AudioCallClient] Closing old speaker");
                        }
                        speaker.stop();
                        speaker.close();
                    }
                    speaker = newSpeaker;
                    if (DEBUG_MODE) {
                        System.out.println("[AudioCallClient] Opening new speaker...");
                    }
                    speaker.open(speakerFmt);
                    if (DEBUG_MODE) {
                        System.out.println("[AudioCallClient] Starting speaker...");
                    }
                    speaker.start();
                    
                    if (mixerInfo != null) {
                        System.out.println("Speaker set to: " + mixerInfo.getName());
                    } else {
                        System.out.println("Speaker set to: " + (speaker != null ? speaker.getLineInfo().toString() : "null"));
                    }
                } else {
                    if (DEBUG_MODE) {
                        System.out.println("[AudioCallClient] Failed to find speaker device");
                    }
                    if (mixerInfo != null) {
                        System.out.println("Failed to set speaker: " + mixerInfo.getName());
                    } else {
                        System.out.println("Failed to set speaker: " + (speaker != null ? speaker.getLineInfo().toString() : "null"));
                    }
                }
        } catch (LineUnavailableException lue) {
                System.out.println("Error setting speaker: " + lue.getMessage());
                if (DEBUG_MODE) {
                    lue.printStackTrace();
                }
        } finally {
                
                spkrLock.unlock();
                spkrWait.signalAll();
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

  
    private int getMillisForSpeakerBuffer() {
        // ms where COMMON_NETWORK_FORMAT output = exactly NETWORK_BUFFER_SIZE
        int networkFrames = AudioCall.NETWORK_BUFFER_SIZE / AudioCall.COMMON_NETWORK_FORMAT.getFrameSize();
        float ms = networkFrames / AudioCall.COMMON_NETWORK_FORMAT.getFrameRate() * 1000f;

        // Now how many speaker bytes represent that same duration?
        // getBufferSize(speakerFmt, ms) is used by the caller — we just return ms
        return (int) ms;
    }

}

