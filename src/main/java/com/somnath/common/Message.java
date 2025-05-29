// --- src\main\java\com\somnath\common\Message.java ---
package com.somnath.common;
import java.io.Serializable;
import java.util.Base64;

public class Message implements Serializable {
    private static final long serialVersionUID = 3L; // Increment serial version ID again

    private String sender;
    private String recipient; // null for group message, username for private message
    private String fullOriginalText; // The exact string typed by the user (e.g., "@Bob Hello")

    // The actual content that gets encrypted/encoded
    private String contentToEncrypt;

    // States during processing
    private String encryptedContentBase64; // Base64 of RSA encrypted bytes of contentToEncrypt
    private String hammingEncodedBinary; // Binary string after Hamming encoding (client-side before error)
    private String erroredHammingBinary; // Binary string after error simulation (what is sent)
    private String correctedHammingBinary; // Binary string after Hamming correction (server-side)
    private String finalDecryptedContent; // The final readable message body after server decryption

    // Flags to indicate processing outcome
    private boolean errorDetected = false;

    public void setSender(String sender) {
        this.sender = sender;
    }

    public void setEncryptedContentBase64(String encryptedContentBase64) {
        this.encryptedContentBase64 = encryptedContentBase64;
    }

    public void setContentToEncrypt(String contentToEncrypt) {
        this.contentToEncrypt = contentToEncrypt;
    }

    public void setFullOriginalText(String fullOriginalText) {
        this.fullOriginalText = fullOriginalText;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    private boolean errorCorrected = false;

    // --- Add this field to control expanded view ---
    private transient boolean expanded = false; // 'transient' means it won't be serialized/sent over network

    // --- Constructor ---
    // Client will typically create this object
    public Message(String sender, String fullOriginalText, String recipient, String contentToEncrypt) {
        this.sender = sender;
        this.fullOriginalText = fullOriginalText;
        this.recipient = recipient;
        this.contentToEncrypt = contentToEncrypt;
        // Other fields are set later during processing
    }

    // --- Getters (Needed by UI and Server) ---
    public String getSender() { return sender; }
    public String getRecipient() { return recipient; } // null for group
    public String getFullOriginalText() { return fullOriginalText; } // What user typed
    public String getContentToEncrypt() { return contentToEncrypt; } // Just the message body

    public String getEncryptedContentBase64() { return encryptedContentBase64; }
    public String getHammingEncodedBinary() { return hammingEncodedBinary; }
    public String getErroredHammingBinary() { return erroredHammingBinary; }
    public String getCorrectedHammingBinary() { return correctedHammingBinary; }
    public String getFinalDecryptedContent() { return finalDecryptedContent; }

    public boolean isPrivateMessage() { return recipient != null && !recipient.trim().isEmpty(); }
    public boolean isErrorDetected() { return errorDetected; }
    public boolean isErrorCorrected() { return errorCorrected; }

    // --- Getter/Setter for the expanded state ---
    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }


    // --- Setters (Needed by Client/Server to populate) ---
    // Client side populates:
    public void setEncryptedContentBase66(String encryptedContentBase64) { this.encryptedContentBase64 = encryptedContentBase64; }
    public void setHammingEncodedBinary(String hammingEncodedBinary) { this.hammingEncodedBinary = hammingEncodedBinary; }
    public void setErroredHammingBinary(String erroredHammingBinary) { this.erroredHammingBinary = erroredHammingBinary; }

    // Server side populates:
    public void setCorrectedHammingBinary(String correctedHammingBinary) { this.correctedHammingBinary = correctedHammingBinary; }
    public void setFinalDecryptedContent(String finalDecryptedContent) { this.finalDecryptedContent = finalDecryptedContent; }
    public void setErrorDetected(boolean errorDetected) { this.errorDetected = errorDetected; }
    public void setErrorCorrected(boolean errorCorrected) { this.errorCorrected = errorCorrected; }


    @Override
    public String toString() {
        // Basic string for debugging logs
        return "Message{" +
                "sender='" + sender + '\'' +
                ", recipient='" + (recipient == null ? "GROUP" : recipient) + '\'' +
                ", finalDecryptedContent='" + finalDecryptedContent + '\'' +
                ", errorDetected=" + errorDetected +
                ", expanded=" + expanded + // Include expanded state in debug
                '}';
    }
}