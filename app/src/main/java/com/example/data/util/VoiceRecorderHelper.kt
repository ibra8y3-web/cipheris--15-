package com.example.data.util

import android.content.Context
import android.media.MediaRecorder
import java.io.File

object VoiceRecorderHelper {
    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null

    fun startRecording(context: Context): String? {
        return try {
            stopRecording() // Clean up any active sessions

            val outputDir = File(context.cacheDir, "cipheris_voice")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            val file = File(outputDir, "voice_record_${System.currentTimeMillis()}.m4a")
            currentFile = file

            // Using the standard constructor for safety and maximum compatibility
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun stopRecording(): String? {
        return try {
            val path = currentFile?.absolutePath
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    // Suppress if stop is called immediately without audio samples
                }
                release()
            }
            mediaRecorder = null
            currentFile = null
            path
        } catch (e: Exception) {
            e.printStackTrace()
            mediaRecorder = null
            currentFile = null
            null
        }
    }

    fun isRecording(): Boolean {
        return mediaRecorder != null
    }
}
