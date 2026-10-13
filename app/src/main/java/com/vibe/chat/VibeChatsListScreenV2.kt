package com.vibe.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

data class ChatItem(
    val id: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val statusText: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibeChatsListScreen(
    onChatClick: (String) -> Unit,
    onAlexClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val sampleChats = remember {
        listOf(
            ChatItem("1", "Алексей", "Привет! Как дела?", "09:41", 2, true),
            ChatItem("2", "Мария", "Стикер 👍", "09:32", 1, true, "📎 Стикер"),
            ChatItem("3", "Друзья", "Иван: Отлично 👍", "08:45", 3, false),
            ChatItem("4", "Работа", "Документ.pdf", "Вчера"),
            ChatItem("5", "Екатерина", "Хорошо, спасибо!", "Вчера", 0, true),
            ChatItem("6", "Семья", "Мама: ❤️", "Вчера")
        )
    }

    val filteredChats = remember(searchQuery, sampleChats) {
        sampleChats.filter { chat ->
            chat.name.contains(searchQuery.trim(), ignoreCase = true) ||
                chat.lastMessage.contains(searchQuery.trim(), ignoreCase = true) ||
                (chat.statusText?.contains(searchQuery.trim(), ignoreCase = true) == true)
        }
    }

    Scaffold(
        containerColor = VibeDarkBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VibeSurfaceColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VibeChat",
                        color = VibePrimaryGreen,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Камера",
                            tint = VibeTextWhite
                        )
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Меню",
                            tint = VibeTextWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Поиск чатов...",
                            color = VibeTextGray
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Поиск",
                            tint = VibeTextGray
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Очистить поиск",
                                    tint = VibeTextGray
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = VibeSurfaceVariant,
                        unfocusedContainerColor = VibeSurfaceVariant,
                        focusedBorderColor = VibePrimaryGreen,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = VibePrimaryGreen,
                        focusedTextColor = VibeTextWhite,
                        unfocusedTextColor = VibeTextWhite
                    )
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = VibeSurfaceColor,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = {
                        Icon(
                            Icons.Default.Chat,
                            contentDescription = "Чаты"
                        )
                    },
                    label = { Text("Чаты") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VibePrimaryGreen,
                        selectedTextColor = VibePrimaryGreen,
                        unselectedIconColor = VibeTextGray,
                        unselectedTextColor = VibeTextGray,
                        indicatorColor = VibeSurfaceVariant
                    )
                )

                NavigationBarItem(
                    selected = false,
                    onClick = {},
                    icon = {
                        Icon(
                            Icons.Default.DonutLarge,
                            contentDescription = "Статусы"
                        )
                    },
                    label = { Text("Статусы") },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = VibeTextGray,
                        unselectedTextColor = VibeTextGray
                    )
                )

                NavigationBarItem(
                    selected = false,
                    onClick = {},
                    icon = {
                        Icon(
                            Icons.Default.Groups,
                            contentDescription = "Сообщества"
                        )
                    },
                    label = { Text("Сообщества") },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = VibeTextGray,
                        unselectedTextColor = VibeTextGray
                    )
                )

                NavigationBarItem(
                    selected = false,
                    onClick = {},
                    icon = {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = "Звонки"
                        )
                    },
                    label = { Text("Звонки") },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = VibeTextGray,
                        unselectedTextColor = VibeTextGray
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAlexClick,
                containerColor = VibePrimaryGreen,
                contentColor = VibeOnPrimary,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = "Alex AI"
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            if (filteredChats.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = VibeTextGray,
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Чаты не найдены",
                                color = VibeTextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Попробуй изменить поисковый запрос",
                                color = VibeTextGray,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredChats,
                    key = { chat -> chat.id }
                ) { chat ->
                    ChatItemRow(
                        chat = chat,
                        onClick = { onChatClick(chat.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatItemRow(
    chat: ChatItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(VibeSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = chat.name.take(1).uppercase(),
                color = VibePrimaryGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )

            if (chat.isOnline) {
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(VibeDarkBackground)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(VibePrimaryGreen)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.name,
                    color = VibeTextWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = chat.time,
                    color = if (chat.unreadCount > 0) {
                        VibePrimaryGreen
                    } else {
                        VibeTextGray
                    },
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.statusText ?: chat.lastMessage,
                    color = VibeTextGray,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                if (chat.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))

                    Badge(
                        containerColor = VibePrimaryGreen,
                        contentColor = VibeOnPrimary
                    ) {
                        Text(
                            text = chat.unreadCount.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
