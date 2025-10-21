package main.java.rs.raf.pds.v4.z5;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

import main.java.rs.raf.pds.v4.z5.messages.ChatMessage;
import main.java.rs.raf.pds.v4.z5.messages.InfoMessage;
import main.java.rs.raf.pds.v4.z5.messages.KryoUtil;
import main.java.rs.raf.pds.v4.z5.messages.ListUsers;
import main.java.rs.raf.pds.v4.z5.messages.Login;
import main.java.rs.raf.pds.v4.z5.messages.PrivateMessage;
import main.java.rs.raf.pds.v4.z5.messages.WhoRequest;


public class ChatServer implements Runnable {

    private volatile Thread thread = null;
    private volatile boolean running = false;

    private final Server server;
    private final int portNumber;

    // Mape i liste
    private final ConcurrentMap<String, Connection> userConnectionMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<Connection, String> connectionUserMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<Connection>> chatRooms = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> userActiveRoomsMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<ChatMessage>> chatRoomsMessages = new ConcurrentHashMap<>();
    private final List<PrivateMessage> privateMessagesList = new CopyOnWriteArrayList<>();

    public ChatServer(int portNumber) {
        this.server = new Server();
        this.portNumber = portNumber;
        KryoUtil.registerKryoClasses(server.getKryo());
        registerListener();
    }

    /** Registracija listenera za sve evente. */
    private void registerListener() {
        server.addListener(new Listener() {

            @Override
            public void received(Connection connection, Object object) {

                //  LOGIN
                if (object instanceof Login) {
                    Login login = (Login) object;
                    registerUser(login, connection);
                    connection.sendTCP(new InfoMessage("👋 Welcome " + login.getUserName()));
                    updateUserChatRooms(connection);
                    return;
                }

                //  WHO REQUEST
                if (object instanceof WhoRequest) {
                    ListUsers listUsers = new ListUsers(getAllUsers());
                    connection.sendTCP(listUsers);
                    updateUserChatRooms(connection);
                    return;
                }

                // PRIVATNA PORUKA
                if (object instanceof PrivateMessage) {
                    PrivateMessage pm = (PrivateMessage) object;
                    handlePrivateMessage(pm, connection);
                    return;
                }

                // OBIČNA CHAT PORUKA (može biti multicast)
                if (object instanceof ChatMessage) {
                    ChatMessage chatMessage = (ChatMessage) object;
                    if (chatMessage.getTxt().startsWith("@")) {
                        handlePrivateOrMulticast(chatMessage, connection);
                        return;
                    }
                    addMessageToChatRoom(chatMessage);
                    broadcastChatMessage(chatMessage, connection);
                    return;
                }

                // KOMANDE (ROOMS, HISTORY, EDIT, itd.)
                if (object instanceof String) {
                    handleCommand((String) object, connection);
                }
            }

            @Override
            public void disconnected(Connection connection) {
                String user = connectionUserMap.get(connection);
                connectionUserMap.remove(connection);
                userConnectionMap.remove(user);
                broadcastInfo(user + " has disconnected.");
            }
        });
    }

    

    private void registerUser(Login login, Connection conn) {
        String userName = login.getUserName();
        if (userConnectionMap.containsKey(userName)) {
            Connection old = userConnectionMap.get(userName);
            if (old.isConnected()) old.close();
        }
        chatRooms.computeIfAbsent("GLOBAL", k -> new CopyOnWriteArrayList<>()).add(conn);
        userConnectionMap.put(userName, conn);
        connectionUserMap.put(conn, userName);
        userActiveRoomsMap.putIfAbsent(userName, "GLOBAL");
        broadcastInfo("User " + userName + " joined the server.");
        broadcastUserListUpdate("GLOBAL");
    }

    

    private void handlePrivateMessage(PrivateMessage pm, Connection sender) {
        String to = pm.getRecipient();
        Connection receiver = userConnectionMap.get(to);

        if (receiver != null && receiver.isConnected()) {
            receiver.sendTCP(pm);
            sender.sendTCP(new InfoMessage("Private message delivered to " + to));
            privateMessagesList.add(pm);
            System.out.println("Private from " + pm.getUser() + " → " + to + ": " + pm.getTxt());
        } else {
            sender.sendTCP(new InfoMessage("User " + to + " is offline or unknown."));
        }
    }

    private void handlePrivateOrMulticast(ChatMessage msg, Connection senderConn) {
        String text = msg.getTxt().trim();
        String fromUser = msg.getUser();

        int firstSpace = text.indexOf(' ');
        if (firstSpace == -1) return;

        String toPart = text.substring(1, firstSpace);
        String msgText = text.substring(firstSpace + 1);
        String[] recipients = toPart.split(",");

        for (String r : recipients) {
            r = r.trim();
            Connection targetConn = userConnectionMap.get(r);
            if (targetConn != null && targetConn.isConnected()) {
            	ChatMessage privateMsg = new ChatMessage(fromUser,"[group] " + msgText,userActiveRoomsMap.getOrDefault(fromUser, "GLOBAL"));
                privateMsg.setChatRoom(userActiveRoomsMap.getOrDefault(fromUser, "GLOBAL"));
                targetConn.sendTCP(privateMsg);
            } else {
                senderConn.sendTCP(new InfoMessage("User " + r + " not found or offline."));
            }
        }
    }



    private void handleCommand(String command, Connection conn) {
        String[] parts = command.split(" ");
        String action = parts[0].toUpperCase();

        switch (action) {
            case "/CREATE":
                if (parts.length >= 2) createRoom(parts[1], conn);
                break;
            case "/LISTROOMS":
                listRooms(conn);
                break;
            case "/JOIN":
                if (parts.length >= 2) joinRoom(parts[1], conn);
                break;
            case "/ROOM":
                if (parts.length >= 2) setActiveRoom(conn, parts[1]);
                break;
            case "/INVITE":
                if (parts.length >= 3) inviteUser(parts[1], parts[2], conn);
                break;
            case "/HISTORY":
                sendRoomHistory(conn, parts);
                break;
        }
    }


    private void createRoom(String roomName, Connection conn) {
        chatRooms.putIfAbsent(roomName, new CopyOnWriteArrayList<>());
        conn.sendTCP(new InfoMessage("Room '" + roomName + "' created."));
    }

    private void listRooms(Connection conn) {
        conn.sendTCP(new InfoMessage("Available rooms: " + chatRooms.keySet()));
    }

    private void joinRoom(String roomName, Connection conn) {
        chatRooms.computeIfAbsent(roomName, k -> new CopyOnWriteArrayList<>()).add(conn);
        conn.sendTCP(new InfoMessage("Joined room '" + roomName + "'."));
    }

    private void setActiveRoom(Connection conn, String roomName) {
        String user = connectionUserMap.get(conn);
        userActiveRoomsMap.put(user, roomName);
        conn.sendTCP(new InfoMessage("Active room set to: " + roomName));
        broadcastUserListUpdate(roomName);
    }

    private void inviteUser(String invitedUser, String roomName, Connection inviterConn) {
        Connection invitedConn = userConnectionMap.get(invitedUser);
        String inviter = connectionUserMap.get(inviterConn);

        if (invitedConn != null && invitedConn.isConnected()) {
            invitedConn.sendTCP(new InfoMessage("📩 " + inviter + " invited you to room '" + roomName + "'."));
            inviterConn.sendTCP(new InfoMessage("Invitation sent to " + invitedUser + "."));
        } else {
            inviterConn.sendTCP(new InfoMessage("User " + invitedUser + " not found."));
        }
    }
    
    private void updateUserChatRooms(Connection connection) {
        String user = connectionUserMap.get(connection);
        if (user == null) return;

        List<String> rooms = chatRooms.entrySet().stream()
                .filter(entry -> entry.getValue().contains(connection))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (rooms.isEmpty()) {
            connection.sendTCP(new InfoMessage("You are not in any chat rooms."));
        } else {
            connection.sendTCP(new InfoMessage("You are in rooms: " + String.join(", ", rooms)));
        }
    }

    private void sendRoomHistory(Connection conn, String[] parts) {
        if (parts.length != 2) {
            conn.sendTCP(new InfoMessage("Usage: /HISTORY <N>"));
            return;
        }
        String user = connectionUserMap.get(conn);
        String room = userActiveRoomsMap.getOrDefault(user, "GLOBAL");
        int n;
        try {
            n = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            conn.sendTCP(new InfoMessage("Invalid number."));
            return;
        }

        List<ChatMessage> msgs = chatRoomsMessages.getOrDefault(room, Collections.emptyList());
        int start = Math.max(0, msgs.size() - n);
        List<ChatMessage> recent = msgs.subList(start, msgs.size());
        for (ChatMessage m : recent) conn.sendTCP(m);
    }



    private void addMessageToChatRoom(ChatMessage msg) {
        String room = msg.getChatRoom() != null ? msg.getChatRoom() : "GLOBAL";
        chatRoomsMessages.computeIfAbsent(room, k -> new ArrayList<>()).add(msg);
    }

    private void broadcastChatMessage(ChatMessage msg, Connection except) {
        String user = connectionUserMap.get(except);
        String room = userActiveRoomsMap.getOrDefault(user, "GLOBAL");
        for (Map.Entry<String, String> e : userActiveRoomsMap.entrySet()) {
            if (room.equals(e.getValue())) {
                Connection c = userConnectionMap.get(e.getKey());
                if (c != null && c.isConnected() && c != except) c.sendTCP(msg);
            }
        }
    }

    private void broadcastUserListUpdate(String room) {
        List<String> users = userActiveRoomsMap.entrySet().stream()
                .filter(e -> room.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        for (String u : users) {
            Connection c = userConnectionMap.get(u);
            if (c != null && c.isConnected())
                c.sendTCP(new ListUsers(users.toArray(new String[0])));
        }
    }

    private void broadcastInfo(String text) {
        for (Connection c : userConnectionMap.values()) {
            if (c.isConnected()) c.sendTCP(new InfoMessage(text));
        }
    }



    private String[] getAllUsers() {
        return userConnectionMap.keySet().toArray(new String[0]);
    }


    public void start() throws IOException {
        server.start();
        server.bind(portNumber);
        thread = new Thread(this);
        thread.start();
        System.out.println("ChatServer started on port " + portNumber);
    }

    public void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    @Override
    public void run() {
        running = true;
        while (running) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) { }
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java ChatServer <port>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        try {
            new ChatServer(port).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
