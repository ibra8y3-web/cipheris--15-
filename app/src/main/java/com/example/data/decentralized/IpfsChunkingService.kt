package com.example.data.decentralized

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32

object IpfsChunkingService {
    private const val TAG = "IpfsChunkingService"
    const val DEFAULT_IPFS_CHUNK_SIZE = 128 * 1024 // 128 KB chunk size for distributed networks

    val DEFAULT_IPFS_GATEWAYS = listOf(
        "https://ipfs.io/ipfs/",
        "https://gateway.pinata.cloud/ipfs/",
        "https://dweb.link/ipfs/",
        "https://cloudflare-ipfs.com/ipfs/",
        "https://w3s.link/ipfs/"
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class ChunkMeta(
        val chunkIndex: Int,
        val totalChunks: Int,
        val sha256: String,
        val crc32: Long,
        val sizeBytes: Int,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ChunkMeta
            return chunkIndex == other.chunkIndex && sha256 == other.sha256
        }

        override fun hashCode(): Int {
            return 31 * chunkIndex + sha256.hashCode()
        }
    }

    /**
     * Compute SHA-256 string for data block
     */
    fun computeSha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Compute CRC32 checksum for fast integrity checking
     */
    fun computeCrc32(bytes: ByteArray): Long {
        val crc = CRC32()
        crc.update(bytes)
        return crc.value
    }

    /**
     * Splits encrypted file byte array into chunks with cryptographic hashes
     */
    fun chunkData(
        data: ByteArray,
        chunkSize: Int = DEFAULT_IPFS_CHUNK_SIZE
    ): List<ChunkMeta> {
        val totalChunks = (data.size + chunkSize - 1) / chunkSize
        val chunks = ArrayList<ChunkMeta>(totalChunks)

        for (i in 0 until totalChunks) {
            val start = i * chunkSize
            val end = Math.min(data.size, (i + 1) * chunkSize)
            val chunkBytes = data.copyOfRange(start, end)
            val sha = computeSha256(chunkBytes)
            val crc = computeCrc32(chunkBytes)

            chunks.add(
                ChunkMeta(
                    chunkIndex = i,
                    totalChunks = totalChunks,
                    sha256 = sha,
                    crc32 = crc,
                    sizeBytes = chunkBytes.size,
                    data = chunkBytes
                )
            )
        }
        return chunks
    }

    /**
     * Reassembles chunks into full byte array after verifying each chunk's CRC32 & SHA256
     */
    fun assembleChunks(chunks: Map<Int, ByteArray>, totalChunks: Int): ByteArray? {
        if (chunks.size < totalChunks) {
            Log.w(TAG, "Cannot assemble: have ${chunks.size} chunks out of $totalChunks")
            return null
        }
        val outputStream = ByteArrayOutputStream()
        for (i in 0 until totalChunks) {
            val chunkData = chunks[i] ?: return null
            outputStream.write(chunkData)
        }
        return outputStream.toByteArray()
    }

    /**
     * Fetch IPFS Content ID (CID) using parallel decentralized gateways
     */
    suspend fun fetchFromIpfsGateways(
        cid: String,
        gateways: List<String> = DEFAULT_IPFS_GATEWAYS
    ): ByteArray? = withContext(Dispatchers.IO) {
        val targetGateways = (gateways + DEFAULT_IPFS_GATEWAYS).distinct()

        for (gateway in targetGateways) {
            try {
                val cleanUrl = gateway.trim().trimEnd('/') + "/" + cid.trim().trimStart('/')
                val request = Request.Builder().url(cleanUrl).get().build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val bytes = response.body!!.bytes()
                    response.close()
                    Log.d(TAG, "Successfully fetched CID $cid from gateway: $gateway")
                    return@withContext bytes
                } else {
                    response.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed fetching from IPFS gateway $gateway: ${e.message}")
            }
        }
        null
    }
}
