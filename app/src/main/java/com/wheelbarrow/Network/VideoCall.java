package com.wheelbarrow.Network;

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

import com.wheelbarrow.Components.VideoCallComp;
import com.wheelbarrow.Components.Config.User;

class Feed {
    VideoCallComp videoFeed;
    DataOutputStream videoStreamWriter;
}
public class VideoCall implements AutoCloseable {
    HashMap<User, Feed> videoFeeds;

    //[THREADS]
    Thread sendThread;
    ExecutorService videoSupply;

    //[LOCKS]
    ReentrantLock readLock = new ReentrantLock();

    //[CONDITIONS]
    Condition sendCondition = readLock.newCondition();

    private FFmpegFrameGrabber videoGrabber;

    VideoCall() {
        this.videoFeeds = new HashMap<>();
        this.videoSupply = Executors.newCachedThreadPool();
    }

    public void start() {
        for (Feed feed : videoFeeds.values()) {
            videoSupply.submit(() -> supplyVideo(feed));
        }
    }
    public void stop() {

    }

    public void offer(User u, byte[] data) {
        Feed feed = videoFeeds.get(u);
        if (feed != null && feed.videoStreamWriter != null) {
            try {
                feed.videoStreamWriter.write(data);
                feed.videoStreamWriter.flush();
            } catch (IOException e) {
                System.err.println("Error writing to video stream for user " + u.getUsername() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void setVideoSource() {
        
    }

    public HashMap<User, Feed> getVideoFeeds() {
        return videoFeeds;
    }

    public void setOnVideoSupply(Consumer<byte[]> onVideoSupply) {

    }

    public void supplyVideo(Feed feed) {
        while (true) {
            try {
                feed.videoStreamWriter.write(videoGrabber.grabAtFrameRate().data.array());
            } catch (IOException e) {
                System.err.println("Error writing to video stream for user " + feed.videoFeed.getUser().getUsername() + ": " + e.getMessage());
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
        videoGrabber.start();
    }
    public void setFrame(String windowTitle) {
        videoGrabber = new FFmpegFrameGrabber(windowTitle);
        videoGrabber.setFormat("gdigrab");
        videoGrabber.start();
    }

    public void addUser(User u) {
        if (!videoFeeds.containsKey(u)) {
            Feed feed = new Feed();
            feed.videoFeed = new VideoCallComp(u);
            feed.videoStreamWriter = new DataOutputStream(feed.videoFeed.getVideoOutputStream());
            videoFeeds.put(u, feed);
            videoSupply.submit(() -> supplyVideo(feed));
        }
    }

    @Override
    public void close() {
        stop();
    }
}
