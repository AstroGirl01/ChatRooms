package main.java.rs.raf.pds.v4.z5;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

import main.java.rs.raf.pds.v4.z5.messages.*;

public class ChatServer implements Runnable {

    private volatile Thread thread;
    private volatile boolean running;

    private final Server server;
    private final int portNumber;

    private final ConcurrentMap<String, Connection> userConnectionMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<Connection, String> connectionUserMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<Connection>> chatRooms = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> userActiveRoomsMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<ChatMessage>> chatRoomsMessages = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> roomMessageCounters = new ConcurrentHashMap<>();

    
    public ChatServer(int portNumber) {
        this.server = new Server();
        this.portNumber = portNumber;
        KryoUtil.registerKryoClasses(server.getKryo());
        chatRooms.putIfAbsent("PublicChatRoom", new CopyOnWriteArrayList<>());
        registerListener();
        System.out.println("✅ Default room 'PublicChatRoom' created at server startup.");
    }

    private void registerListener() {
        server.addListener(new Listener() {

            @Override
            public void received(Connection connection, Object object) {

                if (object instanceof Login login) {
                    registerUser(login, connection);
                    connection.sendTCP(new InfoMessage("👋 Welcome " + login.getUserName()));
                    listRooms(connection);
                    return;
                }

                if (object instanceof PrivateMessage pm) {
                    handlePrivateMessage(pm, connection);
                    return;
                }

                if (object instanceof ChatMessage chatMessage) {
                    addMessageToChatRoom(chatMessage);
                    broadcastChatMessage(chatMessage, connection);
                    return;
                }

                if (object instanceof String command) {
                    handleCommand(command, connection);
                }
            }

            @Override
            public void disconnected(Connection connection) {
                String user = connectionUserMap.remove(connection);
                if (user != null) userConnectionMap.remove(user);
                broadcastInfo("User " + user + " has disconnected.");
            }
        });
    }

    private void registerUser(Login login, Connection conn) {
        String userName = login.getUserName();
        if (userConnectionMap.containsKey(userName)) {
            Connection old = userConnectionMap.get(userName);
            if (old.isConnected()) old.close();
        }
        chatRooms.get("PublicChatRoom").add(conn);
        userConnectionMap.put(userName, conn);
        connectionUserMap.put(conn, userName);
        userActiveRoomsMap.put(userName, "PublicChatRoom");
        broadcastInfo("User " + userName + " joined the server.");
    }

    private void handlePrivateMessage(PrivateMessage pm, Connection sender) {
        String recipient = pm.getRecipient();
        Connection receiver = userConnectionMap.get(recipient);
        if (receiver == null || !receiver.isConnected()) {
            sender.sendTCP(new InfoMessage("⚠️ User '" + recipient + "' is not online."));
            return;
        }
        receiver.sendTCP(pm);
    }

    private void handleCommand(String command, Connection conn) {
        if (command == null || command.isBlank()) return;

        // sačuvaj originalni unos
        String trimmed = command.trim();
        // koristi upper samo za prepoznavanje komandi
        String upper = trimmed.toUpperCase();

        String[] parts = trimmed.split(" ");

        if (upper.startsWith("/CREATE")) {
            if (parts.length >= 2) createRoom(parts[1], conn);
        }

        else if (upper.startsWith("/LISTROOMS")) {
            listRooms(conn);
        }

        else if (upper.startsWith("/JOIN")) {
            if (parts.length >= 2) joinRoom(parts[1], conn);
        }

        else if (upper.startsWith("/INVITE")) {
            if (parts.length >= 3) {
                String invitedUser = parts[1].replace("@", "").trim();
                String roomName = parts[2].replace("@", "").trim();
                inviteUser(invitedUser, roomName, conn);
            }
        }

        else if (upper.startsWith("/HISTORY")) {
            if (parts.length >= 2) sendRoomHistory(conn, parts);
        }

        else if (upper.startsWith("/GETMOREMESSAGES")) {
            String user = connectionUserMap.get(conn);
            String room = userActiveRoomsMap.getOrDefault(user, "PublicChatRoom");
            sendMoreMessages(room, conn);
        }

        else if (upper.startsWith("/LEAVEROOM")) {
            String user = connectionUserMap.get(conn);
            String currentRoom = userActiveRoomsMap.get(user);
            leaveRoom(currentRoom, user, conn);
        }

        // ⚡ Popravljeno — koristi originalni tekst (ne upper)
        else if (upper.startsWith("/REPLY")) {
            if (parts.length >= 3) replyToMessage(parts, conn);
        }

        else if (upper.startsWith("/EDIT")) {
            if (parts.length >= 3) editMessage(parts, conn);
        }
    }


    private void createRoom(String roomName, Connection conn) {
        chatRooms.putIfAbsent(roomName, new CopyOnWriteArrayList<>());
        broadcastInfo("🆕 New chat room created: " + roomName);
        listRooms(conn);
    }

    private void listRooms(Connection conn) {
        String[] roomNames = chatRooms.keySet().toArray(new String[0]);
        Arrays.sort(roomNames);
        conn.sendTCP(new ListRooms(roomNames));
    }

    private void joinRoom(String roomName, Connection conn) {
        chatRooms.computeIfAbsent(roomName, k -> new CopyOnWriteArrayList<>()).add(conn);
        String user = connectionUserMap.get(conn);
        userActiveRoomsMap.put(user, roomName);
        listRooms(conn);
        broadcastUserListUpdate(roomName);
    }
    
    private void leaveRoom(String roomName, String user, Connection conn) {
        List<Connection> members = chatRooms.get(roomName);
        if (members != null) members.remove(conn);

        userActiveRoomsMap.put(user, ""); 
        broadcastUserListUpdate(roomName);

        conn.sendTCP(new ListUsers(new String[0]));
    }



    private void inviteUser(String invitedUser, String roomName, Connection inviterConn) {
        String inviter = connectionUserMap.get(inviterConn);
        Connection invitedConn = userConnectionMap.get(invitedUser);

        if (invitedConn != null && invitedConn.isConnected()) {
            invitedConn.sendTCP(new InfoMessage("📩 " + inviter + " invites you to join room '" + roomName + "'."));
            inviterConn.sendTCP(new InfoMessage("✅ Invite sent to user " + invitedUser + ", room: '" + roomName + "'."));
        } else {
            inviterConn.sendTCP(new InfoMessage("⚠️ User " + invitedUser + " is not found / offline."));
        }
    }

    private void addReplyMessageToChatRoom(ChatMessage msg) {
        String room = msg.getChatRoom() != null ? msg.getChatRoom() : "PublicChatRoom";
        int index = roomMessageCounters.getOrDefault(room, 0) + 1;
        roomMessageCounters.put(room, index);
        msg.setIndex(index);

        chatRoomsMessages.computeIfAbsent(room, k -> new ArrayList<>()).add(msg);

        List<ChatMessage> list = chatRoomsMessages.get(room);
        if (list.size() > 10) list.remove(0);
    }


    private void sendLastMessages(String roomName, Connection conn) {
        List<ChatMessage> messages = chatRoomsMessages.getOrDefault(roomName, Collections.emptyList());
        if (!messages.isEmpty()) {
            conn.sendTCP(new InfoMessage("📜 Last 10 messages from '" + roomName + "':"));
            int start = Math.max(0, messages.size() - 10);
            List<ChatMessage> last10 = messages.subList(start, messages.size());
            for (ChatMessage msg : last10) conn.sendTCP(msg);
        }
    }
    private void replyToMessage(String[] parts, Connection conn) {
        String user = connectionUserMap.get(conn);
        String room = userActiveRoomsMap.getOrDefault(user, "PublicChatRoom");

        int index;
        try {
            index = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            conn.sendTCP(new InfoMessage("⚠️ Invalid message number."));
            return;
        }

        List<ChatMessage> messages = chatRoomsMessages.get(room);
        if (messages == null || index < 1 || index > messages.size()) {
            conn.sendTCP(new InfoMessage("⚠️ Message with index " + index + " not found."));
            return;
        }

        ChatMessage original = messages.get(index - 1);
        String replyText = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));

        // citirani deo originalne poruke
        String excerpt = original.getTxt().length() > 40 ?
                original.getTxt().substring(0, 40) + "..." : original.getTxt();

        ChatMessage replyMsg = new ChatMessage(
        	    user,
        	    "💬 Reply to " + original.getUser() + ": \"" + excerpt + "\"\n" + replyText,
        	    room
        	);


        addMessageToChatRoom(replyMsg);
        broadcastChatMessage(replyMsg, conn);
    }

    
    private void editMessage(String[] parts, Connection conn) {
        String user = connectionUserMap.get(conn);
        String room = userActiveRoomsMap.getOrDefault(user, "PublicChatRoom");

        int index;
        try {
            index = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            conn.sendTCP(new InfoMessage("⚠️ Invalid message number."));
            return;
        }

        List<ChatMessage> messages = chatRoomsMessages.get(room);
        if (messages == null || index < 1 || index > messages.size()) {
            conn.sendTCP(new InfoMessage("⚠️ Message not found."));
            return;
        }

        ChatMessage oldMsg = messages.get(index - 1);
        if (!oldMsg.getUser().equals(user)) {
            conn.sendTCP(new InfoMessage("❌ You can only edit your own messages."));
            return;
        }

        String newText = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
        oldMsg.setTxt(newText + " (Edited)");

        for (Connection c : chatRooms.getOrDefault(room, Collections.emptyList())) {
            if (c != null && c.isConnected()) {
                c.sendTCP(oldMsg);
            }
        }
    }

    private void sendRoomHistory(Connection conn, String[] parts) {
        String user = connectionUserMap.get(conn);
        String room = userActiveRoomsMap.getOrDefault(user, "PublicChatRoom");

        int n = Integer.parseInt(parts[1]);
        List<ChatMessage> msgs = chatRoomsMessages.getOrDefault(room, Collections.emptyList());
        if (msgs.isEmpty()) {
            conn.sendTCP(new InfoMessage("⚠️ No messages found in this room."));
            return;
        }

        int start = Math.max(0, msgs.size() - n);
        List<ChatMessage> lastMsgs = msgs.subList(start, msgs.size());

        conn.sendTCP(new InfoMessage("📜 Last " + lastMsgs.size() + " messages from '" + room + "':"));
        for (ChatMessage m : lastMsgs) conn.sendTCP(m);
    }


    private void sendMoreMessages(String roomName, Connection conn) {
        List<ChatMessage> messages = chatRoomsMessages.getOrDefault(roomName, Collections.emptyList());
        if (messages.isEmpty()) {
            conn.sendTCP(new InfoMessage("⚠️ No additonal messages from room '" + roomName + "'."));
            return;
        }

        conn.sendTCP(new InfoMessage("📜 Loading additional messages from room '" + roomName + "':"));
        int start = Math.max(0, messages.size() - 20); 
        List<ChatMessage> moreMsgs = messages.subList(start, messages.size());
        for (ChatMessage msg : moreMsgs) conn.sendTCP(msg);
    }

    private void addMessageToChatRoom(ChatMessage msg) {
        String room = msg.getChatRoom() != null ? msg.getChatRoom() : "PublicChatRoom";
        int index = roomMessageCounters.getOrDefault(room, 0) + 1;
        roomMessageCounters.put(room, index);

        msg.setIndex(index); // novo polje u ChatMessage klasi
        chatRoomsMessages.computeIfAbsent(room, k -> new ArrayList<>()).add(msg);

        List<ChatMessage> list = chatRoomsMessages.get(room);
        if (list.size() > 10) list.remove(0);
    }

    private void broadcastChatMessage(ChatMessage msg, Connection except) {
        String user = connectionUserMap.get(except);
        String room = userActiveRoomsMap.getOrDefault(user, "PublicChatRoom");
        for (Map.Entry<String, String> e : userActiveRoomsMap.entrySet()) {
            if (room.equals(e.getValue())) {
                Connection c = userConnectionMap.get(e.getKey());
                if (c != null && c.isConnected()) c.sendTCP(msg);
            }
        }
    }

    private void broadcastInfo(String text) {
        for (Connection c : userConnectionMap.values()) {
            if (c.isConnected()) c.sendTCP(new InfoMessage(text));
        }
    }

    private void broadcastUserListUpdate(String roomName) {
        List<String> usersInRoom = userActiveRoomsMap.entrySet().stream()
                .filter(entry -> roomName.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (String username : usersInRoom) {
            Connection c = userConnectionMap.get(username);
            if (c != null && c.isConnected()) {
                c.sendTCP(new ListUsers(usersInRoom.toArray(new String[0])));
            }
        }
    }

    public void start() throws IOException {
        try {
            server.start();
            server.bind(portNumber);
            thread = new Thread(this);
            thread.start();
            System.out.println("ChatServer started on port " + portNumber);
        } catch (IOException e) {
            System.err.println("⚠️ Port " + portNumber + " is busy. Try another one.");
            throw e;
        }
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
            } catch (InterruptedException ignored) {}
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
