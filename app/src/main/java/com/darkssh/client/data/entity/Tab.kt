package com.darkssh.client.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class TabType {
    SSH_TERMINAL,
    SFTP_BROWSER
}

@Entity(
    tableName = "tabs",
    indices = [
        Index(value = ["hostId"]),
        Index(value = ["position"]),
    ],
)
data class Tab(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: TabType = TabType.SSH_TERMINAL,
    val hostId: Long = 0L,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val title: String = "",
    val themeId: String? = null, // For terminal theme
)
