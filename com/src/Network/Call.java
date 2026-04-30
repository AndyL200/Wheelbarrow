package Network;

import Components.ComponentMacros.DatagramType;

public interface Call {
    public void start();
    public void stop();

    public static final int GENERIC_BUFFER_SIZE = 4096;

}

record Packet(DatagramType type, byte[] data) {}
