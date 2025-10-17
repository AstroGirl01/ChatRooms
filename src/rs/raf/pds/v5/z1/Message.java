package rs.raf.pds.v5.z1;

import java.io.Serializable;

public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    public int id;
    public String user;
    public String tipPoruke;
    public String poruka;

    public Message() {}

    public Message(int id, String user) {
        this.id = id;
        this.user = user;
    }

    public String getTipPoruke() {
        return tipPoruke;
    }

    public String getPoruka() {
        return poruka;
    }

    public String getUser() {
        return user;
    }

    @Override
    public String toString() {
        return "[" + id + "] " + user + " (" + tipPoruke + "): " + poruka;
    }
}
