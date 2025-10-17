package main.java.rs.raf.pds.v4.z5.messages;

import java.io.Serializable;

public class ChatMessage implements Serializable {
    private String user;
    private String txt;
    private boolean isPrivate;
    private String chatRoom; // ✅ DODATO

    public ChatMessage() {}

    public ChatMessage(String user, String txt) {
        this.user = user;
        this.txt = txt;
        this.isPrivate = false;
        this.chatRoom = "GLOBAL"; // ✅ podrazumevana soba
    }

    // === GETTERI I SETTERI ===
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }

    public String getTxt() { return txt; }
    public void setTxt(String txt) { this.txt = txt; }

    public boolean isPrivate() { return isPrivate; }
    public void setPrivate(boolean aPrivate) { isPrivate = aPrivate; }

    public String getChatRoom() { return chatRoom; }
    public void setChatRoom(String chatRoom) { this.chatRoom = chatRoom; }

    @Override
    public String toString() {
        return "(" + chatRoom + ") " + user + ": " + txt;
    }

    // Ako imaš metodu format(), možeš je ovako doraditi:
    public String format() {
        return "(" + chatRoom + ") " + user + ": " + txt;
    }
}
