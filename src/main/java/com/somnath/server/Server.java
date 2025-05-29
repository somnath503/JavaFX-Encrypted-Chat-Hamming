package com.somnath.server;


import com.somnath.common.Message;
import com.somnath.common.RSAUtil;
import com.somnath.common.HammingUtil;

import javax.crypto.BadPaddingException;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Server {

    private static final int PORT = 12345;
    private ServerSocket serverSocket;
    // Use a synchronized map to manage clients by username
    private Map<String, ClientHandler> clientHandlers = Collections.synchronizedMap(new HashMap<>());
    private KeyPair serverKeyPair;

    public Server() {
        try {
            // 1. Generate Server's RSA Key Pair
            serverKeyPair = RSAUtil.generateKeyPair();
            System.out.println("Server RSA KeyPair generated.");

            // 2. Start Server Socket
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server started on port " + PORT);

            // 3. Accept Connections
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connecting: " + clientSocket);

                // Create a handler for the client
                ClientHandler handler = new ClientHandler(clientSocket, this);
                handler.start();
            }

        } catch (NoSuchAlgorithmException e) {
            System.err.println("RSA Algorithm not available: " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("Error starting server or accepting connection: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Close server socket on exit
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing server socket: " + e.getMessage());
            }
        }
    }

    // Method to add a client handler to the map
    public void addClient(String username, ClientHandler handler) {
        clientHandlers.put(username, handler);
        System.out.println("Client '" + username + "' connected. Active clients: " + clientHandlers.size());
        // Optional: Notify all clients that a new user joined
        broadcastStatusMessage("User '" + username + "' joined.");
    }

    // Method to remove a client handler when they disconnect
    public void removeClient(String username) {
        if (username != null) {
            clientHandlers.remove(username);
            System.out.println("Client '" + username + "' disconnected. Active clients: " + clientHandlers.size());
            // Optional: Notify all clients that a user left
            broadcastStatusMessage("User '" + username + "' left.");
        }
    }

    // Broadcast a simple status message (not encrypted/encoded) - useful for join/leave
    private void broadcastStatusMessage(String status) {
        // Create a special message type if needed, or just log
        // For now, just log server-side. Clients can infer joins/leaves from chat messages.
        System.out.println("[STATUS] " + status);
        // If you want clients to display this, create a separate "StatusMessage" object
        // or add a 'type' field to Message and broadcast it.
    }


    // Method to route the processed message
    public void routeMessage(Message msg, ClientHandler senderHandler) {
        System.out.println("Routing message from " + msg.getSender() + " to " + (msg.isPrivateMessage() ? msg.getRecipient() : "GROUP"));

        if (msg.isPrivateMessage()) {
            // Private Message
            ClientHandler recipientHandler = clientHandlers.get(msg.getRecipient());

            // Send to sender (so they see their own message pipeline)
            senderHandler.sendMessage(msg);

            if (recipientHandler != null && recipientHandler != senderHandler) {
                // Send to recipient
                recipientHandler.sendMessage(msg);
                System.out.println("Sent private message to '" + msg.getRecipient() + "'");
            } else if (recipientHandler == senderHandler) {
                System.out.println("Private message to self: Sent back to sender.");
            }
            else {
                System.out.println("Private message recipient '" + msg.getRecipient() + "' not found.");
                // Optional: Send a "User not found" message back to sender
                // Requires creating and sending a special message type to senderHandler
                senderHandler.sendMessage(createErrorMessage("User '" + msg.getRecipient() + "' not found.", "SERVER"));
            }
        } else {
            // Group Message (Broadcast)
            // Send to all clients, including the sender
            for (ClientHandler handler : clientHandlers.values()) {
                handler.sendMessage(msg);
            }
            System.out.println("Broadcast group message.");
        }
    }

    // Helper to create a simple text message from the server for the client UI
    private Message createErrorMessage(String text, String sender) {
        // This is a simple message that doesn't go through crypto/hamming pipeline
        Message errorMsg = new Message(sender, text, null, text);
        errorMsg.setFinalDecryptedContent(text); // Set the final text directly
        // Other fields can be left null
        return errorMsg;
    }


    public PrivateKey getServerPrivateKey() {
        return serverKeyPair.getPrivate();
    }

    public PublicKey getServerPublicKey() {
        return serverKeyPair.getPublic();
    }


    // Inner class to handle individual client connections
    class ClientHandler extends Thread {
        private Socket clientSocket;
        private Server server;
        private ObjectInputStream inputStream;
        private ObjectOutputStream outputStream;
        private String username; // Added username field

        public ClientHandler(Socket socket, Server server) {
            this.clientSocket = socket;
            this.server = server;
        }

        public String getUsername() {
            return username;
        }

        public void run() {
            try {
                // Initialize streams (Output first to avoid deadlock)
                outputStream = new ObjectOutputStream(clientSocket.getOutputStream());
                inputStream = new ObjectInputStream(clientSocket.getInputStream());

                // 1. Send Server's Public Key to the Client
                outputStream.writeObject(server.getServerPublicKey());
                outputStream.flush(); // Ensure key is sent immediately
                System.out.println("Sent public key to client: " + clientSocket);

                // 2. Receive Client's Username
                // Assuming the client sends the username as a String object first
                Object initialObject = inputStream.readObject();
                if (initialObject instanceof String) {
                    this.username = (String) initialObject;
                    // Basic validation
                    if (this.username == null || this.username.trim().isEmpty()) {
                        System.err.println("Client provided empty username. Disconnecting: " + clientSocket);
                        return; // Exit handler run method
                    }
                    this.username = this.username.trim(); // Trim whitespace
                    System.out.println("Received username '" + this.username + "' from client: " + clientSocket);

                    // Add client to the server's map
                    if (server.clientHandlers.containsKey(this.username)) {
                        // Username already exists. Deny connection or append suffix?
                        // For now, deny and close connection.
                        System.err.println("Username '" + this.username + "' already in use. Denying connection: " + clientSocket);
                        sendMessage(createErrorMessage("Username '" + this.username + "' is already in use. Please try a different name.", "SERVER"));
                        return; // Exit handler run method
                    }
                    server.addClient(this.username, this);

                } else {
                    System.err.println("First object from client was not a username String. Disconnecting: " + clientSocket);
                    return; // Exit handler run method
                }


                // 3. Read Messages from Client
                Message receivedMessage;
                while (clientSocket.isConnected() && (receivedMessage = (Message) inputStream.readObject()) != null) {
                    // The received message object contains sender, recipient, fullOriginalText,
                    // contentToEncrypt (parsed client-side), encryptedContentBase64, erroredHammingBinary

                    // --- Server-side Processing Pipeline ---

                    // Step A: Hamming Correct
                    String erroredBinary = receivedMessage.getErroredHammingBinary();
                    String correctedBinary = HammingUtil.correctError(erroredBinary, receivedMessage);
                    receivedMessage.setCorrectedHammingBinary(correctedBinary);
                    // System.out.println("Hamming Corrected. Error Detected: " + receivedMessage.isErrorDetected()); // Too verbose


                    // Step B: Hamming Decode
                    String decodedBinary = HammingUtil.decode(correctedBinary);

                    // Step C: Convert Binary String back to Encrypted Bytes
                    byte[] encryptedBytes = HammingUtil.binaryStringToBytes(decodedBinary);

                    // Step D: RSA Decrypt
                    try {
                        byte[] decryptedBytes = RSAUtil.decrypt(encryptedBytes, server.getServerPrivateKey());
                        String finalDecryptedContent = RSAUtil.bytesToString(decryptedBytes);
                        receivedMessage.setFinalDecryptedContent(finalDecryptedContent);
                        // System.out.println("RSA Decrypted. Final Content: '" + finalDecryptedContent + "'"); // Too verbose


                        // --- Server-side Routing ---
                        server.routeMessage(receivedMessage, this); // Pass the message and the sender handler

                    } catch (BadPaddingException e) {
                        // This might happen if decryption fails (e.g., due to uncorrectable error or tampering)
                        System.err.println("RSA Decryption failed (BadPaddingException) for message from " + receivedMessage.getSender() + ". Likely corrupt. " + e.getMessage());
                        receivedMessage.setFinalDecryptedContent("[DECRYPTION FAILED]"); // Set error text
                        // Route the message anyway so clients see the failure
                        server.routeMessage(receivedMessage, this);
                    } catch (Exception e) {
                        // Other decryption errors
                        System.err.println("RSA Decryption failed unexpectedly for message from " + receivedMessage.getSender() + ": " + e.getMessage());
                        e.printStackTrace();
                        receivedMessage.setFinalDecryptedContent("[DECRYPTION ERROR]"); // Set error text
                        server.routeMessage(receivedMessage, this); // Route the message with error info
                    }
                }

            } catch (SocketException e) {
                System.out.println("Client '" + username + "' disconnected (SocketException).");
            } catch (EOFException e) {
                System.out.println("Client '" + username + "' disconnected (EOFException).");
            } catch (IOException e) {
                System.err.println("IO error with client '" + username + "': " + e.getMessage());
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                System.err.println("Received unknown object from client '" + username + "': " + e.getMessage());
                e.printStackTrace();
            } finally {
                // Clean up
                try {
                    if (inputStream != null) inputStream.close();
                    if (outputStream != null) outputStream.close();
                    if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing client socket/streams for '" + username + "': " + e.getMessage());
                }
                server.removeClient(this.username); // Remove this handler from the server's map
            }
        }

        // Method for server to send a message object to this specific client
        public void sendMessage(Message msg) {
            try {
                if (outputStream != null) {
                    outputStream.writeObject(msg);
                    outputStream.flush();
                    // System.out.println("Sent message object to client '" + username + "'."); // Too verbose
                }
            } catch (IOException e) {
                System.err.println("Error sending message to client '" + username + "': " + e.getMessage());
                // Consider removing client if sending fails consistently
                // server.removeClient(this.username); // Or handle appropriately
            }
        }
    }

    public static void main(String[] args) {
        new Server(); // Start the server
    }
}