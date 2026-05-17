package Components.ComponentMacros;

public class DatagramPacket {
    public class PacketHeader {
        public int MAGIC = 0xFE;
        private String from;
        private String to;
        PacketHeader(String from, String to) {
            this.from = from;
            this.to = to;
        }
        public String getFrom() {
            return from;
        }
        public String getTo() {
            return to;
        }
    }

    PacketHeader header;
    byte[] payload;
    DatagramPacket(PacketHeader header, byte[] payload) {
        this.header = header;
        this.payload = payload;
    }
}