package com.example.repeatreminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        if (action == "com.example.repeatreminder.ALARM_TRIGGER" || action == "com.example.repeatreminder.TEST_TRIGGER") {
            playSoundAndVibrate(context)
            
            // Reschedule next alarm if it's a repeating one
            if (action == "com.example.repeatreminder.ALARM_TRIGGER") {
                val intervalMinutes = intent.getIntExtra("interval", 0)
                if (intervalMinutes > 0) {
                    scheduleNextAlarm(context, intervalMinutes)
                }
            }
        }
    }

    private fun playSoundAndVibrate(context: Context) {
        try {
            // Play Sound
            val soundUri = getSavedRingtoneUri(context)
            if (soundUri != null) {
                val ringtone = RingtoneManager.getRingtone(context, soundUri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ringtone.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                ringtone.play()
            }

            // Vibrate
            if (getVibrationPreference(context)) {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }

                if (vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Error playing sound or vibrating", e)
        }
    }

    private fun scheduleNextAlarm(context: Context, intervalMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextTriggerTime = System.currentTimeMillis() + (intervalMinutes * 60 * 1000)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.repeatreminder.ALARM_TRIGGER"
            putExtra("interval", intervalMinutes)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use setExactAndAllowWhileIdle for precision even in Doze mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerTime, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextTriggerTime, pendingIntent)
        }
    }

    private fun getSavedRingtoneUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        val uriString = prefs.getString("sound_uri", "")
        return if (uriString.isNullOrEmpty()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else Uri.parse(uriString)
    }

    private fun getVibrationPreference(context: Context): Boolean {
        val prefs = context.getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("vibration_enabled", true)
    }
}
