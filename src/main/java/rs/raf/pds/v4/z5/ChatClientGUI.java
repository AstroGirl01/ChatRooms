package main.java.rs.raf.pds.v4.z5;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import main.java.rs.raf.pds.v4.z5.messages.ChatMessage;

import java.io.IOException;
import java.util.*;

public class ChatClientGUI extends Application implements ChatClient.ChatMessageCallback {

    private String activeUsername = null;
    private final Map<String, ChatClient> clients = new HashMap<>();
    private ChatClient activeClient;

    private ListView<String> messagesList;
    private TextField inputField;
    private ListView<String> userList;
    private ListView<String> roomList;
    private Label activeUserLabel;
    private Label activeRoomLabel;

    private String activeRoom = "PublicChatRoom";
    private final String initialUser;

    public ChatClientGUI(String username) {
        this.initialUser = username;
    }

    @Override
    public void start(Stage stage) {
        messagesList = new ListView<>();
        messagesList.setFocusTraversable(false);

        messagesList.setCellFactory(list -> {
            ListCell<String> cell = new ListCell<>() {
                @Override
                protected void updateItem(String msg, boolean empty) {
                    super.updateItem(msg, empty);
                    if (empty || msg == null) {
                        setText(null);
                        setGraphic(null);
                        setStyle("");
                        return;
                    }

                    if (msg.contains("💬 Reply to")) {
                        String[] parts = msg.split("\n", 2);
                        String quote = parts[0].trim();
                        String replyText = parts.length > 1 ? parts[1].trim() : "";

                        javafx.scene.text.Text quoteText = new javafx.scene.text.Text(quote + "\n");
                        quoteText.setStyle("-fx-fill: #666; -fx-font-style: italic;");

                        javafx.scene.text.Text replyTextNode = new javafx.scene.text.Text(replyText);
                        replyTextNode.setStyle("-fx-fill: black; -fx-font-style: normal;");

                        javafx.scene.text.TextFlow textFlow = new javafx.scene.text.TextFlow(quoteText, replyTextNode);
                        textFlow.setLineSpacing(2);
                        textFlow.setPadding(new Insets(2, 0, 2, 5));
                        textFlow.setStyle("-fx-background-color: #fafafa; "
                                + "-fx-border-color: #ccc; -fx-border-width: 0 0 0 2; "
                                + "-fx-border-insets: 0 0 0 5;");

                        setGraphic(textFlow);
                        setText(null);
                        return;
                    }

                    if (msg.contains("(Edited)")) {
                        String base = msg.replace("(Edited)", "").trim();

                        javafx.scene.text.Text normalPart = new javafx.scene.text.Text(base + " ");
                        normalPart.setStyle("-fx-fill: black; -fx-font-style: normal;");

                        javafx.scene.text.Text editedPart = new javafx.scene.text.Text("(Edited)");
                        editedPart.setStyle("-fx-fill: #888; -fx-font-style: italic;");

                        javafx.scene.text.TextFlow textFlow = new javafx.scene.text.TextFlow(normalPart, editedPart);
                        textFlow.setLineSpacing(2);
                        setGraphic(textFlow);
                        setText(null);
                        return;
                    }


                    setText(msg);
                    setStyle("-fx-text-fill: black; -fx-font-style: normal;");
                    setGraphic(null);
                }
            };

            ContextMenu menu = new ContextMenu();
            MenuItem replyItem = new MenuItem("💬 Reply");
            MenuItem editItem = new MenuItem("✏️ Edit");

            // --- REPLY ---
            replyItem.setOnAction(e -> {
                String selected = cell.getItem();
                if (selected == null) return;

                int index = extractMessageIndex(selected);
                String cleanText = selected.replaceAll("^\\(\\d+\\).*?:\\s*", "").trim();

                Dialog<String> replyDialog = new Dialog<>();
                replyDialog.setTitle("Reply to Message");
                replyDialog.setHeaderText("Replying to:\n" + cleanText);

                TextField replyField = new TextField();
                replyField.setPromptText("Enter your reply...");
                replyDialog.getDialogPane().setContent(replyField);
                replyDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

                replyDialog.setResultConverter(button -> button == ButtonType.OK ? replyField.getText() : null);
                replyDialog.showAndWait().ifPresent(replyText -> {
                    if (replyText != null && !replyText.isBlank()) {
                        activeClient.processUserInput("/REPLY " + index + " " + replyText, activeRoom);
                    }
                });
            });

            // --- EDIT ---
            editItem.setOnAction(e -> {
                String selected = cell.getItem();
                if (selected == null) return;

                boolean isMyMessage = selected.contains("] (" + activeRoom + ") " + activeUsername + ":");
                if (!isMyMessage) {
                    showError("⚠️ You can only edit your own messages.");
                    return;
                }

                String oldText = selected.replaceAll(".*?:\\s*", "").replace("(Edited)", "").trim();
                int index = extractMessageIndex(selected);

                TextInputDialog editDialog = new TextInputDialog(oldText);
                editDialog.setTitle("Edit Message");
                editDialog.setHeaderText("Edit your message:");
                editDialog.setContentText("Message text:");

                Optional<String> result = editDialog.showAndWait();
                result.ifPresent(newText -> {
                    if (!newText.equals(oldText)) {

                        activeClient.processUserInput("/EDIT " + index + " " + newText, activeRoom);


                        Platform.runLater(() -> {
                            List<String> items = new ArrayList<>(messagesList.getItems());
                            for (int i = 0; i < items.size(); i++) {
                                if (items.get(i).contains("(" + index + ")")) {
                                    String updated = items.get(i).replace(oldText, newText + " (Edited)");
                                    items.set(i, updated);
                                    break;
                                }
                            }
                            messagesList.getItems().setAll(items);
                        });
                    }
                });
            });

            menu.getItems().addAll(replyItem, editItem);
            cell.setContextMenu(menu);

            return cell;
        });


        inputField = new TextField();
        inputField.setPromptText("Type text...");
        Button sendBtn = new Button("Send");
        sendBtn.setOnAction(e -> sendMessage());
        HBox bottom = new HBox(10, inputField, sendBtn);
        bottom.setPadding(new Insets(10));

        // === Lista korisnika ===
        userList = new ListView<>();
        userList.setPrefWidth(160);
        userList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String user, boolean empty) {
                super.updateItem(user, empty);

                if (empty || user == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                setText(user);
                if (activeClient != null && user.equals(activeClient.getUserName())) {
                    setStyle("-fx-text-fill: #2e8b57; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-text-fill: #444; -fx-font-weight: normal;");
                }

                setOnMouseEntered(e -> setStyle(getStyle() + " -fx-background-color: #f0f0f0;"));
                setOnMouseExited(e -> {
                    if (activeClient != null && user.equals(activeClient.getUserName())) {
                        setStyle("-fx-text-fill: #2e8b57; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #444; -fx-font-weight: normal;");
                    }
                });
            }
        });

        Button changeUserBtn = new Button("Change User");
        changeUserBtn.setOnAction(e -> changeActiveUser());

        activeUserLabel = new Label("Active user: (none)");
        activeRoomLabel = new Label("Active room: PublicChatRoom");

        VBox left = new VBox(10,
                new Label("Users:"),
                userList,
                changeUserBtn,
                activeUserLabel,
                activeRoomLabel
        );
        left.setPadding(new Insets(10));
        left.setAlignment(Pos.TOP_CENTER);

        // === Lista soba ===
        roomList = new ListView<>();
        roomList.setPrefWidth(220);
        roomList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String room, boolean empty) {
                super.updateItem(room, empty);
                if (empty || room == null) {
                    setText(null);
                    setStyle("");
                } else {
                    if (room.equals(activeRoom)) {
                        setText("✅ " + room);
                        setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    } else {
                        setText(room);
                        setStyle("");
                    }
                }
            }
        });

        Button createRoomBtn = new Button("Create Room");
        createRoomBtn.setOnAction(e -> createRoomDialog());

        Button joinRoomBtn = new Button("Join Room");
        joinRoomBtn.setOnAction(e -> joinSelectedRoom());

        Button leaveRoomBtn = new Button("Leave Room");
        leaveRoomBtn.setOnAction(e -> leaveCurrentRoom());

        Button refreshRoomsBtn = new Button("Refresh Rooms");
        refreshRoomsBtn.setOnAction(e -> refreshRooms());

        VBox right = new VBox(10,
                new Label("Chat rooms:"),
                roomList,
                createRoomBtn,
                joinRoomBtn,
                leaveRoomBtn,
                refreshRoomsBtn
        );
        right.setPadding(new Insets(10));
        right.setAlignment(Pos.TOP_CENTER);

        // === Layout ===
        BorderPane root = new BorderPane(messagesList, null, right, bottom, left);
        Scene scene = new Scene(root, 1050, 580);
        scene.getStylesheets().add("styles.css");

        stage.setTitle("Chat Control Panel");
        stage.setScene(scene);
        stage.show();

        connectUser(initialUser);
        userList.getSelectionModel().selectFirst();
        activeRoom = "PublicChatRoom";
        activeRoomLabel.setText("Active room: " + activeRoom);
        refreshRooms();
    }

    // === Room Management ===
    private void createRoomDialog() {
        if (activeClient == null) {
            showError("No active user!");
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New chat room");
        dialog.setHeaderText("Enter the name of the new room:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            activeClient.createRoom(name);
            Platform.runLater(() -> {
                messagesList.getItems().add("Room '" + name + "' created successfully.");
                refreshRooms();
            });
        });
    }

    private void joinSelectedRoom() {
        if (activeClient == null) {
            showError("No active user!");
            return;
        }
        String selectedRoom = roomList.getSelectionModel().getSelectedItem();
        if (selectedRoom == null) {
            showError("Please select a room from the list first.");
            return;
        }
        List<String> lastMessages = activeClient.joinRoom(selectedRoom);
        Platform.runLater(() -> {
            messagesList.getItems().add("Welcome to the room '" + selectedRoom + "'.");
            messagesList.getItems().addAll(lastMessages);
        });
        activeRoom = selectedRoom;
        activeRoomLabel.setText("Active room: " + selectedRoom);
        roomList.refresh();
    }

    private void leaveCurrentRoom() {
        if (activeClient == null || activeRoom == null || activeRoom.isEmpty()) {
            showError("No active room to leave.");
            return;
        }
        activeClient.processUserInput("/LEAVEROOM", activeRoom);
        Platform.runLater(() -> messagesList.getItems().add("🚪 You have left the room '" + activeRoom + "'."));
        activeRoom = "";
        activeRoomLabel.setText("Active room: (none)");
        roomList.getSelectionModel().clearSelection();
        roomList.refresh();
    }

    private void refreshRooms() {
        if (activeClient == null) {
            System.out.println("[WARN] Tried to refresh rooms, but no active client is connected.");
            return;
        }
        try {
            activeClient.getAllRooms();
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to refresh rooms: " + e.getMessage());
        }
    }

    private void sendMessage() {
        String input = inputField.getText().trim();
        if (input.isEmpty() || activeClient == null) return;

        if (input.toUpperCase().startsWith("/EDIT") || input.toUpperCase().startsWith("/REPLY")) {
            activeClient.processUserInput(input, activeRoom);
            inputField.clear();
            return;
        }

        if (activeRoom == null || activeRoom.isEmpty()) {
            showError("You cannot send a message because you are not in any room. Please select one first.");
            return;
        }

        activeClient.processUserInput(input, activeRoom);
        inputField.clear();
    }

    private int extractMessageIndex(String messageText) {
        try {
            if (messageText.startsWith("(")) {
                int end = messageText.indexOf(")");
                return Integer.parseInt(messageText.substring(1, end));
            }
        } catch (Exception ignored) {}
        return -1;
    }

    @Override
    public void handleMessage(String message) {
        Platform.runLater(() -> {
            if (message.contains("(Edited)")) {
                List<String> items = new ArrayList<>(messagesList.getItems());
                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i).substring(0, Math.min(10, items.get(i).length()))
                            .equals(message.substring(0, Math.min(10, message.length())))) {
                        items.set(i, message);
                        messagesList.getItems().setAll(items);
                        return;
                    }
                }
            }
            messagesList.getItems().add(message);
        });
    }

    @Override
    public void handleUserListUpdate(List<String> users, String room) {
        if (!room.equals(activeRoom)) return;
        Platform.runLater(() -> {
            userList.getItems().setAll(users);
            userList.refresh();
        });
    }

    @Override
    public void handleMessageUpdate(ChatMessage oldMsg, ChatMessage newMsg, String room) {
        Platform.runLater(() -> {
            List<String> items = new ArrayList<>(messagesList.getItems());
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).startsWith("(" + oldMsg.getIndex() + ")")) {
                    String updated = "(" + newMsg.getIndex() + ") "
                            + newMsg.getFormattedTimestamp()
                            + " (" + newMsg.getChatRoom() + ") "
                            + newMsg.getUser() + ": " + newMsg.getTxt();
                    items.set(i, updated);
                    break;
                }
            }
            messagesList.getItems().setAll(items);
        });
    }



    @Override
    public void handleRoomListUpdate(List<String> rooms) {
        Platform.runLater(() -> {
            roomList.getItems().setAll(rooms);
            roomList.refresh();
        });
    }

    private void changeActiveUser() {
        String selected = userList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a user from the list first.");
            return;
        }

        if (!clients.containsKey(selected)) {
            connectUser(selected);
        }

        activeClient = clients.get(selected);
        activeUsername = selected;

        Platform.runLater(() -> {
            activeUserLabel.setText("Active user: " + selected);
            messagesList.getItems().add("=== Switched to user: " + selected + " ===");
            userList.refresh();
        });

        refreshRooms();
    }


    private void connectUser(String username) {
        try {
            if (clients.containsKey(username)) return;

            ChatClient client = new ChatClient("localhost", 54555, username, this);
            client.start();
            clients.put(username, client);

            if (activeClient == null) {
                activeClient = client;
                activeUsername = username;
                activeUserLabel.setText("Active user: " + username);
            }

            Platform.runLater(() -> {
                if (!userList.getItems().contains(username))
                    userList.getItems().add(username);
                userList.refresh();
            });

        } catch (IOException e) {
            showError("Cannot connect user: " + username + "\n" + e.getMessage());
        }
    }

    private void showError(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, msg).showAndWait());
    }
}
