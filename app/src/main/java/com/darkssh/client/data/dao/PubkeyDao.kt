package com.darkssh.client.data.dao

import androidx.room.*
import com.darkssh.client.data.entity.Pubkey
import kotlinx.coroutines.flow.Flow

@Dao
interface PubkeyDao {
    @Query("SELECT * FROM pubkeys ORDER BY nickname COLLATE NOCASE ASC")
    fun getAll(): Flow<List<Pubkey>>

    @Query("SELECT * FROM pubkeys WHERE id = :id")
    suspend fun getById(id: Long): Pubkey?

    @Insert
    suspend fun insert(pubkey: Pubkey): Long

    @Update
    suspend fun update(pubkey: Pubkey)

    @Delete
    suspend fun delete(pubkey: Pubkey)
}