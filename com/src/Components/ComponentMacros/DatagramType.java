package Components.ComponentMacros;

public enum DatagramType {
    AUDIO(1),
    VIDEO(2),
    UNKNOWN(99);

    private final int value;

    DatagramType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public String toString() {
        return switch (this) {
            case AUDIO -> "AUDIO";
            case VIDEO -> "VIDEO";
            case UNKNOWN -> "UNKNOWN";
        };
    }
}


