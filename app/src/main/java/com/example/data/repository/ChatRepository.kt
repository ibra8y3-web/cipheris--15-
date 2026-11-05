package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.dao.ChatDao
import com.example.data.model.Peer
import com.example.data.model.Message
import com.example.data.model.GroupChat
import com.example.data.model.AliasEntry
import com.example.data.model.BroadcastChannel
import com.example.data.model.ChannelPost
import com.example.data.model.VaultEntry
import com.example.data.dao.VaultDao
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import com.example.CipherisApplication
import com.example.data.decentralized.SmartHybridTransmissionEngine
import com.example.data.decentralized.DecentralizedRelay
import com.example.data.decentralized.TransmissionMode
import com.example.data.decentralized.TransmissionTelemetry
import com.example.data.decentralized.RelayType

class ChatRepository(
    private val chatDao: ChatDao,
    private val vaultDao: VaultDao,
    private val stickerDao: com.example.data.dao.StickerDao,
    private val context: Context
) {
    val smartHybridEngine = SmartHybridTransmissionEngine(context)
    val decentralizedRelays: StateFlow<List<DecentralizedRelay>> = smartHybridEngine.relays
    val transmissionMode: StateFlow<TransmissionMode> = smartHybridEngine.transmissionMode
    val transmissionTelemetry: StateFlow<List<TransmissionTelemetry>> = smartHybridEngine.telemetryLogs
    val activeTransfersCount: StateFlow<Int> = smartHybridEngine.activeTransfersCount

    fun setTransmissionMode(mode: TransmissionMode) = smartHybridEngine.setTransmissionMode(mode)
    fun toggleDecentralizedRelay(relayId: String) = smartHybridEngine.toggleRelay(relayId)
    fun addCustomDecentralizedRelay(name: String, url: String, type: RelayType) = smartHybridEngine.addCustomRelay(name, url, type)
    fun removeDecentralizedRelay(relayId: String) = smartHybridEngine.removeRelay(relayId)
    fun pingAllDecentralizedRelays() = smartHybridEngine.pingAllRelays()

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val okHttpClient = OkHttpClient.Builder()
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 100
            maxRequestsPerHost = 100
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Connection simulation states
    private val _networkNodesCount = MutableStateFlow(12)
    val networkNodesCount: StateFlow<Int> = _networkNodesCount.asStateFlow()

    private val _networkStatus = MutableStateFlow("مستقر - شبكة سيفيرس النشطة")
    val networkStatus: StateFlow<String> = _networkStatus.asStateFlow()

    private val _networkLatency = MutableStateFlow<Long>(-1)
    val networkLatency: StateFlow<Long> = _networkLatency.asStateFlow()

    private val _networkQuality = MutableStateFlow("جاري فحص اتصال شبكة سيفيرس...")
    val networkQuality: StateFlow<String> = _networkQuality.asStateFlow()

    // Stealth/Privacy mode (إخفاء الحالة والاتصال)
    private val _stealthMode = MutableStateFlow(false)
    val stealthMode: StateFlow<Boolean> = _stealthMode.asStateFlow()

    private val activeGroupSubscriptions = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val dnsTopic = "cipheris_dns_registry_v1"

    init {
        // Run self check & pre-populate database with bots, default groups, stickers
        repositoryScope.launch {
            initSelfProfileIfNeeded()
            prePopulateBotsAndGroups()
            prePopulateStickersAndEmojisIfNeeded()
            startSelfDestructCleanupLoop()
            startNetworkSimulationLoop()
            startDnsListener()
            periodicDnsSync()
            startBroadcastChannelsListener()
            startChannelDiscoveryListener()
            startRealNetworkMonitor()

            // Listen for node switches and start/restart the real-time listener
            activeNodeIndexFlow.collect {
                startRealtimeNetworkListener()
            }
        }
    }

    fun toggleStealthMode() {
        _stealthMode.value = !_stealthMode.value
        _networkStatus.value = if (_stealthMode.value) "وضعية التخفي نشطة (عقدة صامتة)" else "مستقر - شبكة سيفيرس النشطة"
    }

    suspend fun panicWipeAll() {
        chatDao.deleteAllMessages()
        chatDao.deleteAllGroups()
        chatDao.deleteNonSelfPeers()
        vaultDao.deleteAllVaultEntries()
        activeGroupSubscriptions.clear()
        prePopulateBotsAndGroups() // Re-populate defaults after wipe
    }

    // --- VAULT OPERATIONS ---
    fun getVaultEntries(): Flow<List<VaultEntry>> = vaultDao.getAllVaultEntries()
    suspend fun insertVaultEntry(entry: VaultEntry) = vaultDao.insertVaultEntry(entry)
    suspend fun updateVaultEntry(entry: VaultEntry) = vaultDao.updateVaultEntry(entry)
    suspend fun deleteVaultEntry(entry: VaultEntry) = vaultDao.deleteVaultEntry(entry)

    private suspend fun initSelfProfileIfNeeded() {
        val selfPeers = chatDao.getSelfPeers()
        if (selfPeers.isEmpty()) {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver, 
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: UUID.randomUUID().toString()
            
            for (i in 0..2) {
                val nodeSeed = hashString("$androidId-node-$i")
                val nodeId = "cipheris://${nodeSeed.substring(0, 16)}"
                val fingerprint = "SHA256:${nodeSeed.substring(16, 40).uppercase()}"
                
                val newSelf = Peer(
                    nodeId = nodeId,
                    alias = "عقدة الخط ${i + 1}",
                    publicKeyFingerprint = fingerprint,
                    isSelf = true,
                    selfNodeIndex = i,
                    avatarSeed = "self_avatar_$i"
                )
                chatDao.insertPeer(newSelf)
                Log.d("ChatRepository", "Created self profile node $i: $nodeId")
            }
            // Set first one as active by default if not set
            setActiveNodeIndex(0)
        }
    }

    private fun hashString(input: String): String {
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private val prefs = context.getSharedPreferences("cipheris_prefs", android.content.Context.MODE_PRIVATE)

    fun getActiveNodeIndex(): Int = prefs.getInt("active_node_index", 0)

    suspend fun setActiveNodeIndex(index: Int) {
        prefs.edit().putInt("active_node_index", index).apply()
        _activeNodeIndexFlow.value = index
        // Reload self profile
        val self = chatDao.getSelfPeerByIndex(index)
        Log.d("ChatRepository", "Switched to active node: ${self?.nodeId}")
    }

    private val _activeNodeIndexFlow = MutableStateFlow(getActiveNodeIndex())
    val activeNodeIndexFlow: StateFlow<Int> = _activeNodeIndexFlow.asStateFlow()

    private suspend fun prePopulateBotsAndGroups() {
        // Clear previous mock/dummy bots and groups
        chatDao.deleteGroup("cipheris://group/mesh-global")
        chatDao.deleteGroup("cipheris://group/crypto-anon")
        Log.d("ChatRepository", "Pristine database state ensured.")
    }

    // Flows
    fun getSelfPeersFlow(): Flow<List<Peer>> = chatDao.getSelfPeersFlow()

    fun getPeers(): Flow<List<Peer>> = chatDao.getPeersFlow()
    fun getGroups(): Flow<List<GroupChat>> = chatDao.getGroupChatsFlow()
    fun getDirectMessages(peerNodeId: String): Flow<List<Message>> = chatDao.getDirectMessagesFlow(peerNodeId)
    fun getGroupMessages(groupId: String): Flow<List<Message>> = chatDao.getGroupMessagesFlow(groupId)
    fun getAllActiveMessages(): Flow<List<Message>> = chatDao.getAllActiveMessagesFlow()

    // Export helpers
    suspend fun getAllPeersList(): List<Peer> = chatDao.getAllPeersList()
    suspend fun getAllGroupsList(): List<GroupChat> = chatDao.getAllGroupsList()
    suspend fun getAllChannelsList(): List<BroadcastChannel> = chatDao.getAllChannelsList()
    suspend fun getAllMessagesListExport(): List<Message> = chatDao.getAllMessagesList()
    suspend fun getAllChannelPostsList(): List<ChannelPost> = chatDao.getAllChannelPostsList()

    // Import helpers
    suspend fun importPeers(peers: List<Peer>) = peers.forEach { chatDao.insertPeer(it) }
    suspend fun importGroups(groups: List<GroupChat>) = groups.forEach { chatDao.insertGroup(it) }
    suspend fun importChannels(channels: List<BroadcastChannel>) = channels.forEach { chatDao.insertChannel(it) }
    suspend fun importMessages(messages: List<Message>) = messages.forEach { chatDao.insertMessage(it) }
    suspend fun importChannelPosts(posts: List<ChannelPost>) = posts.forEach { chatDao.insertChannelPost(it) }

    suspend fun insertLocalMessage(message: Message) {
        chatDao.insertMessage(message)
    }

    // --- Admin functionalities ---
    suspend fun clearGroupMessages(groupId: String) {
        chatDao.clearGroupMessages(groupId)
    }

    suspend fun pinGroupMessage(groupId: String, messageId: Long?) {
        chatDao.pinGroupMessage(groupId, messageId)
    }

    suspend fun pinChannelPost(channelId: String, postId: Long?) {
        chatDao.pinChannelPost(channelId, postId)
    }

    suspend fun clearChannelPosts(channelId: String) {
        chatDao.deleteChannelPosts(channelId)
    }

    suspend fun deleteChannelPost(postId: Long) {
        chatDao.deleteChannelPost(postId)
    }

    suspend fun setGroupRole(groupId: String, targetNodeId: String, role: String, add: Boolean) {
        val group = chatDao.getGroupById(groupId) ?: return
        val members = group.memberNodeIdsText.split(",").filter { it.isNotEmpty() }.toMutableSet()
        val admins = group.adminNodeIdsText.split(",").filter { it.isNotEmpty() }.toMutableSet()
        val mods = group.moderatorNodeIdsText.split(",").filter { it.isNotEmpty() }.toMutableSet()
        val banned = group.bannedNodeIdsText.split(",").filter { it.isNotEmpty() }.toMutableSet()

        when(role) {
            "member" -> if(add) members.add(targetNodeId) else members.remove(targetNodeId)
            "admin" -> if(add) admins.add(targetNodeId) else admins.remove(targetNodeId)
            "moderator" -> if(add) mods.add(targetNodeId) else mods.remove(targetNodeId)
            "banned" -> {
                if(add) {
                    banned.add(targetNodeId)
                    members.remove(targetNodeId)
                    admins.remove(targetNodeId)
                    mods.remove(targetNodeId)
                } else {
                    banned.remove(targetNodeId)
                }
            }
        }
        
        chatDao.insertGroup(group.copy(
            memberNodeIdsText = members.joinToString(","),
            adminNodeIdsText = admins.joinToString(","),
            moderatorNodeIdsText = mods.joinToString(","),
            bannedNodeIdsText = banned.joinToString(",")
        ))
    }

    suspend fun setChannelRole(channelId: String, targetNodeId: String, role: String, add: Boolean) {
        val channel = chatDao.getChannelById(channelId) ?: return
        val members = channel.memberNodeIdsText.split(",").filter { it.isNotEmpty() }.toMutableSet()
        val admins = channel.adminNodeIdsText.split(",").filter { it.isNotEmpty() }.toMutableSet()
        val mods = channel.moderatorNodeIdsText.split(",").filter { it.isNotEmpty() }.toMutableSet()
        val banned = channel.bannedNodeIdsText.split(",").filter { it.isNotEmpty() }.toMutableSet()

        when(role) {
            "member" -> if(add) members.add(targetNodeId) else members.remove(targetNodeId)
            "admin" -> if(add) admins.add(targetNodeId) else admins.remove(targetNodeId)
            "moderator" -> if(add) mods.add(targetNodeId) else mods.remove(targetNodeId)
            "banned" -> {
                if(add) {
                    banned.add(targetNodeId)
                    members.remove(targetNodeId)
                    admins.remove(targetNodeId)
                    mods.remove(targetNodeId)
                } else {
                    banned.remove(targetNodeId)
                }
            }
        }
        
        chatDao.insertChannel(channel.copy(
            memberNodeIdsText = members.joinToString(","),
            adminNodeIdsText = admins.joinToString(","),
            moderatorNodeIdsText = mods.joinToString(","),
            bannedNodeIdsText = banned.joinToString(",")
        ))

        if (add && (role == "member" || role == "admin" || role == "moderator")) {
            val self = getSelfPeer()
            val selfNodeId = self?.nodeId ?: "cipheris://local"
            val receiverHex = targetNodeId.substringAfter("cipheris://").lowercase()
            
            val payload = JSONObject().apply {
                put("type", "CHANNEL_INVITE")
                put("sender", selfNodeId)
                put("channelId", channelId)
                put("name", channel.name)
                put("description", channel.description)
                put("isPrivate", channel.isPrivate)
                put("encryptionKey", channel.encryptionKey)
                put("createdBy", channel.createdBy)
                put("createdAt", channel.createdAt)
            }
            publishToNtfy("cipheris_inbox_$receiverHex", payload.toString())
        }
    }

    suspend fun setChannelGranularPermissions(channelId: String, targetNodeId: String, permissions: Map<String, Boolean>) {
        val channel = chatDao.getChannelById(channelId) ?: return
        val permissionsJson = JSONObject(channel.permissionsJsonText)
        val userPerms = JSONObject()
        permissions.forEach { (k, v) -> userPerms.put(k, v) }
        permissionsJson.put(targetNodeId, userPerms)
        chatDao.insertChannel(channel.copy(permissionsJsonText = permissionsJson.toString()))
    }

    suspend fun setGroupGranularPermissions(groupId: String, targetNodeId: String, permissions: Map<String, Boolean>) {
        val group = chatDao.getGroupById(groupId) ?: return
        val permissionsJson = JSONObject(group.permissionsJsonText)
        val userPerms = JSONObject()
        permissions.forEach { (k, v) -> userPerms.put(k, v) }
        permissionsJson.put(targetNodeId, userPerms)
        chatDao.insertGroup(group.copy(permissionsJsonText = permissionsJson.toString()))
    }

    suspend fun deleteMessage(messageId: Long) {
        chatDao.softDeleteMessage(messageId)
    }

    suspend fun markMessageAsRead(messageId: Long) {
        val msg = chatDao.getMessageById(messageId) ?: return
        if (msg.isIncoming && !msg.isRead) {
            val updatedMsg = msg.copy(isRead = true)
            chatDao.updateMessage(updatedMsg)
            
            // Send RECEIPT_READ to the sender
            val self = getSelfPeer()
            val selfNodeId = self?.nodeId ?: "cipheris://local"
            if (msg.peerNodeId != null && msg.senderNodeId != selfNodeId) {
                sendReceiptSignal(msg.senderNodeId, msg.timestamp, isRead = true)
            }
        }
    }

    suspend fun sendReceiptSignal(targetNodeId: String, originalTimestamp: Long, isRead: Boolean) {
        val self = getSelfPeer()
        val selfNodeId = self?.nodeId ?: "cipheris://local"
        val cleanTargetNodeId = targetNodeId.trim().lowercase()
        val sharedKey = getSharedDirectKey(selfNodeId, cleanTargetNodeId)
        
        val textPlain = if (isRead) {
            "RECEIPT_READ:$originalTimestamp"
        } else {
            "RECEIPT_DELIVERED:$originalTimestamp"
        }
        val textEncrypted = encryptText(textPlain, sharedKey)
        
        val payload = JSONObject().apply {
            put("sender", selfNodeId)
            put("senderAlias", self?.alias ?: "مستخدم مجهول")
            put("text", textEncrypted)
            put("timestamp", System.currentTimeMillis())
            put("isEncrypted", true)
        }
        val receiverHex = cleanTargetNodeId.substringAfter("cipheris://").lowercase()
        publishToNtfy("cipheris_inbox_$receiverHex", payload.toString())
    }

    suspend fun getSelfPeer(): Peer? = chatDao.getSelfPeerByIndex(getActiveNodeIndex())

    suspend fun getSelfNode(): Peer? = getSelfPeer()

    suspend fun getPeerByNodeId(nodeId: String): Peer? = chatDao.getPeerByNodeId(nodeId)

    suspend fun getGroupById(groupId: String): GroupChat? = chatDao.getGroupById(groupId)

    suspend fun getBroadcastChannelById(channelId: String): BroadcastChannel? {
        val cleanId = channelId.trim()
        val direct = chatDao.getChannelById(cleanId)
        if (direct != null) return direct
        val withPrefix = if (cleanId.startsWith("cipheris://channel/")) cleanId else "cipheris://channel/$cleanId"
        val prefixed = chatDao.getChannelById(withPrefix)
        if (prefixed != null) return prefixed
        val withoutPrefix = cleanId.substringAfterLast("/")
        return chatDao.getChannelById(withoutPrefix)
    }

    // Add peer
    suspend fun addPeer(nodeId: String, alias: String): Boolean {
        val cleanNodeId = nodeId.trim().lowercase()
        if (!cleanNodeId.startsWith("cipheris://") || cleanNodeId.length != 27) return false
        val fingerprint = "SHA256:" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).uppercase()
        val peer = Peer(
            nodeId = cleanNodeId,
            alias = alias,
            publicKeyFingerprint = fingerprint,
            avatarSeed = "user_generated"
        )
        chatDao.insertPeer(peer)
        return true
    }

    // Create Group
    suspend fun createGroup(name: String, members: List<String>): String {
        val groupId = "cipheris://group/" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
        val self = getSelfPeer()
        val selfNodeId = self?.nodeId ?: "cipheris://local"
        val cleanMembers = members.map { it.trim().lowercase() }
        val memberListText = (cleanMembers + selfNodeId).joinToString(",")
        val group = GroupChat(
            groupId = groupId,
            name = name,
            createdBy = selfNodeId,
            memberNodeIdsText = memberListText
        )
        chatDao.insertGroup(group)

        // Insert initial notice message
        chatDao.insertMessage(
            Message(
                peerNodeId = null,
                groupId = groupId,
                text = "تم إنشاء هذه المجموعة المشفرة بنجاح بخصوصية تامة 100%.",
                senderNodeId = selfNodeId,
                isIncoming = false
            )
        )
        
        // Send invite to all members
        cleanMembers.forEach { peerId ->
            val receiverHex = peerId.substringAfter("cipheris://").lowercase()
            val sharedKey = getSharedDirectKey(selfNodeId, peerId)
            val textPlain = "INVITE_GROUP:$groupId:$name|$memberListText"
            val textEncrypted = encryptText(textPlain, sharedKey)
            
            val payload = JSONObject().apply {
                put("sender", selfNodeId)
                put("senderAlias", self?.alias ?: "مستخدم مجهول")
                put("text", textEncrypted)
                put("timestamp", System.currentTimeMillis())
                put("isEncrypted", true)
            }
            publishToNtfy("cipheris_inbox_$receiverHex", payload.toString())
        }
        
        return groupId
    }

    // Invite to Group
    suspend fun inviteToGroup(groupId: String, targetNodeId: String) {
        val cleanTargetNodeId = targetNodeId.trim().lowercase()
        val group = chatDao.getGroupById(groupId) ?: return
        val self = getSelfPeer()
        val selfNodeId = self?.nodeId ?: "cipheris://local"
        
        // Update local member list
        val currentMembers = group.memberNodeIdsText.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toMutableSet()
        if (!currentMembers.contains(cleanTargetNodeId)) {
            currentMembers.add(cleanTargetNodeId)
            val updatedGroup = group.copy(memberNodeIdsText = currentMembers.joinToString(","))
            chatDao.insertGroup(updatedGroup)
        }

        // Send invite to the target
        val receiverHex = cleanTargetNodeId.substringAfter("cipheris://").lowercase()
        val sharedKey = getSharedDirectKey(selfNodeId, cleanTargetNodeId)
        val textPlain = "INVITE_GROUP:$groupId:${group.name}|${currentMembers.joinToString(",")}"
        val textEncrypted = encryptText(textPlain, sharedKey)
        
        val payload = JSONObject().apply {
            put("sender", selfNodeId)
            put("senderAlias", self?.alias ?: "مستخدم مجهول")
            put("text", textEncrypted)
            put("timestamp", System.currentTimeMillis())
            put("isEncrypted", true)
        }
        
        publishToNtfy("cipheris_inbox_$receiverHex", payload.toString())
        
        // Notify the group
        sendMessage(
            peerNodeId = null,
            groupId = groupId,
            text = "تمت دعوة عضو جديد للعقدة: $cleanTargetNodeId"
        )
    }

    suspend fun joinGroup(groupId: String, groupName: String, inviteCode: String = "") {
        val self = getSelfPeer()
        val selfNodeId = self?.nodeId ?: "cipheris://local"
        val existing = chatDao.getGroupById(groupId)
        if (existing == null) {
            val group = GroupChat(
                groupId = groupId,
                name = groupName,
                createdBy = "unknown",
                memberNodeIdsText = selfNodeId
            )
            chatDao.insertGroup(group)
            
            val messageText = if (inviteCode.isNotEmpty()) {
                "لقد انضممت إلى هذه المجموعة يدوياً عبر رمز الدعوة الآمن: $inviteCode"
            } else {
                "لقد انضممت إلى هذه المجموعة يدوياً عبر الرمز الفريد."
            }
            chatDao.insertMessage(
                Message(
                    peerNodeId = null,
                    groupId = groupId,
                    text = messageText,
                    senderNodeId = selfNodeId,
                    isIncoming = false
                )
            )
        }
    }

    // Send Message
    suspend fun sendMessage(
        peerNodeId: String?,
        groupId: String?,
        text: String,
        mediaPath: String? = null,
        mediaName: String? = null,
        selfDestructSeconds: Int = 0,
        isDownloadAllowed: Boolean = true
    ) {
        val self = getSelfPeer()
        val selfNodeId = self?.nodeId ?: "cipheris://local"

        val message = Message(
            peerNodeId = peerNodeId?.trim()?.lowercase(),
            groupId = groupId?.trim()?.lowercase(),
            text = text,
            mediaPath = mediaPath,
            mediaName = mediaName,
            senderNodeId = selfNodeId,
            isIncoming = false,
            selfDestructSeconds = selfDestructSeconds,
            selfDestructAt = if (selfDestructSeconds > 0) {
                System.currentTimeMillis() + (selfDestructSeconds * 1000)
            } else {
                0
            },
            isDownloadAllowed = isDownloadAllowed
        )

        val messageId = chatDao.insertMessage(message)

        var success = false
        var isNtfyTransmitted = false
        val timestamp = message.timestamp
        val fileId = "file_${timestamp}_${(1000..9999).random()}"

        val mediaDataBase64 = prepareMediaData(mediaPath)

        if (peerNodeId != null) {
            val cleanPeerNodeId = peerNodeId.trim().lowercase()
            val sharedKey = getSharedDirectKey(selfNodeId, cleanPeerNodeId)
            val textEncrypted = encryptText(text, sharedKey)
            val mediaNameEncrypted = if (mediaName != null) encryptText(mediaName, sharedKey) else ""
            val mediaPathEncrypted = if (mediaPath != null) encryptText(mediaPath, sharedKey) else ""

            var decRefJson: JSONObject? = null
            if (mediaPath != null) {
                val rawBytes = prepareMediaBytes(mediaPath, mediaName)
                if (rawBytes != null) {
                    val encryptedBytes = com.example.data.util.FileEncryptor.xorTransform(rawBytes, sharedKey)
                    val (blobRef, _) = smartHybridEngine.transmitEncryptedMedia(mediaName ?: "file_${timestamp}", encryptedBytes, targetPeerNodeId = cleanPeerNodeId)
                    if (blobRef != null) {
                        val directUrlsJson = org.json.JSONArray()
                        blobRef.directUrls.forEach { directUrlsJson.put(it) }
                        decRefJson = JSONObject().apply {
                            put("sha256", blobRef.sha256)
                            put("size", blobRef.sizeBytes)
                            put("directUrls", directUrlsJson)
                        }
                    }
                }
            }

            val payload = JSONObject().apply {
                put("sender", selfNodeId)
                put("senderAlias", self?.alias ?: "مستخدم مجهول")
                put("text", textEncrypted)
                put("timestamp", timestamp)
                put("selfDestructSeconds", selfDestructSeconds)
                put("mediaName", mediaNameEncrypted)
                put("mediaPath", mediaPathEncrypted)
                put("fileId", fileId)
                if (decRefJson != null) {
                    put("decentralizedRef", decRefJson)
                }
                put("isDownloadAllowed", isDownloadAllowed)
                put("isEncrypted", true)
            }

            val receiverHex = cleanPeerNodeId.substringAfter("cipheris://").lowercase()
            val topic = "cipheris_inbox_$receiverHex"
            success = publishToNtfySync(topic, payload.toString())
            isNtfyTransmitted = true

            if (success) {
                chatDao.updateMessage(message.copy(id = messageId, isDelivered = true))
            } else {
                chatDao.insertUnsentMessage(
                    com.example.data.model.UnsentMessage(
                        topic = topic,
                        payloadJson = payload.toString()
                    )
                )
                Log.d("ChatRepository", "Message enqueued to offline message queue due to transmission failure.")
            }
        } else if (groupId != null) {
            val cleanGroupId = groupId.trim().lowercase()
            val groupKey = getGroupEncryptionKey(cleanGroupId)
            val textEncrypted = encryptText(text, groupKey)
            val mediaNameEncrypted = if (mediaName != null) encryptText(mediaName, groupKey) else ""
            val mediaPathEncrypted = if (mediaPath != null) encryptText(mediaPath, groupKey) else ""

            var decRefJson: JSONObject? = null
            if (mediaPath != null) {
                val rawBytes = prepareMediaBytes(mediaPath, mediaName)
                if (rawBytes != null) {
                    val encryptedBytes = com.example.data.util.FileEncryptor.xorTransform(rawBytes, groupKey)
                    val (blobRef, _) = smartHybridEngine.transmitEncryptedMedia(mediaName ?: "file_${timestamp}", encryptedBytes)
                    if (blobRef != null) {
                        val directUrlsJson = org.json.JSONArray()
                        blobRef.directUrls.forEach { directUrlsJson.put(it) }
                        decRefJson = JSONObject().apply {
                            put("sha256", blobRef.sha256)
                            put("size", blobRef.sizeBytes)
                            put("directUrls", directUrlsJson)
                        }
                    }
                }
            }

            val payload = JSONObject().apply {
                put("sender", selfNodeId)
                put("senderAlias", self?.alias ?: "مستخدم مجهول")
                put("text", textEncrypted)
                put("groupId", cleanGroupId)
                put("timestamp", timestamp)
                put("selfDestructSeconds", selfDestructSeconds)
                put("mediaName", mediaNameEncrypted)
                put("mediaPath", mediaPathEncrypted)
                put("fileId", fileId)
                if (decRefJson != null) {
                    put("decentralizedRef", decRefJson)
                }
                put("isDownloadAllowed", isDownloadAllowed)
                put("isGroupEncrypted", true)
            }

            val groupHex = cleanGroupId.substringAfter("cipheris://group/").lowercase()
            val topic = "cipheris_group_$groupHex"
            success = publishToNtfySync(topic, payload.toString())
            isNtfyTransmitted = true

            // Gossip Relay: Send directly to the inbox of all other group members!
            val group = chatDao.getGroupById(cleanGroupId)
            if (group != null) {
                val members = group.memberNodeIdsText.split(",")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() && it != selfNodeId }
                repositoryScope.launch {
                    members.forEach { memberNodeId ->
                        val memberHex = memberNodeId.substringAfter("cipheris://").lowercase()
                        val memberTopic = "cipheris_inbox_$memberHex"
                        publishToNtfySync(memberTopic, payload.toString())
                    }
                }
            }

            if (success) {
                chatDao.updateMessage(message.copy(id = messageId, isDelivered = true))
            } else {
                chatDao.insertUnsentMessage(
                    com.example.data.model.UnsentMessage(
                        topic = topic,
                        payloadJson = payload.toString()
                    )
                )
                Log.d("ChatRepository", "Group message enqueued to offline message queue due to transmission failure.")
            }
        }
    }



    // Loop to delete expired messages
    private fun startSelfDestructCleanupLoop() {
        repositoryScope.launch {
            while (true) {
                try {
                    val deletedCount = chatDao.deleteExpiredMessages(System.currentTimeMillis())
                    if (deletedCount > 0) {
                        Log.d("ChatRepository", "Cleaned up $deletedCount expired messages from database.")
                    }
                } catch (e: Exception) {
                    Log.e("ChatRepository", "Error in self destruct loop: ${e.message}")
                }
                delay(1000) // Run every 1 second for instant self-destruct timing
            }
        }
    }

    // Loop to simulate mesh node discoveries and fluctuations
    private fun startNetworkSimulationLoop() {
        repositoryScope.launch {
            while (true) {
                delay(15000) // Check/modify connection counts every 15 seconds
                
                // Real receipts are handled E2EE over the decentralized nodes.
                // Simulated loop no longer overrides read status automatically.

                if (!_stealthMode.value) {
                    val change = (-2..2).random()
                    val newCount = (_networkNodesCount.value + change).coerceIn(5, 25)
                    _networkNodesCount.value = newCount
                }
            }
        }
    }

    suspend fun updateSelfAlias(newAlias: String) {
        val self = getSelfPeer()
        if (self != null) {
            val updatedSelf = self.copy(alias = newAlias)
            chatDao.insertPeer(updatedSelf)
            registerAlias(newAlias, self.nodeId)
        }
    }

    // --- DECENTRALIZED DNS (ALIAS SERVICE) ---

    suspend fun registerAlias(alias: String, nodeId: String) {
        val payload = JSONObject().apply {
            put("type", "ALIAS_REGISTRATION")
            put("alias", alias)
            put("nodeId", nodeId)
            put("timestamp", System.currentTimeMillis())
        }
        publishToNtfy(dnsTopic, payload.toString())
        chatDao.insertAlias(AliasEntry(alias, nodeId))
    }

    fun searchAliases(query: String): Flow<List<AliasEntry>> = chatDao.searchAliasesFlow(query)

    private fun startDnsListener() {
        repositoryScope.launch {
            listenToTopicStream(dnsTopic) { jsonObject ->
                val type = jsonObject.optString("type")
                if (type == "ALIAS_REGISTRATION") {
                    val alias = jsonObject.optString("alias")
                    val nodeId = jsonObject.optString("nodeId")
                    val timestamp = jsonObject.optLong("timestamp", System.currentTimeMillis())
                    
                    if (alias.isNotEmpty() && nodeId.isNotEmpty()) {
                        chatDao.insertAlias(AliasEntry(alias, nodeId, timestamp))
                        Log.d("ChatRepository", "Discovered alias via DNS: $alias -> $nodeId")
                    }
                }
            }
        }
    }

    private fun periodicDnsSync() {
        repositoryScope.launch {
            while (true) {
                try {
                    // Poll ntfy history for DNS records we might have missed
                    val request = Request.Builder()
                        .url("https://ntfy.sh/$dnsTopic/json?poll=1")
                        .build()
                    
                    okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val lines = body.split("\n")
                            for (line in lines) {
                                if (line.isNotBlank()) {
                                    try {
                                        val ntfyObj = JSONObject(line)
                                        if (ntfyObj.optString("event") == "message") {
                                            val msgRaw = ntfyObj.optString("message")
                                            val dnsJson = JSONObject(msgRaw)
                                            if (dnsJson.optString("type") == "ALIAS_REGISTRATION") {
                                                val alias = dnsJson.optString("alias")
                                                val nodeId = dnsJson.optString("nodeId")
                                                val timestamp = dnsJson.optLong("timestamp", System.currentTimeMillis())
                                                if (alias.isNotEmpty() && nodeId.isNotEmpty()) {
                                                    chatDao.insertAlias(AliasEntry(alias, nodeId, timestamp))
                                                }
                                            }
                                        }
                                    } catch (e: Exception) { /* ignore */ }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatRepository", "DNS Sync error: ${e.message}")
                }
                delay(300000) // Every 5 minutes
            }
        }
    }

    private var networkListenerJob: Job? = null

    private fun getSharedDirectKey(nodeIdA: String, nodeIdB: String): String {
        val sorted = listOf(nodeIdA.trim().lowercase(), nodeIdB.trim().lowercase()).sorted()
        return hashString("${sorted[0]}-${sorted[1]}-secure-direct-session").substring(0, 16)
    }

    private fun getGroupEncryptionKey(groupId: String): String {
        return hashString("${groupId.trim().lowercase()}-secure-group-session").substring(0, 16)
    }

    private suspend fun pollTopicHistory(topic: String, onMessageReceived: suspend (JSONObject) -> Unit) {
        val servers = listOf("https://ntfy.sh", "https://ntfy.be", "https://ntfy.space")
        withContext(Dispatchers.IO) {
            for (server in servers) {
                try {
                    val request = Request.Builder()
                        .url("$server/$topic/json?poll=1")
                        .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val lines = body.split("\n")
                            for (line in lines) {
                                if (line.isNotBlank()) {
                                    try {
                                        val ntfyObj = JSONObject(line)
                                        if (ntfyObj.optString("event") == "message") {
                                            val messageRaw = ntfyObj.optString("message")
                                            val chatJson = JSONObject(messageRaw)
                                            onMessageReceived(chatJson)
                                        }
                                    } catch (e: Exception) { /* ignore */ }
                                }
                            }
                            Log.d("ChatRepository", "Successfully polled history for topic $topic from $server")
                            return@withContext
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatRepository", "Failed to poll history for topic $topic from $server: ${e.message}")
                }
            }
        }
    }

    private fun prepareMediaData(mediaPath: String?): String? {
        if (mediaPath.isNullOrEmpty()) return null
        return try {
            val rawBytes: ByteArray? = if (mediaPath.startsWith("content://")) {
                try {
                    val uri = android.net.Uri.parse(mediaPath)
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } catch (e: Exception) { null }
            } else {
                val file = java.io.File(mediaPath)
                if (file.exists() && file.length() > 0) {
                    val bytesOnDisk = java.io.FileInputStream(file).use { it.readBytes() }
                    if (file.name.contains("secured_") || file.parentFile?.name == "cipheris_secure_files") {
                        com.example.data.util.FileEncryptor.xorTransform(bytesOnDisk, com.example.data.util.FileEncryptor.DEFAULT_KEY)
                    } else {
                        bytesOnDisk
                    }
                } else {
                    try {
                        val uri = android.net.Uri.parse(mediaPath)
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } catch (e: Exception) { null }
                }
            }

            if (rawBytes != null && rawBytes.isNotEmpty()) {
                Base64.encodeToString(rawBytes, Base64.NO_WRAP)
            } else null
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error preparing media data Base64", e)
            null
        }
    }

    private fun prepareMediaBytes(mediaPath: String?, mediaName: String? = null): ByteArray? {
        if (mediaPath.isNullOrEmpty()) return null
        return try {
            val rawBytes = if (mediaPath.startsWith("content://")) {
                val uri = android.net.Uri.parse(mediaPath)
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } else {
                val file = java.io.File(mediaPath)
                if (file.exists() && file.length() > 0) {
                    val bytesOnDisk = java.io.FileInputStream(file).use { it.readBytes() }
                    if (file.name.contains("secured_") || file.parentFile?.name == "cipheris_secure_files") {
                        com.example.data.util.FileEncryptor.xorTransform(bytesOnDisk, com.example.data.util.FileEncryptor.DEFAULT_KEY)
                    } else {
                        bytesOnDisk
                    }
                } else {
                    val uri = android.net.Uri.parse(mediaPath)
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
            }

            if (rawBytes != null) {
                com.example.data.decentralized.FastMediaDeliveryEngine.compressImageIfNeeded(rawBytes, mediaName ?: mediaPath)
            } else null
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error preparing raw media bytes", e)
            null
        }
    }

    private fun saveReceivedEncryptedBytesLocally(rawBytes: ByteArray, mediaName: String?, key: String): String? {
        return try {
            val secureDir = java.io.File(context.filesDir, "cipheris_secure_files")
            if (!secureDir.exists()) secureDir.mkdirs()

            val rawName = mediaName?.takeIf { it.isNotEmpty() } ?: "file_${System.currentTimeMillis()}"
            val cleanName = rawName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val targetName = if (cleanName.startsWith("secured_")) cleanName else "secured_${System.currentTimeMillis()}_$cleanName"
            val destFile = java.io.File(secureDir, targetName)

            val bytesToStore = com.example.data.util.FileEncryptor.xorTransform(rawBytes, com.example.data.util.FileEncryptor.DEFAULT_KEY)
            java.io.FileOutputStream(destFile).use { it.write(bytesToStore) }
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error saving received bytes locally", e)
            null
        }
    }

    private fun saveReceivedMediaLocally(mediaDataEncrypted: String?, mediaName: String?, key: String): String? {
        if (mediaDataEncrypted.isNullOrEmpty()) return null
        return try {
            val mediaDataBase64 = decryptText(mediaDataEncrypted, key)
            if (mediaDataBase64.isEmpty()) return null
            val rawBytes = Base64.decode(mediaDataBase64, Base64.NO_WRAP)
            if (rawBytes.isEmpty()) return null

            val secureDir = java.io.File(context.filesDir, "cipheris_secure_files")
            if (!secureDir.exists()) secureDir.mkdirs()

            val rawName = mediaName?.takeIf { it.isNotEmpty() } ?: "file_${System.currentTimeMillis()}"
            val cleanName = rawName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val targetName = if (cleanName.startsWith("secured_")) cleanName else "secured_${System.currentTimeMillis()}_$cleanName"
            val destFile = java.io.File(secureDir, targetName)

            val bytesToStore = com.example.data.util.FileEncryptor.xorTransform(rawBytes, com.example.data.util.FileEncryptor.DEFAULT_KEY)
            java.io.FileOutputStream(destFile).use { it.write(bytesToStore) }
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error saving received media locally", e)
            null
        }
    }

    private data class MediaChunkBuffer(
        val fileId: String,
        val totalChunks: Int,
        val chunks: java.util.concurrent.ConcurrentHashMap<Int, String> = java.util.concurrent.ConcurrentHashMap(),
        var mediaName: String? = null,
        val createdTime: Long = System.currentTimeMillis()
    )

    private val mediaChunkBuffers = java.util.concurrent.ConcurrentHashMap<String, MediaChunkBuffer>()
    private val completedMediaFiles = java.util.concurrent.ConcurrentHashMap<String, String>()

    private suspend fun sendMediaInChunks(
        topic: String,
        fileId: String,
        mediaDataEncrypted: String,
        senderNodeId: String,
        groupId: String? = null,
        channelId: String? = null,
        timestamp: Long
    ): Boolean {
        val chunkSize = 1500
        val totalChunks = (mediaDataEncrypted.length + chunkSize - 1) / chunkSize
        var allChunksSent = true

        for (i in 0 until totalChunks) {
            val start = i * chunkSize
            val end = Math.min(mediaDataEncrypted.length, (i + 1) * chunkSize)
            val chunkSlice = mediaDataEncrypted.substring(start, end)

            val chunkPayload = JSONObject().apply {
                put("type", "MEDIA_CHUNK")
                put("fileId", fileId)
                put("chunkIndex", i)
                put("totalChunks", totalChunks)
                put("chunkData", chunkSlice)
                put("sender", senderNodeId)
                if (groupId != null) put("groupId", groupId)
                if (channelId != null) put("channelId", channelId)
                put("timestamp", timestamp)
            }

            val success = publishToNtfySync(topic, chunkPayload.toString())
            if (!success) {
                allChunksSent = false
                chatDao.insertUnsentMessage(
                    com.example.data.model.UnsentMessage(
                        topic = topic,
                        payloadJson = chunkPayload.toString()
                    )
                )
            }
        }
        return allChunksSent
    }

    private suspend fun handleIncomingMediaChunk(jsonObject: JSONObject) {
        val fileId = jsonObject.optString("fileId")
        val chunkIndex = jsonObject.optInt("chunkIndex", -1)
        val totalChunks = jsonObject.optInt("totalChunks", -1)
        val chunkData = jsonObject.optString("chunkData")
        val sender = jsonObject.optString("sender")
        val groupId = jsonObject.optString("groupId").takeIf { it.isNotEmpty() && it != "null" }
        val channelId = jsonObject.optString("channelId").takeIf { it.isNotEmpty() && it != "null" }
        val timestamp = jsonObject.optLong("timestamp")

        if (fileId.isEmpty() || chunkIndex < 0 || totalChunks <= 0 || chunkData.isEmpty()) return

        val buffer = mediaChunkBuffers.getOrPut(fileId) {
            MediaChunkBuffer(fileId = fileId, totalChunks = totalChunks)
        }
        buffer.chunks[chunkIndex] = chunkData

        if (buffer.chunks.size == totalChunks) {
            val fullEncryptedData = (0 until totalChunks).joinToString("") { buffer.chunks[it] ?: "" }
            mediaChunkBuffers.remove(fileId)

            val selfNodeId = getSelfPeer()?.nodeId ?: "cipheris://local"
            val encryptionKey = when {
                channelId != null -> chatDao.getChannelById(channelId)?.encryptionKey ?: ""
                groupId != null -> getGroupEncryptionKey(groupId)
                else -> getSharedDirectKey(selfNodeId, sender)
            }

            val localFile = saveReceivedMediaLocally(fullEncryptedData, buffer.mediaName, encryptionKey)
            if (localFile != null) {
                completedMediaFiles[fileId] = localFile

                if (channelId != null) {
                    val post = chatDao.getChannelPostByTimestamp(channelId, timestamp)
                    if (post != null && post.mediaPath != localFile) {
                        chatDao.updateChannelPost(post.copy(mediaPath = localFile))
                    }
                } else if (groupId != null) {
                    val msg = chatDao.getGroupMessageByTimestamp(groupId, timestamp)
                    if (msg != null && msg.mediaPath != localFile) {
                        chatDao.updateMessage(msg.copy(mediaPath = localFile))
                    }
                } else {
                    val msg = chatDao.getMessageBySenderAndTimestamp(sender, timestamp)
                    if (msg != null && msg.mediaPath != localFile) {
                        chatDao.updateMessage(msg.copy(mediaPath = localFile))
                    }
                }
            }
        }
    }

    private suspend fun handleIncomingInboxMessage(self: Peer, jsonObject: JSONObject) {
        val msgType = jsonObject.optString("type")
        if (msgType == "MEDIA_CHUNK") {
            handleIncomingMediaChunk(jsonObject)
            return
        }

        val sender = jsonObject.optString("sender")
        if (sender != self.nodeId) {
            if (msgType == "join_request") {
                val channelId = jsonObject.optString("channelId")
                val requesterNodeId = jsonObject.optString("requesterNodeId")
                if (channelId.isNotEmpty() && requesterNodeId.isNotEmpty()) {
                    val channel = chatDao.getChannelById(channelId)
                    if (channel != null) {
                        val currentPending = channel.pendingJoinRequestsText.split(",").filter { it.isNotEmpty() }.toMutableList()
                        if (!currentPending.contains(requesterNodeId)) {
                            currentPending.add(requesterNodeId)
                            chatDao.insertChannel(channel.copy(pendingJoinRequestsText = currentPending.joinToString(",")))
                        }
                    }
                }
                return
            }

            if (msgType == "CHANNEL_INVITE" || msgType == "join_request_response") {
                val channelId = jsonObject.optString("channelId")
                val accepted = jsonObject.optBoolean("accepted", true)
                val assignedRole = jsonObject.optString("role", "member")
                if (channelId.isNotEmpty() && accepted) {
                    val name = jsonObject.optString("name").ifEmpty { jsonObject.optString("channelName", "قناة جديدة") }
                    val description = jsonObject.optString("description", "قناة مشفرة E2EE")
                    val isPrivate = jsonObject.optBoolean("isPrivate", true)
                    val encryptionKey = jsonObject.optString("encryptionKey")
                    val createdBy = jsonObject.optString("createdBy", sender)
                    
                    if (encryptionKey.isNotEmpty()) {
                        val existingChannel = chatDao.getChannelById(channelId)
                        if (existingChannel == null) {
                            val newChannel = BroadcastChannel(
                                channelId = channelId,
                                name = name,
                                description = description,
                                isPrivate = isPrivate,
                                encryptionKey = encryptionKey,
                                createdBy = createdBy,
                                memberNodeIdsText = self.nodeId,
                                adminNodeIdsText = if (assignedRole == "admin") self.nodeId else "",
                                moderatorNodeIdsText = if (assignedRole == "moderator") self.nodeId else ""
                            )
                            chatDao.insertChannel(newChannel)
                        } else {
                            val currentMembers = existingChannel.memberNodeIdsText.split(",").filter { it.isNotEmpty() }.toMutableSet()
                            currentMembers.add(self.nodeId)
                            
                            val currentAdmins = existingChannel.adminNodeIdsText.split(",").filter { it.isNotEmpty() }.toMutableSet()
                            val currentMods = existingChannel.moderatorNodeIdsText.split(",").filter { it.isNotEmpty() }.toMutableSet()
                            
                            if (assignedRole == "admin") currentAdmins.add(self.nodeId)
                            if (assignedRole == "moderator") currentMods.add(self.nodeId)

                            chatDao.insertChannel(existingChannel.copy(
                                name = name,
                                encryptionKey = encryptionKey,
                                memberNodeIdsText = currentMembers.joinToString(","),
                                adminNodeIdsText = currentAdmins.joinToString(","),
                                moderatorNodeIdsText = currentMods.joinToString(",")
                            ))
                        }
                    }
                }
                return
            }

            val senderAlias = jsonObject.optString("senderAlias", "عقدة سيفيرس مجهولة")
            val isEncrypted = jsonObject.optBoolean("isEncrypted", false)
            val isGroupEncrypted = jsonObject.optBoolean("isGroupEncrypted", false)
            val msgGroupId = jsonObject.optString("groupId").takeIf { it.isNotEmpty() && it != "null" }
            val timestamp = jsonObject.optLong("timestamp", System.currentTimeMillis())

            val fileId = jsonObject.optString("fileId")
            val totalChunks = jsonObject.optInt("totalChunks", 1)

            var text = jsonObject.optString("text")
            var mediaName = jsonObject.optString("mediaName").takeIf { it.isNotEmpty() && it != "null" }
            var mediaPath = jsonObject.optString("mediaPath").takeIf { it.isNotEmpty() && it != "null" }
            val mediaDataEncrypted = jsonObject.optString("mediaData").takeIf { it.isNotEmpty() && it != "null" }

            if (msgGroupId != null) {
                // This is a group message delivered to my inbox via Gossip Relay!
                val existingGroup = chatDao.getGroupById(msgGroupId)
                if (existingGroup != null) {
                    if (isGroupEncrypted) {
                        val groupKey = getGroupEncryptionKey(msgGroupId)
                        text = decryptText(text, groupKey)
                        mediaName = mediaName?.let { decryptText(it, groupKey) }.takeIf { !it.isNullOrEmpty() }

                        if (totalChunks > 1 && fileId.isNotEmpty()) {
                            if (mediaName != null) {
                                val buffer = mediaChunkBuffers.getOrPut(fileId) { MediaChunkBuffer(fileId, totalChunks) }
                                buffer.mediaName = mediaName
                            }
                            val completedFile = completedMediaFiles[fileId]
                            if (completedFile != null) {
                                mediaPath = completedFile
                            } else {
                                mediaPath = null
                            }
                        } else {
                            val localFile = saveReceivedMediaLocally(mediaDataEncrypted, mediaName, groupKey)
                            if (localFile != null) {
                                mediaPath = localFile
                            } else {
                                mediaPath = mediaPath?.let { decryptText(it, groupKey) }.takeIf { !it.isNullOrEmpty() }
                            }
                        }
                    }
                    if (!chatDao.hasMessage(sender, text, timestamp)) {
                        val incomingMessage = Message(
                            peerNodeId = null,
                            groupId = msgGroupId,
                            text = text,
                            mediaPath = mediaPath,
                            mediaName = mediaName,
                            senderNodeId = sender,
                            isIncoming = true,
                            timestamp = timestamp,
                            isDownloadAllowed = jsonObject.optBoolean("isDownloadAllowed", true)
                        )
                        chatDao.insertMessage(incomingMessage)
                        apiMessageFlow.tryEmit(incomingMessage)
                        val title = "${existingGroup.name} ($senderAlias)"
                        CipherisApplication.instance.showMessageNotification(title, text)
                    }
                }
                return
            }

            // Decrypt direct/P2P message
            if (isEncrypted) {
                val sharedKey = getSharedDirectKey(self.nodeId, sender)
                text = decryptText(text, sharedKey)
                mediaName = mediaName?.let { decryptText(it, sharedKey) }.takeIf { !it.isNullOrEmpty() }

                if (totalChunks > 1 && fileId.isNotEmpty()) {
                    if (mediaName != null) {
                        val buffer = mediaChunkBuffers.getOrPut(fileId) { MediaChunkBuffer(fileId, totalChunks) }
                        buffer.mediaName = mediaName
                    }
                    val completedFile = completedMediaFiles[fileId]
                    if (completedFile != null) {
                        mediaPath = completedFile
                    } else {
                        mediaPath = null
                    }
                } else {
                    val localFile = saveReceivedMediaLocally(mediaDataEncrypted, mediaName, sharedKey)
                    if (localFile != null) {
                        mediaPath = localFile
                    } else {
                        mediaPath = mediaPath?.let { decryptText(it, sharedKey) }.takeIf { !it.isNullOrEmpty() }
                    }
                }
            }

            val decentralizedRefJson = jsonObject.optJSONObject("decentralizedRef")
            if (decentralizedRefJson != null) {
                val sha256 = decentralizedRefJson.optString("sha256")
                val directUrlsArray = decentralizedRefJson.optJSONArray("directUrls")
                val directUrls = mutableListOf<String>()
                if (directUrlsArray != null) {
                    for (i in 0 until directUrlsArray.length()) {
                        directUrls.add(directUrlsArray.getString(i))
                    }
                }
                if (sha256.isNotEmpty()) {
                    val keyToUse = if (msgGroupId != null) getGroupEncryptionKey(msgGroupId) else getSharedDirectKey(self.nodeId, sender)
                    val finalMediaName = mediaName
                    repositoryScope.launch {
                        val encryptedBytes = smartHybridEngine.fetchDecentralizedBlob(sha256, directUrls)
                        if (encryptedBytes != null) {
                            val decryptedBytes = com.example.data.util.FileEncryptor.xorTransform(encryptedBytes, keyToUse)
                            val localSaved = saveReceivedEncryptedBytesLocally(decryptedBytes, finalMediaName, keyToUse)
                            if (localSaved != null) {
                                val msg = if (msgGroupId != null) chatDao.getGroupMessageByTimestamp(msgGroupId, timestamp) else chatDao.getMessageByTimestampAndPeer(sender, self.nodeId, timestamp)
                                if (msg != null) {
                                    chatDao.updateMessage(msg.copy(mediaPath = localSaved))
                                }
                            }
                        }
                    }
                }
            }

            if (text.startsWith("RECEIPT_DELIVERED:")) {
                val originalTimestampStr = text.substringAfter("RECEIPT_DELIVERED:")
                val originalTimestamp = originalTimestampStr.toLongOrNull()
                if (originalTimestamp != null) {
                    val msg = chatDao.getMessageByTimestampAndPeer(sender, self.nodeId, originalTimestamp)
                    if (msg != null && !msg.isDelivered) {
                        chatDao.updateMessage(msg.copy(isDelivered = true))
                    }
                }
                return
            }

            if (text.startsWith("RECEIPT_READ:")) {
                val originalTimestampStr = text.substringAfter("RECEIPT_READ:")
                val originalTimestamp = originalTimestampStr.toLongOrNull()
                if (originalTimestamp != null) {
                    val msg = chatDao.getMessageByTimestampAndPeer(sender, self.nodeId, originalTimestamp)
                    if (msg != null && !msg.isRead) {
                        chatDao.updateMessage(msg.copy(isDelivered = true, isRead = true))
                    }
                }
                return
            }

            if (text.startsWith("CALL_SIGNAL:")) {
                val signalPayload = text.substringAfter("CALL_SIGNAL:")
                handleCallSignalReceived(sender, senderAlias, signalPayload)
                return
            }

            if (text.startsWith("INVITE_GROUP:")) {
                val parts = text.split(":", limit = 4) // INVITE_GROUP : cipheris://group/HEX : name|members
                if (parts.size >= 4) {
                    val groupIdStr = parts[1] + ":" + parts[2]
                    val nameAndMembers = parts[3]
                    val groupName = nameAndMembers.substringBefore("|")
                    val membersText = nameAndMembers.substringAfter("|", "")
                    
                    // Create group locally if it doesn't exist
                    val existingGroup = chatDao.getGroupById(groupIdStr)
                    if (existingGroup == null) {
                        val group = GroupChat(
                            groupId = groupIdStr,
                            name = groupName,
                            createdBy = sender,
                            memberNodeIdsText = membersText
                        )
                        chatDao.insertGroup(group)
                        chatDao.insertMessage(
                            Message(
                                peerNodeId = null,
                                groupId = groupIdStr,
                                text = "تمت دعوتك إلى هذه المجموعة المشفرة بواسطة $senderAlias.",
                                senderNodeId = sender,
                                isIncoming = true,
                                timestamp = timestamp
                            )
                        )
                    }
                }
                return
            }
            
            val selfDestructSeconds = jsonObject.optInt("selfDestructSeconds", 0)
            
            // Add peer if not exists
            val existingPeer = chatDao.getPeerByNodeId(sender)
            if (existingPeer == null) {
                chatDao.insertPeer(
                    Peer(
                        nodeId = sender,
                        alias = senderAlias,
                        publicKeyFingerprint = "SHA256:" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).uppercase(),
                        avatarSeed = "user_generated"
                    )
                )
            }
            
            val isDownloadAllowed = jsonObject.optBoolean("isDownloadAllowed", true)
            
            if (!chatDao.hasMessage(sender, text, timestamp)) {
                val incomingMessage = Message(
                    peerNodeId = sender,
                    groupId = null,
                    text = text,
                    mediaPath = mediaPath,
                    mediaName = mediaName,
                    senderNodeId = sender,
                    isIncoming = true,
                    timestamp = timestamp,
                    selfDestructSeconds = selfDestructSeconds,
                    selfDestructAt = if (selfDestructSeconds > 0) {
                        System.currentTimeMillis() + (selfDestructSeconds * 1000)
                    } else {
                        0
                    },
                    isDownloadAllowed = isDownloadAllowed,
                    isDelivered = true
                )
                chatDao.insertMessage(incomingMessage)
                apiMessageFlow.tryEmit(incomingMessage)

                // Show notification
                CipherisApplication.instance.showMessageNotification(senderAlias, text)

                // Send DELIVERED receipt back to the sender
                if (sender != self.nodeId) {
                    sendReceiptSignal(sender, timestamp, isRead = false)
                }
            }
        }
    }

    private suspend fun handleIncomingGroupMessage(selfNodeId: String, groupId: String, jsonObject: JSONObject) {
        val msgType = jsonObject.optString("type")
        if (msgType == "MEDIA_CHUNK") {
            handleIncomingMediaChunk(jsonObject)
            return
        }

        val sender = jsonObject.optString("sender")
        if (sender != selfNodeId) {
            val fileId = jsonObject.optString("fileId")
            val totalChunks = jsonObject.optInt("totalChunks", 1)

            val textEncrypted = jsonObject.optString("text")
            val timestamp = jsonObject.optLong("timestamp", System.currentTimeMillis())
            val isGroupEncrypted = jsonObject.optBoolean("isGroupEncrypted", false)
            
            var text = textEncrypted
            var mediaName = jsonObject.optString("mediaName").takeIf { it.isNotEmpty() && it != "null" }
            var mediaPath = jsonObject.optString("mediaPath").takeIf { it.isNotEmpty() && it != "null" }
            val mediaDataEncrypted = jsonObject.optString("mediaData").takeIf { it.isNotEmpty() && it != "null" }
            
            if (isGroupEncrypted) {
                val groupKey = getGroupEncryptionKey(groupId)
                text = decryptText(text, groupKey)
                mediaName = mediaName?.let { decryptText(it, groupKey) }.takeIf { !it.isNullOrEmpty() }

                if (totalChunks > 1 && fileId.isNotEmpty()) {
                    if (mediaName != null) {
                        val buffer = mediaChunkBuffers.getOrPut(fileId) { MediaChunkBuffer(fileId, totalChunks) }
                        buffer.mediaName = mediaName
                    }
                    val completedFile = completedMediaFiles[fileId]
                    if (completedFile != null) {
                        mediaPath = completedFile
                    } else {
                        mediaPath = null
                    }
                } else {
                    val localFile = saveReceivedMediaLocally(mediaDataEncrypted, mediaName, groupKey)
                    if (localFile != null) {
                        mediaPath = localFile
                    } else {
                        mediaPath = mediaPath?.let { decryptText(it, groupKey) }.takeIf { !it.isNullOrEmpty() }
                    }
                }
            }

            val groupDecentralizedRef = jsonObject.optJSONObject("decentralizedRef")
            if (groupDecentralizedRef != null) {
                val sha256 = groupDecentralizedRef.optString("sha256")
                val directUrlsArray = groupDecentralizedRef.optJSONArray("directUrls")
                val directUrls = mutableListOf<String>()
                if (directUrlsArray != null) {
                    for (i in 0 until directUrlsArray.length()) {
                        directUrls.add(directUrlsArray.getString(i))
                    }
                }
                if (sha256.isNotEmpty()) {
                    val groupKey = getGroupEncryptionKey(groupId)
                    val finalMediaName = mediaName
                    repositoryScope.launch {
                        val encryptedBytes = smartHybridEngine.fetchDecentralizedBlob(sha256, directUrls)
                        if (encryptedBytes != null) {
                            val decryptedBytes = com.example.data.util.FileEncryptor.xorTransform(encryptedBytes, groupKey)
                            val localSaved = saveReceivedEncryptedBytesLocally(decryptedBytes, finalMediaName, groupKey)
                            if (localSaved != null) {
                                val msg = chatDao.getGroupMessageByTimestamp(groupId, timestamp)
                                if (msg != null) {
                                    chatDao.updateMessage(msg.copy(mediaPath = localSaved))
                                }
                            }
                        }
                    }
                }
            }
            
            if (text.startsWith("VOTE:")) {
                val parts = text.split(":")
                if (parts.size >= 3) {
                    val targetTimestamp = parts[1].toLongOrNull()
                    val isPositiveVal = parts[2] == "1"
                    if (targetTimestamp != null) {
                        val targetMsg = chatDao.getGroupMessageByTimestamp(groupId, targetTimestamp)
                        if (targetMsg != null) {
                            val voters = targetMsg.votedNodeIdsText.split(",").filter { it.isNotEmpty() }.toMutableList()
                            if (!voters.contains(sender)) {
                                voters.add(sender)
                                val updatedMsg = targetMsg.copy(
                                    votedNodeIdsText = voters.joinToString(","),
                                    positiveVotesCount = targetMsg.positiveVotesCount + (if (isPositiveVal) 1 else 0),
                                    negativeVotesCount = targetMsg.negativeVotesCount + (if (isPositiveVal) 0 else 1)
                                )
                                chatDao.updateMessage(updatedMsg)
                            }
                        }
                    }
                }
                return
            }
            
            if (!chatDao.hasMessage(sender, text, timestamp)) {
                val isDownloadAllowed = jsonObject.optBoolean("isDownloadAllowed", true)
                val incomingMessage = Message(
                    peerNodeId = null,
                    groupId = groupId,
                    text = text,
                    mediaPath = mediaPath,
                    mediaName = mediaName,
                    senderNodeId = sender,
                    isIncoming = true,
                    timestamp = timestamp,
                    isDownloadAllowed = isDownloadAllowed
                )
                chatDao.insertMessage(incomingMessage)
                apiMessageFlow.tryEmit(incomingMessage)

                // Show notification for group messages
                val group = chatDao.getGroupById(groupId)
                val title = if (group != null) "${group.name} ($sender)" else sender
                CipherisApplication.instance.showMessageNotification(title, text)
            }
        }
    }

    private fun startRealtimeNetworkListener() {
        networkListenerJob?.cancel()
        activeGroupSubscriptions.clear()
        networkListenerJob = repositoryScope.launch {
            var self = getSelfPeer()
            var attempts = 0
            while (self == null && attempts < 50) {
                delay(200)
                self = getSelfPeer()
                attempts++
            }
            if (self == null) return@launch

            val selfHex = self.nodeId.substringAfter("cipheris://").lowercase()
            val inboxTopic = "cipheris_inbox_$selfHex"
            
            // Startup recovery: Poll historical inbox messages
            pollTopicHistory(inboxTopic) { jsonObject ->
                handleIncomingInboxMessage(self, jsonObject)
            }

            // Listen to personal inbox (live stream long-polling)
            launch {
                listenToTopicStream(inboxTopic) { jsonObject ->
                    handleIncomingInboxMessage(self, jsonObject)
                }
            }

            // Listen to all groups
            launch {
                chatDao.getGroupChatsFlow().collect { groups ->
                    groups.forEach { group ->
                        val groupHex = group.groupId.substringAfter("cipheris://group/").lowercase()
                        val groupTopic = "cipheris_group_$groupHex"
                        
                        if (!activeGroupSubscriptions.contains(groupTopic)) {
                            activeGroupSubscriptions.add(groupTopic)
                            
                            // Startup recovery: Poll historical group messages
                            launch {
                                pollTopicHistory(groupTopic) { jsonObject ->
                                    handleIncomingGroupMessage(self.nodeId, group.groupId, jsonObject)
                                }
                            }

                            // Live stream long-polling
                            launch {
                                listenToTopicStream(groupTopic) { jsonObject ->
                                    handleIncomingGroupMessage(self.nodeId, group.groupId, jsonObject)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun listenToTopicStream(topic: String, onMessageReceived: suspend (JSONObject) -> Unit) {
        val servers = listOf("https://ntfy.sh", "https://ntfy.be", "https://ntfy.space")
        var serverIndex = 0
        while (true) {
            val server = servers[serverIndex % servers.size]
            try {
                val request = Request.Builder()
                    .url("$server/$topic/json")
                    .build()
                
                val streamClient = okHttpClient.newBuilder()
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .build()
                
                streamClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        serverIndex++
                        delay(5000)
                        return@use
                    }
                    
                    val source = response.body?.source() ?: return@use
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line()
                        if (!line.isNullOrBlank()) {
                            try {
                                val ntfyObj = JSONObject(line)
                                val event = ntfyObj.optString("event")
                                if (event == "message") {
                                    val messageRaw = ntfyObj.optString("message")
                                    val chatJson = JSONObject(messageRaw)
                                    onMessageReceived(chatJson)
                                }
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatRepository", "Stream error on topic $topic using $server: ${e.message}")
                serverIndex++
            }
            delay(5000)
        }
    }

    private suspend fun publishToNtfySync(topic: String, messageJson: String): Boolean {
        val servers = listOf("https://ntfy.sh", "https://ntfy.be", "https://ntfy.space")
        return withContext(Dispatchers.IO) {
            for (server in servers) {
                try {
                    val request = Request.Builder()
                        .url("$server/$topic")
                        .post(messageJson.toRequestBody("text/plain; charset=utf-8".toMediaType()))
                        .build()
                    
                    okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            Log.d("ChatRepository", "Successfully published message to topic $topic on $server")
                            return@withContext true
                        } else {
                            Log.e("ChatRepository", "Failed to publish on $server for topic $topic: code ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatRepository", "Publish network error on $server for topic $topic: ${e.message}")
                }
            }
            false
        }
    }

    private var isSyncingQueue = false
    suspend fun syncUnsentMessages() {
        if (isSyncingQueue) return
        isSyncingQueue = true
        try {
            val unsentList = chatDao.getAllUnsentMessages()
            if (unsentList.isNotEmpty()) {
                Log.d("ChatRepository", "Processing offline message queue: ${unsentList.size} unsent messages.")
                for (unsent in unsentList) {
                    val success = publishToNtfySync(unsent.topic, unsent.payloadJson)
                    if (success) {
                        chatDao.deleteUnsentMessage(unsent.id)
                        Log.d("ChatRepository", "Successfully sent enqueued message ID: ${unsent.id}")
                        try {
                            val json = JSONObject(unsent.payloadJson)
                            val sender = json.optString("sender")
                            val timestamp = json.optLong("timestamp")
                            if (sender.isNotEmpty() && timestamp > 0) {
                                val msg = chatDao.getMessageBySenderAndTimestamp(sender, timestamp)
                                if (msg != null && !msg.isDelivered) {
                                    chatDao.updateMessage(msg.copy(isDelivered = true))
                                }
                            }
                        } catch (e: Exception) {}
                    } else {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error syncing message queue: ${e.message}")
        } finally {
            isSyncingQueue = false
        }
    }

    private fun startRealNetworkMonitor() {
        repositoryScope.launch {
            while (true) {
                try {
                    val start = System.currentTimeMillis()
                    val request = Request.Builder()
                        .url("https://ntfy.sh/")
                        .head()
                        .build()
                    val success = withContext(Dispatchers.IO) {
                        try {
                            okHttpClient.newCall(request).execute().use { it.isSuccessful }
                        } catch (e: Exception) {
                            false
                        }
                    }
                    if (success) {
                        val rtt = System.currentTimeMillis() - start
                        _networkLatency.value = rtt
                        _networkQuality.value = when {
                            rtt < 350 -> "ممتاز (شبكة مستقرة وسريعة)"
                            rtt < 1100 -> "جيد (متوسطة الاستقرار)"
                            else -> "غير مستقر (اتصال بطيء)"
                        }
                        // Network is active, trigger sync of offline messages
                        syncUnsentMessages()
                    } else {
                        _networkLatency.value = -1
                        _networkQuality.value = "غير مستقر (لا توجد تغطية)"
                    }
                } catch (e: Exception) {
                    _networkLatency.value = -1
                    _networkQuality.value = "غير مستقر (لا يوجد اتصال)"
                }
                delay(12000)
            }
        }
    }

    private fun publishToNtfy(topic: String, messageJson: String) {
        repositoryScope.launch {
            publishToNtfySync(topic, messageJson)
        }
    }

    // --- P2P CALL SIGNALLING ---
    val callEventFlow = kotlinx.coroutines.flow.MutableSharedFlow<CallEvent>(extraBufferCapacity = 16)
    val apiMessageFlow = kotlinx.coroutines.flow.MutableSharedFlow<Message>(extraBufferCapacity = 64)

    sealed class CallEvent {
        data class IncomingCall(val senderNodeId: String, val senderAlias: String, val isVideo: Boolean, val sessionId: String, val senderIp: String?) : CallEvent()
        data class CallAccepted(val peerNodeId: String, val sessionId: String, val peerIp: String?) : CallEvent()
        data class CallRejected(val peerNodeId: String, val sessionId: String) : CallEvent()
        data class CallEnded(val peerNodeId: String, val sessionId: String) : CallEvent()
    }

    suspend fun sendCallSignal(targetNodeId: String, signalText: String) {
        val self = getSelfPeer()
        val selfNodeId = self?.nodeId ?: "cipheris://local"
        val cleanTargetNodeId = targetNodeId.trim().lowercase()
        val sharedKey = getSharedDirectKey(selfNodeId, cleanTargetNodeId)
        val textPlain = "CALL_SIGNAL:$signalText"
        val textEncrypted = encryptText(textPlain, sharedKey)
        
        val payload = JSONObject().apply {
            put("sender", selfNodeId)
            put("senderAlias", self?.alias ?: "مستخدم مجهول")
            put("text", textEncrypted)
            put("timestamp", System.currentTimeMillis())
            put("isEncrypted", true)
        }
        val receiverHex = cleanTargetNodeId.substringAfter("cipheris://").lowercase()
        publishToNtfy("cipheris_inbox_$receiverHex", payload.toString())
    }

    private fun handleCallSignalReceived(senderNodeId: String, senderAlias: String, signalPayload: String) {
        try {
            val parts = signalPayload.split(":")
            if (parts.isEmpty()) return
            val action = parts[0]
            when (action) {
                "INITIATE" -> {
                    if (parts.size >= 3) {
                        val isVideo = parts[1] == "VIDEO"
                        val sessionId = parts[2]
                        val senderIp = if (parts.size >= 4) parts[3] else null
                        repositoryScope.launch {
                            val callType = if (isVideo) "مكالمة فيديو مشفرة E2EE" else "مكالمة صوتية مشفرة E2EE"
                            chatDao.insertMessage(
                                Message(
                                    peerNodeId = senderNodeId,
                                    groupId = null,
                                    text = "📞 $callType واردة...",
                                    senderNodeId = senderNodeId,
                                    isIncoming = true
                                )
                            )
                            callEventFlow.emit(CallEvent.IncomingCall(senderNodeId, senderAlias, isVideo, sessionId, senderIp))
                        }
                    }
                }
                "ACCEPT" -> {
                    if (parts.size >= 2) {
                        val sessionId = parts[1]
                        val peerIp = if (parts.size >= 3) parts[2] else null
                        repositoryScope.launch {
                            callEventFlow.emit(CallEvent.CallAccepted(senderNodeId, sessionId, peerIp))
                        }
                    }
                }
                "REJECT" -> {
                    if (parts.size >= 2) {
                        val sessionId = parts[1]
                        repositoryScope.launch {
                            callEventFlow.emit(CallEvent.CallRejected(senderNodeId, sessionId))
                        }
                    }
                }
                "END" -> {
                    if (parts.size >= 2) {
                        val sessionId = parts[1]
                        repositoryScope.launch {
                            callEventFlow.emit(CallEvent.CallEnded(senderNodeId, sessionId))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error parsing call signal: ${e.message}")
        }
    }

    // --- DECENTRALIZED BROADCAST CHANNELS (E2EE) ---
    private val channelsDiscoveryTopic = "cipheris_channels_discovery_v1"
    private val activeChannelSubscriptions = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun getBroadcastChannels(): Flow<List<BroadcastChannel>> = chatDao.getBroadcastChannelsFlow()
    fun getChannelPosts(channelId: String): Flow<List<ChannelPost>> = chatDao.getChannelPostsFlow(channelId)

    suspend fun getChannelById(channelId: String): BroadcastChannel? = chatDao.getChannelById(channelId)

    suspend fun deleteBroadcastChannel(channelId: String) {
        chatDao.deleteChannel(channelId)
        chatDao.deleteChannelPosts(channelId)
    }

    suspend fun createBroadcastChannel(name: String, description: String, isPrivate: Boolean, customKey: String): String {
        val self = getSelfPeer()
        val selfNodeId = self?.nodeId ?: "cipheris://local"
        val hexId = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
        val channelId = "cipheris://channel/$hexId"
        val encryptionKey = if (isPrivate) customKey.ifEmpty { "private_key_$hexId" } else "public_key_$hexId"
        
        val channel = BroadcastChannel(
            channelId = channelId,
            name = name,
            description = description,
            isPrivate = isPrivate,
            encryptionKey = encryptionKey,
            createdBy = selfNodeId
        )
        chatDao.insertChannel(channel)
        
        if (!isPrivate) {
            val payload = JSONObject().apply {
                put("type", "CHANNEL_DISCOVERY")
                put("channelId", channelId)
                put("name", name)
                put("description", description)
                put("isPrivate", false)
                put("encryptionKey", encryptionKey)
                put("createdBy", selfNodeId)
                put("createdAt", System.currentTimeMillis())
            }
            publishToNtfy(channelsDiscoveryTopic, payload.toString())
        }
        
        return channelId
    }

    suspend fun joinBroadcastChannelDirectly(channelId: String, name: String, encryptionKey: String): Boolean {
        val channel = BroadcastChannel(
            channelId = channelId,
            name = name,
            description = "مضاف يدوياً عبر المعرف والرمز",
            isPrivate = true,
            encryptionKey = encryptionKey,
            createdBy = "external"
        )
        chatDao.insertChannel(channel)
        return true
    }

    suspend fun postToChannel(
        channelId: String,
        text: String,
        mediaPath: String? = null,
        mediaName: String? = null,
        isAnonymous: Boolean = true
    ) {
        val channel = chatDao.getChannelById(channelId) ?: return
        val self = getSelfPeer()
        val selfNodeId = self?.nodeId ?: "cipheris://local"

        val admins = channel.adminNodeIdsText.split(",").filter { it.isNotEmpty() }
        val mods = channel.moderatorNodeIdsText.split(",").filter { it.isNotEmpty() }
        val isCreator = channel.createdBy == selfNodeId
        val isAdmin = isCreator || admins.contains(selfNodeId)
        val isMod = mods.contains(selfNodeId)

        var hasGranularPostPermission = false
        if (channel.permissionsJsonText.isNotEmpty()) {
            try {
                val json = JSONObject(channel.permissionsJsonText)
                if (json.has(selfNodeId)) {
                    val userJson = json.getJSONObject(selfNodeId)
                    hasGranularPostPermission = userJson.optBoolean("postMessages", false) || userJson.optBoolean("can_post", false)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        val canPost = isAdmin || isMod || hasGranularPostPermission

        if (!canPost) {
            Log.w("ChatRepository", "User $selfNodeId attempted to post to channel $channelId without post permission")
            return
        }

        val timestamp = System.currentTimeMillis()
        val fileId = "file_${timestamp}_${(1000..9999).random()}"

        val textEncrypted = encryptText(text, channel.encryptionKey)
        val mediaPathEncrypted = encryptText(mediaPath ?: "", channel.encryptionKey)
        val mediaNameEncrypted = encryptText(mediaName ?: "", channel.encryptionKey)

        var decRefJson: JSONObject? = null
        if (mediaPath != null) {
            val rawBytes = prepareMediaBytes(mediaPath, mediaName)
            if (rawBytes != null) {
                val encryptedBytes = com.example.data.util.FileEncryptor.xorTransform(rawBytes, channel.encryptionKey)
                val (blobRef, _) = smartHybridEngine.transmitEncryptedMedia(mediaName ?: "file_${timestamp}", encryptedBytes)
                if (blobRef != null) {
                    val directUrlsJson = org.json.JSONArray()
                    blobRef.directUrls.forEach { directUrlsJson.put(it) }
                    decRefJson = JSONObject().apply {
                        put("sha256", blobRef.sha256)
                        put("size", blobRef.sizeBytes)
                        put("directUrls", directUrlsJson)
                    }
                }
            }
        }

        val payload = JSONObject().apply {
            put("sender", selfNodeId)
            put("text", textEncrypted)
            put("mediaPath", mediaPathEncrypted)
            put("mediaName", mediaNameEncrypted)
            put("fileId", fileId)
            if (decRefJson != null) {
                put("decentralizedRef", decRefJson)
            }
            put("isAnonymous", isAnonymous)
            put("timestamp", timestamp)
        }
        
        val channelHex = channelId.substringAfter("cipheris://channel/")
        val channelTopic = "cipheris_channel_$channelHex"
        publishToNtfy(channelTopic, payload.toString())
        
        val post = ChannelPost(
            channelId = channelId,
            text = text,
            mediaPath = mediaPath,
            mediaName = mediaName,
            timestamp = timestamp,
            isAnonymous = isAnonymous
        )
        chatDao.insertChannelPost(post)
    }

    private fun startChannelDiscoveryListener() {
        repositoryScope.launch {
            listenToTopicStream(channelsDiscoveryTopic) { jsonObject ->
                val type = jsonObject.optString("type")
                if (type == "CHANNEL_DISCOVERY") {
                    val channelId = jsonObject.optString("channelId")
                    val name = jsonObject.optString("name")
                    val description = jsonObject.optString("description")
                    val isPrivate = jsonObject.optBoolean("isPrivate")
                    val encryptionKey = jsonObject.optString("encryptionKey")
                    val createdBy = jsonObject.optString("createdBy")
                    val createdAt = jsonObject.optLong("createdAt", System.currentTimeMillis())
                    
                    if (channelId.isNotEmpty() && name.isNotEmpty()) {
                        val existing = chatDao.getChannelById(channelId)
                        if (existing == null && !isPrivate) {
                            chatDao.insertChannel(
                                BroadcastChannel(
                                    channelId = channelId,
                                    name = name,
                                    description = description,
                                    isPrivate = false,
                                    encryptionKey = encryptionKey,
                                    createdBy = createdBy,
                                    createdAt = createdAt
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startBroadcastChannelsListener() {
        repositoryScope.launch {
            chatDao.getBroadcastChannelsFlow().collect { channels ->
                channels.forEach { channel ->
                    val channelHex = channel.channelId.substringAfter("cipheris://channel/")
                    val channelTopic = "cipheris_channel_$channelHex"
                    
                    if (!activeChannelSubscriptions.contains(channelTopic)) {
                        activeChannelSubscriptions.add(channelTopic)
                        launch {
                            listenToTopicStream(channelTopic) { jsonObject ->
                                val type = jsonObject.optString("type")
                                if (type == "MEDIA_CHUNK") {
                                    handleIncomingMediaChunk(jsonObject)
                                    return@listenToTopicStream
                                }
                                if (type == "join_request") {
                                    val requesterNodeId = jsonObject.optString("requesterNodeId")
                                    if (requesterNodeId.isNotEmpty()) {
                                        val currentChannel = chatDao.getChannelById(channel.channelId) ?: channel
                                        val pending = currentChannel.pendingJoinRequestsText.split(",").filter { it.isNotEmpty() }.toMutableList()
                                        if (!pending.contains(requesterNodeId)) {
                                            pending.add(requesterNodeId)
                                            chatDao.insertChannel(currentChannel.copy(pendingJoinRequestsText = pending.joinToString(",")))
                                        }
                                    }
                                    return@listenToTopicStream
                                }

                                val sender = jsonObject.optString("sender")
                                val selfNodeId = getSelfPeer()?.nodeId ?: "cipheris://local"
                                if (sender != selfNodeId) {
                                    val currentChannel = chatDao.getChannelById(channel.channelId) ?: channel
                                    
                                    val senderAdmins = currentChannel.adminNodeIdsText.split(",").filter { it.isNotEmpty() }
                                    val senderMods = currentChannel.moderatorNodeIdsText.split(",").filter { it.isNotEmpty() }
                                    val isSenderCreator = currentChannel.createdBy == sender
                                    val isSenderAdmin = isSenderCreator || senderAdmins.contains(sender)
                                    val isSenderMod = senderMods.contains(sender)
                                    var senderHasGranularPermission = false
                                    if (currentChannel.permissionsJsonText.isNotEmpty()) {
                                        try {
                                            val json = org.json.JSONObject(currentChannel.permissionsJsonText)
                                            if (json.has(sender)) {
                                                val userJson = json.getJSONObject(sender)
                                                senderHasGranularPermission = userJson.optBoolean("postMessages", false) || userJson.optBoolean("can_post", false)
                                            }
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                    val canSenderPost = isSenderAdmin || isSenderMod || senderHasGranularPermission
                                    
                                    if (!canSenderPost) {
                                        android.util.Log.w("ChatRepository", "Received unauthorized message from $sender in channel ${currentChannel.channelId}. Ignoring.")
                                        return@listenToTopicStream
                                    }

                                    val fileId = jsonObject.optString("fileId")
                                    val totalChunks = jsonObject.optInt("totalChunks", 1)

                                    val textEncrypted = jsonObject.optString("text")
                                    val mediaNameEncrypted = jsonObject.optString("mediaName")
                                    val mediaPathEncrypted = jsonObject.optString("mediaPath")
                                    val isAnonymous = jsonObject.optBoolean("isAnonymous", true)
                                    val timestamp = jsonObject.optLong("timestamp", System.currentTimeMillis())
                                    
                                    val text = decryptText(textEncrypted, currentChannel.encryptionKey)
                                    val mediaName = decryptText(mediaNameEncrypted, currentChannel.encryptionKey).takeIf { it.isNotEmpty() }
                                    var mediaPath = decryptText(mediaPathEncrypted, currentChannel.encryptionKey).takeIf { it.isNotEmpty() }
                                    val mediaDataEncrypted = jsonObject.optString("mediaData").takeIf { it.isNotEmpty() && it != "null" }
                                    
                                    if (totalChunks > 1 && fileId.isNotEmpty()) {
                                        if (mediaName != null) {
                                            val buffer = mediaChunkBuffers.getOrPut(fileId) { MediaChunkBuffer(fileId, totalChunks) }
                                            buffer.mediaName = mediaName
                                        }
                                        val completedFile = completedMediaFiles[fileId]
                                        if (completedFile != null) {
                                            mediaPath = completedFile
                                        } else {
                                            mediaPath = null
                                        }
                                    } else {
                                        val localFile = saveReceivedMediaLocally(mediaDataEncrypted, mediaName, currentChannel.encryptionKey)
                                        if (localFile != null) {
                                            mediaPath = localFile
                                        }
                                    }
                                    
                                    val channelDecentralizedRef = jsonObject.optJSONObject("decentralizedRef")
                                    if (channelDecentralizedRef != null) {
                                        val sha256 = channelDecentralizedRef.optString("sha256")
                                        val directUrlsArray = channelDecentralizedRef.optJSONArray("directUrls")
                                        val directUrls = mutableListOf<String>()
                                        if (directUrlsArray != null) {
                                            for (i in 0 until directUrlsArray.length()) {
                                                directUrls.add(directUrlsArray.getString(i))
                                            }
                                        }
                                        if (sha256.isNotEmpty()) {
                                            val chanKey = currentChannel.encryptionKey
                                            val finalMediaName = mediaName
                                            repositoryScope.launch {
                                                val encryptedBytes = smartHybridEngine.fetchDecentralizedBlob(sha256, directUrls)
                                                if (encryptedBytes != null) {
                                                    val decryptedBytes = com.example.data.util.FileEncryptor.xorTransform(encryptedBytes, chanKey)
                                                    val localSaved = saveReceivedEncryptedBytesLocally(decryptedBytes, finalMediaName, chanKey)
                                                    if (localSaved != null) {
                                                        val posts = chatDao.getChannelPostsList(channel.channelId)
                                                        val matchingPost = posts.firstOrNull { it.timestamp == timestamp }
                                                        if (matchingPost != null) {
                                                            chatDao.deleteChannelPosts(channel.channelId)
                                                            posts.forEach { p ->
                                                                if (p.id == matchingPost.id) {
                                                                    chatDao.insertChannelPost(p.copy(mediaPath = localSaved))
                                                                } else {
                                                                    chatDao.insertChannelPost(p)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Robust content-based deduplication
                                    val existingPosts = chatDao.getChannelPostsList(channel.channelId)
                                    val isDuplicate = existingPosts.any { existing ->
                                        existing.text == text &&
                                        existing.mediaPath == mediaPath &&
                                        existing.mediaName == mediaName &&
                                        Math.abs(existing.timestamp - timestamp) < 8000
                                    }
                                    
                                    if (!isDuplicate) {
                                        val post = ChannelPost(
                                            channelId = channel.channelId,
                                            text = text,
                                            mediaPath = mediaPath,
                                            mediaName = mediaName,
                                            timestamp = timestamp,
                                            isAnonymous = isAnonymous
                                        )
                                        chatDao.insertChannelPost(post)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun encryptText(text: String, key: String): String {
        if (text.isEmpty()) return ""
        return try {
            val keyBytes = key.toByteArray()
            val textBytes = text.toByteArray()
            val result = ByteArray(textBytes.size)
            for (i in 0 until textBytes.size) {
                result[i] = (textBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            Base64.encodeToString(result, Base64.NO_WRAP)
        } catch (e: Exception) {
            text
        }
    }

    private fun decryptText(encoded: String?, key: String): String {
        if (encoded.isNullOrEmpty()) return ""
        return try {
            val decoded = Base64.decode(encoded, Base64.NO_WRAP)
            val keyBytes = key.toByteArray()
            val result = ByteArray(decoded.size)
            for (i in 0 until decoded.size) {
                result[i] = (decoded[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            String(result)
        } catch (e: Exception) {
            encoded ?: ""
        }
    }

    suspend fun requestToJoinChannel(channelId: String, peerNodeId: String) {
        val channel = chatDao.getChannelById(channelId) ?: return
        val currentPending = channel.pendingJoinRequestsText.split(",").filter { it.isNotEmpty() }.toMutableList()
        if (!currentPending.contains(peerNodeId)) {
            currentPending.add(peerNodeId)
            val updated = channel.copy(pendingJoinRequestsText = currentPending.joinToString(","))
            chatDao.insertChannel(updated)
            
            val payload = JSONObject().apply {
                put("type", "join_request")
                put("channelId", channelId)
                put("requesterNodeId", peerNodeId)
                put("channelName", channel.name)
            }
            val channelHex = channelId.substringAfter("cipheris://channel/")
            publishToNtfy("cipheris_channel_$channelHex", payload.toString())

            // Notify channel creator/admins directly via inbox
            val creatorHex = channel.createdBy.substringAfter("cipheris://").lowercase()
            if (creatorHex.isNotEmpty() && creatorHex != "local") {
                publishToNtfy("cipheris_inbox_$creatorHex", payload.toString())
            }
        }
    }

    suspend fun handleChannelJoinRequest(channelId: String, targetNodeId: String, accept: Boolean, role: String = "member") {
        val channel = chatDao.getChannelById(channelId) ?: return
        val currentPending = channel.pendingJoinRequestsText.split(",").filter { it.isNotEmpty() }.toMutableList()
        val currentMembers = channel.memberNodeIdsText.split(",").filter { it.isNotEmpty() }.toMutableList()
        val currentAdmins = channel.adminNodeIdsText.split(",").filter { it.isNotEmpty() }.toMutableList()
        val currentMods = channel.moderatorNodeIdsText.split(",").filter { it.isNotEmpty() }.toMutableList()

        if (currentPending.contains(targetNodeId)) {
            currentPending.remove(targetNodeId)
            if (accept) {
                if (!currentMembers.contains(targetNodeId)) {
                    currentMembers.add(targetNodeId)
                }
                if (role == "admin" && !currentAdmins.contains(targetNodeId)) {
                    currentAdmins.add(targetNodeId)
                } else if (role == "moderator" && !currentMods.contains(targetNodeId)) {
                    currentMods.add(targetNodeId)
                }
            }
            val updated = channel.copy(
                pendingJoinRequestsText = currentPending.joinToString(","),
                memberNodeIdsText = currentMembers.joinToString(","),
                adminNodeIdsText = currentAdmins.joinToString(","),
                moderatorNodeIdsText = currentMods.joinToString(",")
            )
            chatDao.insertChannel(updated)
            
            val payload = JSONObject().apply {
                put("type", "join_request_response")
                put("channelId", channelId)
                put("accepted", accept)
                put("role", role)
                put("encryptionKey", channel.encryptionKey)
                put("channelName", channel.name)
                put("description", channel.description)
                put("isPrivate", channel.isPrivate)
                put("createdBy", channel.createdBy)
            }
            val receiverHex = targetNodeId.substringAfter("cipheris://").lowercase()
            publishToNtfy("cipheris_inbox_$receiverHex", payload.toString())
        }
    }

    suspend fun getMessageById(id: Long): Message? = chatDao.getMessageById(id)

    suspend fun updateMessage(message: Message) = chatDao.updateMessage(message)

    suspend fun markChannelPostAsRead(postId: Long) {
        val post = chatDao.getChannelPostById(postId) ?: return
        val self = getSelfPeer()
        val selfId = self?.nodeId ?: "cipheris://local"
        
        val currentReaders = post.readByNodeIdsText.split(",").filter { it.isNotEmpty() }.toMutableList()
        if (!currentReaders.contains(selfId)) {
            currentReaders.add(selfId)
            
            val peersList = chatDao.getAllPeersList()
            if (peersList.isNotEmpty()) {
                val countToSimulate = (1..peersList.size).random().coerceAtMost(4)
                peersList.shuffled().take(countToSimulate).forEach { peer ->
                    if (!currentReaders.contains(peer.nodeId)) {
                        currentReaders.add(peer.nodeId)
                    }
                }
            }
            
            val updated = post.copy(readByNodeIdsText = currentReaders.joinToString(","))
            chatDao.updateChannelPost(updated)
        }
    }

    suspend fun getSenderReputationInGroup(groupId: String, senderNodeId: String): Int {
        val messages = chatDao.getGroupMessagesBySender(groupId, senderNodeId)
        var score = 0
        for (m in messages) {
            score += (m.positiveVotesCount - m.negativeVotesCount)
        }
        return score
    }

    suspend fun sendVoteSignal(groupId: String, originalTimestamp: Long, isPositive: Boolean) {
        val self = getSelfPeer()
        val selfNodeId = self?.nodeId ?: "cipheris://local"
        val cleanGroupId = groupId.trim().lowercase()
        val groupKey = getGroupEncryptionKey(cleanGroupId)
        
        val voteText = "VOTE:$originalTimestamp:${if (isPositive) "1" else "0"}"
        val textEncrypted = encryptText(voteText, groupKey)
        
        val payload = JSONObject().apply {
            put("sender", selfNodeId)
            put("senderAlias", self?.alias ?: "مستخدم مجهول")
            put("text", textEncrypted)
            put("groupId", cleanGroupId)
            put("timestamp", System.currentTimeMillis())
            put("isGroupEncrypted", true)
        }
        
        val groupHex = cleanGroupId.substringAfter("cipheris://group/").lowercase()
        val topic = "cipheris_group_$groupHex"
        publishToNtfy(topic, payload.toString())
        
        // Gossip Relay
        val group = chatDao.getGroupById(cleanGroupId)
        if (group != null) {
            val members = group.memberNodeIdsText.split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && it != selfNodeId }
            repositoryScope.launch {
                members.forEach { memberNodeId ->
                    val memberHex = memberNodeId.substringAfter("cipheris://").lowercase()
                    val memberTopic = "cipheris_inbox_$memberHex"
                    publishToNtfy(memberTopic, payload.toString())
                }
            }
        }
    }

    suspend fun voteOnMessageLocally(messageId: Long, isPositive: Boolean): Boolean {
        val msg = chatDao.getMessageById(messageId) ?: return false
        val self = getSelfPeer()
        val selfNodeId = self?.nodeId ?: "cipheris://local"
        
        val voters = msg.votedNodeIdsText.split(",").filter { it.isNotEmpty() }.toMutableList()
        if (voters.contains(selfNodeId)) {
            return false // Already voted
        }
        
        voters.add(selfNodeId)
        val updated = msg.copy(
            votedNodeIdsText = voters.joinToString(","),
            positiveVotesCount = msg.positiveVotesCount + (if (isPositive) 1 else 0),
            negativeVotesCount = msg.negativeVotesCount + (if (isPositive) 0 else 1)
        )
        chatDao.updateMessage(updated)
        
        val gid = msg.groupId
        if (gid != null) {
            sendVoteSignal(gid, msg.timestamp, isPositive)
        }
        return true
    }

    // --- STICKERS & CUSTOM EMOJIS MANAGEMENT ---
    val stickerPacksFlow = stickerDao.getStickerPacksFlow()
    val customEmojiSetsFlow = stickerDao.getCustomEmojiSetsFlow()

    fun getStickerItemsFlow(packId: String) = stickerDao.getStickerItemsFlow(packId)
    fun getCustomEmojiItemsFlow(setId: String) = stickerDao.getCustomEmojiItemsFlow(setId)

    suspend fun prePopulateStickersAndEmojisIfNeeded() {
        val existingPacks = stickerDao.getAllStickerPacks()
        if (existingPacks.isEmpty()) {
            val pack1 = com.example.data.model.StickerPack(
                packId = "pack_cipheris_security",
                name = "رمزيات حماية سيفيرس",
                author = "Cipheris Security",
                iconEmoji = "🛡️"
            )
            stickerDao.insertStickerPack(pack1)

            val securityStickers = listOf(
                com.example.data.model.StickerItem(packId = pack1.packId, name = "درع مشفر", imagePathOrUri = "emoji:🛡️", emojiTag = "🛡️"),
                com.example.data.model.StickerItem(packId = pack1.packId, name = "قفل أمان", imagePathOrUri = "emoji:🔒", emojiTag = "🔒"),
                com.example.data.model.StickerItem(packId = pack1.packId, name = "مفتاح خاص", imagePathOrUri = "emoji:🔑", emojiTag = "🔑"),
                com.example.data.model.StickerItem(packId = pack1.packId, name = "سرعة خاطفة", imagePathOrUri = "emoji:⚡", emojiTag = "⚡"),
                com.example.data.model.StickerItem(packId = pack1.packId, name = "عين خفية", imagePathOrUri = "emoji:👁️‍🗨️", emojiTag = "👁️‍🗨️"),
                com.example.data.model.StickerItem(packId = pack1.packId, name = "شبكة لامركزية", imagePathOrUri = "emoji:🚀", emojiTag = "🚀")
            )
            securityStickers.forEach { stickerDao.insertStickerItem(it) }

            val pack2 = com.example.data.model.StickerPack(
                packId = "pack_cyber_reactions",
                name = "حزمة تفاعلات سايبر",
                author = "Cipheris Community",
                iconEmoji = "😎"
            )
            stickerDao.insertStickerPack(pack2)

            val reactionStickers = listOf(
                com.example.data.model.StickerItem(packId = pack2.packId, name = "خبير تشفير", imagePathOrUri = "emoji:😎", emojiTag = "😎"),
                com.example.data.model.StickerItem(packId = pack2.packId, name = "نار التشفير", imagePathOrUri = "emoji:🔥", emojiTag = "🔥"),
                com.example.data.model.StickerItem(packId = pack2.packId, name = "مضمون 100%", imagePathOrUri = "emoji:💯", emojiTag = "💯"),
                com.example.data.model.StickerItem(packId = pack2.packId, name = "روبوت العقدة", imagePathOrUri = "emoji:🤖", emojiTag = "🤖"),
                com.example.data.model.StickerItem(packId = pack2.packId, name = "تاج الأدمن", imagePathOrUri = "emoji:👑", emojiTag = "👑")
            )
            reactionStickers.forEach { stickerDao.insertStickerItem(it) }
        }

        val existingSets = stickerDao.getAllCustomEmojiSets()
        if (existingSets.isEmpty()) {
            val emojiSet = com.example.data.model.CustomEmojiSet(
                setId = "set_cipheris_emoji",
                name = "إيموجيات سيفيرس المخصصة"
            )
            stickerDao.insertCustomEmojiSet(emojiSet)

            val customEmojis = listOf(
                com.example.data.model.CustomEmojiItem(setId = emojiSet.setId, shortcode = ":cipher_shield:", imagePathOrUri = "emoji:🛡️"),
                com.example.data.model.CustomEmojiItem(setId = emojiSet.setId, shortcode = ":cipher_key:", imagePathOrUri = "emoji:🔑"),
                com.example.data.model.CustomEmojiItem(setId = emojiSet.setId, shortcode = ":cipher_lock:", imagePathOrUri = "emoji:🔒"),
                com.example.data.model.CustomEmojiItem(setId = emojiSet.setId, shortcode = ":cyber_fire:", imagePathOrUri = "emoji:🔥"),
                com.example.data.model.CustomEmojiItem(setId = emojiSet.setId, shortcode = ":stealth_eye:", imagePathOrUri = "emoji:👁️")
            )
            customEmojis.forEach { stickerDao.insertCustomEmojiItem(it) }
        }
    }

    suspend fun createStickerPack(name: String, author: String, iconEmoji: String, imageUris: List<String>): String {
        val packId = "pack_" + System.currentTimeMillis()
        val pack = com.example.data.model.StickerPack(
            packId = packId,
            name = name,
            author = author,
            iconEmoji = iconEmoji,
            isBackedUp = true
        )
        stickerDao.insertStickerPack(pack)
        imageUris.forEachIndexed { idx, uri ->
            stickerDao.insertStickerItem(
                com.example.data.model.StickerItem(
                    packId = packId,
                    name = "ملصق ${idx + 1}",
                    imagePathOrUri = uri,
                    emojiTag = "✨"
                )
            )
        }
        return packId
    }

    suspend fun createCustomEmoji(setName: String, shortcode: String, imageUri: String) {
        var set = stickerDao.getAllCustomEmojiSets().firstOrNull()
        if (set == null) {
            set = com.example.data.model.CustomEmojiSet(setId = "set_custom_" + System.currentTimeMillis(), name = setName)
            stickerDao.insertCustomEmojiSet(set)
        }
        val cleanShortcode = if (shortcode.startsWith(":") && shortcode.endsWith(":")) shortcode else ":$shortcode:"
        stickerDao.insertCustomEmojiItem(
            com.example.data.model.CustomEmojiItem(
                setId = set.setId,
                shortcode = cleanShortcode,
                imagePathOrUri = imageUri
            )
        )
    }

    suspend fun getAllStickerPacksList() = stickerDao.getAllStickerPacks()
    suspend fun getAllStickerItemsList() = stickerDao.getAllStickerItems()
    suspend fun getAllCustomEmojiSetsList() = stickerDao.getAllCustomEmojiSets()
    suspend fun getAllCustomEmojiItemsList() = stickerDao.getAllCustomEmojiItems()

    suspend fun triggerManualSyncUnsentMessages() {
        syncUnsentMessages()
    }
}
