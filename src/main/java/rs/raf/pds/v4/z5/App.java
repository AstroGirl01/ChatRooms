package main.java.rs.raf.pds.v4.z5;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import main.java.rs.raf.pds.v4.z5.messages.ChatMessage;


public class App extends Application implements ChatClient.ChatMessageCallback {

    private ChatClient chatClient;
    private ListView<String> messageListView;
    private ListView<String> userListView;
    private TextField inputField;
    private Label activeRoomLabel;

    private String activeRoom = "GLOBAL";
    private String lastSelectedMessage = null;
    private String lastSelectedUser = null;

    private final String cssPath = "main/java/rs/raf/pds/v5/z1/gui/styles.css";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("ChatRooms App");
        BorderPane loginPane = new BorderPane();
        loginPane.setPadding(new Insets(10));

        Label usernameLabel = new Label("Username:");
        TextField usernameField = new TextField();
        Button loginButton = new Button("Login");

        loginPane.setTop(usernameLabel);
        loginPane.setCenter(usernameField);
        loginPane.setBottom(loginButton);

        loginButton.setOnAction(e -> login(usernameField.getText(), primaryStage));

        Scene loginScene = new Scene(loginPane, 300, 150);
        try {
            loginScene.getStylesheets().add(getClass().getResource(cssPath).toExternalForm());
        } catch (Exception ignored) {}
        primaryStage.setScene(loginScene);
        primaryStage.show();
    }

    /** Prijava korisnika na server */
    private void login(String username, Stage primaryStage) {
        if (username.trim().isEmpty()) {
            showErrorDialog("Please enter a valid username.");
            return;
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            showErrorDialog("Invalid username format. Use only alphanumeric characters and underscores.");
            return;
        }

        chatClient = new ChatClient("localhost", 4555, username, this);

        try {
            chatClient.start();
            primaryStage.close();
            launchChatApp(primaryStage);
        } catch (IOException ex) {
            showErrorDialog("Error connecting to the server.");
        }
    }

    /** Glavni deo aplikacije — chat prozor */
    private void launchChatApp(Stage primaryStage) {
        BorderPane main = new BorderPane();
        main.setPadding(new Insets(10));

        messageListView = new ListView<>();
        messageListView.setPrefSize(600, 400);
        messageListView.setCellFactory(param -> new MessageCell());
        messageListView.setOnMouseClicked(e -> handleMessageSelection());

        inputField = new TextField();
        inputField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) processUserInput();
        });

        Button enterButton = new Button("Enter");
        enterButton.setOnAction(e -> processUserInput());

        HBox inputBox = new HBox(10, inputField, enterButton);
        HBox.setHgrow(inputField, Priority.ALWAYS);

        userListView = new ListView<>();
        userListView.setMaxWidth(240);
        userListView.setCellFactory(param -> new UserListCell());
        userListView.setOnMouseClicked(e -> handleUserSelection());

        activeRoomLabel = new Label(activeRoom);
        VBox userPanel = new VBox(activeRoomLabel, userListView);
        userPanel.setAlignment(Pos.CENTER);

        main.setBottom(inputBox);
        main.setRight(userPanel);
        main.setCenter(messageListView);

        Scene scene = new Scene(main, 800, 600);
        try {
            scene.getStylesheets().add(getClass().getResource(cssPath).toExternalForm());
        } catch (Exception ignored) {}

        primaryStage.setScene(scene);
        primaryStage.setTitle("ChatRooms - " + chatClient.getUserName());
        primaryStage.show();
    }



    private void handleMessageSelection() {
        String selected = messageListView.getSelectionModel().getSelectedItem();
        userListView.getSelectionModel().clearSelection();
        lastSelectedUser = null;

        if (selected == null || selected.startsWith("Server:")) {
            messageListView.getSelectionModel().clearSelection();
            lastSelectedMessage = null;
            return;
        }
        lastSelectedMessage = (selected.equals(lastSelectedMessage)) ? null : selected;
    }

    private void handleUserSelection() {
        String selectedUser = userListView.getSelectionModel().getSelectedItem();
        messageListView.getSelectionModel().clearSelection();
        lastSelectedMessage = null;

        if (selectedUser != null) {
            lastSelectedUser = (selectedUser.equals(lastSelectedUser)) ? null : selectedUser;
        }
    }



    private void processUserInput() {
        String userInput = inputField.getText().trim();
        if (userInput.isEmpty()) return;

        // Ako je selektovan korisnik → privatna poruka
        if (lastSelectedMessage == null) {
            if (lastSelectedUser != null) {
                chatClient.sendPrivateMessage(lastSelectedUser, userInput);
            } else {
                chatClient.processUserInput(userInput, activeRoom);
            }
        }
        // Ako je selektovana poruka → reply
        else {
            if (lastSelectedMessage.startsWith("Private message from")) {
                String[] parts = lastSelectedMessage.split(":", 2)[0].split(" ");
                String from = parts[parts.length - 1];
                chatClient.sendPrivateMessage(from,
                        "replied to message:\n(**" + lastSelectedMessage + "**)\n" + userInput);
            } else {
                chatClient.processUserInput(
                        "replied to message:\n(**" + lastSelectedMessage + "**)\n" + userInput,
                        activeRoom
                );
            }
        }

        messageListView.getSelectionModel().clearSelection();
        lastSelectedMessage = null;
        inputField.clear();
    }


    @Override
    public void handleMessage(String message) {
        Platform.runLater(() -> messageListView.getItems().add(message));
    }

    @Override
    public void handleUserListUpdate(List<String> users, String room) {
        Platform.runLater(() -> {
            userListView.getItems().setAll(users);
            activeRoom = room;
            activeRoomLabel.setText(activeRoom);
        });
    }

    @Override
    public void handleMessageUpdate(ChatMessage oldMsg, ChatMessage newMsg, String room) {
        if (oldMsg == null || newMsg == null) return;
        Platform.runLater(() -> {
            String oldFormatted = normalize(oldMsg);
            String newFormatted = normalize(newMsg);
            for (int i = 0; i < messageListView.getItems().size(); i++) {
                String item = messageListView.getItems().get(i);
                if (item.trim().equalsIgnoreCase(oldFormatted)) {
                    messageListView.getItems().set(i, newFormatted);
                    break;
                } else if (item.contains(oldFormatted)) {
                    messageListView.getItems().set(i, safeReplace(item, oldFormatted, newFormatted));
                    break;
                }
            }
        });
    }


    private String normalize(ChatMessage msg) {
        String room = msg.getChatRoom() != null ? msg.getChatRoom() : "GLOBAL";
        String user = msg.getUser() != null ? msg.getUser() : "unknown";
        String text = msg.getTxt() != null ? msg.getTxt() : "";
        return "(" + room + ") " + user + ": " + text;
    }

    private String safeReplace(String source, String oldPart, String newPart) {
        try { return source.replace(oldPart, newPart); }
        catch (Exception e) { return source; }
    }

    private void showErrorDialog(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }



    private class MessageCell extends ListCell<String> {
        private final Button editButton = new Button("Edit");

        public MessageCell() {
            editButton.setOnAction(e -> handleEditButtonClick());
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                Text msgText = new Text(item);
                setColor(msgText, item.split(":")[0]);
                msgText.setFont(Font.font("Arial", FontWeight.BOLD, 14));

                String[] parts = item.split(":")[0].split("\\)", 2);
                String msgUser = (parts.length > 1) ? parts[1].trim() : "";
                boolean mine = msgUser.equalsIgnoreCase(chatClient.getUserName());

                if (mine) {
                    Region spacer = new Region();
                    HBox box = new HBox(msgText, spacer, editButton);
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    setGraphic(box);
                } else {
                    setGraphic(new HBox(msgText));
                }
            }
        }

        private void handleEditButtonClick() {
            String msg = getItem();
            String editText = inputField.getText().trim();
            if (!editText.isEmpty()) {
                chatClient.processUserInput("/EDIT~ " + msg + "~" + editText, activeRoom);
                inputField.clear();
            }
        }

        private void setColor(Text text, String username) {
            int hash = username.hashCode();
            int r = (hash & 0xFF0000) >> 16;
            int g = (hash & 0x00FF00) >> 8;
            int b = hash & 0x0000FF;
            text.setFill(javafx.scene.paint.Color.rgb(r, g, b));
        }
    }

    private class UserListCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) setText(null);
            else {
                setText(item);
                int hash = item.hashCode();
                int r = (hash & 0xFF0000) >> 16;
                int g = (hash & 0x00FF00) >> 8;
                int b = hash & 0x0000FF;
                setStyle(String.format("-fx-text-fill: rgb(%d, %d, %d); font-weight: 600;", r, g, b));
            }
        }
    }
}
