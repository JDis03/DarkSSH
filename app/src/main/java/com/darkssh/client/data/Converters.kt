package com.darkssh.client.data

import androidx.room.TypeConverter
import com.darkssh.client.data.entity.TabType
import com.darkssh.client.data.model.OsType

class Converters {
    @TypeConverter
    fun fromLongList(value: List<Long>): String = value.joinToString(",")

    @TypeConverter
    fun toLongList(value: String): List<Long> =
        if (value.isBlank()) {
            emptyList()
        } else {
            value.split(",").map { it.trim().toLong() }
        }

    @TypeConverter
    fun fromTabType(value: TabType): String = value.name

    @TypeConverter
    fun toTabType(value: String): TabType = TabType.valueOf(value)
    
    @TypeConverter
    fun fromOsType(value: OsType): String = value.name
    
    @TypeConverter
    fun toOsType(value: String): OsType = try {
        OsType.valueOf(value)
    } catch (e: IllegalArgumentException) {
        OsType.UNKNOWN
    }
}
