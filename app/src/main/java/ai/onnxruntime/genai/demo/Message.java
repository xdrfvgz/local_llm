public class Message {
    private String content;
    private MessageType type;

    public Message(String content, MessageType type) {
        this.content = content;
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public MessageType getType() {
        return type;
    }
}

public enum MessageType {
    SENT, RECEIVED
}
