
package com.vibe.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val AlexBackground = Color(0xFF090B18)
private val AlexPanel = Color(0xFF15182B)
private val AlexPurple = Color(0xFF9B70FF)
private val AlexLavender = Color(0xFFCE9BFF)
private val AlexPink = Color(0xFFFF7FA8)
private val AlexCyan = Color(0xFF69F0D1)
private val AlexWhite = Color(0xFFF3F0FF)
private val AlexMuted = Color(0xFFAAA8C5)
private val AlexBorder = Color(0xFF34304F)

private enum class AlexMode {
    READY, LISTENING, THINKING, SPEAKING
}

@Composable
fun AlexVoiceScreen(
    onOpenChat: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Готов к разговору") }
    var mode by remember { mutableStateOf(AlexMode.READY) }
    var userText by remember { mutableStateOf("") }
    var alexText by remember {
        mutableStateOf("Привет! Я Алекс. Нажми на микрофон — и поговорим.")
    }

    var recognizer by remember {
        mutableStateOf<SpeechRecognizer?>(null)
    }
    var speechEngine by remember {
        mutableStateOf<TextToSpeech?>(null)
    }
    var speechReady by remember { mutableStateOf(false) }
    var responseJob by remember { mutableStateOf<Job?>(null) }
    var ignoreRecognizerErrors by remember { mutableStateOf(false) }
    var requestToken by remember { mutableIntStateOf(0) }

    val conversation = remember {
        mutableStateListOf<Pair<String, String>>()
    }

    val model = remember {
        Firebase.ai(
            backend = GenerativeBackend.googleAI()
        ).generativeModel("gemini-3.8-flash")
    }

    fun stopSpeaking() {
        speechEngine?.stop()

        if (mode == AlexMode.SPEAKING) {
            mode = AlexMode.READY
            status = "Готов к разговору"
        }
    }

    fun stopEverything() {
        requestToken++
        ignoreRecognizerErrors = true

        responseJob?.cancel()
        responseJob = null

        recognizer?.cancel()
        speechEngine?.stop()

        mode = AlexMode.READY
        status = "Остановлено"
    }

    fun speakAnswer(text: String) {
        val engine = speechEngine

        if (engine == null || !speechReady) {
            mode = AlexMode.READY
            status = "Голос загружается или недоступен"
            return
        }

        engine.stop()
        engine.setPitch(0.96f)
        engine.setSpeechRate(0.96f)

        mode = AlexMode.SPEAKING
        status = "Алекс говорит…"

        val result = engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            "ALEX_REPLY"
        )

        if (result == TextToSpeech.ERROR) {
            mode = AlexMode.READY
            status = "Не удалось включить голос"
        }
    }

    fun askAlex(message: String) {
        if (message.isBlank() || responseJob?.isActive == true) {
            return
        }

        stopSpeaking()

        val cleanMessage = message.trim()
        userText = cleanMessage
        conversation.add("Пользователь" to cleanMessage)

        requestToken++
        val currentToken = requestToken

        mode = AlexMode.THINKING
        status = "Алекс обдумывает ответ…"

        responseJob = scope.launch {
            try {
                val history = conversation
                    .takeLast(8)
                    .joinToString("\n") { (speaker, text) ->
                        "$speaker: $text"
                    }

                val prompt = """
                    Ты — Алекс, ИИ-помощник приложения Vibe Chat.

                    Отвечай естественно, дружелюбно и по существу.
                    По умолчанию используй русский язык.
                    Учитывай историю разговора.
                    Формулируй ответ удобно для прослушивания вслух.
                    Не утверждай, что выполнил действие, если у тебя нет
                    инструмента для его выполнения.

                    История разговора:
                    $history

                    Ответь на последнее сообщение пользователя.
                """.trimIndent()

                val response = withContext(Dispatchers.IO) {
                    model.generateContent(prompt)
                }

                if (currentToken != requestToken) {
                    return@launch
                }

                val answer = response.text
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: "Не получилось подготовить ответ. Попробуй ещё раз."

                alexText = answer
                conversation.add("Алекс" to answer)
                speakAnswer(answer)

            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (currentToken == requestToken) {
                    mode = AlexMode.READY
                    status = "Не удалось получить ответ"
                    alexText =
                        "Не удалось получить ответ. Проверь интернет и настройки Firebase AI."
                }
            } finally {
                if (currentToken == requestToken) {
                    responseJob = null
                }
            }
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            status = "На устройстве недоступно распознавание речи"
            return
        }

        if (responseJob?.isActive == true) {
            status = "Сначала останови текущий ответ"
            return
        }

        stopSpeaking()

        val activeRecognizer = recognizer
        if (activeRecognizer == null) {
            status = "Распознавание речи ещё не готово"
            return
        }

        ignoreRecognizerErrors = false

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Говори, я слушаю"
            )
        }

        try {
            mode = AlexMode.LISTENING
            status = "Слушаю тебя…"
            activeRecognizer.startListening(intent)
        } catch (error: Exception) {
            mode = AlexMode.READY
            status = "Не удалось включить микрофон"
        }
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            mode = AlexMode.READY
            status = "Разреши доступ к микрофону в настройках"
        }
    }

    DisposableEffect(context) {
        var alive = true
        var engine: TextToSpeech? = null

        val newEngine = TextToSpeech(context) { result ->
            if (!alive) return@TextToSpeech

            val activeEngine = engine
            if (result == TextToSpeech.SUCCESS && activeEngine != null) {
                val languageResult =
                    activeEngine.setLanguage(Locale("ru", "RU"))

                speechReady =
                    languageResult != TextToSpeech.LANG_MISSING_DATA &&
                    languageResult != TextToSpeech.LANG_NOT_SUPPORTED &&
                    languageResult != TextToSpeech.ERROR

                if (!speechReady) {
                    status = "Установи русские голосовые данные на телефоне"
                }
            } else {
                speechReady = false
                status = "Не удалось запустить голосовой движок"
            }
        }

        engine = newEngine
        speechEngine = newEngine

        newEngine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (alive) {
                        mode = AlexMode.SPEAKING
                        status = "Алекс говорит…"
                    }
                }

                override fun onDone(utteranceId: String?) {
                    if (alive) {
                        mode = AlexMode.READY
                        status = "Готов к разговору"
                    }
                }

                @Deprecated("Deprecated in Android")
                override fun onError(utteranceId: String?) {
                    if (alive) {
                        mode = AlexMode.READY
                        status = "Ошибка воспроизведения голоса"
                    }
                }

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int
                ) {
                    if (alive) {
                        mode = AlexMode.READY
                        status = "Ошибка воспроизведения голоса"
                    }
                }
            }
        )

        onDispose {
            alive = false
            newEngine.stop()
            newEngine.shutdown()
            speechEngine = null
            speechReady = false
        }
    }

    DisposableEffect(context) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            recognizer = null
            onDispose { }
        } else {
            val speechRecognizer =
                SpeechRecognizer.createSpeechRecognizer(context)

            recognizer = speechRecognizer

            speechRecognizer.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        mode = AlexMode.LISTENING
                        status = "Слушаю тебя…"
                    }

                    override fun onBeginningOfSpeech() {
                        status = "Я тебя слушаю"
                    }

                    override fun onRmsChanged(rmsdB: Float) = Unit

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        status = "Распознаю речь…"
                    }

                    override fun onError(error: Int) {
                        if (ignoreRecognizerErrors) return

                        mode = AlexMode.READY
                        status = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH ->
                                "Не расслышал. Попробуй ещё раз"

                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                "Проверь подключение к сети"

                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Нет разрешения на микрофон"

                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "Не услышал речь"

                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                                "Распознавание занято. Попробуй ещё раз"

                            else ->
                                "Не удалось распознать речь"
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        if (ignoreRecognizerErrors) return

                        val spoken = results
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()
                            .orEmpty()

                        if (spoken.isNotBlank()) {
                            askAlex(spoken)
                        } else {
                            mode = AlexMode.READY
                            status = "Не расслышал. Попробуй ещё раз"
                        }
                    }

                    override fun onPartialResults(
                        partialResults: Bundle?
                    ) = Unit

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) = Unit
                }
            )

            onDispose {
                speechRecognizer.cancel()
                speechRecognizer.destroy()
                recognizer = null
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AlexPurple,
            onPrimary = Color.White,
            secondary = AlexCyan,
            onSecondary = AlexBackground,
            background = AlexBackground,
            onBackground = AlexWhite,
            surface = AlexPanel,
            onSurface = AlexWhite,
            surfaceVariant = Color(0xFF22223A),
            onSurfaceVariant = AlexMuted
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF101027),
                            AlexBackground,
                            Color(0xFF11182A)
                        )
                    )
                )
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AlexPurple.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.12f, size.height * 0.2f),
                        radius = size.minDimension * 0.6f
                    ),
                    radius = size.minDimension * 0.6f,
                    center = Offset(size.width * 0.12f, size.height * 0.2f)
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AlexCyan.copy(alpha = 0.07f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.9f, size.height * 0.8f),
                        radius = size.minDimension * 0.55f
                    ),
                    radius = size.minDimension * 0.55f,
                    center = Offset(size.width * 0.9f, size.height * 0.8f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        AlexPurple.copy(alpha = 0.30f),
                                        AlexCyan.copy(alpha = 0.16f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Голос Алекса",
                            tint = AlexCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Алекс",
                            color = AlexWhite,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "VIBE AURORA  ·  AI VOICE",
                            color = AlexMuted,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            stopEverything()
                            onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад к чатам",
                            tint = AlexWhite
                        )
                    }

                    IconButton(
                        onClick = {
                            stopEverything()
                            onOpenChat()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = "Открыть чат с Алексом",
                            tint = AlexCyan
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AlexEnergyOrb(
                            mode = mode,
                            modifier = Modifier.size(250.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = when (mode) {
                                AlexMode.LISTENING -> "Я тебя слушаю"
                                AlexMode.THINKING -> "Подбираю ответ…"
                                AlexMode.SPEAKING -> "Я говорю"
                                AlexMode.READY -> "Давай поговорим"
                            },
                            color = AlexWhite,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(7.dp))

                        Text(
                            text = status,
                            color = AlexMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(12.dp))

                        GradientVoiceWave(
                            active = mode != AlexMode.READY,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        )

                        Spacer(Modifier.height(10.dp))

                        Surface(
                            color = AlexPanel.copy(alpha = 0.94f),
                            shape = RoundedCornerShape(22.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                AlexBorder.copy(alpha = 0.8f)
                            ),
                            shadowElevation = 8.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                if (userText.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(AlexCyan)
                                        )

                                        Spacer(Modifier.width(7.dp))

                                        Text(
                                            text = "ТЫ",
                                            color = AlexCyan,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.5.sp
                                        )
                                    }

                                    Spacer(Modifier.height(6.dp))

                                    Text(
                                        text = userText,
                                        color = AlexWhite,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )

                                    Spacer(Modifier.height(14.dp))

                                    HorizontalDivider(
                                        color = AlexBorder,
                                        thickness = 1.dp
                                    )

                                    Spacer(Modifier.height(12.dp))
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(AlexPink)
                                    )

                                    Spacer(Modifier.width(7.dp))

                                    Text(
                                        text = "АЛЕКС",
                                        color = AlexPink,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp
                                    )
                                }

                                Spacer(Modifier.height(6.dp))

                                Text(
                                    text = alexText,
                                    color = AlexWhite,
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (mode == AlexMode.LISTENING) {
                                ignoreRecognizerErrors = true
                                recognizer?.stopListening()
                                mode = AlexMode.READY
                                status = "Запись остановлена"
                            } else if (
                                mode != AlexMode.THINKING &&
                                responseJob?.isActive != true
                            ) {
                                val granted =
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED

                                if (granted) {
                                    startListening()
                                } else {
                                    microphonePermission.launch(
                                        Manifest.permission.RECORD_AUDIO
                                    )
                                }
                            }
                        },
                        enabled = mode != AlexMode.THINKING,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlexPurple,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF51476D),
                            disabledContentColor = AlexMuted
                        ),
                        modifier = Modifier.size(76.dp)
                    ) {
                        Icon(
                            imageVector = if (mode == AlexMode.LISTENING) {
                                Icons.Default.Stop
                            } else {
                                Icons.Default.Mic
                            },
                            contentDescription = if (
                                mode == AlexMode.LISTENING
                            ) {
                                "Завершить запись"
                            } else {
                                "Говорить с Алексом"
                            },
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(Modifier.width(22.dp))

                    IconButton(
                        onClick = { stopEverything() },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        AlexPurple.copy(alpha = 0.24f),
                                        AlexCyan.copy(alpha = 0.13f)
                                    )
                                )
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Полностью остановить Алекса",
                            tint = AlexCyan,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(Modifier.height(9.dp))

                Text(
                    text = when (mode) {
                        AlexMode.LISTENING ->
                            "Нажми на микрофон, чтобы завершить запись"

                        AlexMode.THINKING ->
                            "Алекс готовит ответ — можно остановить кнопкой"

                        AlexMode.SPEAKING ->
                            "Кнопка справа останавливает голос"

                        AlexMode.READY ->
                            "Нажми на микрофон и говори по-русски"
                    },
                    color = AlexMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AlexEnergyOrb(
    mode: AlexMode,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "alex_particle_orb")

    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.045f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    AlexMode.LISTENING -> 600
                    AlexMode.THINKING -> 380
                    AlexMode.SPEAKING -> 480
                    AlexMode.READY -> 2200
                }
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "particle_pulse"
    )

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    AlexMode.LISTENING -> 4800
                    AlexMode.THINKING -> 2200
                    AlexMode.SPEAKING -> 3000
                    AlexMode.READY -> 10000
                }
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_rotation"
    )

    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    AlexMode.LISTENING -> 1300
                    AlexMode.THINKING -> 850
                    AlexMode.SPEAKING -> 1050
                    AlexMode.READY -> 3600
                }
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_drift"
    )

    val glow = when (mode) {
        AlexMode.LISTENING -> AlexCyan
        AlexMode.THINKING -> AlexPink
        AlexMode.SPEAKING -> AlexLavender
        AlexMode.READY -> AlexPurple
    }

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f

        val cloudRadius = size.minDimension * 0.365f * pulse
        val particleCount = 176
        val goldenAngle = Math.PI * (3.0 - sqrt(5.0))
        val rotationRadians = Math.toRadians(rotation.toDouble())

        val points = Array(particleCount) { Offset.Zero }
        val depths = FloatArray(particleCount)
        val particleSizes = FloatArray(particleCount)
        val particleAlphas = FloatArray(particleCount)

        // Мягкое свечение вокруг всей частицы-облака.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glow.copy(alpha = 0.23f),
                    AlexPurple.copy(alpha = 0.12f),
                    AlexCyan.copy(alpha = 0.055f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = cloudRadius * 1.8f
            ),
            radius = cloudRadius * 1.8f,
            center = Offset(cx, cy)
        )

        // Внутреннее свечение создаёт ощущение энергии внутри сферы.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.10f),
                    glow.copy(alpha = 0.11f),
                    AlexPurple.copy(alpha = 0.06f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = cloudRadius * 1.1f
            ),
            radius = cloudRadius * 1.1f,
            center = Offset(cx, cy)
        )

        // Распределяем частицы по поверхности воображаемой сферы.
        // Небольшое смещение по глубине создаёт объёмный эффект.
        for (i in 0 until particleCount) {
            val normalizedY =
                1f - 2f * (i + 0.5f) / particleCount

            val ringRadius = sqrt(
                (1f - normalizedY * normalizedY).coerceAtLeast(0f)
            )

            val angle =
                i * goldenAngle + rotationRadians

            val x3d = cos(angle).toFloat() * ringRadius
            val z3d = sin(angle).toFloat() * ringRadius

            val floatOffset =
                sin(drift + i * 0.37f).toFloat() * 0.035f

            val depth = (z3d + 1f) / 2f
            depths[i] = depth

            val projectedX =
                cx + x3d * cloudRadius +
                    cos(drift * 0.65f + i * 0.17f).toFloat() *
                    cloudRadius * floatOffset

            val projectedY =
                cy + normalizedY * cloudRadius +
                    z3d * cloudRadius * 0.20f +
                    sin(drift * 0.8f + i * 0.21f).toFloat() *
                    cloudRadius * floatOffset

            points[i] = Offset(projectedX, projectedY)

            particleSizes[i] =
                (0.75f + depth * 1.45f) *
                    if (mode == AlexMode.SPEAKING) 1.12f else 1f

            particleAlphas[i] =
                0.28f + depth * 0.65f
        }

        // Тонкие связи между близкими частицами формируют
        // живую нейронную сетку внутри энергетического облака.
        val maxLinkDistance = cloudRadius * 0.245f
        val maxLinkDistanceSquared =
            maxLinkDistance * maxLinkDistance

        for (i in 0 until particleCount) {
            val first = points[i]

            for (j in i + 1 until particleCount) {
                val second = points[j]
                val dx = first.x - second.x
                val dy = first.y - second.y
                val distanceSquared = dx * dx + dy * dy

                if (distanceSquared < maxLinkDistanceSquared) {
                    val distanceFactor =
                        1f - distanceSquared / maxLinkDistanceSquared

                    val depthFactor =
                        (depths[i] + depths[j]) * 0.5f

                    drawLine(
                        color = glow.copy(
                            alpha = 0.055f +
                                distanceFactor * 0.12f * depthFactor
                        ),
                        start = first,
                        end = second,
                        strokeWidth = 0.55.dp.toPx()
                    )
                }
            }
        }

        // Светящиеся частицы. Передний план ярче, задний мягче.
        for (i in 0 until particleCount) {
            val point = points[i]
            val depth = depths[i]
            val particleRadius =
                particleSizes[i] * 0.72f * density

            val baseColor = when {
                i % 9 == 0 -> AlexCyan
                i % 7 == 0 -> AlexPink
                i % 5 == 0 -> AlexLavender
                else -> glow
            }

            val alpha = particleAlphas[i]

            if (depth > 0.58f) {
                drawCircle(
                    color = baseColor.copy(
                        alpha = alpha * 0.13f
                    ),
                    radius = particleRadius * 3.5f,
                    center = point
                )
            }

            drawCircle(
                color = baseColor.copy(alpha = alpha),
                radius = particleRadius,
                center = point
            )

            if (depth > 0.78f && i % 3 == 0) {
                drawCircle(
                    color = Color.White.copy(
                        alpha = alpha * 0.75f
                    ),
                    radius = particleRadius * 0.42f,
                    center = point
                )
            }
        }

        // Несколько свободных частиц вокруг облака.
        // Они подчёркивают движение и делают силуэт менее
        // похожим на обычный геометрически ровный шар.
        for (i in 0 until 24) {
            val angle =
                Math.toRadians(
                    rotation * 0.72 + i * 137.5
                ) + drift * 0.12

            val spread =
                cloudRadius * (
                    1.08f +
                        0.18f *
                        (0.5f + 0.5f *
                            sin(drift + i * 0.82f).toFloat())
                    )

            val x =
                cx + cos(angle).toFloat() * spread

            val y =
                cy + sin(angle).toFloat() *
                    spread * 0.78f

            val sizeFactor =
                if (i % 4 == 0) 1.8f else 1f

            val particleColor =
                if (i % 3 == 0) AlexCyan else AlexLavender

            drawCircle(
                color = particleColor.copy(
                    alpha = 0.35f +
                        0.25f *
                        abs(sin(drift + i.toFloat()))
                ),
                radius = sizeFactor * density,
                center = Offset(x, y)
            )
        }

        // Внешнее тонкое кольцо энергии.
        drawCircle(
            color = glow.copy(alpha = 0.23f),
            radius = cloudRadius * 1.12f,
            center = Offset(cx, cy),
            style = Stroke(width = 0.8.dp.toPx())
        )

        // Световой акцент вращается вокруг сферы.
        drawArc(
            color = AlexCyan.copy(alpha = 0.78f),
            startAngle = rotation,
            sweepAngle = 55f,
            useCenter = false,
            topLeft = Offset(
                cx - cloudRadius * 1.13f,
                cy - cloudRadius * 1.13f
            ),
            size = Size(
                cloudRadius * 2.26f,
                cloudRadius * 2.26f
            ),
            style = Stroke(
                width = 1.6.dp.toPx(),
                cap = StrokeCap.Round
            )
        )

        drawArc(
            color = AlexPink.copy(alpha = 0.48f),
            startAngle = rotation + 180f,
            sweepAngle = 35f,
            useCenter = false,
            topLeft = Offset(
                cx - cloudRadius * 1.08f,
                cy - cloudRadius * 1.08f
            ),
            size = Size(
                cloudRadius * 2.16f,
                cloudRadius * 2.16f
            ),
            style = Stroke(
                width = 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}

@Composable
private fun GradientVoiceWave(
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "alex_voice_wave")

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (active) 600 else 2200
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val colors = listOf(
        AlexPurple,
        AlexLavender,
        AlexPink,
        AlexCyan,
        AlexPink,
        AlexLavender,
        AlexPurple
    )

    Canvas(modifier = modifier) {
        val count = 29
        val barWidth = 4.dp.toPx()
        val gap = (size.width - count * barWidth) / (count + 1)
        val centerY = size.height / 2f
        val maxHeight = size.height * 0.82f
        val brush = Brush.horizontalGradient(colors)

        for (i in 0 until count) {
            val x = gap + i * (barWidth + gap)
            val wave = abs(sin(phase + i * 0.43f))
            val envelope = 0.18f + 0.82f * abs(
                sin(i * 0.21f + 0.8f)
            )

            val movement = if (active) {
                wave
            } else {
                0.18f + wave * 0.24f
            }

            val barHeight =
                (0.10f + movement * envelope) * maxHeight

            drawRoundRect(
                brush = brush,
                topLeft = Offset(
                    x,
                    centerY - barHeight / 2f
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(
                    barWidth / 2f,
                    barWidth / 2f
                )
            )
        }
    }
}
