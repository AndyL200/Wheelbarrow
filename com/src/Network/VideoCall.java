package Network;

import java.util.function.Consumer;

public interface VideoCall extends Call {
    public void setVideoSource();
    public void offer(byte[] videoData);
    public void setOnVideoSupply(Consumer<byte[]> onVideoSupply);

    public static final int NETWORK_BUFFER_SIZE = 65536; // 64KB, adjust as needed
}
