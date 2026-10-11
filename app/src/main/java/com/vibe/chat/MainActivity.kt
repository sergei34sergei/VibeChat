
@Composable
private fun VibePhotoStudioScreen() {
    var prompt by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf("Реализм") }
    var selectedRatio by remember { mutableStateOf("9:16") }
    var message by remember { mutableStateOf("") }

    val styles = listOf(
        "Реализм",
        "Аниме",
        "3D",
        "Фэнтези",
        "Неон"
    )

    val ratios = listOf("1:1", "9:16", "16:9")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VibeBackground)
            .padding(horizontal = 18.dp)
    ) {
        VibeSectionTitle(
            title = "AI-студия",
            subtitle = "Превращай свои идеи в изображения ✨"
        )

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF29204F),
                            Color(0xFF142D49),
                            Color(0xFF17152F)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Text(
                    "✦  VIBE IMAGE STUDIO",
                    color = Cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    "Представь.\nОпиши. Создай.",
                    color = Color.White,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 35.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "Опиши картинку, которую хочешь увидеть.",
                    color = Muted,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF30205B),
                                    Color(0xFF123A53),
                                    Color(0xFF17152D)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "✧",
                            color = Cyan,
                            fontSize = 57.sp
                        )

                        Text(
                            "Здесь появится твоё изображение",
                            color = Color.White,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "Что создаём?",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = {
                prompt = it.take(1500)
                message = ""
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            placeholder = {
                Text(
                    "Например: футуристический город ночью, неоновые огни, кинематографический стиль...",
                    color = Muted
                )
            },
            colors = VibeFieldColors,
            shape = RoundedCornerShape(20.dp),
            minLines = 3,
            maxLines = 5
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "Стиль изображения",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(9.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            styles.chunked(3).forEach { rowStyles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowStyles.forEach { style ->
                        val selected = selectedStyle == style

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedStyle = style
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) {
                                Color(0xFF34245F)
                            } else {
                                Panel
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) Cyan else BorderNeon
                            )
                        ) {
                            Text(
                                text = style,
                                modifier = Modifier.padding(
                                    horizontal = 6.dp,
                                    vertical = 13.dp
                                ),
                                color = if (selected) Cyan else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    repeat(3 - rowStyles.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Формат изображения",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(9.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ratios.forEach { ratio ->
                val selected = selectedRatio == ratio

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedRatio = ratio
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) {
                        Color(0xFF34245F)
                    } else {
                        Panel
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selected) Cyan else BorderNeon
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            when (ratio) {
                                "1:1" -> "▣"
                                "9:16" -> "▯"
                                else -> "▭"
                            },
                            color = if (selected) Cyan else Muted,
                            fontSize = 22.sp
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            ratio,
                            color = if (selected) Cyan else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        VibeButton(
            text = "✦  Создать изображение",
            onClick = {
                message = if (prompt.isBlank()) {
                    "Сначала опиши изображение, которое хочешь создать."
                } else {
                    "Интерфейс готов! Следующий этап — подключить сервис генерации изображений."
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (message.isNotBlank()) {
            Spacer(Modifier.height(10.dp))

            Text(
                text = message,
                color = Cyan,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(18.dp))
    }
}
