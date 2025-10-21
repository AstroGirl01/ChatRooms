package main.java.rs.raf.pds.v4.z5.messages;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class ChatMessage implements Serializable {

    private String user;
    private String txt;
    private String chatRoom;
    private boolean reply = false;
    private boolean stamped = false;
    private boolean noNeed = false;

    private LocalDateTime timestamp;
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd-M-yyyy HH:mm:ss");

    public ChatMessage() { }

    
    public ChatMessage(String user, String txt, String chatRoom) {
        this.user = user;
        this.chatRoom = chatRoom;
        this.timestamp = LocalDateTime.now();
        this.txt = txt + " [" + timestamp.format(FORMATTER) + "]";
    }

    public ChatMessage(String user, String txt, String chatRoom, boolean stamped) {
        this.user = user;
        this.chatRoom = chatRoom;
        this.timestamp = LocalDateTime.now();
        this.stamped = stamped;
        this.txt = stamped ? txt : txt + " [" + timestamp.format(FORMATTER) + "]";
    }


    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }

    public String getTxt() { return txt; }

    public void setTxt(String txt) {
        this.timestamp = LocalDateTime.now();
        this.txt = stamped ? txt : txt + " [" + timestamp.format(FORMATTER) + "]";
    }

    public String getChatRoom() { return chatRoom; }
    public void setChatRoom(String chatRoom) { this.chatRoom = chatRoom; }

    public boolean isStamped() { return stamped; }
    public void setStamped(boolean stamped) { this.stamped = stamped; }

    public boolean isReply() { return reply; }
    public void setReply(boolean reply) { this.reply = reply; }

    public boolean isNoNeed() { return noNeed; }
    public void setNoNeed(boolean noNeed) { this.noNeed = noNeed; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }


    public String format() {
        return "(" + chatRoom + ") " + user + ": " + txt + "\n";
    }

    @Override
    public String toString() {
        return "[" + timestamp.format(FORMATTER) + "] (" + chatRoom + ") " + user + ": " + txt;
    }
}
