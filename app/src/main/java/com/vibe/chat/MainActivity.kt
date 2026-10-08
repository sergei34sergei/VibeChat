package com.vibe.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF090B16)
private val Panel = Color(0xFF15192B)
private val Purple = Color(0xFF9B5CFF)
private val Cyan = Color(0xFF45E5FF)
private val White = Color(0xFFF5F5FF)
private val Muted = Color(0xFF9298B8)

data class Chat(
    val name: String,
    val message: String,
    val time: String,
    val avatar: String,
    val unread: Int = 0,
    val online: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                VibeChatApp()
            }
        }
    }
}

@Composable
fun VibeChatApp() {
    var selectedTab by remember { mutableStateOf("Чаты") }

    val chats = listOf(
        Chat("Алина", "Ты сегодня свободен? ✨", "12:48", "А", 2, true),
        Chat("Максим", "Скинул тебе видео 🔥", "12:32", "М", 1, true),
        Chat("Друзья 💜", "Олег: Всем привет!", "11:56", "Д", 0),
        Chat("Саша", "Отлично, договорились!", "10:21", "С"),
        Chat("Vibe Team", "Добро пожаловать в Vibe!", "Вчера", "V", 0)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "VIBE",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    color = White
                )
                Text(
                    text = "ТВОЙ МИР. ТВОЙ РИТМ.",
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    color = Cyan
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Brush.linearGradient(listOf(Purple, Cyan)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("V", fontSize = 24.sp, fontWeight = FontWeight.Black,
                    color = Background)
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = if (selectedTab == "Чаты") "Твои сообщения" else selectedTab,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            color = White
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = "",
            onValueChange = {},
            readOnly = true,
            placeholder = { Text("Поиск сообщений", color = Muted) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = White,
                unfocusedTextColor = White,
                focusedBorderColor = Purple,
                unfocusedBorderColor = Panel,
                focusedContainerColor = Panel,
                unfocusedContainerColor = Panel,
                focusedPlaceholderColor = Muted,
                unfocusedPlaceholderColor = Muted
            ),
            singleLine = true
        )

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Все", "Личные", "Группы").forEach { filter ->
                Box(
                    modifier = Modifier
                        .background(
                            if (filter == "Все") Purple.copy(alpha = 0.24f) else Panel,
                            RoundedCornerShape(50)
                        )
                        .padding(horizontal = 17.dp, vertical = 10.dp)
                ) {
                    Text(filter, color = White, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chats) { chat ->
                ChatRow(chat = chat)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            listOf("Чаты", "Звонки", "Контакты", "Профиль").forEach { tab ->
                Column(
                    modifier = Modifier
                        .clickable { selectedTab = tab }
                        .padding(5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (tab) {
                            "Чаты" -> "💬"
                            "Звонки" -> "📞"
                            "Контакты" -> "👥"
                            else -> "👤"
                        },
                        fontSize = 21.sp
                    )
                    Text(
                        tab,
                        color = if (selectedTab == tab) Cyan else Muted,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ChatRow(chat: Chat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(20.dp))
            .clickable { }
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(
                    Brush.linearGradient(
                        listOf(Purple.copy(alpha = 0.8f), Cyan.copy(alpha = 0.65f))
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                chat.avatar,
                color = White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.name,
                    color = White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (chat.online) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(Cyan, CircleShape)
                    )
                }
            }

            Spacer(Modifier.height(5.dp))

            Text(
                chat.message,
                color = Muted,
                fontSize = 12.sp,
                maxLines = 1
            )
        }

        Spacer(Modifier.width(6.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(chat.time, color = Muted, fontSize = 10.sp)

            if (chat.unread > 0) {
                Spacer(Modifier.height(7.dp))
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(listOf(Purple, Cyan)),
                            CircleShape
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        chat.unread.toString(),
                        color = Background,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
