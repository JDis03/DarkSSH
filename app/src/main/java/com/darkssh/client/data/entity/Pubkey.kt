package com.darkssh.client.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pubkeys")
data class Pubkey(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val nickname: String = "",
    val type: String = "",
    @ColumnInfo(name = "privateKey", typeAffinity = ColumnInfo.BLOB)
    val privateKey: ByteArray? = null,
    @ColumnInfo(name = "publicKey", typeAffinity = ColumnInfo.BLOB)
    val publicKey: ByteArray = ByteArray(0),
    val encrypted: Boolean = false,
    val startup: Boolean = false,
    val confirmation: Boolean = false,
    val setupDate: Long? = null,
    @ColumnInfo(defaultValue = "EXPORTABLE")
    val storageType: String = "EXPORTABLE",
    val keystoreAlias: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Pubkey
        if (id != other.id) return false
        if (nickname != other.nickname) return false
        if (type != other.type) return false
        if (privateKey != null) {
            if (other.privateKey == null) return false
            if (!privateKey.contentEquals(other.privateKey)) return false
        } else if (other.privateKey != null) {
            return false
        }
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (encrypted != other.encrypted) return false
        if (startup != other.startup) return false
        if (confirmation != other.confirmation) return false
        if (setupDate != other.setupDate) return false
        if (storageType != other.storageType) return false
        if (keystoreAlias != other.keystoreAlias) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + nickname.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (privateKey?.contentHashCode() ?: 0)
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + encrypted.hashCode()
        result = 31 * result + startup.hashCode()
        result = 31 * result + confirmation.hashCode()
        result = 31 * result + (setupDate?.hashCode() ?: 0)
        result = 31 * result + storageType.hashCode()
        result = 31 * result + (keystoreAlias?.hashCode() ?: 0)
        return result
    }
}