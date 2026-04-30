package Network;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;


//Abstracting these away from the network layer
//Maybe rename??
public class VideoCallServer implements VideoCall, AutoCloseable {
    Consumer<byte[]> onVideoSupply;

    BlockingQueue<byte[]> videoJitterQueue = new LinkedBlockingQueue<>();

    private volatile boolean running = false;

    Thread consumeThread;
    Thread supplyThread;

    public VideoCallServer() {
    }

    public void setOnVideoSupply(Consumer<byte[]> onVideoSupply) {
        this.onVideoSupply = onVideoSupply;
    }

    @Override
    public void start() {

    }

    private void videoSupplier() {
        while (running) {

        }
    }

    private void consumeVideo() {
        while (running) {
            byte[] videoData = videoJitterQueue.poll();
            if (videoData == null) {
                continue; // No data to send, loop again
            }
        }
    }

    public void setVideoSource() {
        // Implementation to set video source (e.g., webcam)
    }

    @Override
    public void stop() {

    }

    @Override
    public void offer(byte[] data) {
        
    }



    @Override
    public void close() {

    }
}


