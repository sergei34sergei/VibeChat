package com.vibe.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    fun sendMessage() {
        val text = messageText.trim()
        if (text.isNotEmpty()) {
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(Date())

            messages.add(
                MessageItem(
                    id = System.currentTimeMillis().toString(),
                    text = text,
                    time = currentTime,
                    isOutgoing = true,
                    isRead = false
                )
            )

            messageText = ""

            coroutineScope.launch {
                listState.animateScrollToItem(messages.size)
            }
        }
    }

    Scaffold(
        containerColor = VibeDarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(VibeSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = companionName.take(1).uppercase(),
                                color = VibePrimaryGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = companionName,
                                color = VibeTextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "в сети",
                                color = VibePrimaryGreen,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад",
                            tint = VibeTextWhite
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Звонок",
                            tint = VibeTextWhite
                        )
                    }

                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Видеозвонок",
                            tint = VibeTextWhite
                        )
                    }

                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Меню",
                            tint = VibeTextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VibeSurfaceColor
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VibeSurfaceColor)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = {},
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEmotions,
                        contentDescription = "Эмодзи",
                        tint = VibeTextGray
                    )
                }

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = {
                        Text(
                            text = "Сообщение",
                            color = VibeTextGray
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 120.dp),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = VibeSurfaceVariant,
                        unfocusedContainerColor = VibeSurfaceVariant,
                        focusedBorderColor = VibePrimaryGreen,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = VibePrimaryGreen,
                        focusedTextColor = VibeTextWhite,
                        unfocusedTextColor = VibeTextWhite
                    ),
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {}) {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = "Вложение",
                                    tint = VibeTextGray
                                )
                            }

                            IconButton(onClick = {}) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Камера",
                                    tint = VibeTextGray
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                FloatingActionButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            sendMessage()
                        }
                    },
                    containerColor = VibePrimaryGreen,
                    contentColor = VibeOnPrimary,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (messageText.isBlank()) {
                            Icons.Default.Mic
                        } else {
                            Icons.Default.Send
                        },
                        contentDescription = if (messageText.isBlank()) {
                            "Голосовое сообщение"
                        } else {
                            "Отправить сообщение"
                        },
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            items(
                items = messages,
                key = { message -> message.id }
            ) { message ->
                MessageBubble(message = message)
            }
        }
    }
}

@Composable
fun MessageBubble(message: MessageItem) {
    val bubbleColor = if (message.isOutgoing) {
        VibeBubbleOutgoing
    } else {
        VibeBubbleIncoming
    }

    val alignment = if (message.isOutgoing) {
        Alignment.End
    } else {
        Alignment.Start
    }

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
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                )
            ) {
                Text(
                    text = message.text,
                    color = VibeTextWhite,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = message.time,
                        color = VibeTextGray,
                        fontSize = 11.sp
                    )

                    if (message.isOutgoing) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = if (message.isRead) {
                                "Прочитано"
                            } else {
                                "Отправлено"
                            },
                            tint = if (message.isRead) {
                                VibePrimaryGreen
                            } else {
                                VibeTextGray
                            },
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
