package com.darkssh.client.data.dao

import androidx.room.*
import com.darkssh.client.data.entity.Host
import kotlinx.coroutines.flow.Flow

@Dao
interface HostDao {
    @Query("SELECT * FROM hosts ORDER BY nickname COLLATE NOCASE ASC")
    fun getAll(): Flow<List<Host>>

    @Query("SELECT * FROM hosts WHERE id = :id")
    suspend fun getById(id: Long): Host?

    @Insert
    suspend fun insert(host: Host): Long

    @Update
    suspend fun update(host: Host)

    @Delete
    suspend fun delete(host: Host)
}