package com.example.universalvideodownloader

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FirebaseManager {
    private var analytics: FirebaseAnalytics? = null
    private var remoteConfig: FirebaseRemoteConfig? = null

    private val _updateMessage = MutableStateFlow("")
    val updateMessage: StateFlow<String> = _updateMessage.asStateFlow()

    private val _updateUrl = MutableStateFlow("")
    val updateUrl: StateFlow<String> = _updateUrl.asStateFlow()

    private val _forceUpdate = MutableStateFlow(false)
    val forceUpdate: StateFlow<Boolean> = _forceUpdate.asStateFlow()

    private val _latestVersionCode = MutableStateFlow(1)
    val latestVersionCode: StateFlow<Int> = _latestVersionCode.asStateFlow()

    fun init(context: Context) {
        try {
            analytics = FirebaseAnalytics.getInstance(context)
            logEvent("app_open", null)

            remoteConfig = FirebaseRemoteConfig.getInstance()
            // Set 0 seconds fetch interval for instant remote update checks
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0)
                .build()
            remoteConfig?.setConfigSettingsAsync(configSettings)

            val defaults = mapOf(
                "update_message" to "A new update is available with performance improvements!",
                "update_url" to "https://github.com/hafeedul/BlackHoleDownloader/raw/main/BlackHoleDownloader_v1.0.apk",
                "force_update" to false,
                "latest_version_code" to 1
            )
            remoteConfig?.setDefaultsAsync(defaults)
            fetchRemoteConfig()
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Firebase initialization skipped or failed: ${e.message}")
        }
    }

    fun logDownloadCompleted(title: String, format: String) {
        try {
            val bundle = Bundle().apply {
                putString("video_title", title)
                putString("video_format", format)
            }
            analytics?.logEvent("video_downloaded", bundle)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun logEvent(eventName: String, params: Bundle? = null) {
        try {
            analytics?.logEvent(eventName, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchRemoteConfig() {
        try {
            remoteConfig?.fetchAndActivate()?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val msg = remoteConfig?.getString("update_message") ?: ""
                    val url = remoteConfig?.getString("update_url") ?: ""
                    val force = remoteConfig?.getBoolean("force_update") ?: false
                    val version = remoteConfig?.getLong("latest_version_code")?.toInt() ?: 1

                    _updateMessage.value = msg
                    _updateUrl.value = url
                    _forceUpdate.value = force
                    _latestVersionCode.value = version

                    Log.d("FirebaseManager", "RemoteConfig updated -> force: $force, version: $version, url: $url")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
