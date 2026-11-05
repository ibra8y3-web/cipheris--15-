package com.example.data.dao

import androidx.room.*
import com.example.data.model.VaultEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_entries ORDER BY timestamp DESC")
    fun getAllVaultEntries(): Flow<List<VaultEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultEntry(entry: VaultEntry)

    @Update
    suspend fun updateVaultEntry(entry: VaultEntry)

    @Delete
    suspend fun deleteVaultEntry(entry: VaultEntry)

    @Query("DELETE FROM vault_entries")
    suspend fun deleteAllVaultEntries()
}
