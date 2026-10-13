package com.vibe.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibe.chat.ui.theme.*

data class MessageItem(
    val id: String,
    val text: String,
    val time: String,
    val isOutgoing: Boolean,
    val isRead: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateChatScreen(
    companionName: String = "Алексей",
    onBackClick: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            MessageItem("1", "Привет! Как дела?", "09:41", false),
            MessageItem("2", "Всё отлично! 😊", "09:42", true),
            MessageItem("3", "Отлично! Что делаешь?", "09:42", false),
            MessageItem("4", "Сейчас работаю, потом свободен", "09:43", true),
            MessageItem("5", "Давай вечером созвонимся?", "09:44", false)
        )
    }

    Scaffold(
        containerColor = VibeDarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(VibeSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(companionName.take(1), color = VibePrimaryGreen, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = companionName, color = VibeTextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = "в сети", color = VibePrimaryGreen, fontSize = 12.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = VibeTextWhite)
                    }
                },
                actions = {
                    IconButton(onClick = { }) { Icon(Icons.Default.Call, contentDescription = "Звонок", tint = VibeTextWhite) }
                    IconButton(onClick = { }) { Icon(Icons.Default.Videocam, contentDescription = "Видео", tint = VibeTextWhite) }
                    IconButton(onClick = { }) { Icon(Icons.Default.MoreVert, contentDescription = "Меню", tint = VibeTextWhite) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VibeSurfaceColor)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VibeSurfaceColor)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { }) {
                    Icon(Icons.Default.EmojiEmotions, contentDescription = "Эмодзи", tint = VibeTextGray)
                }
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Сообщение", color = VibeTextGray) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = VibeSurfaceVariant,
                        unfocusedContainerColor = VibeSurfaceVariant,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = VibeTextWhite,
                        unfocusedTextColor = VibeTextWhite
                    ),
                    trailingIcon = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { }) {
                                Icon(Icons.Default.AttachFile, contentDescription = "Вложение", tint = VibeTextGray)
                            }
                            IconButton(onClick = { }) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Камера", tint = VibeTextGray)
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.width(6.dp))
                FloatingActionButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            messages.add(MessageItem(System.currentTimeMillis().toString(), messageText, "09:45", true))
                            messageText = ""
                        }
                    },
                    containerColor = VibePrimaryGreen,
                    contentColor = VibeOnPrimary,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Голосовое / Отправить")
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            items(messages) { message ->
                MessageBubble(message = message)
            }
        }
    }
}

@Composable
fun MessageBubble(message: MessageItem) {
    val bubbleColor = if (message.isOutgoing) VibeBubbleOutgoing else VibeBubbleIncoming
    val alignment = if (message.isOutgoing) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isOutgoing) 16.dp else 4.dp,
                bottomEnd = if (message.isOutgoing) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(text = message.text, color = VibeTextWhite, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = message.time, color = VibeTextGray, fontSize = 11.sp)
                    if (message.isOutgoing) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Прочитано",
                            tint = if (message.isRead) VibePrimaryGreen else VibeTextGray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
