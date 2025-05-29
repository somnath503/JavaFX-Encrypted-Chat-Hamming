package com.somnath;// AppLauncher.java
import com.somnath.ui.ChatController; // Import the controller

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class AppLauncher extends Application {

    private ChatController chatController; // Keep a reference to the controller

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/ChatWindow.fxml")); // Use / for resource root
        Parent root = loader.load(); // This also initializes the ChatController ->actually builds the GUI componnet s
        chatController = loader.getController(); // Get the controller instance

        primaryStage.setTitle("Encrypted Chat App");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();

        // Handle window closem->this all logic set the window closing event
        primaryStage.setOnCloseRequest(event -> {
            // Properly shut down the client connection
            if (chatController != null && chatController.client != null) {
                chatController.client.closeClient();
            }
            Platform.exit(); // Exit JavaFX application
            System.exit(0); // Exit the whole application
        });
    }

    public static void main(String[] args) {
        launch(args); // Launches the JavaFX application -> main entry point
    }
}