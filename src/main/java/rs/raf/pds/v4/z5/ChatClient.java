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

    public interface ChatMessageCallback {
        void handleMessage(String message);
        void handleUserListUpdate(List<String> users, String room);
        void handleMessageUpdate(ChatMessage oldMessage, ChatMessage message, String room);
        void handleRoomListUpdate(List<String> rooms);
    }

    private void registerListener() {
        client.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                client.sendTCP(new Login(userName));
            }

            @Override
            public void disconnected(Connection connection) {
                printToGUI("⚠️ Disconnected from server.");
            }

            @Override
            public void received(Connection connection, Object object) {
                if (object instanceof ChatMessage msg) {
                    String formatted = "(" + msg.getIndex() + ") " + msg.getFormattedTimestamp()
                            + " (" + msg.getChatRoom() + ") " + msg.getUser() + ": " + msg.getTxt();
                    printToGUI(formatted);
                }

                else if (object instanceof PrivateMessage pm) {
                    String timestamp = "";
                    if (pm.getTimestamp() != null) {
                        timestamp = "[" + pm.getTimestamp().format(
                                java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
                        ) + "]";
                    }
                    printToGUI(timestamp + " 📩 [Private] " + pm.getUser() + " → you: " + pm.getTxt());
                }

                else if (object instanceof ListUsers lu) {
                    callback.handleUserListUpdate(Arrays.asList(lu.getUsers()), activeRoom);
                }

                else if (object instanceof ListRooms lr) {
                    Platform.runLater(() ->
                            callback.handleRoomListUpdate(Arrays.asList(lr.getRooms()))
                    );
                }

                else if (object instanceof InfoMessage im) {
                    printToGUI("[Server] " + im.getTxt());
                }

                else if (object instanceof List<?> msgs) {
                    if (msgs.size() == 2 && msgs.get(0) instanceof ChatMessage && msgs.get(1) instanceof ChatMessage) {
                        callback.handleMessageUpdate((ChatMessage) msgs.get(0), (ChatMessage) msgs.get(1), activeRoom);
                    }
                }
            }
        });
    }

    private void printToGUI(String msg) {
        Platform.runLater(() -> callback.handleMessage(msg));
    }

    public void start() throws IOException {
        client.start();
        client.connect(3000, hostName, portNumber);
    }

    public void stop() {
        if (client.isConnected()) {
            client.close();
            printToGUI("🔌 Disconnected.");
        }
    }

    public String getUserName() { return userName; }
    public String getActiveRoom() { return activeRoom; }
    public void setActiveRoom(String room) { this.activeRoom = room; }

    public void processUserInput(String input, String room) {
        if (input == null || input.trim().isEmpty()) return;

        String trimmed = input.trim();
        String upper = trimmed.toUpperCase(); 

        if (upper.startsWith("/EDIT") || upper.startsWith("/REPLY")) {
            client.sendTCP(trimmed);  
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
            ChatMessage multicastMsg = new ChatMessage(userName, trimmed, activeRoom);
            client.sendTCP(multicastMsg);
            printToGUI("📡 Sent multicast: " + trimmed);
            return;
        }

        // --- LEAVE ROOM ---
        if (upper.startsWith("/LEAVEROOM")) {
            client.sendTCP(upper);
            return;
        }

        // --- Komande ---
        if (upper.startsWith("/CREATE") ||
            upper.startsWith("/LISTROOMS") ||
            upper.startsWith("/JOIN") ||
            upper.startsWith("/ROOM") ||
            upper.startsWith("/INVITE") ||
            upper.startsWith("/MYROOMS") ||
            upper.startsWith("/HISTORY")) {
        	client.sendTCP(trimmed);
            return;
        }

        // --- Obična poruka ---
        ChatMessage msg = new ChatMessage(userName, trimmed, room);
        client.sendTCP(msg);
    }

    /** Slanje privatne poruke */
    private void sendPrivateMessage(String to, String text) {
        PrivateMessage pm = new PrivateMessage(userName, text, to);
        client.sendTCP(pm);
        System.out.println("🕵️‍♀️ You → " + to + ": " + text);
    }

    /** Slanje multicast poruke (grupi korisnika) */
    public void sendMulticastMessage(List<String> recipients, String text) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String u : recipients) joiner.add(u.trim());

        String messageText = "@" + joiner + " " + text;
        ChatMessage groupMsg = new ChatMessage(userName, messageText, activeRoom);
        client.sendTCP(groupMsg);
        printToGUI("📡 You → [" + joiner + "]: " + text);
    }



    /** Kreira sobu preko RPC poziva */
    public void createRoom(String roomName) {
        if (roomName == null || roomName.isEmpty()) return;
        client.sendTCP("/CREATE " + roomName);
    }

    /** Vraća listu svih soba */
    public List<String> getAllRooms() {
        client.sendTCP("/LISTROOMS");
        return new ArrayList<>();
    }

    /** Pridružuje korisnika sobi i ažurira aktivnu sobu */
    public List<String> joinRoom(String roomName) {
        if (roomName == null || roomName.isEmpty()) return new ArrayList<>();
        client.sendTCP("/JOIN " + roomName);
        this.activeRoom = roomName;
        return new ArrayList<>();
    }
}
