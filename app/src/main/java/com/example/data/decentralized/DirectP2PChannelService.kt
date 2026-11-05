package com.example.data.decentralized

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest

object DirectP2PChannelService {
    private const val TAG = "DirectP2PChannel"
    const val P2P_DATA_PORT = 51028
    const val SOCKET_TIMEOUT_MS = 6000

    private var serverSocket: ServerSocket? = null
    private var isListening = false

    /**
     * Start local P2P listener socket for direct peer incoming files
     */
    fun startLocalP2PListener(onFileReceived: (fileName: String, data: ByteArray) -> Unit) {
        if (isListening) return
        isListening = true

        Thread {
            try {
                serverSocket = ServerSocket(P2P_DATA_PORT)
                Log.d(TAG, "Local P2P Direct Server listening on port $P2P_DATA_PORT")

                while (isListening && serverSocket?.isClosed == false) {
                    val clientSocket = serverSocket?.accept() ?: break
                    Thread {
                        handleIncomingP2PStream(clientSocket, onFileReceived)
                    }.start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "P2P Server Socket error: ${e.message}")
            }
        }.start()
    }

    private fun handleIncomingP2PStream(
        socket: Socket,
        onFileReceived: (fileName: String, data: ByteArray) -> Unit
    ) {
        try {
            socket.soTimeout = 15000
            val input: InputStream = socket.getInputStream()
            val headerBuffer = ByteArray(256)
            val headerRead = input.read(headerBuffer)
            if (headerRead <= 0) {
                socket.close()
                return
            }

            val headerStr = String(headerBuffer, 0, headerRead, Charsets.UTF_8).trim()
            val parts = headerStr.split("|")
            val fileName = if (parts.isNotEmpty()) parts[0] else "p2p_file.bin"
            val expectedSize = if (parts.size > 1) parts[1].toLongOrNull() ?: -1L else -1L

            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                if (expectedSize > 0 && outputStream.size() >= expectedSize) {
                    break
                }
            }

            val data = outputStream.toByteArray()
            Log.d(TAG, "Direct P2P received file: $fileName (${data.size} bytes)")
            onFileReceived(fileName, data)

            // Send simple ACK
            socket.getOutputStream().write("ACK\n".toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
            socket.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error in incoming P2P stream: ${e.message}")
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Send raw encrypted bytes directly to peer IP address and port
     */
    suspend fun sendDirectToPeer(
        peerIp: String,
        port: Int = P2P_DATA_PORT,
        fileName: String,
        data: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        if (peerIp.isEmpty() || peerIp == "127.0.0.1" || peerIp == "0.0.0.0") return@withContext false

        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(peerIp, port), SOCKET_TIMEOUT_MS)
            socket.soTimeout = 15000

            val output: OutputStream = socket.getOutputStream()
            val header = "$fileName|${data.size}\n".toByteArray(Charsets.UTF_8)
            output.write(header)
            output.flush()

            output.write(data)
            output.flush()

            val ackBuf = ByteArray(16)
            val ackRead = socket.getInputStream().read(ackBuf)
            socket.close()
            return@withContext (ackRead > 0)
        } catch (e: Exception) {
            Log.w(TAG, "Direct P2P transfer to $peerIp:$port failed: ${e.message}")
            try { socket?.close() } catch (ignored: Exception) {}
            return@withContext false
        }
    }

    fun stopLocalP2PListener() {
        isListening = false
        try {
            serverSocket?.close()
        } catch (ignored: Exception) {}
        serverSocket = null
    }
}
