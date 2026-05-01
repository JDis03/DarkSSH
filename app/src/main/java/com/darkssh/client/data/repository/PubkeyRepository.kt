package com.darkssh.client.data.repository

import com.darkssh.client.data.dao.PubkeyDao
import com.darkssh.client.data.entity.Pubkey
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PubkeyRepository @Inject constructor(private val pubkeyDao: PubkeyDao) {
    fun getAllPubkeys(): Flow<List<Pubkey>> = pubkeyDao.getAll()

    suspend fun getPubkeyById(id: Long): Pubkey? = pubkeyDao.getById(id)

    suspend fun insertPubkey(pubkey: Pubkey): Long = pubkeyDao.insert(pubkey)

    suspend fun updatePubkey(pubkey: Pubkey) = pubkeyDao.update(pubkey)

    suspend fun deletePubkey(pubkey: Pubkey) = pubkeyDao.delete(pubkey)
}