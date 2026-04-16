package Components;

import Components.ComponentMacros.MessageType;

public class Message {
    
    public byte[] messageData;
    public String sender;
    public MessageType type;

    public Message(String sender, byte[] messageData, MessageType type) {
        this.sender = sender;
        this.messageData = messageData;
        this.type = type;
    }

    public Message(String sender, String message, MessageType type) {
        this.sender = sender;
        this.messageData = message.getBytes();
        this.type = type;
    }

    public static Message fromBytes(byte[] data) {
        final int MINIMUM_LENGTH = 12; // 1 byte for type, 4 bytes for sender length
        if (data.length < MINIMUM_LENGTH) {
            throw new IllegalArgumentException("Data must be at least " + MINIMUM_LENGTH + " bytes long");
        }
        int typeValue = data[0];
        MessageType type = MessageType.fromInt(typeValue);
        int senderLength = (data[1] << 24) + (data[2] << 16) + (data[3] << 8) + data[4]; //32 bit sender name max length
        if (data.length < MINIMUM_LENGTH + senderLength) {
            throw new IllegalArgumentException("Data length does not match sender length");
        }
        int messageLength = (data[4+senderLength] << 48) + (data[5+senderLength] << 40) + (data[6+senderLength] << 32) + (data[7+senderLength] << 24) + (data[8+senderLength] << 16) + (data[9+senderLength] << 8) + data[10+senderLength]; //64 bit message max length
        if (data.length < MINIMUM_LENGTH + senderLength + messageLength) {
            throw new IllegalArgumentException("Data length does not match message length");
        }
        byte[] messageData = new byte[messageLength];
        //offset of 12 bytes
        System.arraycopy(data, 12 + senderLength, messageData, 0, messageLength);
        return new Message(new String(data, 5, senderLength), messageData, type);
    }

    public static byte[] toBytes(Message message) {
        int senderLength = message.sender.getBytes().length;
        int messageLength = message.messageData.length;
        byte[] data = new byte[12 + senderLength + messageLength];

        //MessageType
        data[0] = (byte) message.type.getValue();
        
        //sender length and sender
        data[1] = (byte) (senderLength >> 24);
        data[2] = (byte) (senderLength >> 16);
        data[3] = (byte) (senderLength >> 8);
        data[4] = (byte) senderLength;
        System.arraycopy(message.sender.getBytes(), 0, data, 5, senderLength);
       
        //messageData length and data
        data[4+senderLength] = (byte) (messageLength >> 48);
        data[5+senderLength] = (byte) (messageLength >> 40);
        data[6+senderLength] = (byte) (messageLength >> 32);
        data[7+senderLength] = (byte) (messageLength >> 24);
        data[8+senderLength] = (byte) (messageLength >> 16);
        data[9+senderLength] = (byte) (messageLength >> 8);
        data[10+senderLength] = (byte) messageLength;
        System.arraycopy(message.messageData, 0, data, 12 + senderLength, message.messageData.length);
        return data;
    }
}
