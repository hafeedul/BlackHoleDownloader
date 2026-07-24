package com.example.universalvideodownloader

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

class DownloaderViewModel : ViewModel() {
    val fetchState = DownloadManager.fetchState
    val downloads = DownloadManager.downloads

    fun fetchVideoInfo(context: Context, url: String) {
        viewModelScope.launch {
            DownloadManager.updateFetchState(AppState.Fetching)
            val cleanUrl = prepareUrl(url)
            val lowerUrl = cleanUrl.lowercase()

            if (lowerUrl.contains("tiktok")) {
                val tiktokResult = getDirectTikTokMp4(cleanUrl)
                if (tiktokResult != null) {
                    val (playUrl, videoTitle) = tiktokResult
                    startDownload(context, playUrl, videoTitle, null, "b/best")
                    return@launch
                }
            }

            if (lowerUrl.contains("instagram.com")) {
                startDirectDownload(context, cleanUrl)
                return@launch
            }

            try {
                val info = withContext(Dispatchers.IO) {
                    val request = YoutubeDLRequest(cleanUrl)
                    request.addOption("--no-playlist")
                    request.addOption("--no-check-certificate")
                    request.addOption("--geo-bypass")
                    if (lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be")) {
                        request.addOption("--extractor-args", "youtube:player_client=ios,mweb")
                        request.addOption("--user-agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                    } else if (lowerUrl.contains("facebook.com") || lowerUrl.contains("fb.")) {
                        request.addOption("--user-agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                        request.addOption("--referer", "https://www.facebook.com/")
                    } else if (lowerUrl.contains("instagram.com")) {
                        request.addOption("--user-agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                        request.addOption("--referer", "https://www.instagram.com/")
                    } else {
                        request.addOption("--user-agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                        request.addOption("--referer", cleanUrl)
                    }
                    YoutubeDL.getInstance().getInfo(request)
                }
                DownloadManager.updateFetchState(AppState.InfoReady(info))
            } catch (e: Exception) {
                Log.e("DownloaderViewModel", "Fetch info failed for $cleanUrl", e)
                DownloadManager.updateFetchState(AppState.Error(e.localizedMessage ?: "Failed to fetch video info"))
            }
        }
    }

    private suspend fun getDirectTikTokMp4(tiktokUrl: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val apiUrl = "https://www.tikwm.com/api/?url=${URLEncoder.encode(tiktokUrl, "UTF-8")}"
            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            connection.connectTimeout = 6000
            connection.readTimeout = 6000

            val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObj = JSONObject(jsonStr)
            if (jsonObj.optInt("code") == 0) {
                val dataObj = jsonObj.optJSONObject("data")
                if (dataObj != null) {
                    val playUrl = dataObj.optString("play")
                    val title = dataObj.optString("title", "TikTok Video")
                    if (playUrl.isNotBlank()) {
                        Log.d("DownloaderViewModel", "Extracted TikTok direct MP4: $playUrl (Title: $title)")
                        return@withContext Pair(playUrl, title)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DownloaderViewModel", "TikTok API extraction failed: ${e.message}")
        }
        return@withContext null
    }

    private fun extractInstagramShortcode(url: String): String? {
        val regex1 = Regex("""(?:https?://)?(?:www\.)?(?:instagram\.com|instagr\.am)?/(?:p|reel|tv|reels|share/reel)/([A-Za-z0-9_-]+)""")
        val match1 = regex1.find(url)
        if (match1 != null) return match1.groupValues[1]

        val regex2 = Regex("""([A-Za-z0-9_-]{3,40})/?\?igsh=""")
        val match2 = regex2.find(url)
        if (match2 != null) return match2.groupValues[1]

        return null
    }

    private suspend fun getDirectInstagramMp4(shortcode: String): String? = withContext(Dispatchers.IO) {
        try {
            val embedUrl = "https://www.instagram.com/p/$shortcode/embed/captioned/"
            val connection = URL(embedUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            val html = connection.inputStream.bufferedReader().use { it.readText() }

            val mp4Regex = Regex("""https?:[\\/A-Za-z0-9_.\-~%?&=]+(?:\.mp4|bytestream)[\\/A-Za-z0-9_.\-~%?&=]*""")
            val match = mp4Regex.find(html)
            if (match != null) {
                var videoUrl = match.value
                videoUrl = videoUrl.replace("\\/", "/").replace("\\u0026", "&").replace("&amp;", "&")
                Log.d("DownloaderViewModel", "Extracted direct Instagram MP4 URL: $videoUrl")
                return@withContext videoUrl
            }
        } catch (e: Exception) {
            Log.e("DownloaderViewModel", "Direct Instagram HTML extraction failed: ${e.message}")
        }
        return@withContext null
    }

    private suspend fun getDirectMediaStream(webUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(webUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            connection.setRequestProperty("Referer", webUrl)
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            val html = connection.inputStream.bufferedReader().use { it.readText() }

            // Extract direct video source tag src
            val sourceRegex = Pattern.compile("""<source[^>]+src=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
            val sourceMatch = sourceRegex.matcher(html)
            if (sourceMatch.find()) {
                var src = sourceMatch.group(1)
                if (src != null) {
                    if (src.startsWith("//")) src = "https:$src"
                    return@withContext src
                }
            }

            // Extract raw MP4 or M3U8 URLs
            val mp4Regex = Regex("""https?:[\\/A-Za-z0-9_.\-~%?&=]+(?:\.mp4|\.m3u8|bytestream)[\\/A-Za-z0-9_.\-~%?&=]*""")
            val match = mp4Regex.find(html)
            if (match != null) {
                var videoUrl = match.value
                videoUrl = videoUrl.replace("\\/", "/").replace("\\u0026", "&").replace("&amp;", "&")
                return@withContext videoUrl
            }
        } catch (e: Exception) {
            Log.e("DownloaderViewModel", "Generic stream extraction failed: ${e.message}")
        }
        return@withContext null
    }

    private fun prepareUrl(rawUrl: String): String {
        var cleaned = rawUrl.trim().replace("\\", "").removeSurrounding("\"", "\"").removeSurrounding("'", "'")
        val shortcode = extractInstagramShortcode(cleaned)
        if (shortcode != null) {
            return "https://www.instagram.com/p/$shortcode/"
        }
        if (!cleaned.startsWith("http://") && !cleaned.startsWith("https://")) {
            cleaned = "https://$cleaned"
        }
        return cleaned
    }

    fun startDownload(context: Context, url: String, title: String, thumbnailUrl: String?, formatQuery: String) {
        val item = DownloadItem(
            url = prepareUrl(url),
            title = title,
            thumbnailUrl = thumbnailUrl,
            format = formatQuery,
            status = DownloadStatus.QUEUED
        )
        DownloadManager.addDownloadItem(item)

        val intent = Intent(context, DownloadService::class.java).apply {
            action = "START_DOWNLOAD"
            putExtra("DOWNLOAD_ID", item.id)
        }
        startServiceSafe(context, intent)
        DownloadManager.updateFetchState(AppState.Idle)
    }

    fun startDirectDownload(context: Context, url: String) {
        viewModelScope.launch {
            val cleanUrl = prepareUrl(url)
            val lowerUrl = cleanUrl.lowercase()

            if (lowerUrl.contains("tiktok")) {
                val tiktokResult = getDirectTikTokMp4(cleanUrl)
                if (tiktokResult != null) {
                    val (playUrl, videoTitle) = tiktokResult
                    startDownload(context, playUrl, videoTitle, null, "b/best")
                    return@launch
                }
            }

            var finalUrl = cleanUrl

            val shortcode = extractInstagramShortcode(cleanUrl)
            if (shortcode != null) {
                val directMp4 = getDirectInstagramMp4(shortcode)
                if (directMp4 != null) {
                    finalUrl = directMp4
                } else {
                    finalUrl = "https://www.instagram.com/p/$shortcode/embed/captioned/"
                }
            } else {
                var extractedMedia = getDirectMediaStream(cleanUrl)
                if (extractedMedia == null) {
                    extractedMedia = WebViewExtractor.extractMediaUrl(context, cleanUrl)
                }
                if (extractedMedia != null) {
                    finalUrl = extractedMedia
                }
            }

            val titleText = if (lowerUrl.contains("tiktok")) "TikTok Video" else if (lowerUrl.contains("instagram")) "Instagram Video" else if (lowerUrl.contains("facebook") || lowerUrl.contains("fb.")) "Facebook Video" else "Video Download"
            val format = if (lowerUrl.contains("tiktok") || lowerUrl.contains("instagram") || lowerUrl.contains("facebook") || lowerUrl.contains("fb.")) "b/best" else "b/best/mp4"
            startDownload(context, finalUrl, titleText, null, format)
        }
    }

    fun pauseDownload(context: Context, id: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = "PAUSE_DOWNLOAD"
            putExtra("DOWNLOAD_ID", id)
        }
        startServiceSafe(context, intent)
    }

    fun resumeDownload(context: Context, id: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = "RESUME_DOWNLOAD"
            putExtra("DOWNLOAD_ID", id)
        }
        startServiceSafe(context, intent)
    }

    fun cancelDownload(context: Context, id: String) {
        DownloadManager.removeDownloadItem(id)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val intent = Intent(context, DownloadService::class.java).apply {
                    action = "CANCEL_DOWNLOAD"
                    putExtra("DOWNLOAD_ID", id)
                }
                startServiceSafe(context, intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteDownload(id: String) {
        DownloadManager.deleteFileAndRemoveItem(id)
    }

    fun resetFetchState() {
        DownloadManager.updateFetchState(AppState.Idle)
    }

    private fun startServiceSafe(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
