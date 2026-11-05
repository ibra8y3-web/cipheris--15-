package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.StickerPack
import com.example.data.model.StickerItem
import com.example.data.model.CustomEmojiSet
import com.example.data.model.CustomEmojiItem
import kotlinx.coroutines.flow.Flow

@Dao
interface StickerDao {
    @Query("SELECT * FROM sticker_packs ORDER BY createdAt DESC")
    fun getStickerPacksFlow(): Flow<List<StickerPack>>

    @Query("SELECT * FROM sticker_packs")
    suspend fun getAllStickerPacks(): List<StickerPack>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStickerPack(pack: StickerPack)

    @Query("SELECT * FROM sticker_items WHERE packId = :packId")
    fun getStickerItemsFlow(packId: String): Flow<List<StickerItem>>

    @Query("SELECT * FROM sticker_items WHERE packId = :packId")
    suspend fun getStickerItemsByPack(packId: String): List<StickerItem>

    @Query("SELECT * FROM sticker_items")
    suspend fun getAllStickerItems(): List<StickerItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStickerItem(item: StickerItem)

    @Query("SELECT * FROM custom_emoji_sets ORDER BY createdAt DESC")
    fun getCustomEmojiSetsFlow(): Flow<List<CustomEmojiSet>>

    @Query("SELECT * FROM custom_emoji_sets")
    suspend fun getAllCustomEmojiSets(): List<CustomEmojiSet>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomEmojiSet(set: CustomEmojiSet)

    @Query("SELECT * FROM custom_emoji_items WHERE setId = :setId")
    fun getCustomEmojiItemsFlow(setId: String): Flow<List<CustomEmojiItem>>

    @Query("SELECT * FROM custom_emoji_items")
    suspend fun getAllCustomEmojiItems(): List<CustomEmojiItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomEmojiItem(item: CustomEmojiItem)

    @Query("DELETE FROM sticker_packs WHERE packId = :packId")
    suspend fun deleteStickerPack(packId: String)

    @Query("DELETE FROM sticker_items WHERE packId = :packId")
    suspend fun deleteStickerItemsByPack(packId: String)
}
