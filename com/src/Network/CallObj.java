package Network;

public class CallObj implements Call {

    
    public AudioCall audioCall = null;
    public VideoCall videoCall = null;

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    public AudioCall getAudio() {return audioCall;}
    public VideoCall getVideo() {return videoCall;}
    public void openAudioCall() {}
    public void openVideoCall() {}

}
