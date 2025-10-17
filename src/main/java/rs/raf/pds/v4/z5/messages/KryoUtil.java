package main.java.rs.raf.pds.v4.z5.messages;

import java.util.ArrayList;

import com.esotericsoftware.kryo.Kryo;

import main.java.rs.raf.pds.v4.z5.messages.ChatMessage;
import main.java.rs.raf.pds.v4.z5.messages.InfoMessage;
import main.java.rs.raf.pds.v4.z5.messages.ListUsers;
import main.java.rs.raf.pds.v4.z5.messages.Login;
import main.java.rs.raf.pds.v4.z5.messages.PrivateMessage;
import main.java.rs.raf.pds.v4.z5.messages.WhoRequest;

public class KryoUtil {
	public static void registerKryoClasses(Kryo kryo) {
		kryo.register(String.class);
		kryo.register(String[].class);
		kryo.register(ArrayList.class);
		kryo.register(Login.class);
		kryo.register(ChatMessage.class);
		kryo.register(WhoRequest.class);
		kryo.register(ListUsers.class);
		kryo.register(InfoMessage.class);
		kryo.register(PrivateMessage.class);
		
	}
}
