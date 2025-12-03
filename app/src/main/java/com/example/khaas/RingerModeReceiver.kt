package com.example.khaas

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.core.content.ContextCompat

class RingerModeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        checkAndToggleService(context)
    }

    companion object {
        fun checkAndToggleService(context: Context) {
            val prefs = context.getSharedPreferences("KhaasPrefs", Context.MODE_PRIVATE)
            val isVipEnabled = prefs.getBoolean("vip_enabled", true)

            if (!isVipEnabled) {
                stopService(context)
                return
            }

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val ringerMode = audioManager.ringerMode
            val interruptionFilter = notificationManager.currentInterruptionFilter

            val isSilentOrVibrate = ringerMode == AudioManager.RINGER_MODE_SILENT || ringerMode == AudioManager.RINGER_MODE_VIBRATE
            val isDndActive = interruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

            if (isSilentOrVibrate || isDndActive) {
                startService(context)
            } else {
                stopService(context)
            }
        }

        private fun startService(context: Context) {
            val serviceIntent = Intent(context, VipMonitorService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }

        private fun stopService(context: Context) {
            val serviceIntent = Intent(context, VipMonitorService::class.java)
            context.stopService(serviceIntent)
        }
    }
}
