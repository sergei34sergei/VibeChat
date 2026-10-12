
package com.vibe.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val StudioBg = Color(0xFFF3FAF6)
private val StudioPanel = Color.White
private val StudioMint = Color(0xFF208A60)
private val StudioMintLight = Color(0xFFE1F5E9)
private val StudioCoral = Color(0xFFE87979)
private val StudioText = Color(0xFF20372D)
private val StudioMuted = Color(0xFF718078)
private val StudioBorder = Color(0xFFDCEAE1)

@Composable
fun PhotoStudioScreen() {
    var prompt by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf("Реализм") }
    var selectedRatio by remember { mutableStateOf("1:1") }
    var message by remember { mutableStateOf("") }

    val styles = listOf(
        "Реализм",
        "Аниме",
        "3D",
        "Фэнтези",
        "Неон"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "VIBE CREATIVE",
                    color = StudioMint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    text = "AI-студия",
                    color = StudioText,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = "Преврати идею в изображение",
                    color = StudioMuted,
                    fontSize = 13.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(StudioMint, StudioCoral)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✦",
                    color = Color.White,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // Область предпросмотра
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFE0F5E9),
                            Color(0xFFFBE9E5),
                            Color(0xFFE9F4EF)
                        )
                    ),
                    shape = RoundedCornerShape(26.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(22.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✧",
                        color = StudioMint,
                        fontSize = 49.sp
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Твоё воображение — начало",
                    color = StudioText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Предпросмотр появится после подключения генерации",
                    color = StudioMuted,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "01  ·  Опиши свою идею",
            color = StudioText,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = {
                prompt = it.take(1500)
                message = ""
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Например: фотореалистичный игровой персонаж PUBG Mobile, кинематографичный свет, высокая детализация...",
                    color = StudioMuted
                )
            },
            minLines = 4,
            maxLines = 6,
            shape = RoundedCornerShape(19.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = StudioText,
                unfocusedTextColor = StudioText,
                focusedContainerColor = StudioPanel,
                unfocusedContainerColor = StudioPanel,
                focusedBorderColor = StudioMint,
                unfocusedBorderColor = StudioBorder,
                cursorColor = StudioMint,
                focusedPlaceholderColor = StudioMuted,
                unfocusedPlaceholderColor = StudioMuted
            )
        )

        Spacer(Modifier.height(22.dp))

        Text(
            text = "02  ·  Выбери стиль",
            color = StudioText,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(11.dp))

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
                            .padding(bottom = 8.dp)
                            .clickable {
                                selectedStyle = style
                                message = ""
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            StudioMintLight
                        } else {
                            StudioPanel
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) StudioMint else StudioBorder
                        )
                    ) {
                        Box(
                            modifier = Modifier.padding(
                                horizontal = 5.dp,
                                vertical = 13.dp
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = style,
                                color = if (selected) {
                                    StudioMint
                                } else {
                                    StudioText
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                repeat(3 - rowStyles.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "03  ·  Формат изображения",
            color = StudioText,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            listOf("1:1", "9:16", "16:9").forEach { ratio ->
                val selected = selectedRatio == ratio

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedRatio = ratio
                            message = ""
                        },
                    shape = RoundedCornerShape(13.dp),
                    color = if (selected) {
                        StudioMintLight
                    } else {
                        StudioPanel
                    },
                    border = BorderStroke(
                        1.dp,
                        if (selected) StudioMint else StudioBorder
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = ratio,
                            color = if (selected) StudioMint else StudioText,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = when (ratio) {
                                "9:16" -> "TikTok"
                                "16:9" -> "Широкий"
                                else -> "Квадрат"
                            },
                            color = StudioMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        Button(
            onClick = {
                message = when {
                    prompt.isBlank() ->
                        "Сначала опиши изображение, которое хочешь создать."

                    else ->
                        "Настройки сохранены для будущей генерации. Сервис генерации ещё не подключён."
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = StudioMint,
                contentColor = Color.White
            )
        ) {
            Text(
                text = "✦  Создать изображение",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (message.isNotBlank()) {
            Spacer(Modifier.height(10.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = if (prompt.isBlank()) {
                    Color(0xFFFFF0ED)
                } else {
                    StudioMintLight
                }
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(12.dp),
                    color = if (prompt.isBlank()) {
                        Color(0xFFB94E4E)
                    } else {
                        StudioMint
                    },
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "ТВОЁ ТВОРЧЕСТВО",
            color = StudioMint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Здесь будут отображаться твои созданные изображения после подключения сервиса.",
            color = StudioMuted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(20.dp))
    }
}
