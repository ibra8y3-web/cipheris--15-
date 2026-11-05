package com.example.data.util

import java.io.File
import java.io.FileInputStream

object MediaClassifier {
    
    enum class MediaType {
        VIDEO, AUDIO, IMAGE, PDF, MS_OFFICE, ARCHIVE, TEXT, CODE, DATA, EPUB, UNKNOWN
    }

    fun classifyFile(file: File?): MediaType {
        if (file == null || !file.exists() || file.length() == 0L) return MediaType.UNKNOWN

        try {
            val bytes = ByteArray(32)
            FileInputStream(file).use { it.read(bytes) }
            
            // Magic numbers check
            if (bytes.size >= 4) {
                // PDF: %PDF
                if (bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte()) {
                    return MediaType.PDF
                }
                
                // ZIP/JAR/APK/EPUB/DOCX/XLSX: PK..
                if (bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) {
                    val name = file.name.lowercase()
                    if (name.endsWith(".epub")) return MediaType.EPUB
                    if (name.endsWith(".docx") || name.endsWith(".xlsx") || name.endsWith(".pptx")) return MediaType.MS_OFFICE
                    return MediaType.ARCHIVE
                }
                
                // RAR: Rar!
                if (bytes[0] == 0x52.toByte() && bytes[1] == 0x61.toByte() && bytes[2] == 0x72.toByte() && bytes[3] == 0x21.toByte()) {
                    return MediaType.ARCHIVE
                }
                
                // 7Z: 7zBCAF271C
                if (bytes.size >= 6 && bytes[0] == 0x37.toByte() && bytes[1] == 0x7A.toByte() && bytes[2] == 0xBC.toByte() && bytes[3] == 0xAF.toByte() && bytes[4] == 0x27.toByte() && bytes[5] == 0x1C.toByte()) {
                    return MediaType.ARCHIVE
                }

                // GZ: 1f 8b
                if (bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
                    return MediaType.ARCHIVE
                }
            }
            
            // Image headers
            if (bytes.size >= 3) {
                // JPEG: FF D8 FF
                if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
                    return MediaType.IMAGE
                }
            }
            if (bytes.size >= 4) {
                // PNG: 89 50 4E 47
                if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
                    return MediaType.IMAGE
                }
                // GIF: GIF8
                if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte()) {
                    return MediaType.IMAGE
                }
            }
            if (bytes.size >= 12) {
                // WEBP: RIFF ... WEBP
                if (bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
                    bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()) {
                    return MediaType.IMAGE
                }
            }
            
            // Audio headers
            if (bytes.size >= 4) {
                // MP3: ID3 or FF FB
                if ((bytes[0] == 0x49.toByte() && bytes[1] == 0x44.toByte() && bytes[2] == 0x33.toByte()) ||
                    (bytes[0] == 0xFF.toByte() && (bytes[1] == 0xFB.toByte() || bytes[1] == 0xFA.toByte() || bytes[1] == 0xF3.toByte() || bytes[1] == 0xF2.toByte()))) {
                    return MediaType.AUDIO
                }
                // FLAC: fLaC
                if (bytes[0] == 0x66.toByte() && bytes[1] == 0x4C.toByte() && bytes[2] == 0x61.toByte() && bytes[3] == 0x43.toByte()) {
                    return MediaType.AUDIO
                }
                // OGG: OggS
                if (bytes[0] == 0x4F.toByte() && bytes[1] == 0x67.toByte() && bytes[2] == 0x67.toByte() && bytes[3] == 0x53.toByte()) {
                    return MediaType.AUDIO
                }
            }
            if (bytes.size >= 12) {
                // WAV: RIFF ... WAVE
                if (bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
                    bytes[8] == 0x57.toByte() && bytes[9] == 0x41.toByte() && bytes[10] == 0x56.toByte() && bytes[11] == 0x45.toByte()) {
                    return MediaType.AUDIO
                }
            }
            
            // Video headers
            if (bytes.size >= 12) {
                // MP4: .... ftyp
                if (bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() && bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte()) {
                    // Check sub-brand for M4A (audio)
                    if (bytes[8] == 0x4D.toByte() && bytes[9] == 0x34.toByte() && bytes[10] == 0x41.toByte() && bytes[11] == 0x20.toByte()) {
                        return MediaType.AUDIO
                    }
                    return MediaType.VIDEO
                }
                // MKV/WebM: 1A 45 DF A3
                if (bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() && bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte()) {
                    return MediaType.VIDEO
                }
            }
            
            // M4A (AAC / voice record) check
            val name = file.name.lowercase()
            if (name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".amr") || name.contains("صوت_مشفر")) {
                return MediaType.AUDIO
            }
            
            // Code/Text/Data fallback
            if (name.endsWith(".txt") || name.endsWith(".rtf")) return MediaType.TEXT
            if (name.endsWith(".csv") || name.endsWith(".tsv")) return MediaType.DATA
            if (name.endsWith(".kt") || name.endsWith(".java") || name.endsWith(".py") || name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".html") || name.endsWith(".css") || name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".yaml") || name.endsWith(".md")) {
                return MediaType.CODE
            }
            
            // Old MS Office: D0 CF 11 E0 A1 B1 1A E1
            if (bytes.size >= 8 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()) {
                return MediaType.MS_OFFICE
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return MediaType.UNKNOWN
    }
}
