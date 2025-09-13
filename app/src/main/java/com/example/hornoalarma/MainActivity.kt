
package com.example.hornoalarma

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.google.gson.Gson
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // UI Components
    private lateinit var spinnerRecipes: Spinner
    private lateinit var layoutCustomTimes: LinearLayout
    private lateinit var editLeftTime: EditText
    private lateinit var editRightTime: EditText
    private lateinit var editCenterTime: EditText
    private lateinit var layoutTimer: LinearLayout
    private lateinit var textTimeDisplay: TextView
    private lateinit var textCurrentStep: TextView
    private lateinit var textStepCounter: TextView
    private lateinit var layoutProgress: LinearLayout
    private lateinit var layoutProgressDots: LinearLayout
    private lateinit var layoutControls: LinearLayout
    private lateinit var btnStartPause: Button
    private lateinit var btnReset: Button
    private lateinit var layoutTips: LinearLayout
    private lateinit var textRecipeTips: TextView
    private lateinit var btnGuides: Button

    // Logic variables
    private var selectedRecipe = ""
    private var currentStep = 0
    private var timeLeft = 0L
    private var isRunning = false
    private var currentStepName = ""
    private var textToSpeech: TextToSpeech? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var timerUpdateReceiver: BroadcastReceiver

    // Data
    private val recipes = mapOf(
        "pan-especial" to Recipe(
            name = "Pan Especial",
            steps = listOf(
                Step("Lado Izquierdo", 15),
                Step("Lado Derecho", 15),
                Step("Centro", 10)
            ),
            tips = "Para pan especial, usa levadura fresca y deja reposar la masa 30 minutos antes del horneado."
        ),
        "pan-piso" to Recipe(
            name = "Pan de Piso",
            steps = listOf(
                Step("Lado Izquierdo", 15),
                Step("Lado Derecho", 15),
                Step("Centro", 15)
            ),
            tips = "Para pan de piso, precalienta bien el horno y usa harina de fuerza. El vapor inicial es clave."
        )
    )

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

    data class Recipe(val name: String, val steps: List<Step>, val tips: String)
    data class Step(val name: String, val timeMinutes: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationsPermissionIfNeeded()
        acquireWakeLock()
        initializeViews()
        setupSpinner()
        setupListeners()
        initializeTextToSpeech()
        setupBroadcastReceiver()
    }

    // Solo POST_NOTIFICATIONS (Android 13+) requiere petición en runtime
    private fun requestNotificationsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(
                    this,
                    perm
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(perm), 101)
            }
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        // Mantiene CPU despierta en background (recomendado). La pantalla la manejamos con FLAG_KEEP_SCREEN_ON.
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HornoAlarma::TimerWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
    }


    private fun initializeViews() {
        spinnerRecipes = findViewById(R.id.spinner_recipes)
        layoutCustomTimes = findViewById(R.id.layout_custom_times)
        editLeftTime = findViewById(R.id.edit_left_time)
        editRightTime = findViewById(R.id.edit_right_time)
        editCenterTime = findViewById(R.id.edit_center_time)
        layoutTimer = findViewById(R.id.layout_timer)
        textTimeDisplay = findViewById(R.id.text_time_display)
        textCurrentStep = findViewById(R.id.text_current_step)
        textStepCounter = findViewById(R.id.text_step_counter)
        layoutProgress = findViewById(R.id.layout_progress)
        layoutProgressDots = findViewById(R.id.layout_progress_dots)
        layoutControls = findViewById(R.id.layout_controls)
        btnStartPause = findViewById(R.id.btn_start_pause)
        btnReset = findViewById(R.id.btn_reset)
        layoutTips = findViewById(R.id.layout_tips)
        textRecipeTips = findViewById(R.id.text_recipe_tips)
        btnGuides = findViewById(R.id.btn_guides)
    }

    private fun setupSpinner() {
        val recipeOptions = listOf(
            "-- Elegir Receta --",
            "Pan Especial (40 min)",
            "Pan de Piso (45 min)",
            "Personalizado"
        )

        val adapter = ArrayAdapter(this, R.layout.spinner_item, recipeOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRecipes.adapter = adapter
    }

    private fun setupListeners() {
        spinnerRecipes.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                when (position) {
                    0 -> {
                        selectedRecipe = ""
                        hideTimerViews()
                    }

                    1 -> {
                        selectedRecipe = "pan-especial"
                        layoutCustomTimes.visibility = View.GONE
                        showTimerViews()
                    }

                    2 -> {
                        selectedRecipe = "pan-piso"
                        layoutCustomTimes.visibility = View.GONE
                        showTimerViews()
                    }

                    3 -> {
                        selectedRecipe = "personalizado"
                        layoutCustomTimes.visibility = View.VISIBLE
                        showTimerViews()
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnStartPause.setOnClickListener {
            if (isRunning) {
                pauseTimer()
            } else {
                startTimer()
            }
        }

        btnReset.setOnClickListener {
            resetTimer()
        }

        btnGuides.setOnClickListener {
            showGuidesDialog()
        }
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this, this).apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("MainActivity", "TTS onStart: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d("MainActivity", "TTS onDone: $utteranceId")
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e("MainActivity", "TTS onError: $utteranceId")
                }
            })
        }
    }


    private fun setupBroadcastReceiver() {
        timerUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "TIMER_UPDATE" -> {
                        timeLeft = intent.getLongExtra("timeLeft", 0L)
                        currentStep = intent.getIntExtra("currentStep", 0)
                        updateTimeDisplay(timeLeft / 1000)
                        updateStepCounter()
                        updateProgressDots()
                    }

                    "TIMER_STEP_FINISHED" -> {
                        val recipe = getCurrentRecipe() ?: return
                        currentStep++
                        currentStepName = recipe.steps[currentStep].name
                    }

                    "TIMER_ALL_FINISHED" -> {
                        val recipe = getCurrentRecipe() ?: return
                        isRunning = false
                        btnStartPause.text = getString(R.string.start_timer)
                        btnStartPause.setBackgroundResource(R.drawable.button_start_background)
                        resetTimer()
                    }
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale.forLanguageTag("es-ES")
            val result = textToSpeech?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Idioma español no soportado para voz", Toast.LENGTH_SHORT)
                    .show()
            } else {
                textToSpeech?.setSpeechRate(0.6f)
                textToSpeech?.setPitch(1.0f)
            }
        }
    }

    private fun showTimerViews() {
        layoutTimer.visibility = View.VISIBLE
        layoutProgress.visibility = View.VISIBLE
        layoutControls.visibility = View.VISIBLE
        layoutTips.visibility = View.VISIBLE

        val recipe = getCurrentRecipe()
        recipe?.let {
            textRecipeTips.text = it.tips
            createProgressDots(it.steps.size)
            updateStepCounter()
        }
    }

    private fun hideTimerViews() {
        layoutTimer.visibility = View.GONE
        layoutProgress.visibility = View.GONE
        layoutControls.visibility = View.GONE
        layoutTips.visibility = View.GONE
        layoutCustomTimes.visibility = View.GONE
    }

    private fun getCurrentRecipe(): Recipe? {
        return when (selectedRecipe) {
            "personalizado" -> {
                val leftTime = editLeftTime.text.toString().toIntOrNull() ?: 15
                val rightTime = editRightTime.text.toString().toIntOrNull() ?: 15
                val centerTime = editCenterTime.text.toString().toIntOrNull() ?: 10
                Recipe(
                    name = "Personalizado",
                    steps = listOf(
                        Step("Lado Izquierdo", leftTime),
                        Step("Lado Derecho", rightTime),
                        Step("Centro", centerTime)
                    ),
                    tips = "Tiempos personalizados según tu experiencia y tipo de producto."
                )
            }

            else -> recipes[selectedRecipe]
        }
    }

    private fun createProgressDots(stepCount: Int) {
        layoutProgressDots.removeAllViews()

        val dotContainer = LinearLayout(this)
        dotContainer.orientation = LinearLayout.HORIZONTAL
        dotContainer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        for (i in 0 until stepCount) {
            val stepContainer = LinearLayout(this)
            stepContainer.orientation = LinearLayout.VERTICAL
            stepContainer.gravity = android.view.Gravity.CENTER
            stepContainer.layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )

            val dot = View(this)
            val size = dpToPx(24)
            val dotParams = LinearLayout.LayoutParams(size, size)
            dotParams.setMargins(0, 0, 0, dpToPx(8))
            dot.layoutParams = dotParams
            dot.setBackgroundResource(R.drawable.progress_dot_inactive)

            val label = TextView(this)
            val recipe = getCurrentRecipe()
            label.text = recipe?.steps?.get(i)?.name?.split(" ")?.last() ?: ""
            label.textSize = 10f
            label.gravity = android.view.Gravity.CENTER
            // Compat: evita getColor(theme)
            label.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))

            stepContainer.addView(dot)
            stepContainer.addView(label)
            dotContainer.addView(stepContainer)
        }

        layoutProgressDots.addView(dotContainer)
    }

    private fun updateProgressDots() {
        val dotContainer = layoutProgressDots.getChildAt(0) as? LinearLayout ?: return

        for (i in 0 until dotContainer.childCount) {
            val stepContainer = dotContainer.getChildAt(i) as LinearLayout
            val dot = stepContainer.getChildAt(0)

            when {
                i < currentStep -> dot.setBackgroundResource(R.drawable.progress_dot_completed)
                i == currentStep -> dot.setBackgroundResource(R.drawable.progress_dot_active)
                else -> dot.setBackgroundResource(R.drawable.progress_dot_inactive)
            }
        }
    }

    private fun startTimer() {
        val recipe = getCurrentRecipe() ?: return

        if (currentStep == 0 && timeLeft == 0L) {
            currentStep = 0
            timeLeft = recipe.steps[0].timeMinutes * 60L * 1000
            currentStepName = recipe.steps[0].name
            playAlarmAndVoice("Iniciando horneado de ${recipe.name}. Colocar el soplete en el ${recipe.steps[0].name} por ${recipe.steps[0].timeMinutes} minutos")
        }

        isRunning = true
        btnStartPause.text = getString(R.string.pause_timer)
        btnStartPause.setBackgroundResource(R.drawable.button_pause_background)

        // Mantener pantalla encendida mientras corre el temporizador
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Mantener CPU activa (protegido por API para timeout)
        wakeLock?.acquire(10 * 60 * 1000L) // 10 min max

        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_START_TIMER
            putExtra(TimerService.EXTRA_RECIPE_NAME, recipe.name)
            putExtra(TimerService.EXTRA_STEP_NAME, currentStepName)
            putExtra(TimerService.EXTRA_TIME_MINUTES, recipe.steps[0].timeMinutes)
            putExtra(TimerService.EXTRA_STEPS, Gson().toJson(recipe.steps))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        updateStepCounter()
        updateProgressDots()
    }

    private fun pauseTimer() {
        isRunning = false
        btnStartPause.text = getString(R.string.continue_timer)
        btnStartPause.setBackgroundResource(R.drawable.button_start_background)

        if (wakeLock?.isHeld == true) wakeLock?.release()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_PAUSE_TIMER
        }
        startService(intent)
    }

    private fun resetTimer() {
        isRunning = false
        currentStep = 0
        timeLeft = 0L
        currentStepName = ""

        if (wakeLock?.isHeld == true) wakeLock?.release()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        btnStartPause.text = getString(R.string.start_timer)
        btnStartPause.setBackgroundResource(R.drawable.button_start_background)
        textTimeDisplay.text = getString(R.string.time_display)
        textCurrentStep.text = getString(R.string.ready_to_start)
        updateStepCounter()
        updateProgressDots()

        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_RESET_TIMER
        }
        startService(intent)
    }


    private fun updateTimeDisplay(seconds: Long) {
        val mins = seconds / 60
        val secs = seconds % 60
        textTimeDisplay.text = String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }

    private fun updateStepCounter() {
        val recipe = getCurrentRecipe()
        if (recipe != null) {
            textStepCounter.text =
                String.format(getString(R.string.step_counter), currentStep + 1, recipe.steps.size)
            if (currentStep < recipe.steps.size) {
                textCurrentStep.text =
                    currentStepName.ifEmpty { getString(R.string.ready_to_start) }
            }
        }
    }

    private fun playAlarmAndVoice(message: String) {
        Thread {
            var localToneGenerator: ToneGenerator? = null
            try {
                Log.d("MainActivity", "playAlarmAndVoice: Starting tone generation.")
                localToneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                val durations = intArrayOf(200, 200, 200, 200, 500)
                for (dur in durations) {
                    localToneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, dur)
                    Thread.sleep((dur + 50).toLong())
                }
                Thread.sleep(500) // Pausa antes de hablar
            } catch (e: Exception) {
                Log.e("MainActivity", "Error in tone generation thread", e)
            } finally {
                localToneGenerator?.release()
                Log.d("MainActivity", "playAlarmAndVoice: ToneGenerator released.")
            }

            runOnUiThread {
                Log.d("MainActivity", "playAlarmAndVoice: Starting TTS on UI thread.")
                var fullMessage = message
                val randomTip = getRandomTip()
                fullMessage += ". Consejo del día: $randomTip"
                speakCompat(fullMessage)
            }
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

    private fun showGuidesDialog() {
        val guideKeys = guides.keys.toTypedArray()
        val guideNames = arrayOf(
            "🧫 Levadura", "✨ Mejorador", "🌡️ Temperatura", "💨 Vapor", "🍞 Masa",
            "👐 Amasado", "⏰ Reposo", "🌾 Harina", "🧂 Sal", "💧 Agua"
        )

        AlertDialog.Builder(this)
            .setTitle("📖 Guías de Panadería")
            .setItems(guideNames) { _, which ->
                val selectedGuide = guides[guideKeys[which]]
                selectedGuide?.let {
                    showGuideDetail(guideNames[which], it)
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showGuideDetail(title: String, content: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(content)
            .setPositiveButton("🔊 Escuchar") { _, _ ->
                speakCompat(content)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(timerUpdateReceiver)
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(timerUpdateReceiver, IntentFilter("TIMER_UPDATE"))
        registerReceiver(timerUpdateReceiver, IntentFilter("TIMER_STEP_FINISHED"))
        registerReceiver(timerUpdateReceiver, IntentFilter("TIMER_ALL_FINISHED"))
        if (isRunning) {
            updateStepCounter()
            updateProgressDots()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Toast.makeText(
                    this,
                    "Sin permiso de notificaciones, la notificación del temporizador puede no mostrarse.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}