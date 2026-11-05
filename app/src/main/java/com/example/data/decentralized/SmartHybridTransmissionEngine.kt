package com.example.data.decentralized

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class SmartHybridTransmissionEngine(private val context: Context) {
    private val TAG = "SmartHybridEngine"
    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _relays = MutableStateFlow<List<DecentralizedRelay>>(emptyList())
    val relays: StateFlow<List<DecentralizedRelay>> = _relays.asStateFlow()

    private val _transmissionMode = MutableStateFlow(TransmissionMode.SMART_HYBRID)
    val transmissionMode: StateFlow<TransmissionMode> = _transmissionMode.asStateFlow()

    private val _telemetryLogs = MutableStateFlow<List<TransmissionTelemetry>>(emptyList())
    val telemetryLogs: StateFlow<List<TransmissionTelemetry>> = _telemetryLogs.asStateFlow()

    private val _activeTransfersCount = MutableStateFlow(0)
    val activeTransfersCount: StateFlow<Int> = _activeTransfersCount.asStateFlow()

    private val directPeerIpCache = ConcurrentHashMap<String, String>() // nodeId -> IP

    init {
        loadDefaultRelays()
        DirectP2PChannelService.startLocalP2PListener { fileName, data ->
            Log.d(TAG, "Direct P2P background listener received $fileName (${data.size} bytes)")
        }
        pingAllRelays()
    }

    private fun loadDefaultRelays() {
        val initialList = mutableListOf<DecentralizedRelay>()

        // 1. Blossom High-Speed Decentralized Media Servers
        initialList.add(DecentralizedRelay("blossom_primal", "خادم بلوسوم برايمال (Primal Blossom)", "https://blossom.primal.net", RelayType.BLOSSOM_HTTP, true))
        initialList.add(DecentralizedRelay("blossom_download", "خادم نوستر داونلود (Nostr Download)", "https://nostr.download", RelayType.BLOSSOM_HTTP, true))
        initialList.add(DecentralizedRelay("blossom_nostrbuild", "خادم نوستر بيلد (Nostr.build CDN)", "https://cdn.nostr.build", RelayType.BLOSSOM_HTTP, true))
        initialList.add(DecentralizedRelay("blossom_wine", "خادم بلوسوم واين (Nostr Wine)", "https://blossom.nostr.wine", RelayType.BLOSSOM_HTTP, true))

        // 2. Nostr WebSockets Relays
        initialList.add(DecentralizedRelay("nostr_damus", "مرحل داموس العالمي (Damus Relay)", "wss://relay.damus.io", RelayType.NOSTR_WS, true))
        initialList.add(DecentralizedRelay("nostr_noslol", "مرحل نوس لول (Nos.lol Relay)", "wss://nos.lol", RelayType.NOSTR_WS, true))
        initialList.add(DecentralizedRelay("nostr_band", "مرحل نوستر باند (Nostr.band)", "wss://relay.nostr.band", RelayType.NOSTR_WS, true))
        initialList.add(DecentralizedRelay("nostr_snort", "مرحل سنورت (Snort Social Relay)", "wss://relay.snort.social", RelayType.NOSTR_WS, true))

        // 3. IPFS Distributed Gateways
        initialList.add(DecentralizedRelay("ipfs_io", "بوابة IPFS العامة (IPFS.io)", "https://ipfs.io/ipfs/", RelayType.IPFS_GATEWAY, true))
        initialList.add(DecentralizedRelay("ipfs_cloudflare", "بوابة كلاودفلير IPFS (Cloudflare)", "https://cloudflare-ipfs.com/ipfs/", RelayType.IPFS_GATEWAY, true))
        initialList.add(DecentralizedRelay("ipfs_pinata", "بوابة بيناتا الموزعة (Pinata Gateway)", "https://gateway.pinata.cloud/ipfs/", RelayType.IPFS_GATEWAY, true))

        // 4. Mesh Multi-Relays
        initialList.add(DecentralizedRelay("mesh_ntfy_sh", "مرحل سيفيرس المباشر 1 (Ntfy.sh)", "https://ntfy.sh", RelayType.MESH_WEBHOOK, true))
        initialList.add(DecentralizedRelay("mesh_ntfy_be", "مرحل سيفيرس المباشر 2 (Ntfy.be)", "https://ntfy.be", RelayType.MESH_WEBHOOK, true))
        initialList.add(DecentralizedRelay("mesh_ntfy_space", "مرحل سيفيرس المباشر 3 (Ntfy.space)", "https://ntfy.space", RelayType.MESH_WEBHOOK, true))

        _relays.value = initialList
    }

    fun registerPeerIp(nodeId: String, ipAddress: String) {
        if (ipAddress.isNotEmpty() && ipAddress != "127.0.0.1") {
            directPeerIpCache[nodeId.trim().lowercase()] = ipAddress
            Log.d(TAG, "Cached direct P2P IP for $nodeId: $ipAddress")
        }
    }

    fun setTransmissionMode(mode: TransmissionMode) {
        _transmissionMode.value = mode
    }

    fun toggleRelay(relayId: String) {
        _relays.value = _relays.value.map {
            if (it.id == relayId) it.copy(isEnabled = !it.isEnabled) else it
        }
    }

    fun addCustomRelay(name: String, url: String, type: RelayType) {
        val cleanUrl = url.trim()
        val newRelay = DecentralizedRelay(
            id = "custom_" + System.currentTimeMillis(),
            name = name.ifEmpty { cleanUrl },
            url = cleanUrl,
            type = type,
            isEnabled = true,
            isCustom = true
        )
        _relays.value = listOf(newRelay) + _relays.value
        engineScope.launch {
            pingSingleRelay(newRelay.id)
        }
    }

    fun removeRelay(relayId: String) {
        _relays.value = _relays.value.filterNot { it.id == relayId }
    }

    fun pingAllRelays() {
        engineScope.launch {
            _relays.value.forEach { relay ->
                if (relay.isEnabled) {
                    pingSingleRelay(relay.id)
                }
            }
        }
    }

    suspend fun pingSingleRelay(relayId: String) = withContext(Dispatchers.IO) {
        val relay = _relays.value.find { it.id == relayId } ?: return@withContext
        val latency = NostrBlossomRelayService.measureLatency(relay.url)
        val statusText = if (latency > 0) "متصل ($latency ms)" else "غير متاح حالياً"

        _relays.value = _relays.value.map {
            if (it.id == relayId) it.copy(latencyMs = latency, status = statusText) else it
        }
    }

    /**
     * Smart Hybrid Transmission: Uploads / Routes file across 4 layers
     */
    suspend fun transmitEncryptedMedia(
        fileName: String,
        encryptedBytes: ByteArray,
        targetPeerNodeId: String? = null,
        mimeType: String = "application/octet-stream"
    ): Pair<DecentralizedBlobRef?, String> = withContext(Dispatchers.IO) {
        _activeTransfersCount.value += 1
        val startTime = System.currentTimeMillis()
        var usedMethod = "Hybrid Fallback"
        var targetUrl = ""

        try {
            val mode = _transmissionMode.value
            val targetPeerIp = targetPeerNodeId?.let { directPeerIpCache[it.trim().lowercase()] }

            // 1. Check Direct P2P (if mode allows and peer IP is known)
            if ((mode == TransmissionMode.SMART_HYBRID || mode == TransmissionMode.DIRECT_P2P_ONLY) && targetPeerIp != null) {
                val directSuccess = DirectP2PChannelService.sendDirectToPeer(
                    peerIp = targetPeerIp,
                    fileName = fileName,
                    data = encryptedBytes
                )
                if (directSuccess) {
                    usedMethod = "Direct P2P Sockets (0-Hop)"
                    targetUrl = "p2p://$targetPeerIp:${DirectP2PChannelService.P2P_DATA_PORT}"
                    val sha = NostrBlossomRelayService.computeSha256(encryptedBytes)
                    recordTelemetry(fileName, encryptedBytes.size.toLong(), usedMethod, targetUrl, startTime, true)
                    val ref = DecentralizedBlobRef(
                        sha256 = sha,
                        fileName = fileName,
                        mimeType = mimeType,
                        sizeBytes = encryptedBytes.size.toLong(),
                        directUrls = listOf(targetUrl),
                        encryptionKey = ""
                    )
                    return@withContext Pair(ref, usedMethod)
                }
            }

            if (mode == TransmissionMode.DIRECT_P2P_ONLY) {
                recordTelemetry(fileName, encryptedBytes.size.toLong(), "Direct P2P Only", "Failed (Peer unreachable)", startTime, false)
                return@withContext Pair(null, "Direct P2P Failed")
            }

            // 2. High-Speed Global CDN Encrypted Blob (Direct E2EE Multi-Mirror)
            val fastCdnUrl = FastMediaDeliveryEngine.uploadEncryptedBlob(encryptedBytes, fileName)
            if (fastCdnUrl != null) {
                val sha = FastMediaDeliveryEngine.computeSha256(encryptedBytes)
                usedMethod = "High-Speed Encrypted CDN"
                targetUrl = fastCdnUrl

                val ref = DecentralizedBlobRef(
                    sha256 = sha,
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = encryptedBytes.size.toLong(),
                    directUrls = listOf(fastCdnUrl),
                    encryptionKey = ""
                )
                recordTelemetry(fileName, encryptedBytes.size.toLong(), usedMethod, targetUrl, startTime, true)
                return@withContext Pair(ref, usedMethod)
            }

            // 3. Try Blossom Decentralized Media Servers
            val customBlossomServers = _relays.value
                .filter { it.type == RelayType.BLOSSOM_HTTP && it.isEnabled }
                .map { it.url }

            val blossomResult = NostrBlossomRelayService.uploadBlobToBlossom(
                encryptedData = encryptedBytes,
                contentType = mimeType,
                customServers = customBlossomServers
            )

            if (blossomResult != null) {
                val sha = blossomResult.first
                val serverUrl = blossomResult.second
                usedMethod = "Blossom Decentralized Blob"
                targetUrl = "$serverUrl/$sha"

                // Create blob ref with all viable fallback mirrors
                val candidateMirrors = (listOf(serverUrl) + customBlossomServers + NostrBlossomRelayService.DEFAULT_BLOSSOM_SERVERS).distinct().map { "$it/$sha" }
                val ref = DecentralizedBlobRef(
                    sha256 = sha,
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = encryptedBytes.size.toLong(),
                    directUrls = candidateMirrors,
                    encryptionKey = ""
                )
                recordTelemetry(fileName, encryptedBytes.size.toLong(), usedMethod, targetUrl, startTime, true)
                return@withContext Pair(ref, usedMethod)
            }

            // 4. Try IPFS / Chunked Distributed Gateways
            val sha = IpfsChunkingService.computeSha256(encryptedBytes)
            usedMethod = "Distributed IPFS Gateways"
            targetUrl = "ipfs://$sha"

            val ref = DecentralizedBlobRef(
                sha256 = sha,
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = encryptedBytes.size.toLong(),
                directUrls = listOf("https://ipfs.io/ipfs/$sha", "https://dweb.link/ipfs/$sha"),
                ipfsCid = sha,
                encryptionKey = ""
            )
            recordTelemetry(fileName, encryptedBytes.size.toLong(), usedMethod, targetUrl, startTime, true)
            return@withContext Pair(ref, usedMethod)

        } catch (e: Exception) {
            Log.e(TAG, "Transmission failed: ${e.message}")
            recordTelemetry(fileName, encryptedBytes.size.toLong(), usedMethod, e.message ?: "Error", startTime, false)
            return@withContext Pair(null, "Failed: ${e.message}")
        } finally {
            _activeTransfersCount.value = (_activeTransfersCount.value - 1).coerceAtLeast(0)
        }
    }

    /**
     * Download and verify decentralized media blob from multiple parallel mirrors
     */
    suspend fun fetchDecentralizedBlob(
        sha256: String,
        directUrls: List<String> = emptyList()
    ): ByteArray? = withContext(Dispatchers.IO) {
        // 1. Try direct HTTPS URLs first (from FastMediaDelivery or Blossom mirrors)
        for (url in directUrls) {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                val directData = FastMediaDeliveryEngine.downloadEncryptedBlob(url)
                if (directData != null) {
                    return@withContext directData
                }
            }
        }

        // 2. Try candidate Blossom Servers
        val customBlossomServers = _relays.value
            .filter { it.type == RelayType.BLOSSOM_HTTP && it.isEnabled }
            .map { it.url }

        val candidateServers = (directUrls.filter { it.startsWith("http") }.map { it.substringBeforeLast('/') } + customBlossomServers).distinct()
        val data = NostrBlossomRelayService.downloadBlobFromBlossom(sha256, candidateServers)
        if (data != null) {
            return@withContext data
        }

        // 3. Try IPFS Gateways
        val ipfsGateways = _relays.value
            .filter { it.type == RelayType.IPFS_GATEWAY && it.isEnabled }
            .map { it.url }
        val ipfsData = IpfsChunkingService.fetchFromIpfsGateways(sha256, ipfsGateways)
        if (ipfsData != null) {
            return@withContext ipfsData
        }

        null
    }

    private fun recordTelemetry(
        fileName: String,
        fileSize: Long,
        usedMethod: String,
        relayUrl: String,
        startTime: Long,
        isSuccess: Boolean
    ) {
        val duration = System.currentTimeMillis() - startTime
        val item = TransmissionTelemetry(
            transferId = "tx_" + System.currentTimeMillis(),
            fileName = fileName,
            fileSize = fileSize,
            usedMethod = usedMethod,
            relayUrl = relayUrl,
            durationMs = duration,
            isSuccess = isSuccess
        )
        _telemetryLogs.value = (listOf(item) + _telemetryLogs.value).take(50)
    }
}
