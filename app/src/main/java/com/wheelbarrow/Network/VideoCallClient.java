package com.wheelbarrow.Network;

import java.util.List;
import java.util.function.Consumer;
import java.util.ArrayList;

import com.wheelbarrow.Components.VideoCallComp;

public class VideoCallClient implements VideoCall, AutoCloseable {
    List<VideoCallComp> videoFeeds;

    VideoCallClient() {
        this.videoFeeds = new ArrayList<>();
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
    public List<VideoCallComp> getVideoFeeds() {
        return videoFeeds;
    }

    @Override
    public void setOnVideoSupply(Consumer<byte[]> onVideoSupply) {

    }

    @Override
    public void close() {
        stop();
    }
}
