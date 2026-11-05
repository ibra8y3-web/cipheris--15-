package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

sealed interface ParsedDeepLink {
    data class UserLink(
        val nodeId: String,
        val alias: String? = null
    ) : ParsedDeepLink

    data class GroupLink(
        val groupId: String,
        val groupName: String? = null,
        val inviteCode: String = ""
    ) : ParsedDeepLink

    data class ChannelLink(
        val channelId: String,
        val channelName: String? = null,
        val encryptionKey: String = ""
    ) : ParsedDeepLink
}

object DeepLinkHelper {
    private const val TAG = "DeepLinkHelper"

    const val BASE_WEB_DOMAIN = "https://cipheris-web-1.onrender.com"

    /**
     * Parses any URI, Web URL or plain text into a structured ParsedDeepLink
     */
    fun parse(rawInput: String?): ParsedDeepLink? {
        if (rawInput.isNullOrBlank()) return null
        val input = rawInput.trim()

        try {
            // Extract query parameters if present (key, name, id, type)
            var keyParam = ""
            var nameParam: String? = null
            var idParam: String? = null
            var typeParam: String? = null

            try {
                val uri = Uri.parse(input)
                keyParam = uri.getQueryParameter("key") ?: ""
                nameParam = uri.getQueryParameter("name")
                idParam = uri.getQueryParameter("id")
                typeParam = uri.getQueryParameter("type")?.lowercase()
            } catch (_: Exception) {}

            // Helper to clean up any messy strings/prefixes
            val cleanStr = input.lowercase()

            // 1. Group Detection (Checks web /g/, /group/, cipheris://group/, type=group)
            if (cleanStr.contains("/g/") || cleanStr.contains("/group/") || cleanStr.contains("cipheris://group/") || typeParam == "group" || idParam?.contains("group") == true) {
                val rawGid = when {
                    idParam != null -> idParam!!.substringAfter("cipheris://group/").substringAfter("group/").substringBefore("?")
                    input.contains("/g/") -> input.substringAfter("/g/").substringBefore("?")
                    input.contains("/group/") -> input.substringAfter("/group/").substringBefore("?")
                    input.contains("cipheris://group/") -> input.substringAfter("cipheris://group/").substringBefore("?")
                    else -> ""
                }
                val cleanGid = rawGid.substringAfterLast("/").substringAfter("group/").substringAfter("cipheris://").trim()
                if (cleanGid.isNotEmpty()) {
                    val fullGid = if (cleanGid.startsWith("cipheris://group/")) cleanGid else "cipheris://group/$cleanGid"
                    return ParsedDeepLink.GroupLink(groupId = fullGid, groupName = nameParam, inviteCode = keyParam)
                }
            }

            // 2. Channel Detection (Checks web /c/, /channel/, cipheris://channel/, type=channel)
            if (cleanStr.contains("/c/") || cleanStr.contains("/channel/") || cleanStr.contains("cipheris://channel/") || typeParam == "channel" || idParam?.contains("channel") == true) {
                val rawCid = when {
                    idParam != null -> idParam!!.substringAfter("cipheris://channel/").substringAfter("channel/").substringBefore("?")
                    input.contains("/c/") -> input.substringAfter("/c/").substringBefore("?")
                    input.contains("/channel/") -> input.substringAfter("/channel/").substringBefore("?")
                    input.contains("cipheris://channel/") -> input.substringAfter("cipheris://channel/").substringBefore("?")
                    else -> ""
                }
                val cleanCid = rawCid.substringAfterLast("/").substringAfter("channel/").substringAfter("cipheris://").trim()
                if (cleanCid.isNotEmpty()) {
                    val fullCid = if (cleanCid.startsWith("cipheris://channel/")) cleanCid else "cipheris://channel/$cleanCid"
                    return ParsedDeepLink.ChannelLink(channelId = fullCid, channelName = nameParam, encryptionKey = keyParam)
                }
            }

            // 3. User / Node Detection (Checks web /u/, /user/, cipheris://user/, type=user, or raw Node ID)
            if (cleanStr.contains("/u/") || cleanStr.contains("/user/") || cleanStr.contains("cipheris://user/") || typeParam == "user") {
                val rawUid = when {
                    idParam != null -> idParam!!.substringAfter("cipheris://user/").substringAfter("user/").substringBefore("?")
                    input.contains("/u/") -> input.substringAfter("/u/").substringBefore("?")
                    input.contains("/user/") -> input.substringAfter("/user/").substringBefore("?")
                    input.contains("cipheris://user/") -> input.substringAfter("cipheris://user/").substringBefore("?")
                    else -> ""
                }
                val cleanUid = rawUid.substringAfterLast("/").substringAfter("user/").substringAfter("cipheris://").trim()
                if (cleanUid.isNotEmpty()) {
                    val fullUid = if (cleanUid.startsWith("cipheris://")) cleanUid else "cipheris://$cleanUid"
                    return ParsedDeepLink.UserLink(nodeId = fullUid, alias = nameParam)
                }
            }

            // 4. Custom App Scheme without subpath (cipheris://<nodeId>)
            if (input.startsWith("cipheris://") && !input.contains("/group/") && !input.contains("/channel/")) {
                val nodePart = input.substringAfter("cipheris://").substringBefore("?").substringBefore("/")
                if (nodePart.isNotEmpty()) {
                    return ParsedDeepLink.UserLink("cipheris://$nodePart", nameParam)
                }
            }

            // 5. Raw 16-hex character Node ID
            val trimmedOnlyHex = input.replace("cipheris://", "").trim()
            if (trimmedOnlyHex.matches(Regex("^[0-9a-fA-F]{16}$"))) {
                return ParsedDeepLink.UserLink("cipheris://${trimmedOnlyHex.lowercase()}", nameParam)
            }

        } catch (e: Exception) {
            Log.w(TAG, "Error parsing deep link input: $input", e)
        }

        return null
    }

    /**
     * Creates a Universal Web Link for sharing
     */
    fun createUniversalLink(link: ParsedDeepLink): String {
        return when (link) {
            is ParsedDeepLink.UserLink -> {
                val cleanHex = link.nodeId.substringAfterLast("/").substringAfter("cipheris://").substringAfter("user/").trim().lowercase()
                val base = "$BASE_WEB_DOMAIN/u/$cleanHex"
                if (!link.alias.isNullOrBlank()) {
                    "$base?name=${Uri.encode(link.alias)}"
                } else {
                    base
                }
            }
            is ParsedDeepLink.GroupLink -> {
                val cleanGid = link.groupId.substringAfterLast("/").substringAfter("cipheris://group/").substringAfter("group/").substringAfter("cipheris://").trim()
                val base = "$BASE_WEB_DOMAIN/g/$cleanGid"
                val params = mutableListOf<String>()
                if (link.inviteCode.isNotEmpty()) params.add("key=${Uri.encode(link.inviteCode)}")
                if (!link.groupName.isNullOrEmpty()) params.add("name=${Uri.encode(link.groupName)}")
                if (params.isNotEmpty()) "$base?${params.joinToString("&")}" else base
            }
            is ParsedDeepLink.ChannelLink -> {
                val cleanCid = link.channelId.substringAfterLast("/").substringAfter("cipheris://channel/").substringAfter("channel/").substringAfter("cipheris://").trim()
                val base = "$BASE_WEB_DOMAIN/c/$cleanCid"
                val params = mutableListOf<String>()
                if (link.encryptionKey.isNotEmpty()) params.add("key=${Uri.encode(link.encryptionKey)}")
                if (!link.channelName.isNullOrEmpty()) params.add("name=${Uri.encode(link.channelName)}")
                if (params.isNotEmpty()) "$base?${params.joinToString("&")}" else base
            }
        }
    }

    /**
     * Creates an Android Share Intent with rich formatted invitation text
     */
    fun createShareIntent(context: Context, link: ParsedDeepLink, isEnglish: Boolean = false): Intent {
        val webUrl = createUniversalLink(link)
        val shareText = when (link) {
            is ParsedDeepLink.UserLink -> {
                if (isEnglish) {
                    """
                    🔒 Connect with me on Cipheris Secure Chat!
                    End-to-End Encrypted & Decentralized.
                    
                    👉 Tap link to chat: $webUrl
                    
                    🔑 Node ID: ${link.nodeId}
                    """.trimIndent()
                } else {
                    """
                    🔒 تواصل معي عبر تطبيق سيفيرس (Cipheris) للمحادثات المشفرة فائقة الأمان!
                    
                    👉 اضغط على الرابط لبدء المحادثة المباشرة:
                    $webUrl
                    
                    🔑 رمز العقدة الشخصي:
                    ${link.nodeId}
                    """.trimIndent()
                }
            }
            is ParsedDeepLink.GroupLink -> {
                val gName = link.groupName ?: "المجموعة المشفرة"
                if (isEnglish) {
                    """
                    👥 Join our private encrypted group on Cipheris!
                    Group: $gName
                    
                    👉 Tap link to join: $webUrl
                    
                    🔑 Invite Code: ${link.inviteCode}
                    """.trimIndent()
                } else {
                    """
                    👥 انضم إلى مجموعتنا المشفرة الخاصة على تطبيق سيفيرس!
                    اسم المجموعة: $gName
                    
                    👉 اضغط على الرابط للانضمام مباشرة:
                    $webUrl
                    
                    🔑 رمز الدعوة:
                    ${link.inviteCode}
                    """.trimIndent()
                }
            }
            is ParsedDeepLink.ChannelLink -> {
                val cName = link.channelName ?: "القناة الإخبارية"
                if (isEnglish) {
                    """
                    📢 Subscribe to our official broadcast channel on Cipheris!
                    Channel: $cName
                    
                    👉 Tap link to subscribe: $webUrl
                    
                    🔑 Access Key: ${link.encryptionKey}
                    """.trimIndent()
                } else {
                    """
                    📢 اشترك في القناة الرسمية المشفرة على تطبيق سيفيرس!
                    اسم القناة: $cName
                    
                    👉 اضغط على الرابط للاشتراك الفوري:
                    $webUrl
                    
                    🔑 مفتاح الوصول:
                    ${link.encryptionKey}
                    """.trimIndent()
                }
            }
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
    }

    /**
     * Checks the system clipboard for valid Cipheris deep links or Node IDs
     */
    fun checkClipboardForDeepLink(context: Context): ParsedDeepLink? {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0)?.text?.toString()
                    if (!text.isNullOrBlank()) {
                        parse(text)
                    } else null
                } else null
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Error checking clipboard: ${e.message}")
            null
        }
    }
}
