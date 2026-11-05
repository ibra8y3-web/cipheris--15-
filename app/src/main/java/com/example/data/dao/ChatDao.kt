package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Peer
import com.example.data.model.Message
import com.example.data.model.GroupChat
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // --- PEERS ---
    @Query("SELECT * FROM peers WHERE isSelf = 0 ORDER BY addedAt DESC")
    fun getPeersFlow(): Flow<List<Peer>>

    @Query("SELECT * FROM peers")
    suspend fun getAllPeersList(): List<Peer>

    @Query("SELECT * FROM peers WHERE isSelf = 1 ORDER BY selfNodeIndex ASC")
    suspend fun getSelfPeers(): List<Peer>

    @Query("SELECT * FROM peers WHERE isSelf = 1 AND selfNodeIndex = :index LIMIT 1")
    suspend fun getSelfPeerByIndex(index: Int): Peer?

    @Query("SELECT * FROM peers WHERE isSelf = 1")
    fun getSelfPeersFlow(): Flow<List<Peer>>

    @Query("SELECT * FROM peers WHERE nodeId = :nodeId LIMIT 1")
    suspend fun getPeerByNodeId(nodeId: String): Peer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeer(peer: Peer)

    @Query("DELETE FROM peers WHERE nodeId = :nodeId")
    suspend fun deletePeer(nodeId: String)

    // --- MESSAGES ---
    @Query("SELECT * FROM messages WHERE peerNodeId = :peerNodeId AND isDeleted = 0 ORDER BY timestamp ASC")
    fun getDirectMessagesFlow(peerNodeId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE groupId = :groupId AND isDeleted = 0 ORDER BY timestamp ASC")
    fun getGroupMessagesFlow(groupId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: Long): Message?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message): Long

    @Update
    suspend fun updateMessage(message: Message)

    @Query("UPDATE messages SET isDeleted = 1 WHERE id = :messageId")
    suspend fun softDeleteMessage(messageId: Long)

    @Query("UPDATE messages SET selfDestructAt = :destructAt WHERE id = :messageId")
    suspend fun updateSelfDestructAt(messageId: Long, destructAt: Long)

    @Query("DELETE FROM messages WHERE selfDestructAt > 0 AND :currentTime >= selfDestructAt")
    suspend fun deleteExpiredMessages(currentTime: Long): Int

    @Query("SELECT * FROM messages WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllActiveMessagesFlow(): Flow<List<Message>>

    @Query("SELECT * FROM messages")
    suspend fun getAllMessagesList(): List<Message>

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE senderNodeId = :senderNodeId AND text = :text AND timestamp = :timestamp LIMIT 1)")
    suspend fun hasMessage(senderNodeId: String, text: String, timestamp: Long): Boolean

    @Query("SELECT * FROM messages WHERE peerNodeId = :peerNodeId AND senderNodeId = :senderNodeId AND timestamp = :timestamp LIMIT 1")
    suspend fun getMessageByTimestampAndPeer(peerNodeId: String, senderNodeId: String, timestamp: Long): Message?

    @Query("SELECT * FROM messages WHERE senderNodeId = :senderNodeId AND timestamp = :timestamp LIMIT 1")
    suspend fun getMessageBySenderAndTimestamp(senderNodeId: String, timestamp: Long): Message?

    @Query("SELECT * FROM channel_posts WHERE channelId = :channelId AND timestamp = :timestamp LIMIT 1")
    suspend fun getChannelPostByTimestamp(channelId: String, timestamp: Long): com.example.data.model.ChannelPost?

    // --- GROUPS ---
    @Query("SELECT * FROM group_chats ORDER BY createdAt DESC")
    fun getGroupChatsFlow(): Flow<List<GroupChat>>

    @Query("SELECT * FROM group_chats")
    suspend fun getAllGroupsList(): List<GroupChat>

    @Query("SELECT * FROM group_chats WHERE groupId = :groupId LIMIT 1")
    suspend fun getGroupById(groupId: String): GroupChat?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupChat)

    @Query("DELETE FROM group_chats WHERE groupId = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("DELETE FROM group_chats")
    suspend fun deleteAllGroups()

    @Query("DELETE FROM messages WHERE groupId = :groupId")
    suspend fun clearGroupMessages(groupId: String)

    @Query("SELECT * FROM messages WHERE groupId = :groupId AND timestamp = :timestamp LIMIT 1")
    suspend fun getGroupMessageByTimestamp(groupId: String, timestamp: Long): Message?

    @Query("SELECT * FROM messages WHERE groupId = :groupId AND senderNodeId = :senderNodeId AND isDeleted = 0")
    suspend fun getGroupMessagesBySender(groupId: String, senderNodeId: String): List<Message>

    @Query("UPDATE group_chats SET pinnedMessageId = :messageId WHERE groupId = :groupId")
    suspend fun pinGroupMessage(groupId: String, messageId: Long?)

    @Query("DELETE FROM peers WHERE isSelf = 0")
    suspend fun deleteNonSelfPeers()

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    // --- ALIAS REGISTRY (DNS) ---
    @Query("SELECT * FROM alias_registry WHERE alias LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchAliasesFlow(query: String): Flow<List<com.example.data.model.AliasEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlias(entry: com.example.data.model.AliasEntry)

    @Query("SELECT * FROM alias_registry WHERE nodeId = :nodeId LIMIT 1")
    suspend fun getAliasEntryByNodeId(nodeId: String): com.example.data.model.AliasEntry?

    // --- BROADCAST CHANNELS ---
    @Query("SELECT * FROM broadcast_channels ORDER BY createdAt DESC")
    fun getBroadcastChannelsFlow(): Flow<List<com.example.data.model.BroadcastChannel>>

    @Query("SELECT * FROM broadcast_channels")
    suspend fun getAllChannelsList(): List<com.example.data.model.BroadcastChannel>

    @Query("SELECT * FROM broadcast_channels WHERE channelId = :channelId LIMIT 1")
    suspend fun getChannelById(channelId: String): com.example.data.model.BroadcastChannel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: com.example.data.model.BroadcastChannel)

    @Query("DELETE FROM broadcast_channels WHERE channelId = :channelId")
    suspend fun deleteChannel(channelId: String)

    @Query("UPDATE broadcast_channels SET pinnedPostId = :postId WHERE channelId = :channelId")
    suspend fun pinChannelPost(channelId: String, postId: Long?)

    @Query("SELECT * FROM channel_posts WHERE channelId = :channelId ORDER BY timestamp ASC")
    fun getChannelPostsFlow(channelId: String): Flow<List<com.example.data.model.ChannelPost>>

    @Query("SELECT * FROM channel_posts WHERE channelId = :channelId")
    suspend fun getChannelPostsList(channelId: String): List<com.example.data.model.ChannelPost>

    @Query("SELECT * FROM channel_posts WHERE id = :id LIMIT 1")
    suspend fun getChannelPostById(id: Long): com.example.data.model.ChannelPost?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannelPost(post: com.example.data.model.ChannelPost)

    @Update
    suspend fun updateChannelPost(post: com.example.data.model.ChannelPost)

    @Query("DELETE FROM channel_posts WHERE id = :id")
    suspend fun deleteChannelPost(id: Long)

    @Query("SELECT * FROM channel_posts")
    suspend fun getAllChannelPostsList(): List<com.example.data.model.ChannelPost>

    @Query("SELECT EXISTS(SELECT 1 FROM channel_posts WHERE channelId = :channelId AND text = :text AND timestamp = :timestamp LIMIT 1)")
    suspend fun hasChannelPost(channelId: String, text: String, timestamp: Long): Boolean

    @Query("DELETE FROM channel_posts WHERE channelId = :channelId")
    suspend fun deleteChannelPosts(channelId: String)

    // --- UNSENT MESSAGES (QUEUE) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnsentMessage(unsent: com.example.data.model.UnsentMessage): Long

    @Query("SELECT * FROM unsent_messages ORDER BY addedAt ASC")
    suspend fun getAllUnsentMessages(): List<com.example.data.model.UnsentMessage>

    @Query("DELETE FROM unsent_messages WHERE id = :id")
    suspend fun deleteUnsentMessage(id: Long)
}
