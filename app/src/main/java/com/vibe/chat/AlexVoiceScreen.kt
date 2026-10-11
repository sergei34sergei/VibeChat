package com.vibe.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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

@Composable
fun AlexVoiceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var draft by remember { mutableStateOf("") }
    var answer by remember {
        mutableStateOf(
            "Привет! Я Алекс, твой помощник Vibe Chat. " +
                "Нажми на микрофон или напиши мне."
        )
    }

    // Полная техническая ошибка — её можно скопировать.
    var lastError by remember { mutableStateOf("") }

    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var fullScreenFace by remember { mutableStateOf(true) }
    var voiceError by remember { mutableStateOf("") }

    fun copyText(label: String, text: String) {
        if (text.isBlank()) {
            Toast.makeText(
                context,
                "Пока нечего копировать",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            val clipboard = context.getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager

            clipboard.setPrimaryClip(
                ClipData.newPlainText(label, text)
            )

            Toast.makeText(
                context,
                "Скопировано! Теперь вставь текст сюда в чат.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Не удалось скопировать: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun formatError(e: Exception): String {
        return buildString {
            appendLine("Ошибка Vibe Chat / Gemini / Firebase")
            appendLine("Тип: ${e.javaClass.name}")
            appendLine("Сообщение: ${e.message ?: "не указано"}")

            var cause = e.cause
            var count = 0

            while (cause != null && count < 5) {
                appendLine(
                    "Причина ${count + 1}: " +
                        "${cause.javaClass.name}: " +
                        "${cause.message ?: "без подробностей"}"
                )
                cause = cause.cause
                count++
            }
        }.trim()
    }

    val tts = remember {
        TextToSpeech(context) { status ->
            mainHandler.post {
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                } else {
                    ttsReady = false
                    voiceError =
                        "Не удалось запустить голосовой движок Android."
                }
            }
        }
    }

    LaunchedEffect(ttsReady) {
        if (ttsReady) {
            try {
                val result = tts.setLanguage(Locale("ru", "RU"))

                if (
                    result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    ttsReady = false
                    voiceError =
                        "Русский голос не установлен. " +
                            "Проверь настройки синтеза речи Android."
                } else {
                    tts.setPitch(0.88f)
                    tts.setSpeechRate(0.90f)
                    voiceError = ""
                }
            } catch (_: Exception) {
                ttsReady = false
                voiceError = "Не удалось настроить русский голос."
            }
        }
    }

    DisposableEffect(tts) {
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    mainHandler.post {
                        speaking = true
                    }
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        speaking = false
                    }
                }

                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        speaking = false
                        voiceError = "Не удалось озвучить ответ."
                    }
                }
            }
        )

        onDispose {
            try {
                tts.stop()
                tts.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    val recognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
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
                            "Сбой голосового сервиса. Попробуй ещё раз."

                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Разреши доступ к микрофону."

                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Проверь интернет."

                        SpeechRecognizer.ERROR_NO_MATCH ->
                            "Я не расслышал слова. Попробуй ещё раз."

                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            "Микрофон занят. Подожди немного."

                        SpeechRecognizer.ERROR_SERVER ->
                            "Сервис распознавания временно недоступен."

                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "Я не услышал речь. Нажми микрофон и говори."

                        else ->
                            "Не удалось распознать речь. Код: $error"
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

                    if (words.isNotBlank()) {
                        draft = words
                        voiceError = ""
                    } else {
                        voiceError =
                            "Речь не распознана. Попробуй ещё раз."
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

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

    fun startRecognition() {
        if (recognizer == null) {
            voiceError =
                "На телефоне не найден сервис распознавания речи."

            Toast.makeText(
                context,
                voiceError,
                Toast.LENGTH_LONG
            ).show()
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
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "ru-RU"
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "ru-RU"
                )
                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
                )
                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
                )
                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "Говори, Алекс слушает"
                )
            }

            recognizer.startListening(intent)
        } catch (_: Exception) {
            listening = false
            voiceError = "Не получилось включить микрофон."

            Toast.makeText(
                context,
                voiceError,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun speakText(text: String) {
        if (!ttsReady) {
            speaking = false
            voiceError =
                "Голос пока не готов. Проверь настройки синтеза речи."
            return
        }

        try {
            val result = tts.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "alex_reply"
            )

            if (result == TextToSpeech.ERROR) {
                speaking = false
                voiceError =
                    "Голосовой движок не смог озвучить текст."
            } else {
                voiceError = ""
            }
        } catch (_: Exception) {
            speaking = false
            voiceError = "Ошибка запуска озвучивания."
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecognition()
        } else {
            listening = false
            voiceError =
                "Разреши доступ к микрофону в настройках."
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
                        else -> "ГОЛОСОВОЙ ПОМОЩНИК"
                    },
                    color = AlexCyan,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp
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
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2.2f
                        )
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
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
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
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
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
                            .border(
                                1.dp,
                                AlexCyan,
                                CircleShape
                            )
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
                .padding(14.dp)
        ) {
            Text(
                "ОТВЕТ АЛЕКСА",
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
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    answer,
                    color = Color(0xFFF2F5FF),
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )

                if (voiceError.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))

                    Text(
                        voiceError,
                        color = Color(0xFFFFB86C),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }

                if (lastError.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))

                    Text(
                        "ТЕХНИЧЕСКАЯ ОШИБКА",
                        color = Color(0xFFFFB86C),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        lastError,
                        color = Color(0xFFFFD9B0),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    copyText(
                        "Ошибка Gemini Firebase",
                        lastError.ifBlank {
                            voiceError.ifBlank {
                                "Техническая ошибка пока не записана."
                            }
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AlexCyan
                )
            ) {
                Text("📋 Копировать ошибку")
            }

            OutlinedButton(
                onClick = {
                    copyText("Ответ Алекса", answer)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFB7A5FF)
                )
            ) {
                Text("📋 Копировать ответ Алекса")
            }

            if (busy) {
                Spacer(Modifier.height(8.dp))

                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = AlexCyan,
                    trackColor = Color(0xFF202744)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    if (listening) {
                        try {
                            recognizer?.stopListening()
                        } catch (_: Exception) {
                            recognizer?.cancel()
                        }

                        listening = false
                    } else if (
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
                },
                modifier = Modifier.weight(0.9f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AlexCyan
                )
            ) {
                Text(
                    if (listening) "■ Стоп" else "🎙 Микрофон",
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = {
                    val question = draft.trim()

                    if (question.isNotBlank() && !busy) {
                        draft = ""
                        busy = true
                        voiceError = ""
                        lastError = ""
                        speaking = false

                        scope.launch {
                            try {
                                val model = Firebase.ai(
                                    backend = GenerativeBackend.googleAI()
                                ).generativeModel("gemini-3.8-flash")

                                val response = model.generateContent(
                                    "Ты Алекс — спокойный, доброжелательный " +
                                        "голосовой помощник приложения Vibe Chat. " +
                                        "Отвечай на русском естественно, понятно " +
                                        "и не слишком длинно. " +
                                        "Пользователь говорит: $question"
                                )

                                val reply = response.text

                                answer = reply
                                    ?: "Gemini вернул пустой ответ. " +
                                        "Попробуй задать вопрос ещё раз."

                                if (reply.isNullOrBlank()) {
                                    lastError =
                                        "Модель не вернула текст ответа. " +
                                            "Проверь настройки Gemini и Firebase."
                                }

                                if (ttsReady && !reply.isNullOrBlank()) {
                                    speakText(reply)
                                } else if (!ttsReady) {
                                    speaking = false
                                    voiceError =
                                        "Ответ получен, но голос не готов. " +
                                            "Проверь русский голос в Android."
                                }
                            } catch (e: Exception) {
                                lastError = formatError(e)

                                answer =
                                    "Не удалось получить ответ от Алекса.\n\n" +
                                        "Нажми «Копировать ошибку» и отправь " +
                                        "скопированный текст в ChatGPT."

                                speaking = false

                                Toast.makeText(
                                    context,
                                    "Ошибка связи. Текст ошибки сохранён.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1.1f),
                enabled = !busy && draft.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7049D8),
                    disabledContainerColor = Color(0xFF39334D)
                )
            ) {
                Text(
                    if (busy) "Думаю…" else "Спросить Алекса",
                    fontSize = 12.sp
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
