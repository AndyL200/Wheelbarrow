package Network;

import java.net.InetAddress;
import java.util.function.Consumer;

public class VideoCallClient implements VideoCall, AutoCloseable {

    private Consumer<byte[]> onVideoSupply;

    VideoCallClient() {
    }

    @Override
    public void start() {

    }
    @Override
    public void stop() {

    }

    @Override
    public void offer(byte[] data) {

    }

    @Override
    public void setVideoSource() {
        
    }

    @Override
    public void setOnVideoSupply(Consumer<byte[]> onVideoSupply) {
        this.onVideoSupply = onVideoSupply;
    }

    @Override
    public void close() {

    }
}
