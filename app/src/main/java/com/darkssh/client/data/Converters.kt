package com.darkssh.client.data

import androidx.room.TypeConverter
import com.darkssh.client.data.entity.TabType

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
}
