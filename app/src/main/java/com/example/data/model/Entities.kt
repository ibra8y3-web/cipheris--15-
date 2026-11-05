package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "peers")
data class Peer(
    @PrimaryKey val nodeId: String, // cipheris://[hex_id]
    val alias: String,
    val publicKeyFingerprint: String,
    val isSelf: Boolean = false,
    val selfNodeIndex: Int? = null, // 0, 1, or 2 for self nodes
    val addedAt: Long = System.currentTimeMillis(),
    val avatarSeed: String = ""
)

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val peerNodeId: String?, // If null, this is a message in a group
    val groupId: String?, // If null, this is a direct private message
    val text: String,
    val mediaPath: String? = null,
    val mediaName: String? = null,
    val senderNodeId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isIncoming: Boolean,
    val isRead: Boolean = false,
    val selfDestructSeconds: Int = 0, // 0 means no self-destruct
    val selfDestructAt: Long = 0, // 0 means not set, otherwise timestamp when it should disappear
    val isDeleted: Boolean = false,
    val isDownloadAllowed: Boolean = true,
    val readByNodeIdsText: String = "", // Comma-separated Node IDs of readers
    val isDelivered: Boolean = false, // New field
    val positiveVotesCount: Int = 0, // Reputation positive votes
    val negativeVotesCount: Int = 0, // Reputation negative votes
    val votedNodeIdsText: String = "" // Comma-separated Node IDs of users who voted on this message
)

@Entity(tableName = "unsent_messages")
data class UnsentMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topic: String,
    val payloadJson: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "group_chats")
data class GroupChat(
    @PrimaryKey val groupId: String, // cipheris://group/[hex_id]
    val name: String,
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val memberNodeIdsText: String, // Comma separated Node IDs
    val adminNodeIdsText: String = "",
    val moderatorNodeIdsText: String = "",
    val bannedNodeIdsText: String = "",
    val permissionsJsonText: String = "{}", // JSON mapping nodeId to permission flags
    val pinnedMessageId: Long? = null // ID of the pinned message
)

@Entity(tableName = "alias_registry")
data class AliasEntry(
    @PrimaryKey val alias: String, // The human-readable alias
    val nodeId: String,           // The technical nodeId
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "broadcast_channels")
data class BroadcastChannel(
    @PrimaryKey val channelId: String, // cipheris://channel/[hex_id]
    val name: String,
    val description: String,
    val isPrivate: Boolean,
    val encryptionKey: String,
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val memberNodeIdsText: String = "", // Regular subscribers
    val adminNodeIdsText: String = "",
    val moderatorNodeIdsText: String = "",
    val bannedNodeIdsText: String = "",
    val permissionsJsonText: String = "{}", // JSON mapping nodeId to permission flags
    val pendingJoinRequestsText: String = "", // Comma-separated Node IDs of pending requests
    val pinnedPostId: Long? = null // ID of the pinned channel post
)

@Entity(tableName = "channel_posts")
data class ChannelPost(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val text: String,
    val mediaPath: String? = null,
    val mediaName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isAnonymous: Boolean = true,
    val readByNodeIdsText: String = "", // Comma-separated Node IDs of readers
    val isDelivered: Boolean = true
)

@Entity(tableName = "sticker_packs")
data class StickerPack(
    @PrimaryKey val packId: String,
    val name: String,
    val author: String = "Cipheris",
    val iconEmoji: String = "✨",
    val createdAt: Long = System.currentTimeMillis(),
    val isBackedUp: Boolean = false
)

@Entity(tableName = "sticker_items")
data class StickerItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packId: String,
    val name: String = "",
    val imagePathOrUri: String,
    val emojiTag: String = "✨"
)

@Entity(tableName = "custom_emoji_sets")
data class CustomEmojiSet(
    @PrimaryKey val setId: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_emoji_items")
data class CustomEmojiItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val setId: String,
    val shortcode: String,
    val imagePathOrUri: String
)


