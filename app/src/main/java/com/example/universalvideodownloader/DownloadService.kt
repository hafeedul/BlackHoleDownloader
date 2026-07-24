package com.example.universalvideodownloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class DownloadService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeProcessIds = ConcurrentHashMap<String, String>()
    
    private val CHANNEL_ID = "DownloadChannel"
    private val NOTIFICATION_ID = 1001

    private val sizeRegex = Regex("""\[download\]\s+([\d.]+)%\s+of\s+~?([\d.]+\s*[a-zA-Z]+)""")
    private val speedRegex = Regex("""at\s+([\d.]+\s*[a-zA-Z]+/s)""")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val downloadId = intent?.getStringExtra("DOWNLOAD_ID")
        
        when (action) {
            "START_DOWNLOAD" -> {
                if (downloadId != null) {
                    startForeground(NOTIFICATION_ID, buildNotification("Downloading videos..."))
                    startDownloadTask(downloadId)
                }
            }
            "RESUME_DOWNLOAD" -> {
                if (downloadId != null) {
                    startForeground(NOTIFICATION_ID, buildNotification("Downloading videos..."))
                    startDownloadTask(downloadId)
                }
            }
            "PAUSE_DOWNLOAD" -> {
                if (downloadId != null) {
                    pauseDownloadTask(downloadId)
                    updateNotification("Downloads Paused")
                }
            }
            "CANCEL_DOWNLOAD" -> {
                if (downloadId != null) {
                    cancelDownloadTask(downloadId)
                }
            }
            "PAUSE_ALL" -> {
                val list = DownloadManager.downloads.value
                list.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }.forEach {
                    pauseDownloadTask(it.id)
                }
                updateNotification("Downloads Paused")
            }
            "RESUME_ALL" -> {
                startForeground(NOTIFICATION_ID, buildNotification("Downloading videos..."))
                val list = DownloadManager.downloads.value
                list.filter { it.status == DownloadStatus.PAUSED }.forEach {
                    startDownloadTask(it.id)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownloadTask(downloadId: String) {
        val item = DownloadManager.getItem(downloadId) ?: return
        
        // Kill existing process and job for this item if running
        val prevProcessId = activeProcessIds.remove(downloadId)
        if (prevProcessId != null) {
            try {
                YoutubeDL.getInstance().destroyProcessById(prevProcessId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        activeJobs[downloadId]?.cancel()

        val processId = "Proc_${downloadId.take(8)}_${System.currentTimeMillis()}"
        activeProcessIds[downloadId] = processId

        // Set initial downloading state
        DownloadManager.updateDownloadItem(downloadId) {
            it.copy(
                status = DownloadStatus.DOWNLOADING,
                progress = 0f,
                speed = "Connecting..."
            )
        }

        val job = serviceScope.launch {
            try {
                val baseTmpDir = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "UniversalVideoDownloader_tmp")
                val tmpDir = File(baseTmpDir, downloadId)
                if (!tmpDir.exists()) tmpDir.mkdirs()

                val lowerUrl = item.url.lowercase()
                val isDirectStream = lowerUrl.contains("tiktokcdn") || lowerUrl.contains("cdninstagram") || lowerUrl.contains("fbcdn") || (lowerUrl.contains(".mp4") && !lowerUrl.contains("youtube.com") && !lowerUrl.contains("youtu.be"))

                if (isDirectStream) {
                    downloadDirectStreamFile(downloadId, item, tmpDir)
                    return@launch
                }

                val request = YoutubeDLRequest(item.url)
                request.addOption("--no-playlist")
                request.addOption("--no-check-certificate")
                request.addOption("--geo-bypass")
                request.addOption("--continue")
                request.addOption("--no-part")
                // Safe %(id)s filename pattern prevents backslash/slash path corruption in titles
                request.addOption("-o", tmpDir.absolutePath + "/%(id)s.%(ext)s")
                request.addOption("--merge-output-format", "mp4")

                if (lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be")) {
                    request.addOption("--extractor-args", "youtube:player_client=ios,mweb")
                    request.addOption("--user-agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                } else if (lowerUrl.contains("facebook.com") || lowerUrl.contains("fb.")) {
                    request.addOption("--user-agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                    request.addOption("--referer", "https://www.facebook.com/")
                } else if (lowerUrl.contains("instagram.com")) {
                    request.addOption("--user-agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                    request.addOption("--referer", "https://www.instagram.com/")
                } else if (lowerUrl.contains("tiktok.com")) {
                    request.addOption("--user-agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                    request.addOption("--referer", "https://www.tiktok.com/")
                } else {
                    request.addOption("--user-agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                    request.addOption("--referer", item.url)
                }

                // Prioritize universal H.264 (avc1) codec for 100% hardware decoding compatibility across all Android devices
                val actualFormat = if (item.format.isNotBlank() && item.format != "bestvideo+bestaudio/best") {
                    item.format
                } else {
                    "bestvideo[ext=mp4][vcodec^=avc1]+bestaudio[ext=m4a]/bestvideo[ext=mp4]+bestaudio/bestvideo+bestaudio/best"
                }
                request.addOption("-f", actualFormat)

                var currentStatus = "Downloading..."
                var lastNotificationUpdate = System.currentTimeMillis()
                var parsedTotalSize = ""
                var parsedDownloadedSize = ""

                withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, line ->
                        var safeProgress = progress.coerceAtLeast(0f)
                        val sizes = parseSizes(line, safeProgress)
                        if (sizes != null) {
                            safeProgress = sizes.first
                            parsedDownloadedSize = sizes.second.first
                            parsedTotalSize = sizes.second.second
                        }

                        val speedMatch = speedRegex.find(line)
                        if (speedMatch != null) {
                            currentStatus = speedMatch.groupValues[1]
                        } else if (line.contains("[Merger]") || line.contains("Merging formats into")) {
                            currentStatus = "Merging Video & Audio..."
                        }

                        // Check DRM protection logs
                        if (line.contains("DRM", ignoreCase = true) || line.contains("Widevine", ignoreCase = true) || line.contains("encrypted", ignoreCase = true)) {
                            throw Exception("This video is protected by DRM encryption and cannot be downloaded.")
                        }

                        // Verify item still exists before updating
                        val currentItem = DownloadManager.getItem(downloadId)
                        if (currentItem != null) {
                            DownloadManager.updateDownloadItem(downloadId) {
                                it.copy(
                                    status = DownloadStatus.DOWNLOADING,
                                    progress = safeProgress,
                                    eta = etaInSeconds,
                                    speed = currentStatus,
                                    downloadedSize = parsedDownloadedSize.ifEmpty { it.downloadedSize },
                                    totalSize = parsedTotalSize.ifEmpty { it.totalSize }
                                )
                            }
                        }

                        if (System.currentTimeMillis() - lastNotificationUpdate > 1000) {
                            lastNotificationUpdate = System.currentTimeMillis()
                            val notifText = if (parsedTotalSize.isNotEmpty()) {
                                "Downloading: ${"%.0f".format(safeProgress)}% ($parsedDownloadedSize / $parsedTotalSize)"
                            } else {
                                "Downloading: ${"%.0f".format(safeProgress)}%"
                            }
                            updateNotification(notifText)
                        }
                    }
                }

                // Finalize file
                val downloadedFile = tmpDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }?.maxByOrNull { it.lastModified() }
                var finalPath = downloadedFile?.absolutePath

                if (downloadedFile != null) {
                    val sanitizedTitle = item.title.replace(Regex("""[^\w\s.-]"""), "_").trim()
                    val outputFileName = if (sanitizedTitle.isNotBlank()) "${sanitizedTitle}.${downloadedFile.extension}" else downloadedFile.name
                    
                    val uriPath = saveToPublicDownloads(this@DownloadService, downloadedFile, outputFileName, "video/mp4")
                    if (uriPath != null) {
                        finalPath = uriPath
                    }
                    
                    val fileSizeFormatted = "%.1f MB".format((if (downloadedFile.exists()) downloadedFile.length() else 0L) / (1024f * 1024f))
                    if (parsedTotalSize.isEmpty()) parsedTotalSize = fileSizeFormatted
                }

                try {
                    tmpDir.deleteRecursively()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                FirebaseManager.logDownloadCompleted(item.title, item.format)

                val currentItem = DownloadManager.getItem(downloadId)
                if (currentItem != null) {
                    DownloadManager.updateDownloadItem(downloadId) {
                        it.copy(
                            status = DownloadStatus.COMPLETED,
                            progress = 100f,
                            speed = "Completed",
                            downloadedSize = parsedTotalSize,
                            totalSize = parsedTotalSize,
                            filePath = finalPath ?: tmpDir.absolutePath
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e("DownloadService", "Download failed for $downloadId", e)
                val currentItem = DownloadManager.getItem(downloadId)
                if (currentItem != null && currentItem.status != DownloadStatus.PAUSED) {
                    val errorMsg = if (e.localizedMessage?.contains("DRM", ignoreCase = true) == true) {
                        "This video is protected by DRM encryption and cannot be downloaded."
                    } else {
                        e.localizedMessage ?: "Download failed"
                    }
                    DownloadManager.updateDownloadItem(downloadId) {
                        it.copy(
                            status = DownloadStatus.ERROR,
                            errorMessage = errorMsg
                        )
                    }
                }
            } finally {
                activeProcessIds.remove(downloadId)
                activeJobs.remove(downloadId)
                checkStopSelf()
            }
        }
        activeJobs[downloadId] = job
    }

    private suspend fun downloadDirectStreamFile(downloadId: String, item: DownloadItem, tmpDir: File) = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(item.url)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            val hostUrl = url.toString()
            if (hostUrl.contains("tiktokcdn")) {
                connection.setRequestProperty("Referer", "https://www.tiktok.com/")
            } else if (hostUrl.contains("cdninstagram")) {
                connection.setRequestProperty("Referer", "https://www.instagram.com/")
            } else if (hostUrl.contains("fbcdn")) {
                connection.setRequestProperty("Referer", "https://www.facebook.com/")
            }
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw Exception("HTTP Error: $responseCode ${connection.responseMessage}")
            }

            val fileLength = connection.contentLengthLong
            val input = connection.inputStream
            val targetFile = File(tmpDir, "${downloadId.take(8)}.mp4")
            val output = targetFile.outputStream()

            val buffer = ByteArray(16384)
            var totalBytesRead = 0L
            var bytesRead: Int
            var lastUpdate = System.currentTimeMillis()

            while (input.read(buffer).also { bytesRead = it } != -1) {
                val currentItem = DownloadManager.getItem(downloadId)
                if (currentItem == null || currentItem.status == DownloadStatus.PAUSED) break

                output.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastUpdate > 300) {
                    lastUpdate = now
                    val progress = if (fileLength > 0) (totalBytesRead.toFloat() / fileLength.toFloat()) * 100f else 50f
                    val downloadedMb = "%.1f MB".format(totalBytesRead / (1024f * 1024f))
                    val totalMb = if (fileLength > 0) "%.1f MB".format(fileLength / (1024f * 1024f)) else "MB"

                    DownloadManager.updateDownloadItem(downloadId) {
                        it.copy(
                            status = DownloadStatus.DOWNLOADING,
                            progress = progress,
                            speed = "Downloading...",
                            downloadedSize = downloadedMb,
                            totalSize = totalMb
                        )
                    }
                }
            }
            output.flush()
            output.close()
            input.close()

            val sanitizedTitle = item.title.replace(Regex("""[^\w\s.-]"""), "_").trim()
            val outputFileName = if (sanitizedTitle.isNotBlank()) "${sanitizedTitle}.mp4" else "${targetFile.name}"
            
            val targetLength = targetFile.length()
            val uriPath = saveToPublicDownloads(this@DownloadService, targetFile, outputFileName, "video/mp4")
            val finalPath = uriPath ?: targetFile.absolutePath

            val fileSizeFormatted = "%.1f MB".format(targetLength / (1024f * 1024f))
            DownloadManager.updateDownloadItem(downloadId) {
                it.copy(
                    status = DownloadStatus.COMPLETED,
                    progress = 100f,
                    speed = "Completed",
                    downloadedSize = fileSizeFormatted,
                    totalSize = fileSizeFormatted,
                    filePath = finalPath
                )
            }
            try {
                tmpDir.deleteRecursively()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } catch (e: Exception) {
            Log.e("DownloadService", "Direct download error", e)
            DownloadManager.updateDownloadItem(downloadId) {
                it.copy(status = DownloadStatus.ERROR, errorMessage = e.localizedMessage ?: "Download failed")
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseSizes(line: String, progress: Float): Pair<Float, Pair<String, String>>? {
        return try {
            val match = sizeRegex.find(line)
            if (match != null && match.groupValues.size >= 3) {
                val pctParsed = match.groupValues[1].toFloatOrNull() ?: progress
                val totalStr = match.groupValues[2].trim()
                val totalMb = parseToMb(totalStr)
                if (totalMb > 0) {
                    val downloadedMb = (pctParsed / 100f) * totalMb
                    val downloadedStr = "%.1f MB".format(downloadedMb)
                    val totalFormatted = "%.1f MB".format(totalMb)
                    return Pair(pctParsed, Pair(downloadedStr, totalFormatted))
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseToMb(sizeStr: String): Float {
        val clean = sizeStr.uppercase().trim()
        val num = clean.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: return 0f
        return when {
            clean.contains("G") -> num * 1024f
            clean.contains("M") -> num
            clean.contains("K") -> num / 1024f
            else -> num
        }
    }

    private fun pauseDownloadTask(downloadId: String) {
        val procId = activeProcessIds.remove(downloadId)
        if (procId != null) {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    YoutubeDL.getInstance().destroyProcessById(procId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        activeJobs[downloadId]?.cancel()
        activeJobs.remove(downloadId)
        
        val currentItem = DownloadManager.getItem(downloadId)
        if (currentItem != null) {
            DownloadManager.updateDownloadItem(downloadId) {
                it.copy(status = DownloadStatus.PAUSED, speed = "Paused")
            }
        }
        checkStopSelf()
    }

    private fun cancelDownloadTask(downloadId: String) {
        val procId = activeProcessIds.remove(downloadId)
        if (procId != null) {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    YoutubeDL.getInstance().destroyProcessById(procId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        activeJobs[downloadId]?.cancel()
        activeJobs.remove(downloadId)
        DownloadManager.removeDownloadItem(downloadId)
        checkStopSelf()
    }

    private fun checkStopSelf() {
        val downloads = DownloadManager.downloads.value
        val hasActive = downloads.any { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
        val hasPaused = downloads.any { it.status == DownloadStatus.PAUSED }

        if (!hasActive && !hasPaused && activeJobs.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val downloads = DownloadManager.downloads.value
        val isAnyActive = downloads.any { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
        val isAnyPaused = downloads.any { it.status == DownloadStatus.PAUSED }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Black Hole Downloader")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(isAnyActive)
            .setContentIntent(pendingIntent)

        if (isAnyActive) {
            val pauseIntent = Intent(this, DownloadService::class.java).apply { action = "PAUSE_ALL" }
            val pausePending = PendingIntent.getService(this, 10, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(android.R.drawable.ic_media_pause, "Pause All", pausePending)
        } else if (isAnyPaused) {
            val resumeIntent = Intent(this, DownloadService::class.java).apply { action = "RESUME_ALL" }
            val resumePending = PendingIntent.getService(this, 11, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(android.R.drawable.ic_media_play, "Resume All", resumePending)
        }

        return builder.build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun saveToPublicDownloads(context: Context, tempFile: File, displayName: String, mimeType: String): String? {
        if (!tempFile.exists()) return null
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/UniversalVideoDownloader")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    
                    tempFile.delete()
                    return uri.toString()
                }
            } else {
                val publicDownloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val publicOutDir = File(publicDownloadsDir, "UniversalVideoDownloader")
                if (!publicOutDir.exists()) publicOutDir.mkdirs()
                
                val destFile = File(publicOutDir, displayName)
                if (tempFile.renameTo(destFile)) {
                    return destFile.absolutePath
                } else {
                    tempFile.copyTo(destFile, overwrite = true)
                    tempFile.delete()
                    return destFile.absolutePath
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadService", "Failed to save file to public downloads", e)
        }
        return null
    }
}
