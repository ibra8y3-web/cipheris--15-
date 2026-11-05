package com.example.api;

/**
 * Callback interface for third-party apps to receive real-time messaging events.
 */
interface ICipherisApiCallback {
    /**
     * Called when a new secure message is received.
     */
    void onMessageReceived(String senderNodeId, String text, long timestamp);

    /**
     * Called when a message delivery status changes (e.g., sent, delivered).
     */
    void onMessageStatusChanged(String messageId, int status);
}
