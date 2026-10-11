
package com.vibe.chat

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

private val StudioBg = Color(0xFF080D1B)
private val StudioPanel = Color(0xFF151B30)
private val StudioPurple = Color(0xFFAC55FF)
private val StudioCyan = Color(0xFF45E8FF)
private val StudioMuted = Color(0xFFABB4D1)

@Composable
fun PhotoStudioScreen() {
    var prompt by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf("Реализм") }
    var selectedRatio by remember { mutableStateOf("1:1") }
    var message by remember { mutableStateOf("") }

    val styles = listOf(
        "Реализм", "Аниме", "3D", "Фэнтези", "Неон"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF10152D),
                        StudioBg,
                        Color(0xFF11142A)
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "VIBE CREATIVE",
                    color = StudioCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "AI-студия",
                    color = Color.White,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Преврати идею в изображение",
                    color = StudioMuted,
                    fontSize = 13.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(StudioPurple, StudioCyan)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "✦",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // Большая область предпросмотра
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF27204C),
                            Color(0xFF152B48),
                            Color(0xFF17182F)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(22.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .background(Color(0x332ADCF5)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "✧",
                        color = StudioCyan,
                        fontSize = 48.sp
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    "Твоё воображение — начало",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    "Здесь появится созданное изображение",
                    color = StudioMuted,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "01  ·  Опиши свою идею",
            color = Color.White,
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
                    "Например: футуристический город ночью, фиолетовые огни, кинематографичный свет...",
                    color = StudioMuted
                )
            },
            minLines = 4,
            maxLines = 6,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = StudioPanel,
                unfocusedContainerColor = StudioPanel,
                focusedBorderColor = StudioCyan,
                unfocusedBorderColor = Color(0xFF353C5B),
                cursorColor = StudioCyan
            )
        )

        Spacer(Modifier.height(22.dp))

        Text(
            "02  ·  Выбери стиль",
            color = Color.White,
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
                            Color(0xFF33255A)
                        } else {
                            StudioPanel
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) StudioCyan
                            else Color(0xFF303752)
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
                                style,
                                color = if (selected) {
                                    StudioCyan
                                } else {
                                    Color.White
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
            "03  ·  Формат изображения",
            color = Color.White,
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
                        Color(0xFF33255A)
                    } else {
                        StudioPanel
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selected) StudioCyan
                        else Color(0xFF303752)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            ratio,
                            color = if (selected) StudioCyan
                            else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when (ratio) {
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
                message =
                    "Интерфейс готов. Следующий шаг — подключить сервис генерации изображений."
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = StudioPurple,
                contentColor = Color.White
            )
        ) {
            Text(
                "✦  Создать изображение",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (message.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                message,
                color = StudioCyan,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "ТВОЁ ТВОРЧЕСТВО",
            color = StudioCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "Недавние работы появятся здесь после подключения генерации.",
            color = StudioMuted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(16.dp))
    }
}
