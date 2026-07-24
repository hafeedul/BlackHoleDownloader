package com.example.universalvideodownloader

import android.app.Application
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg
import com.yausername.aria2c.Aria2c

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VideoDownloaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            Aria2c.getInstance().init(this)
            FirebaseManager.init(this)
            AdManager.init(this)
            DownloadManager.init(this)
            Log.d("VideoDownloaderApp", "Engines initialized successfully")
            
            val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            val lastUpdate = prefs.getLong("last_ytdlp_update", 0L)
            val now = System.currentTimeMillis()
            
            if (now - lastUpdate > 24 * 60 * 60 * 1000) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        YoutubeDL.getInstance().updateYoutubeDL(this@VideoDownloaderApp)
                        prefs.edit().putLong("last_ytdlp_update", now).apply()
                        Log.d("VideoDownloaderApp", "yt-dlp automatically updated to latest version")
                    } catch (e: Exception) {
                        Log.e("VideoDownloaderApp", "Failed to update yt-dlp", e)
                    }
                }
            } else {
                Log.d("VideoDownloaderApp", "yt-dlp update throttled (updated recently)")
            }
        } catch (e: Exception) {
            Log.e("VideoDownloaderApp", "Failed to initialize app engines", e)
        }
    }
}
