package com.darkssh.client.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.darkssh.client.data.entity.PortForward
import kotlinx.coroutines.flow.Flow

@Dao
interface PortForwardDao {
    @Query("SELECT * FROM port_forwards WHERE hostId = :hostId")
    fun getByHostId(hostId: Long): Flow<List<PortForward>>

    @Insert
    suspend fun insert(portForward: PortForward): Long

    @Delete
    suspend fun delete(portForward: PortForward)
}
