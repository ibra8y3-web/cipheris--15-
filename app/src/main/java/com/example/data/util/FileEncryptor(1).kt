package com.example.data.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object FileEncryptor {

    const val DEFAULT_KEY = "CipherisSecureFiles_2026"

    /**
     * Helper to perform XOR encryption/decryption on byte array with a given string key
     */
    fun xorTransform(bytes: ByteArray, key: String): ByteArray {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val result = ByteArray(bytes.size)
        for (i in bytes.indices) {
            result[i] = (bytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }
        return result
    }

    /**
     * Reads a file from a Uri or path, encrypts its content using XOR with a security key,
     * and saves it to the secure private directory.
     * Returns the absolute path to the encrypted file.
     */
    fun encryptFile(context: Context, uriStr: String, customKey: String? = null): String? {
        val key = customKey?.ifEmpty { DEFAULT_KEY } ?: DEFAULT_KEY
        return try {
            val uri = Uri.parse(uriStr)
            val contentResolver = context.contentResolver
            
            var fileName = "file_${System.currentTimeMillis()}"
            var originalBytes: ByteArray? = null

            if (uri.scheme == "content") {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                }
                originalBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } else {
                val path = uri.path ?: uriStr
                val f = File(path)
                if (f.exists() && f.length() > 0) {
                    fileName = f.name
                    originalBytes = FileInputStream(f).use { it.readBytes() }
                }
            }

            if (originalBytes == null || originalBytes.isEmpty()) return null

            val encryptedBytes = xorTransform(originalBytes, key)

            // Write to private storage
            val secureDir = File(context.filesDir, "cipheris_secure_files")
            if (!secureDir.exists()) {
                secureDir.mkdirs()
            }

            // Clean original filename for storage safety
            val cleanName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val uniqueFileName = "secured_${System.currentTimeMillis()}_$cleanName"
            val encryptedFile = File(secureDir, uniqueFileName)
            
            FileOutputStream(encryptedFile).use { outputStream ->
                outputStream.write(encryptedBytes)
            }

            encryptedFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Helper to perform XOR decryption on byte array with a given string key
     */
    private fun xorDecrypt(bytes: ByteArray, key: String): ByteArray {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val decrypted = ByteArray(bytes.size)
        for (i in bytes.indices) {
            decrypted[i] = (bytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }
        return decrypted
    }

    /**
     * Checks if decrypted bytes match a known valid file header magic signature
     */
    private fun hasValidFileHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 3) return false
        
        // JPEG: FF D8 FF
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return true
        
        // PNG: 89 50 4E 47
        if (bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return true
        
        // GIF: GIF8
        if (bytes.size >= 4 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte()) return true
        
        // PDF: %PDF
        if (bytes.size >= 4 && bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte()) return true
        
        // MP3: ID3 or FF FB
        if ((bytes[0] == 0x49.toByte() && bytes[1] == 0x44.toByte() && bytes[2] == 0x33.toByte()) ||
            (bytes[0] == 0xFF.toByte() && (bytes[1] == 0xFB.toByte() || bytes[1] == 0xFA.toByte()))) return true

        // OGG: OggS
        if (bytes.size >= 4 && bytes[0] == 0x4F.toByte() && bytes[1] == 0x67.toByte() && bytes[2] == 0x67.toByte() && bytes[3] == 0x53.toByte()) return true

        // WAV/RIFF: RIFF
        if (bytes.size >= 4 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte()) return true

        // ZIP/Jar/APK/Docx/Xlsx: PK..
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) return true

        // MP4 / M4A: ftyp at offset 4
        if (bytes.size >= 8 && bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() && bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte()) return true

        return false
    }

    /**
     * Reads an encrypted file, decrypts it, and saves it to a temp cache file.
     * Smartly detects header magic bytes to use customKey or DEFAULT_KEY.
     * Returns the absolute path to the decrypted temp file.
     */
    fun decryptFileToTemp(context: Context, encryptedPath: String, customKey: String? = null): String? {
        return try {
            val encryptedFile = File(encryptedPath)
            if (!encryptedFile.exists() || encryptedFile.length() == 0L) return null

            val encryptedBytes = FileInputStream(encryptedFile).use { it.readBytes() }
            if (encryptedBytes.isEmpty()) return null

            var decryptedBytes: ByteArray? = null
            
            // Try customKey if provided
            if (!customKey.isNullOrEmpty()) {
                val candidateBytes = xorTransform(encryptedBytes, customKey)
                if (hasValidFileHeader(candidateBytes)) {
                    decryptedBytes = candidateBytes
                }
            }

            // Fallback to DEFAULT_KEY if not yet resolved
            if (decryptedBytes == null) {
                val defaultBytes = xorTransform(encryptedBytes, DEFAULT_KEY)
                decryptedBytes = defaultBytes
            }

            // Get original name component
            val originalName = encryptedFile.name.substringAfter("secured_", "temp_file")

            // Write to cache storage
            val cacheDir = context.cacheDir
            val tempFile = File(cacheDir, "decrypted_$originalName")
            
            FileOutputStream(tempFile).use { outputStream ->
                outputStream.write(decryptedBytes)
            }

            tempFile.deleteOnExit()
            tempFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Tries to find an encrypted file in the secure directory that matches the given mediaName,
     * decrypts it, and returns the absolute path to the temp decrypted file.
     */
    fun findAndDecryptFileToTemp(context: Context, mediaName: String, customKey: String? = null): String? {
        return try {
            val secureDir = File(context.filesDir, "cipheris_secure_files")
            if (!secureDir.exists()) return null

            val matchedFile = secureDir.listFiles()?.find { file ->
                file.name.contains(mediaName) || file.name.substringAfter("secured_", "").contains(mediaName)
            } ?: return null

            decryptFileToTemp(context, matchedFile.absolutePath, customKey)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
