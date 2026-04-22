package Components;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import Components.ComponentMacros.MessageType;

public class Message {
    
    public byte[] messageData;
    public String sender;
    public int type;

    public Message(String sender, byte[] messageData, int type) {
        this.sender = sender;
        this.messageData = messageData;
        this.type = type;
    }

    public Message(String sender, String message, int type) {
        this.sender = sender;
        this.messageData = message.getBytes();
        this.type = type;
    }

    public static Message fromBytes(byte[] data) throws IllegalArgumentException {
        final int MINIMUM_LENGTH = 12; // 4 bytes for type, 4 bytes for sender length, 4 bytes for message length
        if (data.length < MINIMUM_LENGTH) {
            throw new IllegalArgumentException("Data must be at least " + MINIMUM_LENGTH + " bytes long");
        }
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        
        try {
            int typeInt = dis.readInt();
            int senderLength = dis.readInt(); //32 bit sender length
            int messageLength = dis.readInt(); //32 bit message length

            if (data.length < MINIMUM_LENGTH + senderLength + messageLength) {
                throw new IllegalArgumentException("Data must be at least " + (MINIMUM_LENGTH + senderLength + messageLength) + " bytes long");
            }
            byte[] senderData = new byte[senderLength];
            dis.readFully(senderData, 0, senderLength);
            byte[] messageData = new byte[messageLength];
            dis.readFully(messageData, 0, messageLength);
            System.out.println("Entire data: " + "type: " + typeInt + ", senderLength: " + senderLength + ", messageLength: " + messageLength + ", sender: " + new String(senderData) + ", message: " + new String(messageData));
            return new Message(new String(senderData), messageData, typeInt);
        }
        catch (IOException e) {
            System.out.println("Error parsing message: " + e.getMessage());
            throw new IllegalArgumentException("Error parsing message: " + e.getMessage());
        }
    }

    

    public static byte[] toBytes(Message message) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        int senderLength = message.sender.getBytes().length;
        int messageLength = message.messageData.length;
        try {
            dos.writeInt(message.type);
            dos.writeInt(senderLength);
            dos.writeInt(messageLength);
            dos.write(message.sender.getBytes(), 0, senderLength);
            dos.write(message.messageData, 0, messageLength);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return baos.toByteArray();
    }
}
