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

    fun init(context: Context) {
        try {
            analytics = FirebaseAnalytics.getInstance(context)
            logEvent("app_open", null)

            remoteConfig = FirebaseRemoteConfig.getInstance()
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600)
                .build()
            remoteConfig?.setConfigSettingsAsync(configSettings)

            val defaults = mapOf(
                "update_message" to "",
                "update_url" to ""
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
                    _updateMessage.value = msg
                    _updateUrl.value = url
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
