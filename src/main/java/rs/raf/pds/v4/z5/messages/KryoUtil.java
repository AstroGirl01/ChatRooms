package main.java.rs.raf.pds.v4.z5.messages;

import java.util.ArrayList;
import java.time.LocalDateTime;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;


import main.java.rs.raf.pds.v4.z5.messages.ChatMessage;
import main.java.rs.raf.pds.v4.z5.messages.InfoMessage;
import main.java.rs.raf.pds.v4.z5.messages.ListUsers;
import main.java.rs.raf.pds.v4.z5.messages.Login;
import main.java.rs.raf.pds.v4.z5.messages.PrivateMessage;

public class KryoUtil {
	public static void registerKryoClasses(Kryo kryo) {
		kryo.register(String.class);
		kryo.register(String[].class);
		kryo.register(ArrayList.class);
		kryo.register(Login.class);
		kryo.register(ChatMessage.class);
		kryo.register(ListUsers.class);
		kryo.register(InfoMessage.class);
		kryo.register(PrivateMessage.class);
		kryo.register(ListRooms.class);

		
		kryo.register(LocalDateTime.class, new Serializer<LocalDateTime>() {
		    @Override
		    public void write(Kryo kryo, Output output, LocalDateTime obj) {
		        output.writeString(obj == null ? null : obj.toString());
		    }

		    @Override
		    public LocalDateTime read(Kryo kryo, Input input, Class<LocalDateTime> type) {
		        String value = input.readString();
		        return (value == null ? null : LocalDateTime.parse(value));
		    }
		});

	}
}
