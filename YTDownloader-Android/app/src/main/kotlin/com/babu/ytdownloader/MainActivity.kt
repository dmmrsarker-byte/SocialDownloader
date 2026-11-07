package com.babu.ytdownloader

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.babu.ytdownloader.data.AppSettings
import com.babu.ytdownloader.ui.MainScreen
import com.babu.ytdownloader.ui.MainViewModel
import com.babu.ytdownloader.ui.extractUrl
import com.babu.ytdownloader.ui.isSupportedSocialUrl
import com.babu.ytdownloader.ui.theme.YTDownloaderTheme

class MainActivity : ComponentActivity() {

  private val vm: MainViewModel by viewModels()

  private val permissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* optional */ }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    requestPermissionsIfNeeded()
    handleShare(intent)

    setContent {
      YTDownloaderTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainScreen(vm)
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleShare(intent)
  }

  /**
   * Automatically grab & download copied social media links from the clipboard
   * whenever the user brings Social Downloader into focus.
   */
  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      checkClipboardAndAutoGrab()
    }
  }

  private fun checkClipboardAndAutoGrab() {
    runCatching {
      val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
      val clip = cm.primaryClip ?: return
      if (clip.itemCount <= 0) return
      val rawText = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
      val url = extractUrl(rawText) ?: return
      if (isSupportedSocialUrl(url)) {
        vm.onClipboardUrl(url)
      }
    }
  }

  /** Any app → Share → Social Downloader: automatically grabs link and starts download */
  private fun handleShare(intent: Intent?) {
    if (intent?.action == Intent.ACTION_SEND) {
      vm.onShared(intent.getStringExtra(Intent.EXTRA_TEXT))
    }
  }

  private fun requestPermissionsIfNeeded() {
    val perms = buildList {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
      if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
    if (perms.isNotEmpty()) permissionLauncher.launch(perms.toTypedArray())
  }
}
