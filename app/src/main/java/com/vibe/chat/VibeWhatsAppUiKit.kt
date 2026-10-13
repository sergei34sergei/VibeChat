package com.vibe.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam

data class VibeChatUiModel(
    val id: String,
    val title: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int = 0,
    val online: Boolean = false,
    val initials: String = title.take(1).uppercase()
)

data class VibeContactUiModel(
    val id: String,
    val name: String,
    val subtitle: String = "",
    val online: Boolean = false,
    val initials: String = name.take(1).uppercase()
)

data class VibeMessageUiModel(
    val id: String,
    val text: String,
    val time: String,
    val outgoing: Boolean
)

enum class VibeMainTab { CHATS, CONTACTS, PROFILE }

@Composable
fun VibeBottomNavigation(
    selected: VibeMainTab,
    onSelect: (VibeMainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = VibeWhatsAppColors.Surface,
        contentColor = VibeWhatsAppColors.TextPrimary
    ) {
        NavigationBarItem(
            selected = selected == VibeMainTab.CHATS,
            onClick = { onSelect(VibeMainTab.CHATS) },
            icon = { Icon(Icons.Default.Chat, contentDescription = "Чаты") },
            label = { Text("Чаты") }
        )
        NavigationBarItem(
            selected = selected == VibeMainTab.CONTACTS,
            onClick = { onSelect(VibeMainTab.CONTACTS) },
            icon = { Icon(Icons.Default.Contacts, contentDescription = "Контакты") },
            label = { Text("Контакты") }
        )
        NavigationBarItem(
            selected = selected == VibeMainTab.PROFILE,
            onClick = { onSelect(VibeMainTab.PROFILE) },
            icon = { Icon(Icons.Default.Person, contentDescription = "Профиль") },
            label = { Text("Профиль") }
        )
    }
}

@Composable
fun VibeChatsScreen(
    chats: List<VibeChatUiModel>,
    onChatClick: (String) -> Unit,
    onNewChatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val filtered = chats.filter {
        it.title.contains(query, ignoreCase = true) ||
            it.lastMessage.contains(query, ignoreCase = true)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VibeWhatsAppColors.Background,
        floatingActionButton = {
            Button(
                onClick = onNewChatClick,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibeWhatsAppColors.Accent,
                    contentColor = ColorDark
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = "Новый чат")
                Spacer(Modifier.width(8.dp))
                Text("Новый чат", fontWeight = FontWeight.SemiBold)
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("VibeChat", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = VibeWhatsAppColors.TextPrimary)
                    Text("Сообщения рядом с тобой", fontSize = 13.sp, color = VibeWhatsAppColors.TextSecondary)
                }
                IconButton(onClick = onNewChatClick) {
                    Icon(Icons.Default.Add, contentDescription = "Создать чат", tint = VibeWhatsAppColors.Accent)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Меню", tint = VibeWhatsAppColors.TextSecondary)
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                placeholder = { Text("Поиск по чатам") },
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = VibeWhatsAppColors.SurfaceElevated,
                    unfocusedContainerColor = VibeWhatsAppColors.SurfaceElevated,
                    focusedIndicatorColor = VibeWhatsAppColors.Accent,
                    unfocusedIndicatorColor = VibeWhatsAppColors.SurfaceElevated,
                    focusedTextColor = VibeWhatsAppColors.TextPrimary,
                    unfocusedTextColor = VibeWhatsAppColors.TextPrimary,
                    focusedPlaceholderColor = VibeWhatsAppColors.TextSecondary,
                    unfocusedPlaceholderColor = VibeWhatsAppColors.TextSecondary
                )
            )
            Spacer(Modifier.height(10.dp))
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Пока тихо", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = VibeWhatsAppColors.TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text("Начни новый чат — здесь появятся переписки.", color = VibeWhatsAppColors.TextSecondary)
                    }
                }
            } else {
                LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                    items(filtered, key = { it.id }) { chat ->
                        VibeChatRow(chat = chat, onClick = { onChatClick(chat.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun VibeChatRow(chat: VibeChatUiModel, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VibeAvatar(chat.initials, chat.online, 54)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(chat.title, color = VibeWhatsAppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(chat.lastMessage, color = VibeWhatsAppColors.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
            Text(chat.time, color = if (chat.unreadCount > 0) VibeWhatsAppColors.Accent else VibeWhatsAppColors.TextSecondary, fontSize = 11.sp)
            if (chat.unreadCount > 0) {
                Spacer(Modifier.height(5.dp))
                Surface(shape = CircleShape, color = VibeWhatsAppColors.Unread) {
                    Text(
                        chat.unreadCount.coerceAtMost(99).toString(),
                        Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = ColorDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
    Divider(color = VibeWhatsAppColors.Divider.copy(alpha = 0.65f), thickness = 0.5.dp, modifier = Modifier.padding(start = 82.dp))
}

@Composable
fun VibeContactsScreen(
    contacts: List<VibeContactUiModel>,
    onContactClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val filtered = contacts.filter { it.name.contains(query, ignoreCase = true) }
    Column(modifier.fillMaxSize().background(VibeWhatsAppColors.Background)) {
        Text("Контакты", Modifier.padding(start = 20.dp, top = 22.dp, bottom = 14.dp), fontSize = 27.sp, fontWeight = FontWeight.Bold, color = VibeWhatsAppColors.TextPrimary)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            placeholder = { Text("Найти контакт") },
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = VibeWhatsAppColors.SurfaceElevated,
                unfocusedContainerColor = VibeWhatsAppColors.SurfaceElevated,
                focusedIndicatorColor = VibeWhatsAppColors.Accent,
                unfocusedIndicatorColor = VibeWhatsAppColors.SurfaceElevated,
                focusedTextColor = VibeWhatsAppColors.TextPrimary,
                unfocusedTextColor = VibeWhatsAppColors.TextPrimary
            )
        )
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
            items(filtered, key = { it.id }) { contact ->
                Row(
                    Modifier.fillMaxWidth().clickable { onContactClick(contact.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VibeAvatar(contact.initials, contact.online, 48)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(contact.name, color = VibeWhatsAppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        if (contact.subtitle.isNotBlank()) {
                            Text(contact.subtitle, color = VibeWhatsAppColors.TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VibeProfileScreen(
    name: String,
    phone: String,
    onEditProfile: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxSize().background(VibeWhatsAppColors.Background).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Профиль", Modifier.fillMaxWidth(), fontSize = 27.sp, fontWeight = FontWeight.Bold, color = VibeWhatsAppColors.TextPrimary)
        Spacer(Modifier.height(30.dp))
        VibeAvatar(name.take(1).uppercase(), false, 94)
        Spacer(Modifier.height(16.dp))
        Text(name, color = VibeWhatsAppColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(phone, color = VibeWhatsAppColors.TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onEditProfile, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = VibeWhatsAppColors.Accent, contentColor = ColorDark)) {
            Text("Изменить профиль")
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onSettings),
            color = VibeWhatsAppColors.Surface,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = VibeWhatsAppColors.Accent)
                Spacer(Modifier.width(12.dp))
                Text("Настройки и приватность", color = VibeWhatsAppColors.TextPrimary)
            }
        }
    }
}

@Composable
fun VibeWhatsAppChatScreen(
    title: String,
    online: Boolean,
    messages: List<VibeMessageUiModel>,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onAttach: () -> Unit,
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember { mutableStateOf("") }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VibeWhatsAppColors.Background,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(VibeWhatsAppColors.Surface).padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = VibeWhatsAppColors.TextPrimary) }
                VibeAvatar(title.take(1).uppercase(), online, 40)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = VibeWhatsAppColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (online) "в сети" else "VibeChat", color = if (online) VibeWhatsAppColors.Accent else VibeWhatsAppColors.TextSecondary, fontSize = 11.sp)
                }
                IconButton(onClick = onVoiceCall) { Icon(Icons.Default.Call, contentDescription = "Позвонить", tint = VibeWhatsAppColors.TextSecondary) }
                IconButton(onClick = onVideoCall) { Icon(Icons.Default.Videocam, contentDescription = "Видеозвонок", tint = VibeWhatsAppColors.TextSecondary) }
            }
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(VibeWhatsAppColors.Background).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(onClick = onAttach) { Icon(Icons.Default.AttachFile, contentDescription = "Прикрепить файл", tint = VibeWhatsAppColors.TextSecondary) }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение") },
                    shape = RoundedCornerShape(22.dp),
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = VibeWhatsAppColors.SurfaceElevated,
                        unfocusedContainerColor = VibeWhatsAppColors.SurfaceElevated,
                        focusedIndicatorColor = VibeWhatsAppColors.Accent,
                        unfocusedIndicatorColor = VibeWhatsAppColors.SurfaceElevated,
                        focusedTextColor = VibeWhatsAppColors.TextPrimary,
                        unfocusedTextColor = VibeWhatsAppColors.TextPrimary
                    )
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        val value = draft.trim()
                        if (value.isNotEmpty()) {
                            onSend(value)
                            draft = ""
                        }
                    },
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(VibeWhatsAppColors.Accent)
                ) { Icon(Icons.Default.Send, contentDescription = "Отправить", tint = ColorDark) }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.fillMaxWidth(0.86f)
                            .clip(RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp,
                                bottomStart = if (message.outgoing) 16.dp else 4.dp,
                                bottomEnd = if (message.outgoing) 4.dp else 16.dp
                            ))
                            .background(if (message.outgoing) VibeWhatsAppColors.BubbleOutgoing else VibeWhatsAppColors.BubbleIncoming)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(message.text, color = VibeWhatsAppColors.TextPrimary, fontSize = 14.sp)
                        Text(message.time, Modifier.align(Alignment.End).padding(top = 4.dp), color = VibeWhatsAppColors.TextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun VibeAvatar(initials: String, online: Boolean, size: Int) {
    Box {
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(VibeWhatsAppColors.SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Text(initials.ifBlank { "V" }.take(2), color = VibeWhatsAppColors.Accent, fontWeight = FontWeight.Bold, fontSize = (size / 2.8f).sp)
        }
        if (online) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape)
                    .background(VibeWhatsAppColors.Accent)
            )
        }
    }
}

private val ColorDark = androidx.compose.ui.graphics.Color(0xFF062A1B)
