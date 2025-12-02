
package com.timeground.repeatreminder

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
        
        if (action == "com.timeground.repeatreminder.ALARM_TRIGGER" || action == "com.timeground.repeatreminder.TEST_TRIGGER") {
            // Start Alarm Service
            startAlarmService(context)
            
            // Reschedule next alarm if it's a repeating one
            if (action == "com.timeground.repeatreminder.ALARM_TRIGGER") {
                val intervalMinutes = intent.getIntExtra("interval", 0)
                if (intervalMinutes > 0) {
                    scheduleNextAlarm(context, intervalMinutes)
                }
            }
        }
    }

    private fun startAlarmService(context: Context) {
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun scheduleNextAlarm(context: Context, intervalMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextTriggerTime = System.currentTimeMillis() + (intervalMinutes * 60 * 1000)
        
        // Save next alarm time for UI persistence
        saveNextAlarmTime(context, nextTriggerTime)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.timeground.repeatreminder.ALARM_TRIGGER"
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
        
        // Notify UI to update
        val updateIntent = Intent("com.timeground.repeatreminder.UPDATE_UI")
        updateIntent.setPackage(context.packageName)
        updateIntent.putExtra("next_alarm_time", nextTriggerTime)
        context.sendBroadcast(updateIntent)
    }
    
    private fun saveNextAlarmTime(context: Context, timeInMillis: Long) {
        val prefs = context.getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("next_alarm_time", timeInMillis).apply()
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
