package com.darkssh.client.data.repository

import com.darkssh.client.data.dao.HostDao
import com.darkssh.client.data.entity.Host
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HostRepository
    @Inject
    constructor(
        private val hostDao: HostDao,
    ) {
        fun getAllHosts(): Flow<List<Host>> = hostDao.getAll()

        suspend fun getHostById(id: Long): Host? = hostDao.getById(id)

        suspend fun insertHost(host: Host): Long = hostDao.insert(host)

        suspend fun updateHost(host: Host) = hostDao.update(host)

        suspend fun deleteHost(host: Host) = hostDao.delete(host)
    }
