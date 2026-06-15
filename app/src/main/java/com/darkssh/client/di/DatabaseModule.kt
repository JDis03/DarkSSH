package com.darkssh.client.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.darkssh.client.data.DarkSHSDatabase
import com.darkssh.client.data.dao.HostDao
import com.darkssh.client.data.dao.KnownHostDao
import com.darkssh.client.data.dao.PortForwardDao
import com.darkssh.client.data.dao.PubkeyDao
import com.darkssh.client.data.dao.TabDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tabs (
                        id TEXT PRIMARY KEY NOT NULL,
                        type TEXT NOT NULL,
                        hostId INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        themeId TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tabs_hostId ON tabs (hostId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tabs_position ON tabs (position)")
            }
        }
    
    private val MIGRATION_2_3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add osType column to tabs table (defaults to UNKNOWN)
                db.execSQL("ALTER TABLE tabs ADD COLUMN osType TEXT NOT NULL DEFAULT 'UNKNOWN'")
            }
        }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): DarkSHSDatabase =
        Room
            .databaseBuilder(context, DarkSHSDatabase::class.java, DarkSHSDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideHostDao(database: DarkSHSDatabase): HostDao = database.hostDao()

    @Provides
    fun providePubkeyDao(database: DarkSHSDatabase): PubkeyDao = database.pubkeyDao()

    @Provides
    fun providePortForwardDao(database: DarkSHSDatabase): PortForwardDao = database.portForwardDao()

    @Provides
    fun provideKnownHostDao(database: DarkSHSDatabase): KnownHostDao = database.knownHostDao()

    @Provides
    fun provideTabDao(database: DarkSHSDatabase): TabDao = database.tabDao()
}
