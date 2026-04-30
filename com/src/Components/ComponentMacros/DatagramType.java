package Components.ComponentMacros;

public enum DatagramType {
    AUDIO(1),
    VIDEO(2);

    private final int value;

    DatagramType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
