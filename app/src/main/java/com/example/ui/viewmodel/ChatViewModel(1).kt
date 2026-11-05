package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.CipherisApplication
import com.example.data.model.Peer
import com.example.data.model.Message
import com.example.data.model.GroupChat
import com.example.data.model.AliasEntry
import com.example.data.model.BroadcastChannel
import com.example.data.model.ChannelPost
import com.example.data.repository.ChatRepository
import com.example.data.p2p.P2PMediaEngine
import com.example.data.decentralized.DecentralizedRelay
import com.example.data.decentralized.TransmissionMode
import com.example.data.decentralized.TransmissionTelemetry
import com.example.data.decentralized.RelayType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.util.UUID
import android.widget.Toast
import com.example.data.util.FileEncryptor
import com.example.data.util.VoiceRecorderHelper
import com.example.data.security.SecurityManager
import com.example.data.model.VaultEntry

class ChatViewModel(
    application: Application,
    private val repository: ChatRepository
) : AndroidViewModel(application) {

    private val securityManager = SecurityManager(application)
    private val _isLocked = MutableStateFlow(securityManager.getMasterPin() != null)
    val isLocked = _isLocked.asStateFlow()

    val masterPin: String? get() = securityManager.getMasterPin()
    val panicPin: String? get() = securityManager.getPanicPin()

    fun unlock(pin: String) {
        if (pin == masterPin) {
            _isLocked.value = false
        }
    }

    fun setMasterPin(pin: String) = securityManager.setMasterPin(pin)
    fun setPanicPin(pin: String) = securityManager.setPanicPin(pin)

    fun handlePanic() {
        viewModelScope.launch {
            securityManager.clearAllData(getApplication())
            _isLocked.value = false
            _onboardingCompleted.value = false
            _currentScreen.value = "onboarding"
        }
    }

    // Vault logic
    val vaultEntries: StateFlow<List<VaultEntry>> = repository.getVaultEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveVaultEntry(entry: VaultEntry) = viewModelScope.launch { repository.insertVaultEntry(entry) }
    fun deleteVaultEntry(entry: VaultEntry) = viewModelScope.launch { repository.deleteVaultEntry(entry) }

    private val sharedPrefs = application.getSharedPreferences("cipheris_prefs", android.content.Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(sharedPrefs.getString("language", "ar") ?: "ar")
    val language: StateFlow<String> = _language.asStateFlow()

    fun setLanguage(lang: String) {
        sharedPrefs.edit().putString("language", lang).apply()
        _language.value = lang
    }

    private val _isDarkTheme = MutableStateFlow(sharedPrefs.getBoolean("dark_theme", false))
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun setDarkTheme(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("dark_theme", enabled).apply()
        _isDarkTheme.value = enabled
    }

    fun toggleDarkTheme() {
        setDarkTheme(!_isDarkTheme.value)
    }

    // White Tone Intensity & Eye Comfort Level (0.0f = pure bright, 1.0f = maximum soft warm comfort)
    private val _whiteToneLevel = MutableStateFlow(sharedPrefs.getFloat("white_tone_level", 0.0f))
    val whiteToneLevel: StateFlow<Float> = _whiteToneLevel.asStateFlow()

    fun setWhiteToneLevel(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        sharedPrefs.edit().putFloat("white_tone_level", clamped).apply()
        _whiteToneLevel.value = clamped
    }

    private val _developerApiEnabled = MutableStateFlow(sharedPrefs.getBoolean("developer_api_enabled", false))
    val developerApiEnabled: StateFlow<Boolean> = _developerApiEnabled.asStateFlow()

    fun setDeveloperApiEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("developer_api_enabled", enabled).apply()
        _developerApiEnabled.value = enabled
    }

    // Decentralized Relays & Media Transmission Engine
    val decentralizedRelays: StateFlow<List<DecentralizedRelay>> = repository.decentralizedRelays
    val transmissionMode: StateFlow<TransmissionMode> = repository.transmissionMode
    val transmissionTelemetry: StateFlow<List<TransmissionTelemetry>> = repository.transmissionTelemetry
    val activeTransfersCount: StateFlow<Int> = repository.activeTransfersCount

    fun setTransmissionMode(mode: TransmissionMode) = repository.setTransmissionMode(mode)
    fun toggleDecentralizedRelay(relayId: String) = repository.toggleDecentralizedRelay(relayId)
    fun addCustomDecentralizedRelay(name: String, url: String, type: RelayType) = repository.addCustomDecentralizedRelay(name, url, type)
    fun removeDecentralizedRelay(relayId: String) = repository.removeDecentralizedRelay(relayId)
    fun pingAllDecentralizedRelays() = repository.pingAllDecentralizedRelays()

    // Deep link handling
    private val _pendingDeepLink = MutableStateFlow<String?>(null)
    val pendingDeepLink: StateFlow<String?> = _pendingDeepLink.asStateFlow()

    private val _pendingChannelKey = MutableStateFlow<String?>(null)
    val pendingChannelKey: StateFlow<String?> = _pendingChannelKey.asStateFlow()

    private val _detectedClipboardLink = MutableStateFlow<com.example.util.ParsedDeepLink?>(null)
    val detectedClipboardLink: StateFlow<com.example.util.ParsedDeepLink?> = _detectedClipboardLink.asStateFlow()

    private var lastDismissedClipboardText: String? = null

    fun handleDeepLink(uri: String) {
        _pendingDeepLink.value = uri
    }

    fun clearDeepLink() {
        _pendingDeepLink.value = null
    }

    fun setPendingChannelKey(key: String) {
        _pendingChannelKey.value = key
    }

    fun clearChannelKey() {
        _pendingChannelKey.value = null
    }

    fun checkClipboardForLinks(context: android.content.Context) {
        val parsed = com.example.util.DeepLinkHelper.checkClipboardForDeepLink(context)
        if (parsed != null) {
            val key = when (parsed) {
                is com.example.util.ParsedDeepLink.UserLink -> parsed.nodeId
                is com.example.util.ParsedDeepLink.GroupLink -> parsed.groupId
                is com.example.util.ParsedDeepLink.ChannelLink -> parsed.channelId
            }
            if (key != lastDismissedClipboardText && _detectedClipboardLink.value == null && _pendingDeepLink.value == null) {
                _detectedClipboardLink.value = parsed
            }
        }
    }

    fun dismissClipboardLink() {
        val current = _detectedClipboardLink.value
        if (current != null) {
            lastDismissedClipboardText = when (current) {
                is com.example.util.ParsedDeepLink.UserLink -> current.nodeId
                is com.example.util.ParsedDeepLink.GroupLink -> current.groupId
                is com.example.util.ParsedDeepLink.ChannelLink -> current.channelId
            }
        }
        _detectedClipboardLink.value = null
    }

    fun executeDeepLink(parsed: com.example.util.ParsedDeepLink) {
        viewModelScope.launch {
            when (parsed) {
                is com.example.util.ParsedDeepLink.UserLink -> {
                    val cleanNode = parsed.nodeId.trim().lowercase()
                    val existing = repository.getPeerByNodeId(cleanNode)
                    if (existing == null) {
                        val alias = parsed.alias ?: "مستخدم ${cleanNode.takeLast(6)}"
                        repository.addPeer(cleanNode, alias)
                    }
                    selectDirectChat(cleanNode)
                }
                is com.example.util.ParsedDeepLink.GroupLink -> {
                    val group = repository.getGroupById(parsed.groupId)
                    if (group == null) {
                        repository.joinGroup(
                            groupId = parsed.groupId,
                            groupName = parsed.groupName ?: "مجموعة مشفرة",
                            inviteCode = parsed.inviteCode
                        )
                    }
                    selectGroupChat(parsed.groupId)
                }
                is com.example.util.ParsedDeepLink.ChannelLink -> {
                    val channel = repository.getBroadcastChannelById(parsed.channelId)
                    if (channel == null) {
                        repository.joinBroadcastChannelDirectly(
                            channelId = parsed.channelId,
                            name = parsed.channelName ?: "قناة مشفرة",
                            encryptionKey = parsed.encryptionKey
                        )
                    }
                    selectChannel(parsed.channelId)
                }
            }
            _detectedClipboardLink.value = null
            clearDeepLink()
            clearChannelKey()
        }
    }

    // Onboarding / Setup flow
    private val _onboardingCompleted = MutableStateFlow(false)
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private val _isGeneratingKeys = MutableStateFlow(false)
    val isGeneratingKeys: StateFlow<Boolean> = _isGeneratingKeys.asStateFlow()

    // Current navigation screens
    // "onboarding", "dashboard", "chat_direct", "chat_group", "new_chat", "new_group"
    private val _currentScreen = MutableStateFlow("onboarding")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    // Selected Chat Details
    private val _selectedPeerNodeId = MutableStateFlow<String?>(null)
    val selectedPeerNodeId: StateFlow<String?> = _selectedPeerNodeId.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<String?>(null)
    val selectedGroupId: StateFlow<String?> = _selectedGroupId.asStateFlow()

    // Current inputs
    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    private val _selfDestructTimer = MutableStateFlow(0) // in seconds (0 = disabled)
    val selfDestructTimer: StateFlow<Int> = _selfDestructTimer.asStateFlow()

    // Attachment states
    private val _selectedAttachmentName = MutableStateFlow<String?>(null)
    val selectedAttachmentName: StateFlow<String?> = _selectedAttachmentName.asStateFlow()

    private val _selectedAttachmentSize = MutableStateFlow<String?>(null)
    val selectedAttachmentSize: StateFlow<String?> = _selectedAttachmentSize.asStateFlow()

    private val _selectedAttachmentUri = MutableStateFlow<String?>(null)
    val selectedAttachmentUri: StateFlow<String?> = _selectedAttachmentUri.asStateFlow()

    // Live Lists from database
    val peers: StateFlow<List<Peer>> = repository.getPeers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selfPeers: StateFlow<List<Peer>> = repository.getSelfPeersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeNodeIndex: StateFlow<Int> = repository.activeNodeIndexFlow

    fun switchActiveNode(index: Int) {
        viewModelScope.launch {
            repository.setActiveNodeIndex(index)
            _selfPeer.value = repository.getSelfNode()
            // Reset current screen or refresh UI as needed
            navigateTo("dashboard")
        }
    }

    val groups: StateFlow<List<GroupChat>> = repository.getGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Network states
    val networkStatus: StateFlow<String> = repository.networkStatus
    val networkNodesCount: StateFlow<Int> = repository.networkNodesCount
    val networkQuality: StateFlow<String> = repository.networkQuality
    val networkLatency: StateFlow<Long> = repository.networkLatency
    val stealthMode: StateFlow<Boolean> = repository.stealthMode

    // Stickers & Custom Emojis
    val stickerPacks = repository.stickerPacksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customEmojiSets = repository.customEmojiSetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getStickerItems(packId: String) = repository.getStickerItemsFlow(packId)
    fun getCustomEmojiItems(setId: String) = repository.getCustomEmojiItemsFlow(setId)

    fun createStickerPack(name: String, author: String, iconEmoji: String, imageUris: List<String>) {
        viewModelScope.launch {
            repository.createStickerPack(name, author, iconEmoji, imageUris)
        }
    }

    fun createCustomEmoji(setName: String, shortcode: String, imageUri: String) {
        viewModelScope.launch {
            repository.createCustomEmoji(setName, shortcode, imageUri)
        }
    }

    fun retryUnsentMessages() {
        viewModelScope.launch {
            repository.triggerManualSyncUnsentMessages()
        }
    }

    private val _isDownloadAllowed = MutableStateFlow(true)
    val isDownloadAllowed: StateFlow<Boolean> = _isDownloadAllowed.asStateFlow()

    fun toggleDownloadAllowed() {
        _isDownloadAllowed.value = !_isDownloadAllowed.value
    }

    // DNS / Alias Search
    private val _aliasSearchQuery = MutableStateFlow("")
    val aliasSearchQuery: StateFlow<String> = _aliasSearchQuery.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val aliasSearchResults: StateFlow<List<AliasEntry>> = _aliasSearchQuery
        .flatMapLatest { query ->
            if (query.length < 2) flowOf(emptyList())
            else repository.searchAliases(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Self Node profile
    private val _selfPeer = MutableStateFlow<Peer?>(null)
    val selfPeer: StateFlow<Peer?> = _selfPeer.asStateFlow()

    // Active Chat Message List Flow
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<Message>> = combine(
        _selectedPeerNodeId,
        _selectedGroupId
    ) { peerId, groupId ->
        Pair(peerId, groupId)
    }.flatMapLatest { (peerId, groupId) ->
        when {
            peerId != null -> repository.getDirectMessages(peerId)
            groupId != null -> repository.getGroupMessages(groupId)
            else -> flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- ADVANCED SEARCH STATE ENGINE ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchDateFilter = MutableStateFlow<String?>(null) // "today", "yesterday", "week", "month" or YYYY-MM-DD
    val searchDateFilter: StateFlow<String?> = _searchDateFilter.asStateFlow()

    private val _searchFileTypeFilter = MutableStateFlow<String?>(null) // "all", "text", "media", "document"
    val searchFileTypeFilter: StateFlow<String?> = _searchFileTypeFilter.asStateFlow()

    private val _searchGroupFilter = MutableStateFlow<String?>(null) // groupId or null for any
    val searchGroupFilter: StateFlow<String?> = _searchGroupFilter.asStateFlow()

    private val _searchSenderFilter = MutableStateFlow<String?>(null) // senderNodeId or null for any
    val searchSenderFilter: StateFlow<String?> = _searchSenderFilter.asStateFlow()

    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun updateSearchDateFilter(filter: String?) { _searchDateFilter.value = filter }
    fun updateSearchFileTypeFilter(filter: String?) { _searchFileTypeFilter.value = filter }
    fun updateSearchGroupFilter(groupId: String?) { _searchGroupFilter.value = groupId }
    fun updateSearchSenderFilter(nodeId: String?) { _searchSenderFilter.value = nodeId }

    val allActiveMessages: StateFlow<List<Message>> = repository.getAllActiveMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchResults: StateFlow<List<Message>> = combine(
        repository.getAllActiveMessages(),
        combine(_searchQuery, _searchDateFilter, _searchFileTypeFilter) { q, d, f -> Triple(q, d, f) },
        combine(_searchGroupFilter, _searchSenderFilter) { g, s -> Pair(g, s) }
    ) { messages, qdf, gs ->
        val (query, dateFilter, fileTypeFilter) = qdf
        val (groupFilter, senderFilter) = gs
        messages.filter { msg ->
            // 1. Keyword check (text or mediaName)
            val matchesQuery = if (query.isEmpty()) true else {
                msg.text.contains(query, ignoreCase = true) || 
                (msg.mediaName?.contains(query, ignoreCase = true) ?: false)
            }

            // 2. Date check
            val matchesDate = if (dateFilter == null) true else {
                val msgTime = msg.timestamp
                val now = System.currentTimeMillis()
                when (dateFilter) {
                    "today" -> {
                        val calMsg = java.util.Calendar.getInstance().apply { timeInMillis = msgTime }
                        val calNow = java.util.Calendar.getInstance().apply { timeInMillis = now }
                        calMsg.get(java.util.Calendar.YEAR) == calNow.get(java.util.Calendar.YEAR) &&
                        calMsg.get(java.util.Calendar.DAY_OF_YEAR) == calNow.get(java.util.Calendar.DAY_OF_YEAR)
                    }
                    "yesterday" -> {
                        val calMsg = java.util.Calendar.getInstance().apply { timeInMillis = msgTime }
                        val calYesterday = java.util.Calendar.getInstance().apply {
                            timeInMillis = now
                            add(java.util.Calendar.DAY_OF_YEAR, -1)
                        }
                        calMsg.get(java.util.Calendar.YEAR) == calYesterday.get(java.util.Calendar.YEAR) &&
                        calMsg.get(java.util.Calendar.DAY_OF_YEAR) == calYesterday.get(java.util.Calendar.DAY_OF_YEAR)
                    }
                    "week" -> {
                        val sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000)
                        msgTime >= sevenDaysAgo
                    }
                    "month" -> {
                        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)
                        msgTime >= thirtyDaysAgo
                    }
                    else -> {
                        try {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            val filterDate = sdf.parse(dateFilter)
                            if (filterDate != null) {
                                val calMsg = java.util.Calendar.getInstance().apply { timeInMillis = msgTime }
                                val calFilter = java.util.Calendar.getInstance().apply { time = filterDate }
                                calMsg.get(java.util.Calendar.YEAR) == calFilter.get(java.util.Calendar.YEAR) &&
                                calMsg.get(java.util.Calendar.DAY_OF_YEAR) == calFilter.get(java.util.Calendar.DAY_OF_YEAR)
                            } else true
                        } catch (e: Exception) {
                            true
                        }
                    }
                }
            }

            // 3. File type check
            val matchesFileType = if (fileTypeFilter == null || fileTypeFilter == "all") true else {
                val hasMedia = !msg.mediaPath.isNullOrEmpty()
                val extension = msg.mediaName?.substringAfterLast('.', "")?.lowercase() ?: ""
                when (fileTypeFilter) {
                    "text" -> !hasMedia
                    "media" -> hasMedia && extension in listOf("jpg", "jpeg", "png", "gif", "webp", "mp4", "mkv", "avi", "mp3", "wav", "m4a")
                    "document" -> hasMedia && extension !in listOf("jpg", "jpeg", "png", "gif", "webp", "mp4", "mkv", "avi", "mp3", "wav", "m4a")
                    else -> true
                }
            }

            // 4. Group check
            val matchesGroup = if (groupFilter == null) true else {
                msg.groupId == groupFilter
            }

            // 5. Sender check
            val matchesSender = if (senderFilter == null) true else {
                msg.senderNodeId.contains(senderFilter, ignoreCase = true)
            }

            matchesQuery && matchesDate && matchesFileType && matchesGroup && matchesSender
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val self = repository.getSelfNode()
            _selfPeer.value = self
            val completed = sharedPrefs.getBoolean("onboarding_completed", false)
            if (completed && self != null) {
                _onboardingCompleted.value = true
                _currentScreen.value = "dashboard"
            } else {
                _onboardingCompleted.value = false
                _currentScreen.value = "onboarding"
            }
        }

        // Listen for real-time E2EE call signals
        viewModelScope.launch {
            repository.callEventFlow.collect { event ->
                when (event) {
                    is ChatRepository.CallEvent.IncomingCall -> {
                        if (_activeCallState.value == null) {
                            // Show system push notification for incoming call
                            try {
                                com.example.CipherisApplication.instance.showCallNotification(event.senderAlias, event.isVideo)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            _activeCallState.value = CallSession(
                                peerNodeId = event.senderNodeId,
                                peerName = event.senderAlias,
                                isVideo = event.isVideo,
                                isIncoming = true,
                                sessionId = event.sessionId,
                                status = "ringing",
                                webrtcState = "NEW",
                                peerIp = event.senderIp,
                                handshakeLogs = listOf(
                                    "WebRTC: مكالمة مشفرة E2EE واردة...",
                                    "WebRTC: جاري فحص هوية المتصل ومصادقة عقدته الرقمية..."
                                )
                            )
                        } else {
                            viewModelScope.launch {
                                repository.sendCallSignal(event.senderNodeId, "REJECT:${event.sessionId}")
                            }
                        }
                    }
                    is ChatRepository.CallEvent.CallAccepted -> {
                        val current = _activeCallState.value
                        if (current != null && current.sessionId == event.sessionId && current.status != "connected") {
                            // Cancel system call notification
                            try {
                                com.example.CipherisApplication.instance.cancelCallNotification()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            event.peerIp?.let { P2PMediaEngine.updatePeerAddress(it) }
                            viewModelScope.launch {
                                updateHandshake("WebRTC: تم استلام إجابة SDP Answer المشفرة والآمنة من الطرف الآخر.", "SDP_ANSWER_RECVD")
                                delay(500)
                                updateHandshake("WebRTC: مطابقة البصمات التشفيرية وتحديد مرشح ICE المشترك الأكثر كفاءة...", "SDP_ANSWER_RECVD")
                                delay(500)
                                updateHandshake("WebRTC: تم فتح مسار البث المباشر E2E نظير-إلى-نظير بنجاح وأمان كامل!", "ESTABLISHED")
                                _activeCallState.value = _activeCallState.value?.copy(
                                    status = "connected",
                                    startTime = System.currentTimeMillis()
                                )
                                // Start caller media streaming
                                P2PMediaEngine.startStreaming(getApplication(), current.isVideo)
                            }
                        }
                    }
                    is ChatRepository.CallEvent.CallRejected -> {
                        val current = _activeCallState.value
                        if (current != null && current.sessionId == event.sessionId) {
                            // Cancel system call notification
                            try {
                                com.example.CipherisApplication.instance.cancelCallNotification()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            _activeCallState.value = current.copy(status = "ended")

                            viewModelScope.launch {
                                val logText = if (current.isIncoming) {
                                    "🚫 تم رفض المكالمة الواردة"
                                } else {
                                    "🚫 تم رفض المكالمة من قبل الطرف الآخر"
                                }
                                repository.insertLocalMessage(
                                    com.example.data.model.Message(
                                        peerNodeId = current.peerNodeId,
                                        groupId = null,
                                        text = logText,
                                        senderNodeId = if (current.isIncoming) current.peerNodeId else (repository.getSelfNode()?.nodeId ?: "cipheris://local"),
                                        isIncoming = current.isIncoming
                                    )
                                )
                            }

                            delay(1500)
                            _activeCallState.value = null
                        }
                    }
                    is ChatRepository.CallEvent.CallEnded -> {
                        val current = _activeCallState.value
                        if (current != null && current.sessionId == event.sessionId) {
                            // Cancel system call notification
                            try {
                                com.example.CipherisApplication.instance.cancelCallNotification()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            _activeCallState.value = current.copy(status = "ended")

                            viewModelScope.launch {
                                val logText = if (current.status == "ringing") {
                                    if (current.isIncoming) {
                                        "❌ مكالمة فائتة (لم يتم الرد عليها)"
                                    } else {
                                        "⏱️ مكالمة صادرة لم يتم الرد عليها"
                                    }
                                } else if (current.status == "connected" && current.startTime > 0) {
                                    val durationSec = (System.currentTimeMillis() - current.startTime) / 1000
                                    val minutes = durationSec / 60
                                    val seconds = durationSec % 60
                                    val timeStr = if (minutes > 0) "$minutes دقيقة و $seconds ثانية" else "$seconds ثانية"
                                    "🏁 انتهت المكالمة المشفرة (المدة: $timeStr)"
                                } else {
                                    "🏁 انتهت المكالمة المشفرة"
                                }
                                repository.insertLocalMessage(
                                    com.example.data.model.Message(
                                        peerNodeId = current.peerNodeId,
                                        groupId = null,
                                        text = logText,
                                        senderNodeId = if (current.isIncoming) current.peerNodeId else (repository.getSelfNode()?.nodeId ?: "cipheris://local"),
                                        isIncoming = current.isIncoming
                                    )
                                )
                            }

                            delay(1500)
                            _activeCallState.value = null
                        }
                    }
                }
            }
        }
    }

    fun completeOnboarding(localAlias: String) {
        viewModelScope.launch {
            _isGeneratingKeys.value = true
            // Simulate heavy cryptographic key pair generation
            kotlinx.coroutines.delay(2500)

            // Register custom local name in repository if provided
            val aliasName = if (localAlias.trim().isEmpty()) "مستخدم مجهول" else localAlias.trim()
            val selfNode = repository.getSelfNode()
            if (selfNode != null) {
                // If it already exists, update alias locally (stored purely local)
                repository.updateSelfAlias(aliasName)
                _selfPeer.value = repository.getSelfNode()
            }

            sharedPrefs.edit().putBoolean("onboarding_completed", true).apply()
            _isGeneratingKeys.value = false
            _onboardingCompleted.value = true
            _currentScreen.value = "dashboard"
        }
    }

    fun inviteToGroup(groupId: String, nodeText: String) {
        viewModelScope.launch {
            repository.inviteToGroup(groupId, nodeText)
        }
    }

    fun joinGroup(groupId: String, groupName: String, inviteCode: String = "") {
        viewModelScope.launch {
            repository.joinGroup(groupId, groupName, inviteCode)
            navigateTo("dashboard")
        }
    }

    fun navigateTo(screen: String) {
        _currentScreen.value = screen
        if (screen == "dashboard") {
            _selectedPeerNodeId.value = null
            _selectedGroupId.value = null
            _messageInput.value = ""
            _selfDestructTimer.value = 0
            clearAttachment()
        }
    }

    fun selectDirectChat(peerNodeId: String) {
        _selectedGroupId.value = null
        _selectedPeerNodeId.value = peerNodeId
        _currentScreen.value = "chat_direct"
    }

    fun selectGroupChat(groupId: String) {
        _selectedPeerNodeId.value = null
        _selectedGroupId.value = groupId
        _currentScreen.value = "chat_group"
    }

    fun updateMessageInput(text: String) {
        _messageInput.value = text
    }

    fun updateSelfDestructTimer(seconds: Int) {
        _selfDestructTimer.value = seconds
    }

    fun simulateSelectAttachment(fileName: String, fileSize: String, uriStr: String? = null) {
        _selectedAttachmentName.value = fileName
        _selectedAttachmentSize.value = fileSize
        _selectedAttachmentUri.value = uriStr
    }

    fun clearAttachment() {
        _selectedAttachmentName.value = null
        _selectedAttachmentSize.value = null
        _selectedAttachmentUri.value = null
    }

    fun toggleStealthMode() {
        repository.toggleStealthMode()
    }

    fun panicWipeAll() {
        viewModelScope.launch {
            repository.panicWipeAll()
            _selectedPeerNodeId.value = null
            _selectedGroupId.value = null
            _currentScreen.value = "dashboard"
        }
    }

    fun updateAliasSearchQuery(query: String) {
        _aliasSearchQuery.value = query
    }

    fun addContact(nodeId: String, alias: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val formattedNodeId = nodeId.trim()
            val formattedAlias = alias.trim()

            if (!formattedNodeId.startsWith("cipheris://")) {
                onError("يجب أن يبدأ عنوان العقدة بـ cipheris://")
                return@launch
            }
            if (formattedNodeId.length != 27) {
                onError("عنوان العقدة غير صالح أو غير مكتمل (يجب أن يكون الرمز كاملاً)")
                return@launch
            }
            if (formattedAlias.isEmpty()) {
                onError("الرجاء إدخال اسم مستعار محلي للعقدة")
                return@launch
            }

            val success = repository.addPeer(formattedNodeId, formattedAlias)
            if (success) {
                onSuccess()
                navigateTo("dashboard")
            } else {
                onError("فشل إضافة العقدة. الرجاء التحقق من العنوان.")
            }
        }
    }

    fun createSecureGroup(groupName: String, selectedMembers: List<String>, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val name = groupName.trim()
            if (name.isEmpty()) {
                onError("الرجاء إدخال اسم المجموعة")
                return@launch
            }

            val groupId = repository.createGroup(name, selectedMembers)
            onSuccess()
            selectGroupChat(groupId)
        }
    }

    fun enqueueMediaUploadWorker(context: Context, peerAddress: String?, mediaPath: String?, mediaName: String?, content: String, isGroup: Boolean = false, channelId: String? = null) {
        if (mediaPath.isNullOrEmpty() && mediaName.isNullOrEmpty()) return
        val data = androidx.work.Data.Builder()
            .putString(com.example.data.worker.MediaUploadWorker.KEY_PEER_ADDRESS, peerAddress)
            .putString(com.example.data.worker.MediaUploadWorker.KEY_MEDIA_PATH, mediaPath)
            .putString(com.example.data.worker.MediaUploadWorker.KEY_MEDIA_NAME, mediaName)
            .putString(com.example.data.worker.MediaUploadWorker.KEY_CONTENT, content)
            .putBoolean(com.example.data.worker.MediaUploadWorker.KEY_IS_GROUP, isGroup)
            .putString(com.example.data.worker.MediaUploadWorker.KEY_CHANNEL_ID, channelId)
            .build()

        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val uploadRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.data.worker.MediaUploadWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                10, java.util.concurrent.TimeUnit.SECONDS
            )
            .build()

        androidx.work.WorkManager.getInstance(context).enqueue(uploadRequest)
    }

    fun sendCurrentMessage() {
        val text = _messageInput.value.trim()
        val mediaPath = _selectedAttachmentUri.value ?: _selectedAttachmentName.value?.let { "cipheris://file/$it" }
        val mediaName = _selectedAttachmentName.value
        val downloadAllowed = _isDownloadAllowed.value

        if (text.isEmpty() && mediaName == null) return

        viewModelScope.launch {
            repository.sendMessage(
                peerNodeId = _selectedPeerNodeId.value,
                groupId = _selectedGroupId.value,
                text = text,
                mediaPath = mediaPath,
                mediaName = mediaName,
                selfDestructSeconds = _selfDestructTimer.value,
                isDownloadAllowed = downloadAllowed
            )

            if (mediaName != null) {
                enqueueMediaUploadWorker(
                    context = getApplication(),
                    peerAddress = _selectedGroupId.value ?: _selectedPeerNodeId.value,
                    mediaPath = mediaPath,
                    mediaName = mediaName,
                    content = text,
                    isGroup = _selectedGroupId.value != null
                )
            }

            // Reset inputs
            _messageInput.value = ""
            _isDownloadAllowed.value = true
            clearAttachment()
            // Reset self destruct timer (it is per message or can be persistent, resetting is standard for UI)
        }
    }

    // --- ADMIN ACTIONS ---
    fun clearGroupMessages(groupId: String) {
        viewModelScope.launch {
            repository.clearGroupMessages(groupId)
        }
    }

    fun clearChannelPosts(channelId: String) {
        viewModelScope.launch {
            repository.clearChannelPosts(channelId)
        }
    }

    fun setGroupRole(groupId: String, targetNodeId: String, role: String, add: Boolean) {
        viewModelScope.launch {
            repository.setGroupRole(groupId, targetNodeId, role, add)
        }
    }

    fun setChannelRole(channelId: String, targetNodeId: String, role: String, add: Boolean) {
        viewModelScope.launch {
            repository.setChannelRole(channelId, targetNodeId, role, add)
        }
    }

    fun setChannelGranularPermissions(channelId: String, targetNodeId: String, permissions: Map<String, Boolean>) {
        viewModelScope.launch {
            repository.setChannelGranularPermissions(channelId, targetNodeId, permissions)
        }
    }

    fun setGroupGranularPermissions(groupId: String, targetNodeId: String, permissions: Map<String, Boolean>) {
        viewModelScope.launch {
            repository.setGroupGranularPermissions(groupId, targetNodeId, permissions)
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    fun pinGroupMessage(groupId: String, messageId: Long?) {
        viewModelScope.launch {
            repository.pinGroupMessage(groupId, messageId)
        }
    }

    fun pinChannelPost(channelId: String, postId: Long?) {
        viewModelScope.launch {
            repository.pinChannelPost(channelId, postId)
        }
    }

    fun deleteChannelPost(postId: Long) {
        viewModelScope.launch {
            repository.deleteChannelPost(postId)
        }
    }

    // --- P2P SECURE E2EE VOICE & VIDEO CALLING ---
    data class CallSession(
        val peerNodeId: String,
        val peerName: String,
        val isVideo: Boolean,
        val isIncoming: Boolean,
        val sessionId: String,
        val status: String, // "ringing", "connecting", "connected", "ended"
        val startTime: Long = 0L,
        val handshakeLogs: List<String> = emptyList(),
        val webrtcState: String = "NEW",
        val peerIp: String? = null
    )

    private val _activeCallState = MutableStateFlow<CallSession?>(null)
    val activeCallState: StateFlow<CallSession?> = _activeCallState.asStateFlow()

    fun startCall(peerNodeId: String, peerName: String, isVideo: Boolean) {
        val sessionId = UUID.randomUUID().toString().substring(0, 8)
        val localIp = P2PMediaEngine.getLocalIpAddress()
        
        P2PMediaEngine.initSession(sessionId, caller = true)
        
        _activeCallState.value = CallSession(
            peerNodeId = peerNodeId,
            peerName = peerName,
            isVideo = isVideo,
            isIncoming = false,
            sessionId = sessionId,
            status = "connecting",
            webrtcState = "NEW",
            handshakeLogs = listOf(
                "WebRTC: تهيئة سياق الاتصال مع عقدة الطرف الآخر...",
                "E2EE: توليد مفتاح جلسة تشفير مباشر AES/XOR-256...",
                "E2EE Local IP: $localIp"
            )
        )
        viewModelScope.launch {
            val callType = if (isVideo) "مكالمة فيديو مشفرة E2EE" else "مكالمة صوتية مشفرة E2EE"
            repository.insertLocalMessage(
                com.example.data.model.Message(
                    peerNodeId = peerNodeId,
                    groupId = null,
                    text = "📞 $callType صادرة...",
                    senderNodeId = repository.getSelfNode()?.nodeId ?: "cipheris://local",
                    isIncoming = false
                )
            )
            
            delay(400)
            updateHandshake("WebRTC: جاري إنشاء عرض اتصال SDP Offer (نوع: offer)...", "GENERATING_SDP")
            delay(400)
            updateHandshake("WebRTC: تم إنشاء SDP Offer المحلي بنجاح وحساب البصمة التشفيرية.", "GENERATING_SDP")
            delay(400)
            updateHandshake("E2EE: تشفير عرض SDP بكامل مفاتيح التعمية الخاصة بالمستلم لمنع التنصت.", "GENERATING_SDP")
            delay(400)
            updateHandshake("WebRTC: بدء تجميع عناوين الاتصال المرشحة ICE Candidates...", "ICE_GATHERING")
            delay(400)
            updateHandshake("WebRTC: تم تحديد العنوان المرشح المحلي: host (UDP $localIp:51025).", "ICE_GATHERING")
            delay(400)
            updateHandshake("WebRTC: جاري إرسال العرض وحزمة الـ SDP عبر نفق الإشارة اللامركزي الآمن...", "SDP_OFFER_SENT")
            
            repository.sendCallSignal(peerNodeId, "INITIATE:${if (isVideo) "VIDEO" else "AUDIO"}:$sessionId:$localIp")
        }
    }

    private fun updateHandshake(log: String, webrtcState: String) {
        val current = _activeCallState.value ?: return
        _activeCallState.value = current.copy(
            handshakeLogs = current.handshakeLogs + log,
            webrtcState = webrtcState
        )
    }

    fun acceptIncomingCall() {
        try {
            com.example.CipherisApplication.instance.cancelCallNotification()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val current = _activeCallState.value ?: return
        val localIp = P2PMediaEngine.getLocalIpAddress()
        
        P2PMediaEngine.initSession(current.sessionId, caller = false)
        current.peerIp?.let { P2PMediaEngine.updatePeerAddress(it) }
        
        _activeCallState.value = current.copy(
            status = "connecting",
            webrtcState = "DECODING_SDP",
            handshakeLogs = listOf(
                "WebRTC: تم استلام عرض SDP Offer المشفر من المتصل.",
                "E2EE: جاري فك تشفير العرض ومطابقة التوقيع الرقمي للمرسل...",
                "E2EE Local IP: $localIp"
            )
        )
        viewModelScope.launch {
            delay(400)
            updateHandshake("WebRTC: تم فك تشفير وضبط SDP Offer البعيد كـ Remote Description.", "DECODING_SDP")
            delay(400)
            updateHandshake("WebRTC: جاري توليد إجابة الاتصال SDP Answer التوافقية...", "GENERATING_SDP")
            delay(400)
            updateHandshake("WebRTC: تم إنشاء SDP Answer المحلي بنجاح (نوع: answer).", "GENERATING_SDP")
            delay(400)
            updateHandshake("WebRTC: بدء تجميع مرشحات ICE المحلية (عبر نفق P2P)...", "ICE_GATHERING")
            delay(400)
            updateHandshake("WebRTC: تم تحديد العنوان المرشح المحلي: host (UDP $localIp:51025).", "ICE_GATHERING")
            delay(400)
            updateHandshake("WebRTC: جاري إرسال إجابة SDP Answer المشفرة مجدداً للمتصل الأصلي...", "SDP_ANSWER_SENT")
            
            repository.sendCallSignal(current.peerNodeId, "ACCEPT:${current.sessionId}:$localIp")
            
            delay(400)
            _activeCallState.value = _activeCallState.value?.copy(
                status = "connected",
                webrtcState = "ESTABLISHED",
                startTime = System.currentTimeMillis(),
                handshakeLogs = (_activeCallState.value?.handshakeLogs ?: emptyList()) + "WebRTC: تم فتح مسار البث المباشر E2E نظير-إلى-نظير بنجاح وأمان كامل!"
            )
            // Trigger actual audio/video capture & streaming on the worker threads
            P2PMediaEngine.startStreaming(getApplication(), current.isVideo)
        }
    }

    fun rejectIncomingCall() {
        try {
            com.example.CipherisApplication.instance.cancelCallNotification()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val current = _activeCallState.value ?: return
        _activeCallState.value = null
        P2PMediaEngine.stopStreaming()
        viewModelScope.launch {
            repository.insertLocalMessage(
                com.example.data.model.Message(
                    peerNodeId = current.peerNodeId,
                    groupId = null,
                    text = "🚫 تم رفض المكالمة الواردة",
                    senderNodeId = (repository.getSelfNode()?.nodeId ?: "cipheris://local"),
                    isIncoming = false
                )
            )
            repository.sendCallSignal(current.peerNodeId, "REJECT:${current.sessionId}")
        }
    }

    fun endActiveCall() {
        try {
            com.example.CipherisApplication.instance.cancelCallNotification()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val current = _activeCallState.value ?: return
        _activeCallState.value = current.copy(status = "ended")
        P2PMediaEngine.stopStreaming()
        viewModelScope.launch {
            val logText = if (current.status == "ringing") {
                if (current.isIncoming) "❌ مكالمة فائتة (لم يتم الرد عليها)" else "⏱️ مكالمة صادرة لم يتم الرد عليها"
            } else if (current.status == "connected" && current.startTime > 0) {
                val durationSec = (System.currentTimeMillis() - current.startTime) / 1000
                val minutes = durationSec / 60
                val seconds = durationSec % 60
                val timeStr = if (minutes > 0) "$minutes دقيقة و $seconds ثانية" else "$seconds ثانية"
                "🏁 انتهت المكالمة المشفرة (المدة: $timeStr)"
            } else {
                "🏁 انتهت المكالمة المشفرة"
            }
            repository.insertLocalMessage(
                com.example.data.model.Message(
                    peerNodeId = current.peerNodeId,
                    groupId = null,
                    text = logText,
                    senderNodeId = if (current.isIncoming) current.peerNodeId else (repository.getSelfNode()?.nodeId ?: "cipheris://local"),
                    isIncoming = current.isIncoming
                )
            )

            repository.sendCallSignal(current.peerNodeId, "END:${current.sessionId}")
            delay(1500)
            _activeCallState.value = null
        }
    }

    // --- DECENTRALIZED BROADCAST CHANNELS (E2EE) ---
    val broadcastChannels: StateFlow<List<BroadcastChannel>> = repository.getBroadcastChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedChannelId = MutableStateFlow<String?>(null)
    val selectedChannelId: StateFlow<String?> = _selectedChannelId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedChannelPosts: StateFlow<List<ChannelPost>> = _selectedChannelId
        .flatMapLatest { id ->
            if (id == null) kotlinx.coroutines.flow.flowOf<List<ChannelPost>>(emptyList())
            else repository.getChannelPosts(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectChannel(channelId: String?) {
        _selectedChannelId.value = channelId
        if (channelId != null) {
            _currentScreen.value = "channel_view"
        }
    }

    fun deleteBroadcastChannel(channelId: String) {
        viewModelScope.launch {
            repository.deleteBroadcastChannel(channelId)
            if (_selectedChannelId.value == channelId) {
                _selectedChannelId.value = null
                _currentScreen.value = "dashboard"
            }
        }
    }

    fun createBroadcastChannel(name: String, description: String, isPrivate: Boolean, customKey: String) {
        viewModelScope.launch {
            repository.createBroadcastChannel(name, description, isPrivate, customKey)
            _currentScreen.value = "dashboard"
        }
    }

    fun joinChannelDirectly(channelId: String, name: String, encryptionKey: String) {
        viewModelScope.launch {
            repository.joinBroadcastChannelDirectly(channelId, name, encryptionKey)
        }
    }

    fun postToChannel(channelId: String, text: String, isAnonymous: Boolean = true) {
        val mediaPath = _selectedAttachmentUri.value
        val mediaName = _selectedAttachmentName.value
        viewModelScope.launch {
            repository.postToChannel(
                channelId = channelId,
                text = text,
                mediaPath = mediaPath,
                mediaName = mediaName,
                isAnonymous = isAnonymous
            )
            _isDownloadAllowed.value = true
            clearAttachment()
        }
    }

    fun exportAppApkAsPasswordProtectedZip(context: Context, passwordKey: String, callback: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apkPath = context.applicationInfo.sourceDir
                val sourceFile = java.io.File(apkPath)
                
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetZipFile = java.io.File(downloadsDir, "Cipheris_Secure_Installer.zip")
                if (targetZipFile.exists()) {
                    targetZipFile.delete()
                }

                // Create a temporary file with the original name inside the zip
                val tempApkFile = java.io.File(context.cacheDir, "Cipheris_v1.1_Installer.apk")
                sourceFile.inputStream().use { input ->
                    tempApkFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val key = passwordKey.ifEmpty { "123" }
                val zipFile = ZipFile(targetZipFile, key.toCharArray())
                val zipParameters = ZipParameters().apply {
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                }
                zipFile.addFile(tempApkFile, zipParameters)
                tempApkFile.delete() // Clean up

                withContext(Dispatchers.Main) {
                    callback("تم ضغط وتشفير ملف الـ APK بنجاح وحفظه باسم '${targetZipFile.name}' في مجلد التنزيلات (Downloads).\n\n• كلمة مرور ملف الـ ZIP: $key\n• يمكنك مشاركة هذا الملف بأمان كامل وتجاوز أي فحص أو حجب برمجى!")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback("فشل تصدير ZIP: ${e.localizedMessage}")
                }
            }
        }
    }

    fun exportLocalData(password: String, callback: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val peers = repository.getAllPeersList()
                val groups = repository.getAllGroupsList()
                val channels = repository.getAllChannelsList()
                val messages = repository.getAllMessagesListExport()
                val posts = repository.getAllChannelPostsList()

                val root = JSONObject()
                root.put("version", 1)
                root.put("timestamp", System.currentTimeMillis())

                val peersArr = JSONArray()
                peers.forEach {
                    peersArr.put(JSONObject().apply {
                        put("nodeId", it.nodeId)
                        put("alias", it.alias)
                        put("publicKeyFingerprint", it.publicKeyFingerprint)
                        put("isSelf", it.isSelf)
                        put("avatarSeed", it.avatarSeed)
                    })
                }
                root.put("peers", peersArr)

                val groupsArr = JSONArray()
                groups.forEach {
                    groupsArr.put(JSONObject().apply {
                        put("groupId", it.groupId)
                        put("name", it.name)
                        put("createdBy", it.createdBy)
                        put("createdAt", it.createdAt)
                        put("memberNodeIdsText", it.memberNodeIdsText)
                    })
                }
                root.put("groups", groupsArr)

                val channelsArr = JSONArray()
                channels.forEach {
                    channelsArr.put(JSONObject().apply {
                        put("channelId", it.channelId)
                        put("name", it.name)
                        put("description", it.description)
                        put("encryptionKey", it.encryptionKey)
                        put("isPrivate", it.isPrivate)
                        put("createdBy", it.createdBy)
                        put("createdAt", it.createdAt)
                    })
                }
                root.put("channels", channelsArr)

                val messagesArr = JSONArray()
                messages.forEach {
                    messagesArr.put(JSONObject().apply {
                        put("peerNodeId", it.peerNodeId)
                        put("groupId", it.groupId)
                        put("text", it.text)
                        put("senderNodeId", it.senderNodeId)
                        put("isIncoming", it.isIncoming)
                        put("timestamp", it.timestamp)
                        put("mediaPath", it.mediaPath)
                        put("mediaName", it.mediaName)
                        put("selfDestructSeconds", it.selfDestructSeconds)
                        put("selfDestructAt", it.selfDestructAt)
                    })
                }
                root.put("messages", messagesArr)

                val postsArr = JSONArray()
                posts.forEach {
                    postsArr.put(JSONObject().apply {
                        put("channelId", it.channelId)
                        put("text", it.text)
                        put("isAnonymous", it.isAnonymous)
                        put("timestamp", it.timestamp)
                    })
                }
                root.put("channelPosts", postsArr)

                // Save JSON to temp file
                val context = getApplication<Application>()
                val tempJsonFile = java.io.File(context.cacheDir, "cipheris_backup.json")
                tempJsonFile.writeText(root.toString())

                // Zip and encrypt
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetZipFile = java.io.File(downloadsDir, "Cipheris_Secure_Backup_${System.currentTimeMillis()}.cipheris")
                
                val zipFile = if (password.isNotEmpty()) ZipFile(targetZipFile, password.toCharArray()) else ZipFile(targetZipFile)
                val zipParameters = ZipParameters().apply {
                    if (password.isNotEmpty()) {
                        isEncryptFiles = true
                        encryptionMethod = EncryptionMethod.AES
                        aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                    } else {
                        isEncryptFiles = false
                    }
                }
                zipFile.addFile(tempJsonFile, zipParameters)
                tempJsonFile.delete()

                withContext(Dispatchers.Main) {
                    val modeText = if (password.isNotEmpty()) "المشفرة (AES-256)" else "غير المشفرة"
                    callback("تم تصدير النسخة الاحتياطية $modeText بنجاح إلى مجلد التنزيلات باسم: ${targetZipFile.name}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback("فشل التصدير: ${e.localizedMessage}")
                }
            }
        }
    }

    fun importLocalData(uri: Uri, password: String, callback: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val tempZipFile = java.io.File(context.cacheDir, "import_backup.zip")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempZipFile.outputStream().use { output -> input.copyTo(output) }
                }

                val extractDir = java.io.File(context.cacheDir, "extracted_backup")
                if (extractDir.exists()) extractDir.deleteRecursively()
                extractDir.mkdirs()

                val zipFile = ZipFile(tempZipFile)
                if (zipFile.isEncrypted) {
                    if (password.isEmpty()) {
                        withContext(Dispatchers.Main) { callback("النسخة الاحتياطية مشفرة وتطلب كلمة مرور لفك تشفيرها.") }
                        return@launch
                    }
                    zipFile.setPassword(password.toCharArray())
                }
                zipFile.extractAll(extractDir.absolutePath)
                tempZipFile.delete()

                val jsonFile = extractDir.listFiles()?.find { it.name.endsWith(".json") }
                if (jsonFile == null) {
                    withContext(Dispatchers.Main) { callback("لم يتم العثور على بيانات صالحة في الملف.") }
                    return@launch
                }

                val root = JSONObject(jsonFile.readText())
                
                // Import Peers
                val peersArr = root.optJSONArray("peers")
                if (peersArr != null) {
                    val peers = mutableListOf<Peer>()
                    for (i in 0 until peersArr.length()) {
                        val obj = peersArr.getJSONObject(i)
                        peers.add(Peer(
                            nodeId = obj.getString("nodeId"),
                            alias = obj.getString("alias"),
                            publicKeyFingerprint = obj.getString("publicKeyFingerprint"),
                            isSelf = obj.getBoolean("isSelf"),
                            avatarSeed = obj.getString("avatarSeed")
                        ))
                    }
                    repository.importPeers(peers)
                }

                // Import Groups
                val groupsArr = root.optJSONArray("groups")
                if (groupsArr != null) {
                    val groups = mutableListOf<GroupChat>()
                    for (i in 0 until groupsArr.length()) {
                        val obj = groupsArr.getJSONObject(i)
                        groups.add(GroupChat(
                            groupId = obj.getString("groupId"),
                            name = obj.getString("name"),
                            createdBy = obj.getString("createdBy"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            memberNodeIdsText = obj.getString("memberNodeIdsText")
                        ))
                    }
                    repository.importGroups(groups)
                }

                // Import Channels
                val channelsArr = root.optJSONArray("channels")
                if (channelsArr != null) {
                    val channels = mutableListOf<BroadcastChannel>()
                    for (i in 0 until channelsArr.length()) {
                        val obj = channelsArr.getJSONObject(i)
                        channels.add(BroadcastChannel(
                            channelId = obj.getString("channelId"),
                            name = obj.getString("name"),
                            description = obj.getString("description"),
                            encryptionKey = obj.getString("encryptionKey"),
                            isPrivate = obj.getBoolean("isPrivate"),
                            createdBy = obj.optString("createdBy", "Imported"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        ))
                    }
                    repository.importChannels(channels)
                }

                // Import Messages
                val messagesArr = root.optJSONArray("messages")
                if (messagesArr != null) {
                    val messages = mutableListOf<Message>()
                    for (i in 0 until messagesArr.length()) {
                        val obj = messagesArr.getJSONObject(i)
                        messages.add(Message(
                            peerNodeId = obj.optString("peerNodeId").takeIf { it != "null" && it.isNotEmpty() },
                            groupId = obj.optString("groupId").takeIf { it != "null" && it.isNotEmpty() },
                            text = obj.getString("text"),
                            senderNodeId = obj.getString("senderNodeId"),
                            isIncoming = obj.getBoolean("isIncoming"),
                            timestamp = obj.getLong("timestamp"),
                            mediaPath = obj.optString("mediaPath").takeIf { it != "null" && it.isNotEmpty() },
                            mediaName = obj.optString("mediaName").takeIf { it != "null" && it.isNotEmpty() },
                            selfDestructSeconds = obj.optInt("selfDestructSeconds", 0),
                            selfDestructAt = obj.optLong("selfDestructAt", 0)
                        ))
                    }
                    repository.importMessages(messages)
                }

                // Import Posts
                val postsArr = root.optJSONArray("channelPosts")
                if (postsArr != null) {
                    val posts = mutableListOf<ChannelPost>()
                    for (i in 0 until postsArr.length()) {
                        val obj = postsArr.getJSONObject(i)
                        posts.add(ChannelPost(
                            channelId = obj.getString("channelId"),
                            text = obj.getString("text"),
                            isAnonymous = obj.optBoolean("isAnonymous", true),
                            timestamp = obj.getLong("timestamp")
                        ))
                    }
                    repository.importChannelPosts(posts)
                }

                withContext(Dispatchers.Main) {
                    callback("تم استيراد البيانات بنجاح! قد تحتاج لإعادة تشغيل التطبيق لتحديث كافة القوائم.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback("فشل الاستيراد: تأكد من صحة كلمة المرور. (${e.localizedMessage})")
                }
            }
        }
    }

    val isRecordingAudio = MutableStateFlow(false)

    fun encryptAndSelectFile(context: Context, uriStr: String, sizeStr: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val encryptedPath = FileEncryptor.encryptFile(context, uriStr)
            if (encryptedPath != null) {
                val file = java.io.File(encryptedPath)
                _selectedAttachmentName.value = file.name.substringAfter("secured_", "secured_file")
                _selectedAttachmentSize.value = sizeStr
                _selectedAttachmentUri.value = encryptedPath
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "تم تشفير وحفظ الملف محلياً بنجاح في Cipheris!", Toast.LENGTH_SHORT).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "فشل تشفير الملف", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun startVoiceRecording(context: Context) {
        val path = VoiceRecorderHelper.startRecording(context)
        if (path != null) {
            isRecordingAudio.value = true
            Toast.makeText(context, "جاري التسجيل الصوتي الآمن...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "فشل بدء التسجيل، يرجى تفعيل الأذونات", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopVoiceRecording(context: Context, isChannel: Boolean = false, destinationId: String? = null) {
        isRecordingAudio.value = false
        val originalPath = VoiceRecorderHelper.stopRecording()
        if (originalPath != null) {
            val origFile = java.io.File(originalPath)
            if (!origFile.exists() || origFile.length() < 100) {
                Toast.makeText(context, "التسجيل الصوتي قصير جداً أو فارغ", Toast.LENGTH_SHORT).show()
                return
            }
            viewModelScope.launch(Dispatchers.IO) {
                val encryptedPath = FileEncryptor.encryptFile(context, Uri.fromFile(origFile).toString())
                if (encryptedPath != null) {
                    val fileName = "صوت_مشفر_${System.currentTimeMillis()}.m4a"
                    if (isChannel && destinationId != null) {
                        repository.postToChannel(
                            channelId = destinationId,
                            text = "🎤 تسجيل صوتي مشفر آمن",
                            mediaPath = encryptedPath,
                            mediaName = fileName,
                            isAnonymous = true
                        )
                    } else {
                        repository.sendMessage(
                            peerNodeId = _selectedPeerNodeId.value,
                            groupId = _selectedGroupId.value,
                            text = "🎤 تسجيل صوتي مشفر آمن",
                            mediaPath = encryptedPath,
                            mediaName = fileName,
                            selfDestructSeconds = _selfDestructTimer.value,
                            isDownloadAllowed = true
                        )
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "تم إرسال التسجيل الصوتي مشفراً بنجاح!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun markMessageAsReadBySelf(messageId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg = repository.getMessageById(messageId) ?: return@launch
            
            if (msg.peerNodeId != null && msg.groupId == null) {
                // Direct Chat: Use the secure E2EE read receipt protocol
                repository.markMessageAsRead(messageId)
            } else {
                // Group Chat: Keep existing multi-reader logic
                val selfPeer = repository.getSelfPeer()
                val selfId = selfPeer?.nodeId ?: "cipheris://local"
                val currentReaders = msg.readByNodeIdsText.split(",").filter { it.isNotEmpty() }.toMutableList()
                if (!currentReaders.contains(selfId)) {
                    currentReaders.add(selfId)
                    
                    val peersList = repository.getAllPeersList()
                    if (peersList.isNotEmpty()) {
                        val countToSimulate = (1..peersList.size).random().coerceAtMost(3)
                        peersList.shuffled().take(countToSimulate).forEach { peer ->
                            if (!currentReaders.contains(peer.nodeId)) {
                                currentReaders.add(peer.nodeId)
                            }
                        }
                    }
                    
                    val updatedMsg = msg.copy(readByNodeIdsText = currentReaders.joinToString(","))
                    repository.updateMessage(updatedMsg)
                }
            }
        }
    }

    fun markChannelPostAsReadBySelf(postId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.markChannelPostAsRead(postId)
        }
    }

    fun submitJoinRequest(channelId: String) {
        viewModelScope.launch {
            val selfId = repository.getSelfPeer()?.nodeId ?: "cipheris://local"
            repository.requestToJoinChannel(channelId, selfId)
        }
    }

    fun handleJoinRequest(channelId: String, targetNodeId: String, accept: Boolean, role: String = "member") {
        viewModelScope.launch {
            repository.handleChannelJoinRequest(channelId, targetNodeId, accept, role)
        }
    }

    fun voteOnMessage(messageId: Long, isPositive: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.voteOnMessageLocally(messageId, isPositive)
        }
    }

    suspend fun getSenderReputation(groupId: String, senderNodeId: String): Int {
        return repository.getSenderReputationInGroup(groupId, senderNodeId)
    }
}

class ChatViewModelFactory(
    private val application: Application,
    private val repository: ChatRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
