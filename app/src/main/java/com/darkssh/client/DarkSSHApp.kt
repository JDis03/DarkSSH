package com.darkssh.client

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DarkSSHApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: DarkSSHApp
            private set
    }
}