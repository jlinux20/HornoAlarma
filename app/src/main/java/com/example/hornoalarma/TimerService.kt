package com.example.hornoalarma

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.app.Service.STOP_FOREGROUND_REMOVE
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder

class TimerService : Service() {

    companion object {
        const val ACTION_START_TIMER = "com.example.hornoalarma.START_TIMER"
        const val ACTION_STOP_TIMER = "com.example.hornoalarma.STOP_TIMER"
        const val ACTION_PAUSE_TIMER = "com.example.hornoalarma.PAUSE_TIMER"
        const val ACTION_RESET_TIMER = "com.example.hornoalarma.RESET_TIMER"
        const val EXTRA_RECIPE_NAME = "recipe_name"
        const val EXTRA_STEP_NAME = "step_name"
        const val EXTRA_TIME_MINUTES = "time_minutes"
        const val EXTRA_STEPS = "steps"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "timer_channel"
    }

    private lateinit var prefs: SharedPreferences
    private var countDownTimer: CountDownTimer? = null
    private var timeLeft = 0L
    private var isRunning = false
    private var currentStep = 0
    private var recipeName = ""
    private var stepName = ""
    private var steps: List<MainActivity.Step> = listOf()

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("timer_prefs", MODE_PRIVATE)
        createNotificationChannel()
        loadState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TIMER -> {
                recipeName = intent.getStringExtra(EXTRA_RECIPE_NAME) ?: "Receta"
                stepName = intent.getStringExtra(EXTRA_STEP_NAME) ?: "Paso"
                val timeMinutes = intent.getIntExtra(EXTRA_TIME_MINUTES, 0)
                val stepsJson = intent.getStringExtra(EXTRA_STEPS) ?: "[]"
                steps = Gson().fromJson(stepsJson, Array<MainActivity.Step>::class.java).toList()
                if (timeLeft == 0L) {
                    timeLeft = timeMinutes * 60L * 1000
                }
            isRunning = true
            saveState()
            try {
                startForeground(NOTIFICATION_ID, createNotification(recipeName, stepName, timeMinutes), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                startTimer()
            } catch (e: Exception) {
                Log.e("TimerService", "Error starting foreground service", e)
                isRunning = false
                saveState()
                stopSelf()
            }
            }
            ACTION_PAUSE_TIMER -> {
                isRunning = false
                countDownTimer?.cancel()
                countDownTimer = null
                saveState()
            }
            ACTION_RESET_TIMER -> {
                isRunning = false
                currentStep = 0
                timeLeft = 0L
                countDownTimer?.cancel()
                countDownTimer = null
                saveState()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_STOP_TIMER -> {
                isRunning = false
                countDownTimer?.cancel()
                countDownTimer = null
            saveState()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            }
        }
        return START_STICKY
    }

    private fun createNotification(recipeName: String, stepName: String, timeMinutes: Int): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⏰ Timer Activo - $recipeName")
            .setContentText("Paso: $stepName - ${timeMinutes}min restantes")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Timer Notifications"
            val descriptionText = "Notificaciones del timer de horneado"
            val importance = NotificationManager.IMPORTANCE_LOW // Sin sonido ni vibración
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun loadState() {
        timeLeft = prefs.getLong("timeLeft", 0L)
        isRunning = prefs.getBoolean("isRunning", false)
        currentStep = prefs.getInt("currentStep", 0)
        recipeName = prefs.getString("recipeName", "") ?: ""
        stepName = prefs.getString("stepName", "") ?: ""
        val stepsJson = prefs.getString("steps", "[]")
        steps = Gson().fromJson(stepsJson, Array<MainActivity.Step>::class.java).toList()
        if (isRunning) {
            try {
                startForeground(NOTIFICATION_ID, createNotification(recipeName, stepName, (timeLeft / 60000).toInt()), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                startTimer()
            } catch (e: Exception) {
                Log.e("TimerService", "Error starting foreground service on loadState", e)
                isRunning = false
                saveState()
                stopSelf()
            }
        }
    }

    private fun saveState() {
        prefs.edit().apply {
            putLong("timeLeft", timeLeft)
            putBoolean("isRunning", isRunning)
            putInt("currentStep", currentStep)
            putString("recipeName", recipeName)
            putString("stepName", stepName)
            putString("steps", Gson().toJson(steps))
        }.apply()
    }

    private fun startTimer() {
        // Asegurar que siempre se cree un nuevo timer (evita bloqueo tras onFinish)
        countDownTimer?.cancel()
        countDownTimer = null

        if (timeLeft <= 0L) return

        countDownTimer = object : CountDownTimer(timeLeft, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeft = millisUntilFinished
                updateNotification()
                sendUpdateBroadcast()
            }

            override fun onFinish() {
                // Liberar referencia para permitir reinicio del siguiente paso
                countDownTimer = null
                onTimerFinished()
            }
        }.start()
    }

    private fun updateNotification() {
        val notification = createNotification(recipeName, stepName, (timeLeft / 60000).toInt())
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun sendUpdateBroadcast() {
        val intent = Intent("TIMER_UPDATE").apply {
            putExtra("timeLeft", timeLeft)
            putExtra("currentStep", currentStep)
        }
        sendBroadcast(intent)
    }

    private fun onTimerFinished() {
        if (currentStep < steps.size - 1) {
            currentStep++
            timeLeft = steps[currentStep].timeMinutes * 60L * 1000
            stepName = steps[currentStep].name
            saveState()
            startTimer()
            sendBroadcast(Intent("TIMER_STEP_FINISHED"))
        } else {
            sendBroadcast(Intent("TIMER_ALL_FINISHED"))
            isRunning = false
            saveState()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        saveState()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}
