package com.babu.ytdownloader

import android.app.Application
import com.babu.ytdownloader.data.AppSettings
import com.babu.ytdownloader.data.HistoryRepository
import com.babu.ytdownloader.download.DownloadManager
import com.babu.ytdownloader.engine.YtDlpEngine

class YtApp : Application() {
  override fun onCreate() {
    super.onCreate()
    AppSettings.init(this)
    HistoryRepository.init(this)
    DownloadManager.init(this)
    // Extracts python + yt-dlp + ffmpeg on first launch (takes a few seconds)
    YtDlpEngine.init(this)
  }
}
