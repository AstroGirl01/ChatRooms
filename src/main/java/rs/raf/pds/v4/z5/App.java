package main.java.rs.raf.pds.v4.z5;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        Label usernameLabel = new Label("Username");
        TextField usernameField = new TextField();
        Button loginButton = new Button("Login");

        VBox loginBox = new VBox(10, usernameLabel, usernameField, loginButton);
        loginBox.setAlignment(Pos.CENTER);
        Scene loginScene = new Scene(loginBox, 300, 200);

        primaryStage.setTitle("Chat Login");
        primaryStage.setScene(loginScene);
        primaryStage.show();

        loginButton.setOnAction(e -> {
            String username = usernameField.getText().trim();
            if (username.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Username is missing!").showAndWait();
                return;
            }

            try {
                ChatClientGUI chatGui = new ChatClientGUI(username);
                chatGui.start(new Stage());

                primaryStage.close(); 
            } catch (Exception ex) {
                ex.printStackTrace();
                new Alert(Alert.AlertType.ERROR, "Greška pri pokretanju GUI-a.").showAndWait();
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
