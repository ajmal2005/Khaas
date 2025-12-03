package com.example.khaas

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.telephony.TelephonyManager
import android.app.NotificationManager
import android.os.Build

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallInterceptorReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallInterceptor"
        private var mediaPlayer: MediaPlayer? = null
        private var previousRingerVolume: Int = -1
        private var previousInterruptionFilter: Int = -1
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("KhaasPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("vip_enabled", true)) {
            return
        }

        if (intent.action == "com.example.khaas.SIMULATE_CALL") {
            val incomingNumber = intent.getStringExtra("incoming_number")
            if (!incomingNumber.isNullOrEmpty()) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        checkVipAndPlaySound(context, incomingNumber)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        } else if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            
            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    if (!incomingNumber.isNullOrEmpty()) {
                        handleRinging(context, incomingNumber)
                    }
                }
                TelephonyManager.EXTRA_STATE_IDLE, TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    stopPlayback(context)
                }
            }
        }
    }

    private fun handleRinging(context: Context, incomingNumber: String) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ringerMode = audioManager.ringerMode
        val interruptionFilter = notificationManager.currentInterruptionFilter
        
        // Check if we need to intervene:
        // 1. Ringer is Silent or Vibrate
        // 2. DND is active (Filter is NOT ALL, i.e., Priority, Alarms, or None)
        val isSilentOrVibrate = ringerMode == AudioManager.RINGER_MODE_SILENT || ringerMode == AudioManager.RINGER_MODE_VIBRATE
        val isDndActive = interruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

        if (isSilentOrVibrate || isDndActive) {
            // Mute the ringer immediately to prevent "Repeat Callers" from ringing
            try {
                previousInterruptionFilter = notificationManager.currentInterruptionFilter
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                previousRingerVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    checkVipAndPlaySound(context, incomingNumber)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
    private suspend fun checkVipAndPlaySound(context: Context, incomingNumber: String) {
        val db = AppDatabase.getDatabase(context)
        val dao = db.vipContactDao()
        
        // Fetch all contacts for fuzzy matching
        val allContacts = dao.getAllContacts()
        val cleanIncoming = incomingNumber.replace(Regex("[^0-9]"), "")

        val vipContact = allContacts.find { contact ->
            val cleanContact = contact.phoneNumber.replace(Regex("[^0-9]"), "")
            // Check if one ends with the other (handling country codes)
            // Ensure at least 7 digits match to avoid short number false positives
            if (cleanIncoming.length >= 7 && cleanContact.length >= 7) {
                cleanIncoming.endsWith(cleanContact) || cleanContact.endsWith(cleanIncoming)
            } else {
                cleanIncoming == cleanContact
            }
        }
        
        if (vipContact != null) {
            val prefs = context.getSharedPreferences("KhaasPrefs", Context.MODE_PRIVATE)
            val appRingtone = prefs.getString("pref_app_ringtone", null)
            val ringtoneUri = if (appRingtone != null) {
                Uri.parse(appRingtone)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            
            playRingtone(context, ringtoneUri)
        }
    }

    private fun playRingtone(context: Context, uri: Uri) {
        // Stop any existing playback first
        stopPlayback(context)

        // Ensure volume is up for Alarm stream based on preference
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxStreamVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        
        val prefs = context.getSharedPreferences("KhaasPrefs", Context.MODE_PRIVATE)
        val volumePercent = prefs.getInt("vip_max_volume", 100)
        
        val targetVolume = (maxStreamVolume * (volumePercent / 100.0)).toInt()
        
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            try {
                setDataSource(context, uri)
                prepare()
                start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopPlayback(context: Context) {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null

        // Restore Ringer Volume
        if (previousRingerVolume != -1) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_RING, previousRingerVolume, 0)
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
            previousRingerVolume = -1
        }
    }
}
