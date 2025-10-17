package rs.raf.pds.v5.z1;

import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Color.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ServerMessageSocket {

    public static void main(String[] args) throws IOException {

        if (args.length != 1) {
            System.err.println("Usage: java ServerMessageSocket <port number>");
            System.exit(1);
        }

        int portNumber = Integer.parseInt(args[0]);

        try (
            ServerSocket serverSocket = new ServerSocket(portNumber);
            Socket clientSocket = serverSocket.accept();
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()));
        ) {
            AnsiConsole.systemInstall();

            System.out.println(ansi().fg(RED).a("Server osluškuje port: " + portNumber).reset());

            ObjectInputStream objectStream = new ObjectInputStream(clientSocket.getInputStream());
            Input kryoInput = new Input(clientSocket.getInputStream());
            Kryo kryo = new Kryo();
            KryoUtil.registerKryoClasses(kryo);

            ObjectMapper jsonMapper = new ObjectMapper();
            jsonMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);

            System.out.println("Username: " + in.readLine());

            out.println("Tip serijalizacije: [S] Java, [K] Kryo, [J] JSON String, [B] JSON Binary ?");
            String serType = in.readLine().toUpperCase();

            switch (serType) {
                case "S": System.out.println("Java Serijalizacija: "); break;
                case "K": System.out.println("Kryo Serijalizacija: "); break;
                case "J": System.out.println("JSON String Serijalizacija: "); break;
                case "B": System.out.println("JSON Binary Serijalizacija: "); break;
                default:  System.out.println("String poruke: "); break;
            }

            boolean running = true;
            Message m;
            byte[] jsonBytes = new byte[1000];

            while (running) {

                if (serType == null)
                    break;

                else if ("S".equals(serType)) {
                    Object o = objectStream.readObject();
                    if (o instanceof Message) {
                        m = (Message) o;
                        ispisPoruke(m, YELLOW);
                    }
                }
                else if ("J".equals(serType)) {
                    String jsonString = in.readLine();
                    m = jsonMapper.readValue(jsonString, Message.class);
                    ispisPoruke(m, GREEN);
                }
                else if ("B".equals(serType)) {
                    clientSocket.getInputStream().read(jsonBytes);
                    m = jsonMapper.readValue(jsonBytes, Message.class);
                    ispisPoruke(m, CYAN);
                }
                else if ("K".equals(serType)) {
                    m = kryo.readObject(kryoInput, Message.class);
                    ispisPoruke(m, RED);
                }
                else {
                    m = new Message(0, "NoName User");
                    m.tipPoruke = "STRING";
                    m.poruka = in.readLine();
                    ispisPoruke(m, BLUE);
                }

                System.out.println(">");
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Exception caught when trying to listen on port " + portNumber);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static void ispisPoruke(Message m, Ansi.Color color) {
        System.out.println(ansi().fg(color).a(m.tipPoruke + ", Message #" + m.id + ":").reset());
        System.out.println(m.user + ": " + m.poruka);
    }
}
