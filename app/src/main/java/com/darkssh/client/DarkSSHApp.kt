package com.darkssh.client

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.apache.sshd.common.util.OsUtils
import org.apache.sshd.common.util.io.PathUtils
import org.spongycastle.jce.provider.BouncyCastleProvider
import timber.log.Timber
import java.security.Security

@HiltAndroidApp
class DarkSSHApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        // Configure Apache SSHD for Android
        initializeSshdForAndroid()
        
        instance = this
    }
    
    private fun initializeSshdForAndroid() {
        // Register SpongyCastle (BouncyCastle for Android) as security provider
        // This is CRITICAL for Apache SSHD to work on Android
        Security.removeProvider("BC") // Remove built-in BC if exists
        Security.insertProviderAt(BouncyCastleProvider(), 1) // Insert SpongyCastle at highest priority
        
        Timber.i("Registered SpongyCastle security provider")
        
        // Set Android flag
        OsUtils.setAndroid(true)
        
        // Configure user home and working directory
        val filesPath = filesDir.toPath()
        System.setProperty("user.home", filesPath.toString())
        PathUtils.setUserHomeFolderResolver { filesPath }
        System.setProperty("user.dir", filesPath.toString())
        OsUtils.setCurrentWorkingDirectoryResolver { filesPath }
        System.setProperty("user.name", "android")
        OsUtils.setCurrentUser("android")
        
        // Log all security providers
        Timber.d("Security providers: ${Security.getProviders().joinToString { it.name }}")
    }

    companion object {
        lateinit var instance: DarkSSHApp
            private set
    }
}
