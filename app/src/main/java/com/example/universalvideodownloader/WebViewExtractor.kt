package com.example.universalvideodownloader

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object WebViewExtractor {
    suspend fun extractMediaUrl(context: Context, pageUrl: String): String? = suspendCancellableCoroutine { continuation ->
        Handler(Looper.getMainLooper()).post {
            var isResumed = false
            val webView = WebView(context.applicationContext)
            
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                userAgentString = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"
            }

            fun finishWith(resultUrl: String?) {
                if (!isResumed) {
                    isResumed = true
                    try {
                        webView.stopLoading()
                        webView.destroy()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    continuation.resume(resultUrl)
                }
            }

            // Timeout safety (14 seconds)
            Handler(Looper.getMainLooper()).postDelayed({
                finishWith(null)
            }, 14000)

            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val urlStr = request?.url?.toString() ?: ""
                    val lower = urlStr.lowercase()
                    if ((lower.contains(".mp4") || lower.contains(".m3u8") || lower.contains("bytestream") || lower.contains("tiktokcdn") || lower.contains("v16-webapp") || lower.contains("v19-webapp") || lower.contains("video_id=")) &&
                        !lower.contains(".js") && !lower.contains(".css") && !lower.contains(".png") && !lower.contains(".jpg") && !lower.contains(".json") && !lower.contains(".webp")) {
                        Log.d("WebViewExtractor", "Intercepted direct media URL: $urlStr")
                        finishWith(urlStr)
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(
                        "(function() { " +
                        "  var v = document.querySelector('video'); " +
                        "  if (v) { " +
                        "    try { v.play(); } catch(e){} " +
                        "    if (v.src && v.src.startsWith('http')) return v.src; " +
                        "  } " +
                        "  var s = document.querySelector('source'); if(s && s.src && s.src.startsWith('http')) return s.src; " +
                        "  return ''; " +
                        "})()"
                    ) { result ->
                        val clean = result?.replace("\"", "")?.trim()
                        if (!clean.isNullOrEmpty() && clean.startsWith("http")) {
                            Log.d("WebViewExtractor", "DOM evaluated video URL: $clean")
                            finishWith(clean)
                        }
                    }
                }
            }

            try {
                webView.loadUrl(pageUrl)
            } catch (e: Exception) {
                finishWith(null)
            }
        }
    }
}
