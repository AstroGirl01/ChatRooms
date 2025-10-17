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
    private TextField inputField;
    private String activeRoom = "GLOBAL";
    private ListView<String> userListView;
    private Label activeRoomLabel;
    private final String cssPath = "/rs/raf/pds/v4/z5/resources/styles.css";
    private String lastSelectedMessage = null;
    private String lastSelectedUser = null;

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
        } catch (Exception ex) {
            ex.printStackTrace();
            showErrorDialog("Error connecting to the server.");
        }
    }

    private void launchChatApp(Stage primaryStage) {
        BorderPane mainBorderPane = new BorderPane();
        mainBorderPane.setPadding(new Insets(10));

        messageListView = new ListView<>();
        messageListView.setCellFactory(param -> new MessageCell());
        messageListView.setPrefSize(600, 400);

        messageListView.setOnMouseClicked(event -> {
            String selectedMessage = messageListView.getSelectionModel().getSelectedItem();
            userListView.getSelectionModel().clearSelection();
            lastSelectedUser = null;
            if (selectedMessage != null) {
                if (selectedMessage.startsWith("Server:")) {
                    messageListView.getSelectionModel().clearSelection();
                    lastSelectedMessage = null;
                    return;
                }
                if (selectedMessage.equals(lastSelectedMessage)) {
                    messageListView.getSelectionModel().clearSelection();
                    lastSelectedMessage = null;
                } else {
                    lastSelectedMessage = selectedMessage;
                }
            }
        });

        inputField = new TextField();
        inputField.setPrefWidth(720);
        inputField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                String userInput = inputField.getText().trim();
                if (userInput.contains("~")) {
                    showErrorDialog("Invalid character '~' in the message. Please remove it.");
                    return;
                }
                processUserInput();
            }
        });

        userListView = new ListView<>();
        userListView.setPrefHeight(720);
        userListView.setMaxWidth(240);
        userListView.setOnMouseClicked(event -> {
            String selectedUser = userListView.getSelectionModel().getSelectedItem();
            messageListView.getSelectionModel().clearSelection();
            lastSelectedMessage = null;

            if (selectedUser != null) {
                if (selectedUser.equals(lastSelectedUser)) {
                    userListView.getSelectionModel().clearSelection();
                    lastSelectedUser = null;
                } else {
                    lastSelectedUser = selectedUser;
                }
            }
        });

        activeRoomLabel = new Label(activeRoom);
        VBox userListVBox = new VBox(activeRoomLabel, userListView);
        userListVBox.setAlignment(Pos.CENTER);
        userListView.setCellFactory(param -> new UserListCell());

        Button enterButton = new Button("Enter");
        enterButton.setPrefWidth(240);
        enterButton.setOnAction(e -> processUserInput());

        HBox inputBox = new HBox(10);
        inputBox.getChildren().addAll(inputField, new Region(), enterButton);
        HBox.setHgrow(inputField, Priority.ALWAYS);

        mainBorderPane.setBottom(inputBox);
        mainBorderPane.setRight(userListVBox);
        mainBorderPane.setCenter(messageListView);

        Scene chatScene = new Scene(mainBorderPane, 800, 600);
        try {
            chatScene.getStylesheets().add(getClass().getResource(cssPath).toExternalForm());
        } catch (Exception ignored) {}
        primaryStage.setScene(chatScene);
        primaryStage.setTitle("ChatRooms App - " + chatClient.getUserName());
        primaryStage.show();
    }

    @Override
    public void handleMessage(String message) {
        Platform.runLater(() -> messageListView.getItems().add(message));
    }

    private class MessageCell extends ListCell<String> {
        private final Button editButton;

        public MessageCell() {
            editButton = new Button("Edit");
            editButton.setOnAction(event -> handleEditButtonClick());
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                Text messageText = new Text(item);
                setColorForMessage(messageText, item.split(":")[0]);
                messageText.setFont(Font.font("Arial", FontWeight.BOLD, 14));
                String[] parts = item.split(":")[0].split("\\)", 2);
                String messageUsername = (parts.length > 1) ? parts[1].trim() : "";
                boolean isClientMessage = messageUsername.equalsIgnoreCase(chatClient.getUserName());
                if (isClientMessage) {
                    Region spacer = new Region();
                    HBox messageBox = new HBox(messageText, spacer, editButton);
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    setGraphic(messageBox);
                } else {
                    setGraphic(new HBox(messageText));
                }
            }
        }

        private void handleEditButtonClick() {
            String message = getItem();
            String userInput = inputField.getText().trim();
            if (!userInput.isEmpty()) {
                chatClient.processUserInput("/EDIT~ " + message + "~" + userInput, activeRoom);
                inputField.clear();
            }
        }

        private void setColorForMessage(Text text, String username) {
            int hashCode = username.hashCode();
            int red = (hashCode & 0xFF0000) >> 16;
            int green = (hashCode & 0x00FF00) >> 8;
            int blue = hashCode & 0x0000FF;
            text.setFill(javafx.scene.paint.Color.rgb(red, green, blue));
        }
    }

    private class UserListCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) setText(null);
            else {
                setText(item);
                setStyle(getUserStyle(item));
            }
        }

        private String getUserStyle(String username) {
            int hashCode = username.hashCode();
            int red = (hashCode & 0xFF0000) >> 16;
            int green = (hashCode & 0x00FF00) >> 8;
            int blue = hashCode & 0x0000FF;
            return String.format("-fx-text-fill: rgb(%d, %d, %d);font-weight: 600;", red, green, blue);
        }
    }

    @Override
    public void handleUserListUpdate(List<String> users, String room) {
        Platform.runLater(() -> {
            userListView.getItems().setAll(users);
            activeRoom = room;
            activeRoomLabel.setText(activeRoom);
        });
    }

    private void processUserInput() {
        String userInput = inputField.getText().trim();
        if (userInput.isEmpty()) return;

        if (lastSelectedMessage == null) {
            if (lastSelectedUser != null) {
                chatClient.sendPrivateMessage(lastSelectedUser, userInput);
            } else {
                chatClient.processUserInput(userInput, activeRoom);
            }
        } else {
            if (lastSelectedMessage.startsWith("Private message from")) {
                String[] parts = lastSelectedMessage.split(":", 2)[0].split(" ");
                String from = parts[parts.length - 1];
                chatClient.sendPrivateMessage(from, "replied to message:\n(**" + lastSelectedMessage + "**)\n" + userInput);
            } else {
                chatClient.processUserInput("replied to message:\n(**" + lastSelectedMessage + "**)\n" + userInput, activeRoom);
            }
        }

        messageListView.getSelectionModel().clearSelection();
        lastSelectedMessage = null;
        inputField.clear();
    }

  
    @Override
    public void handleMessageUpdate(ChatMessage oldMessage, ChatMessage message, String room) {
        if (oldMessage == null || message == null) {
            System.err.println("[WARN] handleMessageUpdate pozvan sa null vrednostima!");
            return;
        }

        Platform.runLater(() -> {
            String oldFormatted = normalizeMessage(oldMessage);
            String newFormatted = normalizeMessage(message);

            for (int i = 0; i < messageListView.getItems().size(); i++) {
                String existing = messageListView.getItems().get(i).trim();

                if (existing.equalsIgnoreCase(oldFormatted)) {
                    messageListView.getItems().set(i, newFormatted);
                    return;
                }

                if (existing.contains(oldFormatted)) {
                    messageListView.getItems().set(i, existing.replace(oldFormatted, newFormatted));
                    return;
                }
            }
        });
    }


    /** Pomocna funkcija — pravi ujednačen format poruke */
    private String normalizeMessage(ChatMessage msg) {
        if (msg == null) return "";
        String room = msg.getChatRoom() != null ? msg.getChatRoom() : "GLOBAL";
        String user = msg.getUser() != null ? msg.getUser() : "unknown";
        String text = msg.getTxt() != null ? msg.getTxt() : "";
        return "(" + room + ") " + user + ": " + text;
    }


    private String safeReplace(String source, String oldPart, String newPart) {
        try {
            return source.replace(oldPart, newPart);
        } catch (Exception e) {
            return source; // fallback
        }
    }

    private void showErrorDialog(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
