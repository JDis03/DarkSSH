package com.darkssh.client.data.repository

import com.darkssh.client.data.dao.TabDao
import com.darkssh.client.data.entity.Tab
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TabRepository
    @Inject
    constructor(
        private val tabDao: TabDao,
    ) {
        fun getAllTabs(): Flow<List<Tab>> = tabDao.getAll()

        suspend fun getTabById(id: String): Tab? = tabDao.getById(id)

        fun getTabsByHostId(hostId: Long): Flow<List<Tab>> = tabDao.getByHostId(hostId)

        suspend fun insertTab(tab: Tab) = tabDao.insert(tab)

        suspend fun updateTab(tab: Tab) = tabDao.update(tab)

        suspend fun deleteTab(tab: Tab) = tabDao.delete(tab)

        suspend fun deleteAllTabs() = tabDao.deleteAll()

        suspend fun updateTabPosition(
            id: String,
            position: Int,
        ) = tabDao.updatePosition(id, position)
    }
