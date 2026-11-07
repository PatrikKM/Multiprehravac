package com.example.multiprehrava

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class PlayerApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Setup ServiceWorkerController to intercept /api/timedtext requests
        TimedTextInterceptor.setupServiceWorker()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Delete broken downloaded yt-dlp binary if present to restore bundled version
                val ytDlpDir = File(noBackupFilesDir, "youtubedl-android/yt-dlp")
                if (ytDlpDir.exists()) {
                    ytDlpDir.deleteRecursively()
                    Log.d("PlayerApplication", "Cleaned up old downloaded yt-dlp binary")
                }

                YoutubeDL.getInstance().init(this@PlayerApplication)
                FFmpeg.getInstance().init(this@PlayerApplication)
                Log.d("PlayerApplication", "YoutubeDL and FFmpeg initialized successfully")
            } catch (e: YoutubeDLException) {
                Log.e("PlayerApplication", "Failed to initialize YoutubeDL", e)
            } catch (e: Exception) {
                Log.e("PlayerApplication", "Failed to initialize YoutubeDL/FFmpeg", e)
            }
        }
    }
}
