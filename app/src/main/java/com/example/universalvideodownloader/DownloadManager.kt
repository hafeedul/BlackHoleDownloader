package com.example.universalvideodownloader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.yausername.youtubedl_android.mapper.VideoInfo
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

    fun updateFetchState(newState: AppState) {
        _fetchState.value = newState
    }

    @Synchronized
    fun addDownloadItem(item: DownloadItem) {
        _downloads.value = _downloads.value.filter { it.id != item.id } + item
    }

    @Synchronized
    fun updateDownloadItem(id: String, transform: (DownloadItem) -> DownloadItem) {
        _downloads.value = _downloads.value.map { item ->
            if (item.id == id) transform(item) else item
        }
    }

    @Synchronized
    fun removeDownloadItem(id: String) {
        _downloads.value = _downloads.value.filter { it.id != id }
    }

    @Synchronized
    fun deleteFileAndRemoveItem(id: String) {
        val item = _downloads.value.find { it.id == id }
        item?.filePath?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
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
