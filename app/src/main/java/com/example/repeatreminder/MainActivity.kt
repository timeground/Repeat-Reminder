package com.example.repeatreminder

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
    
    private var isRunning = false
    private var intervalMinutes = 15
    private var countDownTimer: CountDownTimer? = null
    private var nextAlarmTime: Long = 0
    private var startTimeCalendar: Calendar? = null
    
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
        
        tvClock.setOnClickListener { showTimePickerDialog() }
        btnResetTime.setOnClickListener { resetToNow() }
        tvSound.setOnClickListener { pickRingtone() }
        
        switchVibration.setOnCheckedChangeListener { _, isChecked ->
            saveVibrationPreference(isChecked)
        }
        
        // Start clock
        handler.post(clockRunnable)
        
        // Load saved preferences
        updateSoundUI()
        loadVibrationPreference()
        
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

    private fun startReminder() {
        if (!checkPermissions()) return

        val input = etInterval.text.toString().toIntOrNull()
        if (input == null || input <= 0) {
            Toast.makeText(this, "Invalid interval", Toast.LENGTH_SHORT).show()
            return
        }
        intervalMinutes = input

        // Schedule first alarm
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
            action = "com.example.repeatreminder.ALARM_TRIGGER"
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
            action = "com.example.repeatreminder.ALARM_TRIGGER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)

        isRunning = false
        countDownTimer?.cancel()
        tvCountdown.visibility = TextView.INVISIBLE
        tvNextAlarm.visibility = TextView.INVISIBLE
        
        btnAction.text = getString(R.string.start_reminder)
        btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.green_start)
        etInterval.isEnabled = true
        tvClock.isEnabled = true
        btnResetTime.isEnabled = true
    }

    private fun startCountdown() {
        tvCountdown.visibility = TextView.VISIBLE
        tvNextAlarm.visibility = TextView.VISIBLE
        
        countDownTimer?.cancel()
        
        val duration = nextAlarmTime - System.currentTimeMillis()
        
        countDownTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                tvCountdown.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                // Alarm should fire now. 
                // The receiver will reschedule, but we need to update UI for next cycle.
                // In a real app, we might use LiveData/Flow or a shared preference listener.
                // For simplicity, we'll just restart the timer assuming the alarm worked.
                nextAlarmTime = System.currentTimeMillis() + (intervalMinutes * 60 * 1000)
                startCountdown() 
            }
        }.start()
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
            
            // Update label to show selected time
            val timeText = String.format(Locale.getDefault(), "STARTING AT %02d:%02d", selectedHour, selectedMinute)
            tvStartLabel.text = timeText
            
            // Show reset button
            btnResetTime.visibility = View.VISIBLE
            
        }, hour, minute, true).show()
    }
    
    private fun resetToNow() {
        startTimeCalendar = null
        tvStartLabel.text = "SELECT START TIME"
        btnResetTime.visibility = View.INVISIBLE
    }
    
    private fun updateClock() {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
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
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(clockRunnable)
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
}
