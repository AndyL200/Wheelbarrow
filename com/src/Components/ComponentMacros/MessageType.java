package Components.ComponentMacros;



public enum MessageType {
    SERVER_INFO(1),
    SERVER_CACHE(1 << 1),
    MESSAGE(1 << 2),
    BROADCAST(1 << 3),
    WELCOME(1 << 4),
    TYPING(1 << 5),
    OTHER(1 << 6);

    private final int value;
    
    MessageType(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
    
    //TODO
    // public MessageOther getOther() {
    //     if (this == OTHER) {
    //         return new MessageOther();
    //     }
    //     throw new IllegalStateException("MessageType is not OTHER");
    // }

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
            case TYPING -> "TYPING";
            case OTHER -> "OTHER";
        };
    }
}
