
package com.vibe.chat

import android.Manifest
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val AlexCyan = Color(0xFF57E8FF)
private val AlexBlue = Color(0xFF328BFF)
private val AlexPurple = Color(0xFF9B6BFF)
private val AlexBg = Color(0xFF050817)
private val AlexPanel = Color(0xFF111A30)
private val AlexText = Color(0xFFF2F5FF)
private val AlexMuted = Color(0xFF8E9BBB)

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
    var voicePaused by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf("") }

    val messages = remember {
        mutableStateListOf(
            AlexMessage(
                "assistant",
                "Привет! Я Алекс ✦\n\nРад тебя видеть. Напиши сообщение или открой голосовой режим — поговорим."
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
            val result = tts.setLanguage(Locale("ru", "RU"))
            tts.setPitch(0.96f)
            tts.setSpeechRate(0.94f)

            if (
                result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                voiceError =
                    "Русский голос не установлен. Проверь настройки синтеза речи Android."
            }
        }
    }

    val recognizer = remember {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                SpeechRecognizer.createSpeechRecognizer(context)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    val latestVoiceActive by rememberUpdatedState(voiceActive)
    val latestVoicePaused by rememberUpdatedState(voicePaused)
    val latestBusy by rememberUpdatedState(busy)
    val latestSpeaking by rememberUpdatedState(speaking)

    fun startListening() {
        if (!voiceActive || voicePaused || busy || speaking) return

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            voiceError = "Разреши доступ к микрофону."
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
            Log.e("AlexVoice", "startListening", e)
        }
    }

    val latestStartListening by rememberUpdatedState<() -> Unit> {
        { startListening() }
    }

    fun stopVoice() {
        voiceActive = false
        voicePaused = false
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
                val history = messages.dropLast(1).takeLast(12)

                val prompt = buildString {
                    append(
                        "Ты Алекс — дружелюбный ИИ-помощник приложения Vibe Chat. "
                    )
                    append("Отвечай по-русски естественно, понятно и по делу. ")
                    append("Учитывай историю беседы. ")
                    append("Не заявляй, что выполнил действия, которых не выполнял. ")
                    append("Не выдумывай доступ к устройству или личным данным.\n\n")
                    append("История:\n")

                    history.forEach { message ->
                        append(
                            if (message.role == "user") "Пользователь: "
                            else "Алекс: "
                        )
                        append(message.text)
                        append("\n")
                    }

                    append("\nНовое сообщение пользователя: ")
                    append(question)
                    append("\nОтвет Алекса:")
                }

                val model = Firebase.ai(
                    backend = GenerativeBackend.googleAI()
                ).generativeModel("gemini-3.8-flash")

                val response = withTimeout(45_000L) {
                    model.generateContent(prompt)
                }

                val reply = response.text?.trim().orEmpty()
                if (reply.isBlank()) {
                    throw IllegalStateException(
                        "AI вернул пустой ответ. Попробуй ещё раз."
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
                            "alex_${System.currentTimeMillis()}"
                        )

                        if (result == TextToSpeech.ERROR) {
                            speaking = false
                            voiceError = "Ответ готов, но озвучить его не удалось."
                        }
                    } else {
                        voiceError =
                            "Ответ готов, но голосовой движок ещё не готов."
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AlexVoice", "AI request failed", e)

                val detail = e.localizedMessage
                    ?.takeIf { it.isNotBlank() }
                    ?: e.javaClass.simpleName

                val friendly = when {
                    e is kotlinx.coroutines.TimeoutCancellationException ->
                        "Ответ не пришёл за 45 секунд. Проверь интернет и доступность AI."
                    detail.contains("App Check", ignoreCase = true) ||
                        detail.contains("attestation", ignoreCase = true) ||
                        detail.contains("token", ignoreCase = true) ->
                        "Firebase отклонил проверку App Check. Нужно проверить настройки Firebase."
                    detail.contains("quota", ignoreCase = true) ||
                        detail.contains("resource exhausted", ignoreCase = true) ->
                        "Похоже, исчерпан лимит запросов AI. Попробуй позже."
                    detail.contains("permission", ignoreCase = true) ||
                        detail.contains("unauthenticated", ignoreCase = true) ||
                        detail.contains("unauthorized", ignoreCase = true) ->
                        "Firebase не разрешил запрос. Проверь доступ и настройки проекта."
                    else ->
                        "Не удалось получить ответ от AI."
                }

                messages.add(
                    AlexMessage(
                        "assistant",
                        "$friendly\n\nПодробности: $detail"
                    )
                )
                voiceError = friendly

                Toast.makeText(
                    context,
                    "Алекс не смог получить ответ",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                busy = false

                if (latestVoiceActive && !latestVoicePaused) {
                    handler.postDelayed({
                        if (
                            latestVoiceActive &&
                            !latestVoicePaused &&
                            !latestBusy &&
                            !latestSpeaking
                        ) {
                            latestStartListening()
                        }
                    }, 900)
                }
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
                        SpeechRecognizer.ERROR_AUDIO ->
                            "Проблема с микрофоном."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Разреши доступ к микрофону."
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Проверь интернет для распознавания речи."
                        SpeechRecognizer.ERROR_NO_MATCH ->
                            "Я не расслышал слова. Попробуй ещё раз."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            "Микрофон занят."
                        SpeechRecognizer.ERROR_SERVER ->
                            "Сервис распознавания временно недоступен."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "Я не услышал речь. Можешь начать говорить."
                        else ->
                            "Не удалось распознать речь. Попробуй ещё раз."
                    }

                    val retryable =
                        error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY

                    if (retryable && latestVoiceActive && !latestVoicePaused) {
                        handler.postDelayed({
                            if (
                                latestVoiceActive &&
                                !latestVoicePaused &&
                                !latestBusy &&
                                !latestSpeaking
                            ) {
                                latestStartListening()
                            }
                        }, 1200)
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
                    } else if (latestVoiceActive && !latestVoicePaused) {
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
                        if (latestVoiceActive && !latestVoicePaused) {
                            handler.postDelayed({
                                if (
                                    latestVoiceActive &&
                                    !latestVoicePaused &&
                                    !latestBusy &&
                                    !latestSpeaking
                                ) {
                                    latestStartListening()
                                }
                            }, 650)
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    handler.post {
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

    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val chatTransition = rememberInfiniteTransition(label = "alex-chat")
    val headerPulse by chatTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "header-pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF030611),
                        Color(0xFF091229),
                        Color(0xFF100C27)
                    )
                )
                )
            .padding(horizontal = 15.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(23.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF111C36),
                            Color(0xFF15152E),
                            Color(0xFF21153B)
                        )
                    )
                )
                .border(
                    1.dp,
                    AlexCyan.copy(alpha = 0.25f),
                    RoundedCornerShape(23.dp)
                )
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(53.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(AlexCyan, AlexBlue, AlexPurple)
                        )
                    )
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10162D)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "✦",
                    color = AlexCyan,
                    fontSize = (29 * headerPulse).sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Алекс",
                    color = Color.White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    when {
                        busy -> "Подбираю ответ…"
                        speaking -> "Говорю с тобой"
                        listening -> "Слушаю тебя"
                        else -> "Твой AI-помощник"
                    },
                    color = AlexCyan,
                    fontSize = 12.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "VIBE",
                    color = AlexCyan,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    "AI",
                    color = AlexPurple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0xFF0D1730))
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (busy) Color(0xFFFFC078) else AlexCyan
                    )
            )
            Spacer(Modifier.width(9.dp))
            Text(
                when {
                    busy -> "Алекс формирует ответ"
                    speaking -> "Голосовой ответ"
                    listening -> "Микрофон включён"
                    else -> "Готов к разговору"
                },
                color = AlexMuted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                "●",
                color = AlexCyan.copy(alpha = 0.75f),
                fontSize = 9.sp
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(13.dp),
            contentPadding = PaddingValues(vertical = 15.dp)
        ) {
            itemsIndexed(messages) { _, message ->
                val isUser = message.role == "user"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) {
                        Arrangement.End
                    } else {
                        Arrangement.Start
                    },
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (!isUser) {
                        Box(
                            modifier = Modifier
                                .padding(end = 7.dp, bottom = 3.dp)
                                .size(29.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(AlexBlue, AlexPurple)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✦",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.widthIn(max = 290.dp),
                        horizontalAlignment = if (isUser) {
                            Alignment.End
                        } else {
                            Alignment.Start
                        }
                    ) {
                        Text(
                            if (isUser) "ТЫ" else "АЛЕКС",
                            color = if (isUser) AlexPurple else AlexCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(
                                horizontal = 7.dp,
                                vertical = 3.dp
                            )
                        )

                        Text(
                            text = message.text,
                            color = AlexText,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            modifier = Modifier
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 19.dp,
                                        topEnd = 19.dp,
                                        bottomStart = if (isUser) 19.dp else 5.dp,
                                        bottomEnd = if (isUser) 5.dp else 19.dp
                                    )
                                )
                                .background(
                                    if (isUser) {
                                        Brush.linearGradient(
                                            listOf(
                                                Color(0xFF4B327F),
                                                Color(0xFF29264E)
                                            )
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            listOf(
                                                Color(0xFF14233F),
                                                Color(0xFF11182E)
                                            )
                                        )
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (isUser) {
                                        AlexPurple.copy(alpha = 0.48f)
                                    } else {
                                        AlexBlue.copy(alpha = 0.30f)
                                    },
                                    RoundedCornerShape(19.dp)
                                )
                                .padding(14.dp)
                        )
                    }
                }
            }

            if (busy) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(AlexPanel)
                            .padding(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(19.dp),
                            color = AlexCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(11.dp))
                        Text(
                            "Алекс думает…",
                            color = AlexCyan,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        if (voiceError.isNotBlank()) {
            Text(
                voiceError,
                color = Color(0xFFFFC078),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(bottom = 7.dp)
            )
        }

        Surface(
            color = Color(0xFF10182D),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                AlexBlue.copy(alpha = 0.42f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(9.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Напиши Алексу…",
                            color = AlexMuted
                        )
                    },
                    shape = RoundedCornerShape(18.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AlexCyan,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = AlexCyan,
                        focusedContainerColor = Color(0xFF0B1224),
                        unfocusedContainerColor = Color(0xFF0B1224)
                    )
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            voiceActive = true
                            voicePaused = false
                            voiceError = ""

                            if (
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                startListening()
                            } else {
                                micPermission.launch(
                                    Manifest.permission.RECORD_AUDIO
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(0.9f)
                            .height(49.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AlexCyan
                        )
                    ) {
                        Text("🎙 Голос", fontSize = 12.sp)
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
                            .weight(1.2f)
                            .height(49.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6543D5),
                            disabledContainerColor = Color(0xFF292943)
                        )
                    ) {
                        Text(
                            if (busy) "Думаю…" else "Отправить  ➤",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "V I B E   C H A T   •   A L E X   A I",
            color = Color(0xFF52658B),
            fontSize = 9.sp,
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
                paused = voicePaused,
                error = voiceError,
                onExit = { stopVoice() },
                onToggleMic = {
                    if (!voicePaused) {
                        voicePaused = true
                        listening = false
                        try {
                            recognizer?.cancel()
                        } catch (_: Exception) {
                        }
                        voiceError = "Микрофон на паузе."
                    } else {
                        voicePaused = false
                        voiceError = ""
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
    paused: Boolean,
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
                durationMillis = if (speaking) 4200 else 14000
            )
        ),
        label = "orb-rotation"
    )

    val pulse by transition.animateFloat(
        initialValue = 0.90f,
        targetValue = when {
            speaking -> 1.16f
            listening -> 1.10f
            else -> 1.03f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (speaking) 500 else 1200
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
                        Color(0xFF02040E),
                        Color(0xFF08122A),
                        Color(0xFF160B30),
                        Color(0xFF030511)
                    )
                )
            )
            .padding(horizontal = 22.dp, vertical = 18.dp)
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
                    Text("‹  Назад в чат", color = AlexCyan)
                }

                Text(
                    "VIBE VOICE",
                    color = AlexMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }

            Spacer(Modifier.height(17.dp))

            Text(
                "АЛЕКС",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 7.sp
            )

            Spacer(Modifier.height(9.dp))

            Text(
                when {
                    busy -> "ОБДУМЫВАЮ ТВОЙ ВОПРОС"
                    speaking -> "ГОВОРЮ С ТОБОЙ"
                    listening -> "Я СЛУШАЮ ТЕБЯ"
                    paused -> "МИКРОФОН НА ПАУЗЕ"
                    else -> "Я РЯДОМ. МОЖЕМ ПОГОВОРИТЬ"
                },
                color = AlexCyan,
                fontSize = 10.sp,
                letterSpacing = 1.1.sp,
                textAlign = TextAlign.Center
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.56f)
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
                color = AlexCyan.copy(alpha = 0.09f + wave * 0.08f),
                radius = r * (1.95f + wave * 0.18f),
                center = Offset(cx, cy),
                style = Stroke(width = 1.4f)
            )

            drawCircle(
                color = AlexBlue.copy(alpha = 0.65f),
                radius = r * 1.55f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.6f)
            )

            drawCircle(
                color = AlexPurple.copy(alpha = 0.8f),
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
                size = androidx.compose.ui.geometry.Size(
                    r * 3.36f,
                    r * 3.36f
                ),
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )

            drawArc(
                color = AlexPurple,
                startAngle = -rotation * 0.72f + 130f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = Offset(cx - r * 1.48f, cy - r * 1.48f),
                size = androidx.compose.ui.geometry.Size(
                    r * 2.96f,
                    r * 2.96f
                ),
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )

            drawArc(
                color = Color(0xFF8DD9FF).copy(alpha = 0.8f),
                startAngle = rotation * 0.55f + 210f,
                sweepAngle = 45f,
                useCenter = false,
                topLeft = Offset(cx - r * 1.82f, cy - r * 1.82f),
                size = androidx.compose.ui.geometry.Size(
                    r * 3.64f,
                    r * 3.64f
                ),
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
                color = Color.White.copy(alpha = 0.8f),
                radius = r * 0.08f,
                center = Offset(cx - r * 0.34f, cy - r * 0.38f)
            )

            for (i in 0 until 36) {
                val angle =
                    (i * 10f + rotation) * (Math.PI.toFloat() / 180f)
                val distance =
                    r * (1.95f + 0.13f * sin(angle * 3f + wave))
                val x = cx + cos(angle) * distance
                val y = cy + sin(angle) * distance

                drawCircle(
                    color = if (i % 3 == 0) AlexCyan else AlexPurple,
                    radius = if (speaking) 2.7f else 1.7f,
                    center = Offset(x, y),
                    alpha = if (speaking) 0.95f else 0.5f
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
                    paused -> "Нажми микрофон, чтобы продолжить"
                    else -> "Нажми микрофон, чтобы говорить"
                },
                color = AlexText,
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

            Spacer(Modifier.height(23.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onToggleMic,
                    modifier = Modifier
                        .height(58.dp)
                        .widthIn(min = 160.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AlexCyan
                    )
                ) {
                    Text(
                        if (paused) "🎙 Продолжить" else "Ⅱ  Пауза",
                        fontSize = 13.sp
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
                letterSpacing = 1.5.sp
            )
        }
    }
}
