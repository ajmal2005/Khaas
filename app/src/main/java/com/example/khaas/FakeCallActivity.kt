package com.example.khaas

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class FakeCallActivity : AppCompatActivity() {



    private var mediaPlayer: MediaPlayer? = null
    private lateinit var incomingCallLayout: LinearLayout
    private lateinit var connectedLayout: LinearLayout
    private lateinit var chronometer: Chronometer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fake_call)

        incomingCallLayout = findViewById(R.id.incomingCallLayout)
        connectedLayout = findViewById(R.id.connectedLayout)
        chronometer = findViewById(R.id.chronometer)

        val name = intent.getStringExtra("EXTRA_NAME") ?: "Unknown Caller"
        val number = intent.getStringExtra("EXTRA_NUMBER") ?: "Mobile"

        updateCallerInfo(name, number)

        startRingtone()

        findViewById<FloatingActionButton>(R.id.answerButton).setOnClickListener {
            answerCall()
        }

        findViewById<FloatingActionButton>(R.id.declineButton).setOnClickListener {
            declineCall()
        }
        
        findViewById<FloatingActionButton>(R.id.endCallButton).setOnClickListener {
            declineCall()
        }
    }

    private fun updateCallerInfo(name: String, number: String) {
        // Update incoming call layout
        incomingCallLayout.findViewById<TextView>(R.id.callerNameText)?.text = name
        incomingCallLayout.findViewById<TextView>(R.id.callerNumberText)?.text = number

        // Update connected layout
        connectedLayout.findViewById<TextView>(R.id.connectedNameText)?.text = name
    }

    private fun startRingtone() {
        val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        
        // Get volume preference
        val prefs = getSharedPreferences("KhaasPrefs", android.content.Context.MODE_PRIVATE)
        val volumePercent = prefs.getInt("vip_max_volume", 100)
        val volume = volumePercent / 100f
        
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA) // Use MEDIA to allow volume scaling easily on top of system volume
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            try {
                setDataSource(applicationContext, notification)
                prepare()
                setVolume(volume, volume) // Set volume based on preference
                isLooping = true
                start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopRingtone() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
    }

    private fun answerCall() {
        stopRingtone()
        incomingCallLayout.visibility = View.GONE
        connectedLayout.visibility = View.VISIBLE
        chronometer.base = SystemClock.elapsedRealtime()
        chronometer.start()
    }

    private fun declineCall() {
        stopRingtone()
        chronometer.stop()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtone()
    }
}
