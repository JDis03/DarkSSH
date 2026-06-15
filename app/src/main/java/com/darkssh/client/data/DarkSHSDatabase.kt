package com.darkssh.client.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.darkssh.client.data.dao.HostDao
import com.darkssh.client.data.dao.KnownHostDao
import com.darkssh.client.data.dao.PortForwardDao
import com.darkssh.client.data.dao.PubkeyDao
import com.darkssh.client.data.dao.TabDao
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.entity.KnownHost
import com.darkssh.client.data.entity.PortForward
import com.darkssh.client.data.entity.Pubkey
import com.darkssh.client.data.entity.Tab

@Database(
    entities = [Host::class, Pubkey::class, PortForward::class, KnownHost::class, Tab::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DarkSHSDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao

    abstract fun pubkeyDao(): PubkeyDao

    abstract fun portForwardDao(): PortForwardDao

    abstract fun knownHostDao(): KnownHostDao

    abstract fun tabDao(): TabDao

    companion object {
        const val DATABASE_NAME = "darkssh.db"
    }
}
