
package com.vibe.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.launch
import java.util.Locale

private val AlexCyan = Color(0xFF42DFFF)
private val AlexBlue = Color(0xFF208DFF)
private val AlexPurple = Color(0xFF9260FF)
private val AlexPanel = Color(0xE8111A30)

private data class AlexMessage(
    val role: String,
    val text: String
)

@Composable
fun AlexVoiceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var voiceSessionActive by remember { mutableStateOf(false) }
    var fullScreenFace by remember { mutableStateOf(true) }
    var voiceError by remember { mutableStateOf("") }

    val messages = remember {
        mutableStateListOf(
            AlexMessage(
                "assistant",
                "Привет! Я Алекс, твой помощник Vibe Chat. " +
                    "Можешь написать мне или включить голосовой разговор."
            )
        )
    }

    val tts = remember {
        TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (status != TextToSpeech.SUCCESS) {
                voiceError = "Не удалось запустить синтез речи."
            }
        }
    }

    LaunchedEffect(ttsReady) {
        if (ttsReady) {
            val result = tts.setLanguage(Locale("ru", "RU"))
            tts.setPitch(0.92f)
            tts.setSpeechRate(0.94f)

            if (
                result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                voiceError =
                    "На телефоне не установлен русский голос. " +
                    "Проверь настройки синтеза речи."
            }
        }
    }

    val toneGenerator = remember {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 75)
        } catch (_: Exception) {
            null
        }
    }

    DisposableEffect(tts, toneGenerator) {
        onDispose {
            try {
                tts.stop()
                tts.shutdown()
            } catch (_: Exception) {
            }

            try {
                toneGenerator?.release()
            } catch (_: Exception) {
            }
        }
    }

    val recognizer = remember(context) {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                SpeechRecognizer.createSpeechRecognizer(context)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun startRecognition() {
        if (!voiceSessionActive || busy || speaking) return

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            voiceError = "Нужно разрешение на использование микрофона."
            return
        }

        if (recognizer == null) {
            voiceError =
                "На телефоне не найден сервис распознавания речи."
            return
        }

        try {
            voiceError = ""

            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "ru-RU"
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "Говори, Алекс слушает"
                )
            }

            recognizer.startListening(intent)
        } catch (e: Exception) {
            listening = false
            voiceError = "Не получилось включить микрофон."
            Log.e("AlexVoice", "startRecognition failed", e)
        }
    }

    val latestVoiceActive by rememberUpdatedState(voiceSessionActive)
    val latestBusy by rememberUpdatedState(busy)
    val latestSpeaking by rememberUpdatedState(speaking)
    val latestStartRecognition by rememberUpdatedState<() -> Unit>({
        startRecognition()
    })

    fun sendMessage(questionText: String, speakReply: Boolean) {
        val question = questionText.trim()

        if (question.isBlank() || busy) return

        messages.add(AlexMessage("user", question))
        busy = true
        listening = false
        speaking = false
        voiceError = ""

        scope.launch {
            try {
                val history = messages
                    .dropLast(1)
                    .takeLast(16)

                val prompt = buildString {
                    append(
                        "Ты Алекс — спокойный, доброжелательный " +
                            "голосовой помощник приложения Vibe Chat. "
                    )
                    append(
                        "Отвечай естественно и понятно на русском языке. "
                    )
                    append(
                        "Учитывай историю диалога и предыдущие сообщения. "
                    )
                    append(
                        "Не утверждай, что выполнил действия, " +
                            "которые фактически не выполнял. "
                    )
                    append(
                        "Не выдумывай доступ к телефону, файлам " +
                            "или личным данным пользователя.\n\n"
                    )

                    append("История разговора:\n")

                    history.forEach { message ->
                        val speaker =
                            if (message.role == "user") {
                                "Пользователь"
                            } else {
                                "Алекс"
                            }

                        append(speaker)
                        append(": ")
                        append(message.text)
                        append("\n")
                    }

                    append("Пользователь: ")
                    append(question)
                    append("\nАлекс:")
                }

                val model = Firebase.ai(
                    backend = GenerativeBackend.googleAI()
                ).generativeModel("gemini-3.8-flash")

                val response = model.generateContent(prompt)
                val reply = response.text?.trim().orEmpty()

                if (reply.isBlank()) {
                    throw IllegalStateException(
                        "Модель вернула пустой ответ."
                    )
                }

                messages.add(AlexMessage("assistant", reply))

                if (speakReply) {
                    if (ttsReady) {
                        speaking = true

                        val result = tts.speak(
                            reply,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "alex_reply_${System.currentTimeMillis()}"
                        )

                        if (result == TextToSpeech.ERROR) {
                            speaking = false
                            voiceError =
                                "Не удалось озвучить ответ. " +
                                "Проверь настройки голосового движка."

                            if (voiceSessionActive) {
                                mainHandler.postDelayed({
                                    latestStartRecognition()
                                }, 900)
                            }
                        }
                    } else {
                        voiceError =
                            "Ответ получен, но русский голос пока не готов."

                        if (voiceSessionActive) {
                            mainHandler.postDelayed({
                                latestStartRecognition()
                            }, 1200)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AlexVoice", "Gemini request failed", e)

                val details = e.localizedMessage
                    ?.takeIf { it.isNotBlank() }
                    ?: "неизвестная ошибка"

                messages.add(
                    AlexMessage(
                        "assistant",
                        "Не удалось получить ответ от AI. " +
                            "Проверь интернет, Firebase AI Logic, " +
                            "App Check и доступность модели.\n\n" +
                            "Подробности: $details"
                    )
                )

                voiceError = "Не удалось получить ответ от Gemini."

                if (voiceSessionActive) {
                    mainHandler.postDelayed({
                        latestStartRecognition()
                    }, 1400)
                }

                Toast.makeText(
                    context,
                    "Алекс не смог получить ответ",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                busy = false
            }
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceError = ""
            startRecognition()
        } else {
            voiceSessionActive = false
            listening = false
            voiceError =
                "Разреши доступ к микрофону, чтобы говорить с Алексом."
        }
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                    voiceError = ""
                }

                override fun onBeginningOfSpeech() {
                    listening = true
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    listening = false
                }

                override fun onError(error: Int) {
                    listening = false

                    voiceError = when (error) {
                        SpeechRecognizer.ERROR_AUDIO ->
                            "Проблема с микрофоном."

                        SpeechRecognizer.ERROR_CLIENT ->
                            "Сервис распознавания перезапускается."

                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Разреши доступ к микрофону."

                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Проверь интернет для распознавания речи."

                        SpeechRecognizer.ERROR_NO_MATCH ->
                            "Я не расслышал слова. Попробуй ещё раз."

                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            "Микрофон занят. Пробую снова."

                        SpeechRecognizer.ERROR_SERVER ->
                            "Сервис распознавания временно недоступен."

                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "Я не услышал речь. Можешь начать говорить."

                        else ->
                            "Не удалось распознать речь."
                    }

                    val retryable =
                        error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_CLIENT ||
                        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY

                    if (retryable && latestVoiceActive) {
                        mainHandler.postDelayed({
                            if (
                                latestVoiceActive &&
                                !latestBusy &&
                                !latestSpeaking
                            ) {
                                latestStartRecognition()
                            }
                        }, 1000)
                    }
                }

                override fun onResults(results: Bundle?) {
                    listening = false

                    val words = results
                        ?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        ?.firstOrNull()
                        .orEmpty()
                        .trim()

                    if (words.isNotBlank()) {
                        voiceError = ""
                        sendMessage(words, speakReply = true)
                    } else {
                        voiceError = "Речь не распознана."

                        if (latestVoiceActive) {
                            mainHandler.postDelayed({
                                latestStartRecognition()
                            }, 900)
                        }
                    }
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {}

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {}
            }
        )

        onDispose {
            try {
                recognizer?.cancel()
                recognizer?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    DisposableEffect(tts) {
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    speaking = true
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        speaking = false

                        if (latestVoiceActive) {
                            mainHandler.postDelayed({
                                if (
                                    latestVoiceActive &&
                                    !latestBusy &&
                                    !latestSpeaking
                                ) {
                                    latestStartRecognition()
                                }
                            }, 650)
                        }
                    }
                }

                @Deprecated("Deprecated in Android API")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        speaking = false
                        voiceError = "Не удалось завершить озвучку."

                        if (latestVoiceActive) {
                            mainHandler.postDelayed({
                                latestStartRecognition()
                            }, 900)
                        }
                    }
                }
            }
        )

        onDispose {
            try {
                tts.stop()
            } catch (_: Exception) {
            }
        }
    }

    val animation = rememberInfiniteTransition(
        label = "alex-animation"
    )

    val glow by animation.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alex-glow"
    )

    val rotation by animation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000)
        ),
        label = "alex-rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF060A16),
                        Color(0xFF0B1024),
                        Color(0xFF10102B)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    "ALEX AI",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    letterSpacing = 2.sp
                )

                Text(
                    when {
                        busy -> "ДУМАЮ И АНАЛИЗИРУЮ"
                        listening -> "● СЛУШАЮ ТЕБЯ"
                        speaking -> "● ОТВЕЧАЮ ГОЛОСОМ"
                        voiceSessionActive -> "ГОЛОСОВОЙ РЕЖИМ ВКЛЮЧЁН"
                        else -> "ГОЛОСОВОЙ ПОМОЩНИК"
                    },
                    color = AlexCyan,
                    fontSize = 10.sp,
                    letterSpacing = 1.1.sp
                )
            }

            TextButton(
                onClick = {
                    fullScreenFace = !fullScreenFace
                }
            ) {
                Text(
                    if (fullScreenFace) "Свернуть" else "На весь экран",
                    color = Color(0xFFB7A5FF),
                    fontSize = 11.sp
                )
            }
        }

        if (fullScreenFace) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.95f)
                    .padding(vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height * 0.43f
                    val r = size.minDimension * 0.32f * glow

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x6032BFFF),
                                Color(0x20305DFF),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = r * 1.9f
                        ),
                        radius = r * 1.9f,
                        center = Offset(cx, cy)
                    )

                    drawCircle(
                        color = AlexCyan.copy(alpha = 0.4f),
                        radius = r * 1.48f,
                        center = Offset(cx, cy)
                    )

                    drawCircle(
                        color = Color(0xFF080D20),
                        radius = r * 1.43f,
                        center = Offset(cx, cy)
                    )

                    drawCircle(
                        color = AlexBlue.copy(alpha = 0.75f),
                        radius = r * 1.26f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2.2f)
                    )

                    drawArc(
                        color = AlexCyan,
                        startAngle = rotation,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = Offset(
                            cx - r * 1.37f,
                            cy - r * 1.37f
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            r * 2.74f,
                            r * 2.74f
                        ),
                        style = Stroke(
                            width = 4f,
                            cap = StrokeCap.Round
                        )
                    )

                    drawArc(
                        color = AlexPurple,
                        startAngle = rotation + 160f,
                        sweepAngle = 65f,
                        useCenter = false,
                        topLeft = Offset(
                            cx - r * 1.23f,
                            cy - r * 1.23f
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            r * 2.46f,
                            r * 2.46f
                        ),
                        style = Stroke(
                            width = 3f,
                            cap = StrokeCap.Round
                        )
                    )
                }

                Image(
                    painter = painterResource(
                        id = R.drawable.alex_avatar
                    ),
                    contentDescription = "Аватарка Алекса",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(205.dp)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            color = AlexCyan.copy(alpha = 0.9f),
                            shape = CircleShape
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "A L E X",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 5.sp
                    )

                    Text(
                        when {
                            listening -> "СЛУШАЮ ТВОЙ ГОЛОС"
                            busy -> "ОБРАБАТЫВАЮ ИНФОРМАЦИЮ"
                            speaking -> "ГОВОРЮ С ТОБОЙ"
                            else -> "ТВОЙ ИИ-ПОМОЩНИК"
                        },
                        color = AlexCyan,
                        fontSize = 9.sp,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AlexPanel)
                    .border(
                        1.dp,
                        AlexCyan.copy(alpha = 0.35f),
                        RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(
                            id = R.drawable.alex_avatar
                        ),
                        contentDescription = "Алекс",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(1.dp, AlexCyan, CircleShape)
                    )

                    Text(
                        "ALEX AI НА СВЯЗИ",
                        color = AlexCyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(if (fullScreenFace) 0.92f else 1.4f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(AlexPanel)
                .border(
                    1.dp,
                    Color(0xFF283C62),
                    RoundedCornerShape(22.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                "ИСТОРИЯ РАЗГОВОРА",
                color = AlexCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                messages.forEach { message ->
                    val isUser = message.role == "user"

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (isUser) {
                            Alignment.End
                        } else {
                            Alignment.Start
                        }
                    ) {
                        Text(
                            if (isUser) "ТЫ" else "АЛЕКС",
                            color = if (isUser) {
                                Color(0xFFB7A5FF)
                            } else {
                                AlexCyan
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(3.dp))

                        Text(
                            text = message.text,
                            color = Color(0xFFF2F5FF),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            modifier = Modifier
                                .widthIn(max = 310.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isUser) {
                                        Color(0xFF302858)
                                    } else {
                                        Color(0xFF17243C)
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (isUser) {
                                        AlexPurple.copy(alpha = 0.45f)
                                    } else {
                                        AlexBlue.copy(alpha = 0.35f)
                                    },
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(10.dp)
                        )
                    }
                }

                if (busy) {
                    Text(
                        "Алекс думает…",
                        color = AlexCyan,
                        fontSize = 12.sp
                    )

                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = AlexCyan,
                        trackColor = Color(0xFF202744)
                    )
                }

                if (voiceError.isNotBlank()) {
                    Text(
                        voiceError,
                        color = Color(0xFFFFB86C),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "Скажи или напиши Алексу…",
                    color = Color(0xFF8792B7)
                )
            },
            shape = RoundedCornerShape(18.dp),
            maxLines = 3,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AlexCyan,
                unfocusedBorderColor = Color(0xFF303957),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = AlexCyan
            )
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    if (voiceSessionActive) {
                        voiceSessionActive = false
                        listening = false
                        speaking = false

                        try {
                            recognizer?.cancel()
                            tts.stop()
                        } catch (_: Exception) {
                        }

                        voiceError = "Голосовой режим выключен."
                    } else {
                        voiceSessionActive = true
                        voiceError = ""

                        try {
                            toneGenerator?.startTone(
                                ToneGenerator.TONE_PROP_ACK,
                                180
                            )
                        } catch (_: Exception) {
                        }

                        if (ttsReady) {
                            tts.stop()
                        }

                        if (
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            startRecognition()
                        } else {
                            micPermission.launch(
                                Manifest.permission.RECORD_AUDIO
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AlexCyan
                )
            ) {
                Text(
                    if (voiceSessionActive) {
                        "■ Стоп"
                    } else {
                        "🎙 Голос"
                    },
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = {
                    val question = draft.trim()

                    if (question.isNotBlank() && !busy) {
                        draft = ""
                        sendMessage(question, speakReply = false)
                    }
                },
                modifier = Modifier.weight(1.25f),
                enabled = !busy && draft.isNotBlank(),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7049D8),
                    disabledContainerColor = Color(0xFF39334D)
                )
            ) {
                Text(
                    if (busy) "Думаю…" else "Спросить",
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(5.dp))

        Text(
            "ALEX AI • VIBE CHAT",
            color = Color(0xFF63759B),
            fontSize = 9.sp,
            letterSpacing = 1.8.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
