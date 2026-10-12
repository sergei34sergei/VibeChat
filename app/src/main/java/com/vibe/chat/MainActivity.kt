
@Composable
private fun AlexChatScreen(
    onOpenVoice: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var responseJob by remember { mutableStateOf<Job?>(null) }

    val messages = remember {
        mutableStateListOf<Pair<String, String>>(
            "Алекс" to "Привет! Я Алекс. Напиши мне что-нибудь — я постараюсь помочь."
        )
    }

    val model = remember {
        Firebase.ai(
            backend = GenerativeBackend.googleAI()
        ).generativeModel("gemini-3.8-flash")
    }

    fun stopGeneration() {
        responseJob?.cancel()
        responseJob = null
        thinking = false
        errorText = "Запрос остановлен"
    }

    fun sendMessage() {
        val question = draft.trim()

        if (question.isEmpty() || thinking) return

        draft = ""
        errorText = ""
        messages.add("Ты" to question)
        thinking = true

        val history = messages
            .takeLast(10)
            .joinToString("\n") { (speaker, text) ->
                "$speaker: $text"
            }

        responseJob = scope.launch {
            try {
                val prompt = """
                    Ты — Алекс, ИИ-помощник приложения Vibe Chat.
                    Отвечай дружелюбно, понятно и по существу.
                    По умолчанию говори на русском языке.
                    Учитывай историю переписки.
                    Не утверждай, что выполнил действия,
                    которые на самом деле не выполнял.

                    История:
                    $history

                    Продолжи разговор и ответь на последнее сообщение.
                """.trimIndent()

                val result = withContext(Dispatchers.IO) {
                    model.generateContent(prompt)
                }

                val answer = result.text
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: "Не получилось подготовить ответ. Попробуй ещё раз."

                messages.add("Алекс" to answer)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorText = "Не удалось получить ответ. Проверь интернет и настройки Firebase AI."
            } finally {
                thinking = false
                responseJob = null
            }
        }
    }

    BackHandler {
        stopGeneration()
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090A12))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF151725))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    stopGeneration()
                    onBack()
                }
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Назад",
                    tint = Color.White
                )
            }

            Column(Modifier.weight(1f)) {
                Text(
                    "Алекс AI",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (thinking) "Обдумывает ответ…" else "Текстовый режим",
                    color = Color(0xFFAAA9BF),
                    fontSize = 12.sp
                )
            }

            IconButton(
                onClick = {
                    stopGeneration()
                    onOpenVoice()
                }
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Голосовой режим",
                    tint = Color(0xFF72E7FF)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { message ->
                val fromAlex = message.first == "Алекс"

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (fromAlex) 0.dp else 35.dp,
                            end = if (fromAlex) 35.dp else 0.dp
                        )
                        .clip(RoundedCornerShape(17.dp))
                        .background(
                            if (fromAlex) Color(0xFF1B1D30)
                            else Color(0xFF39304F)
                        )
                        .padding(13.dp)
                ) {
                    Text(
                        message.first,
                        color = if (fromAlex) {
                            Color(0xFF72E7FF)
                        } else {
                            Color(0xFFC5B7FF)
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(5.dp))

                    Text(
                        message.second,
                        color = Color(0xFFF6F3FF),
                        fontSize = 15.sp
                    )
                }
            }

            if (thinking) {
                item {
                    Text(
                        "Алекс печатает…",
                        color = Color(0xFFAAA9BF),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            if (errorText.isNotBlank()) {
                item {
                    Text(
                        errorText,
                        color = Color(0xFFFF9CD6),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF151725))
                .navigationBarsPadding()
                .imePadding()
                .padding(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(4000) },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("Напиши Алексу…")
                },
                enabled = !thinking,
                maxLines = 4,
                shape = RoundedCornerShape(18.dp)
            )

            Spacer(Modifier.width(7.dp))

            IconButton(
                enabled = if (thinking) {
                    true
                } else {
                    draft.isNotBlank()
                },
                onClick = {
                    if (thinking) {
                        stopGeneration()
                    } else {
                        sendMessage()
                    }
                }
            ) {
                Icon(
                    if (thinking) Icons.Default.Stop else Icons.Default.Send,
                    contentDescription = if (thinking) {
                        "Остановить"
                    } else {
                        "Отправить"
                    },
                    tint = Color(0xFF72E7FF)
                )
            }
        }
    }
}
