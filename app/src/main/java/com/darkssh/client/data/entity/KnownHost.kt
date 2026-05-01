package com.darkssh.client.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "known_hosts",
    foreignKeys = [
        ForeignKey(
            entity = Host::class,
            parentColumns = ["id"],
            childColumns = ["hostId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["hostId"]),
        Index(value = ["hostId", "hostKeyAlgo"]),
        Index(value = ["hostId", "hostKeyAlgo", "hostKey"], unique = true),
    ],
)
data class KnownHost(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val hostId: Long,
    val hostname: String = "",
    val port: Int = 22,
    val hostKeyAlgo: String = "",
    @ColumnInfo(name = "hostKey") val hostKey: String = "",
)