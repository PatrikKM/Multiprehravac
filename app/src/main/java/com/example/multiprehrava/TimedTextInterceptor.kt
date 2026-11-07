package com.example.multiprehrava

import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import java.net.HttpURLConnection
import java.net.URL

object TimedTextInterceptor {

    private const val TAG = "TimedTextIntercept"
    private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"

    fun intercept(request: WebResourceRequest, userAgent: String? = null): WebResourceResponse? {
        val originalUrl = request.url.toString()

        // 1. Detect timedtext query
        if (!originalUrl.contains("/api/timedtext")) {
            return null
        }

        // Do not intercept if it's already translated to cs or if original lang is cs
        if (originalUrl.contains("tlang=cs") || originalUrl.contains("lang=cs")) {
            Log.d(TAG, "Request already in Czech or translated: $originalUrl")
            return null
        }

        // Check if query contains lang=
        if (!originalUrl.contains("lang=")) {
            return null
        }

        try {
            // 2. Rewrite URL with tlang=cs & fmt=vtt
            val builder = originalUrl.toUri().buildUpon()
                .appendQueryParameter("tlang", "cs")

            if (!originalUrl.contains("fmt=")) {
                builder.appendQueryParameter("fmt", "vtt")
            }

            val rewrittenUrl = builder.build().toString()
            Log.d(TAG, "Intercepting & Rewriting timedtext URL -> $rewrittenUrl")

            // 3. Connect with Cookies & Headers (403 Protection)
            val url = URL(rewrittenUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000

                // Copy request headers
                request.requestHeaders?.forEach { (key, value) ->
                    setRequestProperty(key, value)
                }

                // Explicitly override authentication & security headers
                val cookie = CookieManager.getInstance().getCookie("https://www.youtube.com")
                if (!cookie.isNullOrEmpty()) {
                    setRequestProperty("Cookie", cookie)
                }
                setRequestProperty("Referer", "https://www.youtube.com/")
                setRequestProperty("Origin", "https://www.youtube.com")
                setRequestProperty("User-Agent", userAgent ?: DEFAULT_USER_AGENT)
            }

            val statusCode = connection.responseCode
            Log.d(TAG, "TimedText HTTP Response Status: $statusCode")

            if (statusCode == 200) {
                val mimeType = if (rewrittenUrl.contains("fmt=vtt")) "text/vtt" else "text/xml"
                val responseHeaders = mutableMapOf<String, String>()

                connection.headerFields?.forEach { (key, values) ->
                    if (key != null && values.isNotEmpty()) {
                        responseHeaders[key] = values.joinToString(",")
                    }
                }

                val inputStream = connection.inputStream
                return WebResourceResponse(
                    mimeType,
                    "utf-8",
                    200,
                    "OK",
                    responseHeaders,
                    inputStream
                )
            } else {
                Log.w(TAG, "Server returned error status $statusCode for $rewrittenUrl")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error intercepting timedtext request", e)
        }

        return null
    }

    fun setupServiceWorker(userAgentString: String? = null) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val swController = ServiceWorkerController.getInstance()
                swController.setServiceWorkerClient(object : ServiceWorkerClient() {
                    override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                        return intercept(request, userAgentString)
                    }
                })
                Log.d(TAG, "ServiceWorkerController successfully initialized!")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize ServiceWorkerController", e)
        }
    }
}

class YouTubeWebViewClient : WebViewClient() {
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        if (request != null) {
            val intercepted = TimedTextInterceptor.intercept(request, view?.settings?.userAgentString)
            if (intercepted != null) {
                return intercepted
            }
        }
        return super.shouldInterceptRequest(view, request)
    }
}
