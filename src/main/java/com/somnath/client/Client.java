package com.somnath.client;

import com.somnath.common.Message;
import com.somnath.common.RSAUtil;
import com.somnath.common.HammingUtil;
import com.somnath.ui.ChatController;

import java.io.*;
import java.net.*;
import java.security.PublicKey;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Client {

    private String host;
    private int port;
    public Socket socket; // Made public for the UI controller to check state
    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;
    private PublicKey serverPublicKey;
    private String username;
    private ChatController controller;

    // Regex to parse @username <message>
    // Captures username in group 1, message in group 2
    private static final Pattern PRIVATE_MESSAGE_PATTERN = Pattern.compile("^@(\\w+)\\s+(.*)");


    public Client(String host, int port, String username, ChatController controller) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.controller = controller;
    }

    public void startClient() {
        try {
            // 1. Connect to Server
            socket = new Socket(host, port);
            System.out.println("Connected to server: " + socket);

            // Initialize streams (Output first to avoid deadlock)
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            inputStream = new ObjectInputStream(socket.getInputStream());

            // 2. Receive Server's Public Key
            serverPublicKey = (PublicKey) inputStream.readObject();
            System.out.println("Received server public key.");

            // 3. Send Client's Username to Server
            outputStream.writeObject(username);
            outputStream.flush();
            System.out.println("Sent username '" + username + "' to server.");

            // Server might deny the connection if username is taken.
            // We need to wait for a response or assume success if no immediate error.
            // A more robust approach would be for the server to send a success/failure confirmation object.
            // For this implementation, we'll proceed assuming success unless disconnected.

            // 4. Start a thread to listen for incoming messages
            new Thread(this::listenForMessages).start();

        } catch (ConnectException e) {
            System.err.println("Connection refused. Is the server running?");
            controller.displayStatus("Connection failed: Server refused connection.");
        } catch (UnknownHostException e) {
            System.err.println("Unknown host: " + host);
            controller.displayStatus("Connection failed: Unknown host.");
        } catch (IOException e) {
            System.err.println("IO error during connection setup: " + e.getMessage());
            e.printStackTrace();
            controller.displayStatus("Connection failed: IO Error. See console.");
        } catch (ClassNotFoundException e) {
            System.err.println("Received unexpected data from server during key exchange: " + e.getMessage());
            e.printStackTrace();
            controller.displayStatus("Connection failed: Protocol Error. See console.");
        }
    }

    // Thread method to continuously listen for messages from the server
    private void listenForMessages() {
        try {
            Object receivedObject;
            while (socket.isConnected() && (receivedObject = inputStream.readObject()) != null) {
                // Server sends back the processed Message object
                if (receivedObject instanceof Message) {
                    Message receivedMessage = (Message) receivedObject;
                    // Pass the complete message object to the UI controller for display.
                    // The UI will decide how to render it based on its fields (sender, recipient, etc.)
                    System.out.println("Client received message object. Final Text: '" + receivedMessage.getFinalDecryptedContent() + "'");
                    controller.displayMessage(receivedMessage); // Update the UI
                } else {
                    System.err.println("Received unexpected object type from server: " + receivedObject.getClass().getName());
                }
            }
        } catch (SocketException e) {
            System.out.println("Disconnected from server (SocketException).");
            controller.displayStatus("Disconnected from server.");
        } catch (EOFException e) {
            System.out.println("Disconnected from server (EOFException).");
            controller.displayStatus("Disconnected from server.");
        } catch (IOException e) {
            System.err.println("IO error while listening for messages: " + e.getMessage());
            e.printStackTrace();
            controller.displayStatus("Error receiving message. See console.");
        } catch (ClassNotFoundException e) {
            System.err.println("Received unknown object from server: " + e.getMessage());
            e.printStackTrace();
            controller.displayStatus("Error processing received data. See console.");
        } finally {
            closeClient(); // Clean up resources
        }
    }

    // Method to send a message from the client UI
    public void sendMessage(String fullOriginalText) {
        if (socket == null || !socket.isConnected() || outputStream == null || serverPublicKey == null) {
            System.err.println("Not connected to server or server public key not received.");
            controller.displayStatus("Cannot send: Not connected or no server key.");
            return;
        }

        String recipient = null;
        String contentToEncrypt = fullOriginalText.trim(); // Default: group message, full text is content

        // --- Parse for Private Message Syntax ---
        Matcher matcher = PRIVATE_MESSAGE_PATTERN.matcher(fullOriginalText.trim());
        if (matcher.matches()) {
            recipient = matcher.group(1); // The username after '@'
            contentToEncrypt = matcher.group(2).trim(); // The message body after the username
            System.out.println("Parsed as Private Message: Recipient='" + recipient + "', Content='" + contentToEncrypt + "'");
        } else {
            System.out.println("Parsed as Group Message: Content='" + contentToEncrypt + "'");
        }

        // Don't send empty content
        if (contentToEncrypt.isEmpty()) {
            System.out.println("Message content is empty after parsing. Not sending.");
            return;
        }


        try {
            // 1. Create Message object
            Message messageToSend = new Message(this.username, fullOriginalText.trim(), recipient, contentToEncrypt);

            // 2. RSA Encrypt the actual message content using server's public key
            byte[] contentBytes = RSAUtil.stringToBytes(contentToEncrypt);
            byte[] encryptedBytes = RSAUtil.encrypt(contentBytes, serverPublicKey);
            String encryptedBase64 = RSAUtil.bytesToBase64(encryptedBytes);
            messageToSend.setEncryptedContentBase64(encryptedBase64);
            // System.out.println("Encrypted Content (Base64): " + encryptedBase64); // Too verbose

            // 3. Convert encrypted bytes to binary string
            String encryptedBinary = HammingUtil.bytesToBinaryString(encryptedBytes);

            // 4. Hamming Encode the encrypted binary string
            String hammingEncoded = HammingUtil.encode(encryptedBinary);
            messageToSend.setHammingEncodedBinary(hammingEncoded);
            // System.out.println("Hamming Encoded (Binary): " + hammingEncoded); // Too verbose


            // 5. Simulate a 1-bit error in the Hamming Encoded binary string
            String erroredHamming = HammingUtil.simulateError(hammingEncoded);
            messageToSend.setErroredHammingBinary(erroredHamming);
            // System.out.println("Errored Hamming (Binary): " + erroredHamming); // Too verbose
            // System.out.println("Error simulated at index (within Hamming string): " + findFirstDifference(hammingEncoded, erroredHamming)); // Helper for debugging


            // 6. Send the Message object to the server
            // The server will receive the object containing:
            // sender, fullOriginalText, recipient, contentToEncrypt, encryptedContentBase64, hammingEncodedBinary, erroredHammingBinary
            // The server will then populate correctedHammingBinary, finalDecryptedContent, error flags
            // and route the full object back to relevant clients.
            outputStream.writeObject(messageToSend);
            outputStream.flush(); // Send the message immediately
            System.out.println("Message object sent to server.");


        } catch (Exception e) {
            System.err.println("Error sending message: " + e.getMessage());
            e.printStackTrace();
            controller.displayStatus("Error sending message. See console.");
        }
    }

    // Simple helper to find the index of the first difference (for debugging error simulation)
    private int findFirstDifference(String s1, String s2) {
        int minLength = Math.min(s1.length(), s2.length());
        for (int i = 0; i < minLength; i++) {
            if (s1.charAt(i) != s2.charAt(i)) {
                return i;
            }
        }
        if (s1.length() != s2.length()) return minLength;
        return -1; // No difference
    }


    // Clean up client resources
    public void closeClient() {
        try {
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("Client connection closed.");
        } catch (IOException e) {
            System.err.println("Error closing client: " + e.getMessage());
        }
    }

    // Getter for username (needed by ChatController)
    public String getUsername() {
        return username;
    }
}