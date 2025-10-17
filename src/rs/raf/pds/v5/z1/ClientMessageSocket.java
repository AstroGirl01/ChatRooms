package rs.raf.pds.v5.z1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.Socket;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;

public class ClientMessageSocket implements Runnable{
	
	private volatile Thread thread = null;
	
	Socket socket;
	volatile boolean running = false;
	final String userName;
	
	public ClientMessageSocket(Socket socket, String userName) {
		this.socket = socket;
		this.userName = userName;
	}
	
	public void start() {
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
		try (
	         PrintWriter out =
	         	new PrintWriter(socket.getOutputStream(), true);
	         BufferedReader in =
	                new BufferedReader(
	                    new InputStreamReader(socket.getInputStream()));
	         BufferedReader stdIn =
	               new BufferedReader(
	                    new InputStreamReader(System.in))	// Za čitanje sa standardnog ulaza - tastature!
	        ) {
				ObjectOutputStream objectStream = new ObjectOutputStream(socket.getOutputStream());
							
				ObjectMapper jsonMapper = new ObjectMapper(); 
				jsonMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
			
				Kryo kryo = new Kryo();
				KryoUtil.registerKryoClasses(kryo);
				Output kryoOutput = new Output(socket.getOutputStream());
				
				out.println(userName);	// Login 
				System.out.println("Server:"+in.readLine());
	            String serType = stdIn.readLine();
	            out.println(serType);
	            
				String userInput;
				running = true;
				int counter = 0;
				
	            while (running) {
	            	userInput = stdIn.readLine();
	            	if (userInput == null || "BYE".equalsIgnoreCase(userInput))// userInput - tekst koji je unet sa tastature!
	            	{
	            		userInput = "Bye";
	            		running = false;
	            	}
	            	else if ("J".equalsIgnoreCase(serType)) {
	            		Message m = new Message(++counter, userName);
	            		m.tipPoruke = "JSON String";
	            		m.poruka = userInput;
	            		String jsonString = jsonMapper.writeValueAsString(m);
	            		out.println(jsonString);
	            		out.flush();
	            	}
	            	else if ("B".equalsIgnoreCase(serType)) {
	            		Message m = new Message(++counter, userName);
	            		m.tipPoruke = "JSON Binary";
	            		m.poruka = userInput;
	            		byte[] jsonBytes = jsonMapper.writeValueAsBytes(m);
	            		socket.getOutputStream().write(jsonBytes);
	            		socket.getOutputStream().flush();
	            	}
	            	else if ("S".equalsIgnoreCase(serType)) {
	            		Message m = new Message(++counter, userName);
	            		m.tipPoruke = "Java Serializable";
	            		m.poruka = userInput;
	            		objectStream.writeObject(m);
	            		objectStream.flush();
	            	}
	            	else if ("K".equalsIgnoreCase(serType)) {
	            		Message m = new Message(++counter, userName);
	            		m.tipPoruke = "Kryo";
	            		m.poruka = userInput;
	            		kryo.writeObject(kryoOutput, m);
	            		kryoOutput.flush();
	            			
	            	}
	            	else {
	            		out.println(userInput);							// Slanje unetog teksta ka serveru
	            		out.flush();
	            	}
	            	
	            }
	            
	    } catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			try {
				running = false;
				socket.close();
			} catch (IOException e) {
				
				e.printStackTrace();
			}
		}
		
	}

	public static void main(String[] args) {
		if (args.length != 3) {
            System.err.println(
                "Usage: java ClientMessageSocket <host name> <port number> <username>");
            System.exit(1);
        }
 
        String hostName = args[0];
        int portNumber = Integer.parseInt(args[1]);
        String userName = args[2];
        
        try{
             Socket socket = new Socket(hostName, portNumber);
        	
             ClientMessageSocket client = new ClientMessageSocket(socket, userName);
        	 client.start();
        	 
        	 client.thread.join();
         } catch (IOException e) {
           System.err.println("Couldn't get I/O for the connection to " +
                    hostName);
                
         } catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
        System.exit(1);
	}
}
