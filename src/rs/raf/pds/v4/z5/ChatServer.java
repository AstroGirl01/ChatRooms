package rs.raf.pds.v4.z5;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

import rs.raf.pds.v4.z5.messages.ChatMessage;
import rs.raf.pds.v4.z5.messages.InfoMessage;
import rs.raf.pds.v4.z5.messages.KryoUtil;
import rs.raf.pds.v4.z5.messages.ListUsers;
import rs.raf.pds.v4.z5.messages.Login;
import rs.raf.pds.v4.z5.messages.WhoRequest;

public class ChatServer implements Runnable {

    private volatile Thread thread = null;
    volatile boolean running = false;

    final Server server;
    final int portNumber;

    ConcurrentMap<String, Connection> userConnectionMap = new ConcurrentHashMap<>();
    ConcurrentMap<Connection, String> connectionUserMap = new ConcurrentHashMap<>();

    // Istorija svih poruka
    List<ChatMessage> messageHistory = Collections.synchronizedList(new ArrayList<>());

    public ChatServer(int portNumber) {
        this.server = new Server();
        this.portNumber = portNumber;
        KryoUtil.registerKryoClasses(server.getKryo());
        registerListener();
    }

    private void registerListener() {
        server.addListener(new Listener() {
            public void received(Connection connection, Object object) {
                if (object instanceof Login) {
                    Login login = (Login) object;
                    newUserLogged(login, connection);
                    connection.sendTCP(new InfoMessage("Hello " + login.getUserName()));
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return;
                }

                if (object instanceof ChatMessage) {
                    ChatMessage chatMessage = (ChatMessage) object;
                    System.out.println(chatMessage.getUser() + ": " + chatMessage.getTxt());
                    messageHistory.add(chatMessage);

                    if (chatMessage.getTxt().startsWith("@")) {
                        handlePrivateOrMulticast(chatMessage, connection);
                    } else {
                        broadcastChatMessage(chatMessage, connection);
                    }
                    return;
                }

                if (object instanceof WhoRequest) {
                    ListUsers listUsers = new ListUsers(getAllUsers());
                    connection.sendTCP(listUsers);
                    return;
                }
            }

            public void disconnected(Connection connection) {
                String user = connectionUserMap.get(connection);
                connectionUserMap.remove(connection);
                userConnectionMap.remove(user);
                showTextToAll(user + " has disconnected!", connection);
            }
        });
    }

    // Privatne i grupne poruke
    private void handlePrivateOrMulticast(ChatMessage message, Connection senderConn) {
        String text = message.getTxt().trim();
        String fromUser = message.getUser();

        int firstSpace = text.indexOf(' ');
        if (firstSpace == -1) return;

        String toPart = text.substring(1, firstSpace);
        String msgText = text.substring(firstSpace + 1);
        String[] recipients = toPart.split(",");

        for (String r : recipients) {
            r = r.trim();
            Connection targetConn = userConnectionMap.get(r);
            if (targetConn != null && targetConn.isConnected()) {
                ChatMessage privateMsg = new ChatMessage(fromUser, "[priv] " + msgText);
                targetConn.sendTCP(privateMsg);
            } else {
                senderConn.sendTCP(new InfoMessage("User " + r + " not found or offline."));
            }
        }
    }

    String[] getAllUsers() {
        String[] users = new String[userConnectionMap.size()];
        int i = 0;
        for (String user : userConnectionMap.keySet()) {
            users[i] = user;
            i++;
        }
        return users;
    }

    void newUserLogged(Login loginMessage, Connection conn) {
        userConnectionMap.put(loginMessage.getUserName(), conn);
        connectionUserMap.put(conn, loginMessage.getUserName());
        showTextToAll("User " + loginMessage.getUserName() + " has connected!", conn);
    }

    private void broadcastChatMessage(ChatMessage message, Connection exception) {
        for (Connection conn : userConnectionMap.values()) {
            if (conn.isConnected() && conn != exception)
                conn.sendTCP(message);
        }
    }

    private void showTextToAll(String txt, Connection exception) {
        System.out.println(txt);
        for (Connection conn : userConnectionMap.values()) {
            if (conn.isConnected() && conn != exception)
                conn.sendTCP(new InfoMessage(txt));
        }
    }

    public void start() throws IOException {
        server.start();
        server.bind(portNumber);

        if (thread == null) {
            thread = new Thread(this);
            thread.start();
        }
    }

    public void stop() {
        Thread stopThread = thread;
        thread = null;
        running = false;
        if (stopThread != null)
            stopThread.interrupt();
    }

    @Override
    public void run() {
        running = true;
        while (running) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar chatServer.jar <port number>");
            System.out.println("Recommended port number is 54555");
            System.exit(1);
        }

        int portNumber = Integer.parseInt(args[0]);
        try {
            ChatServer chatServer = new ChatServer(portNumber);
            chatServer.start();
            chatServer.thread.join();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }
}
