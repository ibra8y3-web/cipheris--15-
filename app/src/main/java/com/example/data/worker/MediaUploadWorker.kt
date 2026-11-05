package com.example.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background WorkManager worker dedicated to processing media dispatch queues.
 * Guarantees reliable media upload and transmission even if the app process is closed or suspended.
 */
class MediaUploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val messageId = inputData.getString(KEY_MESSAGE_ID)
        val peerAddress = inputData.getString(KEY_PEER_ADDRESS)
        val mediaPath = inputData.getString(KEY_MEDIA_PATH)
        val isGroup = inputData.getBoolean(KEY_IS_GROUP, false)
        val channelId = inputData.getString(KEY_CHANNEL_ID)

        Log.d("MediaUploadWorker", "Processing background media upload for msg: $messageId, path: $mediaPath")

        try {
            // WorkManager handles execution in background thread with automatic retries
            val repository = com.example.CipherisApplication.instance.repository

            if (!channelId.isNullOrEmpty()) {
                // Background channel post dispatch
                repository.postToChannel(
                    channelId = channelId,
                    text = inputData.getString(KEY_CONTENT) ?: "",
                    mediaPath = mediaPath,
                    mediaName = inputData.getString(KEY_MEDIA_NAME)
                )
            } else if (!peerAddress.isNullOrEmpty()) {
                // Background direct or group message dispatch
                repository.sendMessage(
                    peerNodeId = if (!isGroup) peerAddress else null,
                    groupId = if (isGroup) peerAddress else null,
                    text = inputData.getString(KEY_CONTENT) ?: "",
                    mediaPath = mediaPath,
                    mediaName = inputData.getString(KEY_MEDIA_NAME)
                )
            }

            Log.d("MediaUploadWorker", "Media upload completed successfully for msg: $messageId")
            Result.success()
        } catch (e: Exception) {
            Log.e("MediaUploadWorker", "Media upload failed, retrying...", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(
                    Data.Builder().putString("error", e.message ?: "Max retries reached").build()
                )
            }
        }
    }

    companion object {
        const val KEY_MESSAGE_ID = "message_id"
        const val KEY_PEER_ADDRESS = "peer_address"
        const val KEY_MEDIA_PATH = "media_path"
        const val KEY_MEDIA_NAME = "media_name"
        const val KEY_CONTENT = "content"
        const val KEY_IS_GROUP = "is_group"
        const val KEY_CHANNEL_ID = "channel_id"
    }
}
