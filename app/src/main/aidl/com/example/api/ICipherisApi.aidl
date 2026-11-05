package com.example.api;

import com.example.api.ICipherisApiCallback;

/**
 * Main API interface for third-party integration with the decentralized messaging app.
 * Maintains end-to-end encryption under the hood.
 */
interface ICipherisApi {
    /**
     * Registers a callback for incoming events. 
     * Requires the developer's registered App ID or API Key for verification.
     */
    boolean registerCallback(ICipherisApiCallback callback, String appId);

    /**
     * Unregisters a previously bound callback.
     */
    void unregisterCallback(ICipherisApiCallback callback);

    /**
     * Sends a secure message to a peer node via the decentralized network.
     * @param targetPeerId The destination node ID.
     * @param content The message text to send.
     * @return A unique message ID for tracking status.
     */
    String sendMessage(String targetPeerId, String content);

    /**
     * Retrieves the current network connection status of the decentralized node.
     * @return integer status code (0: Disconnected, 1: Connecting, 2: Connected)
     */
    int getConnectionStatus();
}
