package com.vibe.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Dark = Color(0xFF090B16)
private val Card = Color(0xFF171A2B)
private val Purple = Color(0xFF9B5CFF)
private val Blue = Color(0xFF45E5FF)
private val White = Color(0xFFF7F5FF)
private val Gray = Color(0xFF9CA3BF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                VibeApp()
            }
        }
    }
}

@Composable
fun VibeApp() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Dark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "VIBE",
                        color = White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 5.sp
                    )

                    Text(
                        text = "ТВОЙ МИР. ТВОЙ РИТМ.",
                        color = Blue,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp
                    )
                }

                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = Purple
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "V",
                            color = White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(35.dp))

            Text(
                text = "Сообщения",
                color = White,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Твои люди всегда рядом",
                color = Gray,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            ChatItem("А", "Алина", "Привет! Как дела? ✨", "12:48")
            ChatItem("М", "Максим", "Посмотри это видео 🔥", "12:32")
            ChatItem("Д", "Друзья 💜", "Олег: Всем привет!", "11:56")
            ChatItem("С", "Саша", "До скорой встречи!", "10:21")
            ChatItem("V", "Vibe Team", "Добро пожаловать!", "Вчера")

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                BottomItem("💬", "Чаты", true)
                BottomItem("📞", "Звонки", false)
                BottomItem("👥", "Контакты", false)
                BottomItem("👤", "Профиль", false)
            }
        }
    }
}

@Composable
fun ChatItem(
    avatar: String,
    name: String,
    message: String,
    time: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(Card, RoundedCornerShape(18.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(49.dp),
            shape = CircleShape,
            color = Purple
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = avatar,
                    color = White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = name,
                color = White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = message,
                color = Gray,
                fontSize = 12.sp
            )
        }

        Text(
            text = time,
            color = Blue,
            fontSize = 10.sp
        )
    }
}

@Composable
fun BottomItem(
    icon: String,
    label: String,
    selected: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = icon,
            fontSize = 21.sp
        )

        Text(
            text = label,
            color = if (selected) Blue else Gray,
            fontSize = 10.sp
        )
    }
}
