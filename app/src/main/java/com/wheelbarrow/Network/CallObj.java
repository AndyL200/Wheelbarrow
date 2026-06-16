package com.wheelbarrow.Network;

import java.net.NetworkInterface;
import java.net.SocketException;

public abstract class CallObj implements Call {

    
    public volatile AudioCall audioCall = null;
    public volatile VideoCall videoCall = null;

    //Overridden further
    @Override
    public void start() {
        openAudioCall();
        openVideoCall();
    }

    @Override
    public void stop() {
        if (audioCall != null) {
            audioCall.stop();
        }
        if (videoCall != null) {
            videoCall.stop();
        }
    }

    public AudioCall getAudio() {return audioCall;}
    public VideoCall getVideo() {return videoCall;}
    public int getMinMTU() {
        int[] minMTU = {65535};
        try {
        NetworkInterface.getNetworkInterfaces().asIterator().forEachRemaining((ni) -> {
            try {
                if (ni.isLoopback() || !ni.isUp()) {
                    return;
                }
                int mtu = ni.getMTU();
                if (mtu > 0 && mtu < minMTU[0]) {
                    minMTU[0] = mtu;
                }
            } catch (SocketException e) {
                System.out.println("Error checking network interface: " + e.getMessage());
                return;
            }
            
        });
        } catch (SocketException se) {
            System.out.println("Failed to set send buffer size: " + se.getMessage());
            return 0;
        }

        return minMTU[0];
    }
    //defaults
    public void openAudioCall() {if (audioCall != null) audioCall.start();}
    public void openVideoCall() {if (videoCall != null) videoCall.start();}

}
