package Network;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.bytedeco.javacv.FFmpegFrameGrabber;

import java.awt.GraphicsDevice;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import Components.VideoCallComp;

class Feed {
    VideoCallComp videoFeed;
    DataOutputStream videoStreamWriter;
}
public class VideoCall implements AutoCloseable {
    List<Feed> videoFeeds;
    HashMap<Integer, Integer> clientIdtoFeedIdx; // Map client IDs to feed indices for routing video data

    //[THREADS]
    Thread sendThread;
    ExecutorService videoSupply;

    //[LOCKS]
    ReentrantLock readLock = new ReentrantLock();

    //[CONDITIONS]
    Condition sendCondition = readLock.newCondition();

    private FFmpegFrameGrabber videoGrabber;

    VideoCall() {
        this.videoFeeds = new ArrayList<>();
        this.videoSupply = Executors.newCachedThreadPool();
    }

    public void start() {
        for (Feed feed : videoFeeds) {
            videoSupply.submit(() -> supplyVideo(feed));
        }
    }
    public void stop() {

    }

    public void offer(int client_idx, byte[] data) {
        Integer feedIdx = clientIdtoFeedIdx.get(client_idx);
        if (feedIdx == null) {
            addUser(client_idx);
        }
        Feed feed = videoFeeds.get(feedIdx);
        if (feed != null && feed.videoStreamWriter != null) {
            try {
                feed.videoStreamWriter.write(data);
                feed.videoStreamWriter.flush();
            } catch (IOException e) {
                System.err.println("Error writing to video stream for user " + client_idx + ": " + e.getMessage());
            }
        } else {

        }
    }

    public void setVideoSource() {
        
    }

    public List<Feed> getVideoFeeds() {
        return videoFeeds;
    }

    public void setOnVideoSupply(Consumer<byte[]> onVideoSupply) {

    }

    public void supplyVideo(Feed feed) {
        while (true) {
            try {
                feed.videoStreamWriter.write(videoGrabber.grabAtFrameRate().data.array());
            } catch (IOException e) {
                System.err.println("Error writing to video stream for user " + feed.videoFeed.getClientId() + ": " + e.getMessage());
                e.printStackTrace();
            } catch (InterruptedException e) {
                return; // Thread interrupted, exit gracefully
            }
        }
    }

    public void setFrame(GraphicsDevice screen) {
        videoGrabber = new FFmpegFrameGrabber(screen.getIDstring());
        String os = System.getProperty("os.name").toLowerCase();
        //[TODO] this is only supported on windows, check others later
        videoGrabber.setFormat("gdigrab");
        try {
            videoGrabber.start();
        } catch (Exception e) {
            System.err.println("Error starting video grabber: " + e.getMessage());
        }
    }
    public void setFrame(String windowTitle) {
        videoGrabber = new FFmpegFrameGrabber(windowTitle);
        videoGrabber.setFormat("gdigrab");
        try {
            videoGrabber.start();
        } catch (Exception e) {
            System.err.println("Error starting video grabber: " + e.getMessage());
        }
    }

    public void addUser(int clientId) {
        Feed feed = new Feed();
        feed.videoFeed = new VideoCallComp(videoFeeds.size()-1);
        feed.videoStreamWriter = new DataOutputStream(feed.videoFeed.getVideoOutputStream());
        videoFeeds.add(feed);
        clientIdtoFeedIdx.put(clientId, videoFeeds.size() - 1);
        videoSupply.submit(() -> supplyVideo(feed));
    }

    @Override
    public void close() {
        stop();
    }
}
