# JAVA-ChatRooms
Java ChatRooms GUI app

This is an enhanced chat application developed as part of an exercise/exam. Users can send private messages, create chat rooms, and perform various actions on the chat server.

## 🚀 Features Overview

| Feature | Description |
|----------|-------------|
| 🧑‍💻 Multi-user chat | Multiple users can connect and chat in real time |
| 🏠 Chat rooms | Users can create, join, and leave custom rooms |
| 💬 Reply to messages | Replies appear with an excerpt of the original message |
| ✏️ Edit messages | Edit your own messages directly via GUI |
| 📩 Private messages | Send direct messages using `@username` |
| 👥 User list | Shows only users present in the active room |
| 🌐 GUI-based control | All interactions available via buttons and right-click menus |
| 🧰 KryoNet | Efficient client-server communication |
| 🖥️ JavaFX UI | Clean and modern interface for all chat actions |

---
# 📘 How to Use the Chat Application

This section provides clear instructions on how to use every feature implemented in the Java ChatRooms project.

---
## 🟢 1. Logging in

- When you start the application (`App.java`), you will be prompted to enter your **username**.
- After logging in, you are automatically added to the default room **PublicChatRoom**.
- The active room is shown with a ✅ icon in the right panel.
- Active user and active room are shown at the bottom of the left panel.

---

## 💬 2. Sending a Public Message

- Simply type your message in the text field at the bottom and click **“Send”**.
- The message will be sent to everyone in the **active chat room**.

## ✉️ 3. Sending a Private Message

- To send a private message to a specific user, use the **@username** prefix.
- Example:  @User1 Hello!
- Only **User1** will receive this message.

---
## 👥 4. Sending a Group / Multicast Message

- To send a message to multiple users at once, use curly brackets `@{}` to list recipients.
- Example:  @{User1,User2,User3} Hej svima!
- The message will be delivered to all users listed inside the brackets.

---
## 🏠 5. Chat Rooms Management

### ➕ Create a Chat Room
- Click **“Create Room”** on the right panel.
- Enter a room name (for example `room1`).
- The new room will automatically appear in the list.

### 🔁 List All Rooms
- Click **“Refresh Rooms”** to view all available rooms.
- Default room: **PublicChatRoom**

### 🚪 Join a Room
- Select a room from the list and click **“Join room”**.
- You will automatically receive the **last 10 messages** from that room.
- The joined room becomes active and is marked with ✅.

### 🚪 Leave a Room
- You can click on **”Leave Room”** and then User that is active will automaticaly be disconnected from that room. Active room is now set to (none).
---

## 📩 6. Inviting Other Users to a Room

- To invite another user to join a room, type: `/INVITE @user_name @room_name`
- Example: /INVITE @kristina @soba1
- The invited user will receive a message like:
> 📩 User1 invites you to join room 'room1'.

## 🕓 7. Viewing Older Messages (History)

- By default, you receive the **last 10 messages** when joining a room.
- To load additional messages, use: /HISTORY 20
- This will display the last 20 messages from the current room.

---

## 💬 8. Replying to a Message

- To reply to someone’s message, right-click (in GUI) 

- The reply will include a short excerpt of the original message (e.g. “Reply to: User1 – ‘Hello!’”).

---

## ✏️ 9. Editing a Message

- To edit one of your previous messages, use right-click (in GUI)
- Users can edit their messages by using right-click and clicking on **Edit** button next to a message, and writing a new one. The pop-up screen will appear and when you are ready click 'OK'. 
- The updated message will be re-sent to all users in the room with a note: (Edited)

---

## 🧑‍🤝‍🧑 10. Viewing Active Users in a Room

- The left panel “Users” always shows **only users who are in the same active room**.
- When a user joins or leaves a room, the list automatically updates.

---

## 🧭 11. Active User and Room

- Below the user list you will always see: 
- Active Users: <username>
- Active : <naziv>
- The active room is also marked with ✅ in the right panel.

---
## 🧭 11. Active User and Room

- When you type /INVITE 'username' 'room' this User gets invited to join a room
- Server will write a message from User that invites you.
- Example:

- The active room is also marked with ✅ in the right panel.

---

---

## 🧰 Technical Notes

- Default room: **PublicChatRoom**
- KryoNet is used for TCP networking.
- Messages are represented by the classes: `ChatMessage`, `PrivateMessage`, `ReplyMessage`, and `InfoMessage`.
- All messages are automatically serialized via `KryoUtil.java`.



## Technologies Used

- Java
- JavaFX (for the graphical user interface)
- [KryoNet](https://github.com/EsotericSoftware/kryonet) (for network communication)

## Usage

1. Clone the repository:

```bash
git clone https://github.com/AstroGirl01/ChatRooms
cd ChatRooms
```
2. Build and run application. Provided .bat files for ease of usage.
   
