package main.java.rs.raf.pds.v4.z5;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import javafx.application.Platform;
import main.java.rs.raf.pds.v4.z5.messages.*;

import java.io.IOException;
import java.util.*;

public class ChatClient {

    public static final int DEFAULT_CLIENT_READ_BUFFER_SIZE = 1_000_000;
    public static final int DEFAULT_CLIENT_WRITE_BUFFER_SIZE = 1_000_000;

    private final Client client;
    private final String hostName;
    private final int portNumber;
    public final String userName;
    private final ChatMessageCallback callback;

    private String activeRoom = "GLOBAL";

    public ChatClient(String hostName, int portNumber, String userName, ChatMessageCallback callback) {
        this.client = new Client(DEFAULT_CLIENT_WRITE_BUFFER_SIZE, DEFAULT_CLIENT_READ_BUFFER_SIZE);
        this.hostName = hostName;
        this.portNumber = portNumber;
        this.userName = userName;
        this.callback = callback;

        KryoUtil.registerKryoClasses(client.getKryo());
        registerListener();
    }

    /** Callback interfejs za GUI */
    public interface ChatMessageCallback {
        void handleMessage(String message);
        void handleUserListUpdate(List<String> users, String room);
        void handleMessageUpdate(ChatMessage oldMessage, ChatMessage message, String room);
    }

    /** Listener za događaje sa servera */
    private void registerListener() {
        client.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                client.sendTCP(new Login(userName));
                printToGUI("✅ Connected as " + userName);
            }

            @Override
            public void disconnected(Connection connection) {
                printToGUI("⚠️ Disconnected from server.");
            }

            @Override
            public void received(Connection connection, Object object) {
                if (object instanceof ChatMessage) {
                    ChatMessage msg = (ChatMessage) object;
                    String prefix = msg.getChatRoom() != null ? "(" + msg.getChatRoom() + ") " : "";
                    printToGUI(prefix + msg.getUser() + ": " + msg.getTxt());
                }

                else if (object instanceof PrivateMessage) {
                    PrivateMessage pm = (PrivateMessage) object;
                    printToGUI("📩 [Private] " + pm.getUser() + " → you: " + pm.getTxt());
                }

                else if (object instanceof ListUsers) {
                    ListUsers lu = (ListUsers) object;
                    callback.handleUserListUpdate(Arrays.asList(lu.getUsers()), activeRoom);
                }

                else if (object instanceof InfoMessage) {
                    InfoMessage im = (InfoMessage) object;
                    printToGUI("[Server] " + im.getTxt());
                }

                else if (object instanceof List<?>) {
                    // ažuriranje (edit) poruka
                    List<?> msgs = (List<?>) object;
                    if (msgs.size() == 2 && msgs.get(0) instanceof ChatMessage && msgs.get(1) instanceof ChatMessage) {
                        callback.handleMessageUpdate((ChatMessage) msgs.get(0), (ChatMessage) msgs.get(1), activeRoom);
                    }
                }
            }
        });
    }

    /** Ispis poruka u GUI */
    private void printToGUI(String msg) {
        Platform.runLater(() -> callback.handleMessage(msg));
    }

    /** Start konekcije ka serveru */
    public void start() throws IOException {
        client.start();
        client.connect(3000, hostName, portNumber);
    }

    /** Stop konekcije */
    public void stop() {
        if (client.isConnected()) {
            client.close();
            printToGUI("🔌 Disconnected.");
        }
    }

    public String getUserName() { return userName; }
    public String getActiveRoom() { return activeRoom; }
    public void setActiveRoom(String room) { this.activeRoom = room; }

    /** Glavna funkcija za obradu unosa iz GUI-ja */
    public void processUserInput(String input, String room) {
        if (input == null || input.trim().isEmpty()) return;

        String trimmed = input.trim();

        // --- WHO ---
        if ("WHO".equalsIgnoreCase(trimmed)) {
            client.sendTCP(new WhoRequest());
            return;
        }

        // --- BYE ---
        if ("BYE".equalsIgnoreCase(trimmed)) {
            stop();
            return;
        }

        // --- Privatna poruka ---
        if (trimmed.startsWith("@") && !trimmed.startsWith("@{")) {
            int space = trimmed.indexOf(' ');
            if (space > 1) {
                String recipient = trimmed.substring(1, space);
                String text = trimmed.substring(space + 1);
                sendPrivateMessage(recipient, text);
                return;
            }
        }

        // --- Multicast poruka ---
        if (trimmed.startsWith("@{") && trimmed.contains("}")) {
            int end = trimmed.indexOf('}');
            String listPart = trimmed.substring(2, end);
            List<String> users = Arrays.asList(listPart.split(","));
            String msgText = trimmed.substring(end + 1).trim();
            sendMulticastMessage(users, msgText);
            return;
        }

        // --- Komande ---
        if (trimmed.toUpperCase().startsWith("/CREATE") ||
            trimmed.toUpperCase().startsWith("/LISTROOMS") ||
            trimmed.toUpperCase().startsWith("/JOIN") ||
            trimmed.toUpperCase().startsWith("/ROOM") ||
            trimmed.toUpperCase().startsWith("/INVITE") ||
            trimmed.toUpperCase().startsWith("/MYROOMS") ||
            trimmed.toUpperCase().startsWith("/HISTORY")) {
            client.sendTCP(trimmed.toUpperCase());
            return;
        }

        // --- Obična poruka ---
        ChatMessage msg = new ChatMessage(userName, trimmed, room);
        client.sendTCP(msg);
    }

    /** Slanje privatne poruke */
    public void sendPrivateMessage(String to, String text) {
        PrivateMessage pm = new PrivateMessage(userName, text, to);
        client.sendTCP(pm);
        printToGUI("🕵️‍♀️ You → " + to + ": " + text);
    }

    /** Slanje multicast poruke (grupi korisnika) */
    public void sendMulticastMessage(List<String> recipients, String text) {
        // Pretvaramo listu korisnika u string da bi server znao kome ide
        StringJoiner joiner = new StringJoiner(", ");
        for (String u : recipients) joiner.add(u.trim());

        String messageText = "@" + joiner + " " + text;
        ChatMessage groupMsg = new ChatMessage(userName, messageText, activeRoom);
        client.sendTCP(groupMsg);
        printToGUI("📡 You → [" + joiner + "]: " + text);
    }
}
