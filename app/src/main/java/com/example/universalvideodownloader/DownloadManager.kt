package com.example.universalvideodownloader

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.yausername.youtubedl_android.mapper.VideoInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

sealed class AppState {
    object Idle : AppState()
    object Fetching : AppState()
    data class InfoReady(val info: VideoInfo) : AppState()
    data class Error(val message: String) : AppState()
}

object DownloadManager {
    private val _fetchState = MutableStateFlow<AppState>(AppState.Idle)
    val fetchState: StateFlow<AppState> = _fetchState.asStateFlow()

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("download_manager", Context.MODE_PRIVATE)
        val jsonStr = prefs?.getString("downloads", null)
        if (jsonStr != null) {
            try {
                val array = JSONArray(jsonStr)
                val items = mutableListOf<DownloadItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val statusStr = obj.optString("status", "QUEUED")
                    val originalStatus = try { DownloadStatus.valueOf(statusStr) } catch (e: Exception) { DownloadStatus.QUEUED }
                    val finalStatus = if (originalStatus == DownloadStatus.DOWNLOADING || originalStatus == DownloadStatus.QUEUED) DownloadStatus.PAUSED else originalStatus
                    
                    items.add(
                        DownloadItem(
                            id = obj.getString("id"),
                            url = obj.getString("url"),
                            title = obj.getString("title"),
                            thumbnailUrl = if (obj.has("thumbnailUrl")) obj.getString("thumbnailUrl") else null,
                            format = obj.getString("format"),
                            progress = obj.optDouble("progress", 0.0).toFloat(),
                            eta = obj.optLong("eta", 0L),
                            speed = if (finalStatus == DownloadStatus.PAUSED) "Paused" else obj.optString("speed", ""),
                            downloadedSize = obj.optString("downloadedSize", ""),
                            totalSize = obj.optString("totalSize", ""),
                            status = finalStatus,
                            errorMessage = if (obj.has("errorMessage")) obj.getString("errorMessage") else null,
                            filePath = if (obj.has("filePath")) obj.getString("filePath") else null,
                            dateAdded = obj.optLong("dateAdded", System.currentTimeMillis())
                        )
                    )
                }
                _downloads.value = items
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun save() {
        try {
            val array = JSONArray()
            _downloads.value.forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("url", item.url)
                    put("title", item.title)
                    if (item.thumbnailUrl != null) put("thumbnailUrl", item.thumbnailUrl)
                    put("format", item.format)
                    put("progress", item.progress.toDouble())
                    put("eta", item.eta)
                    put("speed", item.speed)
                    put("downloadedSize", item.downloadedSize)
                    put("totalSize", item.totalSize)
                    put("status", item.status.name)
                    if (item.errorMessage != null) put("errorMessage", item.errorMessage)
                    if (item.filePath != null) put("filePath", item.filePath)
                    put("dateAdded", item.dateAdded)
                }
                array.put(obj)
            }
            prefs?.edit()?.putString("downloads", array.toString())?.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateFetchState(newState: AppState) {
        _fetchState.value = newState
    }

    @Synchronized
    fun addDownloadItem(item: DownloadItem) {
        _downloads.value = _downloads.value.filter { it.id != item.id } + item
        save()
    }

    @Synchronized
    fun updateDownloadItem(id: String, transform: (DownloadItem) -> DownloadItem) {
        _downloads.value = _downloads.value.map { item ->
            if (item.id == id) transform(item) else item
        }
        save()
    }

    @Synchronized
    fun removeDownloadItem(id: String) {
        _downloads.value = _downloads.value.filter { it.id != id }
        save()
    }

    @Synchronized
    fun deleteFileAndRemoveItem(id: String) {
        val item = _downloads.value.find { it.id == id }
        item?.filePath?.let { path ->
            try {
                if (path.startsWith("content://")) {
                    // We shouldn't delete media store files without explicit user consent on API 30+ 
                    // but we will remove it from the list
                } else {
                    val file = File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        removeDownloadItem(id)
    }

    fun getItem(id: String): DownloadItem? {
        return _downloads.value.find { it.id == id }
    }
}
