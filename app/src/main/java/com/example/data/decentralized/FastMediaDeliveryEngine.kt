package com.example.data.decentralized

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object FastMediaDeliveryEngine {
    private const val TAG = "FastMediaDelivery"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun computeSha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Smart Image Compressor: Reduces large image bytes to high quality lightweight JPEG (< 350KB)
     */
    fun compressImageIfNeeded(rawBytes: ByteArray, fileName: String?): ByteArray {
        val isImage = fileName?.lowercase()?.let {
            it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp")
        } ?: false

        if (!isImage || rawBytes.size <= 250 * 1024) {
            return rawBytes
        }

        return try {
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return rawBytes

            // Scale down if dimensions are huge
            val maxDimension = 1600
            val width = bitmap.width
            val height = bitmap.height
            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                val ratio = if (width > height) maxDimension.toFloat() / width else maxDimension.toFloat() / height
                val targetW = (width * ratio).toInt().coerceAtLeast(1)
                val targetH = (height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            } else {
                bitmap
            }

            val outStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outStream)
            val compressed = outStream.toByteArray()
            Log.d(TAG, "Compressed image from ${rawBytes.size / 1024}KB to ${compressed.size / 1024}KB")
            compressed
        } catch (e: Exception) {
            Log.w(TAG, "Failed compressing image, using raw bytes", e)
            rawBytes
        }
    }

    /**
     * Uploads encrypted binary blob to reliable global CDNs
     * Returns direct HTTPS download URL or null
     */
    suspend fun uploadEncryptedBlob(
        encryptedBytes: ByteArray,
        fileName: String = "secure_blob.bin"
    ): String? = withContext(Dispatchers.IO) {
        val safeFileName = if (fileName.endsWith(".bin")) fileName else "$fileName.bin"

        // Provider 1: tmpfiles.org (Instant direct upload API)
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    safeFileName,
                    encryptedBytes.toRequestBody("application/octet-stream".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("https://tmpfiles.org/api/v1/upload")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            response.close()

            if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                val json = JSONObject(responseBody)
                if (json.optString("status") == "success") {
                    val pageUrl = json.getJSONObject("data").getString("url")
                    // Convert tmpfiles.org/XXXX/file -> tmpfiles.org/dl/XXXX/file for direct binary download
                    val directUrl = pageUrl.replace("tmpfiles.org/", "tmpfiles.org/dl/")
                    Log.d(TAG, "Uploaded via tmpfiles: $directUrl")
                    return@withContext directUrl
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tmpfiles upload failed: ${e.message}")
        }

        // Provider 2: Catbox.moe (Ultra high availability anonymous upload)
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("reqtype", "fileupload")
                .addFormDataPart(
                    "fileToUpload",
                    safeFileName,
                    encryptedBytes.toRequestBody("application/octet-stream".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("https://catbox.moe/user/api.php")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val resultUrl = response.body?.string()?.trim()
            response.close()

            if (response.isSuccessful && !resultUrl.isNullOrEmpty() && resultUrl.startsWith("http")) {
                Log.d(TAG, "Uploaded via Catbox: $resultUrl")
                return@withContext resultUrl
            }
        } catch (e: Exception) {
            Log.w(TAG, "Catbox upload failed: ${e.message}")
        }

        // Provider 3: 0x0.st (Fast null pointer CDN)
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    safeFileName,
                    encryptedBytes.toRequestBody("application/octet-stream".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("https://0x0.st")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val resultUrl = response.body?.string()?.trim()
            response.close()

            if (response.isSuccessful && !resultUrl.isNullOrEmpty() && resultUrl.startsWith("http")) {
                Log.d(TAG, "Uploaded via 0x0.st: $resultUrl")
                return@withContext resultUrl
            }
        } catch (e: Exception) {
            Log.w(TAG, "0x0.st upload failed: ${e.message}")
        }

        // Provider 4: Uguu.se (Ephemeral fast upload)
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "files[]",
                    safeFileName,
                    encryptedBytes.toRequestBody("application/octet-stream".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("https://uguu.se/upload?output=json")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            response.close()

            if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                val json = JSONObject(responseBody)
                val filesArray = json.optJSONArray("files")
                if (filesArray != null && filesArray.length() > 0) {
                    val directUrl = filesArray.getJSONObject(0).getString("url")
                    Log.d(TAG, "Uploaded via Uguu: $directUrl")
                    return@withContext directUrl
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Uguu upload failed: ${e.message}")
        }

        null
    }

    /**
     * Downloads encrypted binary blob from direct URL
     */
    suspend fun downloadEncryptedBlob(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val bytes = response.body!!.bytes()
                response.close()
                if (bytes.isNotEmpty()) {
                    Log.d(TAG, "Downloaded encrypted blob (${bytes.size} bytes) from $url")
                    return@withContext bytes
                }
            } else {
                response.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download failed from $url: ${e.message}")
        }
        null
    }
}
