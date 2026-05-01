package com.darkssh.client.data.repository

import com.darkssh.client.data.dao.PortForwardDao
import com.darkssh.client.data.entity.PortForward
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortForwardRepository @Inject constructor(private val portForwardDao: PortForwardDao) {
    fun getPortForwardsByHostId(hostId: Long): Flow<List<PortForward>> =
        portForwardDao.getByHostId(hostId)

    suspend fun insertPortForward(portForward: PortForward): Long =
        portForwardDao.insert(portForward)

    suspend fun deletePortForward(portForward: PortForward) = portForwardDao.delete(portForward)
}