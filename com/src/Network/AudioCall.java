package Network;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

public interface AudioCall extends Call {
    //allow users to set the mic they want to use
    public static TargetDataLine findMic(Mixer.Info mixerInfo) {
        try {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            TargetDataLine line = (TargetDataLine) mixer.getLine(new Line.Info(TargetDataLine.class));
            return line;
        } catch (LineUnavailableException l) {
            System.out.println("Error setting microphone: " + l.getMessage());
            return null;
        }
    }

    public static SourceDataLine findSpeaker(Mixer.Info mixerInfo) {
        try {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            SourceDataLine line = (SourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));
            return line;
        } catch (LineUnavailableException l) {
            System.out.println("Error setting speaker: " + l.getMessage());
            return null;
        }
    }

    public static List<Mixer.Info> getAvailableMics() {
        List<Mixer.Info> result = new ArrayList<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            Line.Info lineInfo = new Line.Info(TargetDataLine.class);
            if (mixer.isLineSupported(lineInfo)) {
                result.add(info);
            }
        }
        return result;
    }

    public static List<Mixer.Info> getAvailableSpeakers() {
        List<Mixer.Info> result = new ArrayList<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            Line.Info lineInfo = new Line.Info(SourceDataLine.class);
            if (mixer.isLineSupported(lineInfo)) {
                result.add(info);
            }
        }
        return result;
    }

    public static AudioFormat getBestFormat(Mixer.Info mixerInfo) {
        if (mixerInfo == null) {
                try {
                for (Mixer.Info info : AudioSystem.getMixerInfo()) {
                    Mixer mixer = AudioSystem.getMixer(info);
                    if (mixer.isLineSupported(new DataLine.Info(TargetDataLine.class, null))) {
                        return getBestFormat(info); // reuse your probing logic
                    }
                }
            } catch (Exception e) { }
                return new AudioFormat(44100f, 16, 2, true, false);
        }
        Mixer mixer = AudioSystem.getMixer(mixerInfo);

        float[] sampleRates = {192000f, 96000f, 48000f, 44100f, 22050f, 16000f, 8000f};
        int[] sampleSizes  = {32, 24, 16, 8};
        int[] channels     = {2, 1};

        for (float rate : sampleRates) {
            for (int size : sampleSizes) {
                for (int ch : channels) {
                    AudioFormat candidate = new AudioFormat(rate, size, ch, true, false);
                    DataLine.Info info = new DataLine.Info(TargetDataLine.class, candidate);
                    if (mixer.isLineSupported(info)) {
                        return candidate; // first match = best match
                    }
                }
            }
        }
        return new AudioFormat(44100f, 16, 2, true, false); // safe fallback
    }

    
    public static int getBufferSize(AudioFormat fmt, int milliseconds) {
        int frameSize = fmt.getFrameSize();
        float frameRate = fmt.getFrameRate();
        int frames = (int) (frameRate * milliseconds / 1000.0f);
        return frames * frameSize; // guaranteed frame-aligned AND time-consistent
    }

    public byte[] convertMicStream(byte[] input, AudioFormat tgtFmt);
    public byte[] convertSpkrStream(byte[] input, AudioFormat tgtFmt);

    
    public static final AudioFormat COMMON_NETWORK_FORMAT = new AudioFormat(48000f, 16, 1, true, false);
    public static final int NETWORK_BUFFER_SIZE = getBufferSize(COMMON_NETWORK_FORMAT, 20); // 20ms of audio per packet
    public void setMic(Mixer.Info mixerInfo);
    public void setSpeaker(Mixer.Info mixerInfo);
    
}
