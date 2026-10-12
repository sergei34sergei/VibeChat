package com.vibe.chat

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.storage.FirebaseStorage
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PrivateMessage(
    val id: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val createdAt: Timestamp?,
    val attachmentUrl: String = "",
    val attachmentName: String = "",
    val attachmentType: String = "",
    val readBy: List<String> = emptyList()
)

private val VibeChatBackground = Color(0xFF0B0D18)
private val VibeChatPanel = Color(0xFF131626)
private val VibeChatIncoming = Color(0xFF191B30)
private val VibeChatOutgoing = Color(0xFF263044)
private val VibeChatOutgoingDark = Color(0xFF1D3940)
private val VibeChatText = Color(0xFFE5E9FA)
private val VibeChatMuted = Color(0xFF858BA9)
private val VibeChatAccent = Color(0xFFAD94FF)
private val VibeChatGreen = Color(0xFF75F7DF)

@Composable
fun PrivateChatScreen(
    otherUserId: String,
    otherUserName: String,
    onBack: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val currentUser = auth.currentUser
    val context = LocalContext.current
    val myId = currentUser?.uid.orEmpty()

    var myName by remember(myId) {
        mutableStateOf(
            currentUser?.phoneNumber.orEmpty().ifBlank { "Пользователь" }
        )
    }

    var actualOtherName by remember(otherUserId, otherUserName) {
        mutableStateOf(otherUserName.ifBlank { "Собеседник" })
    }

    var otherAvatarBase64 by remember(otherUserId) {
        mutableStateOf("")
    }

    var messages by remember(myId, otherUserId) {
        mutableStateOf<List<PrivateMessage>>(emptyList())
    }

    var input by remember(myId, otherUserId) {
        mutableStateOf("")
    }
    var showEmojiPicker by remember(myId, otherUserId) { mutableStateOf(false) }
    var emojiSearch by remember(myId, otherUserId) { mutableStateOf("") }
    var emojiCategory by remember(myId, otherUserId) { mutableStateOf("Vibe") }
    var attachmentUploading by remember(myId, otherUserId) { mutableStateOf(false) }

    var error by remember(myId, otherUserId) {
        mutableStateOf("")
    }

    var sending by remember(myId, otherUserId) {
        mutableStateOf(false)
    }

    var chatReady by remember(myId, otherUserId) {
        mutableStateOf(false)
    }

    var loading by remember(myId, otherUserId) {
        mutableStateOf(true)
    }

    val listState = rememberLazyListState()

    val chatId = remember(myId, otherUserId) {
        listOf(myId, otherUserId).sorted().joinToString("_")
    }

    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && chatReady && myId.isNotBlank()) {
            attachmentUploading = true
            error = ""
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val fileName = runCatching {
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                }
            }.getOrNull() ?: "Файл"
            val safeName = fileName.replace(Regex("[^A-Za-zА-Яа-я0-9._-]"), "_").take(90)
            val storageRef = FirebaseStorage.getInstance().reference
                .child("chat_attachments/$chatId/${System.currentTimeMillis()}_$safeName")
            val stream = runCatching { resolver.openInputStream(uri) }.getOrNull()
            if (stream == null) {
                attachmentUploading = false
                error = "Не удалось открыть выбранный файл."
            } else {
                storageRef.putStream(stream, com.google.firebase.storage.StorageMetadata.Builder()
                    .setContentType(mimeType).build())
                    .continueWithTask { task ->
                        stream.close()
                        if (!task.isSuccessful) throw (task.exception ?: Exception("Не удалось загрузить файл"))
                        storageRef.downloadUrl
                    }
                    .addOnSuccessListener { downloadUri ->
                        db.collection("chats").document(chatId).collection("messages")
                            .add(mapOf(
                                "text" to "",
                                "authorId" to myId,
                                "authorName" to myName,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "readBy" to listOf(myId),
                                "attachmentUrl" to downloadUri.toString(),
                                "attachmentName" to fileName,
                                "attachmentType" to mimeType
                            ))
                            .addOnSuccessListener { attachmentUploading = false }
                            .addOnFailureListener { e ->
                                attachmentUploading = false
                                error = "Файл загружен, но сообщение не отправилось: ${e.localizedMessage ?: "ошибка"}"
                            }
                    }
                    .addOnFailureListener { e ->
                        attachmentUploading = false
                        error = "Не удалось загрузить файл: ${e.localizedMessage ?: "проверь Firebase Storage"}"
                    }
            }
        } else if (uri != null) {
            error = "Сначала дождись подключения к чату."
        }
    }

    DisposableEffect(otherUserId) {
        var registration: ListenerRegistration? = null

        if (otherUserId.isNotBlank()) {
            registration = db.collection("users")
                .document(otherUserId)
                .addSnapshotListener { snapshot, exception ->
                    if (exception == null && snapshot != null && snapshot.exists()) {
                        actualOtherName = snapshot.getString("name")
                            ?.takeIf { it.isNotBlank() }
                            ?: otherUserName.ifBlank { "Собеседник" }

                        otherAvatarBase64 =
                            snapshot.getString("avatarBase64").orEmpty()
                    }
                }
        }

        onDispose { registration?.remove() }
    }

    val otherAvatarBitmap = remember(otherAvatarBase64) {
        try {
            if (otherAvatarBase64.isBlank()) {
                null
            } else {
                val bytes = Base64.decode(otherAvatarBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        }
    }

    LaunchedEffect(myId, otherUserId) {
        when {
            myId.isBlank() -> {
                error = "Войди в аккаунт, чтобы открыть переписку."
                loading = false
            }
            otherUserId.isBlank() -> {
                error = "Не удалось определить собеседника."
                loading = false
            }
            myId == otherUserId -> {
                error = "Нельзя открыть личный чат с самим собой."
                loading = false
            }
        }
    }

    DisposableEffect(myId) {
        var registration: ListenerRegistration? = null

        if (myId.isNotBlank()) {
            registration = db.collection("users")
                .document(myId)
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        error = "Ошибка профиля: ${
                            exception.localizedMessage ?: "неизвестная ошибка"
                        }"
                    } else {
                        myName = snapshot?.getString("name")
                            ?.takeIf { it.isNotBlank() }
                            ?: currentUser?.phoneNumber.orEmpty()
                                .ifBlank { "Пользователь" }
                    }
                }
        }

        onDispose { registration?.remove() }
    }

    DisposableEffect(chatId, myId, otherUserId) {
        var messageRegistration: ListenerRegistration? = null
        var active = true

        chatReady = false
        loading = true
        messages = emptyList()

        if (
            myId.isNotBlank() &&
            otherUserId.isNotBlank() &&
            myId != otherUserId
        ) {
            val chatRef = db.collection("chats").document(chatId)

            chatRef.set(
                mapOf(
                    "participantIds" to listOf(myId, otherUserId).sorted(),
                    "createdAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).addOnSuccessListener {
                if (!active) return@addOnSuccessListener

                chatReady = true
                error = ""

                messageRegistration = chatRef.collection("messages")
                    .orderBy("createdAt")
                    .addSnapshotListener { snapshot, exception ->
                        if (!active) return@addSnapshotListener

                        if (exception != null) {
                            error = "Не удалось загрузить сообщения: ${
                                exception.localizedMessage
                                    ?: "проверь интернет и правила Firebase"
                            }"
                            loading = false
                        } else if (snapshot != null) {
                            messages = snapshot.documents.map { document ->
                                PrivateMessage(
                                    id = document.id,
                                    authorId = document.getString("authorId").orEmpty(),
                                    authorName = document.getString("authorName").orEmpty(),
                                    text = document.getString("text").orEmpty(),
                                    createdAt = document.getTimestamp("createdAt"),
                                    attachmentUrl = document.getString("attachmentUrl").orEmpty(),
                                    attachmentName = document.getString("attachmentName").orEmpty(),
                                    attachmentType = document.getString("attachmentType").orEmpty(),
                                    readBy = (document.get("readBy") as? List<*>)?.filterIsInstance<String>().orEmpty()
                                )
                            }
                            snapshot.documents.forEach { document ->
                                val authorId = document.getString("authorId").orEmpty()
                                val readBy = (document.get("readBy") as? List<*>)?.filterIsInstance<String>().orEmpty()
                                if (authorId.isNotBlank() && authorId != myId && myId !in readBy) {
                                    document.reference.update("readBy", FieldValue.arrayUnion(myId))
                                }
                            }
                            error = ""
                            loading = false
                        }
                    }
            }.addOnFailureListener { exception ->
                if (!active) return@addOnFailureListener

                chatReady = false
                loading = false
                error = "Не удалось открыть чат: ${
                    exception.localizedMessage ?: "проверь интернет и Firebase"
                }"
            }
        } else {
            loading = false
        }

        onDispose {
            active = false
            messageRegistration?.remove()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VibeChatBackground)
            .imePadding()
    ) {
        // Верхняя панель диалога.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VibeChatBackground)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Назад",
                    tint = VibeChatText
                )
            }

            Box(
                modifier = Modifier
                    .size(43.dp)
                    .clip(CircleShape)
                    .background(VibeChatPanel),
                contentAlignment = Alignment.Center
            ) {
                if (otherAvatarBitmap != null) {
                    Image(
                        bitmap = otherAvatarBitmap.asImageBitmap(),
                        contentDescription = "Аватар собеседника",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        actualOtherName.firstOrNull()?.uppercase() ?: "?",
                        color = VibeChatAccent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.width(11.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    actualOtherName,
                    color = VibeChatText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )

                Spacer(Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (chatReady) VibeChatGreen else VibeChatMuted
                            )
                    )

                    Spacer(Modifier.width(6.dp))

                    Text(
                        if (chatReady) "Личный чат" else "Подключение…",
                        color = VibeChatMuted,
                        fontSize = 12.sp
                    )
                }
            }

            IconButton(onClick = {}) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Дополнительно",
                    tint = VibeChatMuted
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF343650))
        )

        if (error.isNotBlank()) {
            Surface(
                color = Color(0xFF30202D),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    error,
                    color = Color(0xFFFF9BCB),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(11.dp)
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = 12.dp,
                vertical = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (messages.isEmpty()) {
                item(key = "chat_welcome") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 50.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(78.dp)
                                .clip(CircleShape)
                                .background(VibeChatPanel),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✦", color = VibeChatAccent, fontSize = 35.sp)
                        }

                        Spacer(Modifier.height(18.dp))

                        Text(
                            when {
                                loading -> "Подключаем переписку…"
                                chatReady -> "Начни разговор"
                                else -> "Личная переписка"
                            },
                            color = VibeChatText,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(7.dp))

                        Text(
                            if (chatReady) {
                                "Напиши $actualOtherName первое сообщение"
                            } else {
                                "Здесь будут ваши сообщения"
                            },
                            color = VibeChatMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            items(messages, key = { it.id }) { message ->
                val mine = message.authorId == myId

                val bubbleShape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (mine) 18.dp else 5.dp,
                    bottomEnd = if (mine) 5.dp else 18.dp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) {
                        Arrangement.End
                    } else {
                        Arrangement.Start
                    },
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 285.dp)
                            .clip(bubbleShape)
                            .background(
                                if (mine) {
                                    Brush.linearGradient(
                                        listOf(
                                            VibeChatOutgoing,
                                            VibeChatOutgoingDark
                                        )
                                    )
                                } else {
                                    Brush.linearGradient(
                                        listOf(
                                            VibeChatIncoming,
                                            VibeChatIncoming
                                        )
                                    )
                                }
                            )
                            .padding(
                                horizontal = 11.dp,
                                vertical = 7.dp
                            )
                    ) {
                        if (!mine) {
                            Text(
                                message.authorName.ifBlank { actualOtherName },
                                color = VibeChatAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )

                            Spacer(Modifier.height(3.dp))
                        }

                        Text(
                            message.text,
                            color = VibeChatText,
                            fontSize = 14.sp,
                            lineHeight = 19.sp
                        )

                        if (message.attachmentUrl.isNotBlank()) {
                            Spacer(Modifier.height(7.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF252B40))
                                    .clickable {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(message.attachmentUrl)))
                                        }.onFailure { error = "Не удалось открыть вложение." }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, tint = VibeChatGreen, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(7.dp))
                                Column(Modifier.weight(1f, fill = false)) {
                                    Text(message.attachmentName.ifBlank { "Вложение" }, color = VibeChatText, fontSize = 12.sp, maxLines = 2)
                                    Text("Открыть файл", color = VibeChatGreen, fontSize = 10.sp)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            message.createdAt?.let { timestamp ->
                                Text(
                                    SimpleDateFormat(
                                        "HH:mm",
                                        Locale.getDefault()
                                    ).format(Date(timestamp.toDate().time)),
                                    color = if (mine) {
                                        Color(0xFFB64A48)
                                    } else {
                                        VibeChatMuted
                                    },
                                    fontSize = 10.sp
                                )
                            }

                            if (mine) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "✓✓",
                                    color = if (otherUserId in message.readBy) VibeChatGreen else VibeChatMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        // Фирменная панель Emoji Vibe: отдельная кнопка открывает категории и поиск.
        if (showEmojiPicker) {
            val emojiSets = mapOf(
                "Vibe" to listOf("🫧", "🪐", "🪼", "🌌", "✨", "💫", "🩵", "💜", "🪽", "🦋", "🌙", "☄️", "🪷", "🧿", "🪄", "🤍", "🥹", "😎", "🫶", "🤝", "🫠", "🙃", "😶‍🌫️", "🤖"),
                "Лица" to listOf("😀", "😄", "😂", "🥹", "😍", "😎", "🤔", "😴", "🥳", "😭", "😤", "🤗", "🫠", "🙃", "😶‍🌫️", "🤯", "🫡", "🤫"),
                "Сердца" to listOf("🩵", "💜", "🤍", "🩷", "❤️", "🧡", "💛", "💚", "💙", "🖤", "🤎", "💖", "💗", "💘", "💝", "💞", "💕", "🫶"),
                "Знаки" to listOf("✨", "💫", "🔥", "⭐", "🌟", "⚡", "☄️", "🌈", "💎", "🎉", "🎧", "🎮", "🚀", "🪄", "🧿", "♾️")
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(VibeChatPanel)
                    .border(1.dp, Color(0xFF343650), RoundedCornerShape(20.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("VIBE EMOJI", color = VibeChatGreen, fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium)
                        Text("Выбери настроение", color = VibeChatText, fontSize = 15.sp, fontWeight = FontWeight.Normal)
                    }
                    IconButton(onClick = { showEmojiPicker = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть Emoji", tint = VibeChatMuted, modifier = Modifier.size(19.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0B0D18)).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = VibeChatMuted, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (emojiSearch.isBlank()) Text("Найти смайлик…", color = VibeChatMuted, fontSize = 12.sp)
                        BasicTextField(value = emojiSearch, onValueChange = { emojiSearch = it }, singleLine = true, textStyle = TextStyle(color = VibeChatText, fontSize = 12.sp), modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    emojiSets.keys.forEach { category ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (emojiCategory == category) Color(0xFF263044) else Color.Transparent)
                                .clickable { emojiCategory = category }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(category, color = if (emojiCategory == category) VibeChatGreen else VibeChatMuted, fontSize = 10.sp, fontWeight = FontWeight.Normal)
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                val shownEmojis = (emojiSets[emojiCategory] ?: emojiSets["Vibe"].orEmpty()).filter { emojiSearch.isBlank() || it.contains(emojiSearch, ignoreCase = true) }
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(8),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 142.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(shownEmojis.size) { index ->
                        val emoji = shownEmojis[index]
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                                .clickable {
                                    if (input.length < 2000) input = (input + emoji).take(2000)
                                    showEmojiPicker = false
                                    emojiSearch = ""
                                }.padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(emoji, fontSize = 21.sp) }
                    }
                }
            }
        }

        if (attachmentUploading) {
            Text("Загружаем вложение…", color = VibeChatGreen, fontSize = 11.sp, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
        }

        // Поле ввода.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VibeChatBackground)
                .padding(start = 9.dp, end = 9.dp, top = 5.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                enabled = chatReady && !attachmentUploading,
                onClick = { attachmentPicker.launch(arrayOf("image/*", "video/*", "application/pdf", "text/*", "application/*")) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = "Прикрепить фото или файл", tint = if (attachmentUploading) VibeChatMuted else VibeChatGreen, modifier = Modifier.size(21.dp))
            }

            IconButton(
                onClick = { showEmojiPicker = !showEmojiPicker },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.EmojiEmotions, contentDescription = "Открыть Emoji", tint = if (showEmojiPicker) VibeChatAccent else VibeChatGreen, modifier = Modifier.size(21.dp))
            }

            Spacer(Modifier.width(4.dp))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(VibeChatPanel)
                    .border(
                        1.dp,
                        Color(0xFFE6EAE7),
                        RoundedCornerShape(23.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it.take(2000) },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        color = VibeChatText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    cursorBrush = Brush.verticalGradient(
                        listOf(VibeChatAccent, VibeChatOutgoing)
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (input.isEmpty()) {
                                Text(
                                    "Написать сообщение…",
                                    color = VibeChatMuted,
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Spacer(Modifier.width(8.dp))

            val canSend = input.isNotBlank() &&
                chatReady &&
                myId.isNotBlank() &&
                otherUserId.isNotBlank() &&
                myId != otherUserId &&
                !sending

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) VibeChatOutgoing
                        else VibeChatPanel
                    )
                    .clickable(enabled = canSend) {
                        val textToSend = input.trim()

                        if (textToSend.isNotBlank() && !sending) {
                            sending = true
                            error = ""

                            db.collection("chats")
                                .document(chatId)
                                .collection("messages")
                                .add(
                                    mapOf(
                                        "text" to textToSend,
                                        "authorId" to myId,
                                        "authorName" to myName,
                                        "createdAt" to FieldValue.serverTimestamp(),
                                        "readBy" to listOf(myId)
                                    )
                                )
                                .addOnSuccessListener {
                                    input = ""
                                    sending = false
                                }
                                .addOnFailureListener { exception ->
                                    error = "Не удалось отправить сообщение: ${
                                        exception.localizedMessage ?: "ошибка Firebase"
                                    }"
                                    sending = false
                                }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Отправить",
                    tint = if (canSend) Color.White else VibeChatMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
