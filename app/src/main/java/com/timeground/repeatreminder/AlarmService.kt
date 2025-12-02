package com.timeground.repeatreminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var isRinging = false

    companion object {
        const val CHANNEL_ID = "AlarmServiceChannel"
        const val ACTION_STOP = "STOP_ALARM"
        const val ACTION_START = "START_ALARM"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Initialize Vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        stopAlarm()
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP) {
            stopAlarm()
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START) {
            if (!isRinging) {
                startForeground(1, createNotification())
                playAlarm()
                isRinging = true
                saveRingingState(true)
                
                // Auto-stop after 5 seconds (Single notification style)
                handler.postDelayed(timeoutRunnable, 5000)
            }
        }

        return START_STICKY
    }

    private fun playAlarm() {
        try {
            // Play Sound
            val soundUri = getSavedRingtoneUri()
            if (soundUri != null) {
                ringtone = RingtoneManager.getRingtone(applicationContext, soundUri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ringtone?.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                ringtone?.play()
            }

            // Vibrate
            if (getVibrationPreference()) {
                if (vibrator?.hasVibrator() == true) {
                    val pattern = longArrayOf(0, 500, 200, 500) // Wait 0, Vibrate 500, Wait 200, Vibrate 500
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1)) // -1 means NO REPEAT
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(pattern, -1)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmService", "Error playing alarm", e)
        }
    }

    private fun stopAlarm() {
        try {
            handler.removeCallbacks(timeoutRunnable)
            ringtone?.stop()
            vibrator?.cancel()
            
            isRinging = false
            saveRingingState(false)
        } catch (e: Exception) {
            Log.e("AlarmService", "Error stopping alarm", e)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Repeat Reminder")
            .setContentText("Alarm is ringing")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Silent notification
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Alarm Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun getSavedRingtoneUri(): Uri? {
        val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        val uriString = prefs.getString("sound_uri", "")
        return if (uriString.isNullOrEmpty()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else Uri.parse(uriString)
    }

    private fun getVibrationPreference(): Boolean {
        val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("vibration_enabled", true)
    }
    
    private fun saveRingingState(isRinging: Boolean) {
        val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_alarm_ringing", isRinging).apply()
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}
