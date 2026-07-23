package com.example.universalvideodownloader

import android.app.Application
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg
import com.yausername.aria2c.Aria2c

class VideoDownloaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            Aria2c.getInstance().init(this)
            FirebaseManager.init(this)
            AdManager.init(this)
            Log.d("VideoDownloaderApp", "YoutubeDL, FFmpeg, Aria2c, Firebase, and AdManager initialized successfully")
        } catch (e: Exception) {
            Log.e("VideoDownloaderApp", "Failed to initialize app engines", e)
        }
    }
}
