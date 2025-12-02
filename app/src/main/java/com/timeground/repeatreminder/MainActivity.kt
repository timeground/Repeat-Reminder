package com.timeground.repeatreminder

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import java.util.Calendar
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var etInterval: EditText
    private lateinit var btnAction: Button
    private lateinit var tvCountdown: TextView
    private lateinit var tvNextAlarm: TextView
    private lateinit var tvClock: TextView
    private lateinit var tvStartLabel: TextView
    private lateinit var btnResetTime: TextView
    private lateinit var tvSound: TextView
    private lateinit var switchVibration: SwitchCompat
    private lateinit var switchTimeFormat: SwitchCompat
    private lateinit var btnThemeToggle: android.widget.ImageButton
    
    private var isRunning = false
    private var intervalMinutes = 15
    private var countDownTimer: CountDownTimer? = null
    private var nextAlarmTime: Long = 0
    private var startTimeCalendar: Calendar? = null
    private var use24HourFormat = false
    private var isDarkMode = false
    
    private val handler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            val now = System.currentTimeMillis()
            val delay = 1000 - (now % 1000)
            handler.postDelayed(this, delay)
        }
    }
    
    private val ringtoneLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                saveRingtone(uri)
            } else {
                // "Silent" was picked
                saveRingtone(null)
            }
        }
    }
    
    private val updateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.timeground.repeatreminder.UPDATE_UI") {
                val nextTime = intent.getLongExtra("next_alarm_time", 0)
                if (nextTime > 0) {
                    nextAlarmTime = nextTime
                    // Also update shared prefs in memory to be safe
                    val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putLong("next_alarm_time", nextTime).apply()
                    
                    if (isRunning) {
                        startCountdown()
                    } else {
                        restoreState()
                    }
                } else {
                    restoreState()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etInterval = findViewById(R.id.etInterval)
        btnAction = findViewById(R.id.btnAction)
        tvCountdown = findViewById(R.id.tvCountdown)
        tvNextAlarm = findViewById(R.id.tvNextAlarm)
        tvClock = findViewById(R.id.tvClock)
        tvStartLabel = findViewById(R.id.tvStartLabel)
        btnResetTime = findViewById(R.id.btnResetTime)
        tvSound = findViewById(R.id.tvSound)
        switchVibration = findViewById(R.id.switchVibration)
        switchTimeFormat = findViewById(R.id.switchTimeFormat)
        btnThemeToggle = findViewById(R.id.btnThemeToggle)
        
        tvClock.setOnClickListener { showTimePickerDialog() }
        btnResetTime.setOnClickListener { resetToNow() }
        tvSound.setOnClickListener { pickRingtone() }
        
        switchVibration.setOnCheckedChangeListener { _, isChecked ->
            saveVibrationPreference(isChecked)
        }
        
        switchTimeFormat.setOnCheckedChangeListener { _, isChecked ->
            saveTimeFormatPreference(isChecked)
            updateClock()
            // Refresh other UI elements if needed
            if (isRunning) startCountdown() // Refresh next alarm text
            if (startTimeCalendar != null) {
                // Refresh start label
                val hour = startTimeCalendar!!.get(Calendar.HOUR_OF_DAY)
                val minute = startTimeCalendar!!.get(Calendar.MINUTE)
                updateStartLabel(hour, minute)
            }
        }
        
        btnThemeToggle.setOnClickListener {
            val newMode = !isDarkMode
            saveDarkModePreference(newMode)
            applyTheme(newMode)
        }
        
        // Start clock
        handler.post(clockRunnable)
        
        // Load saved preferences
        updateSoundUI()
        loadVibrationPreference()
        loadTimeFormatPreference()
        loadDarkModePreference()
        
        val btnMinus = findViewById<Button>(R.id.btnMinus)
        val btnPlus = findViewById<Button>(R.id.btnPlus)
        
        val btn5m = findViewById<Button>(R.id.btn5m)
        val btn10m = findViewById<Button>(R.id.btn10m)
        val btn15m = findViewById<Button>(R.id.btn15m)
        val btn30m = findViewById<Button>(R.id.btn30m)
        val btn60m = findViewById<Button>(R.id.btn60m)
        
        val presets = listOf(btn5m, btn10m, btn15m, btn30m, btn60m)

        btnMinus.setOnClickListener { adjustInterval(-1, presets) }
        btnPlus.setOnClickListener { adjustInterval(1, presets) }
        
        btn5m.setOnClickListener { setPreset(5, presets) }
        btn10m.setOnClickListener { setPreset(10, presets) }
        btn15m.setOnClickListener { setPreset(15, presets) }
        btn30m.setOnClickListener { setPreset(30, presets) }
        btn60m.setOnClickListener { setPreset(60, presets) }
        
        btnAction.setOnClickListener { toggleReminder() }

        // Initialize UI
        updatePresetUI(intervalMinutes, presets)
        checkPermissions()
    }

    private fun setPreset(minutes: Int, buttons: List<Button>) {
        if (isRunning) return
        intervalMinutes = minutes
        etInterval.setText(minutes.toString())
        updatePresetUI(minutes, buttons)
    }

    private fun updatePresetUI(selectedMinutes: Int, buttons: List<Button>) {
        buttons.forEach { btn ->
            val btnMinutes = btn.text.toString().replace("m", "").toIntOrNull() ?: 0
            if (btnMinutes == selectedMinutes) {
                btn.setTextColor(ContextCompat.getColor(this, R.color.white))
                btn.backgroundTintList = ContextCompat.getColorStateList(this, R.color.blue_preset)
            } else {
                btn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                btn.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gray_preset)
            }
        }
    }

    private fun adjustInterval(delta: Int, buttons: List<Button>) {
        if (isRunning) return
        var current = etInterval.text.toString().toIntOrNull() ?: 15
        current += delta
        if (current < 1) current = 1
        etInterval.setText(current.toString())
        intervalMinutes = current
        updatePresetUI(current, buttons)
    }

    private fun toggleReminder() {
        if (isRunning) {
            stopReminder()
        } else {
            startReminder()
        }
    }

    override fun onResume() {
        super.onResume()
        checkAlarmState()
        
        val filter = android.content.IntentFilter("com.timeground.repeatreminder.UPDATE_UI")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateReceiver, filter)
        }
    }
    
    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(updateReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }

    private fun checkAlarmState() {
        val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        val isRinging = prefs.getBoolean("is_alarm_ringing", false)
        
        if (isRinging) {
            showStopAlarmUI()
        } else {
            // If not ringing, check if we should be running
            restoreState()
        }
    }

    private fun showStopAlarmUI() {
        btnAction.text = getString(R.string.stop_reminder)
        btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.red_stop)
        btnAction.setOnClickListener { stopAlarmService() }
        
        tvCountdown.visibility = TextView.VISIBLE
        tvCountdown.text = "ALARM RINGING"
        tvNextAlarm.visibility = TextView.INVISIBLE
        
        etInterval.isEnabled = false
        tvClock.isEnabled = false
        btnResetTime.isEnabled = false
    }

    private fun stopAlarmService() {
        val intent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP
        }
        startService(intent)
        
        // Update UI immediately
        val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_alarm_ringing", false).apply()
        
        // If we were running a repeating alarm, we should go back to running state (countdown)
        // But for simplicity, stopping the alarm usually means acknowledging it.
        // If it's repeating, the next one is already scheduled by the Receiver.
        // So we should restore the "Running" state.
        restoreState()
    }

    private fun saveState(running: Boolean, interval: Int, nextAlarm: Long) {
        val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("is_running", running)
            putInt("interval_minutes", interval)
            putLong("next_alarm_time", nextAlarm)
            apply()
        }
    }

    private fun restoreState() {
        val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        isRunning = prefs.getBoolean("is_running", false)
        intervalMinutes = prefs.getInt("interval_minutes", 15)
        nextAlarmTime = prefs.getLong("next_alarm_time", 0)
        
        // Also check if Receiver updated the next alarm time
        val receiverNextTime = prefs.getLong("next_alarm_time", 0)
        if (receiverNextTime > nextAlarmTime) {
            nextAlarmTime = receiverNextTime
        }

        if (isRunning) {
            // SELF-HEALING: If nextAlarmTime is in the past, something went wrong.
            // We should reschedule it for the next interval.
            if (nextAlarmTime < System.currentTimeMillis()) {
                val now = System.currentTimeMillis()
                // Calculate next slot: current time + interval
                // Or better: keep adding interval to old time until it's future (to keep cadence)
                // But for simplicity and robustness, just schedule from NOW.
                nextAlarmTime = now + (intervalMinutes * 60 * 1000)
                
                // Save fixed state
                saveState(true, intervalMinutes, nextAlarmTime)
                
                // Reschedule Alarm
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(this, AlarmReceiver::class.java).apply {
                    action = "com.timeground.repeatreminder.ALARM_TRIGGER"
                    putExtra("interval", intervalMinutes)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmTime, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextAlarmTime, pendingIntent)
                }
            }

            // Update UI to running state
            etInterval.setText(intervalMinutes.toString())
            btnAction.text = getString(R.string.stop_reminder)
            btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.red_stop)
            btnAction.setOnClickListener { toggleReminder() } // Back to normal toggle
            
            etInterval.isEnabled = false
            tvClock.isEnabled = false
            btnResetTime.isEnabled = false
            
            startCountdown()
        } else {
            // Update UI to stopped state
            stopReminderUI()
        }
    }

    private fun startReminder() {
        if (!checkPermissions()) return

        val input = etInterval.text.toString().toIntOrNull()
        if (input == null || input <= 0) {
            Toast.makeText(this, "Invalid interval", Toast.LENGTH_SHORT).show()
            return
        }
        intervalMinutes = input

        // Schedule first alarm
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val triggerTime = if (startTimeCalendar != null) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, startTimeCalendar!!.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, startTimeCalendar!!.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.before(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            target.timeInMillis
        } else {
            System.currentTimeMillis() + (intervalMinutes * 60 * 1000)
        }
        
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = "com.timeground.repeatreminder.ALARM_TRIGGER"
            putExtra("interval", intervalMinutes)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }

        // Update UI
        isRunning = true
        nextAlarmTime = triggerTime
        
        saveState(true, intervalMinutes, nextAlarmTime)
        
        btnAction.text = getString(R.string.stop_reminder)
        btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.red_stop)
        etInterval.isEnabled = false
        tvClock.isEnabled = false
        btnResetTime.isEnabled = false
        
        startCountdown()
    }

    private fun stopReminder() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = "com.timeground.repeatreminder.ALARM_TRIGGER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)

        // Also stop the service if it's running
        stopAlarmService()

        isRunning = false
        saveState(false, intervalMinutes, 0)
        
        stopReminderUI()
    }
    
    private fun stopReminderUI() {
        countDownTimer?.cancel()
        tvCountdown.visibility = TextView.INVISIBLE
        tvNextAlarm.visibility = TextView.INVISIBLE
        
        btnAction.text = getString(R.string.start_reminder)
        btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.green_start)
        btnAction.setOnClickListener { toggleReminder() }
        
        etInterval.isEnabled = true
        tvClock.isEnabled = true
        btnResetTime.isEnabled = true
    }

    private fun startCountdown() {
        tvCountdown.visibility = TextView.VISIBLE
        tvNextAlarm.visibility = TextView.VISIBLE
        
        countDownTimer?.cancel()
        
        val duration = nextAlarmTime - System.currentTimeMillis()
        
        if (duration < 0) {
             // Alarm should have fired. If we are here, maybe it's ringing or just passed.
             // If ringing, checkAlarmState() would have caught it.
             // If just passed, we wait for next cycle.
             tvCountdown.text = "00:00"
             return
        }
        
        countDownTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                tvCountdown.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                // Timer finished
            }
        }.start()
        
        val pattern = if (use24HourFormat) "HH:mm" else "hh:mm a"
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        tvNextAlarm.text = "Next Alarm: ${sdf.format(java.util.Date(nextAlarmTime))}"
    }

    private fun checkPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                return false
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
                return false
            }
        }
        return true
    }

    private fun showTimePickerDialog() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            startTimeCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, selectedHour)
                set(Calendar.MINUTE, selectedMinute)
                set(Calendar.SECOND, 0)
            }
            
            updateStartLabel(selectedHour, selectedMinute)
            
            // Show reset button
            btnResetTime.visibility = View.VISIBLE
            
        }, hour, minute, use24HourFormat).show()
    }
    
    private fun updateStartLabel(hour: Int, minute: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val pattern = if (use24HourFormat) "HH:mm" else "hh:mm a"
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        val timeText = "STARTING AT ${sdf.format(calendar.time)}"
        tvStartLabel.text = timeText
    }
    
    private fun resetToNow() {
        startTimeCalendar = null
        tvStartLabel.text = "SELECT START TIME"
        btnResetTime.visibility = View.INVISIBLE
    }
    
    private fun updateClock() {
        val pattern = if (use24HourFormat) "HH:mm:ss" else "hh:mm:ss a"
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        tvClock.text = sdf.format(java.util.Date())
    }
    
    private fun pickRingtone() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Notification Tone")
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, getSavedRingtoneUri())
        ringtoneLauncher.launch(intent)
    }
    
    private fun saveRingtone(uri: Uri?) {
        val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("sound_uri", uri?.toString() ?: "").apply()
        updateSoundUI()
    }
    
    private fun getSavedRingtoneUri(): Uri? {
        val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        val uriString = prefs.getString("sound_uri", "")
        return if (uriString.isNullOrEmpty()) null else Uri.parse(uriString)
    }
    
    private fun updateSoundUI() {
        val uri = getSavedRingtoneUri()
        if (uri != null) {
            val ringtone = RingtoneManager.getRingtone(this, uri)
            tvSound.text = "Sound: ${ringtone.getTitle(this)}"
        } else {
            tvSound.text = "Sound: Default" // Or Silent if we want to distinguish
        }
    }
    
    private fun saveVibrationPreference(isEnabled: Boolean) {
        val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("vibration_enabled", isEnabled).apply()
    }

    private fun loadVibrationPreference() {
        val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("vibration_enabled", true)
        switchVibration.isChecked = isEnabled
    }

    private fun saveTimeFormatPreference(is24Hour: Boolean) {
        use24HourFormat = is24Hour
        val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("use_24h_format", is24Hour).apply()
    }

    private fun loadTimeFormatPreference() {
        val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        use24HourFormat = prefs.getBoolean("use_24h_format", false)
        switchTimeFormat.isChecked = use24HourFormat
    }
    
    private fun saveDarkModePreference(isDark: Boolean) {
        isDarkMode = isDark
        val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("dark_mode_enabled", isDark).apply()
    }

    private fun loadDarkModePreference() {
        val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        isDarkMode = prefs.getBoolean("dark_mode_enabled", false)
        applyTheme(isDarkMode)
    }
    
    private fun applyTheme(isDark: Boolean) {
        isDarkMode = isDark
        val mode = if (isDark) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        if (androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() != mode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
        }
        
        // Update icon
        if (isDark) {
            btnThemeToggle.setImageResource(R.drawable.ic_sun)
        } else {
            btnThemeToggle.setImageResource(R.drawable.ic_moon)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(clockRunnable)
    }
}
