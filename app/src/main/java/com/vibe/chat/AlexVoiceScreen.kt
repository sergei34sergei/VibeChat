
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin

private val AlexCyan = Color(0xFF55E8FF)
private val AlexBlue = Color(0xFF328BFF)
private val AlexPurple = Color(0xFF9A6BFF)
private val AlexBackground = Color(0xFF050817)
private val AlexPanel = Color(0xFF111A30)

private data class AlexMessage(
    val role: String,
    val text: String
)

@Composable
fun AlexVoiceScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val handler = remember { Handler(Looper.getMainLooper()) }

    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var voiceActive by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf("") }

    val messages = remember {
        mutableStateListOf(
            AlexMessage(
                "assistant",
                "Привет! Я Алекс. Напиши мне сообщение или нажми на микрофон, чтобы поговорить голосом."
            )
        )
    }

    val listState = rememberLazyListState()

    val tts = remember {
        TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (status != TextToSpeech.SUCCESS) {
                voiceError = "Не удалось запустить голосовой движок телефона."
            }
        }
    }

    LaunchedEffect(ttsReady) {
        if (ttsReady) {
            val languageResult = tts.setLanguage(Locale("ru", "RU"))
            tts.setPitch(0.94f)
            tts.setSpeechRate(0.95f)

            if (
                languageResult == TextToSpeech.LANG_MISSING_DATA ||
                languageResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                voiceError =
                    "Русский голос не установлен. Проверь настройки синтеза речи телефона."
            }
        }
    }

    val tone = remember {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        } catch (_: Exception) {
            null
        }
    }

    val recognizer = remember {
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

    val latestVoiceActive by rememberUpdatedState(voiceActive)
    val latestBusy by rememberUpdatedState(busy)
    val latestSpeaking by rememberUpdatedState(speaking)

    fun startListening() {
        if (!voiceActive || busy || speaking) return

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            voiceError = "Нужно разрешение на микрофон."
            return
        }

        val speechRecognizer = recognizer
        if (speechRecognizer == null) {
            voiceError = "На телефоне не найден сервис распознавания речи."
            return
        }

        try {
            voiceError = ""
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Алекс слушает")
            }
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            listening = false
            voiceError = "Не получилось включить микрофон."
            Log.e("AlexVoice", "startListening failed", e)
        }
    }

    val latestStartListening by rememberUpdatedState<() -> Unit>({
        startListening()
    })

    fun stopVoice() {
        voiceActive = false
        listening = false
        speaking = false

        try {
            recognizer?.cancel()
            tts.stop()
        } catch (_: Exception) {
        }
    }

    fun sendMessage(text: String, speakReply: Boolean) {
        val question = text.trim()
        if (question.isBlank() || busy) return

        messages.add(AlexMessage("user", question))
        busy = true
        listening = false
        voiceError = ""

        scope.launch {
            try {
                val history = messages.dropLast(1).takeLast(16)

                val prompt = buildString {
                    append(
                        "Ты Алекс — доброжелательный ИИ-помощник приложения Vibe Chat. "
                    )
                    append("Отвечай на русском языке, естественно и понятно. ")
                    append("Учитывай историю разговора. ")
                    append("Не утверждай, что выполнил действия, которых не выполнял. ")
                    append("Не выдумывай доступ к телефону или личным данным.\n\n")
                    append("История разговора:\n")

                    history.forEach { message ->
                        append(
                            if (message.role == "user") "Пользователь: "
                            else "Алекс: "
                        )
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
                    throw IllegalStateException("Модель вернула пустой ответ.")
                }

                messages.add(AlexMessage("assistant", reply))

                if (speakReply) {
                    if (ttsReady) {
                        speaking = true
                        val result = tts.speak(
                            reply,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "alex_${System.currentTimeMillis()}"
                        )

                        if (result == TextToSpeech.ERROR) {
                            speaking = false
                            voiceError = "Не удалось озвучить ответ."
                            if (voiceActive) {
                                handler.postDelayed({
                                    latestStartListening()
                                }, 1000)
                            }
                        }
                    } else {
                        voiceError = "Ответ готов, но голосовой движок ещё не готов."
                        if (voiceActive) {
                            handler.postDelayed({
                                latestStartListening()
                            }, 1200)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AlexVoice", "Gemini request failed", e)

                messages.add(
                    AlexMessage(
                        "assistant",
                        "Не удалось получить ответ от AI. Проверь интернет, Firebase AI Logic, App Check и доступность модели.\n\n" +
                            (e.localizedMessage ?: "Подробности ошибки недоступны.")
                    )
                )

                voiceError = "Не удалось получить ответ от Gemini."

                Toast.makeText(
                    context,
                    "Алекс не смог получить ответ",
                    Toast.LENGTH_SHORT
                ).show()

                if (voiceActive) {
                    handler.postDelayed({
                        latestStartListening()
                    }, 1400)
                }
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
            startListening()
        } else {
            stopVoice()
            voiceError = "Разреши доступ к микрофону, чтобы говорить с Алексом."
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
                        SpeechRecognizer.ERROR_AUDIO -> "Проблема с микрофоном."
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
                        else -> "Сервис распознавания перезапускается."
                    }

                    val retryable =
                        error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_CLIENT ||
                        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY

                    if (retryable && latestVoiceActive) {
                        handler.postDelayed({
                            if (
                                latestVoiceActive &&
                                !latestBusy &&
                                !latestSpeaking
                            ) {
                                latestStartListening()
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
                    } else if (latestVoiceActive) {
                        handler.postDelayed({
                            latestStartListening()
                        }, 900)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
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
                    handler.post { speaking = true }
                }

                override fun onDone(utteranceId: String?) {
                    handler.post {
                        speaking = false
                        if (latestVoiceActive) {
                            handler.postDelayed({
                                if (
                                    latestVoiceActive &&
                                    !latestBusy &&
                                    !latestSpeaking
                                ) {
                                    latestStartListening()
                                }
                            }, 650)
                        }
                    }
                }

                @Deprecated("Deprecated in Android API")
                override fun onError(utteranceId: String?) {
                    handler.post {
                        speaking = false
                        voiceError = "Не удалось завершить озвучку."
                        if (latestVoiceActive) {
                            handler.postDelayed({
                                latestStartListening()
                            }, 900)
                        }
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

    DisposableEffect(tone) {
        onDispose {
            try {
                tone?.release()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val transition = rememberInfiniteTransition(label = "alex-chat-glow")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chat-pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF070B19),
                        Color(0xFF0C1228),
                        Color(0xFF101027)
                    )
                )
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(AlexBlue, AlexPurple, AlexCyan)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "✦",
                    color = Color.White,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Алекс",
                    color = Color.White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when {
                        busy -> "Думаю над ответом…"
                        speaking -> "Отвечаю голосом"
                        listening -> "Слушаю тебя"
                        else -> "Твой ИИ-помощник"
                    },
                    color = AlexCyan,
                    fontSize = 12.sp
                )
            }

            Text(
                "AI",
                color = AlexCyan,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }

        HorizontalDivider(color = Color(0xFF24304E))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            itemsIndexed(messages) { _, message ->
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
                        color = if (isUser) AlexPurple else AlexCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = message.text,
                        color = Color(0xFFF3F5FF),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (isUser) Color(0xFF302650)
                                else AlexPanel
                            )
                            .border(
                                1.dp,
                                if (isUser) AlexPurple.copy(alpha = 0.55f)
                                else AlexBlue.copy(alpha = 0.35f),
                                RoundedCornerShape(18.dp)
                            )
                            .padding(13.dp)
                    )
                }
            }

            if (busy) {
                item {
                    Text(
                        "Алекс думает…",
                        color = AlexCyan,
                        fontSize = 13.sp
                    )
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = AlexCyan,
                        trackColor = AlexPanel
                    )
                }
            }
        }

        if (voiceError.isNotBlank()) {
            Text(
                voiceError,
                color = Color(0xFFFFC078),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("Напиши сообщение Алексу…", color = Color(0xFF8996B7))
            },
            shape = RoundedCornerShape(20.dp),
            maxLines = 4,
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

        Spacer(Modifier.height(9.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    voiceActive = true
                    voiceError = ""

                    try {
                        tone?.startTone(ToneGenerator.TONE_PROP_ACK, 130)
                    } catch (_: Exception) {
                    }

                    if (
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        startListening()
                    } else {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AlexCyan
                )
            ) {
                Text("🎙 Голос", fontSize = 13.sp)
            }

            Button(
                onClick = {
                    val question = draft.trim()
                    if (question.isNotBlank() && !busy) {
                        draft = ""
                        sendMessage(question, speakReply = false)
                    }
                },
                enabled = !busy && draft.isNotBlank(),
                modifier = Modifier
                    .weight(1.25f)
                    .height(52.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7049D8),
                    disabledContainerColor = Color(0xFF39334D)
                )
            ) {
                Text(if (busy) "Думаю…" else "Отправить")
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            "VIBE CHAT • ALEX AI",
            color = Color(0xFF65769C),
            fontSize = 9.sp,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (voiceActive) {
        Dialog(
            onDismissRequest = { stopVoice() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AlexFullScreenVoiceMode(
                listening = listening,
                speaking = speaking,
                busy = busy,
                error = voiceError,
                onExit = { stopVoice() },
                onToggleMic = {
                    if (voiceActive && listening) {
                        try {
                            recognizer?.cancel()
                        } catch (_: Exception) {
                        }
                        listening = false
                        voiceError = "Микрофон временно остановлен."
                    } else if (voiceActive && !busy && !speaking) {
                        startListening()
                    }
                }
            )
        }
    }
}

@Composable
private fun AlexFullScreenVoiceMode(
    listening: Boolean,
    speaking: Boolean,
    busy: Boolean,
    error: String,
    onExit: () -> Unit,
    onToggleMic: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "alex-orb")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (speaking) 4200 else 13000
            )
        ),
        label = "orb-rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.90f,
        targetValue = if (speaking) 1.17f else if (listening) 1.10f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (speaking) 450 else 1100
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb-pulse"
    )
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb-wave"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF030511),
                        Color(0xFF090F26),
                        Color(0xFF100A29),
                        Color(0xFF030511)
                    )
                )
            )
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onExit) {
                    Text("‹  Назад в чат", color = AlexCyan, fontSize = 14.sp)
                }

                Text(
                    "VIBE VOICE",
                    color = Color(0xFF8A9BC4),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                "АЛЕКС",
                color = Color.White,
                fontSize = 29.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 7.sp
            )

            Spacer(Modifier.height(7.dp))

            Text(
                when {
                    busy -> "ОБДУМЫВАЮ ТВОЙ ВОПРОС"
                    speaking -> "ГОВОРЮ С ТОБОЙ"
                    listening -> "Я СЛУШАЮ ТЕБЯ"
                    else -> "Я РЯДОМ. МОЖЕМ ПОГОВОРИТЬ"
                },
                color = AlexCyan,
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                textAlign = TextAlign.Center
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.57f)
                .align(Alignment.Center)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val base = size.minDimension * 0.245f
            val r = base * pulse

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x6642DFFF),
                        Color(0x33208DFF),
                        Color(0x229260FF),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = r * 2.5f
                ),
                radius = r * 2.5f,
                center = Offset(cx, cy)
            )

            drawCircle(
                color = AlexCyan.copy(alpha = 0.08f + wave * 0.07f),
                radius = r * (1.95f + wave * 0.18f),
                center = Offset(cx, cy),
                style = Stroke(width = 1.4f)
            )

            drawCircle(
                color = AlexBlue.copy(alpha = 0.55f),
                radius = r * 1.55f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.6f)
            )

            drawCircle(
                color = AlexPurple.copy(alpha = 0.75f),
                radius = r * 1.34f,
                center = Offset(cx, cy),
                style = Stroke(width = 2.2f)
            )

            drawArc(
                color = AlexCyan,
                startAngle = rotation,
                sweepAngle = 115f,
                useCenter = false,
                topLeft = Offset(cx - r * 1.68f, cy - r * 1.68f),
                size = androidx.compose.ui.geometry.Size(r * 3.36f, r * 3.36f),
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )

            drawArc(
                color = AlexPurple,
                startAngle = -rotation * 0.72f + 130f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = Offset(cx - r * 1.48f, cy - r * 1.48f),
                size = androidx.compose.ui.geometry.Size(r * 2.96f, r * 2.96f),
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )

            drawArc(
                color = Color(0xFF8DD9FF).copy(alpha = 0.75f),
                startAngle = rotation * 0.55f + 210f,
                sweepAngle = 45f,
                useCenter = false,
                topLeft = Offset(cx - r * 1.82f, cy - r * 1.82f),
                size = androidx.compose.ui.geometry.Size(r * 3.64f, r * 3.64f),
                style = Stroke(width = 2f, cap = StrokeCap.Round)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFECFFFF),
                        Color(0xFF62DFFF),
                        Color(0xFF247DFF),
                        Color(0xFF512DAB),
                        Color(0xFF080D24)
                    ),
                    center = Offset(cx - r * 0.25f, cy - r * 0.28f),
                    radius = r * 1.55f
                ),
                radius = r,
                center = Offset(cx, cy)
            )

            drawCircle(
                color = Color.White.copy(alpha = 0.75f),
                radius = r * 0.08f,
                center = Offset(cx - r * 0.34f, cy - r * 0.38f)
            )

            // Мягкие световые линии вокруг сферы.
            for (i in 0 until 36) {
                val angle = (i * 10f + rotation) * (Math.PI.toFloat() / 180f)
                val distance = r * (1.95f + 0.13f * sin(angle * 3f + wave))
                val x = cx + kotlin.math.cos(angle) * distance
                val y = cy + kotlin.math.sin(angle) * distance
                val alpha = if (speaking) 0.95f else 0.45f

                drawCircle(
                    color = if (i % 3 == 0) AlexCyan else AlexPurple,
                    radius = if (speaking) 2.7f else 1.7f,
                    center = Offset(x, y),
                    alpha = alpha
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                when {
                    busy -> "Секунду, я думаю…"
                    speaking -> "Слушай мой ответ"
                    listening -> "Говори — я слушаю"
                    else -> "Нажми на микрофон, чтобы говорить"
                },
                color = Color(0xFFD8E5FF),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            if (error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    error,
                    color = Color(0xFFFFC078),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onToggleMic,
                    modifier = Modifier
                        .height(58.dp)
                        .widthIn(min = 150.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AlexCyan
                    )
                ) {
                    Text(
                        if (listening) "Ⅱ  Пауза" else "🎙  Микрофон",
                        fontSize = 14.sp
                    )
                }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = onExit,
                    modifier = Modifier.size(58.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF702B57)
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("■", fontSize = 23.sp, color = Color.White)
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "ГОЛОСОВОЙ РЕЖИМ • ALEX AI",
                color = Color(0xFF64759D),
                fontSize = 9.sp,
                letterSpacing = 1.6.sp
            )
        }
    }
}
