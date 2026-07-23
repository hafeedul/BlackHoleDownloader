package com.example.universalvideodownloader

import java.util.UUID

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, ERROR }

data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val format: String,
    val progress: Float = 0f,
    val eta: Long = 0L,
    val speed: String = "Starting...",
    val downloadedSize: String = "",
    val totalSize: String = "",
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val errorMessage: String? = null,
    val filePath: String? = null,
    val dateAdded: Long = System.currentTimeMillis()
)
