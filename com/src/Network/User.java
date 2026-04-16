package Network;
import java.net.InetAddress;

import Components.Message;

public interface User {
    public void send(byte[] message);
    public void send(Message message);
    public byte[] receive();
    public String getName();
    public InetAddress getAddress();
}
