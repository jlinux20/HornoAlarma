package com.example.hornoalarma

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.app.Service.STOP_FOREGROUND_REMOVE
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import java.util.*

class TimerService : Service(), TextToSpeech.OnInitListener {

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
    private var textToSpeech: TextToSpeech? = null

    private val guides = mapOf(
        "levadura" to "Ay, querido, la levadura es como una amiga caprichosa. Ponla en agua tibia con azuquita y si no hace espumita en cinco minutitos, mejor cámbiale por una nueva.",
        "mejorador" to "Escúchame bien, el mejorador es como el maquillaje del pan. Poquito está bien, mucho lo arruina. Solo una cucharadita por kilo de harina.",
        "temperatura" to "Mira, el horno es como un viejo cascarrabias. Hay que conocerlo bien. Que esté bien calientito, unos 220 graditos, por lo menos media hora antes.",
        "vapor" to "Te voy a decir un secreto de abuela: pon una tacita con agua al fondo del horno. Vas a ver qué corteza tan bonita te queda.",
        "masa" to "La masa es como un bebé, hay que sentirla. Cuando la tocas con el dedo y regresa despacito, ya está lista para el horno.",
        "amasado" to "Ay, mi nieto, amasar es como abrazar la masa. Con cariño, con tiempo. Ocho minutitos por lo menos, hasta que esté suavecita.",
        "reposo" to "La paciencia es virtud, querido. Deja que la masa descanse y crezca. Como nosotros, necesita su tiempo para estar lista.",
        "harina" to "Usa buena harina, mi amor. La harina barata es como zapatos baratos, al final te sale más caro.",
        "sal" to "La sal es importante, pero no te pases. Una cucharadita por cada taza de harina está perfecto. Mucha sal amarga el pan.",
        "agua" to "El agua tibia, como para bañar a un bebé. Muy caliente mata la levadura, muy fría no la despierta."
    )

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("timer_prefs", MODE_PRIVATE)
        createNotificationChannel()
        initializeTextToSpeech()
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
            val message = "Tiempo terminado para ${steps[currentStep].name}. Ahora cambiar el soplete al ${steps[currentStep + 1].name} por ${steps[currentStep + 1].timeMinutes} minutos"
            playAlarmAndVoice(message)
            currentStep++
            timeLeft = steps[currentStep].timeMinutes * 60L * 1000
            stepName = steps[currentStep].name
            saveState()
            startTimer()
            sendBroadcast(Intent("TIMER_STEP_FINISHED"))
        } else {
            val message = "¡Horneado terminado! Tu $recipeName está listo. Revisar el pan y retirar del horno con cuidado"
            playAlarmAndVoice(message)
            sendBroadcast(Intent("TIMER_ALL_FINISHED"))
            isRunning = false
            saveState()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this, this).apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("TimerService", "TTS onStart: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d("TimerService", "TTS onDone: $utteranceId")
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e("TimerService", "TTS onError: $utteranceId")
                }
            })
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale.forLanguageTag("es-ES")
            val result = textToSpeech?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TimerService", "Idioma español no soportado para voz")
            } else {
                textToSpeech?.setSpeechRate(0.6f)
                textToSpeech?.setPitch(1.0f)
            }
        }
    }

    private fun playAlarmAndVoice(message: String) {
        Thread {
            var localToneGenerator: ToneGenerator? = null
            try {
                Log.d("TimerService", "playAlarmAndVoice: Starting tone generation.")
                localToneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                val durations = intArrayOf(200, 200, 200, 200, 500)
                for (dur in durations) {
                    localToneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, dur)
                    Thread.sleep((dur + 50).toLong())
                }
                Thread.sleep(500) // Pausa antes de hablar
            } catch (e: Exception) {
                Log.e("TimerService", "Error in tone generation thread", e)
            } finally {
                localToneGenerator?.release()
                Log.d("TimerService", "playAlarmAndVoice: ToneGenerator released.")
            }

            // Speak the message
            val fullMessage = message + ". Consejo del día: ${getRandomTip()}"
            speakCompat(fullMessage)
        }.start()
    }

    private fun getRandomTip(): String {
        val tipKeys = guides.keys.toList()
        val randomKey = tipKeys.random()
        return guides[randomKey] ?: ""
    }

    private fun speakCompat(text: String) {
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "alarm_speech")
        // Forzar el uso del canal de música para evitar interferencias de tonos del sistema
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)

        // Limpiar la cola y añadir un breve silencio antes de hablar
        textToSpeech?.playSilentUtterance(250, TextToSpeech.QUEUE_FLUSH, "silence")
        textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, params, "alarm_speech")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        saveState()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}
