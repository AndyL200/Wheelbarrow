package Components.ComponentMacros;



public enum MessageType {
    SERVER_INFO(1),
    SERVER_CACHE(1 << 1),
    MESSAGE(1 << 2),
    BROADCAST(1 << 3),
    WELCOME(1 << 4),
    TYPING(1 << 5),
    AUDIO_HOST(1 << 6),
    VIDEO_HOST(1 << 7),
    OTHER(1 << 8);

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
            case AUDIO_HOST -> "AUDIO_HOST";
            case VIDEO_HOST -> "VIDEO_HOST";
            case OTHER -> "OTHER";
        };
    }
}
