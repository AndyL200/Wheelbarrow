package Components.ComponentMacros;



public enum MessageType {
    SERVER_INFO(1),
    SERVER_CACHE(1 << 1),
    MESSAGE(1 << 2),
    BROADCAST(1 << 3),
    WELCOME(1 << 4),
    TYPING(1 << 5),
    AUDIO(1 << 6),
    VIDEO(1 << 7),
    OTHER(1 << 8),
    LOGIN(1 << 9),
    AUTH_OK(1 << 10),
    AUTH_FAIL(1 << 11);

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
    public static String toString(MessageType type) {
        return switch(type) {
            case SERVER_INFO -> "SERVER_INFO";
            case SERVER_CACHE -> "SERVER_CACHE";
            case MESSAGE -> "MESSAGE";
            case BROADCAST -> "BROADCAST";
            case WELCOME -> "WELCOME";
            case TYPING -> "TYPING";
            case AUDIO -> "AUDIO";
            case VIDEO -> "VIDEO";
            case OTHER -> "OTHER";
            case LOGIN -> "LOGIN";
            case AUTH_OK -> "AUTH_OK";
            case AUTH_FAIL -> "AUTH_FAIL";
        };
    }
}
