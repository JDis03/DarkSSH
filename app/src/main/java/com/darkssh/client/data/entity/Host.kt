package com.darkssh.client.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hosts",
    indices = [
        Index(value = ["nickname"], unique = true),
        Index(value = ["hostname"]),
    ],
)
data class Host(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val nickname: String = "",
    val hostname: String = "",
    val username: String = "",
    val port: Int = 22,
    val protocol: String = "ssh",
    val lastConnected: Long? = null,
    val color: String = "",
    val pubkeyId: Long? = null,
    val useAuthAgent: String = "no",
    val postLoginScript: String = "",
    val compression: Boolean = false,
    val utf8ByDefault: Boolean = false,
    val stayConnected: Boolean = false,
    val quickDisconnect: Boolean = false,
)