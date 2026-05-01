package com.darkssh.client.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.darkssh.client.data.entity.KnownHost
import kotlinx.coroutines.flow.Flow

@Dao
interface KnownHostDao {
    @Query("SELECT * FROM known_hosts WHERE hostId = :hostId")
    fun getByHostId(hostId: Long): Flow<List<KnownHost>>

    @Query("SELECT * FROM known_hosts WHERE hostId = :hostId AND hostKeyAlgo = :algo")
    suspend fun getByHostIdAndAlgo(hostId: Long, algo: String): List<KnownHost>

    @Query("SELECT * FROM known_hosts WHERE hostId = :hostId AND hostKeyAlgo = :algo AND hostKey = :key")
    suspend fun getByHostIdAlgoAndKey(hostId: Long, algo: String, key: String): KnownHost?

    @Query("SELECT * FROM known_hosts")
    fun getAll(): Flow<List<KnownHost>>

    @Insert
    suspend fun insert(knownHost: KnownHost): Long

    @Update
    suspend fun update(knownHost: KnownHost)

    @Delete
    suspend fun delete(knownHost: KnownHost)

    @Query("DELETE FROM known_hosts WHERE hostname = :hostname AND port = :port")
    suspend fun deleteByHostnameAndPort(hostname: String, port: Int)

    @Query("DELETE FROM known_hosts WHERE hostId = :hostId")
    suspend fun deleteByHostId(hostId: Long)
}