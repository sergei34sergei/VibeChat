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
    onChatClick: (String) -> void,
    onAlexClick: () -> void
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val sampleChats = listOf(
        ChatItem("1", "Алексей", "Привет! Как дела?", "09:41", 2, true),
        ChatItem("2", "Мария", "Стикер 👍", "09:32", 1, true, "📎 Стикер"),
        ChatItem("3", "Друзья", "Иван: Отлично 👍", "08:45", 3, false),
        ChatItem("4", "Работа", "Документ.pdf", "Вчера", 0, false),
        ChatItem("5", "Екатерина", "Хорошо, спасибо!", "Вчера", 0, true),
        ChatItem("6", "Семья", "Мама: ❤️", "Вчера", 0, false)
    )

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
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Камера", tint = VibeTextWhite)
                        Icon(Icons.Default.MoreVert, contentDescription = "Меню", tint = VibeTextWhite)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                // Поиск
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск чатов...", color = VibeTextGray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = VibeTextGray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = VibeSurfaceVariant,
                        unfocusedContainerColor = VibeSurfaceVariant,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
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
                    onClick = { },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Чаты") },
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
                    onClick = { },
                    icon = { Icon(Icons.Default.DonutLarge, contentDescription = "Статусы") },
                    label = { Text("Статусы") },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = VibeTextGray,
                        unselectedTextColor = VibeTextGray
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { },
                    icon = { Icon(Icons.Default.Groups, contentDescription = "Сообщества") },
                    label = { Text("Сообщества") },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = VibeTextGray,
                        unselectedTextColor = VibeTextGray
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { },
                    icon = { Icon(Icons.Default.Call, contentDescription = "Звонки") },
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
                onClick = { onAlexClick() },
                containerColor = VibePrimaryGreen,
                contentColor = VibeOnPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.SmartToy, contentDescription = "Alex AI")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(sampleChats) { chat ->
                ChatItemRow(chat = chat, onClick = { onChatClick(chat.id) })
            }
        }
    }
}

@Composable
fun ChatItemRow(chat: ChatItem, onClick: () -> Unit) {
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
                .background(VibeSurfaceVariant)
        ) {
            // Заглушка аватара / фото
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(chat.name.take(1), color = VibePrimaryGreen, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            if (chat.isOnline) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(VibePrimaryGreen, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = chat.name, color = VibeTextWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(text = chat.time, color = VibeTextGray, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.statusText ?: chat.lastMessage,
                    color = VibeTextGray,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                if (chat.unreadCount > 0) {
                    Badge(
                        containerColor = VibePrimaryGreen,
                        contentColor = VibeOnPrimary
                    ) {
                        Text(text = chat.unreadCount.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
