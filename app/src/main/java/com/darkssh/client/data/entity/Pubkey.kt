package com.darkssh.client.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pubkeys")
data class Pubkey(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val nickname: String = "",
    val type: String = "",
    val privateKey: ByteArray = ByteArray(0),
    val publicKey: ByteArray = ByteArray(0),
    val encrypted: Boolean = false,
    val enabled: Boolean = true,
    val setupDate: Long? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Pubkey
        if (id != other.id) return false
        if (nickname != other.nickname) return false
        if (type != other.type) return false
        if (!privateKey.contentEquals(other.privateKey)) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (encrypted != other.encrypted) return false
        if (enabled != other.enabled) return false
        if (setupDate != other.setupDate) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + nickname.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + privateKey.contentHashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + encrypted.hashCode()
        result = 31 * result + enabled.hashCode()
        result = 31 * result + (setupDate?.hashCode() ?: 0)
        return result
    }
}