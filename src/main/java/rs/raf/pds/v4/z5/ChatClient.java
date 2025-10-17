package main.java.rs.raf.pds.v4.z5;

import java.io.IOException;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;

import main.java.rs.raf.pds.v4.z5.messages.*;

import java.util.Arrays;
import java.util.List;

public class ChatClient {

    public static int DEFAULT_CLIENT_READ_BUFFER_SIZE = 1_000_000;
    public static int DEFAULT_CLIENT_WRITE_BUFFER_SIZE = 1_000_000;

    private final Client client;
    private final String hostName;
    private final int portNumber;
    final String userName;
    private final ChatMessageCallback callback;

    public ChatClient(String hostName, int portNumber, String userName, ChatMessageCallback callback) {
        this.client = new Client(DEFAULT_CLIENT_WRITE_BUFFER_SIZE, DEFAULT_CLIENT_READ_BUFFER_SIZE);
        this.hostName = hostName;
        this.portNumber = portNumber;
        this.userName = userName;
        this.callback = callback;

        KryoUtil.registerKryoClasses(client.getKryo());
        registerListener();
    }

    /** Interface for communication with GUI (App.java) */
    public interface ChatMessageCallback {
        void handleMessage(String message);
        void handleUserListUpdate(List<String> users, String room);
		void handleMessageUpdate(ChatMessage oldMessage, ChatMessage message, String room);
    }

    /** Registers server listener */
    private void registerListener() {
        client.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                client.sendTCP(new Login(userName));
                callback.handleMessage("✅ Connected as " + userName);
            }

            @Override
            public void received(Connection connection, Object object) {
                if (object instanceof ChatMessage) {
                    ChatMessage msg = (ChatMessage) object;
                    String text = "(" + msg.getChatRoom() + ") " + msg.getUser() + ": " + msg.getTxt();
                    callback.handleMessage(text);
                } else if (object instanceof ListUsers) {
                    ListUsers lu = (ListUsers) object;
                    callback.handleUserListUpdate(Arrays.asList(lu.getUsers()), "GLOBAL");
                } else if (object instanceof InfoMessage) {
                    InfoMessage im = (InfoMessage) object;
                    callback.handleMessage("Server: " + im.getTxt());
                }
            }

            @Override
            public void disconnected(Connection connection) {
                callback.handleMessage("⚠️ Disconnected from server.");
            }
        });
    }

    /** Connects client to server */
    public void start() throws IOException {
        client.start();
        connect();
    }

    private void connect() throws IOException {
        client.connect(2000, hostName, portNumber);
    }

    /** Sends message typed in GUI */
    public void processUserInput(String input, String room) {
        if (input == null || input.trim().isEmpty()) return;

        if ("WHO".equalsIgnoreCase(input.trim())) {
            client.sendTCP(new WhoRequest());
            return;
        }

        if (input.startsWith("@")) {
            // Private message: @username text
            int spaceIndex = input.indexOf(' ');
            if (spaceIndex > 0) {
                String to = input.substring(1, spaceIndex);
                String msg = input.substring(spaceIndex + 1);
                sendPrivateMessage(to, msg);
            }
        } else {
            ChatMessage message = new ChatMessage(userName, input);
            message.setChatRoom(room);
            client.sendTCP(message);
        }
    }

    /** Sends private message */
    public void sendPrivateMessage(String to, String text) {
        ChatMessage msg = new ChatMessage(userName, "@" + to + " " + text);
        msg.setPrivate(true);
        client.sendTCP(msg);
        callback.handleMessage("(Private) to " + to + ": " + text);
    }

    /** Gracefully disconnect */
    public void stop() {
        if (client.isConnected()) {
            client.close();
            callback.handleMessage("🔌 Disconnected.");
        }
    }

    public String getUserName() {
        return userName;
    }

    public String getActiveRoom() {
        return "GLOBAL";
    }
}
