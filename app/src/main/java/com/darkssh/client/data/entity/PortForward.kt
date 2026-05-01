package com.darkssh.client.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "port_forwards",
    foreignKeys = [
        ForeignKey(
            entity = Host::class,
            parentColumns = ["id"],
            childColumns = ["hostId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["hostId"])],
)
data class PortForward(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val hostId: Long,
    val nickname: String = "",
    val type: String = "local",
    val sourcePort: Int = 0,
    val dest: String = "",
    val destPort: Int = 0,
)