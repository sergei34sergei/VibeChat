
package com.vibe.chat

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.LocalContext
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
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
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

private val VibeChatBackground = Color(0xFF111321)
private val VibeChatPanel = Color(0xE6171A2B)
private val VibeChatIncoming = Color(0xB8242B42)
private val VibeChatOutgoing = Color(0xB52C7180)
private val VibeChatOutgoingDark = Color(0xC01E5368)
private val VibeChatText = Color(0xFFF0F3FF)
private val VibeChatMuted = Color(0xFF9CA7C2)
private val VibeChatAccent = Color(0xFFB5A0FF)
private val VibeChatGreen = Color(0xFF78E8D0)
private val VibeChatLine = Color(0x665E6A91)

private fun loadVibeImage(url: String): Bitmap? {
    return try {
        val connection = URL(url).openConnection().apply {
            connectTimeout = 10000
            readTimeout = 15000
        }

        connection.getInputStream().use { stream ->
            val bytes = stream.readBytes()
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

            var sampleSize = 1
            while (
                bounds.outWidth / sampleSize > 1200 ||
                bounds.outHeight / sampleSize > 1200
            ) {
                sampleSize *= 2
            }

            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
            )
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Иллюстрация приветствия в стиле Vibe Aurora.
 * Полностью рисуется средствами Compose, без внешних файлов.
 */
@Composable
private fun VibeWelcomeArtwork() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(205.dp),
        contentAlignment = Alignment.Center
    ) {
        // Мягкое неоновое свечение на фоне.
        Box(
            modifier = Modifier
                .size(178.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x553FE8D0),
                            Color(0x332B80A0),
                            Color.Transparent
                        )
                    )
                )
        )

        // Внешнее светящееся кольцо.
        Box(
            modifier = Modifier
                .size(148.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            VibeChatGreen,
                            Color(0x664B9EA9),
                            VibeChatAccent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Внутренний объёмный шар.
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF7EF5DC),
                            Color(0xFF287D83),
                            Color(0xFF34335D),
                            Color(0xFF8B70E8)
                        ),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(280f, 300f)
                    )
                )
                .border(
                    1.dp,
                    Color(0x99D7FFF8),
                    CircleShape
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 22.dp, top = 17.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xDFFFFFFF),
                                Color(0x66B8FFF2),
                                Color.Transparent
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 18.dp)
                    .size(35.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0x887D70FF),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Text(
            "✦",
            modifier = Modifier.offset(x = (-79).dp, y = (-46).dp),
            color = VibeChatGreen,
            fontSize = 20.sp
        )

        Text(
            "✧",
            modifier = Modifier.offset(x = 82.dp, y = (-37).dp),
            color = VibeChatAccent,
            fontSize = 25.sp
        )

        Text(
            "·",
            modifier = Modifier.offset(x = (-83).dp, y = 37.dp),
            color = VibeChatAccent,
            fontSize = 30.sp
        )

        Text(
            "✦",
            modifier = Modifier.offset(x = 75.dp, y = 48.dp),
            color = VibeChatGreen,
            fontSize = 15.sp
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Vibe Chat",
                color = VibeChatText,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(3.dp))

            Text(
                "YOUR SPACE · YOUR VIBE",
                color = VibeChatGreen,
                fontSize = 9.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

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
            currentUser?.phoneNumber.orEmpty().ifBlank {
                "Пользователь"
            }
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

    var showEmojiPicker by remember(myId, otherUserId) {
        mutableStateOf(false)
    }

    var emojiSearch by remember(myId, otherUserId) {
        mutableStateOf("")
    }

    var emojiCategory by remember(myId, otherUserId) {
        mutableStateOf("Vibe")
    }

    var attachmentUploading by remember(myId, otherUserId) {
        mutableStateOf(false)
    }

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
            val mimeType = resolver.getType(uri)
                ?: "application/octet-stream"

            val fileName = runCatching {
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && index >= 0) {
                        cursor.getString(index)
                    } else {
                        null
                    }
                }
            }.getOrNull() ?: "Файл"

            val safeName = fileName
                .replace(Regex("[^A-Za-zА-Яа-я0-9._-]"), "_")
                .take(90)

            val storageRef = FirebaseStorage.getInstance()
                .reference
                .child(
                    "chat_attachments/$chatId/" +
                        "${System.currentTimeMillis()}_$safeName"
                )

            val stream = runCatching {
                resolver.openInputStream(uri)
            }.getOrNull()

            if (stream == null) {
                attachmentUploading = false
                error = "Не удалось открыть выбранный файл."
            } else {
                val metadata =
                    com.google.firebase.storage.StorageMetadata.Builder()
                        .setContentType(mimeType)
                        .build()

                storageRef.putStream(stream, metadata)
                    .continueWithTask { task ->
                        runCatching { stream.close() }

                        if (!task.isSuccessful) {
                            throw (
                                task.exception
                                    ?: Exception("Не удалось загрузить файл")
                            )
                        }

                        storageRef.downloadUrl
                    }
                    .addOnSuccessListener { downloadUri ->
                        db.collection("chats")
                            .document(chatId)
                            .collection("messages")
                            .add(
                                mapOf(
                                    "text" to "",
                                    "authorId" to myId,
                                    "authorName" to myName,
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "readBy" to listOf(myId),
                                    "attachmentUrl" to downloadUri.toString(),
                                    "attachmentName" to fileName,
                                    "attachmentType" to mimeType
                                )
                            )
                            .addOnSuccessListener {
                                attachmentUploading = false
                            }
                            .addOnFailureListener { exception ->
                                attachmentUploading = false
                                error =
                                    "Файл загружен, но сообщение не отправилось: " +
                                        (exception.localizedMessage ?: "ошибка")
                            }
                    }
                    .addOnFailureListener { exception ->
                        attachmentUploading = false
                        error = "Не удалось загрузить файл: " +
                            (
                                exception.localizedMessage
                                    ?: "проверь Firebase Storage"
                            )
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

        onDispose {
            registration?.remove()
        }
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
                        error = "Ошибка профиля: " +
                            (
                                exception.localizedMessage
                                    ?: "неизвестная ошибка"
                            )
                    } else {
                        myName = snapshot?.getString("name")
                            ?.takeIf { it.isNotBlank() }
                            ?: currentUser?.phoneNumber.orEmpty()
                                .ifBlank { "Пользователь" }
                    }
                }
        }

        onDispose {
            registration?.remove()
        }
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
                            error = "Не удалось загрузить сообщения: " +
                                (
                                    exception.localizedMessage
                                        ?: "проверь интернет и правила Firebase"
                                )
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
                                    readBy = (
                                        document.get("readBy") as? List<*>
                                    )?.filterIsInstance<String>().orEmpty()
                                )
                            }

                            // Отмечаем входящие сообщения прочитанными.
                            snapshot.documents.forEach { document ->
                                val authorId =
                                    document.getString("authorId").orEmpty()

                                val readBy = (
                                    document.get("readBy") as? List<*>
                                )?.filterIsInstance<String>().orEmpty()

                                if (
                                    authorId.isNotBlank() &&
                                    authorId != myId &&
                                    myId !in readBy
                                ) {
                                    document.reference.update(
                                        "readBy",
                                        FieldValue.arrayUnion(myId)
                                    )
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
                error = "Не удалось открыть чат: " +
                    (
                        exception.localizedMessage
                            ?: "проверь интернет и Firebase"
                    )
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF111622),
                        VibeChatBackground,
                        Color(0xFF101922)
                    )
                )
            )
            .imePadding()
    ) {
        // Верхняя панель.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xEE0D111A))
                .padding(horizontal = 7.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Назад",
                    tint = VibeChatText
                )
            }

            Box(
                modifier = Modifier
                    .size(39.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF263747), Color(0xFF24243A))
                        )
                    )
                    .border(1.dp, Color(0xFF344B50), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (otherAvatarBitmap != null) {
                    Image(
                        bitmap = otherAvatarBitmap.asImageBitmap(),
                        contentDescription = "Аватар собеседника",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        actualOtherName.firstOrNull()?.uppercase() ?: "?",
                        color = VibeChatGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.width(9.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    actualOtherName,
                    color = VibeChatText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )

                Spacer(Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (chatReady) VibeChatGreen else VibeChatMuted
                            )
                    )

                    Spacer(Modifier.width(5.dp))

                    Text(
                        if (chatReady) "Личная переписка" else "Подключение…",
                        color = VibeChatMuted,
                        fontSize = 10.sp
                    )
                }
            }

            IconButton(
                onClick = {},
                modifier = Modifier.size(38.dp)
            ) {
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
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            VibeChatLine,
                            Color(0xFF38504F),
                            VibeChatLine,
                            Color.Transparent
                        )
                    )
                )
        )

        if (error.isNotBlank()) {
            Surface(
                color = Color(0xFF30202D),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    error,
                    color = Color(0xFFFFA8C7),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(9.dp)
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = 10.dp,
                vertical = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (messages.isEmpty()) {
                item(key = "chat_welcome") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        VibeWelcomeArtwork()

                        Spacer(Modifier.height(15.dp))

                        Text(
                            when {
                                loading -> "Подключаем переписку…"
                                chatReady -> "Начни разговор"
                                else -> "Твоя личная вселенная"
                            },
                            color = VibeChatText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            if (chatReady) {
                                "Напиши $actualOtherName первое сообщение"
                            } else {
                                "Здесь будут ваши сообщения"
                            },
                            color = VibeChatMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            items(messages, key = { it.id }) { message ->
                val mine = message.authorId == myId
                val isImage = message.attachmentType
                    .lowercase(Locale.ROOT)
                    .startsWith("image/")

                val isRead = otherUserId in message.readBy

                val bubbleShape = RoundedCornerShape(
                    topStart = 13.dp,
                    topEnd = 13.dp,
                    bottomStart = if (mine) 13.dp else 4.dp,
                    bottomEnd = if (mine) 4.dp else 13.dp
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
                            .widthIn(max = 276.dp)
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
                            .border(
                                width = 0.5.dp,
                                color = if (mine) {
                                    Color(0xFF34534C)
                                } else {
                                    Color(0xFF2B3441)
                                },
                                shape = bubbleShape
                            )
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        if (message.text.isNotBlank()) {
                            Text(
                                message.text,
                                color = VibeChatText,
                                fontSize = 13.sp,
                                lineHeight = 17.sp
                            )
                        }

                        if (message.attachmentUrl.isNotBlank()) {
                            if (isImage) {
                                var imageBitmap by remember(
                                    message.attachmentUrl
                                ) {
                                    mutableStateOf<Bitmap?>(null)
                                }

                                var imageLoadFailed by remember(
                                    message.attachmentUrl
                                ) {
                                    mutableStateOf(false)
                                }

                                LaunchedEffect(message.attachmentUrl) {
                                    val result = withContext(Dispatchers.IO) {
                                        loadVibeImage(message.attachmentUrl)
                                    }
                                    imageBitmap = result
                                    imageLoadFailed = result == null
                                }

                                Spacer(Modifier.height(4.dp))

                                if (imageBitmap != null) {
                                    Image(
                                        bitmap = imageBitmap!!.asImageBitmap(),
                                        contentDescription = message.attachmentName
                                            .ifBlank { "Изображение" },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = 250.dp)
                                            .heightIn(max = 250.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(
                                                            Intent.ACTION_VIEW,
                                                            Uri.parse(message.attachmentUrl)
                                                        )
                                                    )
                                                }.onFailure {
                                                    error = "Не удалось открыть изображение."
                                                }
                                            },
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Surface(
                                        color = Color(0xFF202A35),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(
                                                            Intent.ACTION_VIEW,
                                                            Uri.parse(message.attachmentUrl)
                                                        )
                                                    )
                                                }.onFailure {
                                                    error = "Не удалось открыть вложение."
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.AttachFile,
                                                contentDescription = null,
                                                tint = VibeChatGreen,
                                                modifier = Modifier.size(18.dp)
                                            )

                                            Spacer(Modifier.width(7.dp))

                                            Column {
                                                Text(
                                                    message.attachmentName
                                                        .ifBlank { "Изображение" },
                                                    color = VibeChatText,
                                                    fontSize = 11.sp,
                                                    maxLines = 2
                                                )

                                                Text(
                                                    if (imageLoadFailed) {
                                                        "Нажми, чтобы открыть"
                                                    } else {
                                                        "Загружаем изображение…"
                                                    },
                                                    color = VibeChatMuted,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Spacer(Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF202A35))
                                        .clickable {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse(message.attachmentUrl)
                                                    )
                                                )
                                            }.onFailure {
                                                error = "Не удалось открыть вложение."
                                            }
                                        }
                                        .padding(horizontal = 9.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.AttachFile,
                                        contentDescription = null,
                                        tint = VibeChatGreen,
                                        modifier = Modifier.size(17.dp)
                                    )

                                    Spacer(Modifier.width(6.dp))

                                    Column(
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Text(
                                            message.attachmentName.ifBlank {
                                                "Вложение"
                                            },
                                            color = VibeChatText,
                                            fontSize = 11.sp,
                                            maxLines = 2
                                        )

                                        Text(
                                            "Нажми, чтобы открыть",
                                            color = VibeChatGreen,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            message.createdAt?.let { timestamp ->
                                Text(
                                    SimpleDateFormat(
                                        "HH:mm",
                                        Locale.getDefault()
                                    ).format(Date(timestamp.toDate().time)),
                                    color = VibeChatMuted,
                                    fontSize = 9.sp
                                )
                            }

                            if (mine) {
                                Spacer(Modifier.width(4.dp))

                                Text(
                                    text = if (isRead) "✓✓" else "✓",
                                    color = if (isRead) {
                                        VibeChatGreen
                                    } else {
                                        VibeChatMuted
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Панель выбора эмодзи.
        if (showEmojiPicker) {
            val emojiSets = mapOf(
                "Vibe" to listOf(
                    "🫧", "🪐", "🪼", "🌌", "✨", "💫", "🩵", "💜",
                    "🪽", "🦋", "🌙", "☄️", "🪷", "🧿", "🪄", "🤍",
                    "🥹", "😎", "🫶", "🤝", "🫠", "🙃", "😶‍🌫️", "🤖"
                ),
                "Лица" to listOf(
                    "😀", "😄", "😂", "🥹", "😍", "😎", "🤔", "😴",
                    "🥳", "😭", "😤", "🤗", "🫠", "🙃", "😶‍🌫️", "🤯",
                    "🫡", "🤫"
                ),
                "Сердца" to listOf(
                    "🩵", "💜", "🤍", "🩷", "❤️", "🧡", "💛", "💚",
                    "💙", "🖤", "🤎", "💖", "💗", "💘", "💝", "💞",
                    "💕", "🫶"
                ),
                "Знаки" to listOf(
                    "✨", "💫", "🔥", "⭐", "🌟", "⚡", "☄️", "🌈",
                    "💎", "🎉", "🎧", "🎮", "🚀", "🪄", "🧿", "♾️"
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 9.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(VibeChatPanel)
                    .border(1.dp, VibeChatLine, RoundedCornerShape(15.dp))
                    .padding(9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "VIBE EMOJI",
                            color = VibeChatGreen,
                            fontSize = 9.sp,
                            letterSpacing = 1.3.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            "Выбери настроение",
                            color = VibeChatText,
                            fontSize = 13.sp
                        )
                    }

                    IconButton(
                        onClick = { showEmojiPicker = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Закрыть Emoji",
                            tint = VibeChatMuted,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(VibeChatBackground)
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = VibeChatMuted,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(Modifier.width(7.dp))

                    Box(Modifier.weight(1f)) {
                        if (emojiSearch.isBlank()) {
                            Text(
                                "Найти смайлик…",
                                color = VibeChatMuted,
                                fontSize = 11.sp
                            )
                        }

                        BasicTextField(
                            value = emojiSearch,
                            onValueChange = { emojiSearch = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = VibeChatText,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(7.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    emojiSets.keys.forEach { category ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (emojiCategory == category) {
                                        Color(0xFF263B3B)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable { emojiCategory = category }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                category,
                                color = if (emojiCategory == category) {
                                    VibeChatGreen
                                } else {
                                    VibeChatMuted
                                },
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                val shownEmojis = (
                    emojiSets[emojiCategory] ?: emojiSets["Vibe"].orEmpty()
                ).filter {
                    emojiSearch.isBlank() ||
                        it.contains(emojiSearch, ignoreCase = true)
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 125.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    gridItems(shownEmojis) { emoji ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (input.length < 2000) {
                                        input = (input + emoji).take(2000)
                                    }
                                    showEmojiPicker = false
                                    emojiSearch = ""
                                }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }
            }
        }

        if (attachmentUploading) {
            Text(
                "Загружаем вложение…",
                color = VibeChatGreen,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 14.dp, bottom = 3.dp)
            )
        }

        // Нижняя панель ввода.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xF20D111A))
                .padding(start = 5.dp, end = 7.dp, top = 4.dp, bottom = 5.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                enabled = chatReady && !attachmentUploading,
                onClick = {
                    attachmentPicker.launch(
                        arrayOf(
                            "image/*",
                            "video/*",
                            "application/pdf",
                            "text/*",
                            "application/*"
                        )
                    )
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = "Прикрепить фото или файл",
                    tint = if (attachmentUploading) {
                        VibeChatMuted
                    } else {
                        VibeChatGreen
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = { showEmojiPicker = !showEmojiPicker },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.EmojiEmotions,
                    contentDescription = "Открыть Emoji",
                    tint = if (showEmojiPicker) {
                        VibeChatAccent
                    } else {
                        VibeChatGreen
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(2.dp))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(VibeChatPanel)
                    .border(
                        1.dp,
                        Color(0xFF35444A),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it.take(2000) },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        color = VibeChatText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    cursorBrush = Brush.verticalGradient(
                        listOf(VibeChatGreen, VibeChatAccent)
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (input.isEmpty()) {
                                Text(
                                    "Написать сообщение…",
                                    color = VibeChatMuted,
                                    fontSize = 12.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Spacer(Modifier.width(6.dp))

            val canSend = input.isNotBlank() &&
                chatReady &&
                myId.isNotBlank() &&
                otherUserId.isNotBlank() &&
                myId != otherUserId &&
                !sending

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) {
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF3A6960),
                                    Color(0xFF263E46)
                                )
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(VibeChatPanel, VibeChatPanel)
                            )
                        }
                    )
                    .border(
                        1.dp,
                        if (canSend) Color(0xFF477B70) else VibeChatLine,
                        CircleShape
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
                                    // Очищаем поле только после подтверждения Firebase.
                                    input = ""
                                    sending = false
                                }
                                .addOnFailureListener { exception ->
                                    // При ошибке оставляем текст для повторной отправки.
                                    error = "Не удалось отправить сообщение: " +
                                        (
                                            exception.localizedMessage
                                                ?: "ошибка Firebase"
                                        )
                                    sending = false
                                }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Отправить",
                    tint = if (canSend) VibeChatGreen else VibeChatMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
