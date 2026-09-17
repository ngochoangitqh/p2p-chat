package client;

import java.io.Serializable;

/**
 * Lớp đại diện cho một tin nhắn chat hoặc file được lưu trong HashMap.
 */
public class ChatMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        TEXT,
        FILE,
        SYSTEM
    }

    private final Type type;
    private final String sender;
    private final String content;
    private final String time;
    private final boolean isMe;

    // Thông tin bổ sung cho tin nhắn dạng FILE
    private final String fileId;
    private final String fileName;
    private final long fileSize;
    private final boolean sentByMe;

    public static ChatMessage text(String sender, String content, String time, boolean isMe) {
        return new ChatMessage(Type.TEXT, sender, content, time, isMe, null, null, 0, false);
    }

    public static ChatMessage file(String sender, String fileId, String fileName, long fileSize, String time, boolean isMe, boolean sentByMe) {
        return new ChatMessage(Type.FILE, sender, fileName, time, isMe, fileId, fileName, fileSize, sentByMe);
    }

    public static ChatMessage system(String content, String time) {
        return new ChatMessage(Type.SYSTEM, "SYSTEM", content, time, false, null, null, 0, false);
    }

    public ChatMessage(Type type, String sender, String content, String time, boolean isMe,
                       String fileId, String fileName, long fileSize, boolean sentByMe) {
        this.type = type;
        this.sender = sender;
        this.content = content;
        this.time = time;
        this.isMe = isMe;
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.sentByMe = sentByMe;
    }

    public Type getType() {
        return type;
    }

    public String getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public String getTime() {
        return time;
    }

    public boolean isMe() {
        return isMe;
    }

    public String getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public boolean isSentByMe() {
        return sentByMe;
    }

    @Override
    public String toString() {
        return "[" + time + "] " + sender + " (" + type + "): " + content;
    }
}
