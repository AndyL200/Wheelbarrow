package Components.Helper;

import java.net.InetAddress;

public class VideoCallConfig {
    public String HOST;
    public int PORT;
    public String HOSTNAME;
    
    public VideoCallConfig(String inHost, int inPort, String inHostname) {
        this.HOST = inHost;
        this.PORT = inPort;
        this.HOSTNAME = inHostname;
    }
    
   public static byte[] toBytes(InetAddress address, int port, String hostnamt) {
        if (address == null || port == -1 || hostnamt == null) {
            System.out.println("Invalid video call config: HOST, PORT, or HOSTNAME not set");
            return new byte[0];
        }
        String json = String.format("{\"host\":\"%s\",\"port\":%d,\"hostname\":\"%s\"}", address.getHostAddress(), port, hostnamt);
        return json.getBytes();
    }

    public static VideoCallConfig fromBytes(byte[] bytes) {
        try {
            String jsonStr = new String(bytes);
            String[] parts = jsonStr.replaceAll("[{}\"]", "").split(",");
            String host = null;
            int port = -1;
            String hostname = null;
            for (String part : parts) {
                String[] kv = part.split(":");
                if (kv.length == 2) {
                    switch (kv[0].trim()) {
                        case "host": host = kv[1].trim(); break;
                        case "port": port = Integer.parseInt(kv[1].trim()); break;
                        case "hostname": hostname = kv[1].trim(); break;
                    }
                }
            }
            return new VideoCallConfig(host, port, hostname);
        } catch (Exception e) {
            System.out.println("Error deserializing video call config: " + e.getMessage());
            return new VideoCallConfig(null, -1, null);
        }
   }
}
