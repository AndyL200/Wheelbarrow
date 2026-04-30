package Components.Helper;

import java.net.InetAddress;
import org.json.JSONException;
import org.json.JSONObject;

public class CallConfig {
    public String HOST;
    public int PORT;
    public String HOSTNAME;
        public static byte[] toBytes(InetAddress inHost, int inPort, String HOSTNAME) {
            if (inHost == null || inPort == -1 || HOSTNAME == null) {
                System.out.println("Invalid audio call config: HOST, PORT, or HOSTNAME not set");
                return new byte[0];
            }
            try {
                JSONObject json = new JSONObject();
                json.put("host", inHost.getHostAddress());
                json.put("port", inPort);
                json.put("hostname", HOSTNAME);
                return json.toString().getBytes();
            } catch (JSONException e) {
                System.out.println("Error serializing audio call config: " + e.getMessage());
                return new byte[0];
            }
        }

        public static CallConfig fromBytes(byte[] bytes) {
            CallConfig config = new CallConfig();
            try {
                String jsonStr = new String(bytes);
                JSONObject json = new JSONObject(jsonStr);
                
                config.HOST = json.getString("host");
                config.PORT = json.getInt("port");
                config.HOSTNAME = json.getString("hostname");
                
                return config;
            } catch (JSONException e) {
                System.out.println("Error deserializing audio call config: " + e.getMessage());
                return config;
            }
        }

        CallConfig() {
                this.HOST = null;
                this.PORT = -1;
                this.HOSTNAME = null;
            }
        CallConfig(InetAddress HOST, int PORT, String HOSTNAME) {
            this.HOST = HOST.getHostAddress();
            this.PORT = PORT;
            this.HOSTNAME = HOSTNAME;
        }

        @Override
        public String toString() {
            return HOSTNAME + " (" + HOST + ":" + PORT + ")";
        }
}
