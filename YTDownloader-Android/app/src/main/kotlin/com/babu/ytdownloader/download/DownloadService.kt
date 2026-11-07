package com.babu.ytdownloader.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.babu.ytdownloader.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/** Keeps the process alive while downloads run in the background and shows a progress notification. */
class DownloadService : Service() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  override fun onBind(intent: Intent?): IBinder? = null

  @OptIn(FlowPreview::class)
  override fun onCreate() {
    super.onCreate()
    createChannel()
    ServiceCompat.startForeground(
      this,
      NOTIFICATION_ID,
      buildNotification("Social Downloader — Starting download…", null, 0),
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
    )
    scope.launch {
      DownloadManager.tasks.sample(800).collect { tasks ->
        val active = tasks.filter { it.isActive }
        if (active.isEmpty()) {
          ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
          stopSelf()
          return@collect
        }
        val running = active.firstOrNull { it.status == TaskStatus.RUNNING }
        val title = "Social Downloader (${active.size} active)"
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(title, running?.title, running?.progress?.toInt() ?: 0))
      }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

  /** Android 15+: dataSync services are limited to 6h per day. */
  override fun onTimeout(startId: Int, fgsType: Int) {
    DownloadManager.cancelAll()
    stopSelf()
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  private fun buildNotification(title: String, text: String?, progress: Int) =
    NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle(title)
      .setContentText(text)
      .setOnlyAlertOnce(true)
      .setOngoing(true)
      .setProgress(100, progress, text == null)
      .setContentIntent(
        PendingIntent.getActivity(
          this, 0, Intent(this, MainActivity::class.java),
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
      )
      .build()

  private fun createChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
      getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
  }

  companion object {
    private const val CHANNEL_ID = "downloads"
    private const val NOTIFICATION_ID = 42

    fun start(context: Context) {
      ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
    }
  }
}
