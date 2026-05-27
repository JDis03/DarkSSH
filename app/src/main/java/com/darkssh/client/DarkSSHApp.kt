package com.darkssh.client

import android.app.Application
import com.darkssh.client.util.FileLoggingTree
import dagger.hilt.android.HiltAndroidApp
import org.apache.sshd.common.util.OsUtils
import org.apache.sshd.common.util.io.PathUtils
import org.spongycastle.jce.provider.BouncyCastleProvider
import timber.log.Timber
import java.security.Security

@HiltAndroidApp
class DarkSSHApp : Application() {
    
    private var fileLoggingTree: FileLoggingTree? = null
    
    override fun onCreate() {
        super.onCreate()
        
        // Always plant FileLoggingTree for debug logs
        fileLoggingTree = FileLoggingTree(this)
        Timber.plant(fileLoggingTree!!)
        
        // Also plant DebugTree for logcat in debug builds
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.i("DarkSSH", "App started - version ${BuildConfig.VERSION_NAME}")
        
        // Configure Apache SSHD for Android
        initializeSshdForAndroid()
        
        instance = this
    }
    
    override fun onTerminate() {
        fileLoggingTree?.close()
        super.onTerminate()
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
        
        fun getFileLoggingTree(): FileLoggingTree? = instance.fileLoggingTree
    }
}
