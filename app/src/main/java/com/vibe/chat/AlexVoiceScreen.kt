
package com.vibe.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun AlexVoiceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf("") }
    var answer by remember {
        mutableStateOf(
            "Привет. Я Алекс — голосовой помощник Vibe Chat. Нажми на микрофон или напиши вопрос."
        )
    }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var fullScreenFace by remember { mutableStateOf(true) }

    val tts = remember {
        TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
            }
        }
    }

    LaunchedEffect(ttsReady) {
        if (ttsReady) {
            tts.language = Locale("ru", "RU")
            tts.setPitch(0.78f)
            tts.setSpeechRate(0.88f)
        }
    }

    val recognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    DisposableEffect(recognizer, tts) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
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
                if (
                    error != SpeechRecognizer.ERROR_NO_MATCH &&
                    error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ) {
                    Toast.makeText(
                        context,
                        "Не удалось распознать речь. Попробуй ещё раз.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val words = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()

                if (words.isNotBlank()) {
                    draft = words
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        onDispose {
            try {
                recognizer?.destroy()
            } catch (_: Exception) {
            }

            try {
                tts.stop()
                tts.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (recognizer == null) {
                Toast.makeText(
                    context,
                    "На устройстве нет службы распознавания речи.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                val intent = Intent(
                    RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                ).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                    putExtra(
                        RecognizerIntent.EXTRA_PROMPT,
                        "Говори, я слушаю"
                    )
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                }

                try {
                    recognizer.startListening(intent)
                } catch (_: Exception) {
                    Toast.makeText(
                        context,
                        "Не получилось запустить микрофон.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            Toast.makeText(
                context,
                "Для голосового ввода нужно разрешить микрофон.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val pulse = rememberInfiniteTransition(label = "alex-pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1350),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF090D20),
                        Color(0xFF080B17),
                        Color(0xFF11102A)
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
            Column {
                Text(
                    "ALEX · VIBE AI",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.4.sp,
                    fontSize = 18.sp
                )

                Text(
                    if (busy) "ОБРАБАТЫВАЮ ЗАПРОС"
                    else if (listening) "СЛУШАЮ ТЕБЯ"
                    else "ГОЛОСОВОЙ РЕЖИМ ГОТОВ",
                    color = Color(0xFF51E9FF),
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
            }

            TextButton(
                onClick = { fullScreenFace = !fullScreenFace }
            ) {
                Text(
                    if (fullScreenFace) "Свернуть лицо" else "Развернуть лицо",
                    color = Color(0xFFBDA2FF),
                    fontSize = 11.sp
                )
            }
        }

        if (fullScreenFace) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.88f)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val radius = size.minDimension * 0.285f * glow

                    drawCircle(
                        Color(0x1435E7FF),
                        radius * 1.52f,
                        Offset(cx, cy)
                    )
                    drawCircle(
                        Color(0x2535E7FF),
                        radius * 1.25f,
                        Offset(cx, cy),
                        style = Stroke(width = 2.5f)
                    )
                    drawCircle(
                        Color(0xFF35E7FF),
                        radius,
                        Offset(cx, cy),
                        style = Stroke(width = 3.2f)
                    )
                    drawCircle(
                        Color(0xFF9B5CFF),
                        radius * 0.88f,
                        Offset(cx, cy),
                        style = Stroke(width = 1.3f)
                    )

                    val eyeY = cy - radius * 0.16f

                    drawLine(
                        Color(0xFF35E7FF),
                        Offset(cx - radius * 0.53f, eyeY),
                        Offset(cx - radius * 0.12f, eyeY),
                        strokeWidth = 5f
                    )
                    drawLine(
                        Color(0xFF35E7FF),
                        Offset(cx + radius * 0.12f, eyeY),
                        Offset(cx + radius * 0.53f, eyeY),
                        strokeWidth = 5f
                    )
                    drawLine(
                        Color(0xFF9B5CFF),
                        Offset(cx, eyeY + radius * 0.05f),
                        Offset(cx - radius * 0.07f, cy + radius * 0.16f),
                        strokeWidth = 2.5f
                    )
                    drawArc(
                        color = Color(0xFF35E7FF),
                        startAngle = 15f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(
                            cx - radius * 0.36f,
                            cy + radius * 0.06f
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            radius * 0.72f,
                            radius * 0.46f
                        ),
                        style = Stroke(width = 3.5f)
                    )

                    for (i in -3..3) {
                        val y = cy + i * radius * 0.22f
                        drawLine(
                            Color(0x4035E7FF),
                            Offset(cx - radius * 0.76f, y),
                            Offset(cx + radius * 0.76f, y),
                            strokeWidth = 1f
                        )
                    }

                    drawCircle(
                        Color(0xFF35E7FF),
                        radius * 0.035f,
                        Offset(cx - radius * 0.62f, cy + radius * 0.66f)
                    )
                    drawCircle(
                        Color(0xFF9B5CFF),
                        radius * 0.035f,
                        Offset(cx + radius * 0.62f, cy + radius * 0.66f)
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "А Л Е К С",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 5.sp,
                        fontSize = 18.sp
                    )
                    Text(
                        "ГОЛОГРАФИЧЕСКИЙ ИНТЕРФЕЙС",
                        color = Color(0xFF8D9CBF),
                        fontSize = 9.sp,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(94.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF121831))
                    .border(
                        1.dp,
                        Color(0xFF273B61),
                        RoundedCornerShape(22.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "◉  АЛЕКС НА СВЯЗИ",
                    color = Color(0xFF35E7FF),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(if (fullScreenFace) 1.12f else 1.8f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xE811162B))
                .border(
                    1.dp,
                    Color(0xFF2D365B),
                    RoundedCornerShape(22.dp)
                )
                .padding(14.dp)
        ) {
            Text(
                "ОТВЕТ АЛЕКСА",
                color = Color(0xFF35E7FF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )

            Spacer(Modifier.height(7.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    answer,
                    color = Color(0xFFF4F3FF),
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            }

            if (busy) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF35E7FF),
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
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF35E7FF),
                unfocusedBorderColor = Color(0xFF303957),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color(0xFF35E7FF)
            )
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    if (listening) {
                        recognizer?.stopListening()
                        listening = false
                    } else if (
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        if (recognizer == null) {
                            Toast.makeText(
                                context,
                                "Распознавание речи недоступно на устройстве.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
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
                                    RecognizerIntent.EXTRA_PROMPT,
                                    "Говори, я слушаю"
                                )
                            }

                            try {
                                recognizer.startListening(intent)
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    "Не удалось включить микрофон.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.weight(0.85f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF35E7FF)
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

                        scope.launch {
                            try {
                                val model = Firebase.ai(
                                    backend = GenerativeBackend.googleAI()
                                ).generativeModel("gemini-3.8-flash")

                                val response = model.generateContent(
                                    "Ты Алекс — спокойный, доброжелательный " +
                                        "голосовой помощник приложения Vibe Chat. " +
                                        "Отвечай на русском языке естественно, " +
                                        "ясно и не слишком длинно. " +
                                        "Пользователь говорит: $question"
                                )

                                answer = response.text
                                    ?: "Не получилось получить текстовый ответ. Попробуй ещё раз."

                                if (ttsReady) {
                                    tts.speak(
                                        answer,
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        "alex_reply"
                                    )
                                }
                            } catch (e: Exception) {
                                answer = "Не удалось получить ответ: " +
                                    "${e.localizedMessage ?: "ошибка подключения к AI"}. " +
                                    "Проверь интернет, Firebase AI Logic и App Check."

                                Toast.makeText(
                                    context,
                                    "Ошибка связи с Алексом",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1.15f),
                enabled = !busy && draft.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7B4DDB),
                    disabledContainerColor = Color(0xFF39334D)
                )
            ) {
                Text(
                    if (busy) "Думаю…" else "Спросить Алекса",
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            "Голос зависит от русской озвучки, установленной на телефоне. " +
                "Текстовые ответы используют текущую настройку Firebase AI.",
            color = Color(0xFF8792B7),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
