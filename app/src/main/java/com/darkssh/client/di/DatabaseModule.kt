package com.darkssh.client.di

import android.content.Context
import androidx.room.Room
import com.darkssh.client.data.DarkSHSDatabase
import com.darkssh.client.data.dao.HostDao
import com.darkssh.client.data.dao.KnownHostDao
import com.darkssh.client.data.dao.PortForwardDao
import com.darkssh.client.data.dao.PubkeyDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): DarkSHSDatabase =
        Room
            .databaseBuilder(context, DarkSHSDatabase::class.java, DarkSHSDatabase.DATABASE_NAME)
            .build()

    @Provides
    fun provideHostDao(database: DarkSHSDatabase): HostDao = database.hostDao()

    @Provides
    fun providePubkeyDao(database: DarkSHSDatabase): PubkeyDao = database.pubkeyDao()

    @Provides
    fun providePortForwardDao(database: DarkSHSDatabase): PortForwardDao = database.portForwardDao()

    @Provides
    fun provideKnownHostDao(database: DarkSHSDatabase): KnownHostDao = database.knownHostDao()
}
