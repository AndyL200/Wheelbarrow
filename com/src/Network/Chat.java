package Network;

import java.net.InetAddress;
import java.util.List;
import java.util.function.Consumer;

import Components.Message;
import Components.Config.User;

public interface Chat {
    public void send(byte[] message);
    public void send(Message message);
    public byte[] receive();
    public InetAddress getAddress();
    public void setOnMessageReceived(Consumer<Message> onMessageReceived);
    public ServerInfo getInfo();
    public List<User> getAllUsers();
}
