package Network;

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
    //defaults
    public void openAudioCall() {if (audioCall != null) audioCall.start();}
    public void openVideoCall() {if (videoCall != null) videoCall.start();}

}
