package rs.raf.pds.v4.z5;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;

import rs.raf.pds.v4.z5.messages.ChatMessage;
import rs.raf.pds.v4.z5.messages.InfoMessage;
import rs.raf.pds.v4.z5.messages.KryoUtil;
import rs.raf.pds.v4.z5.messages.ListUsers;
import rs.raf.pds.v4.z5.messages.Login;
import rs.raf.pds.v4.z5.messages.WhoRequest;

public class ChatClient implements Runnable {

    public static int DEFAULT_CLIENT_READ_BUFFER_SIZE = 1000000;
    public static int DEFAULT_CLIENT_WRITE_BUFFER_SIZE = 1000000;

    private volatile Thread thread = null;
    volatile boolean running = false;

    final Client client;
    final String hostName;
    final int portNumber;
    final String userName;

    public ChatClient(String hostName, int portNumber, String userName) {
        this.client = new Client(DEFAULT_CLIENT_WRITE_BUFFER_SIZE, DEFAULT_CLIENT_READ_BUFFER_SIZE);
        this.hostName = hostName;
        this.portNumber = portNumber;
        this.userName = userName;

        KryoUtil.registerKryoClasses(client.getKryo());
        registerListener();
    }

    private void registerListener() {
        client.addListener(new Listener() {
            public void connected(Connection connection) {
                Login loginMessage = new Login(userName);
                client.sendTCP(loginMessage);
            }

            public void received(Connection connection, Object object) {
                if (object instanceof ChatMessage) {
                    ChatMessage chatMessage = (ChatMessage) object;
                    showChatMessage(chatMessage);
                    return;
                }

                if (object instanceof ListUsers) {
                    ListUsers listUsers = (ListUsers) object;
                    showOnlineUsers(listUsers.getUsers());
                    return;
                }

                if (object instanceof InfoMessage) {
                    InfoMessage message = (InfoMessage) object;
                    showMessage("Server: " + message.getTxt());
                    return;
                }
            }

            public void disconnected(Connection connection) {
                showMessage("Disconnected from server.");
            }
        });
    }

    private void showChatMessage(ChatMessage chatMessage) {
        System.out.println(chatMessage.getUser() + ": " + chatMessage.getTxt());
    }

    private void showMessage(String txt) {
        System.out.println(txt);
    }

    private void showOnlineUsers(String[] users) {
        System.out.print("Server (online users): ");
        for (int i = 0; i < users.length; i++) {
            String user = users[i];
            System.out.print(user);
            System.out.printf((i == users.length - 1 ? "\n" : ", "));
        }
    }

    public void start() throws IOException {
        client.start();
        connect();

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

    public void connect() throws IOException {
        client.connect(1000, hostName, portNumber);
    }

    @Override
    public void run() {
        try (BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))) {
            String userInput;
            running = true;

            while (running) {
                userInput = stdIn.readLine();
                if (userInput == null || "BYE".equalsIgnoreCase(userInput)) {
                    running = false;
                } else if ("WHO".equalsIgnoreCase(userInput)) {
                    client.sendTCP(new WhoRequest());
                } else {
                    // ✅ NOVA LOGIKA: prepoznaje privatne i grupne poruke
                    if (userInput.trim().startsWith("@")) {
                        ChatMessage privateMsg = new ChatMessage(userName, userInput.trim());
                        client.sendTCP(privateMsg);
                    } else {
                        ChatMessage message = new ChatMessage(userName, userInput);
                        client.sendTCP(message);
                    }
                }

                if (!client.isConnected() && running)
                    connect();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            running = false;
            System.out.println("CLIENT SE DISCONNECTUJE");
            client.close();
        }
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java -jar chatClient.jar <host name> <port number> <username>");
            System.out.println("Recommended port number is 54555");
            System.exit(1);
        }

        String hostName = args[0];
        int portNumber = Integer.parseInt(args[1]);
        String userName = args[2];

        try {
            ChatClient chatClient = new ChatClient(hostName, portNumber, userName);
            chatClient.start();

            System.out.println("\nUputstvo:");
            System.out.println("- Javna poruka: samo upisi tekst i Enter");
            System.out.println("- Privatna poruka: @korisnik tekst");
            System.out.println("- Multicast (grupa): @korisnik1,korisnik2 tekst");
            System.out.println("- WHO za spisak korisnika");
            System.out.println("- BYE za izlaz\n");

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error: " + e.getMessage());
            System.exit(-1);
        }
    }
}
