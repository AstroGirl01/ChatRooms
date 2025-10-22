package main.java.rs.raf.pds.v4.z5.messages;


import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class PrivateMessage implements Serializable {

    private String user;          // Pošiljalac
    private String txt;           // Tekst poruke
    private String recipient;     // Primalac
    private LocalDateTime timestamp;  // Vreme slanja

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    public PrivateMessage() {}

    public PrivateMessage(String user, String txt, String recipient) {
        this.user = user;
        this.txt = txt;
        this.recipient = recipient;
        this.timestamp = LocalDateTime.now();
    }

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }

    public String getTxt() { return txt; }
    public void setTxt(String txt) { this.txt = txt; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getFormattedTimestamp() {
        return "[" + timestamp.format(FORMATTER) + "]";
    }

    @Override
    public String toString() {
        return getFormattedTimestamp() + " " + user + " → " + recipient + ": " + txt;
    }
}
