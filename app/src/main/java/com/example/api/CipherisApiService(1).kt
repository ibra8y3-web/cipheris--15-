package com.example.api

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.RemoteCallbackList
import android.util.Log
import com.example.CipherisApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CipherisApiService : Service() {

    private val callbacks = RemoteCallbackList<ICipherisApiCallback>()
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val repository by lazy { CipherisApplication.instance.repository }

    private fun isApiEnabled(): Boolean {
        val prefs = getSharedPreferences("cipheris_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("developer_api_enabled", false)
    }

    private val binder = object : ICipherisApi.Stub() {
        override fun registerCallback(callback: ICipherisApiCallback?, appId: String?): Boolean {
            if (!isApiEnabled()) {
                Log.w("CipherisApiService", "Developer API is disabled by user.")
                return false
            }
            if (callback == null || appId == null) return false
            Log.i("CipherisApiService", "Registering third-party callback for appId: $appId")
            return callbacks.register(callback)
        }

        override fun unregisterCallback(callback: ICipherisApiCallback?) {
            if (callback != null) {
                callbacks.unregister(callback)
                Log.i("CipherisApiService", "Unregistered third-party callback")
            }
        }

        override fun sendMessage(targetPeerId: String?, content: String?): String {
            if (!isApiEnabled()) {
                throw SecurityException("Developer API is disabled by user.")
            }
            if (targetPeerId.isNullOrEmpty() || content.isNullOrEmpty()) {
                throw IllegalArgumentException("targetPeerId and content must not be empty")
            }
            
            val messageId = java.util.UUID.randomUUID().toString()
            
            serviceScope.launch {
                try {
                    repository.sendMessage(
                        peerNodeId = targetPeerId,
                        groupId = null,
                        text = content,
                        mediaPath = null,
                        mediaName = null
                    )
                } catch (e: Exception) {
                    Log.e("CipherisApiService", "Error sending API message", e)
                }
            }
            
            return messageId
        }

        override fun getConnectionStatus(): Int {
            if (!isApiEnabled()) return 0
            return when (repository.networkStatus.value) {
                "متصل ومستقر" -> 2
                "غير متصل" -> 0
                else -> 1 // Connecting or weak
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("CipherisApiService", "Cipheris API Service Started")
        
        serviceScope.launch {
            repository.apiMessageFlow.collect { message ->
                if (!isApiEnabled()) return@collect

                val sender = message.peerNodeId ?: message.groupId ?: "Unknown"
                val text = message.text
                val timestamp = message.timestamp
                
                val n = callbacks.beginBroadcast()
                for (i in 0 until n) {
                    try {
                        callbacks.getBroadcastItem(i).onMessageReceived(sender, text, timestamp)
                    } catch (e: Exception) {
                        Log.e("CipherisApiService", "Error broadcasting message to callback", e)
                    }
                }
                callbacks.finishBroadcast()
            }
        }
    }


    override fun onBind(intent: Intent?): IBinder {
        Log.i("CipherisApiService", "Client bound to Cipheris API")
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        callbacks.kill()
        Log.i("CipherisApiService", "Cipheris API Service Destroyed")
    }
}
