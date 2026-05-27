package com.darkssh.client.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.darkssh.client.data.entity.Tab
import kotlinx.coroutines.flow.Flow

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs ORDER BY position ASC")
    fun getAll(): Flow<List<Tab>>

    @Query("SELECT * FROM tabs WHERE id = :id")
    suspend fun getById(id: String): Tab?

    @Query("SELECT * FROM tabs WHERE hostId = :hostId")
    fun getByHostId(hostId: Long): Flow<List<Tab>>

    @Insert
    suspend fun insert(tab: Tab)

    @Update
    suspend fun update(tab: Tab)

    @Delete
    suspend fun delete(tab: Tab)

    @Query("DELETE FROM tabs")
    suspend fun deleteAll()

    @Query("UPDATE tabs SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: String, position: Int)
}
