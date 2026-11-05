package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.ChatDao
import com.example.data.model.Peer
import com.example.data.model.Message
import com.example.data.model.GroupChat
import com.example.data.model.AliasEntry
import com.example.data.model.BroadcastChannel
import com.example.data.model.ChannelPost
import com.example.data.model.UnsentMessage
import com.example.data.model.VaultEntry
import com.example.data.dao.VaultDao

import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import com.example.data.security.SecurityManager

import com.example.data.model.StickerPack
import com.example.data.model.StickerItem
import com.example.data.model.CustomEmojiSet
import com.example.data.model.CustomEmojiItem
import com.example.data.dao.StickerDao

@Database(
    entities = [Peer::class, Message::class, GroupChat::class, AliasEntry::class, BroadcastChannel::class, ChannelPost::class, UnsentMessage::class, VaultEntry::class, StickerPack::class, StickerItem::class, CustomEmojiSet::class, CustomEmojiItem::class],
    version = 14,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun vaultDao(): VaultDao
    abstract fun stickerDao(): StickerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val securityManager = SecurityManager(context)
                val passphrase = securityManager.getDatabasePassphrase().toByteArray()
                val factory = SupportFactory(passphrase)
                
                SQLiteDatabase.loadLibs(context)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cipheris_secure_chat_v2.db"
                )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
