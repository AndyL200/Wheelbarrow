package Network;


public interface Call {
    public void start();
    public void stop();

    public static final int GENERIC_BUFFER_SIZE = AudioCall.NETWORK_BUFFER_SIZE + VideoCall.NETWORK_BUFFER_SIZE;

}

