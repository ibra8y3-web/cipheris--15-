package com.example.data.p2p

import android.content.Context
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Application-level P2P Media Transfer Protocol with Chunking and Selective ACK.
 * Provides reliable, resumable chunk-based file transfer over lossy P2P channels.
 */
object MediaChunkProtocol {

    const val DEFAULT_CHUNK_SIZE = 64 * 1024 // 64 KB per chunk

    enum class TransferState {
        INITIATED,
        IN_PROGRESS,
        COMPLETED,
        FAILED_INTEGRITY
    }

    /**
     * Handshake metadata frame announcing a new file transfer
     */
    data class TransferHeader(
        val transferId: String,
        val fileName: String,
        val fileSize: Long,
        val chunkSize: Int = DEFAULT_CHUNK_SIZE,
        val totalChunks: Int,
        val fileHashSha256: String
    )

    /**
     * Individual file chunk payload frame
     */
    data class ChunkFrame(
        val transferId: String,
        val chunkIndex: Int,
        val totalChunks: Int,
        val chunkSize: Int,
        val payload: ByteArray,
        val chunkCrc32: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ChunkFrame
            return transferId == other.transferId && chunkIndex == other.chunkIndex
        }

        override fun hashCode(): Int {
            var result = transferId.hashCode()
            result = 31 * result + chunkIndex
            return result
        }
    }

    /**
     * Selective Acknowledgement (ACK) message from receiver to sender
     */
    data class AckFrame(
        val transferId: String,
        val nextExpectedChunk: Int,
        val receivedBitmask: List<Boolean>, // Bitmask for received chunk indices
        val missingChunkIndices: List<Int>,
        val state: TransferState
    )

    /**
     * Helper to compute SHA-256 for file integrity verification
     */
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Splits a file into sequential chunks for P2P transmission
     */
    fun buildHeaderAndChunks(
        transferId: String,
        file: File,
        chunkSize: Int = DEFAULT_CHUNK_SIZE
    ): Pair<TransferHeader, List<ChunkFrame>> {
        val fileSize = file.length()
        val totalChunks = Math.ceil(fileSize.toDouble() / chunkSize).toInt()
        val sha256 = computeSha256(file)

        val header = TransferHeader(
            transferId = transferId,
            fileName = file.name,
            fileSize = fileSize,
            chunkSize = chunkSize,
            totalChunks = totalChunks,
            fileHashSha256 = sha256
        )

        val chunks = mutableListOf<ChunkFrame>()
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(chunkSize)
            var index = 0
            var readBytes: Int
            while (input.read(buffer).also { readBytes = it } != -1) {
                val payloadBytes = buffer.copyOf(readBytes)
                val crc = computeCrc32(payloadBytes)
                chunks.add(
                    ChunkFrame(
                        transferId = transferId,
                        chunkIndex = index,
                        totalChunks = totalChunks,
                        chunkSize = readBytes,
                        payload = payloadBytes,
                        chunkCrc32 = crc
                    )
                )
                index++
            }
        }

        return Pair(header, chunks)
    }

    private fun computeCrc32(bytes: ByteArray): Long {
        val crc = java.util.zip.CRC32()
        crc.update(bytes)
        return crc.value
    }

    /**
     * Receiver-side chunk accumulator and assembler
     */
    class ChunkReceiverSession(
        val header: TransferHeader,
        val destFile: File
    ) {
        private val receivedChunks = HashMap<Int, ByteArray>()

        @Synchronized
        fun processChunk(chunk: ChunkFrame): AckFrame {
            if (computeCrc32(chunk.payload) == chunk.chunkCrc32) {
                receivedChunks[chunk.chunkIndex] = chunk.payload
            }

            val missingList = mutableListOf<Int>()
            val bitmask = mutableListOf<Boolean>()
            for (i in 0 until header.totalChunks) {
                val hasChunk = receivedChunks.containsKey(i)
                bitmask.add(hasChunk)
                if (!hasChunk) {
                    missingList.add(i)
                }
            }

            val isComplete = receivedChunks.size == header.totalChunks
            val state = if (isComplete) TransferState.COMPLETED else TransferState.IN_PROGRESS

            if (isComplete) {
                assembleAndVerify()
            }

            return AckFrame(
                transferId = header.transferId,
                nextExpectedChunk = missingList.firstOrNull() ?: header.totalChunks,
                receivedBitmask = bitmask,
                missingChunkIndices = missingList,
                state = state
            )
        }

        private fun assembleAndVerify() {
            try {
                RandomAccessFile(destFile, "rw").use { raf ->
                    for (i in 0 until header.totalChunks) {
                        val data = receivedChunks[i] ?: continue
                        raf.write(data)
                    }
                }
                val actualHash = computeSha256(destFile)
                if (actualHash.equals(header.fileHashSha256, ignoreCase = true)) {
                    Log.d("MediaChunkProtocol", "File transfer verified successfully: ${destFile.name}")
                } else {
                    Log.e("MediaChunkProtocol", "File hash mismatch for ${destFile.name}")
                }
            } catch (e: Exception) {
                Log.e("MediaChunkProtocol", "Error assembling chunks into file", e)
            }
        }
    }
}
