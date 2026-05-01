package com.darkssh.client.data.repository

import com.darkssh.client.data.dao.KnownHostDao
import com.darkssh.client.data.entity.KnownHost
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnownHostRepository
    @Inject
    constructor(
        private val knownHostDao: KnownHostDao,
    ) {
        fun getByHostId(hostId: Long): Flow<List<KnownHost>> = knownHostDao.getByHostId(hostId)

        suspend fun getByHostIdAndAlgo(hostId: Long, algo: String): List<KnownHost> =
            knownHostDao.getByHostIdAndAlgo(hostId, algo)

        suspend fun getByHostIdAlgoAndKey(hostId: Long, algo: String, key: String): KnownHost? =
            knownHostDao.getByHostIdAlgoAndKey(hostId, algo, key)

        fun getAll(): Flow<List<KnownHost>> = knownHostDao.getAll()

        suspend fun insert(knownHost: KnownHost): Long = knownHostDao.insert(knownHost)

        suspend fun update(knownHost: KnownHost) = knownHostDao.update(knownHost)

        suspend fun delete(knownHost: KnownHost) = knownHostDao.delete(knownHost)

        suspend fun deleteByHostnameAndPort(hostname: String, port: Int) =
            knownHostDao.deleteByHostnameAndPort(hostname, port)

        suspend fun deleteByHostId(hostId: Long) = knownHostDao.deleteByHostId(hostId)
    }