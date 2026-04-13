package Components.ComponentMacros;

public enum MessageType {
    SERVER_INFO(1),
    SERVER_CACHE(1 << 1),
    MESSAGE(1 << 2),
    BROADCAST(1 << 3),
    WELCOME(1 << 4),
    OTHER(1 << 5);

    private final int value;
    
    MessageType(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }

    public static MessageType fromInt(int value) {
        for (MessageType type : MessageType.values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid MessageType value: " + value);
    }
    public static String toString(MessageType type) {
        return switch(type) {
            case SERVER_INFO -> "SERVER_INFO";
            case SERVER_CACHE -> "SERVER_CACHE";
            case MESSAGE -> "MESSAGE";
            case BROADCAST -> "BROADCAST";
            case WELCOME -> "WELCOME";
            case OTHER -> "OTHER";
        };
    }
}
