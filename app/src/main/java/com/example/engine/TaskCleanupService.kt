package com.example.engine

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import com.example.AnimeBrowserApplication

/**
 * Lightweight, non-sticky lifecycle sentinel (NO foreground notification, ZERO idle CPU).
 * Sole purpose: when the user swipes ZenithFox Browser away from Android Recents,
 * immediately tear down all Chromium WebViews, media decoders, and timers so ZERO
 * background processes can stay alive or drain battery overnight.
 */
class TaskCleanupService : Service() {

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    return START_NOT_STICKY
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    runCatching {
      (application as? AnimeBrowserApplication)?.chromiumEngine?.shutdownAllSessions()
    }
    stopSelf()
    // Ensure the browser & any lingering WebView sandbox processes exit cleanly when swiped away
    Process.killProcess(Process.myPid())
  }
}
