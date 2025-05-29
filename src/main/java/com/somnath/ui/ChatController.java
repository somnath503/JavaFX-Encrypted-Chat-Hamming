package com.somnath.ui;

// ui/ChatController.java

import com.somnath.common.Message;
import com.somnath.client.Client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox; // Import HBox
import javafx.scene.layout.Priority; // Import Priority for HBox.setHgrow
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class ChatController implements Initializable {

    @FXML private ListView<Message> messageListView;
    @FXML private TextField messageTextField;
    @FXML private Button sendButton;
    @FXML private Label statusLabel;

    public Client client; // Make client public or provide getter if needed for AppLauncher
    private String username;

    // --- Initialization ---
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Prompt for username first
        getUsernameFromUser();

        // Configure the ListView to use a custom cell factory
        messageListView.setCellFactory(listView -> new MessageCell());

        // --- REMOVE any previous list item click handler on messageListView ---
        // The button inside the cell handles toggling now.
        // messageListView.setOnMouseClicked(event -> { ... });


        // Set up action for sending message (Enter key or Button click)
        messageTextField.setOnAction(event -> sendMessage()); // Enter key
        sendButton.setOnAction(event -> sendMessage());    // Button click

        // Make sure the text field is disabled until connected
        messageTextField.setDisable(true);
        sendButton.setDisable(true);

        // Start the client connection attempt after getting username
        if (this.username != null && !this.username.trim().isEmpty()) {
            startClientConnection("localhost", 12345);
        } else {
            displayStatus("Connection cancelled: No username provided.", Color.RED);
        }
    }

    // --- Get Username Dialog ---
    private void getUsernameFromUser() {
        TextInputDialog dialog = new TextInputDialog("User" + (int)(Math.random() * 1000)); // Default with random suffix
        dialog.setTitle("Username");
        dialog.setHeaderText("Enter your username");
        dialog.setContentText("Username:");
        dialog.setGraphic(null); // Remove default icon

        Stage dialogStage = (Stage) dialog.getDialogPane().getScene().getWindow();
        dialogStage.setOnCloseRequest(event -> {
            // If user closes dialog without entering name, exit application
            if (this.username == null || this.username.trim().isEmpty()) {
                Platform.exit();
                System.exit(0);
            }
        });


        dialog.showAndWait().ifPresent(name -> this.username = name.trim());

        // Ensure username is not empty if dialog was just closed
        if (this.username != null && this.username.isEmpty()) {
            this.username = null; // Treat empty as cancelled
        }
    }


    // --- Client Connection Management ---
    private void startClientConnection(String host, int port) {
        // It's better to run the client connection in a separate thread
        new Thread(() -> {
            client = new Client(host, port, username, this); // Pass 'this' controller
            client.startClient(); // This method blocks until connected or fails
            // After startClient returns, check connection status and update UI
            Platform.runLater(() -> {
                // Check if socket exists and is connected
                if (client != null && client.socket != null && client.socket.isConnected()) {
                    displayStatus("Attempting connection as '" + username + "'...", Color.ORANGE); // Initial status

                    // UI elements enabled when socket connects, actual chat depends on server username acceptance
                    messageTextField.setDisable(false);
                    sendButton.setDisable(false);
                    messageTextField.requestFocus(); // Set focus to input field
                } else {
                    // Client.startClient() failed
                    displayStatus("Connection failed during setup.", Color.RED);
                    messageTextField.setDisable(true);
                    sendButton.setDisable(true);
                }
            });
        }).start();
    }


    // --- Sending Message ---
    private void sendMessage() {
        String text = messageTextField.getText();
        if (text == null || text.trim().isEmpty()) {
            return; // Don't send empty messages
        }

        // The client class now handles parsing the @username syntax internally
        if (client != null && client.socket != null && client.socket.isConnected()) {
            // Pass the full typed text to the client layer
            client.sendMessage(text.trim());
            messageTextField.clear(); // Clear input field
        } else {
            displayStatus("Error: Not connected to server.", Color.RED);
            // Maybe attempt to reconnect or prompt user?
        }
    }

    // --- Receiving and Displaying Messages (Called by Client Thread) ---
    public void displayMessage(Message msg) {
        // Ensure UI updates happen on the JavaFX Application Thread
        Platform.runLater(() -> {
            // Check if this is a server error message about username taken
            if ("SERVER".equals(msg.getSender()) && msg.getFinalDecryptedContent() != null && msg.getFinalDecryptedContent().contains("Username")) {
                displayStatus(msg.getFinalDecryptedContent(), Color.RED);
                // If username taken, disconnect the client
                if (client != null) {
                    client.closeClient(); // This will trigger the finally block in client handler
                    messageTextField.setDisable(true);
                    sendButton.setDisable(true);
                }
            } else {
                // Add regular message to the list view
                messageListView.getItems().add(msg);
                // Auto-scroll to the bottom
                messageListView.scrollTo(messageListView.getItems().size() - 1);

                // Update status if it was a successful connection message
                if (client != null && client.socket != null && client.socket.isConnected() && statusLabel.getText().startsWith("Status: Attempting connection as")) {
                    displayStatus("Connected as '" + username + "'", Color.GREEN);
                }

                // If the message is from the server and indicates a join/leave,
                // the MessageCell will handle displaying these differently based on sender == "SERVER".
            }
        });
    }

    // --- Update Status Label (Called by Client Thread) ---
    public void displayStatus(String status) {
        displayStatus(status, Color.BLACK); // Default color
    }

    public void displayStatus(String status, Color color) {
        Platform.runLater(() -> {
            statusLabel.setText("Status: " + status);
            statusLabel.setTextFill(color);
        });
    }


    // --- Custom ListCell for Message Display ---
    // This defines how each Message object is rendered in the ListView
    class MessageCell extends ListCell<Message> {
        private final VBox contentBox = new VBox(5); // Main container for the cell's content
        private final Label senderInfoLabel = new Label(); // Label for Sender [to Recipient]
        private final Button detailsButton = new Button(); // *** ADDED BUTTON to toggle details ***

        // HBox to hold the final decrypted message and the details button side-by-side
        private final HBox messageBodyAndButtonHBox = new HBox(5); // Spacing of 5

        private final VBox detailsVBox = new VBox(5); // *** ADDED VBOX to contain expandable details ***

        // VBox wrappers for individual detail sections (Label + TextFlow)
        // Original Text section will be outside detailsVBox now
        private final VBox originalSection;
        private final VBox encryptedSection;
        private final VBox erroredSection;
        private final VBox correctedSection;


        private final TextFlow finalDecryptedContentFlow = new TextFlow(); // TextFlow for the final decrypted content
        private final Label errorStatusLabel = new Label(); // Label for Hamming error status (MOVED INSIDE detailsVBox)


        public MessageCell() {
            contentBox.setPadding(new Insets(5));

            // Sender/Recipient Info Label
            senderInfoLabel.setFont(Font.font("System", FontWeight.BOLD, 12));

            // Configure the Details Button
            detailsButton.setFont(Font.font("System", FontWeight.NORMAL, 10));
            detailsButton.setPadding(new Insets(1, 5, 1, 5)); // Make button smaller
            // Make button look like a link/minimal
            detailsButton.setStyle("-fx-border-color: transparent; -fx-background-color: transparent; -fx-text-fill: blue; -fx-underline: true;");
            // Handle hover effect for link-like button
            detailsButton.setOnMouseEntered(e -> detailsButton.setTextFill(Color.DARKBLUE));
            detailsButton.setOnMouseExited(e -> detailsButton.setTextFill(Color.BLUE));


            // Set the action for the button click
            detailsButton.setOnAction(event -> {
                Message currentMessage = getItem(); // Get the Message object associated with THIS cell
                if (currentMessage != null) {
                    // Toggle the 'expanded' state of the Message object
                    currentMessage.setExpanded(!currentMessage.isExpanded());
                    // Tell the ListView to refresh its display. This will cause updateItem()
                    // to be called again for this cell, allowing it to update its layout.
                    getListView().refresh(); // Refresh the whole list to update this cell
                }
            });


            // Create the VBox wrappers for each section, passing the TextFlows
            TextFlow fullOriginalTextFlow = new TextFlow();
            TextFlow encryptedContentFlow = new TextFlow();
            TextFlow erroredHammingFlow = new TextFlow();
            TextFlow correctedHammingFlow = new TextFlow();


            // Create the sections
            originalSection = createSection("Original Typed:", fullOriginalTextFlow); // This will go in contentBox
            encryptedSection = createSection("Encrypted Content (Base64):", encryptedContentFlow);
            erroredSection = createSection("Hamming Errored:", erroredHammingFlow);
            correctedSection = createSection("Hamming Corrected:", correctedHammingFlow);
            // Final Decrypted text goes directly into the HBox, its label is part of the section wrapper logic


            // Add the intermediate detail sections *plus* the error status label to the detailsVBox.
            // This VBox's visibility will be toggled.
            detailsVBox.getChildren().addAll(
                    encryptedSection,   // *** Original Section is NOT here anymore ***
                    erroredSection,
                    correctedSection,
                    errorStatusLabel // *** ERROR STATUS LABEL IS PART OF THE TOGGLEABLE DETAILS ***
            );

            // Add the Final Decrypted TextFlow and the Details Button to the HBox
            messageBodyAndButtonHBox.getChildren().addAll(
                    finalDecryptedContentFlow, // The main message text
                    detailsButton              // The toggle button
            );
            // Make the TextFlow take up available space, pushing the button to the right
            HBox.setHgrow(finalDecryptedContentFlow, Priority.ALWAYS);


            // Add components to the main content box in the desired order.
            // Sender Info -> Original Text -> HBox (Message + Button) -> Expandable Details VBox
            contentBox.getChildren().addAll(
                    senderInfoLabel,             // Sender info (always visible)
                    originalSection,             // *** ORIGINAL TEXT SECTION (always visible) ***
                    messageBodyAndButtonHBox,    // HBox containing Final Decrypted and Button
                    detailsVBox                  // The container for hidden/shown details (Encrypted, Hamming steps, Error Status)
            );

            // Add padding to the overall cell content
            setPadding(new Insets(5));

            // Allow the cell to grow/shrink with content inside the ListView
            setPrefWidth(0); // Necessary for wrapping to work properly inside ListView
        }

        // Helper method to create a VBox containing a Label and a TextFlow.
        private VBox createSection(String labelText, TextFlow textFlow) {
            Label sectionLabel = new Label(labelText);
            sectionLabel.setFont(Font.font("System", FontWeight.BOLD, 10));
            VBox sectionBox = new VBox(2); // VBox to stack the label and the TextFlow
            sectionBox.getChildren().addAll(sectionLabel, textFlow);
            // Optionally set padding for consistency between sections
            sectionBox.setPadding(new Insets(2, 0, 2, 0)); // Small top/bottom padding
            return sectionBox; // Return the wrapper VBox
        }


        @Override
        protected void updateItem(Message msg, boolean empty) {
            super.updateItem(msg, empty); // Always call super

            if (empty || msg == null) {
                // If the cell is empty, hide its content box entirely
                setGraphic(null);
                contentBox.setVisible(false);
                contentBox.setManaged(false); // Crucial: prevents hidden cells from taking space
            } else {
                // If the cell has content, make its content box visible
                contentBox.setVisible(true);
                contentBox.setManaged(true);
                setGraphic(contentBox); // Set the graphic of the ListCell


                // --- Populate the UI components with data from the Message object ---

                // Sender/Recipient Info
                if (msg.isPrivateMessage()) {
                    senderInfoLabel.setText(msg.getSender() + " [to " + msg.getRecipient() + "]");
                    senderInfoLabel.setTextFill(Color.DARKBLUE); // Different color for private
                } else {
                    // Handle Server messages differently (e.g., join/leave/errors)
                    if ("SERVER".equals(msg.getSender())) {
                        senderInfoLabel.setText(msg.getSender());
                        senderInfoLabel.setTextFill(Color.DARKGRAY);
                    } else { // Regular group message
                        senderInfoLabel.setText(msg.getSender());
                        senderInfoLabel.setTextFill(Color.BLACK); // Default color for group
                    }
                }

                // Populate TextFlows within their respective section wrappers
                // We get the TextFlow using getChildren().get(1) from the wrapper VBox
                // Original Text (Now always visible)
                ((TextFlow) originalSection.getChildren().get(1)).getChildren().clear();
                ((TextFlow) originalSection.getChildren().get(1)).getChildren().add(new Text("Original: " + (msg.getFullOriginalText() != null ? msg.getFullOriginalText() : "N/A"))); // Added "Original: " label here for clarity


                // Encrypted Content (Inside detailsVBox)
                ((TextFlow) encryptedSection.getChildren().get(1)).getChildren().clear();
                ((TextFlow) encryptedSection.getChildren().get(1)).getChildren().add(new Text(msg.getEncryptedContentBase64() != null ? msg.getEncryptedContentBase64() : "N/A"));

                // Hamming Errored Binary (Inside detailsVBox)
                ((TextFlow) erroredSection.getChildren().get(1)).getChildren().clear();
                ((TextFlow) erroredSection.getChildren().get(1)).getChildren().add(new Text(msg.getErroredHammingBinary() != null ? msg.getErroredHammingBinary() : "N/A"));

                // Hamming Corrected Binary (Inside detailsVBox)
                ((TextFlow) correctedSection.getChildren().get(1)).getChildren().clear();
                ((TextFlow) correctedSection.getChildren().get(1)).getChildren().add(new Text(msg.getCorrectedHammingBinary() != null ? msg.getCorrectedHammingBinary() : "N/A"));


                // Final Decrypted Content (using the direct TextFlow reference, part of the HBox)
                finalDecryptedContentFlow.getChildren().clear();
                String decryptedText = msg.getFinalDecryptedContent() != null ? msg.getFinalDecryptedContent() : "N/A";
                // Prepend "Decrypted: " label here for clarity
                Text decryptedTextNode = new Text("Decrypted: " + decryptedText);

                // Apply colors/styles based on content/sender
                // Check if the message went through the full client-side processing pipeline
                boolean hammingProcessed = msg.getErroredHammingBinary() != null;

                if (decryptedText.startsWith("[DECRYPTION FAILED]") || decryptedText.startsWith("[DECRYPTION ERROR]")) {
                    decryptedTextNode.setFill(Color.RED);
                } else if ("SERVER".equals(msg.getSender()) && !hammingProcessed) {
                    // Special styling for simple server status messages that didn't use crypto/hamming
                    decryptedTextNode.setFill(Color.GRAY);
                    decryptedTextNode.setFont(Font.font("System", FontWeight.BOLD, 10));
                } else {
                    // Default style for regular messages
                    decryptedTextNode.setFill(Color.BLACK);
                    decryptedTextNode.setFont(Font.font("System", FontWeight.NORMAL, Font.getDefault().getSize())); // Reset font
                }
                finalDecryptedContentFlow.getChildren().add(decryptedTextNode);


                // --- Control Visibility and Text based on Message State and Data ---

                // Check if the message went through the full client-side processing pipeline
                // This determines if the button and details should appear at all.
                boolean isExpandable = hammingProcessed; // Variable is defined here within updateItem

                // Button Visibility and Text
                detailsButton.setVisible(isExpandable);
                detailsButton.setManaged(isExpandable); // Control layout space for the button

                // Get the expanded state from the message object
                boolean expanded = msg.isExpanded();

                // Update the button text based on the expanded state
                detailsButton.setText(expanded ? "Hide Details" : "Show Details");


                // Error Status Label - Set its text and color based on Hamming processing result.
                // Its visibility/managed state is controlled by the detailsVBox (which is toggled).
                if (hammingProcessed) {
                    if (msg.isErrorDetected()) {
                        errorStatusLabel.setText("Hamming Error: Detected and Corrected");
                        errorStatusLabel.setTextFill(Color.ORANGE);
                    } else {
                        errorStatusLabel.setText("Hamming Error: None Detected");
                        errorStatusLabel.setTextFill(Color.GREEN);
                    }
                } else {
                    errorStatusLabel.setText(""); // Clear text
                }


                // Set visibility and managed state of the details container VBox
                // (containing Encrypted, Hamming steps, AND Error Status Label)
                // It should only be visible if the message is expandable AND currently expanded
                detailsVBox.setVisible(expanded && isExpandable);
                detailsVBox.setManaged(expanded && isExpandable); // Control layout space for details


                // Ensure sender info, Original Text section, and the HBox containing final decrypted content + button are always visible
                senderInfoLabel.setVisible(true); senderInfoLabel.setManaged(true);
                originalSection.setVisible(true); originalSection.setManaged(true); // Original Text always visible
                messageBodyAndButtonHBox.setVisible(true); messageBodyAndButtonHBox.setManaged(true);
            }
        }
    }

    // --- Application Entry Point ---
    // This is in AppLauncher.java, not ChatController.
    // public static void main(String[] args) { launch(args); }
}