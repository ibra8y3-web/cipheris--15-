package com.example.data.decentralized

enum class RelayType(val displayName: String, val icon: String) {
    NOSTR_WS("مرحل نوستر (Nostr WebSocket)", "⚡"),
    BLOSSOM_HTTP("خادم وسائط بلوسوم (Blossom Media)", "🌸"),
    IPFS_GATEWAY("بوابة كتل IPFS الموزعة", "🌐"),
    P2P_DIRECT("نقل مباشر نظير لنظير (Direct P2P)", "🔗"),
    MESH_WEBHOOK("مرحل الشبكة المتداخلة (Mesh Multi-Relay)", "🛡️")
}

enum class TransmissionMode(val title: String, val description: String) {
    SMART_HYBRID("هجين ذكي تلقائي (موصى به)", "يحاول النقل المباشر P2P أولاً، ثم خوادم Blossom السريعة، ثم كتل IPFS والمرحلات اللامركزية"),
    DIRECT_P2P_ONLY("نقل مباشر نظير لنظير (P2P Direct)", "نقل فوري مباشر بين الجهازين فقط بدون أي خوادم وسيطة"),
    BLOSSOM_NOSTR("شبكة بلوسوم ونوستر اللامركزية (Blossom/Nostr)", "رفع وتنزيل فوري مشفر عبر شبكة خوادم Blossom الموزعة عالمياً"),
    IPFS_DISTRIBUTED("تخزين الكتل الموزع (IPFS Distributed)", "تجزئة الملف لكتل مشفرة قابلة للاستئناف والتوزيع عبر عقد IPFS")
}

data class DecentralizedRelay(
    val id: String,
    val name: String,
    val url: String,
    val type: RelayType,
    val isEnabled: Boolean = true,
    val latencyMs: Long = -1,
    val status: String = "جاهز",
    val isCustom: Boolean = false
)

data class TransmissionTelemetry(
    val transferId: String,
    val fileName: String,
    val fileSize: Long,
    val usedMethod: String,
    val relayUrl: String,
    val durationMs: Long,
    val isSuccess: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class DecentralizedBlobRef(
    val sha256: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val directUrls: List<String>,
    val ipfsCid: String? = null,
    val encryptionKey: String,
    val encryptionIv: String? = null,
    val chunkCount: Int = 1
)
