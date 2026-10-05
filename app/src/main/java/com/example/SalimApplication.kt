package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.webrtc.PeerConnectionFactory

class SalimApplication : Application() {

    companion object {
        private const val TAG = "SalimApplication"
        lateinit var instance: SalimApplication
            private set

        var isFirebaseConfigured: Boolean = false
            private set

        var firebaseErrorMessage: String? = null
            private set

        var isWebRtcAvailable: Boolean = false
            private set

        var webRtcErrorMessage: String? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Install safe crash guard handler
        installSafeCrashGuard()

        // 1. Crash-proof Firebase Initialization
        initFirebaseSafely()

        // 2. Crash-proof WebRTC Native Initialization
        initWebRtcSafely()
    }

    private fun installSafeCrashGuard() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "FATAL GUARD: Uncaught exception in thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun initFirebaseSafely(): Boolean {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                // Initialize with explicit options from Firebase configuration
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:428293373821:android:764070a9c93f569c629614")
                    .setApiKey("AIzaSyBdiTj7YRtZ5ncYv6few_Gfaw9h-mbqU3w")
                    .setProjectId("salim-x-ubaid")
                    .setStorageBucket("salim-x-ubaid.firebasestorage.app")
                    .setGcmSenderId("428293373821")
                    .build()
                FirebaseApp.initializeApp(this, options)
            }
            isFirebaseConfigured = true
            firebaseErrorMessage = null
            Log.i(TAG, "Firebase initialized successfully")
            return true
        } catch (t: Throwable) {
            Log.e(TAG, "Firebase safe initialization failed: ${t.message}", t)
            isFirebaseConfigured = false
            firebaseErrorMessage = t.localizedMessage ?: "Firebase configuration missing or invalid"
            return false
        }
    }

    private fun initWebRtcSafely() {
        try {
            val initOptions = PeerConnectionFactory.InitializationOptions.builder(this)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)
            isWebRtcAvailable = true
            webRtcErrorMessage = null
            Log.i(TAG, "WebRTC peer connection factory initialized successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "WebRTC native initialization failed: ${t.message}", t)
            isWebRtcAvailable = false
            webRtcErrorMessage = t.localizedMessage ?: "WebRTC native library could not be loaded"
        }
    }
}
